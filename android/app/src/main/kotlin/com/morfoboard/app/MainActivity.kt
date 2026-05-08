package com.morfoboard.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import com.morfoboard.app.auth.AuthManager
import com.morfoboard.app.auth.GoogleSignInManager
import com.morfoboard.app.settings.SettingsActivity

/**
 * Main launcher activity.
 * Guides user to enable Morfoboard keyboard.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var authManager: AuthManager
    private lateinit var googleSignInManager: GoogleSignInManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authManager = AuthManager(this)
        googleSignInManager = GoogleSignInManager(this)
        
        // Request microphone permission if not granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
        }
        
        setContentView(buildLayout())
    }

    override fun onResume() {
        super.onResume()
        setContentView(buildLayout()) // Refresh state
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == GoogleSignInManager.RC_SIGN_IN) {
            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(data)
            val result = googleSignInManager.handleSignInResult(task)
            
            if (result is GoogleSignInManager.SignInResult.Success) {
                authManager.saveSession(result.idToken, result.email, result.displayName)
                Toast.makeText(this, "Welcome, ${result.displayName}!", Toast.LENGTH_SHORT).show()
                setContentView(buildLayout())
            } else if (result is GoogleSignInManager.SignInResult.Failure) {
                Toast.makeText(this, "Sign-in failed: ${result.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun buildLayout(): LinearLayout {
        val isKeyboardEnabled = isKeyboardEnabled()
        val isKeyboardSelected = isKeyboardSelected()
        val isSignedIn = authManager.isSignedIn

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(com.morfoboard.app.R.color.background))
            setPadding(dp(32), dp(80), dp(32), dp(32))
            gravity = android.view.Gravity.CENTER_HORIZONTAL

            // App icon/name
            addView(TextView(this@MainActivity).apply {
                text = "⌨️"
                textSize = 64f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 0, 0, dp(16))
            })

            addView(TextView(this@MainActivity).apply {
                text = "Morfoboard"
                setTextColor(getColor(com.morfoboard.app.R.color.text_primary))
                textSize = 28f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 0, 0, dp(8))
            })

            addView(TextView(this@MainActivity).apply {
                text = "AI-Powered Keyboard"
                setTextColor(getColor(com.morfoboard.app.R.color.text_secondary))
                textSize = 16f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 0, 0, dp(48))
            })

            // Status card
            addView(buildStatusCard(isKeyboardEnabled, isKeyboardSelected, isSignedIn))

            // Auth button
            if (!isSignedIn) {
                addView(buildButton("Sign in with Google", true) {
                    startActivityForResult(googleSignInManager.getSignInIntent(), GoogleSignInManager.RC_SIGN_IN)
                })
            } else {
                // Setup buttons
                if (!isKeyboardEnabled) {
                    addView(buildButton("Enable Morfoboard", true) {
                        startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    })
                } else if (!isKeyboardSelected) {
                    addView(buildButton("Switch to Morfoboard", true) {
                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showInputMethodPicker()
                    })
                } else {
                    addView(buildButton("Open Settings", false) {
                        startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                    })
                    
                    addView(buildButton("Sign Out", false) {
                        googleSignInManager.signOut()
                        authManager.clearSession()
                        setContentView(buildLayout())
                    })
                }
            }

            // Instructions
            addView(TextView(this@MainActivity).apply {
                text = if (!isSignedIn) {
                    "Sign in to start using AI features"
                } else if (!isKeyboardEnabled) {
                    "Step 1: Enable Morfoboard in keyboard settings\nStep 2: Select Morfoboard as your keyboard"
                } else if (!isKeyboardSelected) {
                    "Select Morfoboard from the keyboard picker"
                } else {
                    "Morfoboard is ready! Open any app to start typing.\n\nUse ✦ Translate to translate text\nUse ✧ Fix Text to correct grammar"
                }
                setTextColor(getColor(com.morfoboard.app.R.color.text_secondary))
                textSize = 14f
                setPadding(0, dp(32), 0, 0)
                gravity = android.view.Gravity.CENTER
            })
        }
    }

    private fun buildStatusCard(enabled: Boolean, selected: Boolean, signedIn: Boolean): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(com.morfoboard.app.R.drawable.bottom_sheet_bg)
            setPadding(dp(20), dp(20), dp(20), dp(20))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(24)) }

            // Auth status
            addView(buildStatusRow("Signed In", signedIn))

            // Keyboard enabled status
            addView(buildStatusRow("Keyboard Enabled", enabled))

            // Keyboard selected status
            addView(buildStatusRow("Keyboard Active", selected))
        }
    }

    private fun buildStatusRow(label: String, isActive: Boolean): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, dp(8))
            gravity = android.view.Gravity.CENTER_VERTICAL

            addView(TextView(this@MainActivity).apply {
                text = if (isActive) "✅" else "⬜"
                textSize = 18f
                setPadding(0, 0, dp(12), 0)
            })

            addView(TextView(this@MainActivity).apply {
                text = label
                setTextColor(getColor(com.morfoboard.app.R.color.text_primary))
                textSize = 16f
            })
        }
    }

    private fun buildButton(text: String, isPrimary: Boolean, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(12), 0, 0) }

            if (isPrimary) {
                setBackgroundResource(com.morfoboard.app.R.drawable.btn_primary)
                setTextColor(getColor(com.morfoboard.app.R.color.key_text))
            } else {
                setBackgroundResource(com.morfoboard.app.R.drawable.btn_outline)
                setTextColor(getColor(com.morfoboard.app.R.color.action_btn))
            }
            setOnClickListener { onClick() }
        }
    }

    private fun isKeyboardEnabled(): Boolean {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabledMethods = imm.enabledInputMethodList
        return enabledMethods.any { it.packageName == packageName }
    }

    private fun isKeyboardSelected(): Boolean {
        val currentKeyboard = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        return currentKeyboard?.contains(packageName) == true
    }

    private fun dp(value: Int): Int {
        val density = resources.displayMetrics.density
        return (value * density).toInt()
    }
}
