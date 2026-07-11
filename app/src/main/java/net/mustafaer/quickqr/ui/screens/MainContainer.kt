package net.mustafaer.quickqr.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.saveable.rememberSaveable
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
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val selectedTab = when (selectedTabIndex) {
        0 -> ScreenTab.Scanner
        1 -> ScreenTab.Generator
        2 -> ScreenTab.History
        3 -> ScreenTab.Settings
        else -> ScreenTab.Scanner
    }

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

    val resolvedLanguage = remember(language) {
        language ?: run {
            val sysLang = java.util.Locale.getDefault().language
            if (sysLang in listOf("en", "tr", "hi", "ar", "de")) sysLang else "en"
        }
    }

    BackHandler(enabled = selectedTabIndex != 0 && scannedResult == null) {
        selectedTabIndex = 0
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                // Scanner Tab
                NavigationBarItem(
                    selected = selectedTab == ScreenTab.Scanner,
                    onClick = { selectedTabIndex = 0 },
                    icon = { Icon(Icons.Outlined.QrCodeScanner, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_scan)) }
                )

                // Generator Tab
                NavigationBarItem(
                    selected = selectedTab == ScreenTab.Generator,
                    onClick = { selectedTabIndex = 1 },
                    icon = { Icon(Icons.Outlined.QrCode, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_create)) }
                )

                // History Tab
                NavigationBarItem(
                    selected = selectedTab == ScreenTab.History,
                    onClick = { selectedTabIndex = 2 },
                    icon = { Icon(Icons.Outlined.History, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_history)) }
                )

                // Settings Tab
                NavigationBarItem(
                    selected = selectedTab == ScreenTab.Settings,
                    onClick = { selectedTabIndex = 3 },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_settings)) }
                )
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                if (targetState.index > initialState.index) {
                    (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                        slideOutHorizontally { width -> -width } + fadeOut()
                    )
                } else {
                    (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                        slideOutHorizontally { width -> width } + fadeOut()
                    )
                }
            },
            label = "tabTransition",
            modifier = Modifier.fillMaxSize()
        ) { targetTab ->
            when (targetTab) {
                ScreenTab.Scanner -> {
                    ScannerScreen(
                        isPaused = isScannerPaused,
                        onCodeScanned = { code -> viewModel.onCodeScanned(code) },
                        onGalleryScan = { uri, onSuccess, onFailure ->
                            viewModel.scanFromGallery(uri, onSuccess, onFailure)
                        },
                        contentPadding = innerPadding
                    )
                }
                ScreenTab.Generator -> {
                    GeneratorScreen(
                        text = generatorText,
                        qrBitmap = generatedQrBitmap,
                        onTextChange = { txt -> viewModel.updateGeneratorText(txt) },
                        onGenerate = { viewModel.generateQrCode() },
                        onSave = { viewModel.saveQrCodeToGallery() },
                        onShare = { viewModel.getQrCodeShareUri() },
                        contentPadding = innerPadding
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
                        onExport = { format -> viewModel.getExportFileUri(format) },
                        contentPadding = innerPadding
                    )
                }
                ScreenTab.Settings -> {
                    SettingsScreen(
                        hapticEnabled = hapticEnabled,
                        continuousMode = continuousMode,
                        language = resolvedLanguage,
                        onHapticToggle = { enabled -> viewModel.setHapticEnabled(enabled) },
                        onContinuousToggle = { enabled -> viewModel.setContinuousMode(enabled) },
                        onLanguageSelect = { lang -> viewModel.setLanguage(lang) },
                        contentPadding = innerPadding
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
