package fr.openfbx.ui.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.openfbx.core.data.dto.DownloadTask
import fr.openfbx.ui.components.EmptyPane
import fr.openfbx.ui.components.InlineError
import fr.openfbx.ui.components.InlineNotice
import fr.openfbx.ui.components.LoadingPane
import fr.openfbx.ui.components.PillTone
import fr.openfbx.ui.components.ScreenScaffold
import fr.openfbx.ui.components.StatTile
import fr.openfbx.ui.components.StatusPill
import fr.openfbx.ui.downloadStatusLabel
import fr.openfbx.ui.formatBytes
import fr.openfbx.ui.formatEta
import fr.openfbx.ui.formatRate
import kotlinx.coroutines.delay

@Composable
fun DownloadsScreen(viewModel: DownloadsViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var addDialog by rememberSaveable { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<DownloadTask?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(3_000)
            viewModel.refresh(silent = true)
        }
    }

    ScreenScaffold(title = "Téléchargements", onRefresh = { viewModel.refresh() }) {
        state.message?.let { InlineNotice(it) }
        state.error?.let { InlineError(it) { viewModel.refresh() } }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                label = "En cours",
                value = state.runningCount.toString(),
                caption = "tâches actives",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "En attente",
                value = state.queuedCount.toString(),
                caption = "file ou pause",
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                label = "Débit descendant",
                value = formatRate(state.rateDown),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Débit montant",
                value = formatRate(state.rateUp),
                modifier = Modifier.weight(1f),
            )
        }

        Button(onClick = { addDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Ajouter un lien ou un torrent")
        }

        if (state.loading) {
            LoadingPane("Lecture de la file de téléchargement…")
            return@ScreenScaffold
        }

        if (state.tasks.isEmpty()) {
            EmptyPane("Aucun téléchargement dans la file de la Freebox.")
        }

        state.tasks.forEach { task ->
            DownloadCard(
                task = task,
                busy = state.busy,
                onPause = { viewModel.pause(task) },
                onResume = { viewModel.resume(task) },
                onRetry = { viewModel.retry(task) },
                onRemove = { removeTarget = task },
            )
        }

        Spacer(Modifier.height(16.dp))
    }

    if (addDialog) {
        AddDownloadDialog(
            onDismiss = { addDialog = false },
            onConfirm = { url ->
                addDialog = false
                viewModel.add(url)
            },
        )
    }

    removeTarget?.let { task ->
        RemoveDownloadDialog(
            task = task,
            onDismiss = { removeTarget = null },
            onRemove = { eraseFiles ->
                removeTarget = null
                viewModel.remove(task, eraseFiles)
            },
        )
    }
}

@Composable
private fun DownloadCard(
    task: DownloadTask,
    busy: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = task.name ?: "Téléchargement ${task.id}",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOfNotNull(
                            task.type?.uppercase(),
                            task.queuePos?.takeIf { it > 0 }?.let { "position $it" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                StatusPill(text = downloadStatusLabel(task.status), tone = statusTone(task))
            }

            val progress = task.progress
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "${(progress * 100).toInt()} % · ${formatBytes(task.rxBytes)} / ${formatBytes(task.size)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = "Taille en cours d'estimation",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val details = buildList {
                if ((task.rxRate ?: 0L) > 0) add("${formatRate(task.rxRate)} ↓")
                if ((task.txRate ?: 0L) > 0) add("${formatRate(task.txRate)} ↑")
                if ((task.eta ?: 0L) > 0) add("reste ${formatEta(task.eta)}")
                task.error?.takeIf { it.isNotBlank() }?.let { add(it) }
            }
            if (details.isNotEmpty()) {
                Text(
                    text = details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (task.isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                val active = task.isRunning && !task.isPaused
                if (active) {
                    IconButton(onClick = onPause, enabled = !busy) {
                        Icon(Icons.Filled.Pause, contentDescription = "Mettre en pause")
                    }
                } else if (!task.isFinished) {
                    IconButton(onClick = onResume, enabled = !busy) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Relancer")
                    }
                }
                if (task.isFailed) {
                    IconButton(onClick = onRetry, enabled = !busy) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Réessayer")
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRemove, enabled = !busy) {
                    Icon(Icons.Filled.Delete, contentDescription = "Retirer le téléchargement")
                }
            }
        }
    }
}

private fun statusTone(task: DownloadTask): PillTone = when {
    task.isFailed -> PillTone.NEGATIVE
    task.isFinished -> PillTone.POSITIVE
    task.isRunning -> PillTone.POSITIVE
    else -> PillTone.NEUTRAL
}

@Composable
private fun AddDownloadDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter un téléchargement") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Collez un lien HTTP, FTP, un lien magnet ou une adresse de fichier .torrent. " +
                        "Le téléchargement est effectué par la Freebox, même application fermée.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Lien") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(url.trim()) }, enabled = url.isNotBlank()) {
                Text("Ajouter")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun RemoveDownloadDialog(
    task: DownloadTask,
    onDismiss: () -> Unit,
    onRemove: (Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Retirer ce téléchargement ?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    task.name ?: "Téléchargement ${task.id}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Retirer la tâche laisse les fichiers sur le disque de la Freebox. " +
                        "Les supprimer les efface définitivement.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { onRemove(false) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Retirer la tâche") }
                OutlinedButton(
                    onClick = { onRemove(true) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Supprimer les fichiers") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
