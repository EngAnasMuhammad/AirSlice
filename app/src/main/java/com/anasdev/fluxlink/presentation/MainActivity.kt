package com.anasdev.fluxlink.presentation

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.os.LocaleListCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.anasdev.fluxlink.domain.model.AppLanguage
import com.anasdev.fluxlink.domain.model.AppTheme
import com.anasdev.fluxlink.domain.model.ServerStatus
import com.anasdev.fluxlink.presentation.navigation.AppNavigation
import com.anasdev.fluxlink.presentation.state.HomeViewModel
import com.anasdev.fluxlink.presentation.state.SettingsViewModel
import com.anasdev.fluxlink.presentation.ui.theme.FluxLinkTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val homeViewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIncomingShareIntent(intent)

        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settingsState by settingsViewModel.uiState.collectAsState()

            val homeState by homeViewModel.uiState.collectAsState()

            // Dynamic Per-App Locale Application
            LaunchedEffect(settingsState.appLanguage) {
                val localeList = if (settingsState.appLanguage == AppLanguage.SYSTEM) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(settingsState.appLanguage.code)
                }
                AppCompatDelegate.setApplicationLocales(localeList)
            }

            // Keep Screen On While Server is Active
            LaunchedEffect(settingsState.keepScreenOn, homeState.serverStatus) {
                if (settingsState.keepScreenOn && (homeState.serverStatus == ServerStatus.RUNNING)) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            // Dynamic Theme Resolution
            val isDarkTheme = when (settingsState.appTheme) {
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
                AppTheme.SYSTEM -> isSystemInDarkTheme()
            }

            FluxLinkTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    AppNavigation()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun handleIncomingShareIntent(incomingIntent: Intent?) {
        if (incomingIntent?.action == Intent.ACTION_SEND && incomingIntent.type == "text/plain") {
            val sharedText = incomingIntent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim()
            if (!sharedText.isNullOrBlank()) {
                val byteSize = sharedText.toByteArray(Charsets.UTF_8).size
                if (byteSize <= com.anasdev.fluxlink.data.ClipboardSessionStore.MAX_ITEM_BYTES) {
                    homeViewModel.handleSharedTextReceived(sharedText)
                }
            }
        }
    }
}
