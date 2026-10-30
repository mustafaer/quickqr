package net.mustafaer.quickqr

import android.app.Application
import net.mustafaer.quickqr.data.AppDatabase
import net.mustafaer.quickqr.data.ImageCodeReader
import net.mustafaer.quickqr.data.MediaExporter
import net.mustafaer.quickqr.data.MlKitImageCodeReader
import net.mustafaer.quickqr.data.RoomScanRepository
import net.mustafaer.quickqr.data.ScanRepository
import net.mustafaer.quickqr.data.SettingsDataStore
import net.mustafaer.quickqr.data.SettingsRepository
import net.mustafaer.quickqr.utils.HapticHelper

/** Feedback for a successful scan. An interface so ViewModel tests need no vibrator. */
fun interface ScanFeedback {
    fun onScanned()
}

/**
 * Manual dependency injection: one instance of each long-lived collaborator,
 * created on first use. At this size a DI framework would cost more in build time
 * and indirection than it saves.
 */
class AppContainer(application: Application) {
    val scans: ScanRepository by lazy {
        RoomScanRepository(AppDatabase.getDatabase(application).scanDao())
    }
    val settings: SettingsRepository by lazy { SettingsDataStore(application) }
    val media: MediaExporter by lazy { MediaExporter(application) }
    val imageReader: ImageCodeReader by lazy { MlKitImageCodeReader(application) }
    val scanFeedback = ScanFeedback { HapticHelper.triggerHapticFeedback(application) }
}
