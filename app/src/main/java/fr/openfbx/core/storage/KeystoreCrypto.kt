package fr.openfbx.core.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * L'`app_token` est un identifiant permanent : il est chiffré en AES-GCM avec
 * une clé matérielle du Keystore Android avant d'être écrit dans DataStore.
 * La clé ne quitte jamais le Keystore et n'est pas sauvegardée : sur une
 * restauration de sauvegarde, le jeton redevient illisible — il suffit de
 * réappairer.
 */
object KeystoreCrypto {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "openfbx_app_token"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String? = runCatching {
        val raw = Base64.decode(encoded, Base64.NO_WRAP)
        if (raw.size <= IV_SIZE) return null
        val iv = raw.copyOfRange(0, IV_SIZE)
        val ciphertext = raw.copyOfRange(IV_SIZE, raw.size)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }.getOrNull()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val existing = runCatching { keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry }
            .getOrNull()
        existing?.let { return it.secretKey }

        // Clé absente ou invalidée (changement de verrouillage d'écran) : on en crée une neuve.
        runCatching { keyStore.deleteEntry(ALIAS) }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}
