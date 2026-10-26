package fr.openfbx.ui.wifi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.openfbx.core.AppGraph
import fr.openfbx.core.data.dto.WifiAccessPoint
import fr.openfbx.core.data.dto.WifiStation
import fr.openfbx.core.net.userMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WifiUiState(
    val loading: Boolean = true,
    val enabled: Boolean = false,
    val accessPoints: List<WifiAccessPoint> = emptyList(),
    val stations: Map<Int, List<WifiStation>> = emptyMap(),
    val stationsLoading: Set<Int> = emptySet(),
    val error: String? = null,
    val message: String? = null,
    val busy: Boolean = false,
)

class WifiViewModel : ViewModel() {

    private val repository = AppGraph.repository
    private val _state = MutableStateFlow(WifiUiState())
    val state: StateFlow<WifiUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun refresh() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            _state.update { it.copy(loading = it.accessPoints.isEmpty(), error = null) }
            runCatching {
                coroutineScope {
                    val enabled = async { repository.wifiEnabled() }
                    val accessPoints = async { repository.wifiAccessPoints() }
                    enabled.await() to accessPoints.await()
                }
            }.onSuccess { (enabled, accessPoints) ->
                _state.update {
                    it.copy(
                        loading = false,
                        enabled = enabled,
                        accessPoints = accessPoints,
                        error = null,
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(loading = false, error = error.userMessage()) }
            }
        }
    }

    fun setWifiEnabled(enabled: Boolean) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            runCatching { repository.setWifiEnabled(enabled) }
                .onSuccess {
                    _state.update {
                        it.copy(busy = false, enabled = enabled, message = if (enabled) "Wi-Fi activé." else "Wi-Fi désactivé.")
                    }
                    refresh()
                }
                .onFailure { error -> _state.update { it.copy(busy = false, error = error.userMessage()) } }
        }
    }

    fun loadStations(apId: Int) {
        if (_state.value.stationsLoading.contains(apId)) return
        _state.update { it.copy(stationsLoading = it.stationsLoading + apId) }
        viewModelScope.launch {
            runCatching { repository.wifiStations(apId) }
                .onSuccess { stations ->
                    _state.update {
                        it.copy(
                            stationsLoading = it.stationsLoading - apId,
                            stations = it.stations + (apId to stations.sortedByDescending { station -> station.signal ?: Int.MIN_VALUE }),
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            stationsLoading = it.stationsLoading - apId,
                            message = error.userMessage(),
                            stations = it.stations + (apId to emptyList()),
                        )
                    }
                }
        }
    }
}
