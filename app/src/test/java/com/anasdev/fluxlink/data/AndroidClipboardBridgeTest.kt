package com.anasdev.fluxlink.data

import com.anasdev.fluxlink.domain.model.ClipboardMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AndroidClipboardBridgeTest {

    private lateinit var echoGuard: ClipboardEchoGuard
    private lateinit var store: ClipboardSessionStore

    @Before
    fun setUp() {
        echoGuard = ClipboardEchoGuard(windowMs = 2000L)
        store = ClipboardSessionStore()
    }

    @Test
    fun echoGuard_detectsDuplicateWithinWindow() {
        echoGuard.register("CopiedText123", timestampMs = 1000L)

        // Query at 2500ms (1500ms elapsed < 2000ms window) -> Should be recognized as echo
        val isEcho = echoGuard.isEcho("CopiedText123", currentTimeMs = 2500L)
        assertTrue("Expected recent write within 2000ms to be identified as echo", isEcho)
    }

    @Test
    fun echoGuard_allowsAfterWindowExpires() {
        echoGuard.register("CopiedText123", timestampMs = 1000L)

        // Query at 3000ms (2000ms elapsed >= 2000ms window) -> Echo guard expired
        val isEchoAtBoundary = echoGuard.isEcho("CopiedText123", currentTimeMs = 3000L)
        assertFalse("Expected echo guard to expire at 2000ms boundary", isEchoAtBoundary)

        // Query at 5000ms -> Clearly expired
        val isEchoLater = echoGuard.isEcho("CopiedText123", currentTimeMs = 5000L)
        assertFalse("Expected echo guard to expire well after 2000ms", isEchoLater)
    }

    @Test
    fun echoGuard_allowsDifferentTextWithinWindow() {
        echoGuard.register("FirstText", timestampMs = 1000L)

        val isEcho = echoGuard.isEcho("DifferentText", currentTimeMs = 1500L)
        assertFalse("Different text should never trigger echo guard", isEcho)
    }

    @Test
    fun echoGuard_clearResetsGuard() {
        echoGuard.register("TextToClear", timestampMs = 1000L)
        echoGuard.clear()

        val isEcho = echoGuard.isEcho("TextToClear", currentTimeMs = 1500L)
        assertFalse("Cleared echo guard should not report echo", isEcho)
    }

    @Test
    fun echoGuard_updatesBaselineOnSubsequentRegister() {
        echoGuard.register("TextA", timestampMs = 1000L)
        echoGuard.register("TextB", timestampMs = 2000L)

        // TextA is no longer active
        assertFalse(echoGuard.isEcho("TextA", currentTimeMs = 2500L))
        // TextB is active
        assertTrue(echoGuard.isEcho("TextB", currentTimeMs = 2500L))
    }

    @Test
    fun storeIntegration_addItemAndRetrieveFromItemsFlow() {
        store.setMode(ClipboardMode.TWO_WAY)

        val added = store.addItem("Hello Android", ClipboardSessionStore.SOURCE_PHONE)
        assertNotNull(added)
        assertEquals("Hello Android", added?.text)
        assertEquals(ClipboardSessionStore.SOURCE_PHONE, added?.source)

        val currentItems = store.getItems()
        assertEquals(1, currentItems.size)
        assertEquals("Hello Android", currentItems[0].text)
    }

    @Test
    fun storeIntegration_receiveOnlyExposesWebOnly() {
        store.setMode(ClipboardMode.RECEIVE_ONLY)

        store.addItem("From Phone", ClipboardSessionStore.SOURCE_PHONE)
        val webItem = store.addItem("From Web", ClipboardSessionStore.SOURCE_WEB)
        assertNotNull("RECEIVE_ONLY should accept web clipboard writes", webItem)

        val visibleItems = store.getItems()
        assertEquals(1, visibleItems.size)
        assertEquals("From Web", visibleItems[0].text)
        assertEquals(ClipboardSessionStore.SOURCE_WEB, visibleItems[0].source)
    }
}
