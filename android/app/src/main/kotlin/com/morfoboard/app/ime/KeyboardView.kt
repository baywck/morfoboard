package com.morfoboard.app.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
import android.view.ViewConfiguration
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.morfoboard.app.R
import com.morfoboard.app.settings.MorfoboardSettingsStore

/**
 * Custom keyboard view that renders QWERTY/symbol layouts.
 */
@Suppress("ViewConstructor")
class KeyboardView(
    context: Context,
    private val onKeyAction: (KeyDef) -> Unit
) : LinearLayout(context) {

    private var shiftState = ShiftState.OFF
    private var isSymbolMode = false
    private var symbolPage = 0
    private var isEmojiMode = false
    private var emojiCategory = 0
    private var emojiPage = 0
    private val keyRows = mutableListOf<LinearLayout>()
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val settingsStore = MorfoboardSettingsStore(context)
    private val keyTouchListenerFactory = KeyTouchListenerFactory(
        audioManager = audioManager,
        onKeyAction = onKeyAction
    )
    // Preferences are stable for this rendered input view; avoid SharedPreferences
    // reads while creating every key and forwarding edge touches.
    private val soundEnabled = settingsStore.keySoundEnabled
    private val hapticEnabled = settingsStore.keyHapticEnabled
    
    // Cached typefaces to avoid repeated allocation
    private val typefaceMedium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val typefaceNormal: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    
    // Cached accent letter sets
    private companion object {
        const val EMOJI_COLUMNS = 8
        const val EMOJI_ROWS = 4
        val ACCENT_LETTERS = setOf("n", "o", "a", "f", "r")
        val SECONDARY_LETTERS = setOf("w", "j", "z", "x", "b")
        val BACKSPACE_KEY = KeyDef("⌫", keyType = KeyType.BACKSPACE, isSpecial = true)
        val ICON_KEY_TYPES = setOf(
            KeyType.SHIFT,
            KeyType.BACKSPACE,
            KeyType.ENTER,
            KeyType.EMOJI_TOGGLE
        )
    }

    init {
        orientation = VERTICAL
        applyThemeBackground()
        // Symmetric outer spacing keeps edge keys close to the screen without
        // making vertical and horizontal rhythm feel unrelated.
        // Alphabet rows use the full screen width; edge-key margins provide the
        // only horizontal breathing room for Q/P and Shift/Backspace.
        setPadding(0, dp(1), 0, dp(3))
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
            when (symbolPage) {
                1 -> KeyboardLayouts.getSymbol2Rows()
                2 -> KeyboardLayouts.getSymbol3Rows()
                else -> KeyboardLayouts.getSymbolRows()
            }
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
        val category = KeyboardLayouts.emojiCategories.getOrElse(emojiCategory) {
            KeyboardLayouts.emojiCategories.first()
        }
        val pageSize = EMOJI_COLUMNS * EMOJI_ROWS
        val pages = category.emojis.chunked(pageSize)
        emojiPage = emojiPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        val visibleEmojis = pages.getOrElse(emojiPage) { emptyList() }

        val grid = GridLayout(context).apply {
            columnCount = EMOJI_COLUMNS
            rowCount = EMOJI_ROWS
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }
        repeat(pageSize) { index ->
            val emoji = visibleEmojis.getOrNull(index)
            grid.addView(createEmojiButton(emoji), GridLayout.LayoutParams().apply {
                width = 0
                height = 0
                columnSpec = GridLayout.spec(index % EMOJI_COLUMNS, 1f)
                rowSpec = GridLayout.spec(index / EMOJI_COLUMNS, 1f)
            })
        }
        addView(grid)

        val bottomBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 0.8f)
        }
        bottomBar.addView(createEmojiControl("ABC", 1.35f) { toggleEmojiMode() })
        bottomBar.addView(createEmojiControl("‹", 0.7f, emojiPage > 0) {
            emojiPage -= 1
            renderLayout()
        })
        bottomBar.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 5f)
            addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                for ((index, emojiCategoryDef) in KeyboardLayouts.emojiCategories.withIndex()) {
                    addView(createEmojiControl(emojiCategoryDef.icon, 0f) {
                        emojiCategory = index
                        emojiPage = 0
                        renderLayout()
                    }.apply {
                        contentDescription = emojiCategoryDef.contentDescription
                        alpha = if (index == emojiCategory) 1f else 0.45f
                        layoutParams = LinearLayout.LayoutParams(dp(42), LayoutParams.MATCH_PARENT)
                    })
                }
            })
        })
        bottomBar.addView(createEmojiControl("›", 0.7f, emojiPage < pages.lastIndex) {
            emojiPage += 1
            renderLayout()
        })
        bottomBar.addView(createEmojiControl("⌫", 1.15f) {
            keyTouchListenerFactory.dispatch(BACKSPACE_KEY)
        })
        addView(bottomBar)
    }

    private fun createEmojiButton(emoji: String?): Button = Button(context).apply {
        text = emoji.orEmpty()
        textSize = 21f
        gravity = Gravity.CENTER
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        setPadding(0, 0, 0, 0)
        minimumWidth = 0
        minimumHeight = 0
        elevation = 0f
        stateListAnimator = null
        isEnabled = emoji != null
        if (emoji != null) {
            contentDescription = "Emoji $emoji"
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                keyTouchListenerFactory.dispatch(KeyDef(emoji, keyType = KeyType.CHARACTER))
            }
        }
    }

    private fun createEmojiControl(
        label: String,
        weight: Float,
        enabled: Boolean = true,
        onClick: () -> Unit
    ): Button = Button(context).apply {
        text = label
        textSize = if (label.length <= 2) 17f else 12f
        setTextColor(settingsStore.currentThemeColors.textSecondary)
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, weight)
        setPadding(0, 0, 0, 0)
        minimumWidth = 0
        minimumHeight = 0
        elevation = 0f
        stateListAnimator = null
        isEnabled = enabled
        alpha = if (enabled) 1f else 0.28f
        setOnClickListener { onClick() }
    }

    fun toggleEmojiMode() {
        isEmojiMode = !isEmojiMode
        if (!isEmojiMode) {
            emojiCategory = 0
            emojiPage = 0
        }
        isSymbolMode = false
        renderLayout()
    }

    private fun createRow(keys: List<KeyDef>): LinearLayout {
        return TouchRoutingRow(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply {
                setMargins(0, 0, 0, 0)
            }

            val isAlphabetHomeRow = !isSymbolMode && !isEmojiMode &&
                keys.size == 9 && keys.all { it.keyType == KeyType.CHARACTER }
            val isAlphabetShiftRow = !isSymbolMode && !isEmojiMode &&
                keys.size == 9 && keys.first().keyType == KeyType.SHIFT

            // Row 2: center nine letter-width keys in the ten-column grid.
            if (isAlphabetHomeRow) addView(createRowSpacer(0.5f, keys.first()))

            if (isAlphabetShiftRow) {
                // Row 3 is full width. Letter keys use an explicit width derived
                // from the QWERTY row; Shift/Backspace split only the remainder.
                addView(createKeyView(keys.first(), alphabetControlWeight = 1.5f))
                keys.subList(1, keys.lastIndex).forEach { key ->
                    addView(createKeyView(key, alphabetLetterWeight = 1f))
                }
                addView(createKeyView(keys.last(), alphabetControlWeight = 1.5f))
            } else {
                for (key in keys) addView(createKeyView(key))
            }

            if (isAlphabetHomeRow) addView(createRowSpacer(0.5f, keys.last()))
        }
    }

    private fun createRowSpacer(weight: Float, touchKey: KeyDef? = null): View = View(context).apply {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, weight)
        if (touchKey != null) {
            isClickable = true
            @Suppress("ClickableViewAccessibility")
            setOnTouchListener(keyTouchListenerFactory.create(
                key = touchKey,
                soundEnabled = soundEnabled,
                hapticEnabled = hapticEnabled
            ))
        } else {
            isClickable = false
        }
    }

    private fun createKeyView(
        key: KeyDef,
        alphabetLetterWeight: Float? = null,
        alphabetControlWeight: Float? = null
    ): View {
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
            val effectiveWeight = alphabetLetterWeight ?: alphabetControlWeight ?: key.widthWeight
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, effectiveWeight).apply {
                // Equal horizontal/vertical gutters produce a regular key grid.
                // Balanced 2dp spacing on every side. TouchRoutingRow keeps
                // these visual gutters active by routing taps to the nearest key.
                setMargins(dp(2), dp(2), dp(2), dp(2))
            }
        }

        // Main label
        val label = displayLabel(key)
        val mainText = if (key.keyType == KeyType.CHARACTER && key.code != 0) {
            if (shiftState == ShiftState.OFF) label.lowercase() else label.uppercase()
        } else {
            label
        }

        if (key.keyType in ICON_KEY_TYPES) {
            // Vector icons remain centered and consistent across OEM fonts.
            container.addView(KeyboardIconView(context, key.keyType, shiftState, textColor).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                contentDescription = when (key.keyType) {
                    KeyType.SHIFT -> "Shift"
                    KeyType.BACKSPACE -> "Backspace"
                    KeyType.ENTER -> "Enter"
                    KeyType.EMOJI_TOGGLE -> "Emoji"
                    else -> key.label
                }
            })
        } else {
            val mainLabel = TextView(context).apply {
                text = mainText
                setTextColor(textColor)
                textSize = when (key.keyType) {
                    KeyType.SPACE -> 13f
                    KeyType.LANGUAGE, KeyType.ACTION_AI -> 17f
                    KeyType.SYMBOL_TOGGLE, KeyType.SYMBOL_PAGE_TOGGLE -> 12f
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
        }

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

        // Read preferences while rendering, not during the latency-sensitive touch path.
        @Suppress("ClickableViewAccessibility")
        container.setOnTouchListener(keyTouchListenerFactory.create(
            key = key,
            soundEnabled = soundEnabled,
            hapticEnabled = hapticEnabled
        ))
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
                    } else if (key.keyType == KeyType.SHIFT) {
                        (container.getChildAt(0) as? KeyboardIconView)?.shiftState = state
                    }
                }
            }
        }
    }

    fun toggleSymbolMode() {
        isSymbolMode = !isSymbolMode
        symbolPage = 0
        isEmojiMode = false
        shiftState = ShiftState.OFF
        renderLayout()
    }
    
    fun toggleSymbolPage() {
        symbolPage = (symbolPage + 1) % 3
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
            KeyType.SYMBOL_PAGE_TOGGLE -> when (symbolPage) {
                0 -> "=\\<"
                1 -> "2/3"
                else -> "?123"
            }
            KeyType.EMOJI_TOGGLE -> "Emoji"
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
/**
 * Routes a gesture that starts in a key gutter to the closest key. Android does
 * not include child margins in a child's hit rect, so ordinary LinearLayout rows
 * create thin dead strips that feel like intermittent missed taps.
 */
private class TouchRoutingRow(context: Context) : LinearLayout(context) {
    // One target per pointer ID: a second finger landing on this row while the
    // first is still down (fast same-row rollover like A+K) must reach its own
    // key. Routing the whole gesture to a single target drops that keystroke.
    private val pointerTargets = android.util.SparseArray<View>()

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                val target = findNearestClickableChild(event.getX(index), event.getY(index))
                if (target != null) {
                    pointerTargets.put(event.getPointerId(index), target)
                    sendToTarget(target, event, index, MotionEvent.ACTION_DOWN)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                for (index in 0 until event.pointerCount) {
                    val target = pointerTargets[event.getPointerId(index)] ?: continue
                    sendToTarget(target, event, index, MotionEvent.ACTION_MOVE)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                val id = event.getPointerId(index)
                pointerTargets[id]?.let { sendToTarget(it, event, index, MotionEvent.ACTION_UP) }
                pointerTargets.remove(id)
            }

            MotionEvent.ACTION_CANCEL -> {
                for (i in 0 until pointerTargets.size()) {
                    sendToTarget(pointerTargets.valueAt(i), event, 0, MotionEvent.ACTION_CANCEL)
                }
                pointerTargets.clear()
            }
        }
        return true
    }

    /**
     * Forwards one pointer of the row-level event to its key as a clean
     * single-pointer gesture, in the key's local coordinates.
     */
    private fun sendToTarget(target: View, source: MotionEvent, pointerIndex: Int, action: Int) {
        var x = source.getX(pointerIndex) - target.left
        var y = source.getY(pointerIndex) - target.top
        if (action == MotionEvent.ACTION_DOWN) {
            // A tap starting in the gutter is clamped just inside the key so the
            // listener sees a normal in-bounds press. Later events keep real
            // offsets so slide-off detection still works.
            x = x.coerceIn(1f, (target.width - 1).coerceAtLeast(1).toFloat())
            y = y.coerceIn(1f, (target.height - 1).coerceAtLeast(1).toFloat())
        }
        val event = MotionEvent.obtain(
            source.downTime, source.eventTime, action, x, y, source.metaState
        )
        target.dispatchTouchEvent(event)
        event.recycle()
    }

    private fun findNearestClickableChild(x: Float, y: Float): View? {
        var nearest: View? = null
        var nearestDistance = Float.MAX_VALUE
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (!child.isClickable || child.visibility != View.VISIBLE) continue
            val dx = when {
                x < child.left -> child.left - x
                x > child.right -> x - child.right
                else -> 0f
            }
            val dy = when {
                y < child.top -> child.top - y
                y > child.bottom -> y - child.bottom
                else -> 0f
            }
            val distance = dx * dx + dy * dy
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearest = child
            }
        }
        return nearest
    }

}

private class KeyTouchListenerFactory(
    private val audioManager: AudioManager?,
    private val onKeyAction: (KeyDef) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var inputRevision = 0L

    fun dispatch(key: KeyDef) {
        inputRevision += 1
        onKeyAction(key)
    }

    fun create(
        key: KeyDef,
        soundEnabled: Boolean,
        hapticEnabled: Boolean
    ): View.OnTouchListener = KeyTouchListener(
        key = key,
        audioManager = audioManager,
        dispatchKey = { dispatchedKey ->
            inputRevision += 1
            onKeyAction(dispatchedKey)
            inputRevision
        },
        isLatestInput = { revision -> revision == inputRevision },
        handler = handler,
        soundEnabled = soundEnabled,
        hapticEnabled = hapticEnabled
    )
}

private class KeyTouchListener(
    private val key: KeyDef,
    private val audioManager: AudioManager?,
    private val dispatchKey: (KeyDef) -> Long,
    private val isLatestInput: (Long) -> Boolean,
    private val handler: Handler,
    private val soundEnabled: Boolean,
    private val hapticEnabled: Boolean
) : View.OnTouchListener {

    companion object {
        private val BACKSPACE_KEY = KeyDef(
            "⌫",
            code = 0,
            keyType = KeyType.BACKSPACE,
            isSpecial = true
        )
        private const val BACKSPACE_REPEAT_DELAY_MS = 50L
    }

    private var isHolding = false
    private var longPressTriggered = false
    private var primaryRevision = -1L
    private var replacementRevision = -1L
    private var downX = 0f
    private var downY = 0f

    private val repeatRunnable = object : Runnable {
        override fun run() {
            if (isHolding) {
                dispatchKey(key)
                handler.postDelayed(this, BACKSPACE_REPEAT_DELAY_MS)
            }
        }
    }

    private val longPressRunnable = Runnable {
        val secondary = key.secondaryLabel
        if (secondary != null && isLatestInput(primaryRevision) && !longPressTriggered) {
            longPressTriggered = true
            // Replace only when no other key has moved the cursor since this press.
            dispatchKey(BACKSPACE_KEY)
            replacementRevision = dispatchKey(KeyDef(secondary, keyType = KeyType.CHARACTER))
        }
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelCallbacks()
                v.isPressed = true
                longPressTriggered = false
                primaryRevision = -1L
                replacementRevision = -1L
                downX = event.x
                downY = event.y

                // Emit on DOWN so fast taps cannot be dropped while waiting for
                // ACTION_UP — and before the haptic/audio binder calls, so the
                // commitText IPC is already queued to the editor when they run.
                primaryRevision = dispatchKey(key)

                if (hapticEnabled) {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                if (soundEnabled) {
                    audioManager?.playSoundEffect(
                        if (key.keyType == KeyType.BACKSPACE) {
                            AudioManager.FX_KEYPRESS_DELETE
                        } else {
                            AudioManager.FX_KEYPRESS_STANDARD
                        },
                        -1f
                    )
                }

                if (key.keyType == KeyType.BACKSPACE) {
                    isHolding = true
                    handler.postDelayed(repeatRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                } else if (key.secondaryLabel != null) {
                    handler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (movedOutsideKey(v, event)) {
                    v.isPressed = false
                    // A slide should never turn an already-typed letter into its long-press symbol.
                    handler.removeCallbacks(longPressRunnable)
                }
            }

            MotionEvent.ACTION_UP -> {
                v.isPressed = false
                cancelCallbacks()
                // Input is emitted on DOWN; this preserves Android's accessibility click event.
                v.performClick()
                resetPress()
            }

            MotionEvent.ACTION_CANCEL -> {
                v.isPressed = false
                cancelCallbacks()
                // If Android cancels a long-press after we replaced the primary, restore it.
                if (longPressTriggered && isLatestInput(replacementRevision)) {
                    dispatchKey(BACKSPACE_KEY)
                    dispatchKey(key)
                }
                resetPress()
            }
        }
        return true
    }

    private fun movedOutsideKey(v: View, event: MotionEvent): Boolean {
        val slop = ViewConfiguration.get(v.context).scaledTouchSlop.toFloat()
        val movedFar = kotlin.math.abs(event.x - downX) > slop ||
            kotlin.math.abs(event.y - downY) > slop
        val outsideBounds = event.x < -slop || event.x > v.width + slop ||
            event.y < -slop || event.y > v.height + slop
        return movedFar && outsideBounds
    }

    private fun cancelCallbacks() {
        isHolding = false
        handler.removeCallbacks(repeatRunnable)
        handler.removeCallbacks(longPressRunnable)
    }

    private fun resetPress() {
        primaryRevision = -1L
        replacementRevision = -1L
        longPressTriggered = false
    }
}

/**
 * Small custom vector set for IME controls. Drawing these locally avoids
 * platform-font glyph differences and keeps every icon optically centered.
 */
private class KeyboardIconView(
    context: Context,
    private val keyType: KeyType,
    shiftState: ShiftState,
    color: Int
) : View(context) {

    var shiftState: ShiftState = shiftState
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = dp(1.8f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        when (keyType) {
            KeyType.SHIFT -> drawShift(canvas, cx, cy)
            KeyType.BACKSPACE -> drawBackspace(canvas, cx, cy)
            KeyType.ENTER -> drawEnter(canvas, cx, cy)
            KeyType.EMOJI_TOGGLE -> drawEmoji(canvas, cx, cy)
            else -> Unit
        }
    }

    private fun drawShift(canvas: Canvas, cx: Float, cy: Float) {
        val path = Path().apply {
            moveTo(cx, cy - dp(8f))
            lineTo(cx - dp(7f), cy - dp(1f))
            lineTo(cx - dp(3f), cy - dp(1f))
            lineTo(cx - dp(3f), cy + dp(6f))
            lineTo(cx + dp(3f), cy + dp(6f))
            lineTo(cx + dp(3f), cy - dp(1f))
            lineTo(cx + dp(7f), cy - dp(1f))
            close()
        }
        if (shiftState == ShiftState.LOCKED) {
            canvas.drawPath(path, fillPaint)
            canvas.drawLine(cx - dp(4f), cy + dp(9f), cx + dp(4f), cy + dp(9f), strokePaint)
        } else {
            canvas.drawPath(path, strokePaint)
            if (shiftState == ShiftState.SINGLE) {
                canvas.drawCircle(cx, cy + dp(3f), dp(1.8f), fillPaint)
            }
        }
    }

    private fun drawBackspace(canvas: Canvas, cx: Float, cy: Float) {
        val body = Path().apply {
            moveTo(cx - dp(10f), cy)
            lineTo(cx - dp(5f), cy - dp(6f))
            lineTo(cx + dp(9f), cy - dp(6f))
            quadTo(cx + dp(11f), cy - dp(6f), cx + dp(11f), cy - dp(4f))
            lineTo(cx + dp(11f), cy + dp(4f))
            quadTo(cx + dp(11f), cy + dp(6f), cx + dp(9f), cy + dp(6f))
            lineTo(cx - dp(5f), cy + dp(6f))
            close()
        }
        canvas.drawPath(body, strokePaint)
        canvas.drawLine(cx, cy - dp(2.5f), cx + dp(5f), cy + dp(2.5f), strokePaint)
        canvas.drawLine(cx + dp(5f), cy - dp(2.5f), cx, cy + dp(2.5f), strokePaint)
    }

    private fun drawEnter(canvas: Canvas, cx: Float, cy: Float) {
        val path = Path().apply {
            moveTo(cx + dp(8f), cy - dp(7f))
            lineTo(cx + dp(8f), cy + dp(1f))
            quadTo(cx + dp(8f), cy + dp(4f), cx + dp(5f), cy + dp(4f))
            lineTo(cx - dp(6f), cy + dp(4f))
            moveTo(cx - dp(2f), cy)
            lineTo(cx - dp(7f), cy + dp(4f))
            lineTo(cx - dp(2f), cy + dp(8f))
        }
        canvas.drawPath(path, strokePaint)
    }

    private fun drawEmoji(canvas: Canvas, cx: Float, cy: Float) {
        canvas.drawCircle(cx, cy, dp(9f), strokePaint)
        canvas.drawCircle(cx - dp(3.2f), cy - dp(2.5f), dp(1.1f), fillPaint)
        canvas.drawCircle(cx + dp(3.2f), cy - dp(2.5f), dp(1.1f), fillPaint)
        canvas.drawArc(
            RectF(cx - dp(4.5f), cy - dp(0.5f), cx + dp(4.5f), cy + dp(5.5f)),
            15f,
            150f,
            false,
            strokePaint
        )
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
