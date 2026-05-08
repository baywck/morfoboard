package com.morfoboard.app.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists user preferences using SharedPreferences.
 */
class MorfoboardSettingsStore(context: Context) {

    companion object {
        private const val PREFS_NAME = "morfoboard_settings"
        private const val KEY_TARGET_LANGUAGE = "target_language"
        private const val KEY_TONE = "tone"
        private const val KEY_KEYBOARD_SIZE = "keyboard_size"
        private const val KEY_ACCENT_COLOR = "accent_color"
        private const val KEY_KEY_SHAPE = "key_shape"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var targetLanguage: String
        get() = prefs.getString(KEY_TARGET_LANGUAGE, "en") ?: "en"
        set(value) = prefs.edit().putString(KEY_TARGET_LANGUAGE, value).apply()

    var tone: String
        get() = prefs.getString(KEY_TONE, "natural") ?: "natural"
        set(value) = prefs.edit().putString(KEY_TONE, value).apply()

    /** Keyboard size preset: "small", "medium", "large" */
    var keyboardSize: String
        get() = prefs.getString(KEY_KEYBOARD_SIZE, "medium") ?: "medium"
        set(value) = prefs.edit().putString(KEY_KEYBOARD_SIZE, value).apply()

    /** Accent color preset name */
    var accentColor: String
        get() = prefs.getString(KEY_ACCENT_COLOR, "mint") ?: "mint"
        set(value) = prefs.edit().putString(KEY_ACCENT_COLOR, value).apply()

    /** Key shape: "rounded", "semi", "square" */
    var keyShape: String
        get() = prefs.getString(KEY_KEY_SHAPE, "rounded") ?: "rounded"
        set(value) = prefs.edit().putString(KEY_KEY_SHAPE, value).apply()

    /** Returns the height ratio based on keyboard size preset */
    val keyboardHeightRatio: Float
        get() = when (keyboardSize) {
            "small" -> 0.26f
            "large" -> 0.36f
            else -> 0.30f // medium
        }

    /** Returns corner radius in dp based on key shape */
    val keyCornerRadiusDp: Float
        get() = when (keyShape) {
            "square" -> 0f
            "semi" -> 4f
            else -> 8f // rounded
        }

    /** Color palette definitions */
    data class AccentPalette(
        val name: String,
        val label: String,
        val keyBg: Int,       // Key background color (ARGB)
        val keyBgPressed: Int,
        val textColor: Int    // Text color on accent keys
    )

    val accentPalettes: List<AccentPalette> = listOf(
        AccentPalette("mint", "Mint", 0xFF1A5C46.toInt(), 0xFF1A6B52.toInt(), 0xFF7DCDB3.toInt()),
        AccentPalette("ocean", "Ocean", 0xFF1A3D5C.toInt(), 0xFF1A4A6B.toInt(), 0xFF7DB3CD.toInt()),
        AccentPalette("lavender", "Lavender", 0xFF3D1A5C.toInt(), 0xFF4A1A6B.toInt(), 0xFFB37DCD.toInt()),
        AccentPalette("rose", "Rose", 0xFF5C1A3D.toInt(), 0xFF6B1A4A.toInt(), 0xFFCD7DB3.toInt()),
        AccentPalette("amber", "Amber", 0xFF5C4A1A.toInt(), 0xFF6B571A.toInt(), 0xFFCDB87D.toInt()),
        AccentPalette("coral", "Coral", 0xFF5C2E1A.toInt(), 0xFF6B371A.toInt(), 0xFFCD9A7D.toInt()),
        AccentPalette("ice", "Ice", 0xFF1A4A5C.toInt(), 0xFF1A576B.toInt(), 0xFF7DC4CD.toInt()),
        AccentPalette("slate", "Slate", 0xFF2A3038.toInt(), 0xFF343B44.toInt(), 0xFFA8B4C0.toInt()),
        AccentPalette("neon", "Neon", 0xFF1A5C2E.toInt(), 0xFF1A6B37.toInt(), 0xFF7DCD9A.toInt())
    )

    /** Get current accent palette */
    val currentPalette: AccentPalette
        get() = accentPalettes.find { it.name == accentColor } ?: accentPalettes[0]
}
