package com.anasdev.fluxlink.presentation.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.anasdev.fluxlink.BuildConfig
import com.anasdev.fluxlink.R
import com.anasdev.fluxlink.domain.model.AppLanguage
import com.anasdev.fluxlink.domain.model.AppTheme
import com.anasdev.fluxlink.presentation.state.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val uriHandler = LocalUriHandler.current

    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showPortDialog by remember { mutableStateOf(false) }

    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = uiState.appTheme,
            onSelectTheme = { selectedTheme ->
                viewModel.setAppTheme(selectedTheme)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showLanguageDialog) {
        LanguageSelectionDialog(
            currentLanguage = uiState.appLanguage,
            onSelectLanguage = { selectedLanguage ->
                viewModel.setAppLanguage(selectedLanguage)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showPortDialog) {
        PortSelectionDialog(
            currentPort = uiState.serverPort,
            onSetPort = { newPort ->
                viewModel.setServerPort(newPort)
                showPortDialog = false
            },
            onDismiss = { showPortDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.settings_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // Section 1: Appearance & Language
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(id = R.string.section_appearance),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp)
                )

                // Item 1: App Theme Mode
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showThemeDialog = true }
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(id = R.string.theme_setting_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(id = uiState.appTheme.titleRes),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 13.sp
                            )
                        },
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_palette),
                                    contentDescription = "Theme Icon",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                }

                // Item 2: App Language / Locale
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLanguageDialog = true }
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(id = R.string.language_setting_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(id = uiState.appLanguage.titleRes),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 13.sp
                            )
                        },
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_language),
                                    contentDescription = "Language Icon",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                }
            }

            // Section 2: Security & Access
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(id = R.string.section_security),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp)
                )

                // Item 3: Require PIN Auth Switch
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(id = R.string.require_pin_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(id = R.string.require_pin_desc),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_lock),
                                    contentDescription = "Security",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        trailingContent = {
                            Switch(
                                checked = uiState.requirePinAuth,
                                onCheckedChange = { viewModel.setRequirePinAuth(it) }
                            )
                        }
                    )
                }
            }

            // Section 3: Server & File Visibility
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(id = R.string.section_server_files),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp)
                )

                // Item 4: Show Hidden Files Switch
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(id = R.string.show_hidden_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(id = R.string.show_hidden_desc),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_visibility),
                                    contentDescription = "Hidden Files",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        trailingContent = {
                            Switch(
                                checked = uiState.showHiddenFiles,
                                onCheckedChange = { viewModel.setShowHiddenFiles(it) }
                            )
                        }
                    )
                }

                // Item 5: Server Port Selection
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPortDialog = true }
                ) {
                    val portAscii = uiState.serverPort.toString().toAsciiDigits()
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(id = R.string.server_port_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        },
                        supportingContent = {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(
                                    text = stringResource(id = R.string.server_port_desc_format, portAscii),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 12.sp,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        localeList = androidx.compose.ui.text.intl.LocaleList("en"),
                                        fontFeatureSettings = "tnum"
                                    )
                                )
                            }
                        },
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_numbers),
                                    contentDescription = "Port",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                }

                // Item 6: Keep Screen On Switch
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(id = R.string.keep_screen_on_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(id = R.string.keep_screen_on_desc),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_stay_current_portrait),
                                    contentDescription = "Keep Screen On",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        trailingContent = {
                            Switch(
                                checked = uiState.keepScreenOn,
                                onCheckedChange = { viewModel.setKeepScreenOn(it) }
                            )
                        }
                    )
                }
            }

            // Section 4: About Section
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(id = R.string.section_about),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp)
                )

                // Item 7: Developer Info
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(id = R.string.developer_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(id = R.string.developer_name),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_code),
                                    contentDescription = "Developer",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                }

                // Item 8: GitHub Repository Link
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            try {
                                uriHandler.openUri("https://github.com/EngAnasMuhammad/AirSlice")
                            } catch (_: Exception) {
                                // Fallback
                            }
                        }
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(id = R.string.github_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        },
                        supportingContent = {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(
                                    text = stringResource(id = R.string.github_url_display),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_public),
                                    contentDescription = "GitHub",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                }
            }

            // Professional App Version & Info Footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_app_logo),
                    contentDescription = "App Logo",
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                )

                Text(
                    text = stringResource(id = R.string.app_name),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                val cleanVersion = BuildConfig.VERSION_NAME.removeSuffix("-debug")
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = "v${cleanVersion.toAsciiDigits()}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            localeList = androidx.compose.ui.text.intl.LocaleList("en"),
                            fontFeatureSettings = "tnum"
                        ),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = stringResource(id = R.string.app_features),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
            }

        }
    }
}

@Composable
private fun ThemeSelectionDialog(
    currentTheme: AppTheme,
    onSelectTheme: (AppTheme) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(id = R.string.theme_dialog_title),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup()
            ) {
                AppTheme.entries.forEach { theme ->
                    val isSelected = theme == currentTheme
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isSelected,
                                onClick = { onSelectTheme(theme) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null
                        )
                        Text(
                            text = stringResource(id = theme.titleRes),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.cancel))
            }
        }
    )
}

@Composable
private fun LanguageSelectionDialog(
    currentLanguage: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(id = R.string.language_dialog_title),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup()
            ) {
                AppLanguage.entries.forEach { lang ->
                    val isSelected = lang == currentLanguage
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isSelected,
                                onClick = { onSelectLanguage(lang) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null
                        )
                        Text(
                            text = stringResource(id = lang.titleRes),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.cancel))
            }
        }
    )
}

@Composable
private fun PortSelectionDialog(
    currentPort: Int,
    onSetPort: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var textValue by remember { mutableStateOf(currentPort.toString()) }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(id = R.string.port_dialog_title),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = {
                        textValue = it.filter { char -> char.isDigit() }
                        val portNum = textValue.toIntOrNull()
                        isError = portNum == null || portNum !in 1024..65535
                    },
                    label = { Text(stringResource(id = R.string.port_dialog_hint)) },
                    isError = isError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isError) {
                    Text(
                        text = stringResource(id = R.string.port_invalid_error),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val portNum = textValue.toIntOrNull()
                    if (portNum != null && portNum in 1024..65535) {
                        onSetPort(portNum)
                    } else {
                        isError = true
                    }
                },
                enabled = !isError && textValue.isNotEmpty()
            ) {
                Text(stringResource(id = R.string.apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.cancel))
            }
        }
    )
}
