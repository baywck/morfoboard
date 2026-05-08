# Morfoboard — AI-Powered Android Keyboard

An Android keyboard (IME) connected to AI agents that can translate text between languages and fix spelling/grammar errors.

## Architecture

```
┌─────────────────┐     HTTPS      ┌──────────────────┐     HTTP/S     ┌─────────────┐
│  Morfoboard App │ ──────────────>│  Morfoboard BE   │ ──────────────>│   9router   │
│  (Android IME)  │<────────────── │  (Go on VPS)     │<────────────── │  (AI/LLM)   │
└─────────────────┘                └──────────────────┘                └─────────────┘
```

### Components

| Component | Tech | Purpose |
|-----------|------|---------|
| Android App | Kotlin, InputMethodService | Keyboard UI, text input, AI integration |
| Backend | Go, net/http | Auth verification, AI proxy, rate limiting |
| AI Router | 9router | LLM inference (user's VPS) |

## Features

- **Full QWERTY Keyboard** — Standard layout with shift, symbols, numbers
- **AI Translation** — Translate typed text to any supported language
- **Grammar/Typo Fix** — AI-powered correction of spelling and grammar
- **Tone Selection** — Choose writing style: casual, natural, formal, professional
- **Google Sign-In** — Secure authentication with encrypted token storage
- **Dark Theme** — Professional dark UI matching modern keyboard standards
- **Offline Detection** — Graceful handling when network is unavailable

## Setup

### Prerequisites

1. Android device or emulator (API 26+)
2. VPS with 9router or compatible OpenAI API endpoint
3. Google Cloud Console project with:
   - Google Sign-In enabled
   - OAuth 2.0 Web Client ID generated
   - SHA-1 fingerprint added

### Backend

```bash
cd backend
cp .env.example .env
# Edit .env with your settings

go run ./cmd/server
# Or with Docker:
docker build -t morfoboard-backend .
docker run -p 8080:8080 --env-file .env morfoboard-backend
```

### Android

1. Update `GoogleSignInManager.kt` with your Web Client ID
2. Update `MorfoboardIME.kt` with your backend URL
3. Build and install:
   ```bash
   cd android
   ./gradlew installDebug
   ```
4. On device: Settings → System → Languages → Keyboards → Add Morfoboard

## API Endpoints

### Health Check
```
GET /api/v1/health
```

### AI Process (Translate/Fix)
```
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

## Development

### SHA-1 Fingerprint (for Google Console)
```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android
```

### Build
```bash
# Backend
cd backend && go build ./cmd/server

# Android
cd android && ./gradlew assembleDebug
```

## License

MIT
