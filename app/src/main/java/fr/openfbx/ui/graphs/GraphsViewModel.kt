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
    val loadingDatabases: Boolean = true,
    val databases: List<String> = emptyList(),
    val database: String = "net",
    val field: String = "rx_1",
    val windowMinutes: Int = 60,
    val points: List<Pair<Long, Double>> = emptyList(),
    val loadingSeries: Boolean = false,
    val seriesError: String? = null,
)

fun fieldSuggestions(database: String): List<String> = when (database) {
    "net" -> listOf("rx_1", "tx_1", "bw_down", "bw_up")
    "temp" -> listOf("temp_cpub", "temp_sw", "temp_cpu")
    "dsl" -> listOf("snr", "rate", "attenuation")
    "switch" -> listOf("rx_1", "tx_1")
    "wifi" -> listOf("rx_1", "tx_1")
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
        viewModelScope.launch {
            _state.update { it.copy(loadingDatabases = true) }
            val databases = runCatching { repository.rrdDatabases() }.getOrDefault(emptyList())
            _state.update { current ->
                val database = if (databases.isEmpty() || databases.contains(current.database)) {
                    current.database
                } else {
                    databases.first()
                }
                current.copy(
                    loadingDatabases = false,
                    databases = databases,
                    database = database,
                    field = if (databases.contains(current.database)) current.field else fieldSuggestions(database).first(),
                )
            }
            loadSeries()
        }
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
