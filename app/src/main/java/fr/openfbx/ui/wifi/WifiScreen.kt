package fr.openfbx.ui.wifi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.openfbx.core.data.dto.WifiAccessPoint
import fr.openfbx.core.data.dto.WifiStation
import fr.openfbx.ui.components.EmptyPane
import fr.openfbx.ui.components.InfoRow
import fr.openfbx.ui.components.InlineError
import fr.openfbx.ui.components.InlineNotice
import fr.openfbx.ui.components.LoadingPane
import fr.openfbx.ui.components.PillTone
import fr.openfbx.ui.components.ScreenScaffold
import fr.openfbx.ui.components.SectionCard
import fr.openfbx.ui.components.StatusPill
import fr.openfbx.ui.components.SwitchRow
import fr.openfbx.ui.formatChannel
import fr.openfbx.ui.formatSignal

@Composable
fun WifiScreen(
    onBack: (() -> Unit)? = null,
    viewModel: WifiViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(emptySet<Int>()) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    ScreenScaffold(title = "Wi-Fi", onRefresh = viewModel::refresh, onBack = onBack) {
        state.message?.let { InlineNotice(it) }
        state.error?.let { InlineError(it) { viewModel.refresh() } }

        SectionCard(title = "Radio Wi-Fi") {
            SwitchRow(
                title = "Wi-Fi de la Freebox",
                subtitle = if (state.enabled) "Activé" else "Désactivé",
                checked = state.enabled,
                enabled = !state.busy,
                onCheckedChange = viewModel::setWifiEnabled,
            )
        }

        if (state.loading) {
            LoadingPane()
            return@ScreenScaffold
        }

        if (state.accessPoints.isEmpty()) {
            EmptyPane("Aucun point d'accès Wi-Fi remonté par la Freebox.")
        }

        state.accessPoints.forEach { accessPoint ->
            val isExpanded = expanded.contains(accessPoint.id)
            AccessPointCard(
                accessPoint = accessPoint,
                expanded = isExpanded,
                stations = state.stations[accessPoint.id],
                stationsLoading = state.stationsLoading.contains(accessPoint.id),
                busy = state.busy,
                onToggleEnabled = { enabled -> viewModel.setAccessPointEnabled(accessPoint.id, enabled) },
                onToggleExpanded = {
                    expanded = if (isExpanded) expanded - accessPoint.id else expanded + accessPoint.id
                    if (!isExpanded) viewModel.loadStations(accessPoint.id)
                },
            )
        }
    }
}

@Composable
private fun AccessPointCard(
    accessPoint: WifiAccessPoint,
    expanded: Boolean,
    stations: List<WifiStation>?,
    stationsLoading: Boolean,
    busy: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleExpanded: () -> Unit,
) {
    SectionCard(title = accessPoint.name ?: "Point d'accès ${accessPoint.id}") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(
                text = when {
                    !accessPoint.isEnabled -> "désactivé"
                    accessPoint.state == "active" -> "actif"
                    else -> accessPoint.state ?: "inconnu"
                },
                tone = when {
                    !accessPoint.isEnabled -> PillTone.NEUTRAL
                    accessPoint.state == "active" -> PillTone.POSITIVE
                    else -> PillTone.NEUTRAL
                },
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = accessPoint.ssid ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        InfoRow(
            "Canal",
            formatChannel(accessPoint.status?.primaryChannel, accessPoint.status?.channelWidth),
        )
        InfoRow("Puissance", accessPoint.status?.txPower?.let { "$it %" })

        SwitchRow(
            title = "Diffusion",
            checked = accessPoint.isEnabled,
            enabled = !busy,
            onCheckedChange = onToggleEnabled,
        )

        HorizontalDivider()

        TextButton(onClick = onToggleExpanded) {
            Text(
                when {
                    expanded -> "Masquer les appareils"
                    stations != null -> "Voir les appareils (${stations.size})"
                    else -> "Voir les appareils connectés"
                },
            )
        }

        if (expanded) {
            when {
                stationsLoading -> LoadingPane("Lecture des appareils…")
                stations.isNullOrEmpty() -> EmptyPane("Aucun appareil connecté à ce point d'accès.")
                else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    stations.forEach { station -> StationRow(station) }
                }
            }
        }
    }
}

@Composable
private fun StationRow(station: WifiStation) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = station.hostname ?: station.mac ?: "Appareil",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatSignal(station.signal),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = station.mac ?: "—",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
        )
    }
}
