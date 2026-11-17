package org.mohtech.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.mohtech.app.ai.audio.VoiceEngine
import org.mohtech.app.ai.audio.createVoiceEngine
import org.mohtech.app.core.theme.RaqeemTheme
import org.mohtech.app.data.repository.LocalExpenseRepository
import org.mohtech.app.data.storage.createAppPreferences
import org.mohtech.app.data.storage.createPlatformExpenseStorage
import org.mohtech.app.feature.dashboard.ExpenseIntent
import org.mohtech.app.feature.dashboard.ExpenseViewModel
import org.mohtech.app.feature.dashboard.ui.DashboardScreen
import org.mohtech.app.feature.onboarding.OnboardingScreen
import org.mohtech.app.feature.splash.SplashScreen

enum class AppDestination {
    SPLASH,
    ONBOARDING,
    DASHBOARD
}

/**
 * Raqeem (رقيم) Main Application Entry Composable
 *
 * Smooth animated transitions across lifecycle:
 * Splash -> Onboarding -> Dashboard
 * Synchronous onboarding verification to eliminate flash-of-unstyled-content (FOUC).
 */
@Composable
fun App(
    voiceEngine: VoiceEngine? = remember { createVoiceEngine() },
    onRequestMicPermission: (() -> Unit)? = null
) {
    val preferences = remember { createAppPreferences() }
    val storage = remember { createPlatformExpenseStorage() }
    val repository = remember { LocalExpenseRepository(storage, preferences) }
    val viewModel = remember { ExpenseViewModel(repository, preferences, voiceEngine = voiceEngine) }

    DisposableEffect(voiceEngine) {
        onDispose {
            voiceEngine?.release()
        }
    }

    val state by viewModel.uiState.collectAsState()
    var currentDestination by remember { mutableStateOf(AppDestination.SPLASH) }

    RaqeemTheme {
        AnimatedContent(
            targetState = currentDestination,
            transitionSpec = {
                when {
                    initialState == AppDestination.SPLASH -> {
                        (fadeIn(animationSpec = tween(400, easing = FastOutSlowInEasing)) +
                            scaleIn(initialScale = 0.96f, animationSpec = tween(400, easing = FastOutSlowInEasing)))
                            .togetherWith(fadeOut(animationSpec = tween(250)))
                    }
                    initialState == AppDestination.ONBOARDING && targetState == AppDestination.DASHBOARD -> {
                        (fadeIn(animationSpec = tween(400, easing = FastOutSlowInEasing)) +
                            slideInHorizontally(animationSpec = tween(400, easing = FastOutSlowInEasing)) { -it / 5 })
                            .togetherWith(
                                fadeOut(animationSpec = tween(280)) +
                                slideOutHorizontally(animationSpec = tween(350)) { it / 5 }
                            )
                    }
                    else -> {
                        fadeIn(animationSpec = tween(350)) togetherWith fadeOut(animationSpec = tween(250))
                    }
                }
            },
            label = "AppNavigationTransition"
        ) { destination ->
            when (destination) {
                AppDestination.SPLASH -> {
                    SplashScreen(
                        onSplashFinished = {
                            if (preferences.isOnboardingCompleted() || state.isOnboardingDone) {
                                currentDestination = AppDestination.DASHBOARD
                            } else {
                                currentDestination = AppDestination.ONBOARDING
                            }
                        }
                    )
                }
                AppDestination.ONBOARDING -> {
                    OnboardingScreen(
                        onComplete = {
                            viewModel.handleIntent(ExpenseIntent.CompleteOnboarding)
                            currentDestination = AppDestination.DASHBOARD
                        }
                    )
                }
                AppDestination.DASHBOARD -> {
                    DashboardScreen(
                        viewModel = viewModel,
                        onRequestMicPermission = onRequestMicPermission
                    )
                }
            }
        }
    }
}
