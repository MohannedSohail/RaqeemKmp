package org.mohtech.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.setValue
import org.mohtech.app.data.storage.AppPreferences

/**
 * AppNavigationState:
 * Encapsulates top-level destination state and splash lifecycle survival.
 * Survives activity recreation / device rotation via [Saver].
 */
class AppNavigationState(
    initialDestination: AppDestination = AppDestination.SPLASH,
    initialSplashShown: Boolean = false
) {
    var currentDestination: AppDestination by mutableStateOf(initialDestination)
        private set

    var splashShown: Boolean by mutableStateOf(initialSplashShown)
        private set

    fun onSplashFinished(preferences: AppPreferences, isStateOnboardingDone: Boolean = false) {
        splashShown = true
        currentDestination = if (preferences.isOnboardingCompleted() || isStateOnboardingDone) {
            AppDestination.DASHBOARD
        } else {
            AppDestination.ONBOARDING
        }
    }

    fun onOnboardingComplete(preferences: AppPreferences) {
        preferences.setOnboardingCompleted(true)
        currentDestination = AppDestination.DASHBOARD
    }

    companion object {
        val Saver: Saver<AppNavigationState, Any> = mapSaver(
            save = { state ->
                mapOf(
                    "destination" to state.currentDestination.name,
                    "splashShown" to state.splashShown
                )
            },
            restore = { map ->
                val destName = map["destination"] as? String
                val dest = destName?.let { runCatching { AppDestination.valueOf(it) }.getOrNull() } ?: AppDestination.SPLASH
                val splash = (map["splashShown"] as? Boolean) ?: false
                AppNavigationState(initialDestination = dest, initialSplashShown = splash)
            }
        )
    }
}

