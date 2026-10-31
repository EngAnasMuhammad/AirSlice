package com.anasdev.fluxlink.domain.model

enum class ConnectionMode(val title: String, val isTurbo: Boolean) {
    HOTSPOT_DIRECT("⚡ Turbo Mode Active (Direct Hotspot)", isTurbo = true),
    WIFI_ROUTER("Connected via Wi-Fi Router", isTurbo = false),
    DISCONNECTED("Disconnected", isTurbo = false)
}

data class NetworkState(
    val isConnected: Boolean = false,
    val networkType: NetworkType = NetworkType.DISCONNECTED,
    val connectionMode: ConnectionMode = ConnectionMode.DISCONNECTED,
    val selectedIpAddress: String? = null,
    val interfaceName: String? = null,
    val selectedPort: Int = 8080,
    val isPortAvailable: Boolean = true,
    val serverUrl: String? = null,
    val availableInterfaces: List<NetworkInterfaceInfo> = emptyList()
)
