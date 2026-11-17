package org.mohtech.app.data.storage

import java.io.File
import java.util.Properties

class JvmAppPreferences : AppPreferences {

    private val configDir = File(System.getProperty("user.home"), ".raqeem").apply {
        if (!exists()) mkdirs()
    }
    private val configFile = File(configDir, "config.properties")
    private val properties = Properties()

    init {
        if (configFile.exists()) {
            configFile.inputStream().use { properties.load(it) }
        }
    }

    override fun isOnboardingCompleted(): Boolean {
        return properties.getProperty("onboarding_completed", "false").toBoolean()
    }

    override fun setOnboardingCompleted(completed: Boolean) {
        properties.setProperty("onboarding_completed", completed.toString())
        save()
    }

    override fun getPreferredCurrency(): String {
        return properties.getProperty("preferred_currency", "₪")
    }

    override fun setPreferredCurrency(currency: String) {
        properties.setProperty("preferred_currency", currency)
        save()
    }

    override fun getWeeklyBudget(): Double {
        return properties.getProperty("weekly_budget")?.toDoubleOrNull() ?: 2500.0
    }

    override fun setWeeklyBudget(budget: Double) {
        properties.setProperty("weekly_budget", budget.toString())
        save()
    }

    private fun save() {
        configFile.outputStream().use { properties.store(it, "Raqeem Config") }
    }
}

actual fun createAppPreferences(context: Any?): AppPreferences = JvmAppPreferences()

