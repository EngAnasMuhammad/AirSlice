package com.anasdev.fluxlink.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileAccessTracker @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("airslice_access_tracker", Context.MODE_PRIVATE)
    private val accessMap = ConcurrentHashMap<String, Long>()
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    init {
        try {
            val all = prefs.all
            for ((key, value) in all) {
                if (value is Long) {
                    accessMap[key] = value
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun recordAccess(filePath: String) {
        if (filePath.isBlank()) return
        val now = System.currentTimeMillis()
        accessMap[filePath] = now

        // Async non-blocking persistence
        scope.launch {
            try {
                prefs.edit().putLong(filePath, now).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getLastAccess(filePath: String): Long? {
        return accessMap[filePath]
    }
}
