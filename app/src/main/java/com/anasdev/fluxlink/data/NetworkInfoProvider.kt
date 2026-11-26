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
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class NetworkInfoProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val portManager: PortManager,
    private val connectedSessionManager: ConnectedSessionManager,
    private val hotspotClientTracker: HotspotClientTracker
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    data class ModeSelectionResult(
        val connectionMode: ConnectionMode,
        val networkType: NetworkType,
        val selectedInterface: NetworkInterfaceInfo?
    )

    companion object {
        fun isHotspotInterface(info: NetworkInterfaceInfo): Boolean {
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

            return isExplicitHotspotName || isHotspotSubnet
        }

        fun isWifiInterface(info: NetworkInterfaceInfo): Boolean {
            val name = info.interfaceName.lowercase(Locale.ROOT)
            return !isHotspotInterface(info) && (info.isWifiOrHotspot || name.startsWith("wlan"))
        }

        fun isPrivateIp(ip: String): Boolean {
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

        /**
         * State Machine Priority Rules:
         * 1. Wi-Fi connected + Hotspot ON + 0 clients -> Wi-Fi Router mode (using Wi-Fi LAN IP).
         * 2. Wi-Fi connected + Hotspot ON + >= 1 hotspot client -> Hotspot mode (Turbo).
         * 3. Wi-Fi NOT connected + Hotspot ON (even with 0 clients) -> Hotspot mode.
         * 4. Wi-Fi connected + Hotspot OFF -> Wi-Fi Router mode.
         */
        fun resolveConnectionModeAndInterface(
            interfaces: List<NetworkInterfaceInfo>,
            hasWifiConnected: Boolean,
            hasHotspotClient: Boolean
        ): ModeSelectionResult {
            if (interfaces.isEmpty()) {
                return ModeSelectionResult(
                    ConnectionMode.DISCONNECTED,
                    NetworkType.DISCONNECTED,
                    null
                )
            }

            val hotspotInterface = interfaces.firstOrNull { isHotspotInterface(it) }
            val wifiInterface = interfaces.firstOrNull { isWifiInterface(it) }
                ?: interfaces.firstOrNull { !isHotspotInterface(it) }

            val isHotspotOn = hotspotInterface != null

            return when {
                // Rule 1: Wi-Fi connected + Hotspot ON + 0 hotspot clients detected -> Wi-Fi Router mode
                hasWifiConnected && isHotspotOn && !hasHotspotClient -> {
                    ModeSelectionResult(
                        ConnectionMode.WIFI_ROUTER,
                        NetworkType.WIFI,
                        wifiInterface ?: hotspotInterface
                    )
                }

                // Rule 2: Wi-Fi connected + Hotspot ON + >= 1 hotspot client detected -> Hotspot mode (Turbo)
                hasWifiConnected && isHotspotOn && hasHotspotClient -> {
                    ModeSelectionResult(
                        ConnectionMode.HOTSPOT_DIRECT,
                        NetworkType.HOTSPOT,
                        hotspotInterface
                    )
                }

                // Rule 3: Wi-Fi NOT connected + Hotspot ON (even with 0 clients) -> Hotspot mode
                !hasWifiConnected && isHotspotOn -> {
                    ModeSelectionResult(
                        ConnectionMode.HOTSPOT_DIRECT,
                        NetworkType.HOTSPOT,
                        hotspotInterface
                    )
                }

                // Rule 4: Wi-Fi connected + Hotspot OFF -> Wi-Fi Router mode
                hasWifiConnected && !isHotspotOn -> {
                    ModeSelectionResult(
                        ConnectionMode.WIFI_ROUTER,
                        NetworkType.WIFI,
                        wifiInterface
                    )
                }

                // Fallback: Neither active Wi-Fi nor Hotspot, but other interface exists (Ethernet, etc.)
                else -> {
                    val fallback = wifiInterface ?: interfaces.firstOrNull()
                    if (fallback != null && isPrivateIp(fallback.ipAddress)) {
                        ModeSelectionResult(ConnectionMode.WIFI_ROUTER, NetworkType.WIFI, fallback)
                    } else if (fallback != null) {
                        ModeSelectionResult(ConnectionMode.WIFI_ROUTER, NetworkType.OTHER, fallback)
                    } else {
                        ModeSelectionResult(ConnectionMode.DISCONNECTED, NetworkType.DISCONNECTED, null)
                    }
                }
            }
        }
    }

    fun getNetworkState(overridePort: Int? = null): NetworkState {
        val interfaces = getFilteredNetworkInterfaces()

        val activeNetwork = connectivityManager?.activeNetwork
        val capabilities = connectivityManager?.getNetworkCapabilities(activeNetwork)
        val hasWifiTransport = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                (connectivityManager?.allNetworks?.any { net ->
                    connectivityManager.getNetworkCapabilities(net)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                } == true)

        val hotspotInterface = interfaces.firstOrNull { isHotspotInterface(it) }
        val hasHotspotClient = hotspotInterface != null &&
                hotspotClientTracker.hasConnectedHotspotClient(hotspotInterface.ipAddress, hotspotInterface.interfaceName)

        val isWifiConnected = hasWifiTransport || interfaces.any { isWifiInterface(it) }

        val selection = resolveConnectionModeAndInterface(
            interfaces = interfaces,
            hasWifiConnected = isWifiConnected,
            hasHotspotClient = hasHotspotClient
        )

        val selectedInterface = selection.selectedInterface
        val connectionMode = selection.connectionMode
        val primaryType = selection.networkType

        val ipAddress = selectedInterface?.ipAddress
        val interfaceName = selectedInterface?.interfaceName

        val port = overridePort ?: portManager.findAvailablePort(startPort = 8080)
        val isAvailable = portManager.isPortAvailable(port)

        val serverUrl = if (ipAddress != null && isAvailable) {
            "http://$ipAddress:$port"
        } else null

        val isConnected = primaryType != NetworkType.DISCONNECTED

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

        // Event-driven reactive response to client activity (immediate topbar mode update)
        val clientActivityJob = CoroutineScope(Dispatchers.IO).launch {
            hotspotClientTracker.clientActivityFlow.collect {
                trySend(getNetworkState())
            }
        }

        // 15s fallback ticker (skips during active transfers)
        val tickerJob = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                delay(15_000L.milliseconds)

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
            clientActivityJob.cancel()
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
}
