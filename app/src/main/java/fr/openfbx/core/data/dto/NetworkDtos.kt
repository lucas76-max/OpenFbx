package fr.openfbx.core.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WifiGlobal(
    val enabled: Boolean = false,
    @SerialName("mac_filter_state") val macFilterState: String? = null,
)

@Serializable
data class WifiAccessPoint(
    val id: Int = 0,
    val name: String? = null,
    val config: WifiApConfig? = null,
    val status: WifiApStatus? = null,
) {
    val isEnabled: Boolean get() = config?.enabled ?: false
    val ssid: String? get() = config?.ssid
    val state: String? get() = status?.state
}

@Serializable
data class WifiApConfig(
    val enabled: Boolean = false,
    val ssid: String? = null,
    @SerialName("hide_ssid") val hideSsid: Boolean? = null,
)

@Serializable
data class WifiApStatus(
    val state: String? = null,
    val channel: Int? = null,
    @SerialName("primary_channel") val primaryChannel: Int? = null,
    @SerialName("secondary_channel") val secondaryChannel: Int? = null,
    @SerialName("channel_width") val channelWidth: Int? = null,
    @SerialName("tx_power") val txPower: Int? = null,
    val authorized: Boolean? = null,
    val active: Boolean? = null,
)

@Serializable
data class WifiStation(
    val mac: String? = null,
    val hostname: String? = null,
    val signal: Int? = null,
    @SerialName("rate_down") val rateDown: Long? = null,
    @SerialName("rate_up") val rateUp: Long? = null,
    val inactive: Int? = null,
    @SerialName("last_activity") val lastActivity: Long? = null,
    @SerialName("connection_type") val connectionType: String? = null,
)

@Serializable
data class L3Connectivity(
    val addr: String? = null,
    val af: String? = null,
    val active: Boolean? = null,
    val reachable: Boolean? = null,
    @SerialName("last_activity") val lastActivity: Long? = null,
)

@Serializable
data class LanHost(
    val id: String? = null,
    @SerialName("primary_name") val primaryName: String? = null,
    @SerialName("primary_addr") val primaryAddr: String? = null,
    @SerialName("host_type") val hostType: String? = null,
    val active: Boolean = false,
    val reachable: Boolean? = null,
    @SerialName("last_time_reachable") val lastTimeReachable: Long? = null,
    @SerialName("last_activity") val lastActivity: Long? = null,
    @SerialName("vendor_name") val vendorName: String? = null,
    @SerialName("interface") val iface: String? = null,
    /** Certains firmwares exposent un débit par appareil (octets/s). */
    @SerialName("rx_rate") val rxRate: Long? = null,
    @SerialName("tx_rate") val txRate: Long? = null,
    @SerialName("l2ident") val l2ident: L2Ident? = null,
    @SerialName("l3connectivities") val l3connectivities: List<L3Connectivity> = emptyList(),
) {
    val mac: String? get() = l2ident?.id
    val ipv4: String? get() = l3connectivities.firstOrNull { it.af == "ipv4" && it.active != false }?.addr
    val ipv6: String? get() = l3connectivities.firstOrNull { it.af == "ipv6" && it.reachable == true }?.addr
}

@Serializable
data class L2Ident(
    val id: String? = null,
    val type: String? = null,
)

@Serializable
data class DhcpConfig(
    val gateway: String? = null,
    val netmask: String? = null,
    @SerialName("ip_range_start") val rangeStart: String? = null,
    @SerialName("ip_range_end") val rangeEnd: String? = null,
    val domain: String? = null,
    @SerialName("sticky_assign") val stickyAssign: Boolean? = null,
    val dns: List<String>? = null,
)

@Serializable
data class DhcpLease(
    val mac: String? = null,
    val ip: String? = null,
    val hostname: String? = null,
    @SerialName("lease_remaining") val leaseRemaining: Long? = null,
    @SerialName("is_static") val isStatic: Boolean? = null,
    val assignable: Boolean? = null,
)

@Serializable
data class StaticLease(
    val id: String? = null,
    val mac: String? = null,
    val ip: String? = null,
    val hostname: String? = null,
    val comment: String? = null,
)
