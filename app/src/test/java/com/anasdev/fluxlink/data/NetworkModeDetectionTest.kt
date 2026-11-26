package com.anasdev.fluxlink.data

import com.anasdev.fluxlink.domain.model.ConnectionMode
import com.anasdev.fluxlink.domain.model.NetworkInterfaceInfo
import com.anasdev.fluxlink.domain.model.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NetworkModeDetectionTest {

    private lateinit var tracker: HotspotClientTracker

    private val wifiInterface = NetworkInterfaceInfo(
        interfaceName = "wlan0",
        ipAddress = "192.168.1.100",
        isWifiOrHotspot = true,
        isPreferred192Subnet = true
    )

    private val hotspotInterface = NetworkInterfaceInfo(
        interfaceName = "ap0",
        ipAddress = "192.168.43.1",
        isWifiOrHotspot = true,
        isPreferred192Subnet = true
    )

    @Before
    fun setUp() {
        tracker = HotspotClientTracker()
    }

    // ==========================================
    // 1. Hotspot Client Tracker Tests
    // ==========================================

    @Test
    fun hotspotClientTrackerDetectsClientOnSubnetWithinWindow() {
        val baseTime = 100_000L
        tracker.recordClient("192.168.43.55", baseTime)

        // Same subnet client within 60s -> true
        assertTrue(tracker.hasConnectedHotspotClient("192.168.43.1", "ap0", baseTime + 10_000L))

        // Same subnet client after 61s -> false (timed out)
        assertFalse(tracker.hasConnectedHotspotClient("192.168.43.1", "ap0", baseTime + 61_000L))

        // Different subnet client -> false
        assertFalse(tracker.hasConnectedHotspotClient("192.168.1.1", "wlan0", baseTime + 10_000L))
    }

    @Test
    fun hotspotClientTrackerIgnoresHostSelfIpAndLoopback() {
        val baseTime = 100_000L
        tracker.recordClient("127.0.0.1", baseTime)
        tracker.recordClient("192.168.43.1", baseTime) // Host self IP

        assertFalse(tracker.hasConnectedHotspotClient("192.168.43.1", "ap0", baseTime + 10_000L))
    }

    @Test
    fun hotspotClientTrackerResetClearsActiveClients() {
        val baseTime = 100_000L
        tracker.recordClient("192.168.43.55", baseTime)
        assertTrue(tracker.hasConnectedHotspotClient("192.168.43.1", "ap0", baseTime + 5_000L))

        tracker.reset()
        assertFalse(tracker.hasConnectedHotspotClient("192.168.43.1", "ap0", baseTime + 5_000L))
    }

    // ==========================================
    // 2. The 4 Mode Selection State Machine Rules
    // ==========================================

    @Test
    fun rule1_wifiOn_hotspotOn_zeroClients_showsWifiRouterMode() {
        val interfaces = listOf(wifiInterface, hotspotInterface)
        val result = NetworkInfoProvider.resolveConnectionModeAndInterface(
            interfaces = interfaces,
            hasWifiConnected = true,
            hasHotspotClient = false
        )

        assertEquals("Mode must be WIFI_ROUTER when 0 hotspot clients are connected", ConnectionMode.WIFI_ROUTER, result.connectionMode)
        assertEquals(NetworkType.WIFI, result.networkType)
        assertEquals("Must select Wi-Fi interface IP for routing", "192.168.1.100", result.selectedInterface?.ipAddress)
        assertEquals("wlan0", result.selectedInterface?.interfaceName)
    }

    @Test
    fun rule2_wifiOn_hotspotOn_withClients_showsHotspotMode() {
        val interfaces = listOf(wifiInterface, hotspotInterface)
        val result = NetworkInfoProvider.resolveConnectionModeAndInterface(
            interfaces = interfaces,
            hasWifiConnected = true,
            hasHotspotClient = true
        )

        assertEquals("Mode must be HOTSPOT_DIRECT when >= 1 hotspot client is connected", ConnectionMode.HOTSPOT_DIRECT, result.connectionMode)
        assertEquals(NetworkType.HOTSPOT, result.networkType)
        assertEquals("Must select Hotspot interface IP", "192.168.43.1", result.selectedInterface?.ipAddress)
        assertEquals("ap0", result.selectedInterface?.interfaceName)
    }

    @Test
    fun rule3_wifiOff_hotspotOn_zeroClients_showsHotspotMode() {
        val interfaces = listOf(hotspotInterface)
        val result = NetworkInfoProvider.resolveConnectionModeAndInterface(
            interfaces = interfaces,
            hasWifiConnected = false,
            hasHotspotClient = false
        )

        assertEquals("Mode must be HOTSPOT_DIRECT when Wi-Fi is disconnected so user sees hotspot IP/QR", ConnectionMode.HOTSPOT_DIRECT, result.connectionMode)
        assertEquals(NetworkType.HOTSPOT, result.networkType)
        assertEquals("192.168.43.1", result.selectedInterface?.ipAddress)
    }

    @Test
    fun rule4_wifiOn_hotspotOff_zeroClients_showsWifiRouterMode() {
        val interfaces = listOf(wifiInterface)
        val result = NetworkInfoProvider.resolveConnectionModeAndInterface(
            interfaces = interfaces,
            hasWifiConnected = true,
            hasHotspotClient = false
        )

        assertEquals("Mode must be WIFI_ROUTER when Hotspot is OFF", ConnectionMode.WIFI_ROUTER, result.connectionMode)
        assertEquals(NetworkType.WIFI, result.networkType)
        assertEquals("192.168.1.100", result.selectedInterface?.ipAddress)
    }

    @Test
    fun emptyInterfaces_returnsDisconnected() {
        val result = NetworkInfoProvider.resolveConnectionModeAndInterface(
            interfaces = emptyList(),
            hasWifiConnected = false,
            hasHotspotClient = false
        )

        assertEquals(ConnectionMode.DISCONNECTED, result.connectionMode)
        assertEquals(NetworkType.DISCONNECTED, result.networkType)
        assertEquals(null, result.selectedInterface)
    }
}
