package com.anasdev.fluxlink.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.anasdev.fluxlink.domain.model.ClientActivityType
import com.anasdev.fluxlink.domain.model.ConnectionMode
import com.anasdev.fluxlink.domain.model.NetworkInterfaceInfo
import com.anasdev.fluxlink.domain.model.NetworkState
import com.anasdev.fluxlink.domain.model.NetworkType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkInfoProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val portManager: PortManager,
    private val connectedSessionManager: ConnectedSessionManager
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    fun getNetworkState(overridePort: Int? = null): NetworkState {
        val interfaces = getFilteredNetworkInterfaces()

        // 1. Determine base connectivity from ConnectivityManager
        val activeNetwork = connectivityManager?.activeNetwork
        val capabilities = connectivityManager?.getNetworkCapabilities(activeNetwork)

        val hasWifiTransport = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        // 2. Select best Wi-Fi or Hotspot interface (Strict Hotspot Priority 1 over Wi-Fi Router Priority 2)
        val selectedInterface = selectBestWifiOrHotspotInterface(interfaces)

        var connectionMode = ConnectionMode.DISCONNECTED
        var primaryType = NetworkType.DISCONNECTED

        if (selectedInterface != null) {
            val score = scoreInterface(selectedInterface)

            // Priority 1: Hotspot interface or local AP subnet (score >= 100) -> HOTSPOT_DIRECT
            if (score >= 100 || (!hasWifiTransport && isPrivateIp(selectedInterface.ipAddress))) {
                connectionMode = ConnectionMode.HOTSPOT_DIRECT
                primaryType = NetworkType.HOTSPOT
            } else if (hasWifiTransport) {
                connectionMode = ConnectionMode.WIFI_ROUTER
                primaryType = NetworkType.WIFI
            } else {
                connectionMode = ConnectionMode.WIFI_ROUTER
                primaryType = NetworkType.WIFI
            }
        }

        val ipAddress = selectedInterface?.ipAddress
        val interfaceName = selectedInterface?.interfaceName

        // 3. Port selection: check port 8080 or fallback to 8081, 8082, etc.
        val port = overridePort ?: portManager.findAvailablePort(startPort = 8080)
        val isAvailable = portManager.isPortAvailable(port)

        val serverUrl = if (ipAddress != null && isAvailable) {
            "http://$ipAddress:$port"
        } else null

        val isConnected = primaryType != NetworkType.DISCONNECTED && ipAddress != null

        return NetworkState(
            isConnected = isConnected,
            networkType = primaryType,
            connectionMode = connectionMode,
            selectedIpAddress = ipAddress,
            interfaceName = interfaceName,
            selectedPort = port,
            isPortAvailable = isAvailable,
            serverUrl = serverUrl,
            availableInterfaces = interfaces
        )
    }

    fun observeNetworkState(): Flow<NetworkState> = callbackFlow {
        // Send initial state
        trySend(getNetworkState())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(getNetworkState())
            }

            override fun onLost(network: Network) {
                trySend(getNetworkState())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                trySend(getNetworkState())
            }
        }

        connectivityManager?.registerDefaultNetworkCallback(callback)

        // Event-driven callback with 15s fallback check (skips polling whenever active transfers are running to prevent netlink lock contention)
        val tickerJob = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                delay(15_000L) // 15-second fallback interval

                val hasActiveTransfer = connectedSessionManager.connectedClients.value.any { client ->
                    val t = client.currentActivity.type
                    t == ClientActivityType.STREAMING_VIDEO ||
                            t == ClientActivityType.DOWNLOADING ||
                            t == ClientActivityType.UPLOADING
                }

                if (!hasActiveTransfer) {
                    trySend(getNetworkState())
                }
            }
        }

        awaitClose {
            tickerJob.cancel()
            connectivityManager?.unregisterNetworkCallback(callback)
        }
    }

    private fun getFilteredNetworkInterfaces(): List<NetworkInterfaceInfo> {
        val resultList = mutableListOf<NetworkInterfaceInfo>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            for (netInt in interfaces.toList()) {
                if (!netInt.isUp || netInt.isLoopback) continue

                val intName = netInt.name.lowercase(Locale.ROOT)
                val isWifiOrApName = isWifiOrHotspotInterfaceName(intName)

                for (addr in netInt.inetAddresses.toList()) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                        val hostAddress = addr.hostAddress ?: continue
                        val is192 = hostAddress.startsWith("192.168.")
                        resultList.add(
                            NetworkInterfaceInfo(
                                interfaceName = netInt.name,
                                ipAddress = hostAddress,
                                isWifiOrHotspot = isWifiOrApName || is192 || isPrivateIp(hostAddress),
                                isPreferred192Subnet = is192
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return resultList
    }

    /**
     * Strict Hotspot Priority Algorithm:
     * Scores candidate interfaces giving Priority 1 (Score 100) to Direct Hotspot AP interfaces (`ap0`, `swlan0`, `192.168.43.x`)
     * over standard Wi-Fi LAN interfaces (Score 80).
     */
    private fun selectBestWifiOrHotspotInterface(interfaces: List<NetworkInterfaceInfo>): NetworkInterfaceInfo? {
        if (interfaces.isEmpty()) return null
        return interfaces.maxByOrNull { scoreInterface(it) }
    }

    private fun scoreInterface(info: NetworkInterfaceInfo): Int {
        val name = info.interfaceName.lowercase(Locale.ROOT)
        val ip = info.ipAddress

        val isExplicitHotspotName = name.startsWith("ap") ||
                name.startsWith("softap") ||
                name.startsWith("swlan") ||
                name.startsWith("wlan1") ||
                name.startsWith("wlan2") ||
                name.startsWith("rndis") ||
                name.startsWith("p2p") ||
                name.startsWith("tether")

        val isHotspotSubnet = ip.startsWith("192.168.43.") ||
                ip.startsWith("192.168.49.") ||
                ip.startsWith("192.168.242.") ||
                ip.startsWith("192.168.137.") ||
                ip.startsWith("172.20.10.")

        if (isExplicitHotspotName || isHotspotSubnet) {
            return 100 // Priority 1: Direct Hotspot AP interface (Turbo Speed)
        }

        if (info.isPreferred192Subnet && info.isWifiOrHotspot) {
            return 80 // Priority 2: 192.168.x.x Wi-Fi LAN
        }

        if (info.isWifiOrHotspot) {
            return 60 // Priority 3: Other Wi-Fi/P2P
        }

        if (isPrivateIp(ip)) {
            return 40 // Priority 4: Other Private IPv4
        }

        return 10 // Priority 5: Any non-loopback IPv4
    }

    private fun isWifiOrHotspotInterfaceName(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return lower.startsWith("wlan") ||
                lower.startsWith("swlan") ||
                lower.startsWith("ap") ||
                lower.startsWith("softap") ||
                lower.startsWith("p2p") ||
                lower.startsWith("wifi") ||
                lower.startsWith("rndis") ||
                lower.startsWith("tether")
    }

    private fun isPrivateIp(ip: String): Boolean {
        return ip.startsWith("192.168.") ||
                ip.startsWith("10.") ||
                (ip.startsWith("172.") && is172PrivateSubnet(ip))
    }

    private fun is172PrivateSubnet(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size >= 2) {
            val secondOctet = parts[1].toIntOrNull() ?: return false
            return secondOctet in 16..31
        }
        return false
    }
}
