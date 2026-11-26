package com.anasdev.fluxlink.data

import com.anasdev.fluxlink.domain.model.AppLanguage
import com.anasdev.fluxlink.domain.model.AppTheme
import com.anasdev.fluxlink.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.Socket
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class HostUrlChangeTest {

    private lateinit var securityManager: SecurityManager
    private lateinit var connectedSessionManager: ConnectedSessionManager
    private lateinit var connectionApprovalManager: ConnectionApprovalManager
    private lateinit var hotspotClientTracker: HotspotClientTracker
    private lateinit var clipboardRateLimiter: ClipboardRateLimiter
    private lateinit var activeClientSockets: MutableSet<Socket>
    private lateinit var hostUrlChangeHandler: HostUrlChangeHandler

    private class FakeSettingsRepository : SettingsRepository {
        private val pinFlow = MutableStateFlow(true)
        override fun getAppTheme(): Flow<AppTheme> = emptyFlow()
        override suspend fun setAppTheme(theme: AppTheme) {}
        override fun getAppLanguage(): Flow<AppLanguage> = emptyFlow()
        override suspend fun setAppLanguage(language: AppLanguage) {}
        override fun getRequirePinAuth(): Flow<Boolean> = pinFlow
        override suspend fun setRequirePinAuth(requirePin: Boolean) { pinFlow.value = requirePin }
        override fun getShowHiddenFiles(): Flow<Boolean> = emptyFlow()
        override suspend fun setShowHiddenFiles(showHidden: Boolean) {}
        override fun getServerPort(): Flow<Int> = emptyFlow()
        override suspend fun setServerPort(port: Int) {}
        override fun getKeepScreenOn(): Flow<Boolean> = emptyFlow()
        override suspend fun setKeepScreenOn(keepScreenOn: Boolean) {}
    }

    @Before
    fun setUp() {
        securityManager = SecurityManager(FakeSettingsRepository())
        connectedSessionManager = ConnectedSessionManager()
        connectionApprovalManager = ConnectionApprovalManager()
        hotspotClientTracker = HotspotClientTracker()
        clipboardRateLimiter = ClipboardRateLimiter()
        activeClientSockets = Collections.newSetFromMap(ConcurrentHashMap())

        hostUrlChangeHandler = HostUrlChangeHandler(
            securityManager = securityManager,
            connectedSessionManager = connectedSessionManager,
            connectionApprovalManager = connectionApprovalManager,
            hotspotClientTracker = hotspotClientTracker,
            clipboardRateLimiter = clipboardRateLimiter,
            activeClientSockets = activeClientSockets
        )
    }

    @Test
    fun hostUrlChangeInvalidatesSessionsClosesSocketsAndClearsTrackers() {
        // 1. Setup active pairing session and client
        securityManager.generateNewPairingSession()
        val oldToken = securityManager.sessionToken
        val clientIp = "192.168.1.55"
        connectedSessionManager.registerSession(oldToken, clientIp, "Mozilla/5.0")
        assertTrue("Session must be initially valid", connectedSessionManager.isSessionValid(oldToken, clientIp))
        assertEquals(1, connectedSessionManager.connectedClients.value.size)

        // 2. Setup pending approval request
        val pendingReq = connectionApprovalManager.createApprovalRequest("192.168.1.60", "Chrome")
        assertNotNull(pendingReq)
        assertEquals(pendingReq, connectionApprovalManager.pendingRequest.value)

        // 3. Setup active client sockets
        val socket1 = Socket()
        val socket2 = Socket()
        activeClientSockets.add(socket1)
        activeClientSockets.add(socket2)
        assertFalse(socket1.isClosed)
        assertFalse(socket2.isClosed)
        assertEquals(2, activeClientSockets.size)

        // 4. Setup tracked client for hotspot detection
        val testNow = 100_000L
        hotspotClientTracker.recordClient("192.168.43.10", testNow)
        assertTrue(hotspotClientTracker.hasConnectedHotspotClient("192.168.43.1", "ap0", testNow))

        // 5. Trigger host URL changed (e.g. Wi-Fi -> Hotspot)
        hostUrlChangeHandler.handleHostUrlChanged("192.168.43.1")

        // 6. Verify: Sockets are closed and collection cleared
        assertTrue("Tracked socket 1 must be closed", socket1.isClosed)
        assertTrue("Tracked socket 2 must be closed", socket2.isClosed)
        assertTrue("activeClientSockets collection must be cleared", activeClientSockets.isEmpty())

        // 7. Verify: Active sessions wiped
        assertTrue("Connected clients list must be empty", connectedSessionManager.connectedClients.value.isEmpty())
        assertFalse("Old session token must be invalidated", securityManager.validateToken(oldToken))

        // 8. Verify: Pending approval wiped
        assertNull("Pending approval request must be cleared", connectionApprovalManager.pendingRequest.value)

        // 9. Verify: Hotspot client tracker cleared
        assertFalse("Hotspot client tracker must be cleared", hotspotClientTracker.hasConnectedHotspotClient("192.168.43.1", "ap0", testNow))

        // 10. Verify: New session token exists for incoming connections on new URL
        assertTrue("New valid session token should be generated", securityManager.sessionToken.isNotBlank())
        assertTrue(securityManager.sessionToken != oldToken)
    }
}
