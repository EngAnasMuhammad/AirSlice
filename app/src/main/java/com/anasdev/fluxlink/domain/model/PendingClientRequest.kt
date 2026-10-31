package com.anasdev.fluxlink.domain.model

enum class ApprovalStatus {
    PENDING,
    APPROVED,
    DENIED
}

data class PendingClientRequest(
    val requestId: String,
    val clientIp: String,
    val userAgentSummary: String,
    val status: ApprovalStatus = ApprovalStatus.PENDING,
    val createdAtMs: Long = System.currentTimeMillis()
)
