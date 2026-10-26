package fr.openfbx.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.openfbx.core.AppGraph
import fr.openfbx.core.data.dto.FileEntry
import fr.openfbx.core.model.childFsPath
import fr.openfbx.core.model.normalizeFsPath
import fr.openfbx.core.net.userMessage
import kotlinx.coroutines.launch

/**
 * Sélecteur de dossier de la Freebox, réutilisé par les téléchargements et
 * l'explorateur (destination d'un envoi).
 */
@Composable
fun FolderPickerDialog(
    initialPath: String = "",
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var path by remember { mutableStateOf(normalizeFsPath(initialPath)) }
    var entries by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load(target: String) {
        path = target
        loading = true
        error = null
        scope.launch {
            runCatching { AppGraph.repository.files(target) }
                .onSuccess { all ->
                    entries = all.filter { it.isDir }.sortedBy { it.displayName.lowercase() }
                    loading = false
                }
                .onFailure {
                    entries = emptyList()
                    loading = false
                    error = it.userMessage()
                }
        }
    }

    LaunchedEffect(Unit) { load(normalizeFsPath(initialPath)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dossier de destination") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = path.ifBlank { "/ (racine)" },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (path.isNotBlank()) {
                        IconButton(onClick = { load(parentOf(path)) }) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = "Dossier parent")
                        }
                    }
                }
                HorizontalDivider()

                when {
                    loading -> Text("Lecture…", style = MaterialTheme.typography.bodySmall)
                    error != null -> Text(
                        error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    entries.isEmpty() -> Text(
                        "Aucun sous-dossier ici.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> Column(
                        Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                    ) {
                        entries.forEach { entry ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        load(childFsPath(path, entry.displayName))
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Filled.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    entry.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(path) }) {
                Text(if (path.isBlank()) "Utiliser la racine" else "Choisir ce dossier")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

private fun parentOf(path: String): String = normalizeFsPath(path).substringBeforeLast('/', "")
