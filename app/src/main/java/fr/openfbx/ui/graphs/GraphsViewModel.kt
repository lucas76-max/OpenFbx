package fr.openfbx.ui.graphs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.openfbx.core.AppGraph
import fr.openfbx.core.net.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GraphsUiState(
    val databases: List<String> = emptyList(),
    val database: String = "net",
    val field: String = "rate_down",
    val windowMinutes: Int = 60,
    val points: List<Pair<Long, Double>> = emptyList(),
    val loadingSeries: Boolean = false,
    val seriesError: String? = null,
)

/**
 * Champs documentés par base RRD (voir `dev.freebox.fr/sdk/os/rrd/`).
 * `net` et `switch` sont en octets/s, `temp` en °C, `dsl` en octets/s
 * (le SNR est en dixièmes de dB).
 */
fun fieldSuggestions(database: String): List<String> = when (database) {
    "net" -> listOf("rate_down", "rate_up", "bw_down", "bw_up")
    "temp" -> listOf("cpub", "cpum", "sw", "hdd")
    "dsl" -> listOf("rate_down", "rate_up", "snr_down", "snr_up")
    "switch" -> listOf("rx_1", "tx_1", "rx_2", "tx_2")
    else -> listOf("value")
}

class GraphsViewModel : ViewModel() {

    private val repository = AppGraph.repository
    private val _state = MutableStateFlow(GraphsUiState())
    val state: StateFlow<GraphsUiState> = _state.asStateFlow()

    init {
        loadDatabases()
    }

    fun loadDatabases() {
        val databases = repository.rrdDatabases()
        _state.update { current ->
            val database = if (databases.contains(current.database)) current.database else databases.first()
            current.copy(
                databases = databases,
                database = database,
                field = if (databases.contains(current.database)) current.field else fieldSuggestions(database).first(),
            )
        }
        loadSeries()
    }

    fun selectDatabase(database: String) {
        _state.update { it.copy(database = database, field = fieldSuggestions(database).first()) }
        loadSeries()
    }

    fun updateField(field: String) {
        _state.update { it.copy(field = field) }
    }

    fun selectField(field: String) {
        _state.update { it.copy(field = field) }
        loadSeries()
    }

    fun selectWindow(minutes: Int) {
        _state.update { it.copy(windowMinutes = minutes) }
        loadSeries()
    }

    fun loadSeries() {
        val snapshot = _state.value
        if (snapshot.field.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(loadingSeries = true, seriesError = null) }
            runCatching { repository.rrdSeries(snapshot.database, snapshot.field, snapshot.windowMinutes) }
                .onSuccess { points ->
                    _state.update { it.copy(loadingSeries = false, points = points) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(loadingSeries = false, points = emptyList(), seriesError = error.userMessage())
                    }
                }
        }
    }
}
