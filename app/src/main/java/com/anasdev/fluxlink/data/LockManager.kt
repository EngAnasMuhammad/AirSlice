package com.anasdev.fluxlink.data

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import com.anasdev.fluxlink.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LockManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    val isCpuLockHeld: Boolean
        get() = wakeLock?.isHeld == true

    val isWifiLockHeld: Boolean
        get() = wifiLock?.isHeld == true

    @Synchronized
    fun acquireLocks() {
        // 1. Acquire Partial CPU WakeLock to keep Ktor coroutines active in background
        if (wakeLock == null) {
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                BuildConfig.CPU_WAKELOCK_TAG
            )?.apply {
                setReferenceCounted(false)
            }
        }

        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(10 * 60 * 1000L /* 10 minutes timeout fallback */)
        }

        // 2. Acquire High-Performance / Low-Latency Wi-Fi Lock to prevent Wi-Fi radio sleep
        if (wifiLock == null) {
            val lockMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION")
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }

            wifiLock = wifiManager?.createWifiLock(
                lockMode,
                BuildConfig.WIFI_LOCK_TAG
            )?.apply {
                setReferenceCounted(false)
            }
        }

        if (wifiLock?.isHeld == false) {
            wifiLock?.acquire()
        }
    }

    @Synchronized
    fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            wakeLock = null
        }

        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            wifiLock = null
        }
    }
}
