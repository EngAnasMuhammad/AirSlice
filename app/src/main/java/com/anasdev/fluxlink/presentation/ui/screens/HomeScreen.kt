package com.anasdev.fluxlink.presentation.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.anasdev.fluxlink.R
import com.anasdev.fluxlink.data.HotspotHelper
import com.anasdev.fluxlink.domain.model.ClientActivityType
import com.anasdev.fluxlink.domain.model.ConnectedClient
import com.anasdev.fluxlink.domain.model.ConnectionMode
import com.anasdev.fluxlink.domain.model.ServerStatus
import com.anasdev.fluxlink.presentation.state.HomeUiState
import com.anasdev.fluxlink.presentation.state.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var showInspectorDialog by remember { mutableStateOf(false) }

    // Auto-refresh network interfaces & connection mode state whenever returning from System Settings
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshNetworkState()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startServer(context)
        } else {
            Toast.makeText(
                context,
                "Notification permission required to start server foreground service.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun onToggleServer() {
        if (uiState.serverStatus == ServerStatus.RUNNING) {
            viewModel.stopServer(context)
        } else {
            // Check storage permission first
            if (!uiState.hasStorageAccess) {
                Toast.makeText(
                    context,
                    "All Files Access permission required to share local files.",
                    Toast.LENGTH_LONG
                ).show()
                viewModel.requestStorageAccess(context)
                return
            }

            // Error Prevention: Ensure device is connected to Wi-Fi or Hotspot before starting server
            val isConnected = uiState.networkState.isConnected && uiState.networkState.selectedIpAddress != null
            if (!isConnected) {
                Toast.makeText(
                    context,
                    "Please connect to Wi-Fi or turn on Mobile Hotspot to start server.",
                    Toast.LENGTH_LONG
                ).show()
                HotspotHelper.openHotspotSettings(context)
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

                if (hasPermission) {
                    viewModel.startServer(context)
                } else {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                viewModel.startServer(context)
            }
        }
    }

    // Official non-deprecated Android Clipboard API with Android 13+ duplicate toast suppression
    @Suppress("UsePropertyAccessSyntax")
    fun copyToClipboard(text: String, label: String = "Server URL") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard?.setPrimaryClip(clip)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, "Copied $label to clipboard!", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareUrl(url: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "AirSlice Mobile Storage Server")
            putExtra(Intent.EXTRA_TEXT, "Connect to local mobile server: $url")
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Server Link"))
    }

    // 1-Tap Connection Request Approval Dialog
    val pendingReq = uiState.pendingClientRequest
    if (pendingReq != null) {
        AlertDialog(
            onDismissRequest = { viewModel.denyConnectionRequest(pendingReq.requestId) },
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_server_notification),
                    contentDescription = "Connection Request",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "🔐 Connection Request",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            text = {
                Text(
                    text = "${pendingReq.userAgentSummary} (${pendingReq.clientIp}) wants to access your files.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.approveConnectionRequest(pendingReq.requestId) }
                ) {
                    Text("Approve (1-Tap)", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { viewModel.denyConnectionRequest(pendingReq.requestId) }
                ) {
                    Text("Deny")
                }
            }
        )
    }

    // Server & Network Inspector Dialog
    if (showInspectorDialog) {
        ServerInspectorDialog(
            uiState = uiState,
            isLandscape = isLandscape,
            onDismiss = { showInspectorDialog = false },
            onOpenHotspotSettings = {
                showInspectorDialog = false
                HotspotHelper.openHotspotSettings(context)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "AirSlice",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    ConnectionModePill(
                        mode = uiState.networkState.connectionMode,
                        onClick = { showInspectorDialog = true }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            // Storage Access Permission Warning Banner (if missing)
            if (!uiState.hasStorageAccess) {
                StoragePermissionBanner(
                    onRequestAccess = { viewModel.requestStorageAccess(context) },
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            if (isLandscape) {
                // Adaptive Landscape 2-Column Row Layout
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = uiState.serverStatus == ServerStatus.RUNNING,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                            },
                            label = "landscapeStateTransition"
                        ) { isRunning ->
                            if (isRunning) {
                                val qrImage = uiState.qrCodeImage
                                if (qrImage != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(170.dp)
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(Color.White)
                                            .border(3.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                                            .padding(8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            bitmap = qrImage,
                                            contentDescription = "Scan QR Code",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            } else {
                                ServerStoppedHeroView()
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (uiState.serverStatus == ServerStatus.RUNNING) {
                            ServerRunningConnectionCard(
                                uiState = uiState,
                                onCopyUrl = { copyToClipboard(it, "Server URL") },
                                onShareUrl = { shareUrl(it) },
                                onCopyPin = { copyToClipboard(it, "Pairing PIN") },
                                showQrCode = false
                            )

                            ConnectedDevicesSection(
                                clients = uiState.connectedClients,
                                onRevokeSession = { viewModel.revokeClientSession(it) }
                            )
                        }

                        MainServerActionButton(
                            isRunning = uiState.serverStatus == ServerStatus.RUNNING,
                            onClick = { onToggleServer() }
                        )
                    }
                }
            } else {
                // Standard Portrait Vertical 3-Part Layout
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = uiState.serverStatus == ServerStatus.RUNNING,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                            },
                            label = "portraitStateTransition"
                        ) { isRunning ->
                            if (isRunning) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    ServerRunningConnectionCard(
                                        uiState = uiState,
                                        onCopyUrl = { copyToClipboard(it, "Server URL") },
                                        onShareUrl = { shareUrl(it) },
                                        onCopyPin = { copyToClipboard(it, "Pairing PIN") },
                                        showQrCode = true
                                    )

                                    ConnectedDevicesSection(
                                        clients = uiState.connectedClients,
                                        onRevokeSession = { viewModel.revokeClientSession(it) }
                                    )
                                }
                            } else {
                                ServerStoppedHeroView()
                            }
                        }
                    }

                    MainServerActionButton(
                        isRunning = uiState.serverStatus == ServerStatus.RUNNING,
                        onClick = { onToggleServer() }
                    )
                }
            }
        }
    }
}

@Composable
private fun StoragePermissionBanner(
    onRequestAccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚠️", fontSize = 18.sp)
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "All Files Access Required",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Grant storage permission to browse and stream local files over Wi-Fi.",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onRequestAccess,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onErrorContainer,
                    contentColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text("Grant Access", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ServerInspectorDialog(
    uiState: HomeUiState,
    isLandscape: Boolean,
    onDismiss: () -> Unit,
    onOpenHotspotSettings: () -> Unit
) {
    val mode = uiState.networkState.connectionMode
    val interfaceName = uiState.networkState.interfaceName ?: "N/A"
    val port = uiState.activeServerPort ?: uiState.networkState.selectedPort
    val ip = uiState.networkState.selectedIpAddress
    val endpoint = if (ip != null) "http://$ip:$port" else "Offline"

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = if (isLandscape) 840.dp else 420.dp)
            .fillMaxWidth(if (isLandscape) 0.88f else 0.90f),
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        text = {
            if (isLandscape) {
                // Adaptive 2-Column Row Layout for Landscape Mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Column: Icon + Title + Mode Badge + Wi-Fi Guidance Tip (Bottom Left)
                    Column(
                        modifier = Modifier
                            .weight(0.85f)
                            .padding(end = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_server_notification),
                                    contentDescription = "Inspector Icon",
                                    modifier = Modifier.size(30.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            Text(
                                text = "Network & Server Status",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                textAlign = TextAlign.Center
                            )

                            InspectorModeBadge(mode = mode)
                        }

                        // Guidance Tip placed on bottom left in Landscape
                        GuidanceTip(mode = mode, modifier = Modifier.padding(top = 12.dp))
                    }

                    // Right Column: Scrollable Diagnostic Cards
                    Column(
                        modifier = Modifier
                            .weight(1.15f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        InspectorDiagnosticContent(
                            mode = mode,
                            interfaceName = interfaceName,
                            endpoint = endpoint,
                            showTip = false
                        )
                    }
                }
            } else {
                // Standard Vertical Column Layout for Portrait Mode
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Section
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_server_notification),
                            contentDescription = "Inspector Icon",
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = "Network & Server Status",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center
                    )

                    InspectorModeBadge(mode = mode)

                    Spacer(modifier = Modifier.height(4.dp))

                    // Scrollable Diagnostic Content
                    InspectorDiagnosticContent(
                        mode = mode,
                        interfaceName = interfaceName,
                        endpoint = endpoint,
                        showTip = true
                    )
                }
            }
        },
        confirmButton = {
            InspectorActionButtons(
                onDismiss = onDismiss,
                onOpenHotspotSettings = onOpenHotspotSettings
            )
        }
    )
}

@Composable
private fun InspectorDiagnosticContent(
    mode: ConnectionMode,
    interfaceName: String,
    endpoint: String,
    showTip: Boolean = true
) {
    // Diagnostic Card 1: Active Interface & Endpoint
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Active Interface & Endpoint",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$interfaceName • $endpoint",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier.basicMarquee()
            )
        }
    }

    // Diagnostic Card 2: Expected Throughput Profile
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Expected Throughput Profile",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = when (mode) {
                    ConnectionMode.HOTSPOT_DIRECT -> "⚡ Exceeding 22+ MB/s (Direct 1-hop zero-copy link)"
                    ConnectionMode.WIFI_ROUTER -> "📶 ~2–5 MB/s (Limited by 2-hop router relay)"
                    ConnectionMode.DISCONNECTED -> "⚠️ Offline"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (mode == ConnectionMode.HOTSPOT_DIRECT) Color(0xFF69F0AE) else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.basicMarquee()
            )
        }
    }

    // Guidance Note for Wi-Fi Router Mode (when showTip is true)
    if (showTip && mode == ConnectionMode.WIFI_ROUTER) {
        GuidanceTip(mode = mode)
    }
}

@Composable
private fun GuidanceTip(
    mode: ConnectionMode,
    modifier: Modifier = Modifier
) {
    if (mode == ConnectionMode.WIFI_ROUTER) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 2.dp)
        ) {
            Text(
                text = "💡 Tip: For 5x faster speeds (exceeding 22+ MB/s) without using mobile data, enable your phone's 5GHz Mobile Hotspot.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun InspectorActionButtons(
    onDismiss: () -> Unit,
    onOpenHotspotSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onDismiss,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text("Close", fontSize = 13.sp)
        }

        Button(
            onClick = onOpenHotspotSettings,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text("Open Hotspot Settings", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun InspectorModeBadge(mode: ConnectionMode) {
    val (label, containerColor, textColor) = when (mode) {
        ConnectionMode.HOTSPOT_DIRECT -> Triple(
            "⚡ Hotspot Turbo Mode Active",
            Color(0xFF003829),
            Color(0xFF69F0AE)
        )
        ConnectionMode.WIFI_ROUTER -> Triple(
            "📶 Wi-Fi Router Mode",
            MaterialTheme.colorScheme.surfaceContainer,
            MaterialTheme.colorScheme.onSurface
        )
        ConnectionMode.DISCONNECTED -> Triple(
            "⚠️ Server Disconnected",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(containerColor)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
private fun ConnectedDevicesSection(
    clients: List<ConnectedClient>,
    onRevokeSession: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Connected Devices (${clients.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (clients.isEmpty()) {
            Text(
                text = "No active browser sessions yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            clients.forEach { client ->
                ConnectedDeviceRowItem(
                    client = client,
                    onRevoke = { onRevokeSession(client.sessionId) }
                )
            }
        }
    }
}

@Composable
private fun ConnectedDeviceRowItem(
    client: ConnectedClient,
    onRevoke: () -> Unit
) {
    val activity = client.currentActivity

    val (activityText, activityColor) = when (activity.type) {
        ClientActivityType.STREAMING_VIDEO -> Pair(
            "▶ Streaming: ${activity.targetFileName ?: "Video"}",
            Color(0xFF00E676)
        )
        ClientActivityType.DOWNLOADING -> Pair(
            "⬇ Downloading: ${activity.targetFileName ?: "File"}",
            Color(0xFF40C4FF)
        )
        ClientActivityType.UPLOADING -> Pair(
            "⬆ Uploading: ${activity.targetFileName ?: "File"}",
            Color(0xFFFFD740)
        )
        ClientActivityType.VIEWING_MEDIA -> Pair(
            "👁️ Viewing: ${activity.targetFileName ?: "Media"}",
            MaterialTheme.colorScheme.primary
        )
        ClientActivityType.BROWSING -> Pair(
            "📁 Browsing: ${activity.targetFileName ?: "Storage"}",
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        ClientActivityType.IDLE -> Pair(
            "Online • Idle",
            Color(0xFF4CAF50)
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (activity.type == ClientActivityType.STREAMING_VIDEO ||
                                activity.type == ClientActivityType.DOWNLOADING ||
                                activity.type == ClientActivityType.UPLOADING)
                                Color(0xFF003829)
                            else
                                MaterialTheme.colorScheme.primaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_server_notification),
                        contentDescription = "Device",
                        modifier = Modifier.size(20.dp),
                        tint = if (activity.type == ClientActivityType.STREAMING_VIDEO ||
                            activity.type == ClientActivityType.DOWNLOADING ||
                            activity.type == ClientActivityType.UPLOADING)
                            Color(0xFF69F0AE)
                        else
                            MaterialTheme.colorScheme.primary
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = client.deviceAndBrowser,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = client.ipAddress,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    AnimatedContent(
                        targetState = activityText,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
                        },
                        label = "activityTransition"
                    ) { currentText ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(activityColor)
                            )
                            Text(
                                text = currentText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = activityColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedButton(
                onClick = onRevoke,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Disconnect", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MainServerActionButton(
    isRunning: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isRunning)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.primary,
            contentColor = if (isRunning)
                MaterialTheme.colorScheme.onErrorContainer
            else
                MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_server_notification),
                contentDescription = "Server Icon",
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = if (isRunning) "Stop Server" else "Start Server",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ConnectionModePill(
    mode: ConnectionMode,
    onClick: () -> Unit
) {
    val (label, containerColor, textColor) = when (mode) {
        ConnectionMode.HOTSPOT_DIRECT -> Triple(
            "⚡ Hotspot Turbo",
            Color(0xFF003829),
            Color(0xFF69F0AE)
        )
        ConnectionMode.WIFI_ROUTER -> Triple(
            "📶 Wi-Fi Router",
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.onSurface
        )
        ConnectionMode.DISCONNECTED -> Triple(
            "⚠️ Offline",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
    }

    Box(
        modifier = Modifier
            .padding(end = 16.dp)
            .clip(RoundedCornerShape(50))
            .background(containerColor)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ServerStoppedHeroView() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_server_notification),
                contentDescription = "Server Ready",
                modifier = Modifier.size(52.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Ready to Share",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Start local HTTP server to transfer files at maximum speed",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ServerRunningConnectionCard(
    uiState: HomeUiState,
    onCopyUrl: (String) -> Unit,
    onShareUrl: (String) -> Unit,
    onCopyPin: (String) -> Unit,
    showQrCode: Boolean = true
) {
    val activePort = uiState.activeServerPort ?: uiState.networkState.selectedPort
    val ip = uiState.networkState.selectedIpAddress
    val serverUrl = if (ip != null) "http://$ip:$activePort" else null
    val qrImage = uiState.qrCodeImage

    // Pulsing Status Dot Animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // 1. High-Contrast QR Code Container
            if (showQrCode && qrImage != null) {
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .border(3.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = qrImage,
                        contentDescription = "Scan QR Code",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // 2. Monospace URL Pill Box with Copy & Share Action Buttons Placed UNDER the Address
            if (serverUrl != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = serverUrl,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { onCopyUrl(serverUrl) },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_copy),
                                        contentDescription = "Copy URL",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text("Copy", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            OutlinedButton(
                                onClick = { onShareUrl(serverUrl) },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_share),
                                        contentDescription = "Share URL",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text("Share", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // 3. Pulsing Online Indicator & PIN Badge Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50).copy(alpha = pulseAlpha))
                    )
                    Text(
                        text = "Online • Ready to connect",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (uiState.pinCode.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .clickable { onCopyPin(uiState.pinCode) }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "PIN: ${uiState.pinCode}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
