package com.anasdev.fluxlink.presentation.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anasdev.fluxlink.domain.model.AppLanguage
import com.anasdev.fluxlink.domain.model.AppTheme
import com.anasdev.fluxlink.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val appTheme: AppTheme = AppTheme.SYSTEM,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val requirePinAuth: Boolean = true,
    val showHiddenFiles: Boolean = false,
    val serverPort: Int = 8080,
    val keepScreenOn: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        settingsRepository.getAppTheme().onEach { theme ->
            _uiState.value = _uiState.value.copy(appTheme = theme)
        }.launchIn(viewModelScope)

        settingsRepository.getAppLanguage().onEach { language ->
            _uiState.value = _uiState.value.copy(appLanguage = language)
        }.launchIn(viewModelScope)

        settingsRepository.getRequirePinAuth().onEach { pinAuth ->
            _uiState.value = _uiState.value.copy(requirePinAuth = pinAuth)
        }.launchIn(viewModelScope)

        settingsRepository.getShowHiddenFiles().onEach { showHidden ->
            _uiState.value = _uiState.value.copy(showHiddenFiles = showHidden)
        }.launchIn(viewModelScope)

        settingsRepository.getServerPort().onEach { port ->
            _uiState.value = _uiState.value.copy(serverPort = port)
        }.launchIn(viewModelScope)

        settingsRepository.getKeepScreenOn().onEach { keepOn ->
            _uiState.value = _uiState.value.copy(keepScreenOn = keepOn)
        }.launchIn(viewModelScope)
    }

    fun setAppTheme(theme: AppTheme) {
        viewModelScope.launch {
            settingsRepository.setAppTheme(theme)
        }
    }

    fun setAppLanguage(language: AppLanguage) {
        viewModelScope.launch {
            settingsRepository.setAppLanguage(language)
        }
    }

    fun setRequirePinAuth(requirePin: Boolean) {
        viewModelScope.launch {
            settingsRepository.setRequirePinAuth(requirePin)
        }
    }

    fun setShowHiddenFiles(showHidden: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowHiddenFiles(showHidden)
        }
    }

    fun setServerPort(port: Int) {
        viewModelScope.launch {
            settingsRepository.setServerPort(port)
        }
    }

    fun setKeepScreenOn(keepScreenOn: Boolean) {
        viewModelScope.launch {
            settingsRepository.setKeepScreenOn(keepScreenOn)
        }
    }
}
