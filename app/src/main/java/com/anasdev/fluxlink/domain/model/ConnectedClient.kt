package com.anasdev.fluxlink.domain.model

data class ConnectedClient(
    val sessionId: String,
    val ipAddress: String,
    val deviceAndBrowser: String,
    val connectedAtMillis: Long = System.currentTimeMillis(),
    val lastActiveMillis: Long = System.currentTimeMillis(),
    val rawActivity: ClientActivity = ClientActivity()
) {
    val currentActivity: ClientActivity
        get() {
            val now = System.currentTimeMillis()
            if (rawActivity.type != ClientActivityType.IDLE && (now - rawActivity.updatedAtMillis) < 5000L) {
                return rawActivity
            }
            return ClientActivity(type = ClientActivityType.IDLE)
        }
}
