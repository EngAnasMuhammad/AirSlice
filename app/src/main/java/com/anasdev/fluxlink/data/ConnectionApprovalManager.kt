package com.anasdev.fluxlink.data

import com.anasdev.fluxlink.domain.model.ApprovalStatus
import com.anasdev.fluxlink.domain.model.PendingClientRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionApprovalManager @Inject constructor() {
    private val activeRequests = ConcurrentHashMap<String, PendingClientRequest>()

    private val _pendingRequest = MutableStateFlow<PendingClientRequest?>(null)
    val pendingRequest: StateFlow<PendingClientRequest?> = _pendingRequest.asStateFlow()

    fun createApprovalRequest(clientIp: String, userAgent: String): PendingClientRequest {
        val requestId = UUID.randomUUID().toString()
        val summary = parseUserAgentSummary(userAgent)
        val request = PendingClientRequest(
            requestId = requestId,
            clientIp = clientIp,
            userAgentSummary = summary,
            status = ApprovalStatus.PENDING
        )
        activeRequests[requestId] = request
        _pendingRequest.value = request
        return request
    }

    fun checkApprovalStatus(requestId: String): ApprovalStatus {
        val req = activeRequests[requestId] ?: return ApprovalStatus.DENIED
        return req.status
    }

    fun approveRequest(requestId: String) {
        val req = activeRequests[requestId] ?: return
        val updated = req.copy(status = ApprovalStatus.APPROVED)
        activeRequests[requestId] = updated
        if (_pendingRequest.value?.requestId == requestId) {
            _pendingRequest.value = null
        }
    }

    fun denyRequest(requestId: String) {
        val req = activeRequests[requestId] ?: return
        val updated = req.copy(status = ApprovalStatus.DENIED)
        activeRequests[requestId] = updated
        if (_pendingRequest.value?.requestId == requestId) {
            _pendingRequest.value = null
        }
    }

    fun clearPendingRequest() {
        _pendingRequest.value = null
    }

    private fun parseUserAgentSummary(ua: String): String {
        if (ua.isBlank()) return "Web Browser"
        val lower = ua.lowercase()

        val browser = when {
            lower.contains("edg") -> "Edge"
            lower.contains("chrome") -> "Chrome"
            lower.contains("firefox") -> "Firefox"
            lower.contains("safari") -> "Safari"
            lower.contains("opera") || lower.contains("opr") -> "Opera"
            else -> "Browser"
        }

        val os = when {
            lower.contains("windows") -> "Windows"
            lower.contains("macintosh") || lower.contains("mac os") -> "Mac"
            lower.contains("android") -> "Android"
            lower.contains("iphone") || lower.contains("ipad") -> "iOS"
            lower.contains("linux") -> "Linux"
            else -> "Device"
        }

        return "$browser • $os"
    }
}
