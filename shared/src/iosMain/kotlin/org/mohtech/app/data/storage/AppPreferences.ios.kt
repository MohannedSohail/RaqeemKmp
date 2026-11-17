package org.mohtech.app.data.storage

import platform.Foundation.NSUserDefaults

class IosAppPreferences : AppPreferences {

    private val userDefaults = NSUserDefaults.standardUserDefaults

    override fun isOnboardingCompleted(): Boolean {
        return userDefaults.boolForKey("onboarding_completed")
    }

    override fun setOnboardingCompleted(completed: Boolean) {
        userDefaults.setBool(completed, forKey = "onboarding_completed")
    }

    override fun getPreferredCurrency(): String {
        return userDefaults.stringForKey("preferred_currency") ?: "₪"
    }

    override fun setPreferredCurrency(currency: String) {
        userDefaults.setObject(currency, forKey = "preferred_currency")
    }

    override fun getWeeklyBudget(): Double {
        if (userDefaults.objectForKey("weekly_budget") == null) {
            return 2500.0
        }
        return userDefaults.doubleForKey("weekly_budget")
    }

    override fun setWeeklyBudget(budget: Double) {
        userDefaults.setDouble(budget, forKey = "weekly_budget")
    }
}

actual fun createAppPreferences(context: Any?): AppPreferences = IosAppPreferences()

