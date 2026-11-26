package com.anasdev.fluxlink.data

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.anasdev.fluxlink.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ClipboardCopyReceiver : BroadcastReceiver() {

    @Inject
    lateinit var clipboardBridge: AndroidClipboardBridge

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        if (intent.action == AndroidClipboardBridge.ACTION_COPY_TO_CLIPBOARD) {
            val text = intent.getStringExtra(AndroidClipboardBridge.EXTRA_CLIP_TEXT) ?: return

            // Prime echo guard through bridge and copy
            clipboardBridge.registerEchoText(text)

            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipData = ClipData.newPlainText("AirSlice", text)
            clipboardManager?.setPrimaryClip(clipData)

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(context, context.getString(R.string.clipboard_copied_toast), Toast.LENGTH_SHORT).show()
            }

            // Dismiss the notification
            NotificationManagerCompat.from(context).cancel(AndroidClipboardBridge.COPY_NOTIFICATION_ID)
        }
    }
}
