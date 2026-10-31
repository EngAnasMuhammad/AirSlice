package com.anasdev.fluxlink.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings

object HotspotHelper {

    /**
     * Safely opens Android's Tethering & Mobile Hotspot Settings screen.
     * Tries direct tethering intents first, then falls back to wireless settings.
     */
    fun openHotspotSettings(context: Context): Boolean {
        val intentsToTry = arrayOf(
            Intent("android.settings.TETHER_SETTINGS"),
            Intent().apply {
                setClassName("com.android.settings", "com.android.settings.TetherSettings")
            },
            Intent().apply {
                setClassName("com.android.settings",
                    $$"com.android.settings.Settings$TetherSettingsActivity"
                )
            },
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )

        for (intent in intentsToTry) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (e: ActivityNotFoundException) {
                // Try next fallback intent
            } catch (e: Exception) {
                // Try next fallback intent
            }
        }
        return false
    }
}
