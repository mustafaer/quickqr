package net.mustafaer.quickqr

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.mustafaer.quickqr.ui.screens.MainContainer
import net.mustafaer.quickqr.ui.theme.QuickQrTheme
import net.mustafaer.quickqr.ui.viewmodel.SettingsViewModel

class MainActivity : AppCompatActivity() {

    private val settingsViewModel: SettingsViewModel by viewModels { SettingsViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )

        // Hold the splash until settings have loaded, so the first frame is
        // already in the right colours — including wallpaper colours.
        splashScreen.setKeepOnScreenCondition { settingsViewModel.state.value == null }

        setContent {
            val settings by settingsViewModel.state.collectAsStateWithLifecycle()
            settings?.let { loaded ->
                QuickQrTheme(dynamicColor = loaded.dynamicColor) {
                    MainContainer(settings = loaded, settingsViewModel = settingsViewModel)
                }
            }
        }
    }
}
