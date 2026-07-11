package net.mustafaer.quickqr.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
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
    private var lastScannedText: String? = null
    private var lastScannedTime: Long = 0
    private var qrGenerateJob: Job? = null

    // --- Settings flows ---
    val hapticEnabled = settingsDataStore.hapticEnabledFlow.stateIn(
        viewModelScope, SharingStarted.Eagerly, true
    )
    val continuousMode = settingsDataStore.continuousModeFlow.stateIn(
        viewModelScope, SharingStarted.Eagerly, false
    )
    val onboardingComplete = settingsDataStore.onboardingCompleteFlow.stateIn(
        viewModelScope, SharingStarted.Eagerly, null
    )
    val language = settingsDataStore.languageFlow.stateIn(
        viewModelScope, SharingStarted.Eagerly, null
    )

    // --- History search query flow ---
    private val _historySearchQuery = MutableStateFlow("")
    val historySearchQuery = _historySearchQuery.asStateFlow()

    // --- History items flow ---
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val historyItems: StateFlow<List<ScanEntity>> = _historySearchQuery
        .debounce { query -> if (query.isBlank()) 0L else 300L }
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
                if (lang != null) {
                    applyLocale(lang)
                }
            }
        }
    }

    // --- Scan handling ---
    fun onCodeScanned(text: String, force: Boolean = false) {
        viewModelScope.launch {
            if (_isScannerPaused.value && !force) return@launch

            val currentTime = System.currentTimeMillis()
            if (continuousMode.value) {
                // Prevent duplicate scans within 2 seconds
                if (text == lastScannedText && (currentTime - lastScannedTime) < 2000) {
                    return@launch
                }
                lastScannedText = text
                lastScannedTime = currentTime

                val type = TypeDetector.detect(text)

                // Add to Room
                viewModelScope.launch(Dispatchers.IO) {
                    val entity = ScanEntity(text = text, type = type.typeStr)
                    scanDao.insertAndCap(entity, 100)
                }

                // Haptic feedback
                if (hapticEnabled.value) {
                    HapticHelper.triggerHapticFeedback(getApplication())
                }

                // Show a feedback toast
                val messageText = if (text.length > 30) text.take(30) + "..." else text
                Toast.makeText(getApplication(), messageText, Toast.LENGTH_SHORT).show()
            } else {
                _isScannerPaused.value = true
                val type = TypeDetector.detect(text)
                _scannedResult.value = text
                _scannedType.value = type

                // Add to Room
                viewModelScope.launch(Dispatchers.IO) {
                    val entity = ScanEntity(text = text, type = type.typeStr)
                    scanDao.insertAndCap(entity, 100)
                }

                // Haptic feedback
                if (hapticEnabled.value) {
                    HapticHelper.triggerHapticFeedback(getApplication())
                }
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
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        val targetLocale = LocaleListCompat.forLanguageTags(langCode)
        if (currentLocales.toLanguageTags() != targetLocale.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(targetLocale)
        }
    }

    // --- QR Generator logic ---
    fun updateGeneratorText(text: String) {
        _generatorText.value = text
        qrGenerateJob?.cancel()
        if (text.isBlank()) {
            _generatedQrBitmap.value = null
        } else {
            qrGenerateJob = viewModelScope.launch(Dispatchers.Default) {
                delay(250)
                val bitmap = QrGenerator.generate(text, 512)
                _generatedQrBitmap.value = bitmap
            }
        }
    }

    fun generateQrCode() {
        val text = _generatorText.value
        if (text.isNotBlank()) {
            qrGenerateJob?.cancel()
            qrGenerateJob = viewModelScope.launch(Dispatchers.Default) {
                val bitmap = QrGenerator.generate(text, 512)
                _generatedQrBitmap.value = bitmap
            }
        }
    }

    // --- Gallery Scan logic ---
    fun scanFromGallery(uri: Uri, onSuccess: (String) -> Unit, onFailure: () -> Unit) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val image = InputImage.fromFilePath(context, uri)
                val scanner = BarcodeScanning.getClient()
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        val barcode = barcodes.firstOrNull()
                        val rawValue = barcode?.rawValue
                        if (rawValue != null) {
                            onCodeScanned(rawValue, force = true)
                            viewModelScope.launch(Dispatchers.Main) {
                                onSuccess(rawValue)
                            }
                        } else {
                            viewModelScope.launch(Dispatchers.Main) {
                                onFailure()
                            }
                        }
                    }
                    .addOnFailureListener {
                        viewModelScope.launch(Dispatchers.Main) {
                            onFailure()
                        }
                    }
                    .addOnCompleteListener {
                        scanner.close()
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                viewModelScope.launch(Dispatchers.Main) {
                    onFailure()
                }
            }
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
        val scans = scanDao.getAllScans().first()
        if (scans.isEmpty()) return@withContext null

        val context = getApplication<Application>()
        try {
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("quickqr_history_")) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val filename = "quickqr_history_${System.currentTimeMillis()}.$format"
        val cacheFile = File(context.cacheDir, filename)

        try {
            FileOutputStream(cacheFile).use { fos ->
                if (format == "csv") {
                    // Write UTF-8 BOM to support Excel showing non-ASCII characters correctly
                    fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                    fos.write("id,text,type,date\n".toByteArray())
                    scans.forEach { scan ->
                        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(scan.timestamp))
                        val cleanText = scan.text.replace("\"", "\"\"")
                        fos.write("${scan.id},\"$cleanText\",${scan.type},\"$dateStr\"\n".toByteArray())
                    }
                } else {
                    val jsonArray = org.json.JSONArray()
                    scans.forEach { scan ->
                        val jsonObject = org.json.JSONObject().apply {
                            put("id", scan.id)
                            put("text", scan.text)
                            put("type", scan.type)
                            put("timestamp", scan.timestamp)
                        }
                        jsonArray.put(jsonObject)
                    }
                    fos.write(jsonArray.toString(2).toByteArray())
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
        try {
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("quickqr_generated_")) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

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
                try {
                    resolver.openOutputStream(uri).use { outputStream ->
                        if (outputStream != null) {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                        } else {
                            throw java.io.IOException("Failed to open output stream")
                        }
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                    }
                    uri
                } catch (writeException: Exception) {
                    resolver.delete(uri, null, null)
                    throw writeException
                }
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
