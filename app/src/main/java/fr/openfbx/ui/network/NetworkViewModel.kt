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

data class NetworkUiState(
    val loading: Boolean = true,
    val connection: ConnectionStatus? = null,
    val ftth: FtthStatus? = null,
    val xdsl: XdslStatus? = null,
    val hosts: List<LanHost> = emptyList(),
    val dhcp: DhcpConfig? = null,
    val leases: List<DhcpLease> = emptyList(),
    val staticLeases: List<StaticLease> = emptyList(),
    val error: String? = null,
    val message: String? = null,
    val busy: Boolean = false,
)

class NetworkViewModel : ViewModel() {

    private val repository = AppGraph.repository
    private val _state = MutableStateFlow(NetworkUiState())
    val state: StateFlow<NetworkUiState> = _state.asStateFlow()

    private var job: Job? = null

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
                    Bundle(
                        connection.await(),
                        ftth.await(),
                        xdsl.await(),
                        hosts.await(),
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
                        hosts = bundle.hosts.sortedBy { host -> host.primaryName ?: host.primaryAddr ?: "" },
                        dhcp = bundle.dhcp,
                        leases = bundle.leases,
                        staticLeases = bundle.staticLeases,
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(loading = false, error = error.userMessage()) }
            }
        }
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

    private data class Bundle(
        val connection: ConnectionStatus,
        val ftth: FtthStatus?,
        val xdsl: XdslStatus?,
        val hosts: List<LanHost>,
        val dhcp: DhcpConfig?,
        val leases: List<DhcpLease>,
        val staticLeases: List<StaticLease>,
    )
}
