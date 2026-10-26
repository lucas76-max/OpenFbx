package fr.openfbx.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.openfbx.core.AppGraph
import fr.openfbx.core.data.dto.DownloadTask
import fr.openfbx.core.net.userMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DownloadsUiState(
    val loading: Boolean = true,
    val tasks: List<DownloadTask> = emptyList(),
    val error: String? = null,
    val message: String? = null,
    val busy: Boolean = false,
    val uploading: Boolean = false,
    val uploadingName: String? = null,
) {
    val runningCount: Int get() = tasks.count { it.isRunning }
    val queuedCount: Int get() = tasks.count { it.status == "queued" || it.isPaused }
    val rateDown: Long get() = tasks.sumOf { it.rxRate ?: 0L }
    val rateUp: Long get() = tasks.sumOf { it.txRate ?: 0L }
    val uploaded: Long get() = tasks.sumOf { it.txBytes ?: 0L }
}

class DownloadsViewModel : ViewModel() {

    private val repository = AppGraph.repository
    private val _state = MutableStateFlow(DownloadsUiState())
    val state: StateFlow<DownloadsUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        refresh()
    }

    fun refresh(silent: Boolean = false) {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            if (!silent) _state.update { it.copy(loading = it.tasks.isEmpty(), error = null) }
            runCatching { repository.downloads() }
                .onSuccess { tasks ->
                    _state.update { it.copy(loading = false, tasks = sort(tasks), error = null) }
                }
                .onFailure { error ->
                    if (!silent) _state.update { it.copy(loading = false, error = error.userMessage()) }
                }
        }
    }

    fun add(url: String, directory: String?) {
        if (url.isBlank()) return
        mutate("Téléchargement ajouté à la file.") { repository.addDownload(url, directory) }
    }

    fun addTorrent(fileName: String, content: ByteArray, directory: String?) {
        if (_state.value.busy || _state.value.uploading) return
        _state.update { it.copy(uploading = true, uploadingName = fileName, error = null, message = null) }
        viewModelScope.launch {
            runCatching { repository.addTorrent(fileName, content, directory) }
                .onSuccess {
                    _state.update {
                        it.copy(uploading = false, uploadingName = null, message = "Torrent « $fileName » ajouté.")
                    }
                    refresh(silent = true)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(uploading = false, uploadingName = null, error = error.userMessage())
                    }
                }
        }
    }

    fun pause(task: DownloadTask) = mutate("Téléchargement mis en pause.") {
        repository.setDownloadStatus(task.id, "stopped")
    }

    fun resume(task: DownloadTask) = mutate("Téléchargement relancé.") {
        repository.setDownloadStatus(task.id, "downloading")
    }

    fun retry(task: DownloadTask) = mutate("Nouvelle tentative demandée.") {
        repository.setDownloadStatus(task.id, "retry")
    }

    fun remove(task: DownloadTask, eraseFiles: Boolean) = mutate(
        if (eraseFiles) "Tâche et fichiers supprimés." else "Tâche retirée de la file.",
    ) {
        repository.removeDownload(task.id, eraseFiles)
    }

    private fun mutate(successMessage: String, block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null, error = null) }
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess {
                    _state.update { it.copy(busy = false, message = successMessage) }
                    refresh(silent = true)
                }
                .onFailure { error ->
                    _state.update { it.copy(busy = false, error = error.userMessage()) }
                }
        }
    }

    /** Les tâches actives d'abord, les terminées en dernier. */
    private fun sort(tasks: List<DownloadTask>): List<DownloadTask> = tasks.sortedWith(
        compareBy<DownloadTask> { priority(it) }.thenByDescending { it.createdTs ?: 0L },
    )

    private fun priority(task: DownloadTask): Int = when {
        task.isFailed -> 0
        task.isRunning -> 1
        task.status == "queued" -> 2
        task.isPaused -> 3
        task.isFinished -> 5
        else -> 4
    }
}
