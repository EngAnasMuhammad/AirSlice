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
    private val connectedSessionManager: ConnectedSessionManager
) {
    // Get connectivityManager by using connectivity service
    // Private because it is used only inside this file
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    fun getNetworkState(overridePort: Int? = null): NetworkState {
        // store the active interfaces list we got
        val interfaces = getFilteredNetworkInterfaces()

        // Determine the currently active connection
        val activeNetwork = connectivityManager?.activeNetwork

        // Get the active network's details without sensitive location information
        val capabilities = connectivityManager?.getNetworkCapabilities(activeNetwork)

        // Check the Wi-Fi is currently transporting
        val hasWifiTransport = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        // Selects the best interface to transport based on a defined score
        val selectedInterface = selectBestWifiOrHotspotInterface(interfaces)

        // A variable with a current connection mode (Wi-Fi, Hotspot, or Disconnected) and assign diconnected as default
        var connectionMode = ConnectionMode.DISCONNECTED
        // A variable with the available network types (Wi-Fi, Hotspot, Cellular, Ethernet or Disconnected) and assign diconnected as default
        var primaryType = NetworkType.DISCONNECTED

        // Check if there is an interface
        if (selectedInterface != null) {
            // store the score of the selected interface
            val score = scoreInterface(selectedInterface)

            // check if the score is more than 100 or (the Wi-Fi is not transporting and the address is private address)
            if (score >= 100 || (!hasWifiTransport && isPrivateIp(selectedInterface.ipAddress))) {
                // Assign connection to hotspot
                connectionMode = ConnectionMode.HOTSPOT_DIRECT
                primaryType = NetworkType.HOTSPOT
            } else if (hasWifiTransport) {
                // Assign connection to Wi-Fi
                connectionMode = ConnectionMode.WIFI_ROUTER
                primaryType = NetworkType.WIFI
            } else {
                // otherwise use the Wi-Fi as the default
                connectionMode = ConnectionMode.WIFI_ROUTER
                primaryType = NetworkType.WIFI
            }
        }

        // Get the IP address and name of the selected interface
        val ipAddress = selectedInterface?.ipAddress
        val interfaceName = selectedInterface?.interfaceName

        // Check if the port is available if not find an available one starting from 8080 increasingly
        val port = overridePort ?: portManager.findAvailablePort(startPort = 8080)
        val isAvailable = portManager.isPortAvailable(port)

        // Assign a url to the server using the IP and port number with if condition to avoid if one is null
        val serverUrl = if (ipAddress != null && isAvailable) {
            "http://$ipAddress:$port"
        } else null

        // Check if the device is connected by unequality between the network type and the disconnect network type
        val isConnected = primaryType != NetworkType.DISCONNECTED

        // Assign these info into the NetworkState to be accessed later in Presentation layer
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


    // Observe the ongoing network state
    fun observeNetworkState(): Flow<NetworkState> = callbackFlow {
        // Send initial state
        trySend(getNetworkState())

        // Sends the changes in the state on three points
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
                delay(15_000L.milliseconds) // 15-second fallback interval

                // Checks if the there is an active transfer
                val hasActiveTransfer = connectedSessionManager.connectedClients.value.any { client ->
                    val t = client.currentActivity.type
                    t == ClientActivityType.STREAMING_VIDEO ||
                            t == ClientActivityType.DOWNLOADING ||
                            t == ClientActivityType.UPLOADING
                }

                // If no active transfer send the network state
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

    // get only the active network interfaces (e.g. Wi-Fi or Hotspot)
    private fun getFilteredNetworkInterfaces(): List<NetworkInterfaceInfo> {
        // Create an empty list of type NetworkInterfaceInfo which hold the main parameters of an interface
        val resultList = mutableListOf<NetworkInterfaceInfo>()

        try {
            // Get all interfaces available on device or return an empty list
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()

            // loop through the list of interfaces
            for (netInt in interfaces.toList()) {
                // If the interface is down (off) or it is a loopback skip it
                if (!netInt.isUp || netInt.isLoopback) continue

                // Store the name of interface in lowercase then compare it with existing list to determine if the interface is for Wi-Fi or Hotspot
                val intName = netInt.name.lowercase(Locale.ROOT)
                val isWifiOrApName = isWifiOrHotspotInterfaceName(intName)

                // Loop through the list of addresses of a single interface
                for (addr in netInt.inetAddresses.toList()) {

                    // If the address is IPv4 and not a loopback or a local IPv6 do the following
                    if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {

                        // Get the host address if found or skip this address if not found
                        val hostAddress = addr.hostAddress ?: continue

                        // Check if the host address start with the standard prefix (192.168.)
                        val is192 = hostAddress.startsWith("192.168.")

                        // Add all the collected info into a single NetworkInterfaceInfo item in the list
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
    // Select Hotspot over Wi-Fi based on the score
    private fun selectBestWifiOrHotspotInterface(interfaces: List<NetworkInterfaceInfo>): NetworkInterfaceInfo? {
        if (interfaces.isEmpty()) return null
        return interfaces.maxByOrNull { scoreInterface(it) }
    }

    // Calculate the score of an interface
    private fun scoreInterface(info: NetworkInterfaceInfo): Int {
        // Get interface name and IP address
        val name = info.interfaceName.lowercase(Locale.ROOT)
        val ip = info.ipAddress

        // Check if the interface name is similar to Wi-Fi or Hotspot
        val isExplicitHotspotName = name.startsWith("ap") ||
                name.startsWith("softap") ||
                name.startsWith("swlan") ||
                name.startsWith("wlan1") ||
                name.startsWith("wlan2") ||
                name.startsWith("rndis") ||
                name.startsWith("p2p") ||
                name.startsWith("tether")

        // Checks the ip address if it matches some private address
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

    // Takes a name and return a Boolean of whether the name represent a Wi-Fi connection or hotspot
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

    // Takes an IP and return a Boolean to determine if it represents a Private IP or not
    private fun isPrivateIp(ip: String): Boolean {
        return ip.startsWith("192.168.") ||
                ip.startsWith("10.") ||
                (ip.startsWith("172.") && is172PrivateSubnet(ip))
    }

    // Check if the IP that start with 172 is a private one or not by checking the second octet of IP
    private fun is172PrivateSubnet(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size >= 2) {
            val secondOctet = parts[1].toIntOrNull() ?: return false
            return secondOctet in 16..31
        }
        return false
    }
}
