package com.anasdev.fluxlink.data

import java.net.Socket

/**
 * Handles tearing down active sessions, closing active client sockets,
 * and resetting client rate limiters/trackers when the host IP or URL changes
 * (e.g. Wi-Fi <-> Hotspot transition).
 */
class HostUrlChangeHandler(
    private val securityManager: SecurityManager,
    private val connectedSessionManager: ConnectedSessionManager,
    private val connectionApprovalManager: ConnectionApprovalManager,
    private val hotspotClientTracker: HotspotClientTracker,
    private val clipboardRateLimiter: ClipboardRateLimiter,
    private val activeClientSockets: MutableSet<Socket>
) {
    fun handleHostUrlChanged(newHostIp: String) {
        // 1. Invalidate all active authenticated web sessions/tokens so old clients cannot stay attached
        securityManager.generateNewPairingSession()
        connectedSessionManager.clearAllSessions()
        connectionApprovalManager.clearPendingRequest()

        // 2. Close all currently tracked active client sockets cleanly
        val socketsToClose = activeClientSockets.toList()
        activeClientSockets.clear()
        for (socket in socketsToClose) {
            try {
                socket.close()
            } catch (_: Exception) {
                // Ignore expected SocketException on closed sockets
            }
        }

        // 3. Clear recent client IP tracker used for hotspot detection and rate limiter
        hotspotClientTracker.reset()
        clipboardRateLimiter.reset()
    }
}
