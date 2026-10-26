package fr.openfbx.core.model

import java.util.Base64

/**
 * Encodage des chemins du système de fichiers Freebox.
 *
 * L'API attend le chemin **complet, avec son `/` initial**, encodé en base64
 * (`/Disque dur` → `L0Rpc3F1ZSBkdXI=`). L'ancienne version encodait seulement
 * `Disque dur`, ce qui donnait un chemin introuvable côté box : impossible
 * d'ouvrir un dossier.
 */
fun encodeFsPath(path: String): String {
    val normalized = normalizeFsPath(path)
    if (normalized.isEmpty()) return ""
    return base64(normalized)
}

/** Comme [encodeFsPath], mais avec le `/` final attendu par `download_dir`. */
fun encodeFsDir(path: String): String {
    val normalized = normalizeFsPath(path)
    val withSlash = if (normalized.isEmpty()) "/" else "$normalized/"
    return base64(withSlash)
}

/** Chemin lisible enfant (`parent` + `/` + `name`), normalisé. */
fun childFsPath(parent: String, name: String): String =
    normalizeFsPath(listOf(parent, name).joinToString("/"))

/** Chemin lisible normalisé : toujours un `/` initial, jamais de `/` final. */
fun normalizeFsPath(path: String): String {
    val trimmed = path.trim().trim('/')
    return if (trimmed.isEmpty()) "" else "/$trimmed"
}

/**
 * Échappe un base64 pour qu'il reste un unique segment d'URL : l'alphabet
 * standard contient `/`, `+` et `=`.
 */
fun fsPathSegment(encoded: String): String = encoded
    .replace("/", "%2F")
    .replace("+", "%2B")
    .replace("=", "%3D")

/** Base64 de la racine (`Lw==`), pour les corps JSON (`dirname`, `download_dir`). */
fun encodeFsRoot(): String = base64("/")

/** Segment d'URL de la racine : `Lw%3D%3D` (`/`). */
fun fsRootSegment(): String = fsPathSegment(encodeFsRoot())

private fun base64(value: String): String =
    Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
