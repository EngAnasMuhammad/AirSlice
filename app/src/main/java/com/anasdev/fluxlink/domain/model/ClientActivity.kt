package com.anasdev.fluxlink.domain.model

enum class ClientActivityType {
    IDLE,
    BROWSING,
    STREAMING_VIDEO,
    VIEWING_MEDIA,
    DOWNLOADING,
    UPLOADING
}

data class ClientActivity(
    val type: ClientActivityType = ClientActivityType.IDLE,
    val targetFileName: String? = null,
    val updatedAtMillis: Long = System.currentTimeMillis()
)
