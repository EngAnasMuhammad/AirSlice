package com.anasdev.fluxlink.presentation.state

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anasdev.fluxlink.data.AndroidClipboardBridge
import com.anasdev.fluxlink.data.ClipboardReadResult
import com.anasdev.fluxlink.data.ClipboardSessionStore
import com.anasdev.fluxlink.data.LockManager
import com.anasdev.fluxlink.data.QrCodeGenerator
import com.anasdev.fluxlink.data.ThumbnailGenerator
import com.anasdev.fluxlink.domain.model.ClipboardItem
import com.anasdev.fluxlink.domain.model.ClipboardMode
import com.anasdev.fluxlink.domain.model.ConnectedClient
import com.anasdev.fluxlink.domain.model.NetworkState
import com.anasdev.fluxlink.domain.model.PendingClientRequest
import com.anasdev.fluxlink.domain.model.ServerStatus
import com.anasdev.fluxlink.domain.repository.NetworkRepository
import com.anasdev.fluxlink.domain.repository.ServerRepository
import com.anasdev.fluxlink.domain.repository.SessionRepository
import com.anasdev.fluxlink.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

data class HomeUiState(
    val networkState: NetworkState = NetworkState(),
    val serverStatus: ServerStatus = ServerStatus.STOPPED,
    val activeServerPort: Int? = null,
    val isCpuLockHeld: Boolean = false,
    val isWifiLockHeld: Boolean = false,
    val hasStorageAccess: Boolean = false,
    val pinCode: String = "",
    val qrTargetUrl: String? = null,
    val qrCodeBitmap: android.graphics.Bitmap? = null,
    val qrCodeImage: ImageBitmap? = null,
    val pendingClientRequest: PendingClientRequest? = null,
    val connectedClients: List<ConnectedClient> = emptyList(),
    val thumbnailCacheSizeBytes: Long = 0L,
    val formattedUptime: String = "00:00",
    val isPinRequired: Boolean = true,
    val clipboardMode: ClipboardMode = ClipboardMode.OFF,
    val clipboardItems: List<ClipboardItem> = emptyList(),
    val pendingSharedText: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
    private val networkRepository: NetworkRepository,
    private val sessionRepository: SessionRepository,
    private val settingsRepository: SettingsRepository,
    private val lockManager: LockManager,
    private val qrCodeGenerator: QrCodeGenerator,
    private val thumbnailGenerator: ThumbnailGenerator,
    private val clipboardSessionStore: ClipboardSessionStore,
    private val clipboardBridge: AndroidClipboardBridge
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _clipboardActionResult = MutableSharedFlow<ClipboardReadResult>()
    val clipboardActionResult: SharedFlow<ClipboardReadResult> = _clipboardActionResult.asSharedFlow()

    private val tickerFlow = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1000L.milliseconds)
        }
    }

    private val clientsWithTickerFlow = combine(sessionRepository.connectedClients, tickerFlow) { clients, _ -> clients }

    init {
        // Collect reactive cache size emissions directly from ThumbnailGenerator
        thumbnailGenerator.cacheSizeBytesFlow.onEach { sizeBytes ->
            _uiState.value = _uiState.value.copy(
                thumbnailCacheSizeBytes = sizeBytes
            )
        }.launchIn(viewModelScope)

        // Collect reactive PIN requirement setting
        settingsRepository.getRequirePinAuth().onEach { pinRequired ->
            _uiState.value = _uiState.value.copy(
                isPinRequired = pinRequired
            )
        }.launchIn(viewModelScope)

        // Observe Clipboard Session Store (Mode + Items)
        combine(
            clipboardSessionStore.mode,
            clipboardSessionStore.items
        ) { mode, items ->
            Pair(mode, items)
        }.onEach { (mode, items) ->
            _uiState.value = _uiState.value.copy(
                clipboardMode = mode,
                clipboardItems = items
            )
        }.launchIn(viewModelScope)

        combine(
            networkRepository.observeNetworkState(),
            serverRepository.serverStatus,
            serverRepository.activePort,
            sessionRepository.pendingRequest,
            clientsWithTickerFlow
        ) { networkState, serverStatus, activePort, pendingRequest, connectedClients ->

            val isRunning = serverStatus == ServerStatus.RUNNING || serverStatus == ServerStatus.RECONNECTING
            val pin = if (isRunning) serverRepository.currentPin else ""
            val token = if (isRunning) serverRepository.sessionToken else ""
            val ip = networkState.selectedIpAddress
            val port = activePort ?: networkState.selectedPort

            val startTime = serverRepository.serverStartTimeMillis
            val uptimeStr = if (isRunning && startTime > 0L) {
                val elapsedSec =
                    0L.coerceAtLeast((System.currentTimeMillis() - startTime) / 1000)
                val mins = elapsedSec / 60
                val secs = elapsedSec % 60
                String.format(Locale.US, "%02d:%02d", mins, secs)
            } else {
                "00:00"
            }

            val qrUrl = if (isRunning && (ip != null)) {
                if (token.isNotEmpty()) {
                    "http://$ip:$port?token=$token"
                } else {
                    "http://$ip:$port"
                }
            } else null

            val qrBitmap = qrUrl?.let { url ->
                qrCodeGenerator.generateQrCodeBitmap(url, 320)
            }

            // Pre-convert QR Bitmap to Compose ImageBitmap off the main UI thread
            val qrImage = withContext(Dispatchers.Default) {
                qrBitmap?.asImageBitmap()
            }

            _uiState.value.copy(
                networkState = networkState,
                serverStatus = serverStatus,
                activeServerPort = activePort,
                isCpuLockHeld = lockManager.isCpuLockHeld,
                isWifiLockHeld = lockManager.isWifiLockHeld,
                hasStorageAccess = checkStoragePermission(),
                pinCode = pin,
                qrTargetUrl = qrUrl,
                qrCodeBitmap = qrBitmap,
                qrCodeImage = qrImage,
                pendingClientRequest = pendingRequest,
                connectedClients = if (isRunning) connectedClients else emptyList(),
                thumbnailCacheSizeBytes = thumbnailGenerator.cacheSizeBytesFlow.value,
                formattedUptime = uptimeStr,
                isPinRequired = _uiState.value.isPinRequired
            )
        }.onEach { updatedUiState ->
            _uiState.value = updatedUiState
        }.launchIn(viewModelScope)

        // Initial background cache size calculation
        refreshCacheSize()
    }

    fun setClipboardMode(mode: ClipboardMode) {
        clipboardSessionStore.setMode(mode)
    }

    fun pushPhoneClipboard() {
        viewModelScope.launch {
            val result = clipboardBridge.pushCurrentPhoneClipboard()
            _clipboardActionResult.emit(result)
        }
    }

    fun addCustomClipboardText(text: String) {
        if (text.isNotBlank()) {
            clipboardSessionStore.addItem(text, ClipboardSessionStore.SOURCE_PHONE)
        }
    }

    fun copyItemToSystemClipboard(text: String) {
        clipboardBridge.copyToSystemClipboard(text)
    }

    fun removeClipboardItem(id: Long) {
        clipboardSessionStore.removeItem(id)
    }

    fun clearClipboardHistory() {
        clipboardSessionStore.clear()
    }

    fun handleSharedTextReceived(text: String) {
        val isRunning = _uiState.value.serverStatus == ServerStatus.RUNNING || _uiState.value.serverStatus == ServerStatus.RECONNECTING
        if (!isRunning) {
            _uiState.value = _uiState.value.copy(pendingSharedText = text)
            viewModelScope.launch {
                _clipboardActionResult.emit(ClipboardReadResult.Error("SERVER_STOPPED"))
            }
            return
        }

        if (clipboardSessionStore.mode.value == ClipboardMode.TWO_WAY) {
            clipboardSessionStore.addItem(text, ClipboardSessionStore.SOURCE_PHONE)
            viewModelScope.launch {
                _clipboardActionResult.emit(ClipboardReadResult.Success(ClipboardItem(0L, text, ClipboardSessionStore.SOURCE_PHONE, System.currentTimeMillis())))
            }
        } else {
            // Store pending shared text so UI dialog can ask user to enable Two-Way
            _uiState.value = _uiState.value.copy(pendingSharedText = text)
        }
    }

    fun confirmEnableTwoWayAndSendSharedText() {
        val text = _uiState.value.pendingSharedText ?: return
        clipboardSessionStore.setMode(ClipboardMode.TWO_WAY)
        clipboardSessionStore.addItem(text, ClipboardSessionStore.SOURCE_PHONE)
        _uiState.value = _uiState.value.copy(pendingSharedText = null)
        viewModelScope.launch {
            _clipboardActionResult.emit(ClipboardReadResult.Success(ClipboardItem(0L, text, ClipboardSessionStore.SOURCE_PHONE, System.currentTimeMillis())))
        }
    }

    fun dismissPendingSharedText() {
        _uiState.value = _uiState.value.copy(pendingSharedText = null)
    }

    fun refreshCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            thumbnailGenerator.recalculateCacheSize()
        }
    }

    fun checkStoragePermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
    }

    fun requestStorageAccess(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = "package:${context.packageName}".toUri()
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                context.startActivity(intent)
            }
        }
    }

    fun refreshNetworkState() {
        val currentNetwork = networkRepository.getNetworkState()
        _uiState.value = _uiState.value.copy(
            networkState = currentNetwork,
            isCpuLockHeld = lockManager.isCpuLockHeld,
            isWifiLockHeld = lockManager.isWifiLockHeld,
            hasStorageAccess = checkStoragePermission()
        )
        refreshCacheSize()
    }

    fun setPort(port: Int) {
        val currentNetwork = networkRepository.getNetworkState(overridePort = port)
        _uiState.value = _uiState.value.copy(networkState = currentNetwork)
    }

    fun approveConnectionRequest(requestId: String) {
        sessionRepository.approveConnectionRequest(requestId)
    }

    fun denyConnectionRequest(requestId: String) {
        sessionRepository.denyConnectionRequest(requestId)
    }

    fun revokeClientSession(sessionId: String) {
        sessionRepository.revokeClientSession(sessionId)
    }

    fun clearThumbnailCache() {
        viewModelScope.launch(Dispatchers.IO) {
            thumbnailGenerator.clearThumbnailCache()
            refreshCacheSize()
        }
    }

    fun startServer(context: Context) {
        serverRepository.startServer(context)
    }

    fun stopServer(context: Context) {
        serverRepository.stopServer(context)
    }
}
