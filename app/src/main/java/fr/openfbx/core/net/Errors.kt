package fr.openfbx.core.net

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Erreur normalisée : soit renvoyée par l'API (`error_code`), soit levée côté client. */
class FreeboxException(
    val errorCode: String?,
    message: String,
    val httpStatus: Int? = null,
    cause: Throwable? = null,
) : Exception(message, cause)

internal fun isSessionErrorCode(errorCode: String?): Boolean =
    errorCode == "auth_required" || errorCode == "invalid_session"

internal fun defaultApiMessage(errorCode: String?): String = when (errorCode) {
    "auth_required", "invalid_session" -> "Session requise par la Freebox."
    "pending_token" -> "L'appairage n'a pas encore été validé sur la Freebox."
    "insufficient_rights", "request_denied" ->
        "Droit insuffisant : autorisez cette action dans Freebox OS → Paramètres → Gestion des accès."
    "too_many_requests", "ratelimit" -> "Trop de requêtes envoyées à la Freebox, patientez un instant."
    "unknown_api_version" -> "Version d'API inconnue de la Freebox."
    "not_found", "does_not_exist" -> "Cet élément n'existe plus sur la Freebox."
    "invalid_token" -> "Jeton d'application invalide : refaites l'appairage."
    "app_token_missing" -> "Aucun jeton d'application : appairez l'application avec la Freebox."
    "network" -> "Connexion à la Freebox impossible."
    "tls" -> "Certificat de la Freebox refusé."
    "invalid_response" -> "Réponse de la Freebox illisible."
    else -> "La Freebox a refusé la requête."
}

/** Message destiné à l'interface, en français. */
fun Throwable.userMessage(): String = when (this) {
    is FreeboxException -> when {
        isSessionErrorCode(errorCode) -> "Session expirée. Ouvrez les réglages pour vous reconnecter."
        message.isNullOrBlank() -> defaultApiMessage(errorCode)
        else -> message!!
    }
    is UnknownHostException -> "Freebox introuvable sur le réseau. Vérifiez que vous êtes bien connecté à son réseau Wi-Fi."
    is SocketTimeoutException -> "La Freebox ne répond pas (délai dépassé)."
    is ConnectException -> "Connexion refusée par la Freebox."
    is SSLException -> "Certificat de la Freebox invalide ou différent de celui mémorisé."
    is IOException -> "Erreur réseau : ${message ?: "connexion interrompue"}"
    else -> message ?: "Erreur inattendue."
}
