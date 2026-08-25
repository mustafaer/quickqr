package net.mustafaer.quickqr.ui.viewmodel

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.FileProvider
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.mustafaer.quickqr.data.AppDatabase
import net.mustafaer.quickqr.data.AppLanguage
import net.mustafaer.quickqr.data.ScanEntity
import net.mustafaer.quickqr.data.SettingsDataStore
import net.mustafaer.quickqr.data.ThemeMode
import net.mustafaer.quickqr.utils.HapticHelper
import net.mustafaer.quickqr.utils.HistoryExporter
import net.mustafaer.quickqr.utils.HistoryFilter
import net.mustafaer.quickqr.utils.QrGenerator
import net.mustafaer.quickqr.utils.QrPayloadDraft
import net.mustafaer.quickqr.utils.QrResult
import net.mustafaer.quickqr.utils.ScanResultType
import net.mustafaer.quickqr.utils.TypeDetector
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.coroutineContext

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        /** A code identical to the previous one is ignored for this long in continuous mode. */
        const val DUPLICATE_SCAN_WINDOW_MS = 2_000L

        /** Cached export/share files older than this are cleaned up on the next export. */
        const val CACHE_FILE_TTL_MS = 3_600_000L

        const val SEARCH_DEBOUNCE_MS = 250L
        const val GENERATE_DEBOUNCE_MS = 250L
        const val QR_SIZE_PX = 512
    }

    private val database = AppDatabase.getDatabase(application)
    private val scanDao = database.scanDao()
    private val settingsDataStore = SettingsDataStore(application)
    private var lastScannedText: String? = null
    private var lastScannedTime: Long = 0
    private var qrGenerateJob: Job? = null

    // --- Settings flows -------------------------------------------------------

    val hapticEnabled = settingsDataStore.hapticEnabledFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val continuousMode = settingsDataStore.continuousModeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Null while still loading — the splash screen stays up until it resolves. */
    val onboardingComplete = settingsDataStore.onboardingCompleteFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The saved language, or the closest match to the device locale until one is saved. */
    val language: StateFlow<AppLanguage> = settingsDataStore.languageFlow
        .map { code -> code?.let(AppLanguage::fromCode) ?: AppLanguage.matchingSystemLocale() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppLanguage.matchingSystemLocale())

    val themeMode = settingsDataStore.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.DEFAULT)

    val dynamicColor = settingsDataStore.dynamicColorFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // --- History --------------------------------------------------------------

    private val _historySearchQuery = MutableStateFlow("")
    val historySearchQuery = _historySearchQuery.asStateFlow()

    @OptIn(FlowPreview::class)
    val historyItems: StateFlow<List<ScanEntity>> = combine(
        scanDao.getAllScans(),
        _historySearchQuery.debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
    ) { scans, query -> HistoryFilter.apply(scans, query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // --- Scanner / scan result ------------------------------------------------

    private val _scannedResult = MutableStateFlow<String?>(null)
    val scannedResult = _scannedResult.asStateFlow()

    private val _scannedType = MutableStateFlow(ScanResultType.TEXT)
    val scannedType = _scannedType.asStateFlow()

    private val _isScannerPaused = MutableStateFlow(false)
    val isScannerPaused = _isScannerPaused.asStateFlow()

    // --- Generator ------------------------------------------------------------

    private val _generatorDraft = MutableStateFlow(QrPayloadDraft())
    val generatorDraft = _generatorDraft.asStateFlow()

    private val _qrResult = MutableStateFlow<QrResult>(QrResult.Empty)
    val qrResult = _qrResult.asStateFlow()

    private val currentBitmap: Bitmap?
        get() = (_qrResult.value as? QrResult.Success)?.bitmap

    init {
        // Re-apply the saved language and theme whenever they change, including on
        // the first emission after process start.
        viewModelScope.launch {
            settingsDataStore.languageFlow.collect { code ->
                if (code != null) applyLocale(code)
            }
        }
        viewModelScope.launch {
            settingsDataStore.themeModeFlow.collect { mode ->
                if (AppCompatDelegate.getDefaultNightMode() != mode.nightMode) {
                    AppCompatDelegate.setDefaultNightMode(mode.nightMode)
                }
            }
        }
    }

    // --- Scan handling --------------------------------------------------------

    /**
     * The most recent continuous-mode scan, surfaced so the screen can show a
     * localised snackbar. Carrying the timestamp makes each scan a distinct value
     * even when the same code is read twice.
     */
    data class ContinuousScan(val text: String, val at: Long)

    private val _lastContinuousScan = MutableStateFlow<ContinuousScan?>(null)
    val lastContinuousScan = _lastContinuousScan.asStateFlow()

    fun consumeContinuousScan() {
        _lastContinuousScan.value = null
    }

    /**
     * @param force set for a gallery pick, which the user asked for explicitly and
     *   which therefore always opens the result sheet — even in continuous mode,
     *   where a live camera scan would only flash a toast.
     */
    fun onCodeScanned(text: String, force: Boolean = false) {
        viewModelScope.launch {
            if (_isScannerPaused.value && !force) return@launch

            val type = TypeDetector.detect(text)
            val now = System.currentTimeMillis()

            if (continuousMode.value && !force) {
                if (text == lastScannedText && (now - lastScannedTime) < DUPLICATE_SCAN_WINDOW_MS) {
                    return@launch
                }
                lastScannedText = text
                lastScannedTime = now
                _lastContinuousScan.value = ContinuousScan(text, now)
            } else {
                _isScannerPaused.value = true
                _scannedResult.value = text
                _scannedType.value = type
            }

            recordScan(text, type)

            if (hapticEnabled.value) {
                HapticHelper.triggerHapticFeedback(getApplication())
            }
        }
    }

    private fun recordScan(text: String, type: ScanResultType) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                scanDao.insertAndCap(
                    ScanEntity(text = text, type = type.typeStr),
                    AppDatabase.HISTORY_LIMIT
                )
            }
        }
    }

    fun resumeScanner() {
        _scannedResult.value = null
        _isScannerPaused.value = false
    }

    fun selectHistoryItem(item: ScanEntity) {
        _scannedResult.value = item.text
        _scannedType.value = ScanResultType.fromString(item.type)
        _isScannerPaused.value = true
    }

    // --- Settings updates -----------------------------------------------------

    fun setHapticEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setHapticEnabled(enabled) }
    }

    fun setContinuousMode(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setContinuousMode(enabled) }
    }

    fun setOnboardingComplete(complete: Boolean) {
        viewModelScope.launch { settingsDataStore.setOnboardingComplete(complete) }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            settingsDataStore.setLanguage(language.code)
            applyLocale(language.code)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsDataStore.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setDynamicColor(enabled) }
    }

    private fun applyLocale(langCode: String) {
        val current = AppCompatDelegate.getApplicationLocales()
        val target = LocaleListCompat.forLanguageTags(langCode)
        if (current.toLanguageTags() != target.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(target)
        }
    }

    // --- Generator ------------------------------------------------------------

    fun updateGeneratorDraft(draft: QrPayloadDraft) {
        _generatorDraft.value = draft
        scheduleGenerate(debounce = true)
    }

    fun generateQrCode() {
        scheduleGenerate(debounce = false)
    }

    fun clearGenerator() {
        _generatorDraft.value = QrPayloadDraft(type = _generatorDraft.value.type)
        qrGenerateJob?.cancel()
        _qrResult.value = QrResult.Empty
    }

    private fun scheduleGenerate(debounce: Boolean) {
        qrGenerateJob?.cancel()
        val payload = _generatorDraft.value.build()
        if (payload.isBlank()) {
            _qrResult.value = QrResult.Empty
            return
        }
        qrGenerateJob = viewModelScope.launch(Dispatchers.Default) {
            if (debounce) delay(GENERATE_DEBOUNCE_MS)
            val result = QrGenerator.generate(payload, QR_SIZE_PX)
            // Without this check a job cancelled while ZXing was encoding would
            // still publish its now-stale bitmap over the newer one.
            coroutineContext.ensureActive()
            _qrResult.value = result
        }
    }

    // --- Gallery scan ---------------------------------------------------------

    fun scanFromGallery(uri: Uri, onFailure: () -> Unit) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            var scanner: BarcodeScanner? = null
            try {
                val image = InputImage.fromFilePath(context, uri)
                scanner = BarcodeScanning.getClient()
                val client = scanner
                client.process(image)
                    .addOnSuccessListener { barcodes ->
                        val rawValue = barcodes.firstOrNull()?.rawValue
                        if (rawValue != null) {
                            onCodeScanned(rawValue, force = true)
                        } else {
                            onFailure()
                        }
                    }
                    .addOnFailureListener { onFailure() }
                    .addOnCompleteListener { runCatching { client.close() } }
            } catch (_: Exception) {
                runCatching { scanner?.close() }
                withContext(Dispatchers.Main) { onFailure() }
            }
        }
    }

    // --- History operations ---------------------------------------------------

    fun updateHistorySearchQuery(query: String) {
        _historySearchQuery.value = query
    }

    fun deleteHistoryItem(item: ScanEntity) {
        viewModelScope.launch(Dispatchers.IO) { scanDao.deleteScan(item) }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) { scanDao.clearAllScans() }
    }

    /**
     * Writes the history the user is currently looking at — search filter
     * included — to a cache file and returns a shareable URI, or null if there is
     * nothing to export or the write failed.
     */
    suspend fun getExportFileUri(format: String): Uri? = withContext(Dispatchers.IO) {
        val scans = historyItems.value
        if (scans.isEmpty()) return@withContext null

        val context = getApplication<Application>()
        pruneCacheFiles("quickqr_history_")

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val body = if (format == "csv") {
            HistoryExporter.toCsv(scans) { dateFormat.format(Date(it)) }
        } else {
            HistoryExporter.toJson(scans)
        }

        val cacheFile = File(context.cacheDir, "quickqr_history_${System.currentTimeMillis()}.$format")
        try {
            FileOutputStream(cacheFile).use { out ->
                // Excel needs the BOM to read non-ASCII CSV as UTF-8.
                if (format == "csv") out.write(HistoryExporter.UTF8_BOM)
                out.write(body.toByteArray(Charsets.UTF_8))
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)
        } catch (_: Exception) {
            cacheFile.delete()
            null
        }
    }

    // --- Share / save generated QR --------------------------------------------

    suspend fun getQrCodeShareUri(): Uri? = withContext(Dispatchers.IO) {
        val bitmap = currentBitmap ?: return@withContext null
        val context = getApplication<Application>()
        pruneCacheFiles("quickqr_generated_")

        val cacheFile = File(context.cacheDir, "quickqr_generated_${System.currentTimeMillis()}.png")
        try {
            FileOutputStream(cacheFile).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)
        } catch (_: Exception) {
            cacheFile.delete()
            null
        }
    }

    suspend fun saveQrCodeToGallery(): Uri? = withContext(Dispatchers.IO) {
        val bitmap = currentBitmap ?: return@withContext null
        val context = getApplication<Application>()
        val filename = "quickqr_${System.currentTimeMillis()}.png"

        try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/QuickQR"
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext null

            try {
                resolver.openOutputStream(uri).use { out ->
                    if (out == null) throw IOException("Could not open the gallery entry for writing")
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                uri
            } catch (writeException: Exception) {
                // Never leave a half-written placeholder behind in the gallery.
                resolver.delete(uri, null, null)
                throw writeException
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun pruneCacheFiles(prefix: String) {
        val now = System.currentTimeMillis()
        runCatching {
            getApplication<Application>().cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith(prefix) && (now - file.lastModified()) > CACHE_FILE_TTL_MS) {
                    file.delete()
                }
            }
        }
    }
}
