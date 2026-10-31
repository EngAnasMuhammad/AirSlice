package com.anasdev.fluxlink.domain.repository

import com.anasdev.fluxlink.domain.model.ConnectedClient
import com.anasdev.fluxlink.domain.model.PendingClientRequest
import kotlinx.coroutines.flow.StateFlow

interface SessionRepository {
    val pendingRequest: StateFlow<PendingClientRequest?>
    val connectedClients: StateFlow<List<ConnectedClient>>

    fun approveConnectionRequest(requestId: String)
    fun denyConnectionRequest(requestId: String)
    fun revokeClientSession(sessionId: String)
}
