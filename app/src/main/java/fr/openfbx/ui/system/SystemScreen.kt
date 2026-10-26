package fr.openfbx.ui.system

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.openfbx.core.AppGraph
import fr.openfbx.core.data.dto.SystemInfo
import fr.openfbx.core.net.userMessage
import fr.openfbx.ui.components.ConfirmDialog
import fr.openfbx.ui.components.InfoRow
import fr.openfbx.ui.components.InlineError
import fr.openfbx.ui.components.InlineNotice
import fr.openfbx.ui.components.LoadingPane
import fr.openfbx.ui.components.ScreenScaffold
import fr.openfbx.ui.components.SectionCard
import fr.openfbx.ui.cpuTemperature
import fr.openfbx.ui.formatTemperature
import fr.openfbx.ui.formatUptime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SystemUiState(
    val loading: Boolean = true,
    val system: SystemInfo? = null,
    val error: String? = null,
    val message: String? = null,
    val rebooting: Boolean = false,
)

class SystemViewModel : ViewModel() {

    private val repository = AppGraph.repository
    private val _state = MutableStateFlow(SystemUiState())
    val state: StateFlow<SystemUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.system == null, error = null) }
            runCatching { repository.system() }
                .onSuccess { info -> _state.update { it.copy(loading = false, system = info) } }
                .onFailure { error -> _state.update { it.copy(loading = false, error = error.userMessage()) } }
        }
    }

    fun reboot() {
        if (_state.value.rebooting) return
        _state.update { it.copy(rebooting = true, message = null) }
        viewModelScope.launch {
            runCatching { repository.reboot() }
                .onSuccess {
                    _state.update { it.copy(rebooting = false, message = "Redémarrage demandé à la Freebox.") }
                }
                .onFailure { error -> _state.update { it.copy(rebooting = false, error = error.userMessage()) } }
        }
    }
}

@Composable
fun SystemScreen(
    onBack: () -> Unit,
    viewModel: SystemViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmReboot by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    ScreenScaffold(title = "Système", onRefresh = viewModel::refresh, onBack = onBack) {
        state.message?.let { InlineNotice(it) }
        state.error?.let { InlineError(it) { viewModel.refresh() } }

        if (state.loading) {
            LoadingPane()
            return@ScreenScaffold
        }

        state.system?.let { system ->
            SectionCard(title = "Informations") {
                InfoRow("Firmware", system.firmwareVersion)
                InfoRow("Modèle", system.boardName)
                InfoRow("Numéro de série", system.serial, mono = true)
                InfoRow("Adresse MAC", system.mac, mono = true)
                InfoRow("Uptime", formatUptime(system.uptimeSeconds))
            }

            SectionCard(title = "Matériel") {
                if (system.sensors.isNotEmpty()) {
                    system.sensors.forEach { sensor ->
                        val label = sensor.name ?: sensor.id ?: "Capteur"
                        InfoRow(label, sensor.value?.let { "%.1f °C".format(it) })
                    }
                } else {
                    InfoRow("Température CPU", formatTemperature(system.cpuTemperature()))
                    InfoRow("Température switch", formatTemperature(system.tempSwitch))
                }
                InfoRow("Ventilateur", system.fanRpm?.let { "$it tr/min" })
                InfoRow("Disque", system.diskStatus)
                InfoRow("Stockage principal", system.mainStorage)
            }

            SectionCard(title = "Sécurité") {
                InfoRow(
                    "Box authentifiée",
                    when (system.authenticated) {
                        true -> "oui"
                        false -> "non"
                        null -> "—"
                    },
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        OutlinedButton(
            onClick = { confirmReboot = true },
            enabled = !state.rebooting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.rebooting) {
                CircularProgressIndicator(Modifier.height(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.height(8.dp))
            }
            Text(if (state.rebooting) "Redémarrage…" else "Redémarrer la Freebox")
        }
    }

    if (confirmReboot) {
        ConfirmDialog(
            title = "Redémarrer la Freebox ?",
            text = "Tous les services (Internet, Wi-Fi, télévision) seront interrompus pendant le redémarrage.",
            confirmLabel = "Redémarrer",
            onConfirm = viewModel::reboot,
            onDismiss = { confirmReboot = false },
        )
    }
}
