package com.anasdev.fluxlink.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.anasdev.fluxlink.BuildConfig
import com.anasdev.fluxlink.R
import com.anasdev.fluxlink.data.AndroidClipboardBridge
import com.anasdev.fluxlink.data.ClipboardSessionStore
import com.anasdev.fluxlink.data.LocalServerEngine
import com.anasdev.fluxlink.data.LockManager
import com.anasdev.fluxlink.data.NetworkInfoProvider
import com.anasdev.fluxlink.domain.model.ClipboardMode
import com.anasdev.fluxlink.presentation.ClipboardPushActivity
import com.anasdev.fluxlink.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ServerForegroundService : Service() {

    @Inject
    lateinit var networkInfoProvider: NetworkInfoProvider

    @Inject
    lateinit var lockManager: LockManager

    @Inject
    lateinit var localServerEngine: LocalServerEngine

    @Inject
    lateinit var clipboardSessionStore: ClipboardSessionStore

    @Inject
    lateinit var clipboardBridge: AndroidClipboardBridge

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var networkMonitorJob: Job? = null
    private var reconnectJob: Job? = null
    private var clipboardModeObserverJob: Job? = null

    @Volatile
    private var isUserStopping = false

    @Volatile
    private var currentActiveServerUrl: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        handleStopServer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SERVER -> {
                val requestedPort = intent.getIntExtra(EXTRA_PORT, BuildConfig.DEFAULT_SERVER_PORT)
                handleStartServer(requestedPort)
            }
            ACTION_STOP_SERVER -> {
                handleStopServer()
            }
        }
        return START_NOT_STICKY
    }

    private fun handleStartServer(port: Int) {
        // 1. Create Notification Channel & startForeground FIRST to fulfill OS requirement
        createNotificationChannel()
        currentActiveServerUrl = "http://localhost:$port"
        val initialNotification = buildServerNotification(currentActiveServerUrl)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        // 2. Check network connectivity
        val networkState = networkInfoProvider.getNetworkState(overridePort = port)
        if (!networkState.isConnected || networkState.selectedIpAddress == null) {
            handleStopServer()
            return
        }

        // 3. Update notification with active IP URL
        currentActiveServerUrl = networkState.serverUrl ?: "http://${networkState.selectedIpAddress}:$port"
        val activeNotification = buildServerNotification(currentActiveServerUrl)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(NOTIFICATION_ID, activeNotification)

        // 4. Acquire CPU Wake Lock and Wi-Fi Lock
        lockManager.acquireLocks()

        // 5. Wire incoming Web -> Phone clipboard bridge callback
        clipboardSessionStore.onItemAddedFromWeb = { item ->
            serviceScope.launch {
                clipboardBridge.handleIncomingWebClipboard(item)
            }
        }

        // 6. Start HTTP Server Engine
        val started = localServerEngine.startServer(networkState.selectedPort)
        if (!started) {
            handleStopServer()
            return
        }

        // 7. Reset stop flag and monitor network & clipboard mode
        isUserStopping = false
        startNetworkMonitoring(networkState.selectedPort)
        startClipboardModeMonitoring()
    }

    private fun startClipboardModeMonitoring() {
        clipboardModeObserverJob?.cancel()
        clipboardModeObserverJob = serviceScope.launch {
            clipboardSessionStore.mode.collectLatest {
                if (currentActiveServerUrl.isNotEmpty() && !isUserStopping) {
                    val updatedNotification = buildServerNotification(currentActiveServerUrl)
                    val notificationManager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
                    notificationManager?.notify(NOTIFICATION_ID, updatedNotification)
                }
            }
        }
    }

    private fun startNetworkMonitoring(activePort: Int) {
        networkMonitorJob?.cancel()
        networkMonitorJob = serviceScope.launch {
            networkInfoProvider.observeNetworkState().collectLatest { state ->
                if (state.isConnected && state.selectedIpAddress != null) {
                    // Network is healthy / recovered!
                    reconnectJob?.cancel()
                    reconnectJob = null
                    localServerEngine.setReconnectingStatus(false)

                    val newUrl = state.serverUrl ?: "http://${state.selectedIpAddress}:$activePort"
                    if (currentActiveServerUrl.isNotEmpty() && currentActiveServerUrl != newUrl) {
                        localServerEngine.onHostUrlChanged(state.selectedIpAddress)
                    }
                    currentActiveServerUrl = newUrl
                    val activeNotification = buildServerNotification(currentActiveServerUrl)
                    val notificationManager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
                    notificationManager?.notify(NOTIFICATION_ID, activeNotification)
                } else if (!isUserStopping && reconnectJob == null) {
                    // Connection lost: initiate exponential backoff reconnection
                    startReconnectionBackoff(activePort)
                }
            }
        }
    }

    private fun startReconnectionBackoff(activePort: Int) {
        reconnectJob?.cancel()
        reconnectJob = serviceScope.launch {
            localServerEngine.setReconnectingStatus(true)

            val backoffDelays = listOf(2_000L, 4_000L, 8_000L, 16_000L)
            var reconnected = false

            for ((index, delayMs) in backoffDelays.withIndex()) {
                val attempt = index + 1
                val totalAttempts = backoffDelays.size

                val reconnectNotification = buildReconnectingNotification(attempt, totalAttempts)
                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.notify(NOTIFICATION_ID, reconnectNotification)

                kotlinx.coroutines.delay(kotlin.time.Duration.parse("${delayMs}ms"))

                if (isUserStopping) return@launch

                val currentState = networkInfoProvider.getNetworkState(overridePort = activePort)
                if (currentState.isConnected && currentState.selectedIpAddress != null) {
                    reconnected = true
                    localServerEngine.setReconnectingStatus(false)

                    currentActiveServerUrl = currentState.serverUrl ?: "http://${currentState.selectedIpAddress}:$activePort"
                    val activeNotification = buildServerNotification(currentActiveServerUrl)
                    notificationManager?.notify(NOTIFICATION_ID, activeNotification)
                    break
                }
            }

            if (!reconnected && !isUserStopping) {
                // All backoff attempts exhausted (total 30 seconds): shut down server safely
                handleStopServer()
            }

            reconnectJob = null
        }
    }

    private fun handleStopServer() {
        isUserStopping = true

        // 1. Unregister clipboard web-write callback & clear echo state
        clipboardSessionStore.onItemAddedFromWeb = null
        clipboardBridge.clearEchoGuard()
        clipboardBridge.cancelPendingNotifications()

        // 2. Cancel observers & jobs
        networkMonitorJob?.cancel()
        networkMonitorJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        clipboardModeObserverJob?.cancel()
        clipboardModeObserverJob = null

        // 3. Stop server engine safely (flushes & closes all open sockets, wipes RAM clipboard store)
        localServerEngine.stopServer("Stop Requested")

        // 4. Release CPU & Wi-Fi Locks
        lockManager.releaseLocks()

        // 5. Remove notification & stop service
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "AirSlice Local Server Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Maintains AirSlice background HTTP server and network connectivity"
            setShowBadge(false)
        }
        val manager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
        manager?.createNotificationChannel(channel)
    }

    private fun buildServerNotification(serverUrl: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ServerForegroundService::class.java).apply {
            action = ACTION_STOP_SERVER
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Local Web Server Active")
            .setContentText("Listening on $serverUrl • Tap to open dashboard")
            .setSmallIcon(R.drawable.ic_server_notification)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                R.drawable.ic_server_notification,
                "Stop Server",
                stopPendingIntent
            )

        // Dynamically include "Send Clipboard" action only in TWO_WAY mode
        if (clipboardSessionStore.mode.value == ClipboardMode.TWO_WAY) {
            val sendClipboardIntent = Intent(this, ClipboardPushActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val sendClipboardPendingIntent = PendingIntent.getActivity(
                this,
                2,
                sendClipboardIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                R.drawable.ic_server_notification,
                getString(R.string.clipboard_notification_send_action),
                sendClipboardPendingIntent
            )
        }

        return builder.build()
    }

    private fun buildReconnectingNotification(attempt: Int, totalAttempts: Int): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ServerForegroundService::class.java).apply {
            action = ACTION_STOP_SERVER
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Reconnecting Network…")
            .setContentText("Searching for Wi-Fi or Hotspot (attempt $attempt/$totalAttempts)")
            .setSmallIcon(R.drawable.ic_server_notification)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                R.drawable.ic_server_notification,
                "Stop Server",
                stopPendingIntent
            )
            .build()
    }

    override fun onDestroy() {
        handleStopServer()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START_SERVER = "com.anasdev.fluxlink.action.START_SERVER"
        const val ACTION_STOP_SERVER = "com.anasdev.fluxlink.action.STOP_SERVER"
        const val EXTRA_PORT = "extra_port"

        val CHANNEL_ID = BuildConfig.NOTIFICATION_CHANNEL_ID
        val NOTIFICATION_ID = BuildConfig.NOTIFICATION_ID
    }
}
