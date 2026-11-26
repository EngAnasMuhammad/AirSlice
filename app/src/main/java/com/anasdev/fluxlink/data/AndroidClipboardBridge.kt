package com.anasdev.fluxlink.data

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.anasdev.fluxlink.R
import com.anasdev.fluxlink.domain.model.ClipboardItem
import com.anasdev.fluxlink.domain.model.ClipboardMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed class ClipboardReadResult {
    data class Success(val item: ClipboardItem) : ClipboardReadResult()
    object SkippedSensitive : ClipboardReadResult()
    object Empty : ClipboardReadResult()
    object PayloadTooLarge : ClipboardReadResult()
    object ModeOff : ClipboardReadResult()
    object EchoIgnored : ClipboardReadResult()
    data class Error(val message: String) : ClipboardReadResult()
}

class ClipboardEchoGuard(
    private val windowMs: Long = 2000L
) {
    @Volatile
    private var lastText: String? = null

    @Volatile
    private var lastTimestamp: Long = 0L

    fun isEcho(text: String, currentTimeMs: Long): Boolean {
        val t = lastText
        val time = lastTimestamp
        return (t != null && t == text && (currentTimeMs - time) < windowMs)
    }

    fun register(text: String, timestampMs: Long) {
        lastText = text
        lastTimestamp = timestampMs
    }

    fun clear() {
        lastText = null
        lastTimestamp = 0L
    }
}

@Singleton
class AndroidClipboardBridge @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clipboardSessionStore: ClipboardSessionStore
) {

    companion object {
        const val SENSITIVE_EXTRA_LEGACY = "android.content.extra.IS_SENSITIVE"
        const val ECHO_WINDOW_MS = 2000L
        const val COPY_NOTIFICATION_CHANNEL_ID = "airslice_clipboard_channel"
        const val COPY_NOTIFICATION_ID = 2002
        const val ACTION_COPY_TO_CLIPBOARD = "com.anasdev.fluxlink.action.COPY_TO_CLIPBOARD"
        const val EXTRA_CLIP_TEXT = "extra_clip_text"
    }

    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    private val echoGuard = ClipboardEchoGuard(ECHO_WINDOW_MS)

    init {
        createClipboardNotificationChannel()
    }

    /**
     * Checks if the current clipboard contains sensitive data or non-text without
     * reading the clipboard payload, preventing Android 12+ paste toasts on sensitive data.
     */
    fun isClipboardSensitiveOrInvalid(description: ClipDescription?): Boolean {
        if (description == null) return false

        // Check sensitive clip extra
        val hasSensitiveExtra = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false) ?: false
        } else {
            description.extras?.getBoolean(SENSITIVE_EXTRA_LEGACY, false) ?: false
        }

        return hasSensitiveExtra
    }

    /**
     * Evaluates whether a given text matches the recent web write echo guard.
     */
    fun isEcho(text: String, currentTimeMs: Long = SystemClock.elapsedRealtime()): Boolean {
        return echoGuard.isEcho(text, currentTimeMs)
    }

    /**
     * Sets the echo guard baseline when text is copied from the app/web.
     */
    fun registerEchoText(text: String, timestampMs: Long = SystemClock.elapsedRealtime()) {
        echoGuard.register(text, timestampMs)
    }

    fun clearEchoGuard() {
        echoGuard.clear()
    }

    /**
     * Cancels any active fallback clipboard notification (e.g. when server stops).
     */
    fun cancelPendingNotifications() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(COPY_NOTIFICATION_ID)
    }

    /**
     * Pushes current phone clipboard content to the session store.
     * Only proceeds when mode is TWO_WAY.
     */
    fun pushCurrentPhoneClipboard(): ClipboardReadResult {
        if (clipboardSessionStore.mode.value != ClipboardMode.TWO_WAY) {
            return ClipboardReadResult.ModeOff
        }

        val manager = clipboardManager ?: return ClipboardReadResult.Error("Clipboard unavailable")

        // 1. Sensitive-clip pre-check: inspect metadata BEFORE getPrimaryClip()
        val desc = manager.primaryClipDescription
        if (desc == null || !desc.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) && !desc.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)) {
            // Check if there is even content
            if (desc == null) return ClipboardReadResult.Empty
        }

        if (isClipboardSensitiveOrInvalid(desc)) {
            return ClipboardReadResult.SkippedSensitive
        }

        // 2. Read content safely
        val clip = try {
            manager.primaryClip
        } catch (e: Exception) {
            return ClipboardReadResult.Error(e.message ?: "Failed to access clipboard")
        }

        if (clip == null || clip.itemCount == 0) {
            return ClipboardReadResult.Empty
        }

        val item = clip.getItemAt(0)
        val text = item.coerceToText(context)?.toString()?.trim()

        if (text.isNullOrBlank()) {
            return ClipboardReadResult.Empty
        }

        if (text.toByteArray(Charsets.UTF_8).size > ClipboardSessionStore.MAX_ITEM_BYTES) {
            return ClipboardReadResult.PayloadTooLarge
        }

        if (isEcho(text)) {
            return ClipboardReadResult.EchoIgnored
        }

        val added = clipboardSessionStore.addItem(text, ClipboardSessionStore.SOURCE_PHONE)
        return if (added != null) {
            ClipboardReadResult.Success(added)
        } else {
            ClipboardReadResult.Error("Failed to store clipboard item")
        }
    }

    /**
     * Handles incoming Web -> Phone clipboard write.
     * Must be safe to invoke from background socket coroutines.
     */
    suspend fun handleIncomingWebClipboard(item: ClipboardItem) {
        val text = item.text

        // Record echo guard immediately
        registerEchoText(text)

        withContext(Dispatchers.Main) {
            var writeSuccess = false
            try {
                val clipData = ClipData.newPlainText("AirSlice", text)
                clipboardManager?.setPrimaryClip(clipData)
                writeSuccess = true
            } catch (_: Exception) {
                // Background clipboard write rejected by strict OEM ROM (MIUI / HyperOS)
                writeSuccess = false
            }

            if (writeSuccess) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(context, context.getString(R.string.clipboard_copied_toast), Toast.LENGTH_SHORT).show()
                }
            } else {
                // Post fallback notification with one-tap Copy button
                postFallbackCopyNotification(text)
            }
        }
    }

    /**
     * Copies a string to the system clipboard from the app UI,
     * priming the echo-guard so it is not re-echoed.
     */
    fun copyToSystemClipboard(text: String, label: String = "AirSlice"): Boolean {
        registerEchoText(text)
        return try {
            val clip = ClipData.newPlainText(label, text)
            clipboardManager?.setPrimaryClip(clip)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(context, context.getString(R.string.clipboard_copied_toast), Toast.LENGTH_SHORT).show()
            }
            true
        } catch (_: Exception) {
            postFallbackCopyNotification(text)
            false
        }
    }

    @SuppressLint("LaunchActivityFromNotification")
    private fun postFallbackCopyNotification(text: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val copyIntent = Intent(context, ClipboardCopyReceiver::class.java).apply {
            action = ACTION_COPY_TO_CLIPBOARD
            putExtra(EXTRA_CLIP_TEXT, text)
        }
        val copyPendingIntent = PendingIntent.getBroadcast(
            context,
            COPY_NOTIFICATION_ID,
            copyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Show truncated preview (never log full text)
        val preview = if (text.length > 60) text.take(60) + "…" else text

        val notification = NotificationCompat.Builder(context, COPY_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_server_notification)
            .setContentTitle(context.getString(R.string.clipboard_web_received_title))
            .setContentText(preview)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(copyPendingIntent)
            .addAction(
                R.drawable.ic_server_notification,
                context.getString(R.string.clipboard_copy_action),
                copyPendingIntent
            )
            .build()

        notificationManager.notify(COPY_NOTIFICATION_ID, notification)
    }

    private fun createClipboardNotificationChannel() {
        val name = context.getString(R.string.clipboard_channel_name)
        val desc = context.getString(R.string.clipboard_channel_desc)
        val channel = NotificationChannel(
            COPY_NOTIFICATION_CHANNEL_ID,
            name,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = desc
            setShowBadge(true)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.createNotificationChannel(channel)
    }
}
