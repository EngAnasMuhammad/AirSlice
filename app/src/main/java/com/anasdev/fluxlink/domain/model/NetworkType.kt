package com.anasdev.fluxlink.domain.model

enum class NetworkType(val displayName: String) {
    WIFI("Wi-Fi"),
    HOTSPOT("Hotspot (AP)"),
    CELLULAR("Cellular Data"),
    ETHERNET("Ethernet"),
    OTHER("Other Network"),
    DISCONNECTED("No Network Connection")
}
