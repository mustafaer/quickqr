package net.mustafaer.quickqr.ui.viewmodel

import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.mustafaer.quickqr.QuickQrApp
import net.mustafaer.quickqr.ScanFeedback
import net.mustafaer.quickqr.data.ImageCodeReader
import net.mustafaer.quickqr.data.ImageReadResult
import net.mustafaer.quickqr.data.ScanRepository
import net.mustafaer.quickqr.data.SettingsRepository
import net.mustafaer.quickqr.utils.ScanClassifier
import net.mustafaer.quickqr.utils.ScanContent

enum class ScanOrigin { CAMERA, GALLERY, HISTORY }

/**
 * What the scanner is doing. One value, so the result sheet can never render a
 * new payload with the previous payload's type — the tearing two separate flows
 * allowed. "Paused" is not a separate flag: the camera is paused exactly while
 * a result is showing.
 */
sealed interface ScanSession {
    data object Scanning : ScanSession
    data class Showing(val content: ScanContent, val origin: ScanOrigin) : ScanSession
}

sealed interface ScannerEvent {
    /** Continuous mode stored a scan without opening the sheet. */
    data class Saved(val content: ScanContent, val summary: String?) : ScannerEvent
    data object NoCodeInImage : ScannerEvent
    data object ImageUnreadable : ScannerEvent
}

class ScannerViewModel(
    private val scans: ScanRepository,
    private val settings: SettingsRepository,
    private val imageReader: ImageCodeReader,
    private val feedback: ScanFeedback,
    private val parsingDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: () -> Long = SystemClock::elapsedRealtime
) : ViewModel() {

    companion object {
        /**
         * How long a code must be out of view before it counts as gone. ML Kit
         * misses the odd frame of a code that is still there; without this a
         * single dropped frame would re-fire the same scan.
         */
        internal const val ABSENT_AFTER_MS = 800L

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as QuickQrApp).container
                ScannerViewModel(
                    container.scans,
                    container.settings,
                    container.imageReader,
                    container.scanFeedback
                )
            }
        }
    }

    private val _session = MutableStateFlow<ScanSession>(ScanSession.Scanning)
    val session: StateFlow<ScanSession> = _session.asStateFlow()

    private val _events = Channel<ScannerEvent>(Channel.BUFFERED)
    val events: Flow<ScannerEvent> = _events.receiveAsFlow()

    private val continuousMode = settings.continuousMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val hapticEnabled = settings.hapticEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Starts true so the notice cannot flash up before the setting has loaded. */
    val privacyNoticeSeen: StateFlow<Boolean> = settings.privacyNoticeSeen
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** The code currently in view, and when it was last seen there. */
    private var present: String? = null
    private var lastSeenAt = 0L

    /** Set while a camera scan is being parsed, so a second code cannot race it. */
    private var delivering = false

    /**
     * Called for every analysed camera frame, including frames with no code in
     * them. A code fires once when it enters the view and not again until it has
     * left — holding the phone over the same code no longer re-saves it, buzzes
     * and shows a snackbar every two seconds.
     */
    fun onFrame(raw: String?) {
        val now = clock()
        if (raw == null) {
            if (now - lastSeenAt > ABSENT_AFTER_MS) present = null
            return
        }
        lastSeenAt = now
        if (raw == present) return
        present = raw

        if (delivering || _session.value is ScanSession.Showing) return
        delivering = true
        viewModelScope.launch {
            try {
                deliver(raw, ScanOrigin.CAMERA)
            } finally {
                delivering = false
            }
        }
    }

    fun onImagePicked(uri: Uri) {
        viewModelScope.launch {
            when (val result = imageReader.read(uri)) {
                is ImageReadResult.Found -> deliver(result.raw, ScanOrigin.GALLERY)
                ImageReadResult.NotFound -> _events.send(ScannerEvent.NoCodeInImage)
                ImageReadResult.Unreadable -> _events.send(ScannerEvent.ImageUnreadable)
            }
        }
    }

    /** Re-opens a stored scan. It is already in history, so nothing is recorded. */
    fun showStored(raw: String) {
        viewModelScope.launch {
            _session.value = ScanSession.Showing(classify(raw), ScanOrigin.HISTORY)
        }
    }

    fun dismiss() {
        _session.value = ScanSession.Scanning
    }

    fun dismissPrivacyNotice() {
        viewModelScope.launch { settings.setPrivacyNoticeSeen() }
    }

    private suspend fun deliver(raw: String, origin: ScanOrigin) {
        val content = classify(raw)
        // A gallery pick is a deliberate request, so it always opens the sheet —
        // even in continuous mode, where a live camera scan only shows a snackbar.
        if (origin == ScanOrigin.CAMERA && continuousMode.value) {
            _events.send(ScannerEvent.Saved(content, ScanClassifier.summarise(content)))
        } else {
            _session.value = ScanSession.Showing(content, origin)
        }
        if (hapticEnabled.value) feedback.onScanned()
        // A failed write must not take the scan result down with it.
        runCatching { scans.record(raw, content.type) }
    }

    private suspend fun classify(raw: String): ScanContent =
        withContext(parsingDispatcher) { ScanClassifier.classify(raw) }
}
