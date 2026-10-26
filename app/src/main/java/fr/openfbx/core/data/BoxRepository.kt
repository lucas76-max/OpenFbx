package fr.openfbx.core.data

import fr.openfbx.core.data.dto.ConnectionStatus
import fr.openfbx.core.data.dto.DhcpConfig
import fr.openfbx.core.data.dto.DhcpLease
import fr.openfbx.core.data.dto.DownloadTask
import fr.openfbx.core.data.dto.FileEntry
import fr.openfbx.core.data.dto.FtthStatus
import fr.openfbx.core.data.dto.LanHost
import fr.openfbx.core.data.dto.StaticLease
import fr.openfbx.core.data.dto.SystemInfo
import fr.openfbx.core.data.dto.WifiAccessPoint
import fr.openfbx.core.data.dto.WifiGlobal
import fr.openfbx.core.data.dto.WifiStation
import fr.openfbx.core.data.dto.XdslStatus
import fr.openfbx.core.discovery.DiscoveryClient
import fr.openfbx.core.model.AccentPalette
import fr.openfbx.core.model.AppSettings
import fr.openfbx.core.model.BoxConfig
import fr.openfbx.core.model.DiscoveredBox
import fr.openfbx.core.model.ThemeMode
import fr.openfbx.core.net.FreeboxClient
import fr.openfbx.core.net.FreeboxException
import fr.openfbx.core.net.PairingStatus
import fr.openfbx.core.net.PairingTicket
import fr.openfbx.core.storage.BoxStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Base64

/**
 * Point d'entrée unique vers la Freebox : découverte, appairage, session
 * et les groupes d'API implémentés (jalons M0 → M2).
 */
class BoxRepository(
    private val store: BoxStore,
    private val appId: String,
    private val appName: String,
    private val appVersion: String,
    private val deviceName: String,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val discovery = DiscoveryClient()

    private var client: FreeboxClient? = null
    private var clientKey: String? = null
    private var cachedPermissions: Map<String, Boolean> = emptyMap()

    @Volatile
    private var lastObservedPin: String? = null

    val boxConfig: Flow<BoxConfig?> get() = store.boxConfig
    val settings: Flow<AppSettings> get() = store.settings

    // ---------------------------------------------------------------- découverte

    suspend fun discoverDefault(): DiscoveredBox = discovery.discoverDefaultHost()

    suspend fun discoverHost(host: String, https: Boolean = true, port: Int? = null): DiscoveredBox =
        discovery.discover(host, https, port)

    suspend fun saveDiscoveredBox(discovered: DiscoveredBox) {
        store.saveBox(discovered.toConfig())
        invalidateClient()
    }

    suspend fun currentConfig(): BoxConfig? = store.currentConfig()

    suspend fun currentSettings(): AppSettings = store.settings.first()

    // ----------------------------------------------------------------- appairage

    suspend fun requestPairing(): PairingTicket = client().requestPairing(appName, appVersion, deviceName)

    suspend fun pairingStatus(trackId: Int): PairingStatus = client().pairingStatus(trackId)

    suspend fun saveAppToken(token: String) {
        store.saveAppToken(token)
        invalidateClient()
    }

    suspend fun hasAppToken(): Boolean = store.hasAppToken()

    suspend fun savePin(fingerprint: String) {
        store.savePin(fingerprint)
        invalidateClient()
    }

    suspend fun forgetPin() {
        store.clearPin()
        invalidateClient()
    }

    suspend fun forgetBox() {
        runCatching { client().closeSession() }
        store.forgetBox()
        invalidateClient()
    }

    suspend fun updateSettings(
        themeMode: ThemeMode? = null,
        accent: AccentPalette? = null,
        dynamicColor: Boolean? = null,
    ) {
        store.updateSettings(themeMode = themeMode, accent = accent, dynamicColor = dynamicColor)
    }

    // -------------------------------------------------------------------- session

    suspend fun openSession(): Map<String, Boolean> {
        val active = client()
        active.openSession()
        cachedPermissions = active.permissions
        return cachedPermissions
    }

    fun permissions(): Map<String, Boolean> = cachedPermissions

    /** Empreinte du certificat relevée lors du dernier échange TLS. */
    fun observedPin(): String? = lastObservedPin

    // --------------------------------------------------------------------- system

    suspend fun system(): SystemInfo = client().getObject("system/", SystemInfo.serializer())

    suspend fun reboot() {
        client().post("system/reboot/", buildJsonObject { put("reason", "OpenFbx") })
    }

    // ----------------------------------------------------------------- connexion

    suspend fun connection(): ConnectionStatus = client().getObject("connection/", ConnectionStatus.serializer())

    suspend fun ftth(): FtthStatus? = optional { client().getObject("connection/ftth/", FtthStatus.serializer()) }

    suspend fun xdsl(): XdslStatus? = optional { client().getObject("connection/xdsl/", XdslStatus.serializer()) }

    // ----------------------------------------------------------------------- wifi

    suspend fun wifiEnabled(): Boolean = client().getObject("wifi/", WifiGlobal.serializer()).enabled

    suspend fun setWifiEnabled(enabled: Boolean) {
        client().put("wifi/", buildJsonObject { put("enabled", enabled) })
    }

    suspend fun wifiAccessPoints(): List<WifiAccessPoint> =
        client().getList("wifi/ap/", WifiAccessPoint.serializer())

    suspend fun setAccessPointEnabled(id: Int, enabled: Boolean) {
        client().put("wifi/ap/$id/config/", buildJsonObject { put("enabled", enabled) })
    }

    suspend fun wifiStations(apId: Int): List<WifiStation> =
        client().getList("wifi/ap/$apId/stations/", WifiStation.serializer(), "station", "stations")

    // ---------------------------------------------------------------------- réseau

    suspend fun lanHosts(iface: String = "pub"): List<LanHost> =
        client().getList("lan/browser/$iface/", LanHost.serializer())

    suspend fun dhcpConfig(): DhcpConfig? = optional { client().getObject("dhcp/config/", DhcpConfig.serializer()) }

    suspend fun dhcpLeases(): List<DhcpLease> = client().getList("dhcp/dynamic_lease/", DhcpLease.serializer())

    suspend fun staticLeases(): List<StaticLease> = client().getList("dhcp/static_lease/", StaticLease.serializer())

    suspend fun addStaticLease(mac: String, ip: String, comment: String?) {
        val body = buildJsonObject {
            put("mac", mac)
            put("ip", ip)
            if (!comment.isNullOrBlank()) put("comment", comment)
        }
        client().post("dhcp/static_lease/", body)
    }

    suspend fun deleteStaticLease(id: String) {
        client().delete("dhcp/static_lease/$id")
    }

    suspend fun wakeOnLan(mac: String) {
        client().post("lan/wol/pub/", buildJsonObject { put("mac", mac) })
    }

    // ------------------------------------------------------------- téléchargements

    suspend fun downloads(): List<DownloadTask> =
        client().getList("downloads/", DownloadTask.serializer(), "downloads", "tasks")

    /** Ajoute un lien HTTP(S), FTP, magnet ou un fichier `.torrent` à la file. */
    suspend fun addDownload(url: String) {
        client().post(
            "downloads/add",
            buildJsonObject { put("download_url", url.trim()) },
        )
    }

    /** `stopped` met en pause, `downloading` relance, `retry` relance une tâche en erreur. */
    suspend fun setDownloadStatus(id: Int, status: String) {
        client().put("downloads/$id", buildJsonObject { put("status", status) })
    }

    /** Retire la tâche ; avec [eraseFiles], les fichiers téléchargés sont aussi effacés. */
    suspend fun removeDownload(id: Int, eraseFiles: Boolean) {
        if (eraseFiles) {
            client().post("downloads/$id/erase", buildJsonObject { })
        } else {
            client().delete("downloads/$id")
        }
    }

    // --------------------------------------------------------------------- fichiers

    /** Liste un dossier. [path] est le chemin lisible (ex. `Disque dur/Musique`). */
    suspend fun files(path: String = ""): List<FileEntry> =
        client().getList("fs/ls/${encodeFsPath(path)}", FileEntry.serializer(), "files", "entries")

    suspend fun createFolder(parent: String, name: String) {
        client().post(
            "fs/mkdir/",
            buildJsonObject {
                put("parent", encodeFsPath(parent))
                put("dirname", name)
            },
        )
    }

    suspend fun deleteFiles(paths: List<String>) {
        client().post(
            "fs/rm/",
            buildJsonObject {
                put("files", JsonArray(paths.map { JsonPrimitive(it) }))
            },
        )
    }

    suspend fun renameFile(sourcePathBase64: String, newName: String) {
        client().post(
            "fs/rename/",
            buildJsonObject {
                put("src", sourcePathBase64)
                put("dst", newName)
            },
        )
    }

    // ------------------------------------------------------------------- métriques

    suspend fun rrdDatabases(): List<String> {
        val element = client().request("GET", "rrd/")
        return (element as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content?.takeIf(String::isNotBlank) }
    }

    suspend fun rrdSeries(database: String, field: String, minutes: Int = 60): List<Pair<Long, Double>> {
        val now = System.currentTimeMillis() / 1000
        val start = now - minutes * 60L
        val path = "rrd/$database/$field?db=$database&start=$start&end=$now&precision=300"
        return FreeboxClient.parseRrdSeries(client().request("GET", path))
    }

    // -------------------------------------------------------------------- interne

    private fun invalidateClient() {
        client = null
        clientKey = null
        cachedPermissions = emptyMap()
    }

    private suspend fun client(): FreeboxClient {
        val config = store.currentConfig()
            ?: throw FreeboxException("not_configured", "Aucune Freebox enregistrée.")

        val key = "${config.host}:${config.httpsPort}:${config.apiMajor}:${config.certFingerprint}"
        client?.let { if (clientKey == key) return it }

        val created = FreeboxClient(
            config = config,
            appId = appId,
            appTokenProvider = {
                store.appToken() ?: throw FreeboxException(
                    "app_token_missing",
                    "Appairez d'abord OpenFbx avec la Freebox.",
                )
            },
            onFingerprintObserved = { fingerprint ->
                lastObservedPin = fingerprint
                scope.launch { store.savePin(fingerprint) }
            },
        )
        client = created
        clientKey = key
        return created
    }

    /**
     * Les modules optionnels (FTTH, xDSL, DHCP…) n'existent pas sur toutes les box.
     * Une erreur ici ne doit pas casser l'écran : on renvoie `null`.
     */
    private suspend fun <T> optional(block: suspend () -> T): T? = runCatching { block() }.getOrNull()
}

/**
 * Encode un chemin du système de fichiers pour l'insérer dans une URL.
 *
 * Freebox OS attend le chemin en base64. Comme cet alphabet contient `/`, `+`
 * et `=`, on les percent-encode pour que le chemin reste un seul segment d'URL.
 * Le chemin vide (racine) reste vide : `fs/ls/`.
 */
fun encodeFsPath(path: String): String {
    if (path.isBlank()) return ""
    val encoded = Base64.getEncoder().encodeToString(path.trimEnd('/').toByteArray(Charsets.UTF_8))
    return encoded
        .replace("/", "%2F")
        .replace("+", "%2B")
        .replace("=", "%3D")
}

/** Base64 d'un chemin enfant, utilisé quand la box ne renvoie pas le champ `path`. */
fun childFsPath(parent: String, name: String): String =
    encodeFsPath(listOf(parent.trimEnd('/'), name).filter { it.isNotEmpty() }.joinToString("/"))
