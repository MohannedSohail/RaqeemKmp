package org.mohtech.app.feature.onboarding

import org.mohtech.app.AppDestination
import org.mohtech.app.AppNavigationState
import org.mohtech.app.data.storage.AppPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppNavigationStateTest {

    private class TestPreferences(
        private var onboardingCompleted: Boolean = false
    ) : AppPreferences {
        override fun isOnboardingCompleted(): Boolean = onboardingCompleted
        override fun setOnboardingCompleted(completed: Boolean) { onboardingCompleted = completed }
        override fun getPreferredCurrency(): String = "₪"
        override fun setPreferredCurrency(currency: String) {}
        override fun getWeeklyBudget(): Double = 2500.0
        override fun setWeeklyBudget(budget: Double) {}
    }

    @Test
    fun testColdLaunchStartsAtSplash() {
        val state = AppNavigationState()
        assertEquals(AppDestination.SPLASH, state.currentDestination)
        assertFalse(state.splashShown)
    }

    @Test
    fun testSplashFinishesToOnboardingWhenNotCompleted() {
        val prefs = TestPreferences(onboardingCompleted = false)
        val state = AppNavigationState()

        state.onSplashFinished(prefs)

        assertEquals(AppDestination.ONBOARDING, state.currentDestination)
        assertTrue(state.splashShown)
    }

    @Test
    fun testSplashFinishesToDashboardWhenCompleted() {
        val prefs = TestPreferences(onboardingCompleted = true)
        val state = AppNavigationState()

        state.onSplashFinished(prefs)

        assertEquals(AppDestination.DASHBOARD, state.currentDestination)
        assertTrue(state.splashShown)
    }

    @Test
    fun testOnboardingCompleteTransitionsToDashboardAndSavesPref() {
        val prefs = TestPreferences(onboardingCompleted = false)
        val state = AppNavigationState(initialDestination = AppDestination.ONBOARDING, initialSplashShown = true)

        state.onOnboardingComplete(prefs)

        assertEquals(AppDestination.DASHBOARD, state.currentDestination)
        assertTrue(prefs.isOnboardingCompleted())
    }

    @Test
    fun testSaverRestoresNavigationStateAcrossRecreation() {
        val original = AppNavigationState(
            initialDestination = AppDestination.DASHBOARD,
            initialSplashShown = true
        )

        // Save
        val saver = AppNavigationState.Saver
        var savedValue: Any? = null
        with(saver) {
            val scope = androidx.compose.runtime.saveable.SaverScope { true }
            savedValue = scope.save(original)
        }

        // Restore
        val restored = saver.restore(savedValue!!) as? AppNavigationState
        assertEquals(AppDestination.DASHBOARD, restored?.currentDestination)
        assertEquals(true, restored?.splashShown)
    }
}

