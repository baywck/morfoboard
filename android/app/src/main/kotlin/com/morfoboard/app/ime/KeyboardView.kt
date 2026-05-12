package com.morfoboard.app.ime

import android.content.Context
import android.graphics.Typeface
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow
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
        val accentLetters = setOf("n", "o", "a", "f", "r")
        val secondaryLetters = setOf("w", "j", "z", "x", "b")
        
        val themeColors = settingsStore.currentThemeColors
        val cornerRadius = settingsStore.keyCornerRadiusDp

        // Determine key type for styling
        val isAccent = key.keyType == KeyType.CHARACTER && accentLetters.contains(key.label.lowercase())
        val isSecondary = key.keyType == KeyType.CHARACTER && secondaryLetters.contains(key.label.lowercase())

        return Button(context).apply {
            tag = key
            val label = displayLabel(key)
            text = if (key.keyType == KeyType.CHARACTER && key.code != 0) {
                if (shiftState == ShiftState.OFF) label.lowercase() else label.uppercase()
            } else {
                label
            }

            // Hero key 'M' branding: slightly transparent
            if (key.label.equals("m", ignoreCase = true)) {
                alpha = 0.6f
            }
            
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            
            // Dynamic background based on settings and theme
            val keyBg = when {
                key.isSpecial -> createKeyBackground(
                    themeColors.keyBgSpecial,
                    themeColors.keyBgPressed,
                    cornerRadius
                )
                isAccent -> createKeyBackground(
                    settingsStore.accentKeyBg,
                    settingsStore.accentKeyBgPressed,
                    cornerRadius
                )
                isSecondary -> createKeyBackground(
                    themeColors.keyBgSpecial,
                    themeColors.keyBgPressed,
                    cornerRadius
                )
                else -> createKeyBackground(
                    themeColors.keyBg,
                    themeColors.keyBgPressed,
                    cornerRadius
                )
            }
            background = keyBg

            // Text color
            val isEmbossed = key.keyType == KeyType.SPACE || (!isAccent && !key.isSpecial)
            val textColor = when {
                isAccent -> settingsStore.accentTextColor
                isEmbossed -> themeColors.textSecondary
                key.isSpecial -> themeColors.textSecondary
                else -> themeColors.textPrimary
            }
            setTextColor(textColor)

            if (isEmbossed && !isSecondary) {
                alpha = 0.7f 
            } else if (key.isSpecial) {
                alpha = 0.85f
            }

            textSize = when (key.keyType) {
                KeyType.SPACE -> 13f 
                KeyType.SHIFT, KeyType.BACKSPACE, KeyType.ENTER, KeyType.LANGUAGE, KeyType.ACTION_AI -> 17f
                KeyType.SYMBOL_TOGGLE, KeyType.SYMBOL_PAGE_TOGGLE -> 12f
                else -> 16f
            }
            letterSpacing = if (key.keyType == KeyType.CHARACTER || key.keyType == KeyType.SPACE) 0.04f else 0.01f
            gravity = Gravity.CENTER
            isAllCaps = false

            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, key.widthWeight).apply {
                setMargins(dp(1), dp(1), dp(1), dp(1))
            }

            setPadding(0, 0, 0, 0)
            minimumWidth = 0
            minimumHeight = 0
            elevation = 0f
            stateListAnimator = null

            setOnTouchListener(KeyTouchListener(key, audioManager, onKeyAction))
        }
    }

    /**
     * Create a programmatic key background drawable with dynamic color and corner radius.
     */
    private fun createKeyBackground(color: Int, pressedColor: Int, cornerRadiusDp: Float): android.graphics.drawable.StateListDrawable {
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
        
        return android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
            addState(intArrayOf(), normalDrawable)
        }
    }

    fun updateShiftState(state: ShiftState) {
        if (shiftState == state) return
        shiftState = state
        
        // Use direct text updates for better performance during shift toggling
        if (!isSymbolMode) {
            for (i in 0 until childCount) {
                val row = getChildAt(i) as? LinearLayout ?: continue
                for (j in 0 until row.childCount) {
                    val btn = row.getChildAt(j) as? Button ?: continue
                    val key = btn.tag as? KeyDef ?: continue
                    if (key.keyType == KeyType.CHARACTER && key.code != 0) {
                        btn.text = if (state == ShiftState.OFF) key.label.lowercase() else key.label.uppercase()
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
            KeyType.EMOJI_TOGGLE -> "☺"
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
 * Handles key press with haptic feedback and sound feedback.
 */
class KeyTouchListener(
    private val key: KeyDef,
    private val audioManager: AudioManager?,
    private val onKeyAction: (KeyDef) -> Unit
) : View.OnTouchListener {

    private var isHolding = false
    private val handler = Handler(Looper.getMainLooper())
    private val repeatRunnable = object : Runnable {
        override fun run() {
            if (isHolding) {
                onKeyAction(key)
                handler.postDelayed(this, 50)
            }
        }
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                v.isPressed = true
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                audioManager?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, -1f)
                onKeyAction(key)
                
                if (key.keyType == KeyType.BACKSPACE) {
                    isHolding = true
                    handler.postDelayed(repeatRunnable, 400)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.isPressed = false
                if (key.keyType == KeyType.BACKSPACE) {
                    isHolding = false
                    handler.removeCallbacks(repeatRunnable)
                }
            }
        }
        return true
    }
}
