package com.morfoboard.app.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists user preferences (language, tone) using SharedPreferences.
 */
class MorfoboardSettingsStore(context: Context) {

    companion object {
        private const val PREFS_NAME = "morfoboard_settings"
        private const val KEY_TARGET_LANGUAGE = "target_language"
        private const val KEY_TONE = "tone"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var targetLanguage: String
        get() = prefs.getString(KEY_TARGET_LANGUAGE, "en") ?: "en"
        set(value) = prefs.edit().putString(KEY_TARGET_LANGUAGE, value).apply()

    var tone: String
        get() = prefs.getString(KEY_TONE, "natural") ?: "natural"
        set(value) = prefs.edit().putString(KEY_TONE, value).apply()
}
