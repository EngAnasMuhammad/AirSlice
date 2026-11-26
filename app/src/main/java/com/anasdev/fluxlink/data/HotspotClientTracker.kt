package com.anasdev.fluxlink.data

import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight, thread-safe tracker for active clients connected to the Hotspot AP interface.
 * Implements Android 11+ safe client detection by tracking recent HTTP socket connections,
 * with best-effort /proc/net/arp fallback on Android 10 and below.
 */
@Singleton
class HotspotClientTracker @Inject constructor() {

    companion object {
        const val CLIENT_ACTIVE_WINDOW_MS = 60_000L // 60 seconds
    }

    // IP -> elapsedRealtime() timestamp
    private val recentClients = ConcurrentHashMap<String, Long>()

    private val _clientActivityFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val clientActivityFlow: SharedFlow<Unit> = _clientActivityFlow.asSharedFlow()

    /**
     * Records recent client connection activity from an incoming HTTP request.
     */
    fun recordClient(ip: String, nowMs: Long = SystemClock.elapsedRealtime()) {
        val cleanIp = ip.trim().removePrefix("::ffff:")
        if (cleanIp != "127.0.0.1" && cleanIp != "localhost" && cleanIp.isNotEmpty()) {
            recentClients[cleanIp] = nowMs
            _clientActivityFlow.tryEmit(Unit)
        }
    }

    /**
     * Determines whether any client is actively connected to the hotspot interface.
     * Main-thread safe, non-blocking check.
     */
    fun hasConnectedHotspotClient(
        hotspotInterfaceIp: String?,
        hotspotInterfaceName: String?,
        nowMs: Long = SystemClock.elapsedRealtime()
    ): Boolean {
        if (hotspotInterfaceIp.isNullOrBlank()) return false

        // 1. Primary Signal (All Android Versions): Active HTTP client in last 60s matching hotspot subnet
        val hasRecentHttpClient = recentClients.entries.any { (clientIp, lastSeen) ->
            (nowMs - lastSeen <= CLIENT_ACTIVE_WINDOW_MS) &&
                    isSameSubnet(clientIp, hotspotInterfaceIp) &&
                    clientIp != hotspotInterfaceIp
        }
        if (hasRecentHttpClient) return true

        // 2. Secondary Best-Effort Signal (API <= 29): /proc/net/arp
        return checkArpTableForHotspot(hotspotInterfaceName)
    }

    fun getRecentHotspotClientCount(
        hotspotInterfaceIp: String?,
        nowMs: Long = SystemClock.elapsedRealtime()
    ): Int {
        if (hotspotInterfaceIp.isNullOrBlank()) return 0
        return recentClients.entries.count { (clientIp, lastSeen) ->
            (nowMs - lastSeen <= CLIENT_ACTIVE_WINDOW_MS) &&
                    isSameSubnet(clientIp, hotspotInterfaceIp) &&
                    clientIp != hotspotInterfaceIp
        }
    }

    fun reset() {
        recentClients.clear()
        _clientActivityFlow.tryEmit(Unit)
    }

    fun isSameSubnet(ip1: String, ip2: String): Boolean {
        val parts1 = ip1.split(".")
        val parts2 = ip2.split(".")
        return if (parts1.size == 4 && parts2.size == 4) {
            parts1[0] == parts2[0] && parts1[1] == parts2[1] && parts1[2] == parts2[2]
        } else {
            false
        }
    }

    private fun checkArpTableForHotspot(interfaceName: String?): Boolean {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
            // Android 11+ restricts /proc/net/arp access
            return false
        }
        return try {
            val file = File("/proc/net/arp")
            if (!file.exists() || !file.canRead()) return false

            file.useLines { lines ->
                lines.drop(1).any { line ->
                    val tokens = line.trim().split(Regex("\\s+"))
                    if (tokens.size >= 6) {
                        val hwAddress = tokens[3]
                        val device = tokens[5]
                        val isValidMac = hwAddress.isNotBlank() &&
                                hwAddress != "00:00:00:00:00:00" &&
                                !hwAddress.contains("00:00:00:00:00:00")
                        val matchesDevice = if (!interfaceName.isNullOrBlank()) {
                            device.equals(interfaceName, ignoreCase = true)
                        } else {
                            device.startsWith("ap", ignoreCase = true) ||
                                    device.startsWith("swlan", ignoreCase = true) ||
                                    device.startsWith("wlan1", ignoreCase = true)
                        }
                        isValidMac && matchesDevice
                    } else {
                        false
                    }
                }
            }
        } catch (_: Throwable) {
            false
        }
    }
}
