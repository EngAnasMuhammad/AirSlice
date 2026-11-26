package com.anasdev.fluxlink.data

import com.anasdev.fluxlink.domain.model.ClipboardItem
import com.anasdev.fluxlink.domain.model.ClipboardMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClipboardSessionStore @Inject constructor() {

    companion object {
        const val MAX_HISTORY_ITEMS = 20
        const val MAX_ITEM_BYTES = 64 * 1024 // 64 KB limit
        const val SOURCE_PHONE = "PHONE"
        const val SOURCE_WEB = "WEB"
    }

    private val idSequence = AtomicLong(0L)

    private val _mode = MutableStateFlow(ClipboardMode.OFF)
    val mode: StateFlow<ClipboardMode> = _mode.asStateFlow()

    private val _items = MutableStateFlow<List<ClipboardItem>>(emptyList())
    val items: StateFlow<List<ClipboardItem>> = _items.asStateFlow()

    /**
     * Session 2 Callback hook for when an item is added from the web interface.
     * Session 2 will register an observer here to write into Android's ClipboardManager
     * and trigger the system notification.
     */
    @Volatile
    var onItemAddedFromWeb: ((ClipboardItem) -> Unit)? = null

    @Synchronized
    fun setMode(newMode: ClipboardMode) {
        _mode.value = newMode
    }

    @Synchronized
    fun addItem(rawText: String, source: String): ClipboardItem? {
        if (_mode.value == ClipboardMode.OFF) {
            return null
        }

        // Validate text and 64 KB cap
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            return null
        }

        val byteSize = trimmed.toByteArray(Charsets.UTF_8).size
        if (byteSize > MAX_ITEM_BYTES) {
            return null
        }

        val currentList = _items.value

        // Deduplicate consecutive identical entries
        if (currentList.isNotEmpty() && (currentList.last().text == trimmed)) {
            return currentList.last()
        }

        val newItem = ClipboardItem(
            id = idSequence.incrementAndGet(),
            text = trimmed,
            source = source,
            timestamp = System.currentTimeMillis(),
        )

        val updatedList = (currentList + newItem).takeLast(MAX_HISTORY_ITEMS)
        _items.value = updatedList

        if (source == SOURCE_WEB) {
            try {
                onItemAddedFromWeb?.invoke(newItem)
            } catch (_: Exception) {
                // Callback protection
            }
        }

        return newItem
    }

    @Synchronized
    fun getItems(sinceId: Long? = null): List<ClipboardItem> {
        val currentMode = _mode.value
        val all = _items.value
        val modeFiltered = when (currentMode) {
            ClipboardMode.OFF -> emptyList()
            ClipboardMode.RECEIVE_ONLY -> all.filter { it.source == SOURCE_WEB }
            ClipboardMode.TWO_WAY -> all
        }

        return if (sinceId != null && sinceId > 0) {
            modeFiltered.filter { it.id > sinceId }
        } else {
            modeFiltered
        }
    }

    @Synchronized
    fun removeItem(id: Long): Boolean {
        val currentMode = _mode.value
        if (currentMode == ClipboardMode.OFF) return false
        val currentList = _items.value
        val target = currentList.find { it.id == id } ?: return false

        // In RECEIVE_ONLY mode, reject deletion of PHONE items (shielded from web clients)
        if (currentMode == ClipboardMode.RECEIVE_ONLY && target.source != SOURCE_WEB) {
            return false
        }

        val updated = currentList.filterNot { it.id == id }
        _items.value = updated
        return true
    }

    /**
     * Clears clipboard items requested via web client.
     * In RECEIVE_ONLY mode, only deletes WEB items, keeping shielded PHONE items intact.
     */
    @Synchronized
    fun clearFromWeb(): Boolean {
        val currentMode = _mode.value
        if (currentMode == ClipboardMode.OFF) return false
        val currentList = _items.value
        _items.value = when (currentMode) {
            ClipboardMode.OFF -> currentList
            ClipboardMode.RECEIVE_ONLY -> currentList.filterNot { it.source == SOURCE_WEB }
            ClipboardMode.TWO_WAY -> emptyList()
        }
        return true
    }

    @Synchronized
    fun clear() {
        _items.value = emptyList()
    }

    /**
     * Resets the entire store to default initial state.
     * Hooked into server lifecycle stop to ensure strict RAM-only hygiene.
     */
    @Synchronized
    fun reset() {
        _mode.value = ClipboardMode.OFF
        _items.value = emptyList()
        idSequence.set(0L)
    }

    /**
     * Returns the latest sequence ID visible to clients in the current mode.
     * In RECEIVE_ONLY mode, scoped strictly to visible WEB items so phone activity is shielded.
     */
    @Synchronized
    fun getLatestId(): Long {
        val currentMode = _mode.value
        return when (currentMode) {
            ClipboardMode.OFF -> 0L
            ClipboardMode.RECEIVE_ONLY -> _items.value.lastOrNull { it.source == SOURCE_WEB }?.id ?: 0L
            ClipboardMode.TWO_WAY -> _items.value.lastOrNull()?.id ?: 0L
        }
    }
}
