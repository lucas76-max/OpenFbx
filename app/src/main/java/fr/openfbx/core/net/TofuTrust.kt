package fr.openfbx.core.net

import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/**
 * Confiance par empreinte (TOFU).
 *
 * Le certificat d'une Freebox est auto-signé et son nom ne correspond pas
 * forcément à l'adresse utilisée sur le réseau local. On ne peut donc pas
 * valider la chaîne avec les autorités système — et désactiver toute
 * vérification serait pire.
 *
 * À la première connexion l'empreinte SHA-256 du certificat est relevée et
 * présentée à l'utilisateur au moment de l'appairage. Une fois confirmée, elle
 * est mémorisée et toute autre empreinte est rejetée : la box est identifiée
 * par son certificat, pas par son nom d'hôte.
 */
class TofuTrustManager(
    private val pinnedFingerprint: String?,
    private val onFingerprintObserved: (String) -> Unit,
) : X509TrustManager {

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("Chaîne de certificats vide")
        val fingerprint = fingerprint(leaf)

        if (pinnedFingerprint.isNullOrBlank()) {
            onFingerprintObserved(fingerprint)
        } else if (!fingerprint.equals(pinnedFingerprint, ignoreCase = true)) {
            throw CertificateException(
                "Le certificat présenté par la Freebox ne correspond pas à celui mémorisé.\n" +
                    "Mémorisé : $pinnedFingerprint\nReçu : $fingerprint",
            )
        }

        leaf.checkValidity()
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    companion object {
        fun fingerprint(certificate: X509Certificate): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
            return digest.joinToString(":") { byte -> "%02X".format(byte.toInt() and 0xFF) }
        }
    }
}
