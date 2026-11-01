package com.anasdev.fluxlink.data

import com.anasdev.fluxlink.BuildConfig
import com.anasdev.fluxlink.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.Collections
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class SecurityManager @Inject constructor(
    private val settingsRepository: SettingsRepository
) {

    @Volatile
    var currentPin: String = ""
        private set

    @Volatile
    var sessionToken: String = ""
        private set

    @Volatile
    var requirePinAuth: Boolean = true
        private set

    private val activeSessions: MutableSet<String> =
        Collections.synchronizedSet(HashSet())

    init {
        settingsRepository.getRequirePinAuth().onEach { enabled ->
            requirePinAuth = enabled
        }.launchIn(CoroutineScope(Dispatchers.IO))
    }

    @Synchronized
    fun generateNewPairingSession(): Pair<String, String> {
        // 1. Generate 4-digit random PIN strictly in ASCII digits (0-9)
        currentPin = String.format(Locale.US, "%04d", Random.nextInt(10000))

        // 2. Generate secure session token
        sessionToken = UUID.randomUUID().toString().replace("-", "").take(16)

        // 3. Reset active sessions
        activeSessions.clear()
        activeSessions.add(sessionToken)

        return Pair(currentPin, sessionToken)
    }

    fun validatePin(inputPin: String): Boolean {
        if (!requirePinAuth) return true
        return currentPin.isNotEmpty() && currentPin == inputPin.trim()
    }

    fun validateToken(inputToken: String): Boolean {
        if (!requirePinAuth) return true
        return sessionToken.isNotEmpty() && sessionToken == inputToken.trim()
    }

    fun isCookieAuthenticated(cookieHeader: String?): Boolean {
        if (!requirePinAuth) return true
        if (cookieHeader.isNullOrBlank() || sessionToken.isEmpty()) return false
        val tokenFromCookie = extractSessionTokenFromCookie(cookieHeader)
        return tokenFromCookie != null && activeSessions.contains(tokenFromCookie)
    }

    fun extractSessionTokenFromCookie(cookieHeader: String): String? {
        val cookies = cookieHeader.split(";")
        for (cookie in cookies) {
            val parts = cookie.trim().split("=")
            if (parts.size == 2 && parts[0].trim() == COOKIE_NAME) {
                return parts[1].trim()
            }
        }
        return null
    }

    @Synchronized
    fun clearSessions() {
        currentPin = ""
        sessionToken = ""
        activeSessions.clear()
    }

    companion object {
        val COOKIE_NAME = BuildConfig.COOKIE_NAME
    }
}
