package fr.openfbx.ui.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import fr.openfbx.ui.formatTimestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_UPLOAD_BYTES = 128 * 1024 * 1024

@Composable
fun FilesScreen(viewModel: FilesViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var newFolderDialog by rememberSaveable { mutableStateOf(false) }
    var newFileDialog by rememberSaveable { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<FileEntry?>(null) }
    var detailTarget by remember { mutableStateOf<FileEntry?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }

    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = displayName(context, uri) ?: "fichier"
            val bytes = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            when {
                bytes == null -> localError = "Impossible de lire « $name »."
                bytes.size > MAX_UPLOAD_BYTES -> localError =
                    "« $name » dépasse 128 Mo : envoyez-le depuis un ordinateur sur le disque de la Freebox."
                bytes.isEmpty() -> viewModel.createFile(name, "")
                else -> viewModel.upload(name, bytes)
            }
        }
    }

    ScreenScaffold(
        title = "Fichiers",
        onRefresh = viewModel::refresh,
        actions = {
            IconButton(onClick = { newFileDialog = true }, enabled = !state.busy && !state.uploading) {
                Icon(Icons.Filled.NoteAdd, contentDescription = "Nouveau fichier")
            }
            IconButton(onClick = { newFolderDialog = true }, enabled = !state.busy && !state.uploading) {
                Icon(Icons.Filled.CreateNewFolder, contentDescription = "Nouveau dossier")
            }
            IconButton(
                onClick = { uploadLauncher.launch(arrayOf("*/*")) },
                enabled = !state.busy && !state.uploading,
            ) {
                Icon(Icons.Filled.Upload, contentDescription = "Envoyer un fichier")
            }
        },
    ) {
        state.message?.let { InlineNotice(it) }
        state.error?.let { InlineError(it) { viewModel.refresh() } }
        localError?.let { InlineError(it) { localError = null } }

        if (state.uploading) {
            SectionCard(title = "Envoi en cours") {
                Text(
                    text = state.uploadingName ?: "fichier",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LinearProgressIndicator(
                    progress = { state.uploadProgress ?: 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "${((state.uploadProgress ?: 0f) * 100).toInt()} %",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Breadcrumbs(
            path = state.path,
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
                    enabled = !state.busy && !state.uploading,
                    onOpen = {
                        if (entry.isDir) viewModel.openEntry(entry) else detailTarget = entry
                    },
                    onDetails = { detailTarget = entry },
                    onRename = { renameTarget = entry },
                    onDelete = { deleteTarget = entry },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Touchez un dossier pour l'ouvrir, un fichier pour sa fiche. Les envois passent par le " +
                "WebSocket d'upload de la Freebox et écrasent un fichier de même nom.",
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

    if (newFileDialog) {
        NewFileDialog(
            onDismiss = { newFileDialog = false },
            onConfirm = { name, content ->
                newFileDialog = false
                viewModel.createFile(name, content)
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

    detailTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { detailTarget = null },
            title = { Text(entry.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (entry.isDir) "Dossier" else "Fichier",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!entry.isDir) {
                        Text("Taille : ${formatBytes(entry.size)}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        "Modifié le ${formatTimestamp(entry.mtime?.let { if (it > 10_000_000_000L) it / 1000 else it })}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { detailTarget = null }) { Text("Fermer") } },
            dismissButton = {
                TextButton(onClick = {
                    renameTarget = entry
                    detailTarget = null
                }) { Text("Renommer") }
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
private fun Breadcrumbs(path: String, crumbs: List<String>, onOpen: (Int) -> Unit) {
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
        if (path.isNotBlank() && crumbs.isEmpty()) {
            Text(path, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FileRow(
    entry: FileEntry,
    enabled: Boolean,
    onOpen: () -> Unit,
    onDetails: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onOpen)
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
                    formatDateShort(entry.mtime?.let { if (it > 10_000_000_000L) it / 1000 else it }),
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
                    text = { Text("Détails") },
                    onClick = {
                        menuOpen = false
                        onDetails()
                    },
                )
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

@Composable
private fun NewFileDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouveau fichier") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom (ex. notes.txt)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Contenu (facultatif)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Le fichier est créé dans le dossier affiché.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim(), content) }, enabled = name.isNotBlank()) {
                Text("Créer")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Nom lisible d'un document choisi dans le sélecteur Android. */
private fun displayName(context: Context, uri: Uri): String? {
    val fromProvider = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull()
    return fromProvider ?: uri.lastPathSegment?.substringAfterLast('/')
}
