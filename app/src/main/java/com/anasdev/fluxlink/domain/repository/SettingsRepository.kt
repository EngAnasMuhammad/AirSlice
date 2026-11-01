package com.anasdev.fluxlink.domain.repository

import com.anasdev.fluxlink.domain.model.AppLanguage
import com.anasdev.fluxlink.domain.model.AppTheme
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getAppTheme(): Flow<AppTheme>
    suspend fun setAppTheme(theme: AppTheme)

    fun getAppLanguage(): Flow<AppLanguage>
    suspend fun setAppLanguage(language: AppLanguage)

    fun getRequirePinAuth(): Flow<Boolean>
    suspend fun setRequirePinAuth(requirePin: Boolean)

    fun getShowHiddenFiles(): Flow<Boolean>
    suspend fun setShowHiddenFiles(showHidden: Boolean)

    fun getServerPort(): Flow<Int>
    suspend fun setServerPort(port: Int)

    fun getKeepScreenOn(): Flow<Boolean>
    suspend fun setKeepScreenOn(keepScreenOn: Boolean)
}
