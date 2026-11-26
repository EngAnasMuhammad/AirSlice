package com.anasdev.fluxlink.presentation

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.anasdev.fluxlink.R
import com.anasdev.fluxlink.data.AndroidClipboardBridge
import com.anasdev.fluxlink.data.ClipboardReadResult
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ClipboardPushActivity : AppCompatActivity() {

    @Inject
    lateinit var clipboardBridge: AndroidClipboardBridge

    private var hasExecuted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !hasExecuted) {
            hasExecuted = true
            executePushAndFinish()
        }
    }

    private fun executePushAndFinish() {
        val result = clipboardBridge.pushCurrentPhoneClipboard()

        val toastMessage = when (result) {
            is ClipboardReadResult.Success -> getString(R.string.clipboard_sent_to_web)
            is ClipboardReadResult.SkippedSensitive -> getString(R.string.clipboard_skipped_sensitive)
            is ClipboardReadResult.Empty -> getString(R.string.clipboard_empty)
            is ClipboardReadResult.PayloadTooLarge -> getString(R.string.clipboard_too_large)
            is ClipboardReadResult.ModeOff -> getString(R.string.clipboard_sharing_disabled)
            is ClipboardReadResult.EchoIgnored -> getString(R.string.clipboard_already_synced)
            is ClipboardReadResult.Error -> result.message
        }

        Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show()
        finish()
    }
}
