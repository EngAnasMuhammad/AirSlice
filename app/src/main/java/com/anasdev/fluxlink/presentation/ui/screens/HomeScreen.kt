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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
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

fun String.toAsciiDigits(): String = buildString(length) {
    for (ch in this@toAsciiDigits) {
        when (ch) {
            in '٠'..'٩' -> append(('0'.code + (ch.code - '٠'.code)).toChar())
            in '۰'..'۹' -> append(('0'.code + (ch.code - '۰'.code)).toChar())
            else -> append(ch)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var showInspectorDialog by remember { mutableStateOf(false) }

    val notificationReqText = stringResource(id = R.string.notification_permission_required)
    val storageReqText = stringResource(id = R.string.storage_permission_required)
    val connectWifiOrHotspotText = stringResource(id = R.string.connect_wifi_or_hotspot_prompt)
    val copiedToastFormat = stringResource(id = R.string.copied_toast_format)
    val serverUrlLabel = stringResource(id = R.string.server_url_label)
    val pairingPinLabel = stringResource(id = R.string.pairing_pin_label)
    val shareSubjectText = stringResource(id = R.string.share_subject)
    val shareTextFormat = stringResource(id = R.string.share_text_format)
    val shareChooserTitle = stringResource(id = R.string.share_chooser_title)

    val isServerRunning = uiState.serverStatus == ServerStatus.RUNNING || uiState.serverStatus == ServerStatus.RECONNECTING
    val isNetworkAvailable = uiState.networkState.isConnected && (uiState.networkState.selectedIpAddress != null)

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
            Toast.makeText(context, notificationReqText, Toast.LENGTH_LONG).show()
        }
    }

    fun onToggleServer() {
        if (isServerRunning) {
            viewModel.stopServer(context)
        } else {
            if (!uiState.hasStorageAccess) {
                Toast.makeText(context, storageReqText, Toast.LENGTH_LONG).show()
                viewModel.requestStorageAccess(context)
                return
            }

            if (!isNetworkAvailable) {
                Toast.makeText(context, connectWifiOrHotspotText, Toast.LENGTH_LONG).show()
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

    @Suppress("UsePropertyAccessSyntax")
    fun copyToClipboard(text: String, label: String = serverUrlLabel) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard?.setPrimaryClip(clip)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, String.format(copiedToastFormat, label), Toast.LENGTH_SHORT).show()
        }
    }

    fun shareUrl(url: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, shareSubjectText)
            putExtra(Intent.EXTRA_TEXT, String.format(shareTextFormat, url))
        }
        context.startActivity(Intent.createChooser(shareIntent, shareChooserTitle))
    }

    // 1-Tap Connection Request Approval Dialog
    val pendingReq = uiState.pendingClientRequest
    if (pendingReq != null) {
        AlertDialog(
            onDismissRequest = { viewModel.denyConnectionRequest(pendingReq.requestId) },
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_server_notification),
                    contentDescription = stringResource(id = R.string.connection_request_title),
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = stringResource(id = R.string.connection_request_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            text = {
                Text(
                    text = stringResource(
                        id = R.string.connection_request_message,
                        pendingReq.userAgentSummary,
                        pendingReq.clientIp
                    ),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { viewModel.denyConnectionRequest(pendingReq.requestId) },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.deny),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }

                    Button(
                        onClick = { viewModel.approveConnectionRequest(pendingReq.requestId) },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.approve_one_tap),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            },
            dismissButton = {}
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
            CenterAlignedTopAppBar(
                navigationIcon = {
                    Text(
                        text = stringResource(id = R.string.app_name),
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                },
                title = {
                    ConnectionModePill(
                        mode = uiState.networkState.connectionMode,
                        onClick = { showInspectorDialog = true }
                    )
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(id = R.string.settings_title),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Storage Access Permission Warning Banner (if missing)
            if (!uiState.hasStorageAccess) {
                StoragePermissionBanner(
                    onRequestAccess = { viewModel.requestStorageAccess(context) },
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            AnimatedContent(
                targetState = isServerRunning,
                transitionSpec = {
                    (fadeIn(tween(400)) + slideInVertically(tween(400)) { height -> height / 3 })
                        .togetherWith(fadeOut(tween(300)) + slideOutVertically(tween(300)) { height -> -height / 3 })
                },
                label = "powerControlMorphingTransition",
                modifier = Modifier.fillMaxSize()
            ) { running ->
                if (!running) {
                    // STOPPED / DISCONNECTED / READY STATES (Centered Control)
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 24.dp)
                        ) {
                            PowerControlButton(
                                isNetworkAvailable = isNetworkAvailable,
                                onClick = { onToggleServer() }
                            )

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) {
                                Text(
                                    text = if (!isNetworkAvailable)
                                        stringResource(id = R.string.no_network_connection)
                                    else
                                        stringResource(id = R.string.ready_to_share),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = if (isLandscape) 18.sp else 22.sp,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = if (!isNetworkAvailable)
                                        stringResource(id = R.string.connect_network_subtitle)
                                    else
                                        stringResource(id = R.string.tap_to_launch_server),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = if (isLandscape) 12.sp else 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                } else {
                    // SERVER RUNNING STATE C (Morphed Glassmorphic Control Bar + Telemetry + Devices)
                    if (isLandscape) {
                        // Landscape Mode: 2-Column Responsive Layout
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Left Column: Morphed Top Bar + Connected Devices List
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                MorphingGlassmorphicControlBar(
                                    uiState = uiState,
                                    onStopServer = { onToggleServer() },
                                    onCopyPin = { copyToClipboard(it, pairingPinLabel) }
                                )

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    ConnectedDevicesSection(
                                        clients = uiState.connectedClients,
                                        onRevokeSession = { viewModel.revokeClientSession(it) }
                                    )
                                }
                            }

                            // Right Column: Horizontally arranged QR + URL Connection Card
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                ServerRunningConnectionCardLandscape(
                                    uiState = uiState,
                                    onCopyUrl = { copyToClipboard(it, serverUrlLabel) },
                                    onShareUrl = { shareUrl(it) }
                                )
                            }
                        }
                    } else {
                        // Portrait Mode: Top Bar -> QR Card -> Connected Devices Section
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            MorphingGlassmorphicControlBar(
                                uiState = uiState,
                                onStopServer = { onToggleServer() },
                                onCopyPin = { copyToClipboard(it, pairingPinLabel) }
                            )

                            ServerRunningConnectionCard(
                                uiState = uiState,
                                onCopyUrl = { copyToClipboard(it, serverUrlLabel) },
                                onShareUrl = { shareUrl(it) }
                            )

                            ConnectedDevicesSection(
                                clients = uiState.connectedClients,
                                onRevokeSession = { viewModel.revokeClientSession(it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Large Interactive Power Button for State A (No Network) and State B (Ready to Share).
 */
@Composable
private fun PowerControlButton(
    isNetworkAvailable: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(175.dp)
    ) {
        // Outer Pulsing Halo Ring for State B (Network Available, Ready to Launch)
        if (isNetworkAvailable) {
            Box(
                modifier = Modifier
                    .size(165.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(Color(0xFF10B981).copy(alpha = pulseAlpha))
            )
        }

        // Main 148.dp Circular Power Control
        val buttonBackground = when {
            !isNetworkAvailable -> Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )
            else -> Brush.linearGradient(
                colors = listOf(
                    Color(0xFF10B981),
                    Color(0xFF059669)
                )
            )
        }

        val iconTint = if (!isNetworkAvailable)
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        else
            Color.White

        Box(
            modifier = Modifier
                .size(148.dp)
                .clip(CircleShape)
                .background(buttonBackground)
                .border(
                    width = 2.dp,
                    color = if (isNetworkAvailable) Color(0xFF34D399).copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape
                )
                .clickable(enabled = isNetworkAvailable) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = if (isNetworkAvailable) R.drawable.ic_power_settings else R.drawable.ic_wifi_off),
                contentDescription = "Power Control",
                modifier = Modifier.size(54.dp),
                tint = iconTint
            )
        }
    }
}

@Composable
private fun PulsingStatusDot(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF10B981)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulseDotTransition")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    Box(
        modifier = modifier
            .size(8.dp)
            .graphicsLayer {
                alpha = dotAlpha
            }
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun UptimeCounter(
    formattedUptime: String,
    modifier: Modifier = Modifier
) {
    val uptimeAscii = remember(formattedUptime) {
        "\u202D" + formattedUptime.toAsciiDigits() + "\u202C"
    }
    Text(
        text = uptimeAscii,
        style = MaterialTheme.typography.labelLarge.copy(
            localeList = androidx.compose.ui.text.intl.LocaleList("en"),
            fontFeatureSettings = "tnum"
        ),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    )
}

/**
 * State C: Morphed Glassmorphic Top Bar with Red Stop Button & Telemetry.
 */
@Composable
private fun MorphingGlassmorphicControlBar(
    uiState: HomeUiState,
    onStopServer: () -> Unit,
    onCopyPin: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(32.dp))
            .padding(horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Start: Docked Circular Red Stop Button (48.dp)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEF4444))
                    .clickable { onStopServer() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_power_settings),
                    contentDescription = "Stop Server",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Center: Telemetry (Pulsing Dot + Active/Reconnecting + Uptime)
            val isReconnecting = uiState.serverStatus == ServerStatus.RECONNECTING
            val statusColor = if (isReconnecting) Color(0xFFF59E0B) else Color(0xFF10B981)
            val statusTextRes = if (isReconnecting) R.string.reconnecting_status else R.string.online_status

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                PulsingStatusDot(color = statusColor)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(id = statusTextRes),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
                Text(
                    text = " • ",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                UptimeCounter(formattedUptime = uiState.formattedUptime)
            }

            // End: Interactive PIN Badge (Only shown when uiState.isPinRequired == true)
            if (uiState.isPinRequired && uiState.pinCode.isNotEmpty()) {
                val pinAscii = ("\u202D" + uiState.pinCode.toAsciiDigits() + "\u202C")
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable { onCopyPin(uiState.pinCode) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "PIN# $pinAscii",
                        style = MaterialTheme.typography.labelLarge.copy(
                            localeList = androidx.compose.ui.text.intl.LocaleList("en"),
                            fontFeatureSettings = "tnum"
                        ),
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
                        text = stringResource(id = R.string.storage_permission_banner_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(id = R.string.storage_permission_banner_description),
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
                Text(stringResource(id = R.string.grant_access), fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
    val offlineText = stringResource(id = R.string.offline)
    val endpoint = if (ip != null) "http://$ip:$port" else offlineText

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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                                text = stringResource(id = R.string.network_server_status_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                textAlign = TextAlign.Center
                            )

                            InspectorModeBadge(mode = mode)
                        }

                        GuidanceTip(mode = mode, modifier = Modifier.padding(top = 12.dp))
                    }

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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
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
                        text = stringResource(id = R.string.network_server_status_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center
                    )

                    InspectorModeBadge(mode = mode)

                    Spacer(modifier = Modifier.height(4.dp))

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
                text = stringResource(id = R.string.active_interface_endpoint_label),
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
                text = stringResource(id = R.string.expected_throughput_label),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = when (mode) {
                    ConnectionMode.HOTSPOT_DIRECT -> stringResource(id = R.string.throughput_hotspot_turbo)
                    ConnectionMode.WIFI_ROUTER -> stringResource(id = R.string.throughput_wifi_router)
                    ConnectionMode.DISCONNECTED -> stringResource(id = R.string.throughput_offline)
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (mode == ConnectionMode.HOTSPOT_DIRECT) Color(0xFF69F0AE) else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.basicMarquee()
            )
        }
    }

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
                text = stringResource(id = R.string.guidance_wifi_tip),
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
            Text(stringResource(id = R.string.close), fontSize = 13.sp)
        }

        Button(
            onClick = onOpenHotspotSettings,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(stringResource(id = R.string.open_hotspot_settings), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun InspectorModeBadge(mode: ConnectionMode) {
    val (label, containerColor, textColor) = when (mode) {
        ConnectionMode.HOTSPOT_DIRECT -> Triple(
            stringResource(id = R.string.hotspot_turbo_mode_active),
            Color(0xFF003829),
            Color(0xFF69F0AE)
        )
        ConnectionMode.WIFI_ROUTER -> Triple(
            stringResource(id = R.string.wifi_router_mode),
            MaterialTheme.colorScheme.surfaceContainer,
            MaterialTheme.colorScheme.onSurface
        )
        ConnectionMode.DISCONNECTED -> Triple(
            stringResource(id = R.string.server_disconnected),
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
            text = stringResource(id = R.string.connected_devices_title, clients.size.toString().toAsciiDigits()),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (clients.isEmpty()) {
            Text(
                text = stringResource(id = R.string.no_active_sessions),
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
    val (activityType, targetFileName) = client.currentActivity

    val defaultVideoName = stringResource(id = R.string.default_video_name)
    val defaultFileName = stringResource(id = R.string.default_file_name)
    val defaultMediaName = stringResource(id = R.string.default_media_name)
    val defaultStorageName = stringResource(id = R.string.default_storage_name)

    val (activityText, activityColor) = when (activityType) {
        ClientActivityType.STREAMING_VIDEO -> Pair(
            stringResource(id = R.string.client_activity_streaming, targetFileName ?: defaultVideoName),
            Color(0xFF00E676)
        )
        ClientActivityType.DOWNLOADING -> Pair(
            stringResource(id = R.string.client_activity_downloading, targetFileName ?: defaultFileName),
            Color(0xFF40C4FF)
        )
        ClientActivityType.UPLOADING -> Pair(
            stringResource(id = R.string.client_activity_uploading, targetFileName ?: defaultFileName),
            Color(0xFFFFD740)
        )
        ClientActivityType.VIEWING_MEDIA -> Pair(
            stringResource(id = R.string.client_activity_viewing, targetFileName ?: defaultMediaName),
            MaterialTheme.colorScheme.primary
        )
        ClientActivityType.BROWSING -> Pair(
            stringResource(id = R.string.client_activity_browsing, targetFileName ?: defaultStorageName),
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        ClientActivityType.IDLE -> Pair(
            stringResource(id = R.string.client_activity_idle),
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
                val isHighActivity = activityType == ClientActivityType.STREAMING_VIDEO ||
                        activityType == ClientActivityType.DOWNLOADING ||
                        activityType == ClientActivityType.UPLOADING

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isHighActivity)
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
                        tint = if (isHighActivity)
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
                Text(stringResource(id = R.string.disconnect), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
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
            stringResource(id = R.string.hotspot_turbo_short),
            Color(0xFF003829),
            Color(0xFF69F0AE)
        )
        ConnectionMode.WIFI_ROUTER -> Triple(
            stringResource(id = R.string.wifi_router_short),
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.onSurface
        )
        ConnectionMode.DISCONNECTED -> Triple(
            stringResource(id = R.string.offline_short),
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(containerColor)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Expand Inspector",
                tint = textColor,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ServerRunningConnectionCard(
    uiState: HomeUiState,
    onCopyUrl: (String) -> Unit,
    onShareUrl: (String) -> Unit
) {
    val activePort = uiState.activeServerPort ?: uiState.networkState.selectedPort
    val serverUrl = uiState.networkState.selectedIpAddress?.let { ip -> "http://$ip:$activePort" }
    val qrImage = uiState.qrCodeImage

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
            if (qrImage != null) {
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
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = serverUrl,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.basicMarquee()
                            )
                        }

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
                                        painter = painterResource(id = R.drawable.ic_content_copy),
                                        contentDescription = "Copy URL",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(stringResource(id = R.string.copy), fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share URL",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(stringResource(id = R.string.share), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerRunningConnectionCardLandscape(
    uiState: HomeUiState,
    onCopyUrl: (String) -> Unit,
    onShareUrl: (String) -> Unit
) {
    val activePort = uiState.activeServerPort ?: uiState.networkState.selectedPort
    val ip = uiState.networkState.selectedIpAddress
    val serverUrl = if (ip != null) "http://$ip:$activePort" else null
    val qrImage = uiState.qrCodeImage

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (qrImage != null) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = qrImage,
                        contentDescription = "Scan QR Code",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            if (serverUrl != null) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = serverUrl,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee()
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { onCopyUrl(serverUrl) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_content_copy),
                                    contentDescription = "Copy",
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(stringResource(id = R.string.copy), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = { onShareUrl(serverUrl) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(stringResource(id = R.string.share), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
