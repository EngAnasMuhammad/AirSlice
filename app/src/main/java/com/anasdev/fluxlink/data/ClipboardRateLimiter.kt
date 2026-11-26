package com.anasdev.fluxlink.data

import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thread-safe, in-memory sliding-window rate limiter for clipboard operations.
 * Protects against runaway scripts or hostile LAN clients flooding the device
 * with clipboard notifications and allocations.
 */
@Singleton
class ClipboardRateLimiter @Inject constructor() {

    companion object {
        const val DEFAULT_MAX_REQUESTS = 12
        const val DEFAULT_WINDOW_MS = 10_000L // 10 seconds
    }

    private val clientTimestamps = mutableMapOf<String, ArrayDeque<Long>>()

    /**
     * Checks if a request from [clientId] is allowed under the rate limit.
     * If allowed, records the request timestamp.
     */
    @Synchronized
    fun isAllowed(
        clientId: String,
        currentTimeMs: Long = System.currentTimeMillis(),
        maxRequests: Int = DEFAULT_MAX_REQUESTS,
        windowMs: Long = DEFAULT_WINDOW_MS
    ): Boolean {
        val queue = clientTimestamps.getOrPut(clientId) { ArrayDeque() }
        val threshold = currentTimeMs - windowMs

        // Evict expired entries outside the sliding window
        while (queue.isNotEmpty() && queue.first() < threshold) {
            queue.removeFirst()
        }

        if (queue.size < maxRequests) {
            queue.addLast(currentTimeMs)
            return true
        }

        return false
    }

    @Synchronized
    fun reset() {
        clientTimestamps.clear()
    }
}
