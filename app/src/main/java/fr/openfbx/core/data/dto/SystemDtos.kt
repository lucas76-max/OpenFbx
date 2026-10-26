package fr.openfbx.core.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SystemSensor(
    val id: String? = null,
    val name: String? = null,
    val value: Double? = null,
)

@Serializable
data class SystemInfo(
    @SerialName("firmware_version") val firmwareVersion: String? = null,
    val mac: String? = null,
    val serial: String? = null,
    val uptime: String? = null,
    @SerialName("uptime_val") val uptimeSeconds: Long? = null,
    @SerialName("board_name") val boardName: String? = null,
    @SerialName("temp_cpub") val tempCpuB: Double? = null,
    @SerialName("temp_cpum") val tempCpuM: Double? = null,
    @SerialName("temp_sw") val tempSwitch: Double? = null,
    /** Firmwares récents : capteurs nommés, à privilégier sur les champs hérités. */
    val sensors: List<SystemSensor> = emptyList(),
    @SerialName("fan_rpm") val fanRpm: Int? = null,
    @SerialName("box_authenticated") val authenticated: Boolean? = null,
    @SerialName("disk_status") val diskStatus: String? = null,
    @SerialName("user_main_storage") val mainStorage: String? = null,
)

@Serializable
data class ConnectionStatus(
    val state: String? = null,
    val type: String? = null,
    val media: String? = null,
    val ipv4: String? = null,
    val ipv6: String? = null,
    @SerialName("rate_down") val rateDown: Long? = null,
    @SerialName("rate_up") val rateUp: Long? = null,
    @SerialName("bandwidth_down") val bandwidthDown: Long? = null,
    @SerialName("bandwidth_up") val bandwidthUp: Long? = null,
    @SerialName("bytes_down") val bytesDown: Long? = null,
    @SerialName("bytes_up") val bytesUp: Long? = null,
) {
    val isUp: Boolean get() = state.equals("up", ignoreCase = true)
}

@Serializable
data class FtthStatus(
    @SerialName("sfp_present") val sfpPresent: Boolean? = null,
    @SerialName("sfp_has_signal") val sfpHasSignal: Boolean? = null,
    @SerialName("sfp_vendor_name") val sfpVendor: String? = null,
    @SerialName("sfp_serial") val sfpSerial: String? = null,
    val link: Boolean? = null,
    @SerialName("sfp_alim_ok") val sfpPowerOk: Boolean? = null,
)

@Serializable
data class XdslStatus(
    val status: XdslLine? = null,
    val down: XdslRate? = null,
    val up: XdslRate? = null,
)

@Serializable
data class XdslLine(
    val status: String? = null,
    @SerialName("line_status") val lineStatus: String? = null,
    val uptime: Long? = null,
)

@Serializable
data class XdslRate(
    val rate: Long? = null,
    val snr: Double? = null,
    @SerialName("attenuation") val attenuation: Double? = null,
)
