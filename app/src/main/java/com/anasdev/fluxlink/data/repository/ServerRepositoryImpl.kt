package com.anasdev.fluxlink.data.repository

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.anasdev.fluxlink.data.LocalServerEngine
import com.anasdev.fluxlink.data.SecurityManager
import com.anasdev.fluxlink.domain.model.ServerStatus
import com.anasdev.fluxlink.domain.repository.ServerRepository
import com.anasdev.fluxlink.service.ServerForegroundService
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerRepositoryImpl @Inject constructor(
    private val localServerEngine: LocalServerEngine,
    private val securityManager: SecurityManager
) : ServerRepository {

    override val serverStatus: StateFlow<ServerStatus>
        get() = localServerEngine.serverStatus

    override val activePort: StateFlow<Int?>
        get() = localServerEngine.activePort

    override val serverStartTimeMillis: Long
        get() = localServerEngine.serverStartTimeMillis

    override val currentPin: String
        get() = securityManager.currentPin

    override val sessionToken: String
        get() = securityManager.sessionToken

    override fun startServer(context: Context) {
        val intent = Intent(context, ServerForegroundService::class.java).apply {
            action = ServerForegroundService.ACTION_START_SERVER
        }
        ContextCompat.startForegroundService(context, intent)
    }

    override fun stopServer(context: Context) {
        val intent = Intent(context, ServerForegroundService::class.java).apply {
            action = ServerForegroundService.ACTION_STOP_SERVER
        }
        context.startService(intent)
    }
}
