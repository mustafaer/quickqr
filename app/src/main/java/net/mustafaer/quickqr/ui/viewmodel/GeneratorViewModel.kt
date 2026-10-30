package net.mustafaer.quickqr.ui.viewmodel

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import net.mustafaer.quickqr.QuickQrApp
import net.mustafaer.quickqr.data.MediaExporter
import net.mustafaer.quickqr.utils.QrEncoder
import net.mustafaer.quickqr.utils.QrPayloadDraft
import net.mustafaer.quickqr.utils.QrResult

class GeneratorViewModel(private val media: MediaExporter) : ViewModel() {

    companion object {
        private const val ENCODE_DEBOUNCE_MS = 250L

        val Factory = viewModelFactory {
            initializer {
                GeneratorViewModel((this[APPLICATION_KEY] as QuickQrApp).container.media)
            }
        }
    }

    /**
     * Compose state rather than a StateFlow: the text fields read and write it
     * synchronously, so fast typing can never race a collector and drop
     * characters or move the cursor — the failure mode Google documents for
     * text-field state held in an asynchronous stream.
     */
    var draft by mutableStateOf(QrPayloadDraft())
        private set

    /**
     * The live preview. `mapLatest` cancels an encode the moment newer input
     * arrives, so a stale symbol can never be published over a fresh one.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val result: StateFlow<QrResult> = snapshotFlow { draft.build() }
        .distinctUntilChanged()
        .debounce { payload -> if (payload.isBlank()) 0L else ENCODE_DEBOUNCE_MS }
        .mapLatest { payload -> withContext(Dispatchers.Default) { QrEncoder.encode(payload) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QrResult.Empty)

    fun updateDraft(newDraft: QrPayloadDraft) {
        draft = newDraft
    }

    /** Clears every field but keeps the selected content type. */
    fun clear() {
        draft = QrPayloadDraft(type = draft.type)
    }

    suspend fun saveToGallery(): Uri? =
        (result.value as? QrResult.Success)?.let { media.saveToGallery(it.matrix) }

    suspend fun shareableImage(): Uri? =
        (result.value as? QrResult.Success)?.let { media.shareableImage(it.matrix) }
}
