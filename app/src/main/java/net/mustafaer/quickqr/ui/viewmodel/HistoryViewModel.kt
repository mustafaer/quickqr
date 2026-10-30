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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.mustafaer.quickqr.QuickQrApp
import net.mustafaer.quickqr.data.MediaExporter
import net.mustafaer.quickqr.data.ScanEntity
import net.mustafaer.quickqr.data.ScanRepository
import net.mustafaer.quickqr.utils.ExportFormat
import net.mustafaer.quickqr.utils.HistoryFilter
import net.mustafaer.quickqr.utils.ScanClassifier
import net.mustafaer.quickqr.utils.ScanResultType

/**
 * One history row, ready to draw. [title] is derived once per database change,
 * off the main thread — not per frame, and never the raw Wi-Fi payload.
 */
data class HistoryRow(
    val entity: ScanEntity,
    val type: ScanResultType,
    val title: String?
) {
    val id: Int get() = entity.id
}

sealed interface HistoryEvent {
    data class Deleted(val id: Int) : HistoryEvent
}

class HistoryViewModel(
    private val scans: ScanRepository,
    private val media: MediaExporter,
    /** Outlives this ViewModel, so a delete still pending at exit is committed. */
    private val applicationScope: CoroutineScope
) : ViewModel() {

    companion object {
        /**
         * Upper bound on how long a deleted row stays recoverable when the screen
         * never reports back — the user left mid-snackbar. Normally the snackbar's
         * own result commits or restores the row well before this.
         */
        private const val MAX_UNDO_WINDOW_MS = 15_000L
        private const val SEARCH_DEBOUNCE_MS = 250L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as QuickQrApp
                HistoryViewModel(app.container.scans, app.container.media, app.applicationScope)
            }
        }
    }

    var query by mutableStateOf("")
        private set

    private val pendingDeletes = MutableStateFlow<Set<Int>>(emptySet())
    private val pendingJobs = mutableMapOf<Int, Job>()

    private val _events = Channel<HistoryEvent>(Channel.BUFFERED)
    val events: Flow<HistoryEvent> = _events.receiveAsFlow()

    private val allRows: Flow<List<HistoryRow>> = scans.scans
        .map { entities ->
            entities.map { entity ->
                val content = ScanClassifier.classify(entity.text)
                HistoryRow(entity, ScanResultType.fromString(entity.type), ScanClassifier.summarise(content))
            }
        }
        .flowOn(Dispatchers.Default)

    /** Null until the first database read, so the empty state never flashes. */
    @OptIn(FlowPreview::class)
    val rows: StateFlow<List<HistoryRow>?> = combine(
        allRows,
        snapshotFlow { query }.debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS },
        pendingDeletes
    ) { rows, needle, pending ->
        HistoryFilter.filter(rows.filterNot { it.id in pending }, needle) {
            listOf(it.entity.text, it.entity.type, it.title)
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun updateQuery(value: String) {
        query = value
    }

    /** Hides the row at once; the delete is committed by [commitDelete] or the fallback timer. */
    fun delete(id: Int) {
        if (id in pendingDeletes.value) return
        pendingDeletes.update { it + id }
        pendingJobs[id] = viewModelScope.launch {
            delay(MAX_UNDO_WINDOW_MS)
            commitDelete(id)
        }
        viewModelScope.launch { _events.send(HistoryEvent.Deleted(id)) }
    }

    fun undoDelete(id: Int) {
        pendingJobs.remove(id)?.cancel()
        pendingDeletes.update { it - id }
    }

    fun commitDelete(id: Int) {
        if (id !in pendingDeletes.value) return
        pendingJobs.remove(id)?.cancel()
        viewModelScope.launch {
            // The row leaves the database before it leaves the pending set, so it
            // never reappears for a frame in between.
            runCatching { scans.delete(listOf(id)) }
            pendingDeletes.update { it - id }
        }
    }

    fun togglePin(row: HistoryRow) {
        viewModelScope.launch { runCatching { scans.setPinned(row.id, !row.entity.pinned) } }
    }

    fun clearAll() {
        pendingJobs.values.forEach { it.cancel() }
        pendingJobs.clear()
        viewModelScope.launch {
            runCatching { scans.clear() }
            pendingDeletes.value = emptySet()
        }
    }

    /** Exports what the user is looking at — search filter included. */
    suspend fun export(format: ExportFormat): Uri? =
        media.exportHistory(rows.value.orEmpty().map { it.entity }, format)

    override fun onCleared() {
        val pending = pendingDeletes.value
        if (pending.isNotEmpty()) applicationScope.launch { runCatching { scans.delete(pending) } }
    }
}
