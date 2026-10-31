package com.anasdev.fluxlink.data

import com.anasdev.fluxlink.BuildConfig
import java.io.IOException
import java.net.ServerSocket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PortManager @Inject constructor() {

    fun findAvailablePort(startPort: Int = BuildConfig.DEFAULT_SERVER_PORT, maxPort: Int = BuildConfig.DEFAULT_SERVER_PORT + 20): Int {
        var currentPort = startPort
        while (currentPort <= maxPort) {
            if (isPortAvailable(currentPort)) {
                return currentPort
            }
            currentPort++
        }
        return startPort
    }

    fun isPortAvailable(port: Int): Boolean {
        return try {
            ServerSocket(port).use { socket ->
                socket.reuseAddress = true
                true
            }
        } catch (e: IOException) {
            false
        }
    }
}
