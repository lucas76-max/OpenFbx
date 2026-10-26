package fr.openfbx.core.log

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Journal interne de l'application.
 *
 * Conserve les derniers événements en mémoire (aucun fichier, aucun envoi) pour
 * pouvoir les copier ou les exporter depuis l'écran Journaux. Les secrets
 * (jetons, challenge, empreinte complète des requêtes) ne sont jamais écrits.
 */
object AppLog {

    enum class Level { DEBUG, INFO, WARN, ERROR }

    data class Entry(
        val time: Long,
        val level: Level,
        val tag: String,
        val message: String,
    )

    private const val MAX_ENTRIES = 800
    private val stamp = SimpleDateFormat("dd/MM HH:mm:ss.SSS", Locale.FRANCE)

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun log(level: Level, tag: String, message: String) {
        val entry = Entry(System.currentTimeMillis(), level, tag, message)
        _entries.update { current ->
            (current + entry).let { if (it.size > MAX_ENTRIES) it.takeLast(MAX_ENTRIES) else it }
        }
    }

    fun d(tag: String, message: String) = log(Level.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(Level.INFO, tag, message)
    fun w(tag: String, message: String) = log(Level.WARN, tag, message)
    fun e(tag: String, message: String) = log(Level.ERROR, tag, message)

    fun clear() = _entries.update { emptyList() }

    /** Texte prêt à coller, du plus ancien au plus récent. */
    fun asText(): String = buildString {
        appendLine("OpenFbx — journal (${_entries.value.size} entrées)")
        appendLine("Généré le ${stamp.format(Date())}")
        appendLine()
        _entries.value.forEach { entry ->
            append(stamp.format(Date(entry.time)))
            append(' ')
            append(entry.level.name.padEnd(5))
            append(" [")
            append(entry.tag)
            append("] ")
            appendLine(entry.message)
        }
    }

    fun formatted(entry: Entry): String {
        val level = when (entry.level) {
            Level.DEBUG -> "D"
            Level.INFO -> "I"
            Level.WARN -> "W"
            Level.ERROR -> "E"
        }
        return "${stamp.format(Date(entry.time))} $level ${entry.tag} · ${entry.message}"
    }
}
