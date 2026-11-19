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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.mohtech.app.ai.audio.VoiceEngine
import org.mohtech.app.ai.audio.createVoiceEngine
import org.mohtech.app.ai.whisper.createLocalSpeechTranscriber
import org.mohtech.app.ai.whisper.createRawAudioRecorder
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
 * Survives configuration change / rotation without reset loop.
 */
@Composable
fun App(
    voiceEngine: VoiceEngine? = null,
    onRequestMicPermission: (() -> Unit)? = null
) {
    val preferences = remember { createAppPreferences() }
    val navigationState = rememberSaveable(saver = AppNavigationState.Saver) {
        AppNavigationState()
    }

    val viewModel: ExpenseViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val storage = createPlatformExpenseStorage()
                val repository = LocalExpenseRepository(storage, preferences)
                val recorder = createRawAudioRecorder()
                val transcriber = createLocalSpeechTranscriber()
                val engine = voiceEngine ?: createVoiceEngine()
                ExpenseViewModel(
                    repository = repository,
                    preferences = preferences,
                    voiceEngine = engine,
                    transcriber = transcriber,
                    audioRecorder = recorder
                )
            }
        }
    )

    val state by viewModel.uiState.collectAsState()

    RaqeemTheme {
        AnimatedContent(
            targetState = navigationState.currentDestination,
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
                            navigationState.onSplashFinished(preferences, state.isOnboardingDone)
                        }
                    )
                }
                AppDestination.ONBOARDING -> {
                    OnboardingScreen(
                        onComplete = {
                            viewModel.handleIntent(ExpenseIntent.CompleteOnboarding)
                            navigationState.onOnboardingComplete(preferences)
                        },
                        onRequestMicPermission = onRequestMicPermission,
                        onPreferencesConfigured = { currency, budget ->
                            preferences.setPreferredCurrency(currency)
                            preferences.setWeeklyBudget(budget)
                            viewModel.handleIntent(ExpenseIntent.UpdateWeeklyBudget(budget))
                        },
                        initialCurrency = preferences.getPreferredCurrency(),
                        initialBudget = preferences.getWeeklyBudget()
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
