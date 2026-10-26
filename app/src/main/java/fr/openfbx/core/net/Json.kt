package fr.openfbx.core.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

val FbxJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

/**
 * Enveloppe de toutes les réponses de Freebox OS :
 * `{"success":true,"result":…}` ou `{"success":false,"error_code":"…","msg":"…"}`.
 */
@Serializable
data class ApiEnvelope<T>(
    val success: Boolean = false,
    val result: T? = null,
    @SerialName("error_code") val errorCode: String? = null,
    val msg: String? = null,
)

fun JsonElement?.asObjectOrThrow(): JsonObject =
    this as? JsonObject ?: throw FreeboxException("invalid_response", "Réponse inattendue de la Freebox")

/**
 * Freebox OS renvoie tantôt un tableau nu, tantôt un objet enveloppant
 * (`{"station":[…]}`). On accepte les deux.
 */
fun JsonElement?.asList(vararg keys: String): List<JsonElement> = when (val element = this) {
    null, is JsonNull -> emptyList()
    is JsonArray -> element.toList()
    is JsonObject -> {
        keys.firstNotNullOfOrNull { key -> (element[key] as? JsonArray)?.toList() }
            ?: if (element.isEmpty()) emptyList() else listOf(element)
    }
    else -> emptyList()
}

fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

fun JsonObject.int(key: String): Int? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toInt()

fun JsonObject.long(key: String): Long? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toLong()

fun JsonObject.double(key: String): Double? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()

fun JsonObject.bool(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.booleanOrNull

fun JsonObject.booleanMap(key: String): Map<String, Boolean> {
    val obj = this[key] as? JsonObject ?: return emptyMap()
    return obj.mapNotNull { (name, value) ->
        (value as? JsonPrimitive)?.booleanOrNull?.let { name to it }
    }.toMap()
}
