package net.mustafaer.quickqr

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class QuickQrApp : Application() {

    lateinit var container: AppContainer
        private set

    /** Work that should outlive any one screen, such as cache cleanup. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // AppCompat does not persist the night mode across process restarts. Set
        // it here, before the first Activity exists, so a user who chose Light or
        // Dark against the system setting no longer pays an Activity recreate —
        // and a frame in the wrong theme — on every cold start. One small
        // preferences file read; the splash screen covers it.
        val themeMode = runBlocking { container.settings.themeMode.first() }
        AppCompatDelegate.setDefaultNightMode(themeMode.nightMode)

        applicationScope.launch(Dispatchers.IO) { container.media.pruneSharedFiles() }
    }
}
