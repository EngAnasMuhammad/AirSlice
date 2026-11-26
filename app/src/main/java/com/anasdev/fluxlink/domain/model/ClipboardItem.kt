package com.anasdev.fluxlink.domain.model

enum class ClipboardMode {
    OFF,
    RECEIVE_ONLY,
    TWO_WAY
}

data class ClipboardItem(
    val id: Long,
    val text: String,
    val source: String, // "PHONE" or "WEB"
    val timestamp: Long
)
