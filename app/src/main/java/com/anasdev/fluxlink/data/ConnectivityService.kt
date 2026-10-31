package com.anasdev.fluxlink.data

import android.app.Service
import android.content.Intent
import android.os.IBinder

class ConnectivityService : Service() {
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
