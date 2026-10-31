package com.anasdev.fluxlink.domain.model

data class NetworkInterfaceInfo(
    val interfaceName: String,
    val ipAddress: String,
    val isWifiOrHotspot: Boolean,
    val isPreferred192Subnet: Boolean
)
