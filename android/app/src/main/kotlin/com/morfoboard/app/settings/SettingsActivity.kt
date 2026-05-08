package com.morfoboard.app.settings

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
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

            addView(TextView(this@SettingsActivity).apply {
                text = "MORFOBOARD"
                setTextColor(getColor(R.color.text_primary))
                textSize = 28f
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                letterSpacing = 0.1f
                setPadding(0, 0, 0, dp(4))
            })
            
            addView(TextView(this@SettingsActivity).apply {
                text = "Neural Keyboard Settings"
                setTextColor(getColor(R.color.accent_blue))
                textSize = 14f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setPadding(0, 0, 0, dp(32))
            })

            addView(buildSection("ACCOUNT") { addView(buildAccountSection()) })
            addView(buildSection("AI TARGET LANGUAGE") { addView(buildLanguageSection()) })
            addView(buildSection("AI WRITING TONE") { addView(buildToneSection()) })
            addView(buildSection("SYSTEM") {
                addView(TextView(this@SettingsActivity).apply {
                    text = "Version 0.1.0-beta\nNeural Engine Active"
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
            ).apply { setMargins(0, 0, 0, dp(24)) }

            addView(TextView(this@SettingsActivity).apply {
                text = title
                setTextColor(getColor(R.color.text_secondary))
                textSize = 12f
                letterSpacing = 0.08f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setPadding(dp(4), 0, 0, dp(8))
            })

            val card = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                val bgDrawable = GradientDrawable().apply {
                    setColor(getColor(R.color.surface))
                    cornerRadius = dp(16).toFloat()
                    setStroke(dp(1), Color.parseColor("#2A2D31")) 
                }
                background = bgDrawable
                setPadding(dp(20), dp(20), dp(20), dp(20))
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
                    text = "DISCONNECT"
                    setTextColor(getColor(R.color.error))
                    textSize = 12f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#1AFFFFFF"))
                        cornerRadius = dp(8).toFloat()
                    }
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48))
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
                    textSize = 16f
                    setPadding(0, 0, 0, dp(16))
                })
                addView(Button(this@SettingsActivity).apply {
                    text = "CONNECT WITH GOOGLE"
                    setTextColor(getColor(R.color.background))
                    textSize = 13f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    background = GradientDrawable().apply {
                        setColor(getColor(R.color.text_primary))
                        cornerRadius = dp(8).toFloat()
                    }
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48))
                    setOnClickListener {
                        signInLauncher.launch(googleSignInManager.getSignInIntent())
                    }
                })
            }
        }
    }

    private fun buildLanguageSection(): LinearLayout {
        val languages = listOf(
            "en" to "English",
            "id" to "Indonesian",
            "es" to "Spanish",
            "jv" to "Javanese"
        )
        val currentTarget = settingsStore.targetLanguage

        val radioGroup = RadioGroup(this).apply { orientation = LinearLayout.VERTICAL }
        for ((code, name) in languages) {
            radioGroup.addView(RadioButton(this).apply {
                text = name
                setTextColor(getColor(R.color.text_primary))
                textSize = 16f
                setPadding(dp(12), dp(12), 0, dp(12))
                isChecked = code == currentTarget
                buttonTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.accent_blue))
                setOnClickListener { settingsStore.targetLanguage = code }
            })
        }
        return LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(radioGroup) }
    }
    
    private fun buildToneSection(): LinearLayout {
        val tones = listOf(
            "natural" to "Natural",
            "professional" to "Professional",
            "casual" to "Casual"
        )
        val currentTone = settingsStore.tone

        val radioGroup = RadioGroup(this).apply { orientation = LinearLayout.VERTICAL }
        for ((code, name) in tones) {
            radioGroup.addView(RadioButton(this).apply {
                text = name
                setTextColor(getColor(R.color.text_primary))
                textSize = 16f
                setPadding(dp(12), dp(12), 0, dp(12))
                isChecked = code == currentTone
                buttonTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.accent_blue))
                setOnClickListener { settingsStore.tone = code }
            })
        }
        return LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(radioGroup) }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
