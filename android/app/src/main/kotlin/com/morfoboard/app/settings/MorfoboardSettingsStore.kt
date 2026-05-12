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
        private const val KEY_THEME = "theme"
        private const val KEY_SOUND = "key_sound"
        private const val KEY_HAPTIC = "key_haptic"
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

    /** Theme: "dark", "light" */
    var theme: String
        get() = prefs.getString(KEY_THEME, "dark") ?: "dark"
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    /** Key press sound enabled */
    var keySoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND, value).apply()

    /** Key press haptic/vibration enabled */
    var keyHapticEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC, value).apply()

    val isDarkTheme: Boolean get() = theme != "light"

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

    /** Theme color definitions */
    data class ThemeColors(
        val keyboardBg: Int,
        val keyBg: Int,
        val keyBgPressed: Int,
        val keyBgSpecial: Int,
        val textPrimary: Int,
        val textSecondary: Int,
        val textMuted: Int
    )

    val currentThemeColors: ThemeColors
        get() = if (isDarkTheme) {
            ThemeColors(
                keyboardBg = 0xFF111315.toInt(),
                keyBg = 0xFF1C1F22.toInt(),
                keyBgPressed = 0xFF363C42.toInt(),
                keyBgSpecial = 0xFF30363C.toInt(),
                textPrimary = 0xFFF3F6F8.toInt(),
                textSecondary = 0xFF9AA4AD.toInt(),
                textMuted = 0xFF69727A.toInt()
            )
        } else {
            ThemeColors(
                keyboardBg = 0xFFEEF1F4.toInt(),
                keyBg = 0xFFFFFFFF.toInt(),
                keyBgPressed = 0xFFD8DCE0.toInt(),
                keyBgSpecial = 0xFFD5DAE0.toInt(),
                textPrimary = 0xFF1A1D20.toInt(),
                textSecondary = 0xFF5A6370.toInt(),
                textMuted = 0xFF8A939E.toInt()
            )
        }

    /** Color palette definitions */
    data class AccentPalette(
        val name: String,
        val label: String,
        val keyBg: Int,
        val keyBgPressed: Int,
        val textColor: Int,
        // Light theme variants
        val keyBgLight: Int,
        val keyBgPressedLight: Int,
        val textColorLight: Int
    )

    val accentPalettes: List<AccentPalette> = listOf(
        AccentPalette("mint", "Mint", 0xFF1A5C46.toInt(), 0xFF1A6B52.toInt(), 0xFF7DCDB3.toInt(), 0xFFD4F5E9.toInt(), 0xFFB8EDDA.toInt(), 0xFF1A7A56.toInt()),
        AccentPalette("ocean", "Ocean", 0xFF1A3D5C.toInt(), 0xFF1A4A6B.toInt(), 0xFF7DB3CD.toInt(), 0xFFD4E8F5.toInt(), 0xFFB8DBED.toInt(), 0xFF1A5A7A.toInt()),
        AccentPalette("lavender", "Lavender", 0xFF3D1A5C.toInt(), 0xFF4A1A6B.toInt(), 0xFFB37DCD.toInt(), 0xFFEAD4F5.toInt(), 0xFFDDB8ED.toInt(), 0xFF5A1A7A.toInt()),
        AccentPalette("rose", "Rose", 0xFF5C1A3D.toInt(), 0xFF6B1A4A.toInt(), 0xFFCD7DB3.toInt(), 0xFFF5D4EA.toInt(), 0xFFEDB8DD.toInt(), 0xFF7A1A5A.toInt()),
        AccentPalette("amber", "Amber", 0xFF5C4A1A.toInt(), 0xFF6B571A.toInt(), 0xFFCDB87D.toInt(), 0xFFF5EED4.toInt(), 0xFFEDE5B8.toInt(), 0xFF7A6A1A.toInt()),
        AccentPalette("coral", "Coral", 0xFF5C2E1A.toInt(), 0xFF6B371A.toInt(), 0xFFCD9A7D.toInt(), 0xFFF5E2D4.toInt(), 0xFFEDD4B8.toInt(), 0xFF7A4A1A.toInt()),
        AccentPalette("ice", "Ice", 0xFF1A4A5C.toInt(), 0xFF1A576B.toInt(), 0xFF7DC4CD.toInt(), 0xFFD4F0F5.toInt(), 0xFFB8E8ED.toInt(), 0xFF1A6A7A.toInt()),
        AccentPalette("slate", "Slate", 0xFF2A3038.toInt(), 0xFF343B44.toInt(), 0xFFA8B4C0.toInt(), 0xFFDDE2E8.toInt(), 0xFFC8D0D8.toInt(), 0xFF3A4450.toInt()),
        AccentPalette("neon", "Neon", 0xFF1A5C2E.toInt(), 0xFF1A6B37.toInt(), 0xFF7DCD9A.toInt(), 0xFFD4F5DE.toInt(), 0xFFB8EDC8.toInt(), 0xFF1A7A3A.toInt())
    )

    /** Get current accent palette with theme-appropriate colors */
    val currentPalette: AccentPalette
        get() = accentPalettes.find { it.name == accentColor } ?: accentPalettes[0]

    /** Get accent key background for current theme */
    val accentKeyBg: Int get() = if (isDarkTheme) currentPalette.keyBg else currentPalette.keyBgLight
    val accentKeyBgPressed: Int get() = if (isDarkTheme) currentPalette.keyBgPressed else currentPalette.keyBgPressedLight
    val accentTextColor: Int get() = if (isDarkTheme) currentPalette.textColor else currentPalette.textColorLight
}
