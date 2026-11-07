package com.anasdev.fluxlink.domain.model

enum class ServerStatus(val displayName: String) {
    STOPPED("Server Stopped"),
    STARTING("Starting Server..."),
    RUNNING("Server Running"),
    RECONNECTING("Reconnecting..."),
    STOPPING("Stopping Server..."),
    ERROR("Server Error")
}
