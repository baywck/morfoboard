# Morfoboard — Product Requirements Document

> **Version**: 1.0  
> **Date**: 2026-05-07  
> **Status**: Draft — Pending Approval  
> **Author**: Pak BOS + Hermes  
> **Source**: `grill_me_answers.md` (Grill Session Consensus)

---

## 1. Overview

### What is Morfoboard?
Morfoboard is an Android keyboard (IME) powered by AI agents. It allows users to type text in any language, then use AI to **translate** it into a target language with natural tone/style, or **fix typos and grammar** — all from within the keyboard itself.

### Why Morfoboard?
- **Multilingual communication** is hard — users make grammar mistakes, use wrong tone, or can't express themselves in a second language
- Existing keyboards (GBoard, SwiftKey) offer basic translation but lack **tone control** and **grammar correction**
- AI-powered correction goes beyond spell-check: it understands context, slang, and natural phrasing
- **Self-hosted AI** via 9router means the user controls the models and can swap them freely

### Target User
- Indonesian users who communicate in multiple languages (Indonesian, Javanese, English, Spanish)
- Users who want AI-assisted writing without leaving their messaging app
- Privacy-conscious users who prefer self-hosted AI over cloud services

---

## 2. Goals & Non-Goals

### Goals (MVP v0.1)
| # | Goal | Success Metric |
|---|------|----------------|
| G1 | AI-powered translation between 4 languages | User can translate typed text ID↔EN↔JV↔ES with natural grammar |
| G2 | AI-powered typo & grammar correction | User can fix typed text in one tap |
| G3 | Tone/style control for translations | User can select casual/natural/formal/professional in settings |
| G4 | Secure auth via Google Sign-In | User signs in once, token silently refreshes |
| G5 | Graceful degradation | Keyboard works normally when AI is unavailable |
| G6 | Backend proxy architecture | API keys never exposed to client |

### Non-Goals (Explicitly Out of Scope for v0.1)
- ❌ Autocomplete / next-word prediction
- ❌ Text summarization
- ❌ On-device AI models (offline AI)
- ❌ iOS support
- ❌ Light theme
- ❌ Multiple AI model selection from keyboard UI
- ❌ Real-time inline suggestions as user types
- ❌ Voice input
- ❌ Emoji/GIF suggestions

---

## 3. User Stories

### 3.1 Translation

**US-T1**: As a user, I want to type text in any language and translate it to my preferred target language so that I can communicate naturally in a language I'm not fluent in.

**Acceptance Criteria:**
- [ ] User types complete text in the input field
- [ ] Action bar shows [🌐 Translate] button above keyboard
- [ ] Tapping Translate shows a language picker (defaulting to last used target)
- [ ] AI processes the text and shows a bottom sheet with the translated result
- [ ] Bottom sheet has [Replace] [Copy] [Dismiss] actions
- [ ] Replace replaces the original text in the host app's input field
- [ ] Copy copies the result to clipboard

**US-T2**: As a user, I want to set my preferred target language and tone/style in settings so that I don't have to configure it every time.

**Acceptance Criteria:**
- [ ] Settings screen shows available target languages: Indonesian, Javanese, English, Spanish
- [ ] User can select multiple target languages (one active at a time)
- [ ] Default target = last used language
- [ ] Tone selector: casual, natural, formal, professional
- [ ] Changes persist across keyboard sessions

**US-T3**: As a user, I want to quickly switch target language during a translate action so that I can translate to a different language without going to settings.

**Acceptance Criteria:**
- [ ] Language switcher appears in the translate flow (bottom sheet or action bar)
- [ ] Selecting a new language makes it the new default
- [ ] Language switch is persisted for next use

### 3.2 Typo & Grammar Correction

**US-G1**: As a user, I want to fix typos and grammar mistakes in my typed text so that my message looks professional and correct.

**Acceptance Criteria:**
- [ ] User types complete text
- [ ] Action bar shows [🔧 Fix Text] button
- [ ] AI corrects both spelling errors AND grammar issues
- [ ] Corrected text shown in bottom sheet with [Replace] [Copy] [Dismiss]
- [ ] AI preserves the original meaning and tone of the text
- [ ] If no errors found, AI returns the original text unchanged (or a "no issues found" message)

### 3.3 Authentication

**US-A1**: As a new user, I want to sign in with my Google account once so that I can access AI features securely.

**Acceptance Criteria:**
- [ ] First launch: Settings screen prompts Google Sign-In
- [ ] Uses Credential Manager API (modern Google Sign-In)
- [ ] Google token stored encrypted on device (EncryptedSharedPreferences)
- [ ] User sees their Google name/email in Settings after sign-in
- [ ] Sign-out option available in Settings

**US-A2**: As a returning user, I want my session to persist silently so that I don't have to sign in every time I use the keyboard.

**Acceptance Criteria:**
- [ ] Token silently refreshes before expiry
- [ ] If refresh fails, user sees a subtle re-login prompt (not blocking keyboard usage)
- [ ] Keyboard remains fully functional as a basic keyboard even when not signed in

### 3.4 Keyboard Basics

**US-K1**: As a user, I want a standard QWERTY keyboard that works like any other keyboard so that I can type normally.

**Acceptance Criteria:**
- [ ] Standard QWERTY layout with all common keys
- [ ] Supports basic typing in all host apps (WhatsApp, browser, notes, etc.)
- [ ] Dark theme
- [ ] Responsive key presses with haptic feedback (optional)
- [ ] Standard keyboard features: shift, backspace, enter, space, numbers/symbols

**US-K2**: As a user, I want to see AI action buttons only when I've typed text so that the keyboard UI stays clean.

**Acceptance Criteria:**
- [ ] Action bar appears above keyboard when there's text in the input field
- [ ] Action bar hides when input field is empty
- [ ] Action bar shows loading state when AI is processing

### 3.5 Error Handling

**US-E1**: As a user, I want the keyboard to work normally even when AI features are unavailable so that I'm never blocked from typing.

**Acceptance Criteria:**
- [ ] No internet connection → keyboard works, AI buttons show disabled state
- [ ] VPS timeout (>30s) → show timeout error toast, text preserved
- [ ] Backend error → show generic error toast, text preserved
- [ ] Google token expired + refresh fails → prompt re-login in Settings, keyboard still works

---

## 4. Technical Architecture

### 4.1 System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     ANDROID DEVICE                          │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              Morfoboard App (Kotlin)                │   │
│  │                                                     │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────┐  │   │
│  │  │  IME Service  │  │  Settings    │  │  Auth    │  │   │
│  │  │  (Keyboard)   │  │  Activity    │  │  Module  │  │   │
│  │  │              │  │              │  │          │  │   │
│  │  │  • QWERTY    │  │  • Languages │  │  • Google│  │   │
│  │  │  • Action Bar│  │  • Tone      │  │    Sign  │  │   │
│  │  │  • Bottom    │  │  • Account   │  │    In    │  │   │
│  │  │    Sheet     │  │              │  │  • Token │  │   │
│  │  └──────┬───────┘  └──────────────┘  │    Mgmt  │  │   │
│  │         │                             └────┬─────┘  │   │
│  │         │                                  │        │   │
│  │  ┌──────▼──────────────────────────────────▼─────┐  │   │
│  │  │              AI Client Module                 │  │   │
│  │  │  • HTTP client (OkHttp/Retrofit)              │  │   │
│  │  │  • Request building (system prompts)          │  │   │
│  │  │  • Error handling & timeout                   │  │   │
│  │  │  • Token injection (Authorization header)     │  │   │
│  │  └──────────────────┬────────────────────────────┘  │   │
│  └─────────────────────┼────────────────────────────────┘   │
│                        │ HTTPS                               │
└────────────────────────┼─────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                    VPS SUMOPOD                              │
│                                                             │
│  ┌──────────────────────┐    ┌────────────────────────────┐ │
│  │   Backend (Go)       │    │   9router                  │ │
│  │                      │    │   (OpenAI-compatible)      │ │
│  │  • Google token      │    │                            │ │
│  │    verification      │───▶│  • Model routing           │ │
│  │  • API key injection │    │  • /v1/chat/completions    │ │
│  │  • Rate limiting     │    │  • Swap models freely      │ │
│  │    (stub for MVP)    │    │                            │ │
│  │  • User management   │    └────────────┬───────────────┘ │
│  │    (stub for MVP)    │                 │                 │
│  └──────────────────────┘                 ▼                 │
│                                  ┌────────────────────┐     │
│                                  │   LLM Model        │     │
│                                  │   (any model via   │     │
│                                  │    9router)        │     │
│                                  └────────────────────┘     │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 Component Breakdown

#### Android App (Kotlin)

| Component | Responsibility |
|-----------|---------------|
| `InputMethodService` | Core IME: keyboard layout, key handling, text input/output |
| `ActionBarController` | Show/hide action bar based on input field state |
| `BottomSheetPresenter` | Display AI results with Replace/Copy/Dismiss |
| `AIClient` | HTTP calls to BE, request/response mapping, timeout handling |
| `AuthManager` | Google Sign-In flow, token storage, silent refresh |
| `SettingsActivity` | Language, tone, account management |
| `ThemeProvider` | Dark theme resources and styling |

#### Backend (Go)

| Component | Responsibility |
|-----------|---------------|
| `auth/verifier.go` | Verify Google ID tokens using Google's public keys |
| `proxy/handler.go` | Accept client request → verify token → forward to 9router |
| `ratelimit/` | Stub for future rate limiting (architecture ready) |
| `config/` | Server config, 9router endpoint, API keys |

### 4.3 Data Models

#### Settings (Android - SharedPreferences / DataStore)

```kotlin
data class MorfoboardSettings(
    val targetLanguage: String = "en",        // ISO 639-1 code
    val availableLanguages: List<String> = listOf("id", "jv", "en", "es"),
    val tone: Tone = Tone.NATURAL,
    val isSignedIn: Boolean = false,
    val userEmail: String? = null,
    val userName: String? = null,
)

enum class Tone(val label: String, val promptHint: String) {
    CASUAL("Casual", "Write in a casual, relaxed, conversational tone"),
    NATURAL("Natural", "Write in a natural, everyday tone"),
    FORMAL("Formal", "Write in a formal, professional tone"),
    PROFESSIONAL("Professional", "Write in a professional, business-appropriate tone")
}
```

#### API Request (Client → BE)

```json
{
  "action": "translate",
  "text": "halo gimana kabarmu",
  "source_language": "auto",
  "target_language": "en",
  "tone": "casual"
}
```

```json
{
  "action": "fix_text",
  "text": "aku sdh mkn tdi mlm"
}
```

#### API Response (BE → Client)

```json
{
  "success": true,
  "original": "halo gimana kabarmu",
  "result": "hey how's it going",
  "action": "translate",
  "model_used": "llama-3.1-8b"
}
```

```json
{
  "success": false,
  "error": "timeout",
  "message": "AI service is taking too long. Please try again."
}
```

### 4.4 API Contracts

#### POST `/api/v1/ai/process`

**Headers:**
```
Authorization: Bearer <google_id_token>
Content-Type: application/json
```

**Request Body:**
```json
{
  "action": "translate" | "fix_text",
  "text": "string (required, 1-5000 chars)",
  "source_language": "auto" | "id" | "jv" | "en" | "es",
  "target_language": "id" | "jv" | "en" | "es",
  "tone": "casual" | "natural" | "formal" | "professional"
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "original": "string",
  "result": "string",
  "action": "translate" | "fix_text",
  "metadata": {
    "model": "string",
    "processing_time_ms": 1234
  }
}
```

**Error Responses:**
| Status | Body | Meaning |
|--------|------|---------|
| 400 | `{"success": false, "error": "invalid_request", "message": "..."}` | Bad request (missing text, invalid language) |
| 401 | `{"success": false, "error": "unauthorized", "message": "..."}` | Invalid/expired Google token |
| 429 | `{"success": false, "error": "rate_limited", "message": "..."}` | Rate limit exceeded (future) |
| 502 | `{"success": false, "error": "ai_unavailable", "message": "..."}` | 9router/LLM unavailable |
| 504 | `{"success": false, "error": "timeout", "message": "..."}` | AI processing timeout |

#### GET `/api/v1/health`

**Response (200 OK):**
```json
{
  "status": "ok",
  "version": "0.1.0",
  "ai_backend": "connected" | "disconnected"
}
```

### 4.5 Prompt Engineering

#### Translation Prompt (System)

```
You are a professional translator. Translate the user's text from {source_language} to {target_language}.

Rules:
- Write in a {tone} tone
- Preserve the original meaning and intent
- Use natural, idiomatic expressions in the target language
- Do not add explanations or notes — return ONLY the translated text
- If the source language is uncertain, detect it automatically
```

#### Typo & Grammar Fix Prompt (System)

```
You are a text correction assistant. Fix all spelling errors and grammar mistakes in the user's text.

Rules:
- Preserve the original language of the text
- Preserve the original tone and style
- Fix typos (misspellings, wrong characters)
- Fix grammar (subject-verb agreement, tense, articles, prepositions)
- If the text has no errors, return it unchanged
- Do not add explanations or notes — return ONLY the corrected text
```

### 4.6 Auth Flow

```
┌──────────┐     ┌──────────────┐     ┌──────────┐     ┌──────────┐
│  User    │────▶│  Credential  │────▶│  Google  │────▶│  Morfo   │
│  opens   │     │  Manager API │     │  OAuth   │     │  BE      │
│  Settings│     │  (Android)   │     │  Server  │     │          │
└──────────┘     └──────────────┘     └──────────┘     └──────────┘
                        │                                     │
                        │ ID Token                            │ Verify token
                        │ (stored encrypted)                  │ with Google
                        ▼                                     ▼
                 ┌──────────────┐                     ┌──────────┐
                 │ Encrypted    │                     │  Return  │
                 │ SharedPrefs  │                     │  session │
                 └──────────────┘                     └──────────┘

Every AI request:
Client ──(Bearer google_id_token)──▶ BE ──(verify)──▶ 9router ──▶ LLM
```

**Token Lifecycle:**
1. User signs in → Google returns ID token + refresh token
2. ID token stored in EncryptedSharedPreferences
3. On each AI request: attach ID token as Bearer token
4. Before expiry (~55 min for 1hr tokens): silent refresh via Google SDK
5. If refresh fails: show re-login prompt in Settings (non-blocking)

---

## 5. UX Specifications

### 5.1 Keyboard Layout (Dark Theme)

```
┌─────────────────────────────────────────────┐
│  ┌───────────────────────────────────────┐  │
│  │  🌐 Translate    🔧 Fix Text   ⚙️    │  │  ← Action Bar (visible when text exists)
│  └───────────────────────────────────────┘  │
│                                             │
│  ┌───┬───┬───┬───┬───┬───┬───┬───┬───┬───┐ │
│  │ Q │ W │ E │ R │ T │ Y │ U │ I │ O │ P │ │
│  ├───┼───┼───┼───┼───┼───┼───┼───┼───┼───┤ │
│  │ A │ S │ D │ F │ G │ H │ J │ K │ L │   │ │
│  ├───┼───┼───┼───┼───┼───┼───┼───┼───┼───┤ │
│  │ ⇧ │ Z │ X │ C │ V │ B │ N │ M │ ⌫ │   │ │
│  └───┴───┴───┴───┴───┴───┴───┴───┴───┴───┘ │
│  [123]  [🌐]  [━━━━━━━━SPACE━━━━━━━]  [⏎]  │
└─────────────────────────────────────────────┘
```

### 5.2 Bottom Sheet (Translation Result)

```
┌─────────────────────────────────────────────┐
│                                             │
│  Translation Result                    ✕    │
│  ─────────────────────────────────────────  │
│                                             │
│  From (auto-detected): Indonesian           │
│  To: English  •  Casual                     │
│                                             │
│  ┌─────────────────────────────────────┐    │
│  │                                     │    │
│  │  hey how's it going                 │    │
│  │                                     │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  │
│  │ Replace  │  │   Copy   │  │ Dismiss  │  │
│  └──────────┘  └──────────┘  └──────────┘  │
│                                             │
└─────────────────────────────────────────────┘
```

### 5.3 Settings Screen

```
┌─────────────────────────────────────────────┐
│  ← Morfoboard Settings                      │
│                                             │
│  ┌─────────────────────────────────────┐    │
│  │ 👤 Google Account                   │    │
│  │ user@gmail.com              [Sign Out]   │
│  └─────────────────────────────────────┘    │
│                                             │
│  ┌─────────────────────────────────────┐    │
│  │ 🌐 Target Languages                 │    │
│  │                                     │    │
│  │  ☑ Indonesian (id)                  │    │
│  │  ☑ Javanese (jv)                    │    │
│  │  ☑ English (en)           ◀ Active  │    │
│  │  ☐ Spanish (es)                     │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  ┌─────────────────────────────────────┐    │
│  │ ✍️  Writing Tone                    │    │
│  │                                     │    │
│  │  ○ Casual                           │    │
│  │  ● Natural                ◀ Active  │    │
│  │  ○ Formal                           │    │
│  │  ○ Professional                     │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  ┌─────────────────────────────────────┐    │
│  │ ℹ️  About                           │    │
│  │ Morfoboard v0.1.0                   │    │
│  └─────────────────────────────────────┘    │
│                                             │
└─────────────────────────────────────────────┘
```

### 5.4 Color Palette (Dark Theme)

| Element | Color | Note |
|---------|-------|------|
| Keyboard background | `#1A1A1A` | Deep dark |
| Key background | `#2D2D2D` | Subtle elevation |
| Key text | `#FFFFFF` | High contrast |
| Action bar background | `#111111` | Darker than keyboard |
| Action button | `#3B82F6` | Blue accent |
| Bottom sheet background | `#1E1E1E` | Slightly elevated |
| Bottom sheet text | `#F5F5F5` | Near white |
| Success state | `#22C55E` | Green |
| Error state | `#EF4444` | Red |
| Loading state | `#3B82F6` | Blue pulse |

---

## 6. Security Considerations

| Concern | Mitigation |
|---------|-----------|
| API key exposure | API key stored on BE only, never on client |
| Token theft | EncryptedSharedPreferences for Google tokens |
| Token expiry | Silent refresh before expiry; graceful fallback |
| Man-in-the-middle | HTTPS only for all API communication |
| Input validation | BE validates all request parameters |
| Rate limiting (future) | Architecture prepared in BE, not active in MVP |

---

## 7. Project Structure

```
morfoboard/
├── android/                          # Android IME app
│   ├── app/
│   │   ├── src/
│   │   │   ├── main/
│   │   │   │   ├── kotlin/com/morfoboard/app/
│   │   │   │   │   ├── MorfoboardApp.kt           # Application class
│   │   │   │   │   ├── ime/
│   │   │   │   │   │   ├── MorfoboardIME.kt       # InputMethodService
│   │   │   │   │   │   ├── KeyboardView.kt        # Custom keyboard view
│   │   │   │   │   │   ├── ActionBarController.kt # AI action buttons
│   │   │   │   │   │   └── BottomSheetPresenter.kt
│   │   │   │   │   ├── ai/
│   │   │   │   │   │   ├── AIClient.kt            # HTTP client
│   │   │   │   │   │   ├── AIRequest.kt           # Request models
│   │   │   │   │   │   ├── AIResponse.kt          # Response models
│   │   │   │   │   │   └── PromptBuilder.kt       # System prompts
│   │   │   │   │   ├── auth/
│   │   │   │   │   │   ├── AuthManager.kt         # Google Sign-In
│   │   │   │   │   │   └── TokenStore.kt          # Encrypted storage
│   │   │   │   │   ├── settings/
│   │   │   │   │   │   ├── SettingsActivity.kt
│   │   │   │   │   │   └── MorfoboardSettings.kt  # Data class
│   │   │   │   │   └── theme/
│   │   │   │   │       └── ThemeProvider.kt
│   │   │   │   ├── res/
│   │   │   │   │   ├── layout/
│   │   │   │   │   │   ├── keyboard_view.xml
│   │   │   │   │   │   ├── action_bar.xml
│   │   │   │   │   │   ├── bottom_sheet_result.xml
│   │   │   │   │   │   └── activity_settings.xml
│   │   │   │   │   ├── xml/
│   │   │   │   │   │   └── method.xml            # IME config
│   │   │   │   │   ├── drawable/
│   │   │   │   │   ├── values/
│   │   │   │   │   │   ├── colors.xml
│   │   │   │   │   │   ├── strings.xml
│   │   │   │   │   │   └── styles.xml
│   │   │   │   │   └── font/
│   │   │   │   └── AndroidManifest.xml
│   │   │   └── test/
│   │   │       └── kotlin/com/morfoboard/app/
│   │   │           ├── ai/
│   │   │           │   ├── PromptBuilderTest.kt
│   │   │           │   └── AIClientTest.kt
│   │   │           └── settings/
│   │   │               └── MorfoboardSettingsTest.kt
│   │   └── build.gradle.kts
│   ├── gradle/
│   └── settings.gradle.kts
│
├── backend/                          # Go backend (auth proxy)
│   ├── cmd/
│   │   └── server/
│   │       └── main.go
│   ├── internal/
│   │   ├── auth/
│   │   │   ├── verifier.go          # Google token verification
│   │   │   └── verifier_test.go
│   │   ├── proxy/
│   │   │   ├── handler.go           # /api/v1/ai/process
│   │   │   ├── handler_test.go
│   │   │   └── prompts.go           # System prompt templates
│   │   ├── ratelimit/
│   │   │   ├── limiter.go           # Stub: interface + basic impl
│   │   │   └── limiter_test.go
│   │   ├── health/
│   │   │   └── handler.go           # /api/v1/health
│   │   └── config/
│   │       └── config.go
│   ├── go.mod
│   ├── go.sum
│   └── Dockerfile
│
├── docs/
│   ├── prd/
│   │   └── morfoboard.md            # This file
│   └── architecture/
│       └── grill_me_answers.md       # Grill session consensus
│
└── README.md
```

---

## 8. Development Phases

### Phase 1: Foundation (Week 1)
- Android project setup with IME skeleton
- Backend Go project setup with health endpoint
- Google Cloud Console setup + OAuth client
- Basic keyboard rendering (QWERTY, dark theme)

### Phase 2: Auth & Connectivity (Week 2)
- Google Sign-In integration (Android)
- Token storage (EncryptedSharedPreferences)
- Backend: Google token verification
- Backend: Proxy to 9router
- End-to-end: authenticated AI request

### Phase 3: Core Features (Week 3)
- Action bar (Translate + Fix Text)
- Translation flow with language picker
- Typo/grammar correction flow
- Bottom sheet result preview
- Prompt engineering & testing

### Phase 4: Polish & Settings (Week 4)
- Settings Activity (language, tone, account)
- Error handling & offline states
- Loading states & animations
- Testing & bug fixes
- Release preparation

---

## 9. Future Considerations (Post-MVP)

| Feature | Priority | Notes |
|---------|----------|-------|
| Light theme | Medium | User preference toggle |
| Rate limiting | High | Architecture ready, activate when needed |
| Usage tracking | Medium | Per-user stats in BE |
| More languages | Medium | Easy to add via settings |
| Autocomplete | Low | Significant UX change |
| On-device model | Low | Requires model optimization |
| iOS support | Low | Would need Kotlin Multiplatform or Swift rewrite |
| Model selection UI | Low | Let power users pick model from keyboard |
| Voice input | Low | Android SpeechRecognizer integration |

---

## 10. Open Items

| Item | Owner | Status |
|------|-------|--------|
| Google Cloud Console project creation | Pak BOS | Pending |
| OAuth 2.0 Client ID (Android) | Pak BOS + Hermes | Pending |
| SHA-1 fingerprint generation | Hermes | Pending (during build setup) |
| 9router endpoint URL | Pak BOS | Pending |
| 9router API key | Pak BOS | Pending (goes in BE config) |
| VPS Sumopod access for BE deployment | Pak BOS | Pending |
| LLM model selection for 9router | Pak BOS | Pending |

---

*This PRD is the source of truth for Morfoboard v0.1. All implementation decisions should reference this document.*
