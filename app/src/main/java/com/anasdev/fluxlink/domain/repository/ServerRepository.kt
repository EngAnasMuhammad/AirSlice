package com.anasdev.fluxlink.domain.repository

import android.content.Context
import com.anasdev.fluxlink.domain.model.ServerStatus
import kotlinx.coroutines.flow.StateFlow

interface ServerRepository {
    val serverStatus: StateFlow<ServerStatus>
    val activePort: StateFlow<Int?>
    val serverStartTimeMillis: Long
    val currentPin: String
    val sessionToken: String

    fun startServer(context: Context)
    fun stopServer(context: Context)
}
