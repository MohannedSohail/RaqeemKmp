package org.mohtech.app.data.storage

import android.content.Context
import android.content.SharedPreferences

class AndroidAppPreferences(context: Context) : AppPreferences {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "raqeem_preferences",
        Context.MODE_PRIVATE
    )

    override fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean("onboarding_completed", false)
    }

    override fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean("onboarding_completed", completed).apply()
    }

    override fun getPreferredCurrency(): String {
        return prefs.getString("preferred_currency", "₪") ?: "₪"
    }

    override fun setPreferredCurrency(currency: String) {
        prefs.edit().putString("preferred_currency", currency).apply()
    }
}

actual fun createAppPreferences(context: Any?): AppPreferences {
    val ctx = (context as? Context) ?: AndroidContextHolder.context
        ?: throw IllegalStateException("Android Context must be provided to initialize AppPreferences")
    return AndroidAppPreferences(ctx.applicationContext)
}
