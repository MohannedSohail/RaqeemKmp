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

    fun getWeeklyBudget(): Double
    fun setWeeklyBudget(budget: Double)
    fun isWeeklyBudgetSet(): Boolean = getWeeklyBudget() > 0.0

    fun getWeekStartDay(): Int = 6
    fun setWeekStartDay(day: Int) {}
}

/**
 * Platform factory to instantiate the platform-backed AppPreferences.
 */
expect fun createAppPreferences(context: Any? = null): AppPreferences

