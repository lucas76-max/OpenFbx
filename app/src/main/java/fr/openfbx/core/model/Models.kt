package fr.openfbx.core.model

/**
 * Connexion enregistrée vers une Freebox.
 *
 * L'URL de base de l'API est toujours dérivée de la découverte (`/api_version`) :
 * `https://{host}:{https_port}{api_base_url}v{major}/`.
 */
data class BoxConfig(
    val host: String,
    val httpsPort: Int = 443,
    val apiBaseUrl: String = "/api/",
    val apiMajor: Int = 4,
    val apiDomain: String? = null,
    val boxModel: String? = null,
    val boxModelName: String? = null,
    val deviceName: String? = null,
    val uid: String? = null,
    val apiVersion: String? = null,
    val certFingerprint: String? = null,
) {
    val baseUrl: String
        get() = "https://$host:$httpsPort${apiBaseUrl.normalizedApiBase()}v$apiMajor/"

    val displayName: String
        get() = boxModelName ?: boxModel ?: deviceName ?: host
}

fun String.normalizedApiBase(): String {
    val trimmed = trim()
    if (trimmed.isEmpty()) return "/api/"
    val withLeading = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
    return if (withLeading.endsWith("/")) withLeading else "$withLeading/"
}

enum class ThemeMode(val label: String) {
    SYSTEM("Système"),
    LIGHT("Clair"),
    DARK("Sombre"),
    OLED("OLED"),
}

/**
 * Couleur d'accent utilisée quand les couleurs dynamiques sont désactivées.
 * Les valeurs exactes (et leurs variantes clair / sombre / OLED) sont définies
 * dans `ui/theme`.
 */
enum class AccentPalette(val label: String) {
    VIOLET("Violet"),
    INDIGO("Indigo"),
    BLEU("Bleu"),
    TURQUOISE("Turquoise"),
    VERT("Vert"),
    AMBRE("Ambre"),
    ROSE("Rose"),
    ROUGE("Rouge"),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentPalette = AccentPalette.VIOLET,
    val dynamicColor: Boolean = true,
    val pollSeconds: Int = 3,
)

/** Résultat d'une découverte `/api_version`. */
data class DiscoveredBox(
    val host: String,
    val httpsPort: Int,
    val apiBaseUrl: String,
    val apiVersion: String,
    val apiDomain: String?,
    val boxModel: String?,
    val boxModelName: String?,
    val deviceName: String?,
    val uid: String?,
    val httpsAvailable: Boolean,
) {
    val apiMajor: Int
        get() = apiVersion.substringBefore('.').toIntOrNull() ?: 4

    fun toConfig(certFingerprint: String? = null) = BoxConfig(
        host = host,
        httpsPort = httpsPort,
        apiBaseUrl = apiBaseUrl.normalizedApiBase(),
        apiMajor = apiMajor,
        apiDomain = apiDomain,
        boxModel = boxModel,
        boxModelName = boxModelName,
        deviceName = deviceName,
        uid = uid,
        apiVersion = apiVersion,
        certFingerprint = certFingerprint,
    )
}
