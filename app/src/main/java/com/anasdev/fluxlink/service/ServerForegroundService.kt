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
import com.anasdev.fluxlink.data.LocalServerEngine
import com.anasdev.fluxlink.data.LockManager
import com.anasdev.fluxlink.data.NetworkInfoProvider
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

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var networkMonitorJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

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
        val initialNotification = buildServerNotification("http://localhost:$port")

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
        val serverUrl = networkState.serverUrl ?: "http://${networkState.selectedIpAddress}:$port"
        val activeNotification = buildServerNotification(serverUrl)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(NOTIFICATION_ID, activeNotification)

        // 4. Acquire CPU Wake Lock and Wi-Fi Lock
        lockManager.acquireLocks()

        // 5. Start HTTP Server Engine
        val started = localServerEngine.startServer(networkState.selectedPort)
        if (!started) {
            handleStopServer()
            return
        }

        // 6. Monitor network connection loss
        startNetworkMonitoring()
    }

    private fun startNetworkMonitoring() {
        networkMonitorJob?.cancel()
        networkMonitorJob = serviceScope.launch {
            networkInfoProvider.observeNetworkState().collectLatest { state ->
                // IF connection is lost, immediately capture and stop server safely
                if (!state.isConnected || state.selectedIpAddress == null) {
                    handleStopServer()
                }
            }
        }
    }

    private fun handleStopServer() {
        // 1. Cancel network observer
        networkMonitorJob?.cancel()
        networkMonitorJob = null

        // 2. Stop server engine safely (flushes & closes all open sockets & files)
        localServerEngine.stopServer("Stop Requested")

        // 3. Release CPU & Wi-Fi Locks
        lockManager.releaseLocks()

        // 4. Remove notification & stop service
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "AirSlice Local Server Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows local mobile server running status and control actions"
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
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
