package net.mustafaer.quickqr.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.mustafaer.quickqr.R
import net.mustafaer.quickqr.data.AppLanguage
import net.mustafaer.quickqr.ui.components.ScanResultSheet
import net.mustafaer.quickqr.ui.theme.labelRes
import net.mustafaer.quickqr.ui.viewmodel.GeneratorViewModel
import net.mustafaer.quickqr.ui.viewmodel.HistoryEvent
import net.mustafaer.quickqr.ui.viewmodel.HistoryViewModel
import net.mustafaer.quickqr.ui.viewmodel.ScanOrigin
import net.mustafaer.quickqr.ui.viewmodel.ScanSession
import net.mustafaer.quickqr.ui.viewmodel.ScannerEvent
import net.mustafaer.quickqr.ui.viewmodel.ScannerViewModel
import net.mustafaer.quickqr.ui.viewmodel.SettingsState
import net.mustafaer.quickqr.ui.viewmodel.SettingsViewModel
import net.mustafaer.quickqr.utils.ScanContent
import java.util.Locale

enum class ScreenTab(val icon: ImageVector, val labelRes: Int) {
    Scanner(Icons.Outlined.QrCodeScanner, R.string.nav_scan),
    Generator(Icons.Outlined.QrCode, R.string.nav_create),
    History(Icons.Outlined.History, R.string.nav_history),
    Settings(Icons.Outlined.Settings, R.string.nav_settings)
}

/** How long the viewfinder holds its lock on a code before the result sheet opens. */
private const val LOCK_ON_MS = 220L

/** How long a continuous-mode scan stays highlighted in the viewfinder. */
private const val PULSE_MS = 700L

@Composable
fun MainContainer(settings: SettingsState, settingsViewModel: SettingsViewModel) {
    val scannerViewModel: ScannerViewModel = viewModel(factory = ScannerViewModel.Factory)
    val generatorViewModel: GeneratorViewModel = viewModel(factory = GeneratorViewModel.Factory)
    val historyViewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory)

    var selectedTab by rememberSaveable { mutableStateOf(ScreenTab.Scanner) }
    // Keeps each tab's scroll position and fields across tab switches; the
    // AnimatedContent below would otherwise dispose them.
    val tabStates = rememberSaveableStateHolder()
    val snackbarHostState = remember { SnackbarHostState() }

    val session by scannerViewModel.session.collectAsStateWithLifecycle()
    val privacyNoticeSeen by scannerViewModel.privacyNoticeSeen.collectAsStateWithLifecycle()
    val showing = session as? ScanSession.Showing

    // A camera scan first locks the viewfinder onto the code, then opens the
    // sheet, so the eye sees what was read. Gallery and history open at once.
    var sheetContent by remember { mutableStateOf<ScanContent?>(null) }
    LaunchedEffect(showing) {
        if (showing == null) {
            sheetContent = null
            return@LaunchedEffect
        }
        if (showing.origin == ScanOrigin.CAMERA) delay(LOCK_ON_MS)
        sheetContent = showing.content
    }

    var pulseCode by remember { mutableStateOf<String?>(null) }
    val lockedCode = showing?.takeIf { it.origin == ScanOrigin.CAMERA }?.content?.raw ?: pulseCode

    // The effective language, read from the configuration rather than from app
    // state — that way it is correct whether it was chosen in this app's picker
    // or in Android Settings, and it updates without a restart.
    val configuration = LocalConfiguration.current
    val language = remember(configuration) {
        AppLanguage.matchingSystemLocale(ConfigurationCompat.getLocales(configuration)[0] ?: Locale.getDefault())
    }

    val resources = LocalResources.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val savedLabel = stringResource(R.string.scan_saved_to_history)
    val noCodeMessage = stringResource(R.string.no_qr_found)
    val unreadableMessage = stringResource(R.string.error_image_unreadable)
    val deletedMessage = stringResource(R.string.history_deleted)
    val undoLabel = stringResource(R.string.history_undo)

    LaunchedEffect(scannerViewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            scannerViewModel.events.collect { event ->
                when (event) {
                    is ScannerEvent.Saved -> {
                        val raw = event.content.raw
                        pulseCode = raw
                        launch {
                            delay(PULSE_MS)
                            if (pulseCode == raw) pulseCode = null
                        }
                        // The derived title, never the raw payload: a continuous
                        // scan of a Wi-Fi code used to print its password here.
                        val label = event.summary ?: resources.getString(event.content.type.labelRes)
                        showMessage(snackbarHostState, "$savedLabel: $label")
                    }
                    ScannerEvent.NoCodeInImage -> showMessage(snackbarHostState, noCodeMessage)
                    ScannerEvent.ImageUnreadable -> showMessage(snackbarHostState, unreadableMessage)
                }
            }
        }
    }

    LaunchedEffect(historyViewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            historyViewModel.events.collect { event ->
                when (event) {
                    is HistoryEvent.Deleted -> launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        val result = snackbarHostState.showSnackbar(
                            message = deletedMessage,
                            actionLabel = undoLabel,
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            historyViewModel.undoDelete(event.id)
                        } else {
                            historyViewModel.commitDelete(event.id)
                        }
                    }
                }
            }
        }
    }

    BackHandler(enabled = selectedTab != ScreenTab.Scanner && sheetContent == null) {
        selectedTab = ScreenTab.Scanner
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                ScreenTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // The scanner stays in the composition for the whole session, so
            // returning to the tab is instant; only the camera binding follows
            // the tab, and the viewfinder's animation stops while it is hidden.
            ScannerScreen(
                isActive = selectedTab == ScreenTab.Scanner,
                isPaused = showing != null,
                lockedCode = lockedCode,
                showPrivacyNotice = !privacyNoticeSeen,
                onDismissPrivacyNotice = scannerViewModel::dismissPrivacyNotice,
                onFrame = scannerViewModel::onFrame,
                onImagePicked = scannerViewModel::onImagePicked,
                contentPadding = innerPadding
            )

            // Opaque scrim over the live camera. Without it, the gap between the
            // outgoing and incoming panels during a tab slide briefly exposes the
            // scanner underneath — even when neither tab is the scanner.
            AnimatedVisibility(
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
                    val forward = targetState.ordinal > initialState.ordinal
                    (slideInHorizontally { width -> if (forward) width else -width } + fadeIn())
                        .togetherWith(slideOutHorizontally { width -> if (forward) -width else width } + fadeOut())
                },
                label = "tabTransition",
                modifier = Modifier.fillMaxSize()
            ) { tab ->
                tabStates.SaveableStateProvider(tab.name) {
                    when (tab) {
                        // Transparent: the live scanner underneath is the Scanner tab.
                        ScreenTab.Scanner -> Box(modifier = Modifier.fillMaxSize())

                        ScreenTab.Generator -> {
                            val result by generatorViewModel.result.collectAsStateWithLifecycle()
                            GeneratorScreen(
                                draft = generatorViewModel.draft,
                                result = result,
                                onDraftChange = generatorViewModel::updateDraft,
                                onClear = generatorViewModel::clear,
                                onSave = generatorViewModel::saveToGallery,
                                onShare = generatorViewModel::shareableImage,
                                snackbarHostState = snackbarHostState,
                                contentPadding = innerPadding
                            )
                        }

                        ScreenTab.History -> {
                            val rows by historyViewModel.rows.collectAsStateWithLifecycle()
                            HistoryScreen(
                                rows = rows,
                                query = historyViewModel.query,
                                onQueryChange = historyViewModel::updateQuery,
                                onItemSelect = { scannerViewModel.showStored(it.entity.text) },
                                onDelete = { historyViewModel.delete(it.id) },
                                onTogglePin = historyViewModel::togglePin,
                                onClearAll = historyViewModel::clearAll,
                                onExport = historyViewModel::export,
                                snackbarHostState = snackbarHostState,
                                contentPadding = innerPadding
                            )
                        }

                        ScreenTab.Settings -> SettingsScreen(
                            state = settings,
                            language = language,
                            onHapticToggle = settingsViewModel::setHapticEnabled,
                            onContinuousToggle = settingsViewModel::setContinuousMode,
                            onLanguageSelect = settingsViewModel::setLanguage,
                            onThemeModeSelect = settingsViewModel::setThemeMode,
                            onDynamicColorToggle = settingsViewModel::setDynamicColor,
                            snackbarHostState = snackbarHostState,
                            contentPadding = innerPadding
                        )
                    }
                }
            }
        }
    }

    sheetContent?.let { content ->
        ScanResultSheet(content = content, onDismiss = scannerViewModel::dismiss)
    }
}
