package com.anasdev.fluxlink.data

import com.anasdev.fluxlink.domain.model.ClientActivity
import com.anasdev.fluxlink.domain.model.ClientActivityType
import com.anasdev.fluxlink.domain.model.ConnectedClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectedSessionManager @Inject constructor() {
    private val sessionsMap = ConcurrentHashMap<String, ConnectedClient>()
    private val activityJobs = ConcurrentHashMap<String, Job>()
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _connectedClients = MutableStateFlow<List<ConnectedClient>>(emptyList())
    val connectedClients: StateFlow<List<ConnectedClient>> = _connectedClients.asStateFlow()

    fun registerSession(token: String, clientIp: String, userAgent: String) {
        val normalizedIp = normalizeIp(clientIp)
        val summary = parseUserAgentSummary(userAgent)
        val now = System.currentTimeMillis()

        val existing = sessionsMap[normalizedIp]
        val updatedClient = if (existing == null) {
            ConnectedClient(
                sessionId = token,
                ipAddress = normalizedIp,
                deviceAndBrowser = summary,
                connectedAtMillis = now,
                lastActiveMillis = now
            )
        } else {
            existing.copy(
                sessionId = token,
                lastActiveMillis = now
            )
        }
        sessionsMap[normalizedIp] = updatedClient
        emitStateFlow()
    }

    fun isSessionValid(token: String, clientIp: String): Boolean {
        val normalizedIp = normalizeIp(clientIp)
        val client = sessionsMap[normalizedIp] ?: return false
        val updated = client.copy(lastActiveMillis = System.currentTimeMillis())
        sessionsMap[normalizedIp] = updated
        emitStateFlow()
        return true
    }

    fun updateClientActivity(clientIp: String, type: ClientActivityType, fileName: String? = null) {
        val normalizedIp = normalizeIp(clientIp)
        val client = sessionsMap[normalizedIp] ?: return
        val now = System.currentTimeMillis()

        // Priority Shield: Do not overwrite active media ops with low-priority browsing
        val currentType = client.currentActivity.type
        if ((type == ClientActivityType.BROWSING || type == ClientActivityType.IDLE) &&
            (currentType == ClientActivityType.STREAMING_VIDEO ||
             currentType == ClientActivityType.DOWNLOADING ||
             currentType == ClientActivityType.UPLOADING)) {
            val touched = client.copy(lastActiveMillis = now)
            sessionsMap[normalizedIp] = touched
            emitStateFlow()
            return
        }

        // Cancel previous pending idle reset job for this client
        activityJobs[normalizedIp]?.cancel()

        val newActivity = ClientActivity(
            type = type,
            targetFileName = fileName,
            updatedAtMillis = now
        )
        val updatedClient = client.copy(
            lastActiveMillis = now,
            rawActivity = newActivity
        )
        sessionsMap[normalizedIp] = updatedClient
        emitStateFlow()

        // Schedule automatic 4-second hold timer before returning to IDLE
        if (type != ClientActivityType.IDLE) {
            activityJobs[normalizedIp] = scope.launch {
                delay(4000L)
                clearClientActivity(normalizedIp)
            }
        }
    }

    fun clearClientActivity(clientIp: String) {
        val normalizedIp = normalizeIp(clientIp)
        val client = sessionsMap[normalizedIp] ?: return
        if (client.currentActivity.type == ClientActivityType.IDLE) return

        activityJobs[normalizedIp]?.cancel()
        val now = System.currentTimeMillis()
        val updatedClient = client.copy(
            lastActiveMillis = now,
            rawActivity = ClientActivity(type = ClientActivityType.IDLE, updatedAtMillis = now)
        )
        sessionsMap[normalizedIp] = updatedClient
        emitStateFlow()
    }

    fun revokeSession(sessionId: String) {
        val entry = sessionsMap.entries.firstOrNull { it.value.sessionId == sessionId || it.key == sessionId }
        if (entry != null) {
            activityJobs[entry.key]?.cancel()
            activityJobs.remove(entry.key)
            sessionsMap.remove(entry.key)
            emitStateFlow()
        }
    }

    fun clearAllSessions() {
        activityJobs.values.forEach { it.cancel() }
        activityJobs.clear()
        sessionsMap.clear()
        emitStateFlow()
    }

    private fun emitStateFlow() {
        _connectedClients.value = sessionsMap.values.sortedByDescending { it.lastActiveMillis }
    }

    private fun normalizeIp(ip: String): String {
        return ip.trim().removePrefix("::ffff:")
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
