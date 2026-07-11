package net.mustafaer.quickqr

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
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
        val splashScreen = installSplashScreen()

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

        // Keep the splash screen visible until the onboarding state is loaded from DataStore
        splashScreen.setKeepOnScreenCondition {
            viewModel.onboardingComplete.value == null
        }

        setContent {
            QuickQrTheme {
                val isOnboardingComplete = viewModel.onboardingComplete.collectAsStateWithLifecycle().value

                AnimatedContent(
                    targetState = isOnboardingComplete,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(500)) togetherWith fadeOut(animationSpec = tween(500))
                    },
                    label = "appTransition"
                ) { state ->
                    when (state) {
                        null -> {
                            // Render a blank placeholder while loading (splash screen remains visible)
                            Box(modifier = Modifier.fillMaxSize())
                        }
                        false -> {
                            OnboardingScreen(
                                onComplete = { viewModel.setOnboardingComplete(true) }
                            )
                        }
                        true -> {
                            MainContainer(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}
