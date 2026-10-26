package fr.openfbx.core.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Une tâche de téléchargement Freebox (`GET /downloads/`).
 *
 * Les états observés selon les firmwares : active, downloading, seeding, queued,
 * stopped, checking, extracting, repairing, stopping, done, error.
 */
@Serializable
data class DownloadTask(
    val id: Int = 0,
    val status: String? = null,
    val name: String? = null,
    val size: Long? = null,
    @SerialName("rx_bytes") val rxBytes: Long? = null,
    @SerialName("rx_rate") val rxRate: Long? = null,
    @SerialName("tx_bytes") val txBytes: Long? = null,
    @SerialName("tx_rate") val txRate: Long? = null,
    val eta: Long? = null,
    @SerialName("queue_pos") val queuePos: Int? = null,
    @SerialName("created_ts") val createdTs: Long? = null,
    val error: String? = null,
    val type: String? = null,
    @SerialName("rx_pct") val rxPct: Int? = null,
    @SerialName("info_hash") val infoHash: String? = null,
) {
    val isPaused: Boolean get() = status == "stopped"
    val isFailed: Boolean get() = status == "error"
    val isFinished: Boolean get() = status == "done"
    val isRunning: Boolean
        get() = status in setOf("active", "downloading", "seeding", "checking", "extracting", "repairing", "stopping")

    /** Avancement entre 0 et 1, ou `null` quand la taille n'est pas encore connue. */
    val progress: Float?
        get() {
            val total = size ?: 0L
            val done = rxBytes ?: 0L
            if (total > 0L) return (done.toDouble() / total).toFloat().coerceIn(0f, 1f)
            return rxPct?.let { (it / 100.0).toFloat().coerceIn(0f, 1f) }
        }
}

/** Une entrée de dossier renvoyée par `GET /fs/ls/{path}`. */
@Serializable
data class FileEntry(
    val name: String? = null,
    /** Chemin encodé en base64, tel que renvoyé par la box. */
    val path: String? = null,
    val size: Long? = null,
    /** `dir` ou `file`. */
    val type: String? = null,
    val mtime: Long? = null,
    val hidden: Boolean? = null,
    val link: String? = null,
) {
    val isDir: Boolean get() = type == "dir"
    val displayName: String get() = name ?: path ?: "—"
}
