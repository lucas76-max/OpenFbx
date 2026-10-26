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
import fr.openfbx.core.log.AppLog
import fr.openfbx.core.model.AccentPalette
import fr.openfbx.core.model.AppSettings
import fr.openfbx.core.model.BoxConfig
import fr.openfbx.core.model.DiscoveredBox
import fr.openfbx.core.model.ThemeMode
import fr.openfbx.core.model.encodeFsDir
import fr.openfbx.core.model.encodeFsPath
import fr.openfbx.core.model.fsPathSegment
import fr.openfbx.core.model.fsRootSegment
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

/**
 * Point d'entrée unique vers la Freebox : découverte, appairage, session et les
 * groupes d'API implémentés.
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
        AppLog.d("Thème", "Réglage : mode=${themeMode ?: "—"} accent=${accent ?: "—"} dynamique=${dynamicColor ?: "—"}")
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
        AppLog.w("Système", "Demande de redémarrage")
        client().post("system/reboot/", buildJsonObject { put("reason", "OpenFbx") })
    }

    // ----------------------------------------------------------------- connexion

    suspend fun connection(): ConnectionStatus = client().getObject("connection/", ConnectionStatus.serializer())

    suspend fun ftth(): FtthStatus? = optional { client().getObject("connection/ftth/", FtthStatus.serializer()) }

    suspend fun xdsl(): XdslStatus? = optional { client().getObject("connection/xdsl/", XdslStatus.serializer()) }

    // ----------------------------------------------------------------------- wifi

    suspend fun wifiEnabled(): Boolean = client().getObject("wifi/config/", WifiGlobal.serializer()).enabled

    suspend fun setWifiEnabled(enabled: Boolean) {
        AppLog.i("Wi-Fi", if (enabled) "Activation du Wi-Fi" else "Désactivation du Wi-Fi")
        client().put("wifi/config/", buildJsonObject { put("enabled", enabled) })
    }

    suspend fun wifiAccessPoints(): List<WifiAccessPoint> =
        client().getList("wifi/ap/", WifiAccessPoint.serializer())

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
        AppLog.i("Réseau", "Wake-on-LAN vers $mac")
        client().post("lan/wol/pub/", buildJsonObject { put("mac", mac) })
    }

    // ------------------------------------------------------------- téléchargements

    suspend fun downloads(): List<DownloadTask> =
        client().getList("downloads/", DownloadTask.serializer(), "downloads", "tasks")

    /**
     * Ajoute un lien HTTP(S), FTP ou magnet.
     *
     * `downloads/add` attend des arguments de formulaire (pas du JSON) et
     * `download_dir` doit être un chemin **base64 terminé par `/`**.
     */
    suspend fun addDownload(url: String, directory: String? = null) {
        val fields = buildMap {
            put("download_url", url.trim())
            if (!directory.isNullOrBlank()) put("download_dir", encodeFsDir(directory))
        }
        AppLog.i("Téléchargements", "Ajout d'un lien${if (directory.isNullOrBlank()) "" else " vers $directory"}")
        client().postForm("downloads/add", fields)
    }

    /** Ajoute un fichier `.torrent` local (envoi multipart de `download_file`). */
    suspend fun addTorrent(fileName: String, content: ByteArray, directory: String? = null) {
        val extra = if (directory.isNullOrBlank()) emptyMap() else mapOf("download_dir" to encodeFsDir(directory))
        AppLog.i("Téléchargements", "Ajout du torrent $fileName (${content.size} o)")
        client().postMultipartFile(
            path = "downloads/add",
            fieldName = "download_file",
            fileName = fileName,
            content = content,
            mediaType = "application/x-bittorrent",
            extraFields = extra,
        )
    }

    /** `stopped` met en pause, `downloading` relance, `retry` relance une tâche en erreur. */
    suspend fun setDownloadStatus(id: Int, status: String) {
        AppLog.i("Téléchargements", "Tâche $id → $status")
        client().put("downloads/$id", buildJsonObject { put("status", status) })
    }

    /** Retire la tâche ; avec [eraseFiles], les fichiers téléchargés sont aussi effacés. */
    suspend fun removeDownload(id: Int, eraseFiles: Boolean) {
        AppLog.i("Téléchargements", "Retrait de la tâche $id (fichiers effacés : $eraseFiles)")
        if (eraseFiles) {
            client().delete("downloads/$id/erase")
        } else {
            client().delete("downloads/$id")
        }
    }

    // --------------------------------------------------------------------- fichiers

    /** Liste un dossier. [path] est le chemin lisible (ex. `/Disque dur/Musique`). */
    suspend fun files(path: String = ""): List<FileEntry> {
        val encoded = encodeFsPath(path)
        val segment = if (encoded.isEmpty()) "" else fsPathSegment(encoded)
        return runCatching {
            client().getList("fs/ls/$segment", FileEntry.serializer(), "files", "entries")
        }.getOrElse { error ->
            // Certains firmwares n'acceptent pas `fs/ls/` nu : on retente avec « / » explicite.
            if (encoded.isEmpty()) {
                AppLog.d("Fichiers", "Racine refusée (${error.message}), nouvel essai avec / explicite")
                client().getList("fs/ls/${fsRootSegment()}", FileEntry.serializer(), "files", "entries")
            } else {
                throw error
            }
        }
    }

    suspend fun createFolder(parent: String, name: String) {
        AppLog.i("Fichiers", "Création du dossier $name dans ${parent.ifBlank { "/" }}")
        client().post(
            "fs/mkdir/",
            buildJsonObject {
                put("parent", encodeFsPath(parent))
                put("dirname", name)
            },
        )
    }

    suspend fun deleteFiles(paths: List<String>) {
        AppLog.w("Fichiers", "Suppression de ${paths.size} élément(s)")
        client().post(
            "fs/rm/",
            buildJsonObject {
                put("files", JsonArray(paths.map { JsonPrimitive(it) }))
            },
        )
    }

    suspend fun renameFile(sourcePathBase64: String, newName: String) {
        AppLog.i("Fichiers", "Renommage vers $newName")
        client().post(
            "fs/rename/",
            buildJsonObject {
                put("src", sourcePathBase64)
                put("dst", newName)
            },
        )
    }

    /** Envoie un fichier dans [directory] (chemin lisible). */
    suspend fun uploadFile(
        directory: String,
        fileName: String,
        content: ByteArray,
        onProgress: (Float) -> Unit = {},
    ) {
        client().uploadFile(directory, fileName, content, onProgress)
    }

    // ------------------------------------------------------------------- métriques

    /**
     * Bases RRD connues. Il n'existe pas d'endpoint de listing : on propose
     * celles documentées.
     */
    fun rrdDatabases(): List<String> = listOf("net", "temp", "dsl", "switch")

    /**
     * `POST /rrd/` avec `{db, fields, date_start, date_end}` ; la réponse est
     * `[{time, <champ>}, …]` (et non des tableaux).
     */
    suspend fun rrdSeries(database: String, field: String, minutes: Int = 60): List<Pair<Long, Double>> {
        val now = System.currentTimeMillis() / 1000
        val start = now - minutes * 60L
        val body = buildJsonObject {
            put("db", database)
            put("fields", JsonArray(listOf(JsonPrimitive(field))))
            put("date_start", start)
            put("date_end", now)
        }
        return FreeboxClient.parseRrdPoints(client().post("rrd/", body), field, precision = 1.0)
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
