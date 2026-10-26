package fr.openfbx.ui.files

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.openfbx.core.data.dto.FileEntry
import fr.openfbx.ui.components.EmptyPane
import fr.openfbx.ui.components.InlineError
import fr.openfbx.ui.components.InlineNotice
import fr.openfbx.ui.components.LoadingPane
import fr.openfbx.ui.components.PillTone
import fr.openfbx.ui.components.ScreenScaffold
import fr.openfbx.ui.components.SectionCard
import fr.openfbx.ui.components.StatusPill
import fr.openfbx.ui.formatBytes
import fr.openfbx.ui.formatDateShort

@Composable
fun FilesScreen(viewModel: FilesViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var newFolderDialog by rememberSaveable { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<FileEntry?>(null) }

    ScreenScaffold(
        title = "Fichiers",
        onRefresh = viewModel::refresh,
        actions = {
            IconButton(onClick = { newFolderDialog = true }, enabled = !state.busy) {
                Icon(Icons.Filled.CreateNewFolder, contentDescription = "Nouveau dossier")
            }
        },
    ) {
        state.message?.let { InlineNotice(it) }
        state.error?.let { InlineError(it) { viewModel.refresh() } }

        Breadcrumbs(
            crumbs = state.crumbs,
            onOpen = { index -> viewModel.open(state.crumbs.take(index + 1).joinToString("/")) },
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!state.isRoot) {
                OutlinedButton(onClick = viewModel::up) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Parent")
                }
            }
            Text(
                text = "${state.entries.size} élément(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.loading) {
            LoadingPane("Lecture du dossier…")
            return@ScreenScaffold
        }

        if (state.entries.isEmpty()) {
            EmptyPane("Ce dossier est vide.")
            return@ScreenScaffold
        }

        SectionCard {
            state.entries.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider()
                FileRow(
                    entry = entry,
                    enabled = !state.busy,
                    onOpen = { viewModel.openEntry(entry) },
                    onRename = { renameTarget = entry },
                    onDelete = { deleteTarget = entry },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "L'explorateur agit sur les disques connectés à la Freebox. Un dossier supprimé " +
                "part avec son contenu : rien n'est récupérable depuis l'application.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
    }

    if (newFolderDialog) {
        NameDialog(
            title = "Nouveau dossier",
            label = "Nom du dossier",
            initial = "",
            confirmLabel = "Créer",
            onDismiss = { newFolderDialog = false },
            onConfirm = { name ->
                newFolderDialog = false
                viewModel.createFolder(name)
            },
        )
    }

    renameTarget?.let { entry ->
        NameDialog(
            title = "Renommer",
            label = "Nouveau nom",
            initial = entry.displayName,
            confirmLabel = "Renommer",
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                renameTarget = null
                viewModel.rename(entry, name)
            },
        )
    }

    deleteTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(if (entry.isDir) "Supprimer ce dossier ?" else "Supprimer ce fichier ?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(entry.displayName, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (entry.isDir) {
                            "Le dossier et tout son contenu seront effacés définitivement du disque de la Freebox."
                        } else {
                            "Le fichier sera effacé définitivement du disque de la Freebox."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    viewModel.delete(entry)
                }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun Breadcrumbs(crumbs: List<String>, onOpen: (Int) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { onOpen(-1) }) { Text("Racine") }
        crumbs.forEachIndexed { index, label ->
            Text("/", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { onOpen(index) }) {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun FileRow(
    entry: FileEntry,
    enabled: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = entry.isDir && enabled, onClick = onOpen)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (entry.isDir) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
            contentDescription = null,
            tint = if (entry.isDir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.displayName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (entry.isDir) "Dossier" else listOfNotNull(
                    formatBytes(entry.size),
                    formatDateShort(entry.mtime),
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (entry.isDir) {
            StatusPill(text = "ouvrir", tone = PillTone.NEUTRAL)
            Spacer(Modifier.width(4.dp))
        }

        Column {
            IconButton(onClick = { menuOpen = true }, enabled = enabled) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Actions")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Renommer") },
                    onClick = {
                        menuOpen = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Supprimer") },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    label: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.trim()) }, enabled = value.isNotBlank()) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
