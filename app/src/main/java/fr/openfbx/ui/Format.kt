package fr.openfbx.ui

import fr.openfbx.core.data.dto.SystemInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FR = Locale.FRANCE

fun formatBytes(value: Long?): String {
    if (value == null) return "—"
    if (value < 1024) return "$value o"
    val units = listOf("ko", "Mo", "Go", "To")
    var size = value.toDouble()
    var index = -1
    while (size >= 1024 && index < units.lastIndex) {
        size /= 1024
        index++
    }
    return String.format(FR, "%.1f %s", size, units[index])
}

fun formatRate(bytesPerSecond: Long?): String {
    if (bytesPerSecond == null) return "—"
    return formatBytes(bytesPerSecond) + "/s"
}

fun formatUptime(seconds: Long?): String {
    if (seconds == null || seconds <= 0) return "—"
    val days = seconds / 86_400
    val hours = (seconds % 86_400) / 3_600
    val minutes = (seconds % 3_600) / 60
    return when {
        days > 0 -> "$days j ${hours} h"
        hours > 0 -> "$hours h ${minutes} min"
        else -> "$minutes min"
    }
}

/**
 * Débit d'une liaison exprimé en **bits par seconde**, l'unité utilisée par
 * Freebox OS pour `bandwidth_down`, `bandwidth_up` et les débits xDSL.
 *
 * L'ancien affichage prenait ces bits pour des mégabits : une ligne à
 * 5 000 000 bit/s (5 Mbit/s) s'affichait « 5000 Gbit/s ». On convertit
 * maintenant explicitement.
 */
fun formatLinkSpeed(bitsPerSecond: Long?): String {
    if (bitsPerSecond == null || bitsPerSecond <= 0) return "—"
    val bits = bitsPerSecond.toDouble()
    return when {
        bitsPerSecond >= 1_000_000_000 -> String.format(FR, "%.2f Gbit/s", bits / 1_000_000_000)
        bitsPerSecond >= 1_000_000 -> String.format(FR, "%.0f Mbit/s", bits / 1_000_000)
        bitsPerSecond >= 1_000 -> String.format(FR, "%.0f kbit/s", bits / 1_000)
        else -> "$bitsPerSecond bit/s"
    }
}

/** Durée restante d'un téléchargement, en secondes. */
fun formatEta(seconds: Long?): String {
    if (seconds == null || seconds <= 0) return "—"
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    val secs = seconds % 60
    return when {
        hours > 0 -> String.format(FR, "%d h %02d", hours, minutes)
        minutes > 0 -> String.format(FR, "%d min %02d s", minutes, secs)
        else -> "$secs s"
    }
}

fun formatTimestamp(epochSeconds: Long?): String {
    if (epochSeconds == null || epochSeconds <= 0) return "—"
    val format = SimpleDateFormat("dd/MM/yyyy HH:mm", FR)
    return format.format(Date(epochSeconds * 1000))
}

fun formatTimeOfDay(epochSeconds: Long?): String {
    if (epochSeconds == null || epochSeconds <= 0) return "—"
    return SimpleDateFormat("HH:mm", FR).format(Date(epochSeconds * 1000))
}

fun formatDateTimeShort(epochSeconds: Long?): String {
    if (epochSeconds == null || epochSeconds <= 0) return "—"
    return SimpleDateFormat("dd/MM HH:mm", FR).format(Date(epochSeconds * 1000))
}

fun formatDateShort(epochSeconds: Long?): String {
    if (epochSeconds == null || epochSeconds <= 0) return "—"
    return SimpleDateFormat("dd/MM/yyyy", FR).format(Date(epochSeconds * 1000))
}

fun formatTemperature(celsius: Double?): String =
    if (celsius == null || celsius <= 0.0) "—" else String.format(FR, "%.1f °C", celsius)

/**
 * Température CPU : champ hérité `temp_cpub`, puis `temp_cpum`, puis capteur
 * nommé des firmwares récents (l'ancien code n'affichait rien quand `temp_cpub`
 * manquait).
 */
fun SystemInfo.cpuTemperature(): Double? =
    tempCpuB ?: tempCpuM
        ?: sensors.firstOrNull {
            val label = "${it.id.orEmpty()} ${it.name.orEmpty()}".lowercase(Locale.ROOT)
            label.contains("cpu")
        }?.value

fun formatChannel(channel: Int?, width: Int?): String {
    if (channel == null) return "—"
    return if (width != null && width > 0) "canal $channel · ${width} MHz" else "canal $channel"
}

fun formatSignal(dbm: Int?): String = when {
    dbm == null -> "—"
    else -> "$dbm dBm"
}

/** Libellé français d'un état de téléchargement Freebox. */
fun downloadStatusLabel(status: String?): String = when (status?.lowercase(Locale.ROOT)) {
    "active", "downloading" -> "en cours"
    "seeding" -> "partage"
    "queued" -> "en attente"
    "stopped" -> "en pause"
    "done" -> "terminé"
    "checking" -> "vérification"
    "extracting" -> "extraction"
    "repairing" -> "réparation"
    "stopping" -> "arrêt"
    "error" -> "erreur"
    else -> status ?: "inconnu"
}

/** Traduit les noms de droits renvoyés par la Freebox. */
fun permissionLabel(key: String): String = when (key) {
    "settings" -> "Modification des réglages"
    "contacts" -> "Contacts"
    "explorer" -> "Explorateur de fichiers"
    "download" -> "Téléchargements"
    "tv" -> "Télévision"
    "parental" -> "Contrôle parental"
    "pvr" -> "Enregistrements"
    "home" -> "Maison connectée et alarme"
    "vm" -> "Machines virtuelles"
    "camera" -> "Caméras"
    "airmedia" -> "AirMedia"
    "upnp" -> "UPnP"
    else -> key.replaceFirstChar { it.titlecase(FR) }
}

fun humanMedia(media: String?): String = when (media?.lowercase(Locale.ROOT)) {
    "ftth" -> "Fibre (FTTH)"
    "ethernet" -> "Ethernet"
    "xdsl" -> "ADSL / VDSL"
    "vpn" -> "VPN"
    else -> media ?: "—"
}

fun shortList(values: List<String>, limit: Int = 3): String =
    if (values.isEmpty()) "—" else values.take(limit).joinToString(", ")
