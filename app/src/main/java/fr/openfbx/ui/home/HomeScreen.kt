package fr.openfbx.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.openfbx.core.data.dto.ConnectionStatus
import fr.openfbx.ui.components.ConfirmDialog
import fr.openfbx.ui.components.InfoRow
import fr.openfbx.ui.components.InlineError
import fr.openfbx.ui.components.InlineNotice
import fr.openfbx.ui.components.LoadingPane
import fr.openfbx.ui.components.PillTone
import fr.openfbx.ui.components.ScreenScaffold
import fr.openfbx.ui.components.SectionCard
import fr.openfbx.ui.components.StatTile
import fr.openfbx.ui.components.StatusPill
import fr.openfbx.ui.cpuTemperature
import fr.openfbx.ui.formatBytes
import fr.openfbx.ui.formatLinkSpeed
import fr.openfbx.ui.formatRate
import fr.openfbx.ui.formatUptime
import fr.openfbx.ui.humanMedia
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    boxName: String,
    onOpenSystem: () -> Unit,
    onOpenWifi: () -> Unit,
    onOpenGraphs: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmReboot by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refresh()
        while (true) {
            delay(5_000)
            viewModel.refreshLive()
        }
    }

    ScreenScaffold(title = "OpenFbx", onRefresh = viewModel::refresh) {
        if (state.loading) {
            LoadingPane("Connexion à la Freebox…")
            return@ScreenScaffold
        }

        state.message?.let { message ->
            InlineNotice(message)
        }
        state.error?.let { message ->
            InlineError(message) { viewModel.refresh() }
        }

        state.connection?.let { connection ->
            ConnectionCard(connection)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    label = "Débit descendant",
                    value = formatRate(connection.rateDown),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Débit montant",
                    value = formatRate(connection.rateUp),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                label = "Wi-Fi",
                value = "${state.accessPointEnabled}/${state.accessPointTotal}",
                caption = "points d'accès actifs",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Appareils",
                value = state.activeHosts.toString(),
                caption = "actifs sur le réseau",
                modifier = Modifier.weight(1f),
            )
        }

        state.system?.let { system ->
            SectionCard(title = "Freebox") {
                InfoRow("Box", boxName)
                InfoRow("Modèle", system.boardName ?: system.mac)
                InfoRow("Firmware", system.firmwareVersion)
                InfoRow("Uptime", formatUptime(system.uptimeSeconds))
                InfoRow("Température CPU", system.cpuTemperature()?.let { "%.1f °C".format(it) } ?: "—")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpenWifi, modifier = Modifier.weight(1f)) {
                Text("Wi-Fi", maxLines = 1)
            }
            OutlinedButton(onClick = onOpenGraphs, modifier = Modifier.weight(1f)) {
                Text("Courbes", maxLines = 1)
            }
            OutlinedButton(onClick = onOpenSystem, modifier = Modifier.weight(1f)) {
                Text("Système", maxLines = 1)
            }
        }

        OutlinedButton(
            onClick = { confirmReboot = true },
            enabled = !state.rebooting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.rebooting) {
                CircularProgressIndicator(Modifier.height(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(if (state.rebooting) "Redémarrage…" else "Redémarrer la Freebox")
        }

        Spacer(Modifier.height(16.dp))
    }

    if (confirmReboot) {
        ConfirmDialog(
            title = "Redémarrer la Freebox ?",
            text = "La connexion Internet et le Wi-Fi seront coupés pendant environ deux minutes.",
            confirmLabel = "Redémarrer",
            onConfirm = viewModel::reboot,
            onDismiss = { confirmReboot = false },
        )
    }
}

@Composable
private fun ConnectionCard(connection: ConnectionStatus) {
    SectionCard(title = "Connexion Internet") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(
                text = if (connection.isUp) "connectée" else "hors ligne",
                tone = if (connection.isUp) PillTone.POSITIVE else PillTone.NEGATIVE,
            )
            Spacer(Modifier.width(8.dp))
            Text(humanMedia(connection.media), style = MaterialTheme.typography.bodyMedium)
        }
        InfoRow("Adresse IPv4", connection.ipv4, mono = true)
        connection.ipv6?.let { InfoRow("Adresse IPv6", it, mono = true) }
        InfoRow(
            "Débit maximal",
            "${formatLinkSpeed(connection.bandwidthDown)} ↓ · ${formatLinkSpeed(connection.bandwidthUp)} ↑",
        )
        InfoRow(
            "Depuis le démarrage",
            "${formatBytes(connection.bytesDown)} ↓ · ${formatBytes(connection.bytesUp)} ↑",
        )
    }
}
