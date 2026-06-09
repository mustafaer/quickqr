package net.mustafaer.quickqr

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.mustafaer.quickqr.ui.screens.MainContainer
import net.mustafaer.quickqr.ui.screens.OnboardingScreen
import net.mustafaer.quickqr.ui.theme.QuickQrTheme
import net.mustafaer.quickqr.ui.viewmodel.AppViewModel

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the splash screen
        installSplashScreen()

        super.onCreate(savedInstanceState)

        // Enable Edge-to-Edge interface support
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

        // Initialize ViewModel using the default Factory
        val viewModel = ViewModelProvider(this)[AppViewModel::class.java]

        setContent {
            QuickQrTheme {
                val isOnboardingComplete = viewModel.onboardingComplete.collectAsStateWithLifecycle().value

                if (!isOnboardingComplete) {
                    OnboardingScreen(
                        onComplete = { viewModel.setOnboardingComplete(true) }
                    )
                } else {
                    MainContainer(viewModel = viewModel)
                }
            }
        }
    }
}
