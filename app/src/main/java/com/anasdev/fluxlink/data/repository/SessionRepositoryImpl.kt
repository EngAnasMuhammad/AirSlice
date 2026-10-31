package com.anasdev.fluxlink.data.repository

import com.anasdev.fluxlink.data.ConnectedSessionManager
import com.anasdev.fluxlink.data.ConnectionApprovalManager
import com.anasdev.fluxlink.domain.model.ConnectedClient
import com.anasdev.fluxlink.domain.model.PendingClientRequest
import com.anasdev.fluxlink.domain.repository.SessionRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val connectionApprovalManager: ConnectionApprovalManager,
    private val connectedSessionManager: ConnectedSessionManager
) : SessionRepository {

    override val pendingRequest: StateFlow<PendingClientRequest?>
        get() = connectionApprovalManager.pendingRequest

    override val connectedClients: StateFlow<List<ConnectedClient>>
        get() = connectedSessionManager.connectedClients

    override fun approveConnectionRequest(requestId: String) {
        connectionApprovalManager.approveRequest(requestId)
    }

    override fun denyConnectionRequest(requestId: String) {
        connectionApprovalManager.denyRequest(requestId)
    }

    override fun revokeClientSession(sessionId: String) {
        connectedSessionManager.revokeSession(sessionId)
    }
}
