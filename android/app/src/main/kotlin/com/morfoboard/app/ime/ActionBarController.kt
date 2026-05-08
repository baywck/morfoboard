package com.morfoboard.app.ime

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.morfoboard.app.R
import com.morfoboard.app.settings.MorfoboardSettingsStore

/**
 * Integrated AI toolbar above Morfoboard keyboard.
 * Logged in: Translate, Fix Text, Settings.
 * Logged out: Login with Google, Settings.
 */
class ActionBarController(
    private val context: Context,
    private val isLoggedIn: () -> Boolean,
    private val onLogin: () -> Unit,
    private val onTranslate: () -> Unit,
    private val onFixText: () -> Unit,
    private val onVoiceInput: () -> Unit,
    private val onSettings: () -> Unit
) {

    private val settingsStore = MorfoboardSettingsStore(context)

    val view: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        setPadding(dp(12), dp(8), dp(12), dp(8))
        gravity = Gravity.CENTER_VERTICAL
        visibility = View.VISIBLE
    }

    private lateinit var translateBtn: LinearLayout
    private lateinit var fixTextBtn: LinearLayout
    private lateinit var loginBtn: LinearLayout
    private lateinit var voiceBtn: ImageButton
    private lateinit var settingsBtn: ImageButton
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var listeningIndicator: LinearLayout
    private var pulseAnimator: ValueAnimator? = null

    private var hasText = false
    private var isLoading = false
    private var isListening = false

    init {
        rebuild()
    }

    private fun rebuild() {
        view.removeAllViews()

        if (isLoggedIn()) {
            translateBtn = createActionButton("✦", "Translate", false) { onTranslate() }
            fixTextBtn = createActionButton("✧", "Fix Text", false) { onFixText() }
            view.addView(translateBtn)
            view.addView(fixTextBtn)
        } else {
            loginBtn = createActionButton("G", "Login with Google", true) { onLogin() }.apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f).apply { marginEnd = dp(8) }
            }
            view.addView(loginBtn)
        }

        // Listening Indicator — clean design with pulsing dot
        listeningIndicator = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(dp(8), 0, 0, 0)
            
            // Pulsing dot
            val dot = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply {
                    marginEnd = dp(8)
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(android.graphics.Color.parseColor("#FF6B6B"))
                }
            }
            addView(dot)
            
            // Text label
            val label = TextView(context).apply {
                text = "Listening"
                setTextColor(android.graphics.Color.parseColor("#FF6B6B"))
                textSize = 13f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
            addView(label)
        }
        view.addView(listeningIndicator)

        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }
        view.addView(spacer)

        // Loading indicator
        loadingIndicator = ProgressBar(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(8) }
            visibility = View.GONE
            indeterminateTintList = android.content.res.ColorStateList.valueOf(context.getColor(R.color.loading))
        }
        view.addView(loadingIndicator)

        // Voice button
        voiceBtn = ImageButton(context).apply {
            setImageResource(R.drawable.ic_morfoboard_mic)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setColorFilter(settingsStore.currentThemeColors.textSecondary)
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(4) }
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            elevation = 0f
            stateListAnimator = null
            setOnClickListener { onVoiceInput() }
            contentDescription = "Voice Input"
        }
        view.addView(voiceBtn)

        // Settings button
        settingsBtn = ImageButton(context).apply {
            setImageResource(R.drawable.ic_morfoboard_sliders)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setColorFilter(settingsStore.currentThemeColors.textSecondary)
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            elevation = 0f
            stateListAnimator = null
            setOnClickListener { onSettings() }
            contentDescription = "Keyboard settings"
        }
        view.addView(settingsBtn)
        updateVisibility()
    }

    private fun createActionButton(
        icon: String,
        label: String,
        primary: Boolean,
        onClick: () -> Unit
    ): LinearLayout {
        val themeColors = settingsStore.currentThemeColors
        
        return LinearLayout(view.context).apply {
            orientation = LinearLayout.HORIZONTAL
            
            // Theme-aware button background
            val bgDrawable = android.graphics.drawable.GradientDrawable().apply {
                if (primary) {
                    setColor(themeColors.textPrimary)
                } else {
                    setColor(if (settingsStore.isDarkTheme) 0xFF181B1E.toInt() else 0xFFE0E4E8.toInt())
                }
                cornerRadius = dp(6).toFloat()
            }
            background = bgDrawable
            setPadding(dp(10), 0, dp(10), 0)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(28)
            ).apply { marginEnd = dp(8) }

            val iconView = TextView(view.context).apply {
                text = icon
                setTextColor(if (primary) themeColors.keyboardBg else themeColors.textSecondary)
                textSize = if (primary) 13f else 12f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
            }
            val labelView = TextView(view.context).apply {
                text = label
                setTextColor(if (primary) themeColors.keyboardBg else themeColors.textPrimary)
                textSize = 11f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setPadding(dp(5), 0, 0, 0)
                gravity = Gravity.CENTER
            }

            addView(iconView)
            addView(labelView)
            setOnClickListener { onClick() }
        }
    }

    fun refreshAuthState() = rebuild()

    fun setTextState(hasText: Boolean) {
        this.hasText = hasText
        updateVisibility()
    }

    fun setLoading(loading: Boolean) {
        isLoading = loading
        updateVisibility()
    }

    fun setListening(listening: Boolean) {
        isListening = listening
        if (::voiceBtn.isInitialized) {
            val color = if (listening) android.graphics.Color.parseColor("#FF6B6B") 
                        else settingsStore.currentThemeColors.textSecondary
            voiceBtn.setColorFilter(color)
        }
        
        // Pulse animation for the recording dot
        if (listening) {
            startPulseAnimation()
        } else {
            stopPulseAnimation()
        }
        
        updateVisibility()
    }
    
    private fun startPulseAnimation() {
        val dot = if (::listeningIndicator.isInitialized && listeningIndicator.childCount > 0) {
            listeningIndicator.getChildAt(0)
        } else null
        
        dot?.let {
            pulseAnimator = ValueAnimator.ofFloat(1f, 0.3f, 1f).apply {
                duration = 1200
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { animator ->
                    it.alpha = animator.animatedValue as Float
                }
                start()
            }
        }
    }
    
    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        
        if (::listeningIndicator.isInitialized && listeningIndicator.childCount > 0) {
            listeningIndicator.getChildAt(0).alpha = 1f
        }
    }

    private fun updateVisibility() {
        view.visibility = View.VISIBLE
        
        if (::listeningIndicator.isInitialized) {
            listeningIndicator.visibility = if (isListening) View.VISIBLE else View.GONE
        }

        if (isLoggedIn() && ::translateBtn.isInitialized && ::fixTextBtn.isInitialized) {
            val enabled = hasText && !isLoading
            translateBtn.isEnabled = enabled
            fixTextBtn.isEnabled = enabled
            translateBtn.alpha = if (enabled) 1f else 0.45f
            fixTextBtn.alpha = if (enabled) 1f else 0.45f
            
            val btnVisibility = if (isListening) View.GONE else View.VISIBLE
            translateBtn.visibility = btnVisibility
            fixTextBtn.visibility = btnVisibility
        } else if (::loginBtn.isInitialized) {
            loginBtn.visibility = if (isListening) View.GONE else View.VISIBLE
        }
        
        if (::loadingIndicator.isInitialized) {
            loadingIndicator.visibility = if (isLoading && !isListening) View.VISIBLE else View.GONE
        }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
