package fr.openfbx.ui.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.openfbx.core.AppGraph
import fr.openfbx.core.data.dto.FileEntry
import fr.openfbx.core.model.childFsPath
import fr.openfbx.core.model.encodeFsPath
import fr.openfbx.core.model.normalizeFsPath
import fr.openfbx.core.net.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FilesUiState(
    val loading: Boolean = true,
    /** Chemin lisible du dossier affiché ; vide pour la racine. */
    val path: String = "",
    val crumbs: List<String> = emptyList(),
    val entries: List<FileEntry> = emptyList(),
    val error: String? = null,
    val message: String? = null,
    val busy: Boolean = false,
    val uploading: Boolean = false,
    val uploadProgress: Float? = null,
    val uploadingName: String? = null,
) {
    val isRoot: Boolean get() = path.isBlank()
}

class FilesViewModel : ViewModel() {

    private val repository = AppGraph.repository
    private val _state = MutableStateFlow(FilesUiState())
    val state: StateFlow<FilesUiState> = _state.asStateFlow()

    init {
        open("")
    }

    fun open(path: String) {
        val normalized = normalizeFsPath(path)
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, path = normalized) }
            runCatching { repository.files(normalized) }
                .onSuccess { entries ->
                    _state.update {
                        it.copy(
                            loading = false,
                            entries = sort(entries),
                            crumbs = normalized.split('/').filter(String::isNotBlank),
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(loading = false, error = error.userMessage()) }
                }
        }
    }

    fun openEntry(entry: FileEntry) {
        if (!entry.isDir) return
        open(childFsPath(_state.value.path, entry.displayName))
    }

    fun up() {
        open(normalizeFsPath(_state.value.path).substringBeforeLast('/', ""))
    }

    fun refresh() = open(_state.value.path)

    fun createFolder(name: String) {
        if (name.isBlank()) return
        mutate("Dossier créé.") { repository.createFolder(_state.value.path, name.trim()) }
    }

    /** Crée un fichier (éventuellement vide) via l'upload WebSocket. */
    fun createFile(name: String, text: String) {
        if (name.isBlank()) return
        upload(name.trim(), text.toByteArray(Charsets.UTF_8))
    }

    fun upload(name: String, content: ByteArray) {
        if (name.isBlank()) return
        val directory = _state.value.path
        _state.update {
            it.copy(uploading = true, uploadingName = name, uploadProgress = 0f, message = null, error = null)
        }
        viewModelScope.launch {
            runCatching {
                repository.uploadFile(directory, name, content) { progress ->
                    _state.update { it.copy(uploadProgress = progress) }
                }
            }.onSuccess {
                _state.update {
                    it.copy(uploading = false, uploadProgress = null, uploadingName = null, message = "« $name » envoyé.")
                }
                refresh()
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        uploading = false,
                        uploadProgress = null,
                        uploadingName = null,
                        error = error.userMessage(),
                    )
                }
            }
        }
    }

    fun delete(entry: FileEntry) {
        mutate("Élément supprimé.") {
            repository.deleteFiles(listOf(entryPath(entry)))
        }
    }

    fun rename(entry: FileEntry, newName: String) {
        if (newName.isBlank()) return
        mutate("Élément renommé.") {
            repository.renameFile(entryPath(entry), newName.trim())
        }
    }

    /** Chemin encodé attendu par les actions de la box. */
    private fun entryPath(entry: FileEntry): String =
        entry.path ?: encodeFsPath(childFsPath(_state.value.path, entry.displayName))

    private fun mutate(successMessage: String, block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null, error = null) }
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess {
                    _state.update { it.copy(busy = false, message = successMessage) }
                    refresh()
                }
                .onFailure { error ->
                    _state.update { it.copy(busy = false, error = error.userMessage()) }
                }
        }
    }

    private fun sort(entries: List<FileEntry>): List<FileEntry> = entries.sortedWith(
        compareBy<FileEntry> { if (it.isDir) 0 else 1 }
            .thenBy { it.displayName.lowercase() },
    )
}
