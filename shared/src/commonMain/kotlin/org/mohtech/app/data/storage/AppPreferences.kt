package org.mohtech.app.data.storage

/**
 * AppPreferences:
 * Offline-first persistent key-value store for app configuration, preferred currency,
 * and onboarding lifecycle completion flag.
 */
interface AppPreferences {
    fun isOnboardingCompleted(): Boolean
    fun setOnboardingCompleted(completed: Boolean)
    fun getPreferredCurrency(): String
    fun setPreferredCurrency(currency: String)
}

/**
 * Platform factory to instantiate the platform-backed AppPreferences.
 */
expect fun createAppPreferences(context: Any? = null): AppPreferences
