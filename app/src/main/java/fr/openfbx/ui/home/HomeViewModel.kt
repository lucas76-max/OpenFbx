package fr.openfbx.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.openfbx.core.AppGraph
import fr.openfbx.core.data.dto.ConnectionStatus
import fr.openfbx.core.data.dto.SystemInfo
import fr.openfbx.core.net.userMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val system: SystemInfo? = null,
    val connection: ConnectionStatus? = null,
    val accessPointTotal: Int = 0,
    val accessPointEnabled: Int = 0,
    val activeHosts: Int = 0,
    val error: String? = null,
    val message: String? = null,
    val rebooting: Boolean = false,
)

class HomeViewModel : ViewModel() {

    private val repository = AppGraph.repository
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun refresh() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            _state.update { it.copy(loading = it.system == null, error = null) }
            runCatching {
                coroutineScope {
                    val system = async { repository.system() }
                    val connection = async { repository.connection() }
                    val accessPoints = async { runCatching { repository.wifiAccessPoints() }.getOrNull() }
                    val hosts = async { runCatching { repository.lanHosts() }.getOrNull() }
                    Snapshot(system.await(), connection.await(), accessPoints.await(), hosts.await())
                }
            }.onSuccess { snapshot ->
                _state.update { current ->
                    current.copy(
                        loading = false,
                        error = null,
                        system = snapshot.system,
                        connection = snapshot.connection,
                        accessPointTotal = snapshot.accessPoints?.size ?: current.accessPointTotal,
                        accessPointEnabled = snapshot.accessPoints?.count { it.isEnabled } ?: current.accessPointEnabled,
                        activeHosts = snapshot.hosts?.count { it.active } ?: current.activeHosts,
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(loading = false, error = error.userMessage()) }
            }
        }
    }

    /** Rafraîchissement léger : seuls les débits changent vraiment. */
    fun refreshLive() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            val connection = runCatching { repository.connection() }.getOrNull() ?: return@launch
            _state.update { it.copy(connection = connection) }
        }
    }

    fun reboot() {
        if (_state.value.rebooting) return
        _state.update { it.copy(rebooting = true, message = null) }
        viewModelScope.launch {
            runCatching { repository.reboot() }
                .onSuccess {
                    _state.update {
                        it.copy(rebooting = false, message = "Redémarrage demandé. La Freebox sera de nouveau joignable dans quelques minutes.")
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(rebooting = false, error = error.userMessage()) }
                }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private data class Snapshot(
        val system: SystemInfo,
        val connection: ConnectionStatus,
        val accessPoints: List<fr.openfbx.core.data.dto.WifiAccessPoint>?,
        val hosts: List<fr.openfbx.core.data.dto.LanHost>?,
    )
}
