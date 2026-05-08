package com.morfoboard.app.ime

import android.content.Context
import android.graphics.Typeface
import android.graphics.Color
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
    private val keyRows = mutableListOf<LinearLayout>()
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    init {
        orientation = VERTICAL
        // Integrated matte surface: no floating card, no outer border.
        setBackgroundResource(R.drawable.keyboard_background_rounded)
        setPadding(dp(6), dp(2), dp(6), dp(8)) // Reduced top padding from 10dp to 2dp
        clipToPadding = false
        renderLayout()
    }

    fun renderLayout() {
        removeAllViews()
        keyRows.clear()

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

    private fun createRow(keys: List<KeyDef>): LinearLayout {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply {
                setMargins(0, 0, 0, 0) // Remove row margins to balance with horizontal key margins
            }

            for (key in keys) {
                val keyView = createKeyView(key)
                addView(keyView)
            }
        }
    }

    private fun createKeyView(key: KeyDef): View {
        val emeraldLetters = setOf("n", "o", "a", "f", "r") // Added f and r
        val blueLetters = setOf("w", "j", "z", "x", "b") // Removed f

        val bgRes = when {
            key.isSpecial -> R.drawable.key_bg_special
            key.keyType == KeyType.CHARACTER && emeraldLetters.contains(key.label.lowercase()) -> R.drawable.key_bg_green
            key.keyType == KeyType.CHARACTER && blueLetters.contains(key.label.lowercase()) -> R.drawable.key_bg_blue
            else -> R.drawable.key_bg_normal
        }

        return Button(context).apply {
            tag = key
            val label = displayLabel(key)
            // Apply case based on shift state for characters
            text = if (key.keyType == KeyType.CHARACTER && key.code != 0) {
                if (shiftState == ShiftState.OFF) label.lowercase() else label.uppercase()
            } else {
                label
            }

            // Hero key 'M' branding: Thin and transparent
            if (key.label.equals("m", ignoreCase = true)) {
                text = "M"
                alpha = 0.6f // Slightly more visible
            }
            
            // Set consistent premium typography
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            
            // Text color logic
            val isEmbossed = key.keyType == KeyType.SPACE || bgRes == R.drawable.key_bg_normal || bgRes == R.drawable.key_bg_blue

            val textColor = when {
                isEmbossed -> context.getColor(R.color.text_secondary)
                bgRes == R.drawable.key_bg_green -> Color.parseColor("#A8E6CF") // Bright green tint
                bgRes == R.drawable.key_bg_special -> context.getColor(R.color.text_secondary) // Muted gray instead of white
                else -> context.getColor(R.color.key_text_bright) // Fallback
            }
            setTextColor(textColor)

            if (isEmbossed) {
                alpha = 0.6f 
            } else if (bgRes == R.drawable.key_bg_special) {
                alpha = 0.85f // Muted but slightly more visible than normal keys
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

            setBackgroundResource(bgRes)

            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, key.widthWeight).apply {
                setMargins(dp(1), dp(1), dp(1), dp(1)) // Very tight margins to prevent missed taps
            }

            // High-end startup product design: minimal, no default shadows, controlled elevation
            setPadding(0, 0, 0, 0)
            minimumWidth = 0
            minimumHeight = 0
            
            // Performance: elevation 0 to reduce rendering overhead during fast typing
            elevation = 0f
            stateListAnimator = null

            // Touch handling with haptic feedback
            setOnTouchListener(KeyTouchListener(key, audioManager, onKeyAction))
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
                        // Ensure 'm' stays thin and transparent even after shift
                        if (key.label.equals("m", ignoreCase = true)) {
                            btn.text = "M"
                            btn.alpha = 0.5f
                        }
                    }
                }
            }
        }
    }

    fun toggleSymbolMode() {
        isSymbolMode = !isSymbolMode
        isSymbolPage2 = false
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
