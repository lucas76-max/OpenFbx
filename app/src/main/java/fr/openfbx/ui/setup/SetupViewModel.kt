package fr.openfbx.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.openfbx.core.AppGraph
import fr.openfbx.core.model.DiscoveredBox
import fr.openfbx.core.net.PairingTicket
import fr.openfbx.core.net.userMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SetupUiState(
    val discovering: Boolean = false,
    val discovered: DiscoveredBox? = null,
    val discoveryError: String? = null,
    val saving: Boolean = false,
    val pairingStarted: Boolean = false,
    val pairingStatus: String? = null,
    val pairingError: String? = null,
    val fingerprint: String? = null,
    val permissions: Map<String, Boolean> = emptyMap(),
    val paired: Boolean = false,
)

class SetupViewModel : ViewModel() {

    private val repository = AppGraph.repository
    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    private var pollJob: Job? = null
    private var ticket: PairingTicket? = null

    fun discover() {
        if (_state.value.discovering) return
        _state.update { it.copy(discovering = true, discoveryError = null, discovered = null) }
        viewModelScope.launch {
            runCatching { repository.discoverDefault() }
                .onSuccess { box -> _state.update { it.copy(discovering = false, discovered = box) } }
                .onFailure { error -> _state.update { it.copy(discovering = false, discoveryError = error.userMessage()) } }
        }
    }

    fun discoverManual(host: String, port: Int?) {
        if (_state.value.discovering) return
        _state.update { it.copy(discovering = true, discoveryError = null, discovered = null) }
        viewModelScope.launch {
            runCatching { repository.discoverHost(host = host, https = true, port = port) }
                .onSuccess { box -> _state.update { it.copy(discovering = false, discovered = box) } }
                .onFailure { error -> _state.update { it.copy(discovering = false, discoveryError = error.userMessage()) } }
        }
    }

    fun saveDiscoveredBox() {
        val box = _state.value.discovered ?: return
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching { repository.saveDiscoveredBox(box) }
                .onSuccess {
                    _state.update { it.copy(saving = false, discovered = null) }
                }
                .onFailure { error ->
                    _state.update { it.copy(saving = false, discoveryError = error.userMessage()) }
                }
        }
    }

    fun startPairing() {
        if (_state.value.pairingStarted) return
        _state.update {
            it.copy(pairingStarted = true, pairingError = null, pairingStatus = "pending")
        }
        viewModelScope.launch {
            runCatching { repository.requestPairing() }
                .onSuccess { newTicket ->
                    ticket = newTicket
                    _state.update { it.copy(fingerprint = repository.observedPin()) }
                    pollPairing(newTicket)
                }
                .onFailure { error ->
                    _state.update { it.copy(pairingStarted = false, pairingError = error.userMessage()) }
                }
        }
    }

    fun restartPairing() {
        pollJob?.cancel()
        ticket = null
        _state.update {
            it.copy(
                pairingStarted = false,
                pairingStatus = null,
                pairingError = null,
                permissions = emptyMap(),
            )
        }
        startPairing()
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }

    private fun pollPairing(activeTicket: PairingTicket) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                val result = runCatching { repository.pairingStatus(activeTicket.trackId) }

                result.onFailure { error ->
                    _state.update { it.copy(pairingError = error.userMessage()) }
                    return@launch
                }

                val status = result.getOrNull() ?: return@launch
                val pin = repository.observedPin()
                _state.update {
                    it.copy(
                        pairingStatus = status.status,
                        permissions = status.permissions,
                        fingerprint = pin ?: it.fingerprint,
                    )
                }

                when (status.status) {
                    "granted" -> {
                        runCatching {
                            repository.saveAppToken(activeTicket.appToken)
                            repository.openSession()
                        }.onSuccess {
                            _state.update { it.copy(paired = true) }
                        }.onFailure { error ->
                            _state.update { it.copy(pairingError = error.userMessage()) }
                        }
                        return@launch
                    }
                    "denied" -> {
                        _state.update { it.copy(pairingError = "La demande a été refusée sur la Freebox.") }
                        return@launch
                    }
                    "timeout" -> {
                        _state.update {
                            it.copy(pairingError = "La demande a expiré : appuyez de nouveau sur « Demander l'appairage ».")
                        }
                        return@launch
                    }
                }

                delay(2_000)
            }
        }
    }
}
