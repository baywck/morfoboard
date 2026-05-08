package com.morfoboard.app.ime

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.morfoboard.app.MainActivity
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

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var speechIntent: Intent? = null

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
        
        setupSpeechRecognizer()
    }

    private fun setupSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID") // Default to ID, can be made dynamic
            }
            
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    // Play a soft beep when ready to record
                    val am = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                    am?.playSoundEffect(android.media.AudioManager.FX_KEYPRESS_STANDARD, 1.0f)
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    setListeningState(false)
                    // Play a soft beep when recording stops
                    val am = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                    am?.playSoundEffect(android.media.AudioManager.FX_KEYPRESS_RETURN, 1.0f)
                }
                override fun onError(error: Int) {
                    Log.e(TAG, "Speech recognition error: $error")
                    setListeningState(false)
                    val msg = when(error) {
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                        else -> "Voice recognition failed"
                    }
                    if (error != SpeechRecognizer.ERROR_NO_MATCH) {
                        Toast.makeText(this@MorfoboardIME, msg, Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onResults(results: Bundle?) {
                    setListeningState(false)
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0] + " "
                        currentInputConnection?.commitText(text, 1)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    override fun onCreateInputView(): View {
        Log.d(TAG, "onCreateInputView")
        
        // Sync navigation bar color with keyboard theme
        val themeColors = settingsStore.currentThemeColors
        window?.window?.navigationBarColor = themeColors.keyboardBg

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
            onVoiceInput = { handleVoiceInput() },
            onSettings = { handleSettings() }
        )
        mainLayout.addView(actionBarCtrl.view)

        // Keyboard with adaptive height
        keyboardView = KeyboardView(this) { key -> handleKeyPress(key) }
        val keyboardHeight = calculateKeyboardHeight()
        keyboardView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            keyboardHeight
        )
        mainLayout.addView(keyboardView)

        rootLayout.addView(mainLayout)
        
        // Apply window insets to add bottom padding for devices where the system
        // IME navigation overlaps with our keyboard (e.g. Vivo, some Samsung)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val bottomInset = maxOf(systemBars.bottom, navBars.bottom)
            
            // Only apply padding if the system is actually overlapping our view
            mainLayout.setPadding(0, 0, 0, bottomInset)
            
            Log.d(TAG, "WindowInsets applied - bottom: ${bottomInset}px")
            WindowInsetsCompat.CONSUMED
        }

        // Bottom sheet presenter
        bottomSheetPresenter = BottomSheetPresenter(this, rootLayout)

        Log.d(TAG, "onCreateInputView complete - keyboard: ${keyboardHeight}px")
        return rootLayout
    }

    /**
     * Calculate keyboard height based on user's size preset.
     */
    private fun calculateKeyboardHeight(): Int {
        val screenHeight = resources.displayMetrics.heightPixels
        val ratio = settingsStore.keyboardHeightRatio
        val targetHeight = (screenHeight * ratio).toInt()
        
        val density = resources.displayMetrics.density
        val minPx = (180 * density).toInt()
        val maxPx = (300 * density).toInt()
        
        return targetHeight.coerceIn(minPx, maxPx)
    }

    override fun onDestroy() {
        super.onDestroy()
        connectivityMonitor.stop()
        scope.cancel()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Re-apply keyboard height and re-render keys every time keyboard appears
        // This ensures settings changes (size, color, shape, theme) take effect immediately
        if (::keyboardView.isInitialized) {
            val newHeight = calculateKeyboardHeight()
            val params = keyboardView.layoutParams
            if (params.height != newHeight) {
                params.height = newHeight
                keyboardView.layoutParams = params
            }
            // Re-render to pick up color/shape/theme changes
            keyboardView.renderLayout()
            
            // Update navigation bar color for theme
            val themeColors = settingsStore.currentThemeColors
            window?.window?.navigationBarColor = themeColors.keyboardBg
        }
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
                handleVoiceInput()
            }
        }

        updateActionBarState()
    }

    // ─────────────────────────────────────────────
    // Voice Input
    // ─────────────────────────────────────────────

    private fun handleVoiceInput() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            return
        }

        if (speechRecognizer == null) {
            setupSpeechRecognizer()
        }

        if (isListening) {
            speechRecognizer?.stopListening()
            setListeningState(false)
        } else {
            speechIntent?.let {
                speechRecognizer?.startListening(it)
                setListeningState(true)
            }
        }
    }

    private fun setListeningState(listening: Boolean) {
        isListening = listening
        if (::actionBarCtrl.isInitialized) {
            actionBarCtrl.setListening(listening)
        }
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
        
        val extracted = ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
        if (extracted?.text != null) {
            return extracted.text.toString()
        }
        
        // Fallback for apps that don't support getExtractedText well
        val textBefore = ic.getTextBeforeCursor(5000, 0) ?: ""
        val textAfter = ic.getTextAfterCursor(5000, 0) ?: ""
        val selected = ic.getSelectedText(0) ?: ""
        
        return textBefore.toString() + selected.toString() + textAfter.toString()
    }

    private fun replaceInputText(newText: String) {
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        
        // Delete all text to reliably replace it
        ic.deleteSurroundingText(10000, 10000)
        ic.commitText(newText, 1)
        ic.endBatchEdit()
    }

    private fun updateActionBarState() {
        val ic = currentInputConnection
        val hasText = if (ic != null) {
            val textBefore = ic.getTextBeforeCursor(100, 0) ?: ""
            val textAfter = ic.getTextAfterCursor(100, 0) ?: ""
            val selected = ic.getSelectedText(0) ?: ""
            textBefore.isNotEmpty() || textAfter.isNotEmpty() || selected.isNotEmpty()
        } else {
            false
        }
        actionBarCtrl.setTextState(hasText)
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
