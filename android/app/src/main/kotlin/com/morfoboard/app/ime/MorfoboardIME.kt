package com.morfoboard.app.ime

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
        // Production endpoint. The backend currently exposes plain HTTP only;
        // migrate this to HTTPS before handling sensitive text in production.
        private const val PRODUCTION_BASE_URL = "http://43.156.68.104:8080"
        // Local mirror window of text before the cursor. Big enough for the
        // auto-caps regex and to amortize backspace bursts between IPC refills.
        private const val LOCAL_TEXT_WINDOW = 32
        // onUpdateSelection echoes of our own commits arrive within this window;
        // anything later is treated as a real cursor move and pays an IPC resync.
        private const val SELF_EDIT_WINDOW_MS = 150L
    }

    private lateinit var rootLayout: FrameLayout
    private lateinit var mainLayout: LinearLayout
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
    
    // Cached regex for auto-caps detection
    private val autoCapsRegex = Regex(".*[.?!]\\s+$")
    
    private var editorHasText = false
    private var activeAIAction: String? = null

    // Mirror of up to LOCAL_TEXT_WINDOW chars before the cursor. Every
    // per-keystroke decision (auto-caps, has-text) reads this instead of doing
    // a blocking InputConnection round-trip on the UI thread — that IPC was the
    // main source of typing lag at high speed.
    private val localTextBefore = StringBuilder()
    private var localTextComplete = false // mirror reaches the start of the field
    private var hasTextAfter = false
    private var hasSelection = false
    private var lastSelfEditUptime = 0L

    // ─────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        settingsStore = MorfoboardSettingsStore(this)
        authManager = AuthManager(this)
        aiClient = AIClient(
            baseUrl = PRODUCTION_BASE_URL,
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
                        if (currentInputConnection?.commitText(text, 1) == true) {
                            onTextCommitted(text)
                        }
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    override fun onCreateInputView(): View {
        Log.d(TAG, "onCreateInputView")
        
        val themeColors = settingsStore.currentThemeColors
        configureImeNavigationBar(themeColors.keyboardBg, settingsStore.isDarkTheme)

        rootLayout = FrameLayout(this)

        mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // Painted keyboardBg so the navigation-inset padding band below the
            // keys reads as part of the keyboard, never as a black gap.
            setBackgroundColor(themeColors.keyboardBg)
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

        // Pad by the exact navigation-bar inset: the system centers the gesture
        // pill inside that band, so matching it exactly is what keeps the
        // handle/edge spacing symmetric (clamping the padding clips the band and
        // makes it look lopsided). The band is painted keyboardBg by mainLayout,
        // so it reads as keyboard surface, not a gap. Zero in 3-button mode,
        // where the IME window already sits above the navigation bar.
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            if (mainLayout.paddingBottom != bottom) {
                mainLayout.setPadding(0, 0, 0, bottom)
            }
            insets
        }
        ViewCompat.requestApplyInsets(rootLayout)

        // Bottom sheet presenter
        bottomSheetPresenter = BottomSheetPresenter(this, rootLayout)

        Log.d(TAG, "onCreateInputView complete - keyboard: ${keyboardHeight}px")
        return rootLayout
    }

    @Suppress("DEPRECATION")
    private fun configureImeNavigationBar(keyboardColor: Int, isDarkTheme: Boolean) {
        val imeWindow = window?.window ?: return
        imeWindow.navigationBarColor = keyboardColor
        // Prevent OEM contrast enforcement from replacing keyboardBg with a scrim.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            imeWindow.isNavigationBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            imeWindow.navigationBarDividerColor = keyboardColor
        }
        WindowInsetsControllerCompat(imeWindow, imeWindow.decorView)
            .isAppearanceLightNavigationBars = !isDarkTheme
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
        speechRecognizer?.destroy()
        speechRecognizer = null
        connectivityMonitor.stop()
        scope.cancel()
    }

    // Track settings state to avoid unnecessary re-renders
    private var lastSettingsHash = 0

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (::keyboardView.isInitialized) {
            val newHeight = calculateKeyboardHeight()
            val params = keyboardView.layoutParams
            if (params.height != newHeight) {
                params.height = newHeight
                keyboardView.layoutParams = params
            }
            
            // Only re-render if settings actually changed
            val currentHash = settingsStore.run {
                (accentColor + theme + keyShape + keyboardSize).hashCode()
            }
            if (currentHash != lastSettingsHash) {
                lastSettingsHash = currentHash
                keyboardView.renderLayout()
                val themeColors = settingsStore.currentThemeColors
                mainLayout.setBackgroundColor(themeColors.keyboardBg)
                actionBarCtrl.applyTheme()
                configureImeNavigationBar(themeColors.keyboardBg, settingsStore.isDarkTheme)
            }
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        resyncEditorState()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEndInt: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEndInt, candidatesStart, candidatesEnd)
        hasSelection = newSelStart != newSelEndInt
        val selectionMoved = oldSelStart != newSelStart || oldSelEnd != newSelEndInt
        // Echoes of our own commits keep the mirror valid; only external cursor
        // moves (tap elsewhere, app edits) pay the blocking resync IPC.
        if (selectionMoved &&
            SystemClock.uptimeMillis() - lastSelfEditUptime > SELF_EDIT_WINDOW_MS
        ) {
            resyncEditorState()
        }
    }

    /**
     * One-shot IPC read of the editor state around the cursor. Called on field
     * focus and on external cursor moves — never on the per-keystroke path.
     */
    private fun resyncEditorState() {
        val ic = currentInputConnection
        localTextBefore.setLength(0)
        if (ic == null) {
            localTextComplete = true
            hasTextAfter = false
            hasSelection = false
        } else {
            refillLocalTextBefore(ic)
            hasTextAfter = !ic.getTextAfterCursor(1, 0).isNullOrEmpty()
            hasSelection = !ic.getSelectedText(0).isNullOrEmpty()
        }
        setEditorHasText(localTextBefore.isNotEmpty() || hasTextAfter || hasSelection)
        updateAutoCaps()
    }

    private fun refillLocalTextBefore(ic: InputConnection) {
        val before = ic.getTextBeforeCursor(LOCAL_TEXT_WINDOW, 0) ?: ""
        localTextBefore.setLength(0)
        localTextBefore.append(before)
        localTextComplete = before.length < LOCAL_TEXT_WINDOW
    }

    /** Mirror bookkeeping after our own commitText — no IPC. */
    private fun onTextCommitted(text: String) {
        localTextBefore.append(text)
        if (localTextBefore.length > LOCAL_TEXT_WINDOW) {
            localTextBefore.delete(0, localTextBefore.length - LOCAL_TEXT_WINDOW)
            localTextComplete = false
        }
        lastSelfEditUptime = SystemClock.uptimeMillis()
        setEditorHasText(true)
        updateAutoCaps()
    }

    /** Auto-caps computed from the local mirror — no IPC. */
    private fun updateAutoCaps() {
        if (shiftState == ShiftState.LOCKED) return
        val shouldCaps = (localTextBefore.isEmpty() && localTextComplete) ||
            localTextBefore.endsWith("\n") ||
            autoCapsRegex.matches(localTextBefore)
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
                // Also drops SINGLE shift back to OFF via the mirror.
                onTextCommitted(char)
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
                if (hasSelection) {
                    // Rare path: verify before a destructive delete, then resync
                    // because the mirror can't know what the selection covered.
                    val selected = ic.getSelectedText(0)
                    if (selected.isNullOrEmpty()) {
                        ic.deleteSurroundingText(1, 0)
                    } else {
                        ic.commitText("", 0)
                    }
                    resyncEditorState()
                } else {
                    // Hot path (incl. 20/s key repeat): zero IPC. The mirror is
                    // refilled only when a delete burst exhausts its window.
                    ic.deleteSurroundingText(1, 0)
                    if (localTextBefore.isNotEmpty()) {
                        localTextBefore.deleteCharAt(localTextBefore.length - 1)
                    }
                    if (localTextBefore.isEmpty() && !localTextComplete) {
                        refillLocalTextBefore(ic)
                    }
                    lastSelfEditUptime = SystemClock.uptimeMillis()
                    setEditorHasText(localTextBefore.isNotEmpty() || hasTextAfter)
                    updateAutoCaps()
                }
            }

            KeyType.ENTER -> {
                val editorInfo = currentInputEditorInfo
                val imeOptions = editorInfo?.imeOptions ?: 0
                val action = imeOptions and EditorInfo.IME_MASK_ACTION
                val inputType = editorInfo?.inputType ?: 0
                
                // Check if the field is multi-line (e.g. WhatsApp chat, notes)
                val isMultiLine = (inputType and android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
                
                // If multi-line field, always insert newline
                // If single-line with an action (Send, Search, Go, etc.), perform that action
                if (isMultiLine) {
                    ic.commitText("\n", 1)
                    onTextCommitted("\n")
                } else if (action != EditorInfo.IME_ACTION_UNSPECIFIED &&
                    action != EditorInfo.IME_ACTION_NONE
                ) {
                    // App may clear the field; the resulting selection change
                    // arrives outside the self-edit window and triggers a resync.
                    ic.performEditorAction(action)
                } else {
                    ic.commitText("\n", 1)
                    onTextCommitted("\n")
                }
            }

            KeyType.SPACE -> {
                ic.commitText(" ", 1)
                onTextCommitted(" ")
            }

            KeyType.SYMBOL_TOGGLE -> {
                keyboardView.toggleSymbolMode()
            }

            KeyType.SYMBOL_PAGE_TOGGLE -> {
                keyboardView.toggleSymbolPage()
            }

            KeyType.EMOJI_TOGGLE -> {
                keyboardView.toggleEmojiMode()
            }

            KeyType.LANGUAGE -> {
                Log.d(TAG, "Language key pressed")
            }

            KeyType.ACTION_AI -> {
                handleVoiceInput()
            }
        }

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

        activeAIAction = AIAction.TRANSLATE.apiValue
        Log.d(TAG, "Translate requested (${text.length} chars)")
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
                    val expectedAction = AIAction.TRANSLATE.apiValue
                    if (activeAIAction != expectedAction || response.action != expectedAction) {
                        handleAIError(
                            AIException.InvalidResponse(
                                "Expected $expectedAction response, received ${response.action}"
                            )
                        )
                        return@onSuccess
                    }
                    activeAIAction = null
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

        activeAIAction = AIAction.FIX_TEXT.apiValue
        Log.d(TAG, "Fix text requested (${text.length} chars)")
        actionBarCtrl.setLoading(true)

        scope.launch {
            try {
                val result = aiClient.process(
                    action = AIAction.FIX_TEXT,
                    text = text
                )

                result.onSuccess { response ->
                    val expectedAction = AIAction.FIX_TEXT.apiValue
                    if (activeAIAction != expectedAction || response.action != expectedAction) {
                        handleAIError(
                            AIException.InvalidResponse(
                                "Expected $expectedAction response, received ${response.action}"
                            )
                        )
                        return@onSuccess
                    }
                    activeAIAction = null
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

        localTextBefore.setLength(0)
        localTextBefore.append(newText.takeLast(LOCAL_TEXT_WINDOW))
        localTextComplete = newText.length <= LOCAL_TEXT_WINDOW
        hasTextAfter = false
        hasSelection = false
        lastSelfEditUptime = SystemClock.uptimeMillis()
        setEditorHasText(newText.isNotEmpty())
        updateAutoCaps()
    }

    private fun setEditorHasText(hasText: Boolean) {
        if (editorHasText == hasText) return
        editorHasText = hasText
        if (::actionBarCtrl.isInitialized) {
            actionBarCtrl.setTextState(hasText)
        }
    }

    private fun handleAIError(error: Throwable) {
        activeAIAction = null
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
