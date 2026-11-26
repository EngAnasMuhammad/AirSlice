package com.anasdev.fluxlink.data

import com.anasdev.fluxlink.domain.model.ClipboardMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClipboardSessionStoreTest {

    private lateinit var store: ClipboardSessionStore

    @Before
    fun setUp() {
        store = ClipboardSessionStore()
    }

    @Test
    fun defaultModeIsOff() {
        assertEquals(ClipboardMode.OFF, store.mode.value)
        assertTrue(store.items.value.isEmpty())
        assertEquals(0L, store.getLatestId())
    }

    @Test
    fun cannotAddItemWhenModeIsOff() {
        val result = store.addItem("Hello World", ClipboardSessionStore.SOURCE_WEB)
        assertNull(result)
        assertTrue(store.getItems().isEmpty())
    }

    @Test
    fun addsItemSuccessfullyWhenEnabled() {
        store.setMode(ClipboardMode.TWO_WAY)
        val item = store.addItem("Hello World", ClipboardSessionStore.SOURCE_PHONE)
        assertNotNull(item)
        assertEquals("Hello World", item?.text)
        assertEquals("PHONE", item?.source)
        assertEquals(1L, item?.id)
        assertEquals(1, store.getItems().size)
    }

    @Test
    fun deduplicatesConsecutiveIdenticalItems() {
        store.setMode(ClipboardMode.TWO_WAY)
        val first = store.addItem("Same Text", ClipboardSessionStore.SOURCE_PHONE)
        val second = store.addItem("Same Text", ClipboardSessionStore.SOURCE_PHONE)

        assertNotNull(first)
        assertNotNull(second)
        assertEquals(first?.id, second?.id)
        assertEquals(1, store.getItems().size)

        // Non-consecutive duplicate should be added
        store.addItem("Different Text", ClipboardSessionStore.SOURCE_PHONE)
        val third = store.addItem("Same Text", ClipboardSessionStore.SOURCE_PHONE)
        assertEquals(3, store.getItems().size)
        assertTrue(third!!.id > first!!.id)
    }

    @Test
    fun capsHistoryAt20Items() {
        store.setMode(ClipboardMode.TWO_WAY)
        for (i in 1..25) {
            store.addItem("Item $i", ClipboardSessionStore.SOURCE_WEB)
        }

        val allItems = store.getItems()
        assertEquals(20, allItems.size)
        assertEquals("Item 6", allItems.first().text)
        assertEquals("Item 25", allItems.last().text)
    }

    @Test
    fun capsSingleItemSizeAt64KB() {
        store.setMode(ClipboardMode.TWO_WAY)

        // 64 KB exact
        val valid64KB = "A".repeat(64 * 1024)
        val resultValid = store.addItem(valid64KB, ClipboardSessionStore.SOURCE_WEB)
        assertNotNull(resultValid)

        // 64 KB + 1 byte
        val invalidOversized = "A".repeat((64 * 1024) + 1)
        val resultInvalid = store.addItem(invalidOversized, ClipboardSessionStore.SOURCE_WEB)
        assertNull(resultInvalid)
    }

    @Test
    fun receiveOnlyModeExposesOnlyWebItems() {
        store.setMode(ClipboardMode.TWO_WAY)
        store.addItem("Phone Secret", ClipboardSessionStore.SOURCE_PHONE)
        store.addItem("Web Snippet", ClipboardSessionStore.SOURCE_WEB)

        // In TWO_WAY, both are visible
        assertEquals(2, store.getItems().size)

        // Switch to RECEIVE_ONLY (Web -> Phone only)
        store.setMode(ClipboardMode.RECEIVE_ONLY)
        val receiveOnlyItems = store.getItems()
        assertEquals(1, receiveOnlyItems.size)
        assertEquals("Web Snippet", receiveOnlyItems.first().text)
    }

    @Test
    fun sinceQueryFilterReturnsOnlyNewerItems() {
        store.setMode(ClipboardMode.TWO_WAY)
        val item1 = store.addItem("Item 1", ClipboardSessionStore.SOURCE_WEB)
        val item2 = store.addItem("Item 2", ClipboardSessionStore.SOURCE_WEB)
        val item3 = store.addItem("Item 3", ClipboardSessionStore.SOURCE_WEB)

        val filtered = store.getItems(sinceId = item1!!.id)
        assertEquals(2, filtered.size)
        assertEquals(listOf(item2!!.id, item3!!.id), filtered.map { it.id })
    }

    @Test
    fun clearWipesItems() {
        store.setMode(ClipboardMode.TWO_WAY)
        store.addItem("Item 1", ClipboardSessionStore.SOURCE_WEB)
        store.addItem("Item 2", ClipboardSessionStore.SOURCE_WEB)
        assertEquals(2, store.getItems().size)

        store.clear()
        assertTrue(store.getItems().isEmpty())
        assertEquals(ClipboardMode.TWO_WAY, store.mode.value) // mode unchanged on clear
    }

    @Test
    fun resetWipesItemsAndResetsModeToOff() {
        store.setMode(ClipboardMode.TWO_WAY)
        store.addItem("Item 1", ClipboardSessionStore.SOURCE_WEB)

        store.reset()
        assertEquals(ClipboardMode.OFF, store.mode.value)
        assertTrue(store.getItems().isEmpty())
        assertEquals(0L, store.getLatestId())
    }

    @Test
    fun webCallbackHookIsTriggeredOnWebItem() {
        store.setMode(ClipboardMode.TWO_WAY)
        var callbackReceived: String? = null
        store.onItemAddedFromWeb = { item ->
            callbackReceived = item.text
        }

        store.addItem("From Phone", ClipboardSessionStore.SOURCE_PHONE)
        assertNull(callbackReceived)

        store.addItem("From Web", ClipboardSessionStore.SOURCE_WEB)
        assertEquals("From Web", callbackReceived)
    }

    @Test
    fun removeItemRemovesSpecificItemById() {
        store.setMode(ClipboardMode.TWO_WAY)
        val item1 = store.addItem("Item 1", ClipboardSessionStore.SOURCE_WEB)
        val item2 = store.addItem("Item 2", ClipboardSessionStore.SOURCE_WEB)

        assertTrue(store.removeItem(item1!!.id))
        assertEquals(1, store.getItems().size)
        assertEquals(item2!!.id, store.getItems().first().id)
        assertFalse(store.removeItem(999L))
    }
}
