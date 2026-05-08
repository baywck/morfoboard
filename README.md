# Morfoboard — AI Writing Layer for Android

Morfoboard is an AI-powered Android keyboard that brings translation, rewriting, and text correction directly into every app where people type. Instead of forcing users to copy text into a separate AI chatbot, Morfoboard embeds AI assistance at the input layer: WhatsApp, Telegram, Gmail, browsers, notes, CRMs, marketplaces, and any Android text field.

The product vision is simple: turn the keyboard into a private, fast, context-aware writing co-pilot.

Repository workflow:

- `main` — Android keyboard branch.
- `backend` — backend/proxy branch.

> The repository currently keeps both `android/` and `backend/` folders so the product can be developed and tested end-to-end. Operationally, `main` is used as the Android branch and `backend` is used as the backend branch.

## What Morfoboard Solves

AI writing tools are powerful, but the workflow is still broken. Users usually need to:

1. Select or copy text from an app.
2. Open an AI chatbot.
3. Write a prompt.
4. Wait for the response.
5. Copy the result back.
6. Fix formatting or tone manually.

That context switching is too slow for daily messaging, customer replies, school communication, professional email, marketplace chats, and multilingual conversations.

Morfoboard removes the friction by moving AI to the point of intent: the keyboard. Users can translate, fix, rewrite, and improve text without leaving the app they are already using.

## Product Positioning

Morfoboard is not just “a keyboard with AI buttons.” It is an AI writing layer for Android.

The long-term opportunity is to become the universal text productivity interface across mobile apps, especially for users who write across Indonesian, English, and mixed-language contexts.

Core principles:

- Fast first: typing must feel as responsive as a normal keyboard.
- Privacy by design: sensitive fields should never be processed without explicit user control.
- Context-aware: show the right AI actions at the right moment instead of exposing a crowded menu.
- Indonesian-first intelligence: support formal Indonesian, casual Indonesian, business tone, marketplace replies, school communication, and Indo-English writing.
- User-controlled AI: support self-hosted or bring-your-own AI endpoints instead of forcing vendor lock-in.

## Architecture

```text
┌─────────────────┐     HTTPS      ┌──────────────────┐     HTTP/S     ┌─────────────┐
│  Morfoboard App │ ──────────────>│  Morfoboard BE   │ ──────────────>│   9router   │
│  Android IME    │<────────────── │  Go on VPS       │<────────────── │  AI / LLM   │
└─────────────────┘                └──────────────────┘                └─────────────┘
```

### Components

| Component | Technology | Purpose |
|---|---|---|
| Android App | Kotlin, InputMethodService | Custom keyboard UI, text input, AI actions, voice input, settings |
| Backend | Go, net/http | Auth verification, AI proxy, rate limiting, user tracking, blacklist controls |
| AI Router | 9router / OpenAI-compatible API | LLM inference for translation and text correction |
| Auth | Google Sign-In | User identity and secure access to the backend |

## AI-Driven Workflow

Morfoboard uses an AI-driven input workflow:

1. The Android IME reads the active text context through `InputConnection`.
2. The user triggers an AI action such as Translate or Fix Text from the keyboard toolbar.
3. The client validates connectivity, authentication, selected language, selected tone, and current text.
4. The request is sent to the Go backend with a strict action schema.
5. The backend verifies the Google ID token, checks user status and blacklist rules, applies request limits, and builds a controlled prompt for the selected action.
6. The backend forwards the request to an OpenAI-compatible model endpoint through 9router.
7. The result returns to the keyboard as a structured response.
8. Morfoboard displays the output in a lightweight keyboard-level result sheet.
9. The user can replace the original text, dismiss it, or continue typing.

This creates a closed-loop AI workflow inside the typing experience: capture context, reason through the requested transformation, return a safe structured result, and let the user apply it immediately.

## Current Features

- Custom Android keyboard built with `InputMethodService`.
- Full QWERTY layout with shift, backspace, enter, space, symbols, and numbers.
- AI Translate directly from the keyboard.
- AI Fix Text for grammar, spelling, typo, and sentence improvements.
- Tone selection: casual, natural, formal, professional.
- Google Sign-In with encrypted token storage.
- Dark and light theme support.
- Color palettes, key shape presets, and keyboard size settings.
- Native speech recognition / voice input.
- Offline detection before AI requests.
- Backend proxy that only accepts valid actions such as `translate` and `fix_text`.
- User tracking and blacklist system in the backend.
- Docker support for backend deployment.

## Why This Can Win

Most mobile AI products live outside the actual writing surface. Morfoboard lives inside the writing surface.

That gives it several advantages:

- Lower friction than chatbot-based workflows.
- Works across almost every Android app.
- Can become a habit because it sits in the default typing path.
- Can support personal, professional, and business communication from one interface.
- Can evolve into a team-controlled writing layer with glossary, tone guide, templates, and private AI endpoints.

The strongest wedge is Indonesian-first AI writing. Many tools handle English well, but fewer tools understand Indonesian business tone, casual chat, marketplace replies, school communication, and natural Indo-English switching. Morfoboard can own that local intelligence layer.

## Project Structure

```text
.
├── android/                  # Android IME application
│   ├── app/src/main/kotlin/com/morfoboard/app/
│   │   ├── MainActivity.kt   # Setup screen for enabling/switching keyboard
│   │   ├── ime/              # MorfoboardIME, keyboard view, action bar
│   │   ├── ai/               # AI client and request/response models
│   │   ├── auth/             # Google auth and token manager
│   │   ├── settings/         # Settings activity and preference store
│   │   └── network/          # Connectivity monitor
│   └── gradle/               # Gradle wrapper and version catalog
├── backend/                  # Go backend service
│   ├── cmd/server/           # Server entrypoint
│   ├── internal/proxy/       # AI proxy handler
│   ├── internal/users/       # User tracking and blacklist logic
│   ├── Dockerfile
│   └── docker-compose.yml
└── README.md
```

## Development Setup

### Prerequisites

1. Android Studio or Android SDK CLI.
2. Android device/emulator API 26+.
3. Go 1.22+ for backend development.
4. VPS or OpenAI-compatible endpoint such as 9router.
5. Google Cloud Console project with:
   - Google Sign-In enabled.
   - OAuth 2.0 Web Client ID.
   - Debug/release SHA-1 fingerprint registered.

Debug SHA-1 used in the local development environment:

```text
2F:80:F9:F7:CE:9B:54:0C:25:FB:97:A6:D9:A8:70:0B:5C:4E:59:70
```

### Backend

```bash
cd backend
cp .env.example .env
# Edit .env with your endpoint, model, API key, and Google client ID.

go run ./cmd/server
```

Docker:

```bash
cd backend
docker compose up --build
```

Typical backend environment variables:

```text
PORT=8080
NINE_ROUTER_URL=https://your-openai-compatible-endpoint/v1/chat/completions
NINE_ROUTER_API_KEY=your_api_key
GOOGLE_CLIENT_ID=your_google_web_client_id
```

### Android

```bash
cd android
./gradlew assembleDebug
./gradlew installDebug
```

After installing the APK:

1. Open the Morfoboard app.
2. Tap `Enable Morfoboard` to open Android keyboard settings.
3. Enable Morfoboard.
4. Tap `Switch to Morfoboard` to make it the active keyboard.
5. Sign in with Google to use AI features.

ADB helper commands:

```bash
adb shell ime list -a
adb shell ime enable com.morfoboard.app/.ime.MorfoboardIME
adb shell ime set com.morfoboard.app/.ime.MorfoboardIME
adb shell settings get secure default_input_method
```

## API Endpoints

### Health Check

```http
GET /api/v1/health
```

### AI Process

```http
POST /api/v1/ai/process
Authorization: Bearer <google_id_token>
Content-Type: application/json

{
  "action": "translate",
  "text": "Halo apa kabar?",
  "source_language": "id",
  "target_language": "en",
  "tone": "natural"
}
```

Valid actions:

- `translate`
- `fix_text`

Example response:

```json
{
  "success": true,
  "result": "Hi, how are you?",
  "original": "Halo apa kabar?",
  "model_used": "selected-model",
  "processing_time_ms": 1200
}
```

## Branch Workflow

### Android Branch (`main`)

Use `main` for Android work:

```bash
git checkout main
cd android
./gradlew assembleDebug
```

Typical scope:

- Keyboard UI/UX.
- IME lifecycle stability.
- Voice input.
- Settings and personalization.
- Android auth/token handling.
- AI client integration.

### Backend Branch (`backend`)

Use `backend` for backend work:

```bash
git checkout backend
cd backend
go test ./...
go run ./cmd/server
```

Typical scope:

- AI proxy.
- Auth verification.
- User tracking.
- Rate limiting and abuse prevention.
- Docker/VPS deployment.
- Observability and logging.

## Product Roadmap

### Phase 1 — Keyboard Core Stability

- Harden the IME lifecycle so it does not crash when switching fields, rotating screens, switching apps, or opening the keyboard picker.
- Improve text extraction and replacement across WhatsApp, Telegram, browsers, notes, Gmail, and web editors.
- Build a test matrix for low-end Android devices, high-DPI screens, compact phones, and emulators.
- Polish haptics, sound, long press behavior, cursor movement, and key repeat.
- Add fallback behavior when `InputConnection` cannot provide full text context.

### Phase 2 — Faster and More Natural AI UX

- Add streaming or progressive loading so AI actions feel responsive.
- Add rewrite presets: shorten, expand, make polite, make casual, make professional.
- Add smart language detection before translation.
- Show contextual quick actions such as “Fix”, “Translate to English”, or “Make Formal”.
- Add local undo/redo for the last AI replacement.
- Add safety guards so AI does not change the user’s meaning too aggressively.

### Phase 3 — Personalization and Premium Keyboard Feel

- Add a premium theme builder: matte dark, clean light, compact layout, no distracting neon/RGB styling.
- Add per-app preferences: formal tone for Gmail/LinkedIn, casual tone for WhatsApp, etc.
- Add personal dictionary and custom phrase snippets.
- Add a private clipboard manager with auto-expiring entries.
- Let users customize the toolbar actions.

### Phase 4 — Privacy, Trust, and BYO AI

- Add Bring Your Own AI endpoint/key support.
- Add on-device rules that decide which text is never sent to the server.
- Add private mode for banking, password, OTP, and sensitive fields.
- Add transparent AI metadata logs without storing sensitive content.
- Publish a clear data retention policy inside the README and onboarding flow.

### Phase 5 — Backend Production Hardening

- Add rate limiting per user, device, and IP.
- Add quota and usage dashboard.
- Add request tracing with request IDs.
- Add structured logs and metrics.
- Add admin blacklist/allowlist dashboard.
- Add model routing: fast model for typo fixes, stronger model for complex rewrites.
- Add cost control through max tokens, timeout, retry policy, and circuit breakers.

### Phase 6 — Distribution and Growth

- Add signed release builds and automatic release notes.
- Build a landing page with demo GIFs/videos.
- Launch closed testing on Google Play.
- Add in-app feedback for bug reports and feature requests.
- Create prompt/template packs for Indonesian, English, business, academic, marketplace, and customer support writing.

## Strategic Product Ideas

1. Turn Morfoboard into a universal Android writing co-pilot, not just a keyboard utility.

2. Make micro-interactions extremely fast. If AI takes a few seconds, the keyboard should show compact loading, cancellation, and non-blocking result previews.

3. Use context-aware actions instead of a long AI menu. Short text can show Fix and Translate. Formal long text can show Shorten, Make Polite, or Summarize. Mixed Indonesian-English text can show Naturalize.

4. Make privacy a selling point. Users are naturally cautious about keyboards because keyboards see everything. Morfoboard should win trust through private mode, BYO endpoints, transparent request metadata, and sensitive-field protections.

5. Build Indonesian-first intelligence. Support formal Indonesian, casual Indonesian, Jaksel-style mixed language, marketplace replies, school communication, and professional office tone.

6. Add quick templates for high-frequency use cases: customer replies, invoice follow-ups, sick leave requests, product captions, polite complaints, teacher/student messages, and job applications.

7. Prioritize typing feel before visual complexity. Great keyboards win through accuracy, spacing, key feedback, comfortable backspace, and low typo rates.

8. Prepare for hybrid on-device AI. Lightweight typo fixes and suggestions can eventually run locally, while complex rewrites and translations go to the server.

9. Explore “Morfoboard for Teams.” Companies could define tone guides, glossaries, forbidden words, approved templates, and private model endpoints.

10. Track the metrics that matter: AI action success rate, replacement rate, cancellation rate, p95 latency, crash-free sessions, average characters processed, and repeat actions per user.

## Development Commands

```bash
# Android build
cd android
./gradlew assembleDebug

# Android install
cd android
./gradlew installDebug

# Backend test
cd backend
go test ./...

# Backend run
cd backend
go run ./cmd/server

# Backend Docker
cd backend
docker compose up --build
```

## Troubleshooting

### Keyboard does not appear

- Make sure the app has a launcher activity.
- Enable the keyboard from Android settings.
- Run:

```bash
adb shell ime list -a
adb shell ime enable com.morfoboard.app/.ime.MorfoboardIME
adb shell ime set com.morfoboard.app/.ime.MorfoboardIME
```

### AI action fails

- Make sure the device is online.
- Make sure the user is signed in with Google.
- Make sure the backend is reachable from the device.
- Make sure backend `GOOGLE_CLIENT_ID` matches the Android Google Sign-In Web Client ID.
- Check backend logs and the AI router response.

### Backend cannot call the model

- Check `NINE_ROUTER_URL`.
- Check `NINE_ROUTER_API_KEY`.
- Check timeout and payload size.
- Try a manual request to the OpenAI-compatible endpoint.

## License

MIT
