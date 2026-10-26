package fr.openfbx.ui.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.openfbx.core.AppGraph
import fr.openfbx.core.data.dto.ConnectionStatus
import fr.openfbx.core.data.dto.DhcpConfig
import fr.openfbx.core.data.dto.DhcpLease
import fr.openfbx.core.data.dto.FtthStatus
import fr.openfbx.core.data.dto.LanHost
import fr.openfbx.core.data.dto.StaticLease
import fr.openfbx.core.data.dto.WifiStation
import fr.openfbx.core.data.dto.XdslStatus
import fr.openfbx.core.net.userMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Débit instantané d'un appareil (octets/s). */
data class HostRate(val rx: Long?, val tx: Long?, val viaWifi: Boolean)

data class NetworkUiState(
    val loading: Boolean = true,
    val connection: ConnectionStatus? = null,
    val ftth: FtthStatus? = null,
    val xdsl: XdslStatus? = null,
    val hosts: List<LanHost> = emptyList(),
    val rates: Map<String, HostRate> = emptyMap(),
    val dhcp: DhcpConfig? = null,
    val leases: List<DhcpLease> = emptyList(),
    val staticLeases: List<StaticLease> = emptyList(),
    val lastUpdate: Long = 0L,
    val error: String? = null,
    val message: String? = null,
    val busy: Boolean = false,
) {
    val activeHosts: Int get() = hosts.count { it.active }
    val totalDown: Long get() = rates.values.sumOf { it.rx ?: 0L }
    val totalUp: Long get() = rates.values.sumOf { it.tx ?: 0L }
}

class NetworkViewModel : ViewModel() {

    private val repository = AppGraph.repository
    private val _state = MutableStateFlow(NetworkUiState())
    val state: StateFlow<NetworkUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var liveJob: Job? = null

    fun refresh() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            _state.update { it.copy(loading = it.connection == null, error = null) }
            runCatching {
                coroutineScope {
                    val connection = async { repository.connection() }
                    val ftth = async { repository.ftth() }
                    val xdsl = async { repository.xdsl() }
                    val hosts = async { runCatching { repository.lanHosts() }.getOrDefault(emptyList()) }
                    val dhcp = async { repository.dhcpConfig() }
                    val leases = async { runCatching { repository.dhcpLeases() }.getOrDefault(emptyList()) }
                    val staticLeases = async { runCatching { repository.staticLeases() }.getOrDefault(emptyList()) }
                    val loadedHosts = hosts.await()
                    val rates = runCatching { loadRates(loadedHosts) }.getOrDefault(emptyMap())
                    Bundle(
                        connection.await(),
                        ftth.await(),
                        xdsl.await(),
                        loadedHosts,
                        rates,
                        dhcp.await(),
                        leases.await(),
                        staticLeases.await(),
                    )
                }
            }.onSuccess { bundle ->
                _state.update {
                    it.copy(
                        loading = false,
                        error = null,
                        connection = bundle.connection,
                        ftth = bundle.ftth,
                        xdsl = bundle.xdsl,
                        hosts = sortHosts(bundle.hosts),
                        rates = bundle.rates,
                        dhcp = bundle.dhcp,
                        leases = bundle.leases,
                        staticLeases = bundle.staticLeases,
                        lastUpdate = System.currentTimeMillis(),
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(loading = false, error = error.userMessage()) }
            }
        }
    }

    /** Rafraîchissement léger : présence et débits des appareils uniquement. */
    fun refreshLive() {
        if (liveJob?.isActive == true || job?.isActive == true) return
        liveJob = viewModelScope.launch {
            val hosts = runCatching { repository.lanHosts() }.getOrNull() ?: return@launch
            val rates = runCatching { loadRates(hosts) }.getOrDefault(emptyMap())
            _state.update {
                it.copy(hosts = sortHosts(hosts), rates = rates, lastUpdate = System.currentTimeMillis())
            }
        }
    }

    /**
     * Débits par appareil : les champs `rx_rate`/`tx_rate` quand le firmware les
     * expose, sinon les débits des stations Wi-Fi associées (identifiées par MAC).
     */
    private suspend fun loadRates(hosts: List<LanHost>): Map<String, HostRate> {
        val fromHosts = hosts.mapNotNull { host ->
            val mac = host.mac?.lowercase() ?: return@mapNotNull null
            if (host.rxRate == null && host.txRate == null) return@mapNotNull null
            mac to HostRate(host.rxRate, host.txRate, viaWifi = false)
        }.toMap()

        val accessPoints = runCatching { repository.wifiAccessPoints() }.getOrDefault(emptyList())
        val stations = buildList {
            accessPoints.forEach { ap ->
                addAll(runCatching { repository.wifiStations(ap.id) }.getOrDefault(emptyList()))
            }
        }
        val fromWifi = stations.mapNotNull { station: WifiStation ->
            val mac = station.mac?.lowercase() ?: return@mapNotNull null
            mac to HostRate(station.rateDown, station.rateUp, viaWifi = true)
        }.toMap()

        return fromHosts + fromWifi
    }

    fun wakeOnLan(mac: String) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            runCatching { repository.wakeOnLan(mac) }
                .onSuccess { _state.update { it.copy(busy = false, message = "Paquet magique envoyé à $mac.") } }
                .onFailure { error -> _state.update { it.copy(busy = false, error = error.userMessage()) } }
        }
    }

    fun addStaticLease(mac: String, ip: String, comment: String?) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            runCatching { repository.addStaticLease(mac, ip, comment) }
                .onSuccess {
                    _state.update { it.copy(busy = false, message = "Bail statique enregistré pour $mac.") }
                    refreshStaticLeases()
                }
                .onFailure { error -> _state.update { it.copy(busy = false, error = error.userMessage()) } }
        }
    }

    fun deleteStaticLease(id: String) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            runCatching { repository.deleteStaticLease(id) }
                .onSuccess {
                    _state.update { it.copy(busy = false, message = "Bail statique supprimé.") }
                    refreshStaticLeases()
                }
                .onFailure { error -> _state.update { it.copy(busy = false, error = error.userMessage()) } }
        }
    }

    private fun refreshStaticLeases() {
        viewModelScope.launch {
            val leases = runCatching { repository.staticLeases() }.getOrNull() ?: return@launch
            _state.update { it.copy(staticLeases = leases) }
        }
    }

    private fun sortHosts(hosts: List<LanHost>): List<LanHost> =
        hosts.sortedWith(
            compareBy<LanHost> { if (it.active) 0 else 1 }
                .thenBy { (it.primaryName ?: it.primaryAddr ?: "").lowercase() },
        )

    private data class Bundle(
        val connection: ConnectionStatus,
        val ftth: FtthStatus?,
        val xdsl: XdslStatus?,
        val hosts: List<LanHost>,
        val rates: Map<String, HostRate>,
        val dhcp: DhcpConfig?,
        val leases: List<DhcpLease>,
        val staticLeases: List<StaticLease>,
    )
}
