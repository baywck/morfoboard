package com.morfoboard.app.ime

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.morfoboard.app.R

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
    private val onSettings: () -> Unit
) {

    val view: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        setPadding(dp(12), dp(8), dp(12), dp(8)) // Balanced padding
        gravity = Gravity.CENTER_VERTICAL
        visibility = View.VISIBLE
    }

    private lateinit var translateBtn: LinearLayout
    private lateinit var fixTextBtn: LinearLayout
    private lateinit var loginBtn: LinearLayout
    private lateinit var settingsBtn: ImageButton
    private lateinit var loadingIndicator: ProgressBar

    private var hasText = false
    private var isLoading = false

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

        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, if (isLoggedIn()) 1f else 0f)
        }
        view.addView(spacer)

        // Loading indicator
        loadingIndicator = ProgressBar(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(8) }
            visibility = View.GONE
            indeterminateTintList = android.content.res.ColorStateList.valueOf(context.getColor(R.color.loading))
        }
        view.addView(loadingIndicator)

        // Settings button - Significantly larger for better visibility
        settingsBtn = ImageButton(context).apply {
            setImageResource(R.drawable.ic_morfoboard_sliders)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setColorFilter(context.getColor(R.color.text_secondary))
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)) // Increased from 25 to 36
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
        return LinearLayout(view.context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundResource(if (primary) R.drawable.login_btn_bg else R.drawable.action_btn_bg)
            setPadding(dp(10), 0, dp(10), 0)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(28)
            ).apply { marginEnd = dp(8) }

            val iconView = TextView(view.context).apply {
                text = icon
                setTextColor(view.context.getColor(if (primary) R.color.login_btn_text else R.color.accent_blue))
                textSize = if (primary) 13f else 12f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
            }
            val labelView = TextView(view.context).apply {
                text = label
                setTextColor(view.context.getColor(if (primary) R.color.login_btn_text else R.color.key_text_bright))
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

    private fun updateVisibility() {
        view.visibility = View.VISIBLE
        if (isLoggedIn() && ::translateBtn.isInitialized && ::fixTextBtn.isInitialized) {
            val enabled = hasText && !isLoading
            translateBtn.isEnabled = enabled
            fixTextBtn.isEnabled = enabled
            translateBtn.alpha = if (enabled) 1f else 0.45f
            fixTextBtn.alpha = if (enabled) 1f else 0.45f
        }
        if (::loadingIndicator.isInitialized) {
            loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
