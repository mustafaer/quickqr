package net.mustafaer.quickqr.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.mustafaer.quickqr.R
import net.mustafaer.quickqr.ui.components.ScanResultSheet
import net.mustafaer.quickqr.ui.viewmodel.AppViewModel

sealed class ScreenTab(val index: Int) {
    data object Scanner : ScreenTab(0)
    data object Generator : ScreenTab(1)
    data object History : ScreenTab(2)
    data object Settings : ScreenTab(3)
}

@Composable
fun MainContainer(
    viewModel: AppViewModel
) {
    var selectedTab by remember { mutableStateOf<ScreenTab>(ScreenTab.Scanner) }

    val hapticEnabled by viewModel.hapticEnabled.collectAsStateWithLifecycle()
    val continuousMode by viewModel.continuousMode.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()

    val historyItems by viewModel.historyItems.collectAsStateWithLifecycle()
    val searchQuery by viewModel.historySearchQuery.collectAsStateWithLifecycle()

    val scannedResult by viewModel.scannedResult.collectAsStateWithLifecycle()
    val scannedType by viewModel.scannedType.collectAsStateWithLifecycle()
    val isScannerPaused by viewModel.isScannerPaused.collectAsStateWithLifecycle()

    val generatorText by viewModel.generatorText.collectAsStateWithLifecycle()
    val generatedQrBitmap by viewModel.generatedQrBitmap.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                // Scanner Tab
                NavigationBarItem(
                    selected = selectedTab == ScreenTab.Scanner,
                    onClick = { selectedTab = ScreenTab.Scanner },
                    icon = { Icon(Icons.Outlined.QrCodeScanner, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_scan)) }
                )

                // Generator Tab
                NavigationBarItem(
                    selected = selectedTab == ScreenTab.Generator,
                    onClick = { selectedTab = ScreenTab.Generator },
                    icon = { Icon(Icons.Outlined.QrCode, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_create)) }
                )

                // History Tab
                NavigationBarItem(
                    selected = selectedTab == ScreenTab.History,
                    onClick = { selectedTab = ScreenTab.History },
                    icon = { Icon(Icons.Outlined.History, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_history)) }
                )

                // Settings Tab
                NavigationBarItem(
                    selected = selectedTab == ScreenTab.Settings,
                    onClick = { selectedTab = ScreenTab.Settings },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_settings)) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                ScreenTab.Scanner -> {
                    ScannerScreen(
                        isPaused = isScannerPaused,
                        onCodeScanned = { code -> viewModel.onCodeScanned(code) },
                        onGalleryScan = { uri, onSuccess, onFailure ->
                            viewModel.scanFromGallery(uri, onSuccess, onFailure)
                        }
                    )
                }
                ScreenTab.Generator -> {
                    GeneratorScreen(
                        text = generatorText,
                        qrBitmap = generatedQrBitmap,
                        onTextChange = { txt -> viewModel.updateGeneratorText(txt) },
                        onGenerate = { viewModel.generateQrCode() },
                        onSave = { viewModel.saveQrCodeToGallery() },
                        onShare = { viewModel.getQrCodeShareUri() }
                    )
                }
                ScreenTab.History -> {
                    HistoryScreen(
                        historyList = historyItems,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { query -> viewModel.updateHistorySearchQuery(query) },
                        onItemSelect = { item -> viewModel.selectHistoryItem(item) },
                        onItemDelete = { item -> viewModel.deleteHistoryItem(item) },
                        onClearAll = { viewModel.clearAllHistory() },
                        onExport = { format -> viewModel.getExportFileUri(format) }
                    )
                }
                ScreenTab.Settings -> {
                    SettingsScreen(
                        hapticEnabled = hapticEnabled,
                        continuousMode = continuousMode,
                        language = language,
                        onHapticToggle = { enabled -> viewModel.setHapticEnabled(enabled) },
                        onContinuousToggle = { enabled -> viewModel.setContinuousMode(enabled) },
                        onLanguageSelect = { lang -> viewModel.setLanguage(lang) }
                    )
                }
            }
        }
    }

    // Modal bottom sheet overlays for scan results
    if (scannedResult != null) {
        ScanResultSheet(
            text = scannedResult!!,
            type = scannedType,
            onDismiss = { viewModel.resumeScanner() }
        )
    }
}
