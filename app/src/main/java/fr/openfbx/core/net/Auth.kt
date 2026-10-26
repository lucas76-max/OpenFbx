package fr.openfbx.core.net

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Authentification Freebox OS.
 *
 * L'`app_token` obtenu à l'appairage sert de clé HMAC-SHA1 pour signer le
 * `challenge` renvoyé par `GET /login/`. Le résultat, en hexadécimal minuscule,
 * est le mot de passe envoyé à `POST /login/session/`.
 */
object FbxAuth {

    fun hmacSha1Hex(appToken: String, challenge: String): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(appToken.toByteArray(Charsets.UTF_8), "HmacSHA1"))
        val digest = mac.doFinal(challenge.toByteArray(Charsets.UTF_8))
        return buildString(digest.size * 2) {
            for (byte in digest) {
                append(HEX[(byte.toInt() shr 4) and 0x0F])
                append(HEX[byte.toInt() and 0x0F])
            }
        }
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
