package com.anasdev.fluxlink.data

import com.anasdev.fluxlink.domain.model.ClipboardMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ClipboardSecurityAndEdgeCasesTest {

    private lateinit var store: ClipboardSessionStore
    private lateinit var rateLimiter: ClipboardRateLimiter

    @Before
    fun setUp() {
        store = ClipboardSessionStore()
        rateLimiter = ClipboardRateLimiter()
    }

    // ==========================================
    // 1. JSON Escaping and Extraction Security
    // ==========================================

    @Test
    fun jsonEscapeAndExtractHandlesSpecialCharacters() {
        val originalText = "Hello \"World\"\nLine 2\tTabbed\\Backslash\rCarriage\bBack\u000CForm"
        val escaped = escapeJsonTestHelper(originalText)

        // Verify JSON string format
        val jsonPayload = "{\"text\":\"$escaped\"}"
        val extracted = extractJsonStringFieldTestHelper(jsonPayload, "text")

        assertEquals(originalText, extracted)
    }

    @Test
    fun jsonEscapeHandlesControlCharactersAndScripts() {
        val xssPayload = "<script>alert(\"XSS & 'quotes'\")</script>"
        val escapedXss = escapeJsonTestHelper(xssPayload)
        val jsonPayload = "{\"text\":\"$escapedXss\"}"
        val extracted = extractJsonStringFieldTestHelper(jsonPayload, "text")
        assertEquals(xssPayload, extracted)

        // Control characters < 0x20 must be escaped as \u00xx
        val textWithNullAndEsc = "Prefix\u0000Middle\u001BEnd"
        val escapedCtrl = escapeJsonTestHelper(textWithNullAndEsc)
        assertTrue(escapedCtrl.contains("\\u0000"))
        assertTrue(escapedCtrl.contains("\\u001b"))

        val jsonCtrl = "{\"text\":\"$escapedCtrl\"}"
        val extractedCtrl = extractJsonStringFieldTestHelper(jsonCtrl, "text")
        assertEquals(textWithNullAndEsc, extractedCtrl)
    }

    @Test
    fun jsonEscapeAndExtractHandlesArabicAndEmojis() {
        val unicodeText = "مرحبا بالعالم! 🚀 📋 ⚡ 🔒 اختبار الحافظة"
        val escaped = escapeJsonTestHelper(unicodeText)
        val jsonPayload = "{\"text\":\"$escaped\"}"
        val extracted = extractJsonStringFieldTestHelper(jsonPayload, "text")

        assertEquals(unicodeText, extracted)
    }

    @Test
    fun extractJsonFieldHandlesMalformedOrMissingField() {
        val missing = "{\"other\":\"value\"}"
        assertNull(extractJsonStringFieldTestHelper(missing, "text"))

        val malformed = "{\"text\": unquoted}"
        assertNull(extractJsonStringFieldTestHelper(malformed, "text"))

        val unterminated = "{\"text\": \"unclosed string"
        assertNull(extractJsonStringFieldTestHelper(unterminated, "text"))
    }

    // ==========================================
    // 2. RECEIVE_ONLY Isolation & Shielding
    // ==========================================

    @Test
    fun receiveOnlyShieldsPhoneItemsFromDeletion() {
        store.setMode(ClipboardMode.TWO_WAY)
        val phoneItem = store.addItem("Phone Sensitive Password", ClipboardSessionStore.SOURCE_PHONE)
        val webItem = store.addItem("Web Public Note", ClipboardSessionStore.SOURCE_WEB)
        assertNotNull(phoneItem)
        assertNotNull(webItem)

        // Switch to RECEIVE_ONLY mode (Web -> Phone only)
        store.setMode(ClipboardMode.RECEIVE_ONLY)

        // Attempting to delete phoneItem by ID must be rejected/shielded
        val phoneDeleteResult = store.removeItem(phoneItem!!.id)
        assertFalse("Phone item deletion must be rejected in RECEIVE_ONLY", phoneDeleteResult)

        // Web item can be deleted normally
        val webDeleteResult = store.removeItem(webItem!!.id)
        assertTrue("Web item deletion should succeed in RECEIVE_ONLY", webDeleteResult)

        // Switch back to TWO_WAY to verify phone item was NEVER deleted
        store.setMode(ClipboardMode.TWO_WAY)
        val remaining = store.getItems()
        assertEquals(1, remaining.size)
        assertEquals("Phone Sensitive Password", remaining[0].text)
    }

    @Test
    fun receiveOnlyClearFromWebLeavesPhoneItemsIntact() {
        store.setMode(ClipboardMode.TWO_WAY)
        store.addItem("Phone Data 1", ClipboardSessionStore.SOURCE_PHONE)
        store.addItem("Web Data 1", ClipboardSessionStore.SOURCE_WEB)
        store.addItem("Phone Data 2", ClipboardSessionStore.SOURCE_PHONE)
        store.addItem("Web Data 2", ClipboardSessionStore.SOURCE_WEB)

        store.setMode(ClipboardMode.RECEIVE_ONLY)
        // Web requests clear all
        store.clearFromWeb()

        // Visible in RECEIVE_ONLY is now empty
        assertTrue(store.getItems().isEmpty())

        // In TWO_WAY, phone items are still completely preserved
        store.setMode(ClipboardMode.TWO_WAY)
        val items = store.getItems()
        assertEquals(2, items.size)
        assertTrue(items.all { it.source == ClipboardSessionStore.SOURCE_PHONE })
    }

    @Test
    fun receiveOnlyScopesLatestIdToWebOnly() {
        store.setMode(ClipboardMode.TWO_WAY)
        val webItem1 = store.addItem("Web 1", ClipboardSessionStore.SOURCE_WEB)
        assertEquals(webItem1!!.id, store.getLatestId())

        store.setMode(ClipboardMode.RECEIVE_ONLY)
        assertEquals(webItem1.id, store.getLatestId())

        // When a new phone item is copied, latestId in RECEIVE_ONLY should NOT leak or update
        store.addItem("Phone 2", ClipboardSessionStore.SOURCE_PHONE)
        assertEquals("latestId must not update for phone item in RECEIVE_ONLY", webItem1.id, store.getLatestId())

        // A new web item updates latestId
        val webItem2 = store.addItem("Web 2", ClipboardSessionStore.SOURCE_WEB)
        assertEquals(webItem2!!.id, store.getLatestId())
    }

    // ==========================================
    // 3. Sliding-Window Rate Limiter
    // ==========================================

    @Test
    fun rateLimiterEnforces12RequestsPer10Seconds() {
        val clientIp = "192.168.1.50"
        val baseTime = 100_000L

        // First 12 requests within window must pass
        for (i in 1..12) {
            assertTrue("Request $i should be allowed", rateLimiter.isAllowed(clientIp, baseTime + i * 100))
        }

        // 13th request within window must be rejected (429)
        assertFalse("13th request must be rejected", rateLimiter.isAllowed(clientIp, baseTime + 1500))

        // Different IP should still be allowed its full quota
        val otherIp = "192.168.1.99"
        assertTrue("Different IP must not be rate limited", rateLimiter.isAllowed(otherIp, baseTime + 1500))

        // After window expires (>10s), requests are allowed again
        val expiredTime = baseTime + 10_500L
        assertTrue("Request after window expiration should be allowed", rateLimiter.isAllowed(clientIp, expiredTime))
    }

    @Test
    fun rateLimiterResetClearsAllEntries() {
        val clientIp = "192.168.1.50"
        for (i in 1..12) {
            rateLimiter.isAllowed(clientIp)
        }
        assertFalse(rateLimiter.isAllowed(clientIp))

        rateLimiter.reset()
        assertTrue("Reset must immediately allow new requests", rateLimiter.isAllowed(clientIp))
    }

    // ==========================================
    // 4. Concurrency Stress Test
    // ==========================================

    @Test
    fun concurrentAddAndRemoveMaintainsInvariants() {
        store.setMode(ClipboardMode.TWO_WAY)
        val threadCount = 8
        val opsPerThread = 50
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)

        for (t in 0 until threadCount) {
            Thread {
                try {
                    for (i in 0 until opsPerThread) {
                        val item = store.addItem("Thread $t Item $i", if (i % 2 == 0) ClipboardSessionStore.SOURCE_WEB else ClipboardSessionStore.SOURCE_PHONE)
                        if (item != null) {
                            successCount.incrementAndGet()
                        }
                        if (i % 5 == 0) {
                            store.getItems()
                        }
                    }
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        assertTrue("All threads must complete within timeout", latch.await(5, TimeUnit.SECONDS))
        assertTrue(successCount.get() > 0)

        // Store must never exceed MAX_HISTORY_ITEMS (20)
        val finalItems = store.getItems()
        assertTrue("Store must never exceed 20 items, got ${finalItems.size}", finalItems.size <= ClipboardSessionStore.MAX_HISTORY_ITEMS)
    }

    // ==========================================
    // Helper functions reproducing JSON logic
    // ==========================================

    private fun escapeJsonTestHelper(str: String): String {
        val sb = StringBuilder(str.length + 16)
        for (i in 0 until str.length) {
            val c = str[i]
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (c.code < 0x20) {
                        val hex = Integer.toHexString(c.code)
                        sb.append("\\u")
                        repeat(4 - hex.length) {
                            sb.append('0')
                        }
                        sb.append(hex)
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun extractJsonStringFieldTestHelper(json: String, fieldName: String): String? {
        val keyPattern = "\"$fieldName\""
        val keyIndex = json.indexOf(keyPattern)
        if (keyIndex == -1) return null

        var index = keyIndex + keyPattern.length

        while (index < json.length && json[index].isWhitespace()) {
            index++
        }
        if (index >= json.length || json[index] != ':') return null
        index++

        while (index < json.length && json[index].isWhitespace()) {
            index++
        }
        if (index >= json.length || json[index] != '"') return null
        index++

        val sb = StringBuilder()
        var isEscaped = false

        while (index < json.length) {
            val ch = json[index]
            if (isEscaped) {
                when (ch) {
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'u' -> {
                        if (index + 4 < json.length) {
                            val hex = json.substring(index + 1, index + 5)
                            try {
                                val codePoint = hex.toInt(16)
                                sb.append(codePoint.toChar())
                                index += 4
                            } catch (_: NumberFormatException) {
                                sb.append("\\u")
                            }
                        } else {
                            sb.append("\\u")
                        }
                    }
                    else -> sb.append(ch)
                }
                isEscaped = false
            } else {
                if (ch == '\\') {
                    isEscaped = true
                } else if (ch == '"') {
                    return sb.toString()
                } else {
                    sb.append(ch)
                }
            }
            index++
        }

        return null
    }
}
