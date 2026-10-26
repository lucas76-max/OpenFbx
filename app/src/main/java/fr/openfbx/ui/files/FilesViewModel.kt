package fr.openfbx.ui.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.openfbx.core.AppGraph
import fr.openfbx.core.data.childFsPath
import fr.openfbx.core.data.dto.FileEntry
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
        val normalized = path.trim().trim('/')
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
        open(childPath(entry))
    }

    fun up() {
        val parent = _state.value.path.substringBeforeLast('/', "")
        open(parent)
    }

    fun refresh() = open(_state.value.path)

    fun createFolder(name: String) {
        if (name.isBlank()) return
        mutate("Dossier créé.") { repository.createFolder(_state.value.path, name.trim()) }
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

    /** Chemin enfant lisible, utilisé quand la box ne renvoie pas `path`. */
    private fun childPath(entry: FileEntry): String {
        val parent = _state.value.path
        return listOf(parent, entry.displayName)
            .filter { it.isNotBlank() }
            .joinToString("/")
    }

    /** Chemin encodé attendu par les actions de la box. */
    private fun entryPath(entry: FileEntry): String =
        entry.path ?: childFsPath(_state.value.path, entry.displayName)

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
