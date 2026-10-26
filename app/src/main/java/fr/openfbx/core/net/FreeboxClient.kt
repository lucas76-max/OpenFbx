package fr.openfbx.core.net

import fr.openfbx.core.log.AppLog
import fr.openfbx.core.model.BoxConfig
import fr.openfbx.core.model.encodeFsPath
import fr.openfbx.core.model.encodeFsRoot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.KSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class PairingTicket(val appToken: String, val trackId: Int)

data class PairingStatus(val status: String, val permissions: Map<String, Boolean> = emptyMap()) {
    val isGranted: Boolean get() = status == "granted"
    val isTerminal: Boolean get() = status == "granted" || status == "denied" || status == "timeout"
}

/**
 * Client HTTP de Freebox OS : découverte déjà faite, appairage et session gérés ici.
 *
 * Chaque appel authentifié envoie `X-Fbx-App-Auth`. Si la box répond
 * `auth_required` / `invalid_session`, une nouvelle session est ouverte (challenge
 * + HMAC) et la requête est rejouée une fois.
 *
 * Toutes les requêtes sont tracées dans [AppLog] (méthode, chemin, statut, durée,
 * code d'erreur) sans jamais écrire de jeton.
 */
class FreeboxClient(
    private val config: BoxConfig,
    private val appId: String,
    private val appTokenProvider: suspend () -> String?,
    private val onFingerprintObserved: (String) -> Unit,
) {
    private val json = FbxJson

    @Volatile
    private var sessionToken: String? = null
    private var sessionPermissions: Map<String, Boolean> = emptyMap()

    /**
     * Sérialise l'ouverture de session : plusieurs écrans lancent leurs requêtes
     * en parallèle au démarrage et recevaient tous `auth_required`, ce qui
     * ouvrait quatre sessions d'affilée. Une seule suffit désormais.
     */
    private val sessionMutex = Mutex()

    var observedFingerprint: String? = null
        private set

    private val trustManager = TofuTrustManager(config.certFingerprint) { fingerprint ->
        observedFingerprint = fingerprint
        AppLog.i(TAG, "Empreinte TLS observée : $fingerprint")
        onFingerprintObserved(fingerprint)
    }

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .sslSocketFactory(sslContext(trustManager).socketFactory, trustManager)
        // L'identité de la box est garantie par l'empreinte du certificat, pas par son nom d'hôte.
        .hostnameVerifier { _, _ -> true }
        .build()

    val permissions: Map<String, Boolean> get() = sessionPermissions

    fun hasSession(): Boolean = sessionToken != null

    // ------------------------------------------------------------------ appairage

    suspend fun requestPairing(appName: String, appVersion: String, deviceName: String): PairingTicket {
        val body = buildJsonObject {
            put("app_id", appId)
            put("app_name", appName)
            put("app_version", appVersion)
            put("device_name", deviceName)
        }
        AppLog.i(TAG, "Demande d'appairage ($deviceName)")
        val result = perform("POST", "login/authorize/", body.toString().toRequestBody(JSON_MEDIA), retryOnAuth = false)
            .asObjectOrThrow()
        val token = result.string("app_token")
            ?: throw FreeboxException("invalid_response", "La Freebox n'a pas renvoyé de jeton d'application.")
        val trackId = result.int("track_id")
            ?: throw FreeboxException("invalid_response", "La Freebox n'a pas renvoyé d'identifiant de suivi.")
        AppLog.i(TAG, "Appairage en attente de validation (track_id=$trackId)")
        return PairingTicket(token, trackId)
    }

    suspend fun pairingStatus(trackId: Int): PairingStatus {
        val result = perform("GET", "login/authorize/$trackId", null, retryOnAuth = false).asObjectOrThrow()
        return PairingStatus(
            status = result.string("status") ?: "pending",
            permissions = result.booleanMap("permissions"),
        )
    }

    // -------------------------------------------------------------------- session

    suspend fun openSession() = sessionMutex.withLock { openSessionLocked() }

    private suspend fun openSessionLocked() {
        val appToken = appTokenProvider()
            ?: throw FreeboxException("app_token_missing", "Aucun jeton d'application enregistré.")

        val challenge = perform("GET", "login/", null, retryOnAuth = false).asObjectOrThrow().string("challenge")
            ?: throw FreeboxException("invalid_response", "Challenge d'authentification absent.")

        val body = buildJsonObject {
            put("app_id", appId)
            put("password", FbxAuth.hmacSha1Hex(appToken, challenge))
        }
        val result = perform("POST", "login/session/", body.toString().toRequestBody(JSON_MEDIA), retryOnAuth = false)
            .asObjectOrThrow()
        sessionToken = result.string("session_token")
            ?: throw FreeboxException("invalid_response", "Jeton de session absent de la réponse.")
        sessionPermissions = result.booleanMap("permissions")
        AppLog.i(TAG, "Session ouverte (${sessionPermissions.count { it.value }} droits accordés)")
    }

    /**
     * Renouvelle la session expirée, sauf si une autre requête vient déjà de le
     * faire entre-temps (comparaison avec le jeton qui a échoué).
     */
    private suspend fun renewSession(staleToken: String?) {
        sessionMutex.withLock {
            if (sessionToken != null && sessionToken != staleToken) return
            sessionToken = null
            openSessionLocked()
        }
    }

    suspend fun closeSession() {
        if (sessionToken == null) return
        runCatching { perform("POST", "login/logout/", null, retryOnAuth = false) }
        sessionToken = null
        sessionPermissions = emptyMap()
        AppLog.i(TAG, "Session fermée")
    }

    // ------------------------------------------------------------------- requêtes

    suspend fun request(
        method: String,
        path: String,
        body: JsonElement? = null,
        retryOnAuth: Boolean = true,
    ): JsonElement = perform(method, path, body?.toString()?.toRequestBody(JSON_MEDIA), retryOnAuth)

    /**
     * Envoi `application/x-www-form-urlencoded`.
     *
     * `downloads/add` refuse un corps JSON : il attend des arguments de formulaire.
     */
    suspend fun postForm(path: String, fields: Map<String, String>): JsonElement {
        val form = FormBody.Builder().apply { fields.forEach { (key, value) -> add(key, value) } }.build()
        return perform("POST", path, form, retryOnAuth = true)
    }

    /** Envoi multipart d'un petit fichier (utilisé pour ajouter un `.torrent`). */
    suspend fun postMultipartFile(
        path: String,
        fieldName: String,
        fileName: String,
        content: ByteArray,
        mediaType: String = "application/octet-stream",
        extraFields: Map<String, String> = emptyMap(),
    ): JsonElement {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(fieldName, fileName, content.toRequestBody(mediaType.toMediaType()))
            .apply { extraFields.forEach { (key, value) -> addFormDataPart(key, value) } }
            .build()
        return perform("POST", path, body, retryOnAuth = true)
    }

    suspend fun <T> getObject(path: String, serializer: KSerializer<T>): T {
        val element = request("GET", path)
        return runCatching { json.decodeFromJsonElement(serializer, element) }
            .getOrElse {
                AppLog.e(TAG, "Décodage impossible pour $path : ${it.message}")
                throw FreeboxException("invalid_response", "Réponse illisible pour $path", cause = it)
            }
    }

    suspend fun <T> getList(path: String, serializer: KSerializer<T>, vararg keys: String): List<T> {
        val element = request("GET", path)
        val items = element.asList(*keys)
        val decoded = items.mapNotNull { item ->
            runCatching { json.decodeFromJsonElement(serializer, item) }.getOrNull()
        }
        if (decoded.size != items.size) {
            AppLog.w(TAG, "$path : ${items.size - decoded.size} entrée(s) illisible(s) ignorée(s)")
        }
        return decoded
    }

    suspend fun post(path: String, body: JsonElement? = null): JsonElement =
        request("POST", path, body)

    suspend fun put(path: String, body: JsonElement? = null): JsonElement =
        request("PUT", path, body)

    suspend fun delete(path: String): JsonElement = request("DELETE", path)

    // ---------------------------------------------------------------------- upload

    /**
     * Envoi d'un fichier vers un dossier de la box.
     *
     * Le transfert HTTP multipart est déprécié depuis l'API v4 : on utilise le
     * WebSocket documenté (`ws/upload`) — un message `upload_start`, puis des
     * trames binaires de 512 Kio, puis `upload_finalize`.
     */
    suspend fun uploadFile(
        directoryPath: String,
        fileName: String,
        content: ByteArray,
        onProgress: (Float) -> Unit = {},
    ) {
        val token = sessionToken ?: run {
            openSession()
            sessionToken
        } ?: throw FreeboxException("auth_required", "Session requise pour envoyer un fichier.")

        val url = config.baseUrl.replaceFirst("https://", "wss://") + "ws/upload"
        AppLog.i(TAG, "Upload de $fileName (${content.size} o) vers ${directoryPath.ifBlank { "/" }}")

        val requestId = 1
        val done = CompletableDeferred<Unit>()
        val request = Request.Builder()
            .url(url)
            .header("X-Fbx-App-Auth", token)
            .build()

        val startMessage = buildJsonObject {
            put("action", "upload_start")
            put("request_id", requestId)
            put("size", content.size)
            put("dirname", if (directoryPath.isBlank()) encodeFsRoot() else encodeFsPath(directoryPath))
            put("filename", fileName)
            put("force", "overwrite")
        }.toString()

        val finalizeMessage = buildJsonObject {
            put("action", "upload_finalize")
            put("request_id", requestId)
        }.toString()

        val chunkSize = 512 * 1024
        var offset = 0
        var socket: WebSocket? = null

        fun sendNextChunk(ws: WebSocket) {
            if (offset >= content.size) {
                ws.send(finalizeMessage)
                return
            }
            val end = minOf(offset + chunkSize, content.size)
            val chunk = content.copyOfRange(offset, end)
            offset = end
            val queued = ws.send(chunk.toByteString())
            if (!queued) {
                done.completeExceptionally(FreeboxException("upload", "Envoi interrompu par la Freebox."))
                return
            }
            onProgress(offset.toFloat() / content.size.coerceAtLeast(1).toFloat())
        }

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                socket = webSocket
                AppLog.d(TAG, "WebSocket d'upload ouvert")
                webSocket.send(startMessage)
            }

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) = Unit

            override fun onMessage(webSocket: WebSocket, text: String) {
                val element = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
                if (element == null) {
                    AppLog.w(TAG, "Message d'upload illisible")
                    return
                }
                val action = element.string("action")
                val success = element.bool("success") ?: true
                if (!success) {
                    val code = element.string("error_code")
                    val message = element.string("msg") ?: "Envoi refusé par la Freebox."
                    AppLog.e(TAG, "Upload refusé ($action) : $code $message")
                    done.completeExceptionally(FreeboxException(code, message))
                    webSocket.close(1000, "error")
                    return
                }
                when (action) {
                    "upload_start" -> {
                        if (content.isEmpty()) webSocket.send(finalizeMessage) else sendNextChunk(webSocket)
                    }
                    "upload_data" -> {
                        val result = element["result"] as? JsonObject
                        val total = result?.long("total_len")
                        if (total != null && content.isNotEmpty()) {
                            onProgress((total.toFloat() / content.size).coerceIn(0f, 1f))
                        }
                        if (offset < content.size) sendNextChunk(webSocket) else webSocket.send(finalizeMessage)
                    }
                    "upload_finalize" -> {
                        onProgress(1f)
                        AppLog.i(TAG, "Upload terminé : $fileName")
                        done.complete(Unit)
                        webSocket.close(1000, "done")
                    }
                    "upload_cancel" -> done.completeExceptionally(
                        FreeboxException("upload_cancelled", "Envoi annulé par la Freebox."),
                    )
                    else -> Unit
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                AppLog.e(TAG, "Échec de l'upload : ${t.message} (HTTP ${response?.code ?: "—"})")
                done.completeExceptionally(
                    FreeboxException("upload", t.message ?: "Échec de l'envoi du fichier.", response?.code, t),
                )
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!done.isCompleted) {
                    done.completeExceptionally(FreeboxException("upload", "Connexion fermée pendant l'envoi ($code)."))
                }
            }
        }

        val ws = http.newWebSocket(request, listener)
        socket = ws
        try {
            withTimeout(5 * 60_000L) { done.await() }
        } finally {
            socket?.close(1000, "finished")
        }
    }

    // -------------------------------------------------------------------- plumbing

    private suspend fun perform(
        method: String,
        path: String,
        payload: RequestBody?,
        retryOnAuth: Boolean,
    ): JsonElement {
        val started = System.currentTimeMillis()
        val usedToken = sessionToken
        val first = parse(execute(method, path, payload))
        val duration = System.currentTimeMillis() - started

        if (first.success) {
            AppLog.d(TAG, "$method $path → ${first.httpStatus} ($duration ms)")
            return first.result ?: JsonObject(emptyMap())
        }

        AppLog.w(
            TAG,
            "$method $path → ${first.httpStatus} ${first.errorCode ?: "?"} " +
                "« ${first.msg ?: "sans message"} » ($duration ms)",
        )

        if (retryOnAuth && isSessionErrorCode(first.errorCode)) {
            renewSession(usedToken)
            val retried = parse(execute(method, path, payload))
            if (retried.success) {
                AppLog.d(TAG, "$method $path → ${retried.httpStatus} après réouverture de session")
                return retried.result ?: JsonObject(emptyMap())
            }
            throw FreeboxException(
                retried.errorCode,
                retried.msg ?: defaultApiMessage(retried.errorCode),
                retried.httpStatus,
            )
        }

        throw FreeboxException(
            first.errorCode,
            first.msg ?: defaultApiMessage(first.errorCode),
            first.httpStatus,
        )
    }

    private fun decode(text: String, httpStatus: Int): EnvelopeResult {
        if (text.isBlank()) {
            return if (httpStatus in 200..299) EnvelopeResult(true, null, null, null, httpStatus)
            else EnvelopeResult(false, null, "http_$httpStatus", "HTTP $httpStatus", httpStatus)
        }
        val envelope = runCatching { json.decodeFromString<ApiEnvelope<JsonElement>>(text) }
            .getOrElse {
                AppLog.e(TAG, "Enveloppe illisible (HTTP $httpStatus) : ${text.take(160)}")
                throw FreeboxException("invalid_response", "Réponse illisible (HTTP $httpStatus)", httpStatus, it)
            }
        return EnvelopeResult(envelope.success, envelope.result, envelope.errorCode, envelope.msg, httpStatus)
    }

    private fun parse(reply: HttpReply): EnvelopeResult = decode(reply.body, reply.code)

    private suspend fun execute(method: String, path: String, payload: RequestBody?): HttpReply =
        withContext(Dispatchers.IO) {
            val url = config.baseUrl + path.removePrefix("/")
            val builder = Request.Builder().url(url)
            sessionToken?.let { builder.header("X-Fbx-App-Auth", it) }

            when (method) {
                "GET" -> builder.get()
                "POST" -> builder.post(payload ?: EMPTY_BODY)
                "PUT" -> builder.put(payload ?: EMPTY_BODY)
                "DELETE" -> if (payload != null) builder.delete(payload) else builder.delete()
                else -> builder.method(method, payload)
            }

            try {
                http.newCall(builder.build()).execute().use { response ->
                    HttpReply(response.code, response.body?.string().orEmpty())
                }
            } catch (e: javax.net.ssl.SSLException) {
                AppLog.e(TAG, "TLS refusé pour $path : ${e.message}")
                throw FreeboxException("tls", e.message ?: "Certificat refusé", cause = e)
            } catch (e: java.io.IOException) {
                AppLog.e(TAG, "Erreur réseau sur $path : ${e.message}")
                throw FreeboxException("network", e.message ?: "Connexion à la Freebox impossible", cause = e)
            }
        }

    private data class HttpReply(val code: Int, val body: String)

    private data class EnvelopeResult(
        val success: Boolean,
        val result: JsonElement?,
        val errorCode: String?,
        val msg: String?,
        val httpStatus: Int,
    )

    companion object {
        private const val TAG = "Freebox"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private val EMPTY_BODY = ByteArray(0).toRequestBody(JSON_MEDIA)

        fun sslContext(manager: X509TrustManager): SSLContext =
            SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(manager), null)
            }

        /**
         * Extrait une série de points d'une réponse RRD.
         *
         * Format documenté : `result.data` est une liste d'objets
         * `{"time": 1353060840, "cpub": 545}`. On accepte aussi l'ancien format
         * en tableaux (`[[timestamp, valeur]]`) par prudence.
         */
        fun parseRrdPoints(element: JsonElement?, field: String, precision: Double = 100.0): List<Pair<Long, Double>> {
            val data = when (element) {
                is JsonObject -> element["data"] as? JsonArray
                is JsonArray -> element
                else -> null
            } ?: return emptyList()

            return data.mapNotNull { row ->
                when (row) {
                    is JsonObject -> {
                        val time = row.long("time") ?: return@mapNotNull null
                        val raw = row.double(field) ?: return@mapNotNull null
                        time to raw / precision
                    }
                    is JsonArray -> {
                        val time = (row.getOrNull(0) as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
                            ?.toLong() ?: return@mapNotNull null
                        val raw = when (val value = row.getOrNull(1)) {
                            is JsonPrimitive -> value.contentOrNull?.toDoubleOrNull()
                            is JsonArray -> (value.firstOrNull() as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
                            else -> null
                        } ?: return@mapNotNull null
                        time to raw
                    }
                    else -> null
                }
            }
        }
    }
}
