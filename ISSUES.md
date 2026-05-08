# Morfoboard — Issues Breakdown

> **Source**: `docs/prd/morfoboard.md`  
> **Generated**: 2026-05-07  
> **Status**: Ready for Implementation

---

## Dependency Graph

```
ISSUE-001 (Scaffolding)
    ├── ISSUE-002 (Keyboard IME)
    │       └── ISSUE-006 (Translation)
    │               └── ISSUE-007 (Fix Text)
    ├── ISSUE-003 (Backend Health)
    │       └── ISSUE-004 (Auth E2E)
    │               └── ISSUE-005 (AI Proxy)
    │                       ├── ISSUE-006 (Translation)
    │                       └── ISSUE-007 (Fix Text)
    └── ISSUE-008 (Settings)
            └── ISSUE-009 (Error Handling)
                    └── ISSUE-010 (Polish & Release)
```

---

## ISSUE-001: Project Scaffolding

**Priority**: 🔴 Critical (blocks everything)  
**Estimated Effort**: 1-2 days  
**Depends on**: Nothing

### Description
Set up both Android and Backend project structures with build systems, dependencies, and basic configuration.

### Checklist

#### Android
- [ ] Create Android project with package `com.morfoboard.app`
- [ ] Configure `build.gradle.kts` with:
  - Min SDK 26, Target SDK latest
  - Kotlin, Coroutines
  - OkHttp (HTTP client)
  - Google Identity Services (Credential Manager)
  - AndroidX Security (EncryptedSharedPreferences)
  - Material Design 3
- [ ] Create `AndroidManifest.xml` with IME service declaration
- [ ] Create `res/xml/method.xml` (IME metadata)
- [ ] Verify project builds successfully

#### Backend
- [ ] Create Go module `morfoboard-backend`
- [ ] Initialize with:
  - `net/http` or `chi` router
  - `google.golang.org/api/idtoken` (Google token verification)
  - Structured logging (`slog`)
- [ ] Create `cmd/server/main.go` entry point
- [ ] Create `Dockerfile` (multi-stage build)
- [ ] Create `docker-compose.yml` for local dev
- [ ] Verify `go build` succeeds

### Acceptance Criteria
- [ ] Android project builds with `./gradlew assembleDebug`
- [ ] Backend compiles with `go build ./cmd/server`
- [ ] Both projects have clean directory structure per PRD §7

### Completion Note
✅ **DONE** — 2026-05-07
- Android project builds with `./gradlew assembleDebug` (Gradle 9.1.0, AGP 8.7.3, Kotlin 2.0.21)
- Backend compiles with `go build ./cmd/server` (Go 1.26.2)
- Both projects have clean directory structure per PRD §7
- Debug keystore generated: `morfoboard-debug.keystore`

---

## ISSUE-002: Basic Keyboard (IME Service)

**Priority**: 🔴 Critical (core product)  
**Estimated Effort**: 2-3 days  
**Depends on**: ISSUE-001

### Description
Implement the core Android InputMethodService with QWERTY keyboard layout, dark theme, and basic typing functionality. The keyboard must work as a standard keyboard in any Android app.

### Checklist

#### IME Service
- [ ] Implement `MorfoboardIME` extending `InputMethodService`
- [ ] Register IME in `AndroidManifest.xml` with:
  - `BIND_INPUT_METHOD` permission
  - Intent filter for `android.view.InputMethod`
  - Metadata pointing to `method.xml`
- [ ] Handle `onCreateInputView()` → inflate keyboard layout
- [ ] Handle key presses → commit text to `InputConnection`
- [ ] Handle Shift state (normal, shifted, caps)
- [ ] Handle backspace, enter, space
- [ ] Handle number/symbol toggle (basic)

#### Keyboard Layout
- [ ] Create QWERTY layout XML or programmatic view
- [ ] 4 rows: QWERTYUIOP / ASDFGHJKL / ⇧ZXCVBNM⌫ / bottom row
- [ ] Dark theme styling per PRD §5.4
- [ ] Key press visual feedback (ripple/highlight)
- [ ] Key pop-up preview on press

#### Action Bar (Stub)
- [ ] Create action bar view above keyboard
- [ ] Contains: [🌐 Translate] [🔧 Fix Text] [⚙️ Settings]
- [ ] Initially hidden (show/hide logic in ISSUE-006)
- [ ] Dark theme styling

#### Theme
- [ ] Keyboard background: `#1A1A1A`
- [ ] Key background: `#2D2D2D`, text: `#FFFFFF`
- [ ] Action bar background: `#111111`
- [ ] Action button accent: `#3B82F6`

### Acceptance Criteria
- [ ] Keyboard appears when user selects Morfoboard as input method
- [ ] User can type text in any app (WhatsApp, Chrome, Notes)
- [ ] Shift/caps lock works correctly
- [ ] Backspace deletes characters
- [ ] Enter commits and sends (in messaging apps)
- [ ] Dark theme renders correctly
- [ ] Keyboard is responsive (no lag on keypress)

### Completion Note
_Pending_

---

## ISSUE-003: Backend Health & Basic Structure

**Priority**: 🟡 High (blocks auth & AI)  
**Estimated Effort**: 1 day  
**Depends on**: ISSUE-001

### Description
Build the Go backend skeleton with health endpoint, configuration, and routing structure. This establishes the server that will host auth verification and AI proxy.

### Checklist

#### Server
- [ ] `cmd/server/main.go`: start HTTP server on configurable port
- [ ] Graceful shutdown on SIGINT/SIGTERM
- [ ] Structured logging with `slog`

#### Routes
- [ ] `GET /api/v1/health` → returns `{"status": "ok", "version": "0.1.0", "ai_backend": "connected" | "disconnected"}`
- [ ] Health check pings 9router to determine `ai_backend` status
- [ ] Placeholder routes for `/api/v1/ai/process` (returns 501 Not Implemented)

#### Config
- [ ] `internal/config/config.go`: load from environment variables
  - `PORT` (default: 8080)
  - `NINE_ROUTER_URL` (9router endpoint)
  - `NINE_ROUTER_API_KEY` (9router API key)
  - `GOOGLE_CLIENT_ID` (for token verification)
- [ ] Validate required config on startup

#### Docker
- [ ] Multi-stage `Dockerfile` (build + alpine runtime)
- [ ] `docker-compose.yml` with backend service
- [ ] Health check in docker-compose

### Acceptance Criteria
- [ ] `curl localhost:8080/api/v1/health` returns valid JSON
- [ ] Server starts and stops cleanly
- [ ] Docker build succeeds
- [ ] All config values load from environment

### Completion Note
_Pending_

---

## ISSUE-004: Google Sign-In Authentication (E2E)

**Priority**: 🟡 High (blocks AI features)  
**Estimated Effort**: 2-3 days  
**Depends on**: ISSUE-001, ISSUE-003

### Description
Implement full Google Sign-In flow: Android client authenticates via Credential Manager API, stores token securely, and Backend verifies Google ID tokens. This is the auth layer that protects all AI endpoints.

### Checklist

#### Google Cloud Console Setup
- [ ] Create Google Cloud project (or use existing)
- [ ] Enable Google Sign-In API
- [ ] Create OAuth 2.0 Client ID (Android type)
- [ ] Generate SHA-1 fingerprint from debug keystore
- [ ] Note Client ID for Android config + BE verification

#### Android - Auth Module
- [ ] Implement `AuthManager` class:
  - `signIn()` → trigger Credential Manager Google Sign-In
  - `signOut()` → clear stored tokens
  - `getValidToken()` → return cached token or silent refresh
  - `isSignedIn()` → check if user has valid session
- [ ] Implement `TokenStore` class:
  - Store ID token in `EncryptedSharedPreferences`
  - Store refresh token (if available)
  - Clear on sign-out
- [ ] Wire Sign-In button in a temporary settings screen
- [ ] Handle sign-in errors (no Google account, cancelled, network error)

#### Backend - Auth Verification
- [ ] `internal/auth/verifier.go`:
  - Verify Google ID token using `google.golang.org/api/idtoken`
  - Validate audience matches our Client ID
  - Extract user email, name, subject
  - Return verified user info or error
- [ ] Middleware: extract `Authorization: Bearer <token>` header
- [ ] Middleware: call verifier, inject user info into request context
- [ ] Apply middleware to `/api/v1/ai/*` routes

#### E2E Test
- [ ] Android: sign in → get token → store token
- [ ] Android: call `GET /api/v1/health` with Bearer token → BE verifies → 200 OK
- [ ] Android: call with invalid token → 401 Unauthorized
- [ ] Token persists across app restarts

### Acceptance Criteria
- [ ] User can sign in with Google on Android
- [ ] Token stored encrypted on device
- [ ] Backend successfully verifies valid Google tokens
- [ ] Backend rejects invalid/expired tokens with 401
- [ ] User info (email, name) available in request context on BE

### Completion Note
_Pending_

---

## ISSUE-005: AI Proxy (BE → 9router)

**Priority**: 🟡 High (core infrastructure)  
**Estimated Effort**: 1-2 days  
**Depends on**: ISSUE-003, ISSUE-004

### Description
Implement the backend proxy that takes authenticated client requests, builds OpenAI-compatible prompts, forwards to 9router, and returns results. This is the bridge between the keyboard and the AI.

### Checklist

#### Proxy Handler
- [ ] `POST /api/v1/ai/process`:
  - Parse request body (action, text, source_lang, target_lang, tone)
  - Validate required fields (text: 1-5000 chars, action: translate|fix_text)
  - Build OpenAI-compatible request to 9router
  - Forward response back to client
- [ ] Timeout: 30 seconds for AI processing
- [ ] Error mapping: 9router errors → Morfoboard error responses

#### Prompt Templates
- [ ] `internal/proxy/prompts.go`:
  - Translation system prompt (with tone parameter)
  - Fix text system prompt
  - Parameterized: source_lang, target_lang, tone
- [ ] User message: the text to process

#### 9router Integration
- [ ] `internal/proxy/client.go`:
  - HTTP client to 9router (`/v1/chat/completions`)
  - API key in `Authorization: Bearer <api_key>` header
  - Request format: `{"model": "...", "messages": [...]}`
  - Response parsing: extract `choices[0].message.content`
- [ ] Health check integration (for `/api/v1/health` endpoint)

#### Request/Response Format
- [ ] Match PRD §4.4 API contract exactly
- [ ] Success response: `{success, original, result, action, metadata}`
- [ ] Error response: `{success: false, error, message}`

### Acceptance Criteria
- [ ] `POST /api/v1/ai/process` with valid auth returns AI result
- [ ] Translation request returns translated text
- [ ] Fix text request returns corrected text
- [ ] Invalid request returns 400 with descriptive error
- [ ] 9router timeout returns 504
- [ ] 9router unavailable returns 502

### Completion Note
_Pending_

---

## ISSUE-006: Translation Feature (Full Vertical Slice)

**Priority**: 🟡 High (core feature)  
**Estimated Effort**: 2-3 days  
**Depends on**: ISSUE-002, ISSUE-005

### Description
Implement the complete translation flow: user types text → taps Translate → language picker → AI processes → bottom sheet with result → Replace/Copy/Dismiss. This is the first full vertical slice through the product.

### Checklist

#### Action Bar Logic
- [ ] Show action bar when `InputConnection` has text
- [ ] Hide action bar when input field is empty
- [ ] Monitor text changes via `onUpdateSelection()`
- [ ] [🌐 Translate] button triggers translation flow

#### Language Picker
- [ ] Show language selection before AI call (chips or dropdown)
- [ ] Default to last used target language
- [ ] Available: Indonesian, Javanese, English, Spanish
- [ ] Persist selection as new default

#### AI Client (Android)
- [ ] `AIClient` class:
  - `translate(text, sourceLang, targetLang, tone)` → API call
  - `fixText(text)` → API call
  - Base URL configurable (for dev/prod)
  - Auth header injection (Bearer token)
  - Timeout handling (30s)
  - Error parsing
- [ ] Loading state on action bar (progress indicator)
- [ ] Disable AI buttons during processing

#### Prompt Integration
- [ ] Build translation request matching BE API contract
- [ ] Source language: "auto" (AI detects)
- [ ] Target language: from user selection
- [ ] Tone: from settings

#### Bottom Sheet
- [ ] `BottomSheetPresenter`:
  - Show translated text in preview area
  - Show metadata (detected source lang, target lang, tone)
  - [Replace] button → replace text in `InputConnection`
  - [Copy] button → copy to clipboard + toast
  - [Dismiss] button → close bottom sheet
- [ ] Dark theme styling per PRD §5.2
- [ ] Handle long text (scrollable preview)

#### E2E Flow
- [ ] User types "halo gimana kabarmu" in WhatsApp
- [ ] Taps [🌐 Translate]
- [ ] Selects English
- [ ] Sees loading state
- [ ] Bottom sheet shows "hey how's it going"
- [ ] Taps [Replace] → text in WhatsApp changes

### Acceptance Criteria
- [ ] Translate button appears when text exists in input field
- [ ] Language picker shows 4 languages with default selection
- [ ] AI returns translated text within 30 seconds
- [ ] Bottom sheet shows result with 3 action buttons
- [ ] Replace correctly replaces text in host app
- [ ] Copy correctly copies to clipboard
- [ ] Dismiss closes bottom sheet without changes
- [ ] Language selection persists for next use

### Completion Note
_Pending_

---

## ISSUE-007: Typo & Grammar Correction Feature

**Priority**: 🟡 High (core feature)  
**Estimated Effort**: 1 day  
**Depends on**: ISSUE-006 (reuses most infrastructure)

### Description
Implement typo and grammar correction flow. Reuses the same UI components as translation (action bar, bottom sheet) but with different AI prompt and no language selection.

### Checklist

#### Action Bar
- [ ] [🔧 Fix Text] button triggers correction flow
- [ ] Same loading state as translation

#### AI Client
- [ ] `fixText(text)` method in `AIClient`
- [ ] No language/tone parameters needed (preserves original language)
- [ ] Fix text request matches BE API contract

#### Bottom Sheet
- [ ] Reuse same `BottomSheetPresenter`
- [ ] Show corrected text
- [ ] Metadata: "Grammar & typos fixed"
- [ ] Same 3 buttons: Replace, Copy, Dismiss

#### E2E Flow
- [ ] User types "aku sdh mkn tdi mlm"
- [ ] Taps [🔧 Fix Text]
- [ ] Loading state
- [ ] Bottom sheet shows "aku sudah makan tadi malam"
- [ ] Taps [Replace] → text changes

### Acceptance Criteria
- [ ] Fix Text button appears alongside Translate
- [ ] AI corrects both typos and grammar
- [ ] Corrected text shown in bottom sheet
- [ ] Replace/Copy/Dismiss work correctly
- [ ] If no errors found, AI returns original text or "no issues found"

### Completion Note
_Pending_

---

## ISSUE-008: Settings Activity

**Priority**: 🟢 Medium  
**Estimated Effort**: 1-2 days  
**Depends on**: ISSUE-002, ISSUE-004

### Description
Build the Settings screen accessible from the gear icon on the keyboard. Includes language management, tone selection, Google account info, and about section.

### Checklist

#### Settings Activity
- [ ] `SettingsActivity`: full-screen activity with dark theme
- [ ] Accessible via gear icon (⚙️) on keyboard action bar
- [ ] Back navigation to keyboard

#### Google Account Section
- [ ] Show signed-in user name + email
- [ ] [Sign Out] button → clear tokens, update UI
- [ ] [Sign In] button (if not signed in) → trigger Google Sign-In
- [ ] Handle sign-in/sign-out state transitions

#### Language Management
- [ ] Show all 4 languages as checkboxes/chips
- [ ] At least one language must be selected
- [ ] Active/last-used language highlighted
- [ ] Tapping a language makes it the active target
- [ ] Persist to SharedPreferences/DataStore

#### Tone Selection
- [ ] Radio group: Casual, Natural, Formal, Professional
- [ ] Current selection highlighted
- [ ] Persist selection on change

#### About Section
- [ ] App name: Morfoboard
- [ ] Version: 0.1.0
- [ ] Package: com.morfoboard.app

### Acceptance Criteria
- [ ] Gear icon opens Settings Activity
- [ ] Google account info displays correctly
- [ ] Language selection persists and affects translate flow
- [ ] Tone selection persists and affects AI prompts
- [ ] Sign out clears tokens and disables AI features
- [ ] Back button returns to keyboard

### Completion Note
_Pending_

---

## ISSUE-009: Error Handling & Offline States

**Priority**: 🟢 Medium  
**Estimated Effort**: 1-2 days  
**Depends on**: ISSUE-006, ISSUE-007, ISSUE-008

### Description
Implement comprehensive error handling and graceful degradation. The keyboard must NEVER be blocked by AI failures. All error states should be user-friendly.

### Checklist

#### Network States
- [ ] No internet → AI buttons show disabled state with tooltip
- [ ] Internet restored → AI buttons re-enable automatically
- [ ] Network monitor: `ConnectivityManager` callback

#### API Errors
- [ ] 401 Unauthorized → attempt silent refresh → if fails, show re-login prompt in Settings
- [ ] 400 Bad Request → show error toast (shouldn't happen in normal use)
- [ ] 502 AI Unavailable → show "AI service temporarily unavailable" toast
- [ ] 504 Timeout → show "Request timed out, please try again" toast
- [ ] Unknown error → show generic error toast

#### Loading States
- [ ] Action bar shows loading indicator during AI processing
- [ ] Disable both buttons during processing
- [ ] Cancel option (optional: dismiss cancels request)

#### Token Refresh
- [ ] Silent refresh 5 minutes before expiry
- [ ] If refresh fails → subtle notification (not blocking)
- [ ] Settings shows "Session expired — please sign in again"

#### UI States
- [ ] Empty state: action bar hidden when no text
- [ ] Loading state: progress indicator on action bar
- [ ] Error state: toast notification, text preserved
- [ ] Success state: bottom sheet with result
- [ ] Disabled state: greyed out AI buttons when offline

### Acceptance Criteria
- [ ] Keyboard works normally when airplane mode is on
- [ ] AI buttons visually indicate unavailability when offline
- [ ] Coming back online re-enables AI buttons automatically
- [ ] All API errors show user-friendly messages
- [ ] Text is NEVER lost due to AI failures
- [ ] Token refresh happens silently in background

### Completion Note
_Pending_

---

## ISSUE-010: Polish, Testing & Release Prep

**Priority**: 🟢 Medium  
**Estimated Effort**: 2-3 days  
**Depends on**: All previous issues

### Description
Final polish pass: animations, edge cases, comprehensive testing, and preparation for release.

### Checklist

#### UI Polish
- [ ] Bottom sheet enter/exit animations
- [ ] Action bar show/hide animations
- [ ] Loading pulse animation
- [ ] Key press haptic feedback (optional)
- [ ] Smooth transitions between states

#### Edge Cases
- [ ] Very long text (5000 chars) — scrollable, no crash
- [ ] Empty text — action bar hidden
- [ ] Special characters (emoji, symbols) — preserved in translation
- [ ] Rapid button tapping — debounce, no duplicate requests
- [ ] Rotation / configuration changes — preserve state
- [ ] Multiple input fields — action bar state per field

#### Testing
- [ ] Unit tests: PromptBuilder, AIClient, TokenStore, MorfoboardSettings
- [ ] Integration test: mock 9router, test full translate flow
- [ ] Manual E2E test: WhatsApp, Chrome, Notes
- [ ] Test all 4 languages
- [ ] Test all 4 tones
- [ ] Test offline → online transitions

#### Release Prep
- [ ] App icon (adaptive icon)
- [ ] App name in launcher: "Morfoboard"
- [ ] ProGuard/R8 rules (keep IME service)
- [ ] Signed release APK/AAB
- [ ] README.md with setup instructions

### Acceptance Criteria
- [ ] All unit tests pass
- [ ] Manual E2E test matrix complete
- [ ] No crashes on common edge cases
- [ ] Release APK builds and installs correctly
- [ ] README explains how to build, configure, and deploy

### Completion Note
_Pending_

---

## Summary

| Issue | Title | Priority | Effort | Status |
|-------|-------|----------|--------|--------|
| 001 | Project Scaffolding | 🔴 Critical | 1-2d | ⬜ Pending |
| 002 | Basic Keyboard (IME) | 🔴 Critical | 2-3d | ⬜ Pending |
| 003 | Backend Health & Structure | 🟡 High | 1d | ⬜ Pending |
| 004 | Google Auth (E2E) | 🟡 High | 2-3d | ⬜ Pending |
| 005 | AI Proxy (BE → 9router) | 🟡 High | 1-2d | ⬜ Pending |
| 006 | Translation (Full Slice) | 🟡 High | 2-3d | ⬜ Pending |
| 007 | Typo/Grammar Fix | 🟡 High | 1d | ⬜ Pending |
| 008 | Settings Activity | 🟢 Medium | 1-2d | ⬜ Pending |
| 009 | Error Handling & Offline | 🟢 Medium | 1-2d | ⬜ Pending |
| 010 | Polish & Release | 🟢 Medium | 2-3d | ⬜ Pending |

**Total Estimated Effort**: 14-22 days

---

*This issues file is append-only. New discoveries during implementation should be added as decimal sub-issues (e.g., ISSUE-006.1) or in the "Unplanned Follow-up Issues" section.*
