package fr.openfbx

import fr.openfbx.core.data.dto.DownloadTask
import fr.openfbx.core.model.BoxConfig
import fr.openfbx.core.model.childFsPath
import fr.openfbx.core.model.encodeFsDir
import fr.openfbx.core.model.encodeFsPath
import fr.openfbx.core.model.fsRootSegment
import fr.openfbx.core.model.normalizeFsPath
import fr.openfbx.core.model.normalizedApiBase
import fr.openfbx.core.net.ApiEnvelope
import fr.openfbx.core.net.FbxAuth
import fr.openfbx.core.net.FbxJson
import fr.openfbx.core.net.asList
import fr.openfbx.core.net.bool
import fr.openfbx.core.net.string
import fr.openfbx.ui.formatEta
import fr.openfbx.ui.formatLinkSpeed
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FbxAuthTest {

    @Test
    fun `hmac sha1 matches the reference vector`() {
        val digest = FbxAuth.hmacSha1Hex(
            appToken = "key",
            challenge = "The quick brown fox jumps over the lazy dog",
        )
        assertEquals("de7c9b85b8b78aa6bc8a7a36f70a90701c9db4d9", digest)
    }

    @Test
    fun `hmac sha1 is lowercase hex of fixed length`() {
        val digest = FbxAuth.hmacSha1Hex("mon-app-token", "challenge-1234")
        assertEquals(40, digest.length)
        assertTrue(digest.all { it in "0123456789abcdef" })
    }

    @Test
    fun `different challenges give different passwords`() {
        val first = FbxAuth.hmacSha1Hex("token", "challenge-a")
        val second = FbxAuth.hmacSha1Hex("token", "challenge-b")
        assertFalse(first == second)
    }
}

class ApiEnvelopeTest {

    @Test
    fun `successful envelope exposes the result`() {
        val payload = """
            {"success":true,"result":{"enabled":true,"ssid":"Freebox-ABC"}}
        """.trimIndent()

        val envelope = FbxJson.decodeFromString<ApiEnvelope<JsonObject>>(payload)
        assertTrue(envelope.success)
        assertTrue(envelope.result!!.bool("enabled") == true)
        assertEquals("Freebox-ABC", envelope.result!!.string("ssid"))
    }

    @Test
    fun `failed envelope exposes the error code and message`() {
        val payload = """
            {"success":false,"error_code":"insufficient_rights","msg":"You need to grant the settings right"}
        """.trimIndent()

        val envelope = FbxJson.decodeFromString<ApiEnvelope<JsonObject>>(payload)
        assertFalse(envelope.success)
        assertEquals("insufficient_rights", envelope.errorCode)
        assertEquals("You need to grant the settings right", envelope.msg)
        assertNull(envelope.result)
    }

    @Test
    fun `unknown fields are ignored so firmware additions do not break parsing`() {
        val payload = """{"success":true,"result":{"a":1},"future_field":[1,2,3]}"""
        val envelope = FbxJson.decodeFromString<ApiEnvelope<JsonObject>>(payload)
        assertTrue(envelope.success)
    }
}

class JsonHelperTest {

    @Test
    fun `a plain array and a wrapped array are read the same way`() {
        val plain = FbxJson.parseToJsonElement("""[{"mac":"aa"},{"mac":"bb"}]""")
        val wrapped = FbxJson.parseToJsonElement("""{"station":[{"mac":"aa"},{"mac":"bb"}]}""")

        assertEquals(2, plain.asList("station").size)
        assertEquals(2, wrapped.asList("station").size)
        assertEquals(2, wrapped.asList("stations", "station").size)
    }

    @Test
    fun `an empty object yields no items`() {
        val empty = FbxJson.parseToJsonElement("{}")
        assertTrue(empty.asList("station").isEmpty())
    }

    @Test
    fun `single object responses are kept as one item`() {
        val single = FbxJson.parseToJsonElement("""{"challenge":"abc"}""")
        val items = single.asList("data")
        assertEquals(1, items.size)
        assertEquals("abc", (items.first() as JsonObject)["challenge"]?.let { (it as JsonPrimitive).content })
    }
}

class RrdSeriesTest {

    @Test
    fun `documented object rows are parsed into points`() {
        // Réponse documentée de POST /rrd/ : result.data = [{time, <champ>}, …]
        val payload = FbxJson.parseToJsonElement(
            """{"date_start":1730000000,"data":[{"time":1730000840,"cpub":54.5},{"time":1730000900,"cpub":55.0}],"date_end":1730001000}""",
        )
        val points = fr.openfbx.core.net.FreeboxClient.parseRrdPoints(payload, "cpub", precision = 1.0)

        assertEquals(2, points.size)
        assertEquals(1730000840L, points.first().first)
        assertEquals(54.5, points.first().second, 0.001)
    }

    @Test
    fun `precision divides the returned values`() {
        val payload = FbxJson.parseToJsonElement("""{"data":[{"time":1730000840,"cpub":5450}]}""")
        val points = fr.openfbx.core.net.FreeboxClient.parseRrdPoints(payload, "cpub", precision = 100.0)
        assertEquals(54.5, points.first().second, 0.001)
    }

    @Test
    fun `legacy array rows are still accepted`() {
        val payload = FbxJson.parseToJsonElement(
            """{"data":[[1730000000,1024.5],[1730000300,null],[1730000600,[9.5]]]}""",
        )
        val points = fr.openfbx.core.net.FreeboxClient.parseRrdPoints(payload, "rate_down", precision = 1.0)

        assertEquals(2, points.size)
        assertEquals(1024.5, points.first().second, 0.001)
        assertEquals(9.5, points.last().second, 0.001)
    }

    @Test
    fun `an unexpected shape yields no points instead of crashing`() {
        val payload = FbxJson.parseToJsonElement("""{"result":"nope"}""")
        assertTrue(fr.openfbx.core.net.FreeboxClient.parseRrdPoints(payload, "cpub", 1.0).isEmpty())
    }
}

class BoxConfigTest {

    @Test
    fun `base url is built from discovery values`() {
        val config = BoxConfig(
            host = "mafreebox.freebox.fr",
            httpsPort = 443,
            apiBaseUrl = "/api/",
            apiMajor = 8,
        )
        assertEquals("https://mafreebox.freebox.fr:443/api/v8/", config.baseUrl)
    }

    @Test
    fun `api base is normalised`() {
        assertEquals("/api/", "api".normalizedApiBase())
        assertEquals("/api/", "/api".normalizedApiBase())
        assertEquals("/api/", " /api/ ".normalizedApiBase())
        assertEquals("/api/", "".normalizedApiBase())
    }

    @Test
    fun `display name prefers the box model`() {
        assertEquals("Freebox Server", BoxConfig(host = "h", boxModelName = "Freebox Server").displayName)
        assertEquals("h", BoxConfig(host = "h").displayName)
    }
}

class LinkSpeedTest {

    @Test
    fun `a 5 mbit line is not displayed as gigabits`() {
        // bandwidth_down est en bit/s : 5 000 000 = 5 Mbit/s, pas 5000 Gbit/s.
        assertEquals("5 Mbit/s", formatLinkSpeed(5_000_000))
    }

    @Test
    fun `gigabit links keep two decimals`() {
        assertEquals("2,50 Gbit/s", formatLinkSpeed(2_500_000_000))
        assertEquals("1,00 Gbit/s", formatLinkSpeed(1_000_000_000))
    }

    @Test
    fun `slow links drop to kbit`() {
        assertEquals("500 kbit/s", formatLinkSpeed(500_000))
        assertEquals("512 bit/s", formatLinkSpeed(512))
    }

    @Test
    fun `missing or zero bandwidth stays empty`() {
        assertEquals("—", formatLinkSpeed(null))
        assertEquals("—", formatLinkSpeed(0))
    }

    @Test
    fun `eta is human readable`() {
        assertEquals("45 s", formatEta(45))
        assertEquals("2 min 05 s", formatEta(125))
        assertEquals("1 h 03", formatEta(3_780))
    }
}

class FsPathTest {

    @Test
    fun `paths are encoded with their leading slash`() {
        // La box attend le chemin complet : « /Disque dur » -> L0Rpc3F1ZSBkdXI=
        assertEquals("L0Rpc3F1ZSBkdXI=", encodeFsPath("Disque dur"))
        assertEquals("L0Rpc3F1ZSBkdXI=", encodeFsPath("/Disque dur"))
        assertEquals("L0Rpc3F1ZSBkdXI=", encodeFsPath("/Disque dur/"))
    }

    @Test
    fun `url segments escape base64 padding and slashes`() {
        val segment = fr.openfbx.core.model.fsPathSegment(encodeFsPath("/Disque dur"))
        assertEquals("L0Rpc3F1ZSBkdXI%3D", segment)
    }

    @Test
    fun `directory paths keep a trailing slash`() {
        // download_dir : « /Disque dur/ » -> L0Rpc3F1ZSBkdXIv
        assertEquals("L0Rpc3F1ZSBkdXIv", encodeFsDir("Disque dur"))
    }

    @Test
    fun `root path is empty while the root segment is explicit`() {
        assertEquals("", encodeFsPath(""))
        assertEquals("", encodeFsPath("  "))
        assertEquals("Lw%3D%3D", fsRootSegment())
    }

    @Test
    fun `paths are normalised before use`() {
        assertEquals("/Disque dur/Musique", normalizeFsPath("Disque dur/Musique/"))
        assertEquals("/Musique", normalizeFsPath("//Musique"))
        assertEquals("", normalizeFsPath("/"))
    }

    @Test
    fun `child paths are joined before encoding`() {
        assertEquals("/Disque dur/Musique", childFsPath("Disque dur", "Musique"))
        assertEquals("/Musique", childFsPath("", "Musique"))
        assertEquals("/Disque dur/Musique", childFsPath("/Disque dur", "Musique"))
    }

    @Test
    fun `url segment never contains a raw slash plus or padding`() {
        val segment = fr.openfbx.core.model.fsPathSegment(encodeFsPath("/Disque dur/Films & séries"))
        assertFalse(segment.contains('/'))
        assertFalse(segment.contains('+'))
        assertFalse(segment.contains('='))
    }
}

class DownloadTaskTest {

    private fun task(json: String): DownloadTask = FbxJson.decodeFromString<DownloadTask>(json)

    @Test
    fun `progress is computed from received bytes`() {
        val download = task("""{"id":3,"name":"image.iso","size":1000,"rx_bytes":250,"status":"active"}""")
        assertEquals(0.25f, download.progress!!, 0.001f)
    }

    @Test
    fun `unknown size leaves progress unknown`() {
        val download = task("""{"id":4,"name":"magnet","size":0,"rx_bytes":0,"status":"queued"}""")
        assertNull(download.progress)
    }

    @Test
    fun `status helpers follow the api vocabulary`() {
        assertTrue(task("""{"status":"downloading"}""").isRunning)
        assertTrue(task("""{"status":"stopped"}""").isPaused)
        assertTrue(task("""{"status":"error"}""").isFailed)
        assertTrue(task("""{"status":"done"}""").isFinished)
    }
}
