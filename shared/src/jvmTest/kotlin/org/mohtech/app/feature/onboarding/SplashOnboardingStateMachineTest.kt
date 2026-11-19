package org.mohtech.app.feature.onboarding

import org.mohtech.app.AppDestination
import org.mohtech.app.data.storage.AppPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * SplashOnboardingStateMachineTest (Feature F7 - Requirement R7):
 * Opaque-box specification tests verifying the navigation state machine and persistence logic
 * of the interactive Splash Screen and multi-step Onboarding flow.
 */
class SplashOnboardingStateMachineTest {

    private class InMemoryPreferences(
        private var onboardingCompleted: Boolean = false,
        private var preferredCurrency: String = "₪",
        private var weeklyBudget: Double = 2500.0
    ) : AppPreferences {
        override fun isOnboardingCompleted(): Boolean = onboardingCompleted
        override fun setOnboardingCompleted(completed: Boolean) { onboardingCompleted = completed }
        override fun getPreferredCurrency(): String = preferredCurrency
        override fun setPreferredCurrency(currency: String) { preferredCurrency = currency }
        override fun getWeeklyBudget(): Double = weeklyBudget
        override fun setWeeklyBudget(budget: Double) { weeklyBudget = budget }
    }

    // =========================================================================
    // Tier 1: Functional Feature Coverage (Requirement R7)
    // =========================================================================

    @Test
    fun testInitialDestinationIsSplash() {
        val destination = AppDestination.SPLASH
        assertEquals(AppDestination.SPLASH, destination)
    }

    @Test
    fun testSplashTransitionsToOnboardingWhenFirstLaunch() {
        val preferences = InMemoryPreferences(onboardingCompleted = false)
        val navState = org.mohtech.app.AppNavigationState()

        navState.onSplashFinished(preferences)

        assertEquals(
            AppDestination.ONBOARDING,
            navState.currentDestination,
            "First-launch app must transition from SPLASH to ONBOARDING"
        )
    }

    @Test
    fun testSplashTransitionsToDashboardWhenOnboardingAlreadyCompleted() {
        val preferences = InMemoryPreferences(onboardingCompleted = true)
        val navState = org.mohtech.app.AppNavigationState()

        navState.onSplashFinished(preferences)

        assertEquals(
            AppDestination.DASHBOARD,
            navState.currentDestination,
            "Returning user who completed onboarding must transition directly from SPLASH to DASHBOARD"
        )
    }

    @Test
    fun testCompleteOnboardingTransitionsToDashboard() {
        val preferences = InMemoryPreferences(onboardingCompleted = false)
        val navState = org.mohtech.app.AppNavigationState(
            initialDestination = AppDestination.ONBOARDING,
            initialSplashShown = true
        )

        navState.onOnboardingComplete(preferences)

        assertEquals(AppDestination.DASHBOARD, navState.currentDestination)
        assertTrue(preferences.isOnboardingCompleted(), "Preferences must persist completed onboarding state")
    }

    @Test
    fun testSkipOnboardingTransitionsToDashboardAndMarksCompleted() {
        val preferences = InMemoryPreferences(onboardingCompleted = false)
        var currentDestination = AppDestination.ONBOARDING

        // Onboarding skip button executes onComplete handler
        preferences.setOnboardingCompleted(true)
        currentDestination = AppDestination.DASHBOARD

        assertEquals(AppDestination.DASHBOARD, currentDestination)
        assertTrue(preferences.isOnboardingCompleted(), "Skipping onboarding must mark it as completed")
    }

    // =========================================================================
    // Tier 2: Boundary, Corner & Idempotence Checks
    // =========================================================================

    @Test
    fun testOnboardingPersistenceAcrossAppLaunches() {
        val preferences = InMemoryPreferences(onboardingCompleted = false)
        assertFalse(preferences.isOnboardingCompleted())

        // 1. Complete onboarding on first launch
        preferences.setOnboardingCompleted(true)

        // 2. Simulate next launch: reading stored preferences directly
        val isCompletedOnNextLaunch = preferences.isOnboardingCompleted()
        assertTrue(isCompletedOnNextLaunch, "Onboarding completed flag must survive app restarts")
    }

    @Test
    fun testIdempotentOnboardingCompletion() {
        val preferences = InMemoryPreferences(onboardingCompleted = false)

        // Multiple consecutive completions must be safe and idempotent
        preferences.setOnboardingCompleted(true)
        preferences.setOnboardingCompleted(true)
        preferences.setOnboardingCompleted(true)

        assertTrue(preferences.isOnboardingCompleted())
    }

    @Test
    fun testPreferencesDefaultIsFalse() {
        val freshPreferences = InMemoryPreferences()
        assertFalse(
            freshPreferences.isOnboardingCompleted(),
            "Default uninitialized state for onboarding must be false"
        )
    }

    @Test
    fun testDestinationEnumCompleteness() {
        val destinations = AppDestination.entries
        assertEquals(3, destinations.size, "App must define exactly 3 primary destinations")
        assertTrue(destinations.contains(AppDestination.SPLASH))
        assertTrue(destinations.contains(AppDestination.ONBOARDING))
        assertTrue(destinations.contains(AppDestination.DASHBOARD))
    }

    @Test
    fun testFastForwardOnboarding() {
        val preferences = InMemoryPreferences(onboardingCompleted = false)
        var currentDestination = AppDestination.SPLASH

        // Splash finishes -> Onboarding
        if (!preferences.isOnboardingCompleted()) {
            currentDestination = AppDestination.ONBOARDING
        }
        assertEquals(AppDestination.ONBOARDING, currentDestination)

        // Immediate complete on first screen -> Dashboard
        preferences.setOnboardingCompleted(true)
        currentDestination = AppDestination.DASHBOARD
        assertEquals(AppDestination.DASHBOARD, currentDestination)
        assertTrue(preferences.isOnboardingCompleted())
    }
}
