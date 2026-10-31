package com.anasdev.fluxlink.data

import com.anasdev.fluxlink.BuildConfig
import java.util.Collections
import java.util.UUID
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityManager @Inject constructor() {

    @Volatile
    var currentPin: String = ""
        private set

    @Volatile
    var sessionToken: String = ""
        private set

    private val activeSessions: MutableSet<String> =
        Collections.synchronizedSet(HashSet())

    @Synchronized
    fun generateNewPairingSession(): Pair<String, String> {
        // 1. Generate 4-digit random PIN (e.g. 4829)
        currentPin = String.format("%04d", Random.nextInt(10000))

        // 2. Generate secure session token
        sessionToken = UUID.randomUUID().toString().replace("-", "").take(16)

        // 3. Reset active sessions
        activeSessions.clear()
        activeSessions.add(sessionToken)

        return Pair(currentPin, sessionToken)
    }

    fun validatePin(inputPin: String): Boolean {
        return currentPin.isNotEmpty() && currentPin == inputPin.trim()
    }

    fun validateToken(inputToken: String): Boolean {
        return sessionToken.isNotEmpty() && sessionToken == inputToken.trim()
    }

    fun isCookieAuthenticated(cookieHeader: String?): Boolean {
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
