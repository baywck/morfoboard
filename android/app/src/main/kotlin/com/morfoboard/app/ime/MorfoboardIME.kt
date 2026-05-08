package com.morfoboard.app.ime

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import com.morfoboard.app.R
import com.morfoboard.app.ai.AIAction
import com.morfoboard.app.ai.AIClient
import com.morfoboard.app.ai.AIException
import com.morfoboard.app.auth.AuthManager
import com.morfoboard.app.network.ConnectivityMonitor
import com.morfoboard.app.settings.MorfoboardSettingsStore
import com.morfoboard.app.settings.SettingsActivity
import kotlinx.coroutines.*

/**
 * Morfoboard Input Method Editor.
 * Phase 1: Direct AI connection, no auth required.
 */
class MorfoboardIME : InputMethodService() {

    companion object {
        private const val TAG = "MorfoboardIME"
        // Production server IP
        private const val DEFAULT_BASE_URL = "http://43.156.68.104:8080"
    }

    private lateinit var rootLayout: FrameLayout
    private lateinit var keyboardView: KeyboardView
    private lateinit var actionBarCtrl: ActionBarController
    private lateinit var bottomSheetPresenter: BottomSheetPresenter
    private lateinit var settingsStore: MorfoboardSettingsStore
    private lateinit var authManager: AuthManager
    private lateinit var aiClient: AIClient
    private lateinit var connectivityMonitor: ConnectivityMonitor

    private var shiftState = ShiftState.OFF
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ─────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        settingsStore = MorfoboardSettingsStore(this)
        authManager = AuthManager(this)
        aiClient = AIClient(
            baseUrl = DEFAULT_BASE_URL,
            tokenProvider = authManager.getTokenProvider()
        )
        connectivityMonitor = ConnectivityMonitor(this)
        connectivityMonitor.start()
    }

    override fun onCreateInputView(): View {
        Log.d(TAG, "onCreateInputView")
        
        // Sync navigation bar color with keyboard background
        window?.window?.navigationBarColor = getColor(R.color.keyboard_bg)

        rootLayout = FrameLayout(this)

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Action bar
        actionBarCtrl = ActionBarController(
            context = this,
            isLoggedIn = { authManager.isSignedIn },
            onLogin = { handleSettings() },
            onTranslate = { handleTranslate() },
            onFixText = { handleFixText() },
            onSettings = { handleSettings() }
        )
        mainLayout.addView(actionBarCtrl.view)

        // Keyboard
        keyboardView = KeyboardView(this) { key -> handleKeyPress(key) }
        mainLayout.addView(keyboardView)

        rootLayout.addView(mainLayout)

        // Bottom sheet presenter
        bottomSheetPresenter = BottomSheetPresenter(this, rootLayout)

        Log.d(TAG, "onCreateInputView complete")
        return rootLayout
    }

    override fun onDestroy() {
        super.onDestroy()
        connectivityMonitor.stop()
        scope.cancel()
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        if (::actionBarCtrl.isInitialized) {
            updateActionBarState()
        }
        updateAutoCaps()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEndInt: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEndInt, candidatesStart, candidatesEnd)
        if (::actionBarCtrl.isInitialized) {
            updateActionBarState()
        }
        updateAutoCaps()
    }

    private fun updateAutoCaps() {
        if (shiftState == ShiftState.LOCKED) return
        val ic = currentInputConnection ?: return
        val textBefore = ic.getTextBeforeCursor(3, 0) ?: ""
        
        // Auto-caps if empty (start of field), new line, or after punctuation and space
        val shouldCaps = textBefore.isEmpty() || 
                         textBefore.endsWith("\n") || 
                         textBefore.matches(Regex(".*[.?!]\\s+$"))
                         
        val newState = if (shouldCaps) ShiftState.SINGLE else ShiftState.OFF
        if (shiftState != newState) {
            shiftState = newState
            if (::keyboardView.isInitialized) {
                keyboardView.updateShiftState(shiftState)
            }
        }
    }

    // ─────────────────────────────────────────────
    // Key Handling
    // ─────────────────────────────────────────────

    private fun handleKeyPress(key: KeyDef) {
        val ic = currentInputConnection ?: return

        when (key.keyType) {
            KeyType.CHARACTER -> {
                val char = if (shiftState != ShiftState.OFF) {
                    key.label.uppercase()
                } else {
                    key.label.lowercase()
                }
                ic.commitText(char, 1)

                if (shiftState == ShiftState.SINGLE) {
                    shiftState = ShiftState.OFF
                    keyboardView.updateShiftState(shiftState)
                }
            }

            KeyType.SHIFT -> {
                shiftState = when (shiftState) {
                    ShiftState.OFF -> ShiftState.SINGLE
                    ShiftState.SINGLE -> ShiftState.LOCKED
                    ShiftState.LOCKED -> ShiftState.OFF
                }
                keyboardView.updateShiftState(shiftState)
            }

            KeyType.BACKSPACE -> {
                val selected = ic.getSelectedText(0)
                if (selected.isNullOrEmpty()) {
                    ic.deleteSurroundingText(1, 0)
                } else {
                    ic.commitText("", 0)
                }
            }

            KeyType.ENTER -> {
                val editorInfo = currentInputEditorInfo
                val action = editorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
                    ?: EditorInfo.IME_ACTION_UNSPECIFIED

                if (action != EditorInfo.IME_ACTION_UNSPECIFIED &&
                    action != EditorInfo.IME_ACTION_NONE
                ) {
                    ic.performEditorAction(action)
                } else {
                    ic.commitText("\n", 1)
                }
            }

            KeyType.SPACE -> {
                ic.commitText(" ", 1)
            }

            KeyType.SYMBOL_TOGGLE -> {
                keyboardView.toggleSymbolMode()
            }

            KeyType.SYMBOL_PAGE_TOGGLE -> {
                keyboardView.toggleSymbolPage()
            }

            KeyType.LANGUAGE -> {
                Log.d(TAG, "Language key pressed")
            }

            KeyType.ACTION_AI -> {
                Toast.makeText(this, "Voice input coming soon", Toast.LENGTH_SHORT).show()
            }
        }

        updateActionBarState()
    }

    // ─────────────────────────────────────────────
    // AI Actions (Phase 1: No auth required)
    // ─────────────────────────────────────────────

    private fun handleTranslate() {
        val text = getInputText()
        if (text.isBlank()) {
            Toast.makeText(this, "Type something first!", Toast.LENGTH_SHORT).show()
            return
        }

        if (!connectivityMonitor.isConnected.value) {
            Toast.makeText(this, getString(R.string.ai_unavailable), Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "Translate: ${text.take(50)}...")
        actionBarCtrl.setLoading(true)

        scope.launch {
            try {
                val result = aiClient.process(
                    action = AIAction.TRANSLATE,
                    text = text,
                    targetLanguage = settingsStore.targetLanguage,
                    tone = settingsStore.tone
                )

                result.onSuccess { response ->
                    actionBarCtrl.setLoading(false)
                    if (response.success && response.result != null) {
                        val targetLang = settingsStore.targetLanguage
                        val metadata = "to $targetLang | ${settingsStore.tone}"
                        bottomSheetPresenter.show(
                            title = "Translation",
                            originalText = text,
                            resultText = response.result,
                            metadata = metadata,
                            onReplace = { replaceText -> replaceInputText(replaceText) },
                            onDismiss = {}
                        )
                    }
                }.onFailure { error ->
                    handleAIError(error)
                }
            } catch (e: Exception) {
                handleAIError(e)
            }
        }
    }

    private fun handleFixText() {
        val text = getInputText()
        if (text.isBlank()) {
            Toast.makeText(this, "Type something first!", Toast.LENGTH_SHORT).show()
            return
        }

        if (!connectivityMonitor.isConnected.value) {
            Toast.makeText(this, getString(R.string.ai_unavailable), Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "Fix Text: ${text.take(50)}...")
        actionBarCtrl.setLoading(true)

        scope.launch {
            try {
                val result = aiClient.process(
                    action = AIAction.FIX_TEXT,
                    text = text
                )

                result.onSuccess { response ->
                    actionBarCtrl.setLoading(false)
                    if (response.success && response.result != null) {
                        bottomSheetPresenter.show(
                            title = "Fixed Text",
                            originalText = text,
                            resultText = response.result,
                            metadata = "Grammar and typos corrected",
                            onReplace = { replaceText -> replaceInputText(replaceText) },
                            onDismiss = {}
                        )
                    }
                }.onFailure { error ->
                    handleAIError(error)
                }
            } catch (e: Exception) {
                handleAIError(e)
            }
        }
    }

    private fun handleSettings() {
        val intent = Intent(this, SettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun handleLoginTest() {
        if (!authManager.isSignedIn) {
            Toast.makeText(this, "Please sign in first in the app!", Toast.LENGTH_SHORT).show()
            return
        }

        actionBarCtrl.setLoading(true)
        scope.launch {
            try {
                val result = aiClient.process(
                    action = "login_test",
                    text = "Testing login flow"
                )
                result.onSuccess { response ->
                    actionBarCtrl.setLoading(false)
                    if (response.success && response.result != null) {
                        // Apply it to the textarea
                        val ic = currentInputConnection
                        ic?.commitText(response.result, 1)
                    }
                }.onFailure { error ->
                    handleAIError(error)
                }
            } catch (e: Exception) {
                handleAIError(e)
            }
        }
    }

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

    private fun getInputText(): String {
        val ic = currentInputConnection ?: return ""
        val text = ic.getExtractedText(
            android.view.inputmethod.ExtractedTextRequest(), 0
        )
        return text?.text?.toString() ?: ""
    }

    private fun replaceInputText(newText: String) {
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        val allText = getInputText()
        if (allText.isNotEmpty()) {
            ic.setSelection(0, allText.length)
        }
        ic.commitText(newText, 1)
        ic.endBatchEdit()
    }

    private fun updateActionBarState() {
        val text = getInputText()
        actionBarCtrl.setTextState(text.isNotBlank())
    }

    private fun handleAIError(error: Throwable) {
        actionBarCtrl.setLoading(false)
        val message = when (error) {
            is AIException.NetworkError -> getString(R.string.ai_unavailable)
            is AIException.Timeout -> getString(R.string.ai_timeout)
            is AIException.AiUnavailable -> getString(R.string.ai_unavailable)
            else -> getString(R.string.ai_error)
        }
        Log.e(TAG, "AI error: ${error.message}")
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
