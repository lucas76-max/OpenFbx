package fr.openfbx.core.discovery

import fr.openfbx.core.model.DiscoveredBox
import fr.openfbx.core.net.FbxJson
import fr.openfbx.core.net.FreeboxException
import fr.openfbx.core.net.TofuTrustManager
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager

@Serializable
private data class DiscoveryInfo(
    @SerialName("api_domain") val apiDomain: String? = null,
    @SerialName("https_port") val httpsPort: Int? = null,
    @SerialName("api_base_url") val apiBaseUrl: String = "/api/",
    @SerialName("api_version") val apiVersion: String = "4.0",
    @SerialName("box_model") val boxModel: String? = null,
    @SerialName("box_model_name") val boxModelName: String? = null,
    @SerialName("device_name") val deviceName: String? = null,
    @SerialName("uid") val uid: String? = null,
    @SerialName("https_available") val httpsAvailable: Boolean = false,
)

/**
 * Découverte de la Freebox.
 *
 * `http://mafreebox.freebox.fr/api_version` suffit sur le réseau local.
 * Pour une adresse personnalisée, la même ressource est interrogée en HTTPS.
 */
class DiscoveryClient {

    private val json = FbxJson

    private val plainClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val tlsClient: OkHttpClient = run {
        val trust = TofuTrustManager(null) { }
        val context = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trust), null)
        }
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .sslSocketFactory(context.socketFactory, trust)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    suspend fun discoverDefaultHost(): DiscoveredBox =
        discover("mafreebox.freebox.fr", https = false, port = null)

    suspend fun discover(host: String, https: Boolean, port: Int? = null): DiscoveredBox {
        val cleanHost = host.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
        if (cleanHost.isEmpty()) throw FreeboxException("invalid_response", "Adresse de Freebox vide.")
        val scheme = if (https) "https" else "http"
        val authority = if (port != null) "$cleanHost:$port" else cleanHost
        return discoverUrl("$scheme://$authority/api_version", cleanHost)
    }

    private suspend fun discoverUrl(url: String, host: String): DiscoveredBox = withContext(Dispatchers.IO) {
        val client = if (url.startsWith("https")) tlsClient else plainClient
        val request = Request.Builder().url(url).get().build()

        val body = try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw FreeboxException("http_${response.code}", "HTTP ${response.code} sur $url")
                }
                response.body?.string().orEmpty()
            }
        } catch (e: FreeboxException) {
            throw e
        } catch (e: java.io.IOException) {
            throw FreeboxException("network", "Freebox injoignable sur $url", cause = e)
        }

        val info = thinkParse(body)
        DiscoveredBox(
            host = host,
            httpsPort = info.httpsPort ?: 443,
            apiBaseUrl = info.apiBaseUrl,
            apiVersion = info.apiVersion,
            apiDomain = info.apiDomain,
            boxModel = info.boxModel,
            boxModelName = info.boxModelName,
            deviceName = info.deviceName,
            uid = info.uid,
            httpsAvailable = info.httpsAvailable,
        )
    }

    /** `/api_version` renvoie l'objet nu ; certaines box l'enveloppent dans `result`. */
    private fun thinkParse(body: String): DiscoveryInfo {
        runCatching { json.decodeFromString<DiscoveryInfo>(body) }.getOrNull()?.let { return it }
        return runCatching {
            val root = json.parseToJsonElement(body)
            val payload = (root as? JsonObject)?.get("result") ?: root
            json.decodeFromJsonElement(DiscoveryInfo.serializer(), payload)
        }.getOrElse {
            throw FreeboxException("invalid_response", "Réponse de découverte illisible.")
        }
    }
}
