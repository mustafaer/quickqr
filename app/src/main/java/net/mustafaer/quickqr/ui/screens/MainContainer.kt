package net.mustafaer.quickqr.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.os.ConfigurationCompat
import net.mustafaer.quickqr.R
import net.mustafaer.quickqr.data.AppLanguage
import net.mustafaer.quickqr.ui.components.ScanResultSheet
import net.mustafaer.quickqr.ui.viewmodel.AppViewModel
import java.util.Locale

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
    val selectedTab = remember(selectedTabIndex) {
        when (selectedTabIndex) {
            1 -> ScreenTab.Generator
            2 -> ScreenTab.History
            3 -> ScreenTab.Settings
            else -> ScreenTab.Scanner
        }
    }

    val hapticEnabled by viewModel.hapticEnabled.collectAsStateWithLifecycle()
    val continuousMode by viewModel.continuousMode.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()

    val historyItems by viewModel.historyItems.collectAsStateWithLifecycle()
    val searchQuery by viewModel.historySearchQuery.collectAsStateWithLifecycle()

    val scannedResult by viewModel.scannedResult.collectAsStateWithLifecycle()
    val scannedType by viewModel.scannedType.collectAsStateWithLifecycle()
    val isScannerPaused by viewModel.isScannerPaused.collectAsStateWithLifecycle()
    val lastContinuousScan by viewModel.lastContinuousScan.collectAsStateWithLifecycle()

    val generatorDraft by viewModel.generatorDraft.collectAsStateWithLifecycle()
    val qrResult by viewModel.qrResult.collectAsStateWithLifecycle()

    // The effective language, read from the configuration rather than from app
    // state — that way it is correct whether it was chosen in this app's picker
    // or in Android Settings, and it updates without a restart.
    val configuration = LocalConfiguration.current
    val language = remember(configuration) {
        AppLanguage.matchingSystemLocale(
            ConfigurationCompat.getLocales(configuration)[0] ?: Locale.getDefault()
        )
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val continuousScanLabel = stringResource(R.string.scan_saved_to_history)

    // Continuous mode has no result sheet, so this is the only confirmation the
    // user gets. A snackbar keeps it inside the app's language and theme, which a
    // raw Toast did not.
    LaunchedEffect(lastContinuousScan) {
        val scan = lastContinuousScan ?: return@LaunchedEffect
        val preview = if (scan.text.length > 40) scan.text.take(40) + "…" else scan.text
        viewModel.consumeContinuousScan()
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(
            message = "$continuousScanLabel: $preview",
            duration = SnackbarDuration.Short
        )
    }

    BackHandler(enabled = selectedTabIndex != 0 && scannedResult == null) {
        selectedTabIndex = 0
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                NavigationBarItem(
                    selected = selectedTab == ScreenTab.Scanner,
                    onClick = { selectedTabIndex = 0 },
                    icon = { Icon(Icons.Outlined.QrCodeScanner, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_scan)) }
                )
                NavigationBarItem(
                    selected = selectedTab == ScreenTab.Generator,
                    onClick = { selectedTabIndex = 1 },
                    icon = { Icon(Icons.Outlined.QrCode, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_create)) }
                )
                NavigationBarItem(
                    selected = selectedTab == ScreenTab.History,
                    onClick = { selectedTabIndex = 2 },
                    icon = { Icon(Icons.Outlined.History, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_history)) }
                )
                NavigationBarItem(
                    selected = selectedTab == ScreenTab.Settings,
                    onClick = { selectedTabIndex = 3 },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_settings)) }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // The scanner stays in the composition for the whole session. Leaving
            // the tab used to tear down the PreviewView, the analysis executor and
            // the ML Kit client and rebuild all of them on the way back; now only
            // the camera use cases unbind, so returning to the tab is instant and
            // the camera still releases while the user is elsewhere.
            ScannerScreen(
                isActive = selectedTab == ScreenTab.Scanner,
                isPaused = isScannerPaused,
                onCodeScanned = viewModel::onCodeScanned,
                onGalleryScan = viewModel::scanFromGallery,
                contentPadding = innerPadding
            )

            // Opaque scrim over the live camera. Without it, the gap between the
            // outgoing and incoming panels during a tab slide briefly exposes the
            // scanner underneath — even when neither tab is the scanner.
            androidx.compose.animation.AnimatedVisibility(
                visible = selectedTab != ScreenTab.Scanner,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                )
            }

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
                    // Transparent: the live scanner underneath is the Scanner tab.
                    ScreenTab.Scanner -> Box(modifier = Modifier.fillMaxSize())

                    ScreenTab.Generator -> GeneratorScreen(
                        draft = generatorDraft,
                        result = qrResult,
                        onDraftChange = viewModel::updateGeneratorDraft,
                        onGenerate = viewModel::generateQrCode,
                        onClear = viewModel::clearGenerator,
                        onSave = viewModel::saveQrCodeToGallery,
                        onShare = viewModel::getQrCodeShareUri,
                        contentPadding = innerPadding
                    )

                    ScreenTab.History -> HistoryScreen(
                        historyList = historyItems,
                        searchQuery = searchQuery,
                        onSearchQueryChange = viewModel::updateHistorySearchQuery,
                        onItemSelect = viewModel::selectHistoryItem,
                        onItemDelete = viewModel::deleteHistoryItem,
                        onClearAll = viewModel::clearAllHistory,
                        onExport = viewModel::getExportFileUri,
                        contentPadding = innerPadding
                    )

                    ScreenTab.Settings -> SettingsScreen(
                        hapticEnabled = hapticEnabled,
                        continuousMode = continuousMode,
                        language = language,
                        themeMode = themeMode,
                        dynamicColor = dynamicColor,
                        onHapticToggle = viewModel::setHapticEnabled,
                        onContinuousToggle = viewModel::setContinuousMode,
                        onLanguageSelect = viewModel::setLanguage,
                        onThemeModeSelect = viewModel::setThemeMode,
                        onDynamicColorToggle = viewModel::setDynamicColor,
                        contentPadding = innerPadding
                    )
                }
            }
        }
    }

    scannedResult?.let { result ->
        ScanResultSheet(
            text = result,
            type = scannedType,
            onDismiss = viewModel::resumeScanner
        )
    }
}
