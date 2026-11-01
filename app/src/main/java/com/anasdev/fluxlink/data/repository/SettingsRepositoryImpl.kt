package com.anasdev.fluxlink.data.repository

import android.content.Context
import androidx.core.content.edit
import com.anasdev.fluxlink.domain.model.AppLanguage
import com.anasdev.fluxlink.domain.model.AppTheme
import com.anasdev.fluxlink.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SettingsRepository {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _appTheme = MutableStateFlow(readAppTheme())
    private val _appLanguage = MutableStateFlow(readAppLanguage())
    private val _requirePinAuth = MutableStateFlow(prefs.getBoolean(KEY_REQUIRE_PIN, true))
    private val _showHiddenFiles = MutableStateFlow(prefs.getBoolean(KEY_SHOW_HIDDEN, false))
    private val _serverPort = MutableStateFlow(prefs.getInt(KEY_SERVER_PORT, 8080))
    private val _keepScreenOn = MutableStateFlow(prefs.getBoolean(KEY_KEEP_SCREEN_ON, false))

    override fun getAppTheme(): Flow<AppTheme> = _appTheme.asStateFlow()

    override suspend fun setAppTheme(theme: AppTheme) {
        prefs.edit { putString(KEY_THEME, theme.name) }
        _appTheme.value = theme
    }

    override fun getAppLanguage(): Flow<AppLanguage> = _appLanguage.asStateFlow()

    override suspend fun setAppLanguage(language: AppLanguage) {
        prefs.edit { putString(KEY_LANGUAGE, language.name) }
        _appLanguage.value = language
    }

    override fun getRequirePinAuth(): Flow<Boolean> = _requirePinAuth.asStateFlow()

    override suspend fun setRequirePinAuth(requirePin: Boolean) {
        prefs.edit { putBoolean(KEY_REQUIRE_PIN, requirePin) }
        _requirePinAuth.value = requirePin
    }

    override fun getShowHiddenFiles(): Flow<Boolean> = _showHiddenFiles.asStateFlow()

    override suspend fun setShowHiddenFiles(showHidden: Boolean) {
        prefs.edit { putBoolean(KEY_SHOW_HIDDEN, showHidden) }
        _showHiddenFiles.value = showHidden
    }

    override fun getServerPort(): Flow<Int> = _serverPort.asStateFlow()

    override suspend fun setServerPort(port: Int) {
        prefs.edit { putInt(KEY_SERVER_PORT, port) }
        _serverPort.value = port
    }

    override fun getKeepScreenOn(): Flow<Boolean> = _keepScreenOn.asStateFlow()

    override suspend fun setKeepScreenOn(keepScreenOn: Boolean) {
        prefs.edit { putBoolean(KEY_KEEP_SCREEN_ON, keepScreenOn) }
        _keepScreenOn.value = keepScreenOn
    }

    private fun readAppTheme(): AppTheme {
        val savedName = prefs.getString(KEY_THEME, AppTheme.SYSTEM.name)
        return try {
            AppTheme.valueOf(savedName ?: AppTheme.SYSTEM.name)
        } catch (_: Exception) {
            AppTheme.SYSTEM
        }
    }

    private fun readAppLanguage(): AppLanguage {
        val savedName = prefs.getString(KEY_LANGUAGE, AppLanguage.SYSTEM.name)
        return try {
            AppLanguage.valueOf(savedName ?: AppLanguage.SYSTEM.name)
        } catch (_: Exception) {
            AppLanguage.SYSTEM
        }
    }

    companion object {
        private const val PREFS_NAME = "airslice_settings_prefs"
        private const val KEY_THEME = "key_app_theme"
        private const val KEY_LANGUAGE = "key_app_language"
        private const val KEY_REQUIRE_PIN = "key_require_pin"
        private const val KEY_SHOW_HIDDEN = "key_show_hidden"
        private const val KEY_SERVER_PORT = "key_server_port"
        private const val KEY_KEEP_SCREEN_ON = "key_keep_screen_on"
    }
}
