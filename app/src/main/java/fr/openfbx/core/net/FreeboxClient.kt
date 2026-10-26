package fr.openfbx.core.net

import fr.openfbx.core.model.BoxConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
 */
class FreeboxClient(
    private val config: BoxConfig,
    private val appId: String,
    private val appTokenProvider: suspend () -> String?,
    private val onFingerprintObserved: (String) -> Unit,
) {
    private val json = FbxJson

    private var sessionToken: String? = null
    private var sessionPermissions: Map<String, Boolean> = emptyMap()

    var observedFingerprint: String? = null
        private set

    private val trustManager = TofuTrustManager(config.certFingerprint) { fingerprint ->
        observedFingerprint = fingerprint
        onFingerprintObserved(fingerprint)
    }

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
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
        val result = request("POST", "login/authorize/", body, retryOnAuth = false).asObjectOrThrow()
        val token = result.string("app_token")
            ?: throw FreeboxException("invalid_response", "La Freebox n'a pas renvoyé de jeton d'application.")
        val trackId = result.int("track_id")
            ?: throw FreeboxException("invalid_response", "La Freebox n'a pas renvoyé d'identifiant de suivi.")
        return PairingTicket(token, trackId)
    }

    suspend fun pairingStatus(trackId: Int): PairingStatus {
        val result = request("GET", "login/authorize/$trackId", retryOnAuth = false).asObjectOrThrow()
        return PairingStatus(
            status = result.string("status") ?: "pending",
            permissions = result.booleanMap("permissions"),
        )
    }

    // -------------------------------------------------------------------- session

    suspend fun openSession() {
        val appToken = appTokenProvider()
            ?: throw FreeboxException("app_token_missing", "Aucun jeton d'application enregistré.")

        val challenge = request("GET", "login/", retryOnAuth = false).asObjectOrThrow().string("challenge")
            ?: throw FreeboxException("invalid_response", "Challenge d'authentification absent.")

        val body = buildJsonObject {
            put("app_id", appId)
            put("password", FbxAuth.hmacSha1Hex(appToken, challenge))
        }
        val result = request("POST", "login/session/", body, retryOnAuth = false).asObjectOrThrow()
        sessionToken = result.string("session_token")
            ?: throw FreeboxException("invalid_response", "Jeton de session absent de la réponse.")
        sessionPermissions = result.booleanMap("permissions")
    }

    suspend fun closeSession() {
        if (sessionToken == null) return
        runCatching { request("POST", "login/logout/", retryOnAuth = false) }
        sessionToken = null
        sessionPermissions = emptyMap()
    }

    // ------------------------------------------------------------------- requêtes

    suspend fun request(
        method: String,
        path: String,
        body: JsonElement? = null,
        retryOnAuth: Boolean = true,
    ): JsonElement {
        val first = parse(execute(method, path, body))
        if (first.success) return first.result ?: JsonObject(emptyMap())

        if (retryOnAuth && isSessionErrorCode(first.errorCode)) {
            sessionToken = null
            openSession()
            val retried = parse(execute(method, path, body))
            if (retried.success) return retried.result ?: JsonObject(emptyMap())
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

    suspend fun <T> getObject(path: String, serializer: KSerializer<T>): T {
        val element = request("GET", path)
        return runCatching { json.decodeFromJsonElement(serializer, element) }
            .getOrElse { throw FreeboxException("invalid_response", "Réponse illisible pour $path", cause = it) }
    }

    suspend fun <T> getList(path: String, serializer: KSerializer<T>, vararg keys: String): List<T> {
        val element = request("GET", path)
        return element.asList(*keys).mapNotNull { item ->
            runCatching { json.decodeFromJsonElement(serializer, item) }.getOrNull()
        }
    }

    suspend fun post(path: String, body: JsonElement? = null): JsonElement =
        request("POST", path, body)

    suspend fun put(path: String, body: JsonElement? = null): JsonElement =
        request("PUT", path, body)

    suspend fun delete(path: String): JsonElement = request("DELETE", path)

    // -------------------------------------------------------------------- plumbing

    private fun decode(text: String, httpStatus: Int): EnvelopeResult {
        if (text.isBlank()) {
            return if (httpStatus in 200..299) EnvelopeResult(true, null, null, null, httpStatus)
            else EnvelopeResult(false, null, "http_$httpStatus", "HTTP $httpStatus", httpStatus)
        }
        val envelope = runCatching { json.decodeFromString<ApiEnvelope<JsonElement>>(text) }
            .getOrElse { throw FreeboxException("invalid_response", "Réponse illisible (HTTP $httpStatus)", httpStatus, it) }
        return EnvelopeResult(envelope.success, envelope.result, envelope.errorCode, envelope.msg, httpStatus)
    }

    private fun parse(reply: HttpReply): EnvelopeResult = decode(reply.body, reply.code)

    private suspend fun execute(method: String, path: String, body: JsonElement?): HttpReply =
        withContext(Dispatchers.IO) {
            val url = config.baseUrl + path.removePrefix("/")
            val builder = Request.Builder().url(url)
            sessionToken?.let { builder.header("X-Fbx-App-Auth", it) }

            val payload = body?.toString()?.toRequestBody(JSON_MEDIA)
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
                throw FreeboxException("tls", e.message ?: "Certificat refusé", cause = e)
            } catch (e: java.io.IOException) {
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
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private val EMPTY_BODY = ByteArray(0).toRequestBody(JSON_MEDIA)

        fun sslContext(manager: X509TrustManager): SSLContext =
            SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(manager), null)
            }

        /** Extrait une série de points depuis une réponse RRD, qu'elle soit plate ou imbriquée. */
        fun parseRrdSeries(element: JsonElement?): List<Pair<Long, Double>> {
            val data = (element as? JsonObject)?.get("data") as? JsonArray ?: return emptyList()
            return data.mapNotNull { row ->
                val values = row as? JsonArray ?: return@mapNotNull null
                val timestamp = (values.getOrNull(0) as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
                    ?.toLong() ?: return@mapNotNull null
                val value = when (val raw = values.getOrNull(1)) {
                    is JsonPrimitive -> raw.contentOrNull?.toDoubleOrNull()
                    is JsonArray -> (raw.firstOrNull() as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
                    else -> null
                }
                value?.let { timestamp to it }
            }
        }
    }
}
