package fr.openfbx.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import fr.openfbx.core.model.AccentPalette
import fr.openfbx.core.model.AppSettings
import fr.openfbx.core.model.BoxConfig
import fr.openfbx.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.openFbxDataStore: DataStore<Preferences> by preferencesDataStore(name = "openfbx")

class BoxStore(context: Context) {

    private val dataStore = context.applicationContext.openFbxDataStore

    val boxConfig: Flow<BoxConfig?> = dataStore.data.map { prefs ->
        val host = prefs[Keys.HOST] ?: return@map null
        BoxConfig(
            host = host,
            httpsPort = prefs[Keys.PORT] ?: 443,
            apiBaseUrl = prefs[Keys.API_BASE] ?: "/api/",
            apiMajor = prefs[Keys.API_MAJOR] ?: 4,
            apiDomain = prefs[Keys.API_DOMAIN],
            boxModel = prefs[Keys.BOX_MODEL],
            boxModelName = prefs[Keys.BOX_MODEL_NAME],
            deviceName = prefs[Keys.DEVICE_NAME],
            uid = prefs[Keys.UID],
            apiVersion = prefs[Keys.API_VERSION],
            certFingerprint = prefs[Keys.PIN],
        )
    }

    /** Vrai dès qu'un jeton d'application (appairage réussi) est enregistré. */
    val hasAppTokenFlow: Flow<Boolean> = dataStore.data.map { prefs -> prefs[Keys.APP_TOKEN] != null }

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            themeMode = ThemeMode.entries.firstOrNull { it.name == prefs[Keys.THEME] } ?: ThemeMode.SYSTEM,
            accent = AccentPalette.entries.firstOrNull { it.name == prefs[Keys.ACCENT] } ?: AccentPalette.VIOLET,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            pollSeconds = prefs[Keys.POLL_SECONDS] ?: 3,
        )
    }

    suspend fun currentConfig(): BoxConfig? = boxConfig.first()

    suspend fun saveBox(config: BoxConfig) {
        dataStore.edit { prefs ->
            prefs[Keys.HOST] = config.host
            prefs[Keys.PORT] = config.httpsPort
            prefs[Keys.API_BASE] = config.apiBaseUrl
            prefs[Keys.API_MAJOR] = config.apiMajor
            prefs[Keys.API_VERSION] = config.apiVersion.orEmpty()
            config.apiDomain?.let { prefs[Keys.API_DOMAIN] = it }
            config.boxModel?.let { prefs[Keys.BOX_MODEL] = it }
            config.boxModelName?.let { prefs[Keys.BOX_MODEL_NAME] = it }
            config.deviceName?.let { prefs[Keys.DEVICE_NAME] = it }
            config.uid?.let { prefs[Keys.UID] = it }
            config.certFingerprint?.let { prefs[Keys.PIN] = it }
        }
    }

    suspend fun savePin(fingerprint: String) {
        dataStore.edit { it[Keys.PIN] = fingerprint }
    }

    suspend fun clearPin() {
        dataStore.edit { it.remove(Keys.PIN) }
    }

    suspend fun saveAppToken(token: String) {
        val encrypted = KeystoreCrypto.encrypt(token)
        dataStore.edit { it[Keys.APP_TOKEN] = encrypted }
    }

    suspend fun appToken(): String? = dataStore.data.first()[Keys.APP_TOKEN]?.let(KeystoreCrypto::decrypt)

    suspend fun hasAppToken(): Boolean = dataStore.data.first()[Keys.APP_TOKEN] != null

    suspend fun forgetBox() {
        dataStore.edit { prefs ->
            Keys.BOX_KEYS.forEach { key -> prefs.remove(key) }
        }
    }

    suspend fun updateSettings(
        themeMode: ThemeMode? = null,
        accent: AccentPalette? = null,
        dynamicColor: Boolean? = null,
        pollSeconds: Int? = null,
    ) {
        dataStore.edit { prefs ->
            themeMode?.let { prefs[Keys.THEME] = it.name }
            accent?.let { prefs[Keys.ACCENT] = it.name }
            dynamicColor?.let { prefs[Keys.DYNAMIC_COLOR] = it }
            pollSeconds?.let { prefs[Keys.POLL_SECONDS] = it }
        }
    }

    private object Keys {
        val HOST = stringPreferencesKey("host")
        val PORT = intPreferencesKey("https_port")
        val API_BASE = stringPreferencesKey("api_base_url")
        val API_MAJOR = intPreferencesKey("api_major")
        val API_VERSION = stringPreferencesKey("api_version")
        val API_DOMAIN = stringPreferencesKey("api_domain")
        val BOX_MODEL = stringPreferencesKey("box_model")
        val BOX_MODEL_NAME = stringPreferencesKey("box_model_name")
        val DEVICE_NAME = stringPreferencesKey("box_device_name")
        val UID = stringPreferencesKey("box_uid")
        val PIN = stringPreferencesKey("cert_fingerprint")
        val APP_TOKEN = stringPreferencesKey("app_token_enc")
        val THEME = stringPreferencesKey("theme_mode")
        val ACCENT = stringPreferencesKey("accent_palette")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val POLL_SECONDS = intPreferencesKey("poll_seconds")

        val BOX_KEYS = listOf(HOST, PORT, API_BASE, API_MAJOR, API_VERSION, API_DOMAIN, BOX_MODEL, BOX_MODEL_NAME, DEVICE_NAME, UID, PIN, APP_TOKEN)
    }
}
