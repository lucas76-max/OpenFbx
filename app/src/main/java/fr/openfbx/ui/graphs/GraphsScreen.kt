package fr.openfbx.ui.graphs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.openfbx.ui.components.EmptyPane
import fr.openfbx.ui.components.InfoRow
import fr.openfbx.ui.components.InlineError
import fr.openfbx.ui.components.LineChart
import fr.openfbx.ui.components.LoadingPane
import fr.openfbx.ui.components.ScreenScaffold
import fr.openfbx.ui.components.SectionCard
import fr.openfbx.ui.formatBytes
import fr.openfbx.ui.formatDateTimeShort

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphsScreen(
    onBack: (() -> Unit)? = null,
    viewModel: GraphsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadDatabases() }

    ScreenScaffold(title = "Graphiques", onRefresh = viewModel::loadDatabases, onBack = onBack) {
        Text(
            "Mesures historiques fournies par le service RRD de Freebox OS. Les séries disponibles " +
                "dépendent du modèle et du firmware de votre box.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionCard(title = "Base de données") {
            if (state.databases.isEmpty()) {
                EmptyPane("Aucune base RRD exposée par cette Freebox.")
            } else {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.databases.forEach { database ->
                        FilterChip(
                            selected = state.database == database,
                            onClick = { viewModel.selectDatabase(database) },
                            label = { Text(database) },
                        )
                    }
                }
            }
        }

        SectionCard(title = "Série") {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                fieldSuggestions(state.database).forEach { field ->
                    FilterChip(
                        selected = state.field == field,
                        onClick = { viewModel.selectField(field) },
                        label = { Text(field) },
                    )
                }
            }
            OutlinedTextField(
                value = state.field,
                onValueChange = viewModel::updateField,
                label = { Text("Champ mesuré") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(60 to "1 h", 360 to "6 h", 1440 to "24 h").forEach { (minutes, label) ->
                    FilterChip(
                        selected = state.windowMinutes == minutes,
                        onClick = { viewModel.selectWindow(minutes) },
                        label = { Text(label) },
                    )
                }
            }
            Button(onClick = viewModel::loadSeries, modifier = Modifier.fillMaxWidth()) {
                Text("Tracer la courbe")
            }
        }

        state.seriesError?.let { message ->
            InlineError(message) { viewModel.loadSeries() }
        }

        SectionCard(title = "Courbe") {
            when {
                state.loadingSeries -> LoadingPane("Lecture des mesures…")
                state.points.isEmpty() -> EmptyPane(
                    "Aucune mesure pour « ${state.field} » dans « ${state.database} ». " +
                        "Essayez un autre champ.",
                )
                else -> {
                    LineChart(state.points)
                    Spacer(Modifier.height(8.dp))
                    InfoRow("Période", "${formatDateTimeShort(state.points.first().first)} → ${formatDateTimeShort(state.points.last().first)}")
                    InfoRow("Mesures", state.points.size.toString())
                    InfoRow("Minimum", formatValue(state.database, state.points.minOf { it.second }))
                    InfoRow("Maximum", formatValue(state.database, state.points.maxOf { it.second }))
                    InfoRow("Dernière valeur", formatValue(state.database, state.points.last().second))
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Les séries sont lues en une fois auprès du service RRD (base + champ + période). " +
                "Les noms de champs varient selon le modèle : en cas d'erreur, essayez-en un autre.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
    }
}

private fun formatValue(database: String, value: Double): String = when (database) {
    "net", "switch", "dsl" -> formatBytes(value.toLong().coerceAtLeast(0L)) + "/s"
    "temp" -> "%.1f °C".format(value)
    else -> "%.2f".format(value)
}
