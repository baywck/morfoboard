package com.morfoboard.app.settings

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.morfoboard.app.R
import com.morfoboard.app.auth.AuthManager
import com.morfoboard.app.auth.GoogleSignInManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var authManager: AuthManager
    private lateinit var settingsStore: MorfoboardSettingsStore
    private lateinit var googleSignInManager: GoogleSignInManager
    private lateinit var signInLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.navigationBarColor = getColor(R.color.background)
        window.statusBarColor = getColor(R.color.background)

        authManager = AuthManager(this)
        settingsStore = MorfoboardSettingsStore(this)
        googleSignInManager = GoogleSignInManager(this)

        signInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val signInResult = googleSignInManager.handleSignInResult(task)
            when (signInResult) {
                is GoogleSignInManager.SignInResult.Success -> {
                    authManager.saveSession(
                        idToken = signInResult.idToken,
                        email = signInResult.email,
                        name = signInResult.displayName
                    )
                    Toast.makeText(this, "Connected successfully", Toast.LENGTH_SHORT).show()
                    recreate()
                }
                is GoogleSignInManager.SignInResult.Failure -> {
                    Toast.makeText(this, signInResult.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        setContentView(buildLayout())
    }

    private fun buildLayout(): ScrollView {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.background))
            setPadding(dp(24), dp(48), dp(24), dp(48))

            // Header
            addView(TextView(this@SettingsActivity).apply {
                text = "MORFOBOARD"
                setTextColor(getColor(R.color.text_primary))
                textSize = 28f
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                letterSpacing = 0.1f
                setPadding(0, 0, 0, dp(4))
            })
            
            addView(TextView(this@SettingsActivity).apply {
                text = "Settings"
                setTextColor(getColor(R.color.text_secondary))
                textSize = 14f
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                setPadding(0, 0, 0, dp(32))
            })

            addView(buildSection("ACCOUNT") { addView(buildAccountSection()) })
            addView(buildSection("THEME") { addView(buildThemeSection()) })
            addView(buildSection("KEYBOARD SIZE") { addView(buildKeyboardSizeSection()) })
            addView(buildSection("ACCENT COLOR") { addView(buildColorSection()) })
            addView(buildSection("KEY SHAPE") { addView(buildKeyShapeSection()) })
            addView(buildSection("AI TARGET LANGUAGE") { addView(buildLanguageSection()) })
            addView(buildSection("AI WRITING TONE") { addView(buildToneSection()) })
            addView(buildSection("ABOUT") {
                addView(TextView(this@SettingsActivity).apply {
                    text = "Version 0.1.0-beta"
                    setTextColor(getColor(R.color.text_secondary))
                    textSize = 14f
                    setLineSpacing(0f, 1.4f)
                })
            })
        }
        
        return ScrollView(this).apply {
            addView(container)
            setBackgroundColor(getColor(R.color.background))
        }
    }

    private fun buildSection(title: String, content: LinearLayout.() -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(20)) }

            addView(TextView(this@SettingsActivity).apply {
                text = title
                setTextColor(getColor(R.color.text_secondary))
                textSize = 11f
                letterSpacing = 0.1f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setPadding(dp(4), 0, 0, dp(8))
            })

            val card = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                val bgDrawable = GradientDrawable().apply {
                    setColor(getColor(R.color.surface))
                    cornerRadius = dp(14).toFloat()
                }
                background = bgDrawable
                setPadding(dp(16), dp(16), dp(16), dp(16))
                content()
            }
            addView(card)
        }
    }

    private fun buildAccountSection(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            if (authManager.isSignedIn) {
                addView(TextView(this@SettingsActivity).apply {
                    text = authManager.userName ?: "User"
                    setTextColor(getColor(R.color.text_primary))
                    textSize = 18f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                })
                addView(TextView(this@SettingsActivity).apply {
                    text = authManager.userEmail ?: ""
                    setTextColor(getColor(R.color.text_secondary))
                    textSize = 14f
                    setPadding(0, dp(2), 0, dp(16))
                })
                addView(Button(this@SettingsActivity).apply {
                    text = "Disconnect"
                    setTextColor(getColor(R.color.error))
                    textSize = 13f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    isAllCaps = false
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#1AFF6B84"))
                        cornerRadius = dp(10).toFloat()
                    }
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44))
                    setOnClickListener {
                        googleSignInManager.signOut()
                        authManager.clearSession()
                        recreate()
                    }
                })
            } else {
                addView(TextView(this@SettingsActivity).apply {
                    text = "Not Connected"
                    setTextColor(getColor(R.color.text_secondary))
                    textSize = 15f
                    setPadding(0, 0, 0, dp(12))
                })
                addView(Button(this@SettingsActivity).apply {
                    text = "Connect with Google"
                    setTextColor(getColor(R.color.background))
                    textSize = 14f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    isAllCaps = false
                    background = GradientDrawable().apply {
                        setColor(getColor(R.color.text_primary))
                        cornerRadius = dp(10).toFloat()
                    }
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44))
                    setOnClickListener {
                        signInLauncher.launch(googleSignInManager.getSignInIntent())
                    }
                })
            }
        }
    }

    private fun buildKeyboardSizeSection(): LinearLayout {
        val sizes = listOf(
            "small" to "Small",
            "medium" to "Medium",
            "large" to "Large"
        )
        return buildChipGroup(sizes, settingsStore.keyboardSize) { selected ->
            settingsStore.keyboardSize = selected
        }
    }

    private fun buildThemeSection(): LinearLayout {
        val themes = listOf(
            "dark" to "Dark",
            "light" to "Light"
        )
        return buildChipGroup(themes, settingsStore.theme) { selected ->
            settingsStore.theme = selected
        }
    }

    private fun buildLanguageSection(): LinearLayout {
        val languages = listOf(
            "en" to "English",
            "id" to "Indonesian",
            "es" to "Spanish",
            "jv" to "Javanese"
        )
        return buildChipGroup(languages, settingsStore.targetLanguage) { selected ->
            settingsStore.targetLanguage = selected
        }
    }
    
    private fun buildToneSection(): LinearLayout {
        val tones = listOf(
            "natural" to "Natural",
            "professional" to "Professional",
            "casual" to "Casual"
        )
        return buildChipGroup(tones, settingsStore.tone) { selected ->
            settingsStore.tone = selected
        }
    }

    /**
     * Build color palette selector with colored circle swatches.
     */
    private fun buildColorSection(): LinearLayout {
        val palettes = settingsStore.accentPalettes
        val currentColor = settingsStore.accentColor
        val swatchViews = mutableListOf<View>()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        for (palette in palettes) {
            val isSelected = palette.name == currentColor
            val swatchSize = dp(36)
            val swatch = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(swatchSize, swatchSize).apply {
                    setMargins(0, 0, dp(10), 0)
                }
                background = createSwatchDrawable(palette.keyBg, isSelected)
                setOnClickListener {
                    settingsStore.accentColor = palette.name
                    // Update all swatches
                    for ((i, sv) in swatchViews.withIndex()) {
                        sv.background = createSwatchDrawable(
                            palettes[i].keyBg,
                            palettes[i].name == palette.name
                        )
                    }
                }
            }
            swatchViews.add(swatch)
            container.addView(swatch)
        }

        // Wrap in a horizontal scroll
        val scrollView = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(container)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(scrollView)
        }
    }

    private fun createSwatchDrawable(color: Int, isSelected: Boolean): android.graphics.drawable.LayerDrawable {
        val circle = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        
        if (isSelected) {
            val ring = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(android.graphics.Color.TRANSPARENT)
                setStroke(dp(2), android.graphics.Color.WHITE)
            }
            return android.graphics.drawable.LayerDrawable(arrayOf(circle, ring))
        }
        
        return android.graphics.drawable.LayerDrawable(arrayOf(circle))
    }

    private fun buildKeyShapeSection(): LinearLayout {
        val shapes = listOf(
            "rounded" to "Rounded",
            "semi" to "Semi",
            "square" to "Square"
        )
        return buildChipGroup(shapes, settingsStore.keyShape) { selected ->
            settingsStore.keyShape = selected
        }
    }

    /**
     * Creates a modern chip/pill selector group.
     * Only one chip can be selected at a time — tapping a chip immediately
     * updates the visual state and persists the selection.
     */
    private fun buildChipGroup(
        options: List<Pair<String, String>>,
        currentValue: String,
        onSelect: (String) -> Unit
    ): LinearLayout {
        val chipViews = mutableListOf<TextView>()
        
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Use a FlowLayout-like approach with wrapping
        val flowContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Create rows of chips (wrap if needed)
        var currentRow = createChipRow()
        var currentRowWidth = 0
        val maxWidth = resources.displayMetrics.widthPixels - dp(80) // account for padding

        for ((code, label) in options) {
            val chip = createChip(label, code == currentValue)
            chipViews.add(chip)
            
            // Estimate chip width
            val estimatedWidth = dp(16 + 24) + (label.length * dp(8))
            
            if (currentRowWidth + estimatedWidth > maxWidth && currentRowWidth > 0) {
                flowContainer.addView(currentRow)
                currentRow = createChipRow()
                currentRowWidth = 0
            }
            
            chip.setOnClickListener {
                // Update all chips visually
                for ((i, chipView) in chipViews.withIndex()) {
                    val isSelected = options[i].first == code
                    updateChipStyle(chipView, isSelected)
                }
                onSelect(code)
            }
            
            currentRow.addView(chip)
            currentRowWidth += estimatedWidth
        }
        
        flowContainer.addView(currentRow)
        return flowContainer
    }

    private fun createChipRow(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(6)) }
        }
    }

    private fun createChip(label: String, isSelected: Boolean): TextView {
        return TextView(this).apply {
            text = label
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(10), dp(16), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, dp(8), 0) }
            
            updateChipStyle(this, isSelected)
        }
    }

    private fun updateChipStyle(chip: TextView, isSelected: Boolean) {
        if (isSelected) {
            chip.background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A5C46"))
                cornerRadius = dp(20).toFloat()
            }
            chip.setTextColor(Color.parseColor("#7DCDB3"))
        } else {
            chip.background = GradientDrawable().apply {
                setColor(Color.parseColor("#1C1F22"))
                cornerRadius = dp(20).toFloat()
            }
            chip.setTextColor(getColor(R.color.text_secondary))
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
