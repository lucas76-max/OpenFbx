package fr.openfbx.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.openfbx.core.AppGraph
import fr.openfbx.core.model.AccentPalette
import fr.openfbx.core.model.AppSettings
import fr.openfbx.core.model.BoxConfig
import fr.openfbx.core.model.ThemeMode
import fr.openfbx.core.net.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val loading: Boolean = true,
    val config: BoxConfig? = null,
    val settings: AppSettings = AppSettings(),
    val permissions: Map<String, Boolean> = emptyMap(),
    val error: String? = null,
    val message: String? = null,
    val busy: Boolean = false,
)

class SettingsViewModel : ViewModel() {

    private val repository = AppGraph.repository
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val config = runCatching { repository.currentConfig() }.getOrNull()
            val settings = runCatching { repository.currentSettings() }.getOrDefault(AppSettings())
            val permissions = runCatching { repository.openSession() }
                .getOrElse { repository.permissions() }
            _state.update {
                it.copy(
                    loading = false,
                    config = config,
                    settings = settings,
                    permissions = permissions,
                )
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            runCatching { repository.updateSettings(themeMode = mode) }
                .onSuccess {
                    _state.update { current -> current.copy(settings = current.settings.copy(themeMode = mode)) }
                }
                .onFailure { error -> _state.update { it.copy(error = error.userMessage()) } }
        }
    }

    /**
     * Choisir une palette désactive les couleurs dynamiques : sinon le système
     * continuerait d'imposer sa propre palette et le choix resterait sans effet.
     */
    fun setAccent(palette: AccentPalette) {
        viewModelScope.launch {
            runCatching { repository.updateSettings(accent = palette, dynamicColor = false) }
                .onSuccess {
                    _state.update { current ->
                        current.copy(settings = current.settings.copy(accent = palette, dynamicColor = false))
                    }
                }
                .onFailure { error -> _state.update { it.copy(error = error.userMessage()) } }
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { repository.updateSettings(dynamicColor = enabled) }
                .onSuccess {
                    _state.update { current -> current.copy(settings = current.settings.copy(dynamicColor = enabled)) }
                }
                .onFailure { error -> _state.update { it.copy(error = error.userMessage()) } }
        }
    }

    fun resetPin() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            runCatching { repository.forgetPin() }
                .onSuccess {
                    _state.update {
                        it.copy(
                            busy = false,
                            message = "Empreinte oubliée : elle sera relevée de nouveau à la prochaine connexion.",
                        )
                    }
                    load()
                }
                .onFailure { error -> _state.update { it.copy(busy = false, error = error.userMessage()) } }
        }
    }

    fun forgetBox() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            runCatching { repository.forgetBox() }
                .onFailure { error -> _state.update { it.copy(busy = false, error = error.userMessage()) } }
        }
    }
}
