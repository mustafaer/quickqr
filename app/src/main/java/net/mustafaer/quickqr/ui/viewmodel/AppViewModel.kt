package net.mustafaer.quickqr.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.FileProvider
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.mustafaer.quickqr.data.AppDatabase
import net.mustafaer.quickqr.data.ScanEntity
import net.mustafaer.quickqr.data.SettingsDataStore
import net.mustafaer.quickqr.utils.HapticHelper
import net.mustafaer.quickqr.utils.QrGenerator
import net.mustafaer.quickqr.utils.ScanResultType
import net.mustafaer.quickqr.utils.TypeDetector
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val scanDao = database.scanDao()
    private val settingsDataStore = SettingsDataStore(application)

    // --- Settings flows ---
    val hapticEnabled = settingsDataStore.hapticEnabledFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), true
    )
    val continuousMode = settingsDataStore.continuousModeFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )
    val onboardingComplete = settingsDataStore.onboardingCompleteFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )
    val language = settingsDataStore.languageFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "en"
    )

    // --- History search query flow ---
    private val _historySearchQuery = MutableStateFlow("")
    val historySearchQuery = _historySearchQuery.asStateFlow()

    // --- History items flow ---
    @OptIn(ExperimentalCoroutinesApi::class)
    val historyItems: StateFlow<List<ScanEntity>> = _historySearchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                scanDao.getAllScans()
            } else {
                scanDao.searchScans("%$query%")
            }
        }.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )

    // --- Active scanner / scan result state ---
    private val _scannedResult = MutableStateFlow<String?>(null)
    val scannedResult = _scannedResult.asStateFlow()

    private val _scannedType = MutableStateFlow<ScanResultType>(ScanResultType.TEXT)
    val scannedType = _scannedType.asStateFlow()

    private val _isScannerPaused = MutableStateFlow(false)
    val isScannerPaused = _isScannerPaused.asStateFlow()

    // --- QR Code Generator state ---
    private val _generatorText = MutableStateFlow("")
    val generatorText = _generatorText.asStateFlow()

    private val _generatedQrBitmap = MutableStateFlow<Bitmap?>(null)
    val generatedQrBitmap = _generatedQrBitmap.asStateFlow()

    init {
        // Apply saved language on startup
        viewModelScope.launch {
            language.collect { lang ->
                applyLocale(lang)
            }
        }
    }

    // --- Scan handling ---
    fun onCodeScanned(text: String) {
        if (_isScannerPaused.value) return
        val type = TypeDetector.detect(text)
        _scannedResult.value = text
        _scannedType.value = type
        _isScannerPaused.value = true

        // Add to Room
        viewModelScope.launch(Dispatchers.IO) {
            val entity = ScanEntity(text = text, type = type.typeStr)
            scanDao.insertScan(entity)
            // Limit to last 100 scans or keep unlimited? Let's cap history at 100 scans for performance.
            scanDao.capHistorySize(100)
        }

        // Haptic feedback
        if (hapticEnabled.value) {
            HapticHelper.triggerHapticFeedback(getApplication())
        }

        // Continuous mode: automatically resume after 3 seconds
        if (continuousMode.value) {
            viewModelScope.launch {
                kotlinx.coroutines.delay(3000)
                resumeScanner()
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

    // --- Settings updates ---
    fun setHapticEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setHapticEnabled(enabled)
        }
    }

    fun setContinuousMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setContinuousMode(enabled)
        }
    }

    fun setOnboardingComplete(complete: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setOnboardingComplete(complete)
        }
    }

    fun setLanguage(langCode: String) {
        viewModelScope.launch {
            settingsDataStore.setLanguage(langCode)
            applyLocale(langCode)
        }
    }

    private fun applyLocale(langCode: String) {
        val appLocale = LocaleListCompat.forLanguageTags(langCode)
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

    // --- QR Generator logic ---
    fun updateGeneratorText(text: String) {
        _generatorText.value = text
        if (text.isBlank()) {
            _generatedQrBitmap.value = null
        }
    }

    fun generateQrCode() {
        val text = _generatorText.value
        if (text.isNotBlank()) {
            viewModelScope.launch(Dispatchers.Default) {
                val bitmap = QrGenerator.generate(text, 512)
                _generatedQrBitmap.value = bitmap
            }
        }
    }

    // --- Gallery Scan logic ---
    fun scanFromGallery(uri: Uri, onSuccess: (String) -> Unit, onFailure: () -> Unit) {
        val context = getApplication<Application>()
        try {
            val image = InputImage.fromFilePath(context, uri)
            val scanner = BarcodeScanning.getClient()
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    val barcode = barcodes.firstOrNull()
                    val rawValue = barcode?.rawValue
                    if (rawValue != null) {
                        onCodeScanned(rawValue)
                        onSuccess(rawValue)
                    } else {
                        onFailure()
                    }
                }
                .addOnFailureListener {
                    onFailure()
                }
        } catch (e: Exception) {
            e.printStackTrace()
            onFailure()
        }
    }

    // --- History operations ---
    fun updateHistorySearchQuery(query: String) {
        _historySearchQuery.value = query
    }

    fun deleteHistoryItem(item: ScanEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            scanDao.deleteScan(item)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            scanDao.clearAllScans()
        }
    }

    // --- History Export files ---
    suspend fun getExportFileUri(format: String): Uri? = withContext(Dispatchers.IO) {
        val scans = historyItems.value
        if (scans.isEmpty()) return@withContext null

        val context = getApplication<Application>()
        val filename = "quickqr_history_${System.currentTimeMillis()}.$format"
        val cacheFile = File(context.cacheDir, filename)

        try {
            FileOutputStream(cacheFile).use { fos ->
                if (format == "csv") {
                    fos.write("id,text,type,date\n".toByteArray())
                    scans.forEach { scan ->
                        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(scan.timestamp))
                        val cleanText = scan.text.replace("\"", "\"\"")
                        fos.write("${scan.id},\"$cleanText\",${scan.type},\"$dateStr\"\n".toByteArray())
                    }
                } else {
                    // Simple JSON manual builder to avoid GSON dependency
                    val sb = java.lang.StringBuilder()
                    sb.append("[\n")
                    scans.forEachIndexed { index, scan ->
                        val cleanText = scan.text
                            .replace("\\", "\\\\")
                            .replace("\"", "\\\"")
                            .replace("\n", "\\n")
                            .replace("\r", "\\r")
                        sb.append("  {\n")
                        sb.append("    \"id\": ${scan.id},\n")
                        sb.append("    \"text\": \"$cleanText\",\n")
                        sb.append("    \"type\": \"${scan.type}\",\n")
                        sb.append("    \"timestamp\": ${scan.timestamp}\n")
                        sb.append("  }")
                        if (index < scans.size - 1) sb.append(",")
                        sb.append("\n")
                    }
                    sb.append("]")
                    fos.write(sb.toString().toByteArray())
                }
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // --- Share / Save generated QR Bitmap ---
    suspend fun getQrCodeShareUri(): Uri? = withContext(Dispatchers.IO) {
        val bitmap = _generatedQrBitmap.value ?: return@withContext null
        val context = getApplication<Application>()
        val cacheFile = File(context.cacheDir, "quickqr_generated_${System.currentTimeMillis()}.png")

        try {
            FileOutputStream(cacheFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun saveQrCodeToGallery(): Uri? = withContext(Dispatchers.IO) {
        val bitmap = _generatedQrBitmap.value ?: return@withContext null
        val context = getApplication<Application>()
        val filename = "quickqr_${System.currentTimeMillis()}.png"

        try {
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/QuickQR")
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri).use { outputStream ->
                    if (outputStream != null) {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    }
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
                uri
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
