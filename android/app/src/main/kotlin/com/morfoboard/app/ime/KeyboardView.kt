package com.morfoboard.app.ime

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.morfoboard.app.R
import com.morfoboard.app.settings.MorfoboardSettingsStore

/**
 * Custom keyboard view that renders QWERTY/symbol layouts.
 */
class KeyboardView(
    context: Context,
    private val onKeyAction: (KeyDef) -> Unit
) : LinearLayout(context) {

    private var shiftState = ShiftState.OFF
    private var isSymbolMode = false
    private var isSymbolPage2 = false
    private var isEmojiMode = false
    private var emojiPage = 0
    private val keyRows = mutableListOf<LinearLayout>()
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val settingsStore = MorfoboardSettingsStore(context)
    
    // Cached typefaces to avoid repeated allocation
    private val typefaceMedium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val typefaceNormal: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    
    // Cached accent letter sets
    private companion object {
        val ACCENT_LETTERS = setOf("n", "o", "a", "f", "r")
        val SECONDARY_LETTERS = setOf("w", "j", "z", "x", "b")
    }

    init {
        orientation = VERTICAL
        applyThemeBackground()
        setPadding(dp(6), dp(2), dp(6), dp(8))
        clipToPadding = false
        renderLayout()
    }

    private fun applyThemeBackground() {
        val themeColors = settingsStore.currentThemeColors
        setBackgroundColor(themeColors.keyboardBg)
    }

    fun renderLayout() {
        applyThemeBackground()
        removeAllViews()
        keyRows.clear()

        if (isEmojiMode) {
            renderEmojiLayout()
            return
        }

        val rows = if (isSymbolMode) {
            if (isSymbolPage2) KeyboardLayouts.getSymbol2Rows() else KeyboardLayouts.getSymbolRows()
        } else {
            KeyboardLayouts.getQwertyRows(shiftState)
        }

        for (row in rows) {
            val rowLayout = createRow(row)
            addView(rowLayout)
            keyRows.add(rowLayout)
        }
    }

    private fun renderEmojiLayout() {
        val themeColors = settingsStore.currentThemeColors
        val emojis = KeyboardLayouts.emojiPages.getOrElse(emojiPage) { KeyboardLayouts.emojiPages[0] }

        // Emoji grid: 8 columns
        val columns = 8
        val emojiRows = emojis.chunked(columns)

        for (row in emojiRows) {
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            }

            for (emoji in row) {
                val btn = Button(context).apply {
                    text = emoji
                    textSize = 22f
                    gravity = Gravity.CENTER
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
                    setPadding(0, 0, 0, 0)
                    minimumWidth = 0
                    minimumHeight = 0
                    elevation = 0f
                    stateListAnimator = null

                    setOnClickListener {
                        performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        onKeyAction(KeyDef(emoji, code = 0, keyType = KeyType.CHARACTER))
                    }
                }
                rowLayout.addView(btn)
            }
            addView(rowLayout)
        }

        // Bottom bar: ABC, page indicators, backspace
        val bottomBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 0.8f)
        }

        // ABC button
        val abcBtn = Button(context).apply {
            text = "ABC"
            textSize = 13f
            setTextColor(themeColors.textPrimary)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1.5f)
            setPadding(0, 0, 0, 0)
            minimumWidth = 0
            elevation = 0f
            stateListAnimator = null
            setOnClickListener { toggleEmojiMode() }
        }
        bottomBar.addView(abcBtn)

        // Page buttons
        for (i in KeyboardLayouts.emojiPages.indices) {
            val pageBtn = Button(context).apply {
                text = when (i) { 0 -> "☺"; 1 -> "👍"; else -> "🔥" }
                textSize = 16f
                alpha = if (i == emojiPage) 1f else 0.4f
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
                setPadding(0, 0, 0, 0)
                minimumWidth = 0
                elevation = 0f
                stateListAnimator = null
                setOnClickListener {
                    emojiPage = i
                    renderLayout()
                }
            }
            bottomBar.addView(pageBtn)
        }

        // Spacer
        bottomBar.addView(View(context).apply {
            layoutParams = LayoutParams(0, 1, 1.5f)
        })

        // Backspace
        val bsBtn = Button(context).apply {
            text = "⌫"
            textSize = 18f
            setTextColor(themeColors.textSecondary)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1.5f)
            setPadding(0, 0, 0, 0)
            minimumWidth = 0
            elevation = 0f
            stateListAnimator = null
            setOnClickListener {
                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                onKeyAction(KeyDef("⌫", code = 0, keyType = KeyType.BACKSPACE, isSpecial = true))
            }
        }
        bottomBar.addView(bsBtn)

        addView(bottomBar)
    }

    fun toggleEmojiMode() {
        isEmojiMode = !isEmojiMode
        if (!isEmojiMode) emojiPage = 0
        isSymbolMode = false
        renderLayout()
    }

    private fun createRow(keys: List<KeyDef>): LinearLayout {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply {
                setMargins(0, 0, 0, 0)
            }

            for (key in keys) {
                val keyView = createKeyView(key)
                addView(keyView)
            }
        }
    }

    private fun createKeyView(key: KeyDef): View {
        val themeColors = settingsStore.currentThemeColors
        val cornerRadius = settingsStore.keyCornerRadiusDp

        val isAccent = key.keyType == KeyType.CHARACTER && ACCENT_LETTERS.contains(key.label.lowercase())
        val isSecondaryStyle = key.keyType == KeyType.CHARACTER && SECONDARY_LETTERS.contains(key.label.lowercase())

        // Key background
        val keyBg = when {
            key.isSpecial -> createKeyBackground(themeColors.keyBgSpecial, themeColors.keyBgPressed, cornerRadius)
            isAccent -> createKeyBackground(settingsStore.accentKeyBg, settingsStore.accentKeyBgPressed, cornerRadius)
            isSecondaryStyle -> createKeyBackground(themeColors.keyBgSpecial, themeColors.keyBgPressed, cornerRadius)
            else -> createKeyBackground(themeColors.keyBg, themeColors.keyBgPressed, cornerRadius)
        }

        // Text color
        val isEmbossed = key.keyType == KeyType.SPACE || (!isAccent && !key.isSpecial)
        val textColor = when {
            isAccent -> settingsStore.accentTextColor
            isEmbossed -> themeColors.textSecondary
            key.isSpecial -> themeColors.textSecondary
            else -> themeColors.textPrimary
        }

        // Use FrameLayout to overlay secondary hint
        val container = FrameLayout(context).apply {
            tag = key
            background = keyBg
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, key.widthWeight).apply {
                setMargins(dp(1), dp(1), dp(1), dp(1))
            }
        }

        // Main label
        val label = displayLabel(key)
        val mainText = if (key.keyType == KeyType.CHARACTER && key.code != 0) {
            if (shiftState == ShiftState.OFF) label.lowercase() else label.uppercase()
        } else {
            label
        }

        val mainLabel = TextView(context).apply {
            text = mainText
            setTextColor(textColor)
            textSize = when (key.keyType) {
                KeyType.SPACE -> 13f
                KeyType.SHIFT, KeyType.BACKSPACE, KeyType.ENTER, KeyType.LANGUAGE, KeyType.ACTION_AI -> 17f
                KeyType.SYMBOL_TOGGLE, KeyType.SYMBOL_PAGE_TOGGLE -> 12f
                KeyType.EMOJI_TOGGLE -> 18f
                else -> 16f
            }
            typeface = typefaceMedium
            letterSpacing = if (key.keyType == KeyType.CHARACTER || key.keyType == KeyType.SPACE) 0.04f else 0.01f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            if (key.label.equals("m", ignoreCase = true)) {
                alpha = 0.6f
            } else if (isEmbossed && !isSecondaryStyle) {
                alpha = 0.7f
            } else if (key.isSpecial) {
                alpha = 0.85f
            }
        }
        container.addView(mainLabel)

        // Secondary hint label (top-right corner)
        if (key.secondaryLabel != null) {
            // Use accent text color for NOAFR keys, muted for others
            val hintColor = if (isAccent) settingsStore.accentTextColor else themeColors.textMuted
            val hintLabel = TextView(context).apply {
                text = key.secondaryLabel
                setTextColor(hintColor)
                textSize = 9f
                typeface = typefaceNormal
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.END
                ).apply {
                    setMargins(0, dp(2), dp(4), 0)
                }
            }
            container.addView(hintLabel)
        }

        // Touch handling
        container.setOnTouchListener(KeyTouchListener(key, audioManager, onKeyAction))
        container.isClickable = true

        return container
    }

    /**
     * Create a programmatic key background drawable with dynamic color and corner radius.
     */
    private fun createKeyBackground(color: Int, pressedColor: Int, cornerRadiusDp: Float): StateListDrawable {
        val radiusPx = dp(cornerRadiusDp.toInt()).toFloat()
        
        val normalDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radiusPx
        }
        
        val pressedDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(pressedColor)
            cornerRadius = radiusPx
        }
        
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
            addState(intArrayOf(), normalDrawable)
        }
    }

    fun updateShiftState(state: ShiftState) {
        if (shiftState == state) return
        shiftState = state
        
        // Use direct text updates for better performance during shift toggling
        if (!isSymbolMode && !isEmojiMode) {
            for (i in 0 until childCount) {
                val row = getChildAt(i) as? LinearLayout ?: continue
                for (j in 0 until row.childCount) {
                    val container = row.getChildAt(j) as? FrameLayout ?: continue
                    val key = container.tag as? KeyDef ?: continue
                    if (key.keyType == KeyType.CHARACTER && key.code != 0) {
                        // First child is the main label TextView
                        val mainLabel = container.getChildAt(0) as? TextView ?: continue
                        mainLabel.text = if (state == ShiftState.OFF) key.label.lowercase() else key.label.uppercase()
                    }
                }
            }
        }
    }

    fun toggleSymbolMode() {
        isSymbolMode = !isSymbolMode
        isSymbolPage2 = false
        isEmojiMode = false
        shiftState = ShiftState.OFF
        renderLayout()
    }
    
    fun toggleSymbolPage() {
        isSymbolPage2 = !isSymbolPage2
        renderLayout()
    }

    private fun displayLabel(key: KeyDef): String {
        return when (key.keyType) {
            KeyType.SHIFT -> "⇧"
            KeyType.BACKSPACE -> "⌫"
            KeyType.ENTER -> "↵"
            KeyType.LANGUAGE -> "◎"
            KeyType.ACTION_AI -> "⌁"
            KeyType.SPACE -> "MORFOBOARD"
            KeyType.SYMBOL_TOGGLE -> if (isSymbolMode) "ABC" else "?123"
            KeyType.SYMBOL_PAGE_TOGGLE -> if (isSymbolPage2) "?123" else "=\\<"
            KeyType.EMOJI_TOGGLE -> "🤫"
            else -> key.label.uppercase()
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}

/**
 * Handles key press with haptic feedback, sound feedback, and long-press for secondary characters.
 * Primary character is emitted immediately on touch for zero-latency typing.
 * Long-press (300ms) replaces the primary with the secondary character.
 */
class KeyTouchListener(
    private val key: KeyDef,
    private val audioManager: AudioManager?,
    private val onKeyAction: (KeyDef) -> Unit
) : View.OnTouchListener {

    private var isHolding = false
    private var longPressTriggered = false
    private val handler = Handler(Looper.getMainLooper())
    
    private val repeatRunnable = object : Runnable {
        override fun run() {
            if (isHolding) {
                onKeyAction(key)
                handler.postDelayed(this, 50)
            }
        }
    }

    private val longPressRunnable = Runnable {
        if (key.secondaryLabel != null && !longPressTriggered) {
            longPressTriggered = true
            // Delete the primary character that was already emitted, then emit secondary
            onKeyAction(KeyDef("⌫", code = 0, keyType = KeyType.BACKSPACE, isSpecial = true))
            onKeyAction(KeyDef(key.secondaryLabel, code = 0, keyType = KeyType.CHARACTER))
        }
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                v.isPressed = true
                longPressTriggered = false
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                audioManager?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, -1f)
                
                // Always emit primary immediately for responsive typing
                onKeyAction(key)
                
                if (key.keyType == KeyType.BACKSPACE) {
                    isHolding = true
                    handler.postDelayed(repeatRunnable, 400)
                } else if (key.secondaryLabel != null) {
                    // Schedule long-press: will replace primary with secondary
                    handler.postDelayed(longPressRunnable, 350)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.isPressed = false
                handler.removeCallbacks(longPressRunnable)
                
                if (key.keyType == KeyType.BACKSPACE) {
                    isHolding = false
                    handler.removeCallbacks(repeatRunnable)
                }
                longPressTriggered = false
            }
        }
        return true
    }
}
