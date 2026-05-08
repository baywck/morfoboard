# Morfoboard — AI-Powered Android Keyboard

Morfoboard adalah keyboard Android (IME) dengan kemampuan AI untuk membantu pengguna menulis lebih cepat, rapi, dan natural. Fokus awalnya adalah dua aksi yang paling sering dibutuhkan saat mengetik di aplikasi apa pun: menerjemahkan teks dan memperbaiki typo/grammar tanpa harus copy-paste ke aplikasi lain.

Repository ini dipakai dengan strategi branch berikut:

- `main` — branch utama untuk aplikasi Android keyboard.
- `backend` — branch untuk service backend/proxy Morfoboard.

> Catatan: struktur folder saat ini masih memuat `android/` dan `backend/` agar pengembangan end-to-end tetap mudah. Secara operasional, `main` diperlakukan sebagai jalur Android dan `backend` sebagai jalur backend.

## Kenapa Morfoboard?

Keyboard adalah titik paling dekat dengan niat pengguna. Kalau AI hanya tersedia di aplikasi chat terpisah, pengguna harus berpindah konteks: copy teks, buka AI, prompt, salin hasil, kembali ke aplikasi asal. Morfoboard memindahkan AI ke tempat pengguna benar-benar menulis.

Target produk:

- Membantu pengguna bilingual/multilingual menulis lebih percaya diri.
- Membuat koreksi teks terasa instan, bukan workflow tambahan.
- Menjadi keyboard produktivitas yang ringan, privat, dan bisa dikendalikan sendiri backend/model-nya.
- Mengarah ke konsep AI writing layer di atas semua aplikasi Android.

## Arsitektur

```text
┌─────────────────┐     HTTPS      ┌──────────────────┐     HTTP/S     ┌─────────────┐
│  Morfoboard App │ ──────────────>│  Morfoboard BE   │ ──────────────>│   9router   │
│  Android IME    │<────────────── │  Go on VPS       │<────────────── │  AI / LLM   │
└─────────────────┘                └──────────────────┘                └─────────────┘
```

### Komponen

| Komponen | Teknologi | Fungsi |
|---|---|---|
| Android App | Kotlin, InputMethodService | Keyboard UI, input text, voice input, settings, AI actions |
| Backend | Go, net/http | Auth verification, AI proxy, rate limiting, user tracking, blacklist |
| AI Router | 9router / OpenAI-compatible API | LLM inference untuk translate dan fix text |
| Auth | Google Sign-In | Identitas user dan akses aman ke backend |

## Fitur Saat Ini

- Full custom Android keyboard berbasis `InputMethodService`.
- QWERTY keyboard dengan shift, backspace, enter, space, symbols, numbers.
- AI Translate untuk menerjemahkan teks langsung dari keyboard.
- AI Fix Text untuk memperbaiki typo, grammar, dan struktur kalimat.
- Tone selection: casual, natural, formal, professional.
- Google Sign-In dengan penyimpanan token terenkripsi.
- Dark/light theme support.
- Color palette, key shape preset, dan keyboard size setting.
- Native speech recognition / voice input.
- Offline detection sebelum menjalankan aksi AI.
- Backend proxy dengan validasi action agar hanya `translate` dan `fix_text` yang diterima.
- User tracking dan blacklist system di backend.
- Docker support untuk deployment backend.

## Struktur Project

```text
.
├── android/                  # Aplikasi Android IME
│   ├── app/src/main/kotlin/com/morfoboard/app/
│   │   ├── MainActivity.kt   # Setup screen untuk enable/switch keyboard
│   │   ├── ime/              # MorfoboardIME, keyboard view, action bar
│   │   ├── ai/               # AI client dan model request/response
│   │   ├── auth/             # Google auth/token manager
│   │   ├── settings/         # Settings activity dan preference store
│   │   └── network/          # Connectivity monitor
│   └── gradle/               # Gradle wrapper dan version catalog
├── backend/                  # Go backend service
│   ├── cmd/server/           # Entrypoint server
│   ├── internal/proxy/       # Handler AI proxy
│   ├── internal/users/       # User tracking/blacklist
│   ├── Dockerfile
│   └── docker-compose.yml
└── README.md
```

## Setup Development

### Prerequisites

1. Android Studio atau Android SDK CLI.
2. Android device/emulator API 26+.
3. Go 1.22+ untuk backend.
4. VPS atau endpoint OpenAI-compatible seperti 9router.
5. Google Cloud Console project dengan:
   - Google Sign-In enabled.
   - OAuth 2.0 Web Client ID.
   - SHA-1 fingerprint debug/release terdaftar.

Debug SHA-1 yang pernah dipakai di environment lokal:

```text
2F:80:F9:F7:CE:9B:54:0C:25:FB:97:A6:D9:A8:70:0B:5C:4E:59:70
```

### Backend

```bash
cd backend
cp .env.example .env
# Edit .env sesuai endpoint/model/key yang digunakan.

go run ./cmd/server
```

Docker:

```bash
cd backend
docker compose up --build
```

Environment utama backend biasanya mencakup:

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

Setelah APK terinstall:

1. Buka aplikasi Morfoboard.
2. Pilih `Enable Morfoboard` untuk membuka Android keyboard settings.
3. Aktifkan Morfoboard.
4. Pilih `Switch to Morfoboard` untuk menjadikannya keyboard aktif.
5. Login Google jika ingin memakai fitur AI.

ADB helper:

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

Action yang valid:

- `translate`
- `fix_text`

Contoh response:

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

### Android branch (`main`)

Gunakan `main` untuk pekerjaan Android:

```bash
git checkout main
cd android
./gradlew assembleDebug
```

Contoh fokus pekerjaan:

- Keyboard UI/UX.
- IME lifecycle stability.
- Voice input.
- Settings dan personalization.
- Android auth/token handling.
- AI client integration.

### Backend branch (`backend`)

Gunakan `backend` untuk pekerjaan backend:

```bash
git checkout backend
cd backend
go test ./...
go run ./cmd/server
```

Contoh fokus pekerjaan:

- AI proxy.
- Auth verification.
- User tracking.
- Rate limiting dan abuse prevention.
- Deployment Docker/VPS.
- Observability dan logs.

## Rencana Pengembangan Kedepan

### Phase 1 — Stabilitas Keyboard Core

- Perkuat lifecycle IME: pastikan tidak crash saat pindah field, rotate screen, switch app, atau keyboard picker.
- Improve text extraction/replacement agar konsisten di WhatsApp, Telegram, browser, notes, Gmail, dan editor web.
- Tambahkan test matrix device: low-end Android, high DPI, tablet kecil, dan emulator.
- Polish haptic, sound, long press, cursor movement, dan key repeat.
- Tambahkan fallback ketika `InputConnection` tidak memberi full text.

### Phase 2 — AI Experience yang Lebih Cepat dan Natural

- Streaming response atau progressive loading agar AI terasa hidup, bukan menunggu kosong.
- Preset rewrite: pendekkan, panjangkan, lebih sopan, lebih santai, lebih profesional.
- Smart language detection sebelum translate.
- Quick action chip berdasarkan konteks: “Fix”, “Translate to EN”, “Make formal”.
- Local cache untuk hasil terakhir agar pengguna bisa undo/redo.
- Safety guard agar AI tidak mengubah maksud teks terlalu jauh.

### Phase 3 — Personalization dan Premium Keyboard Feel

- Theme builder yang tetap premium: matte dark, light clean, compact, tanpa neon/RGB berlebihan.
- Per-app preference: misalnya tone formal untuk Gmail/LinkedIn, casual untuk WhatsApp.
- Personal dictionary dan custom phrase snippets.
- Clipboard manager privat dengan mode auto-expire.
- Custom toolbar layout: user bisa pilih action mana yang muncul.

### Phase 4 — Privacy, Trust, dan BYO AI

- Mode “Bring Your Own AI”: user memasukkan endpoint/key sendiri.
- On-device rules untuk menentukan teks mana yang tidak boleh dikirim ke server.
- Private mode per aplikasi: disable AI di banking/password/OTP fields.
- Transparent AI log: tampilkan request metadata tanpa menyimpan isi sensitif.
- Data retention policy yang jelas di README dan app onboarding.

### Phase 5 — Backend Production Hardening

- Rate limiting per user/device/IP.
- Quota dan usage dashboard.
- Request tracing dengan request ID.
- Structured logs dan metrics.
- Admin blacklist/allowlist dashboard.
- Model routing: cepat untuk fix typo, lebih pintar untuk rewrite kompleks.
- Cost control: max token, timeout, retry policy, circuit breaker.

### Phase 6 — Distribution dan Growth

- Signed release build dan release notes otomatis.
- Landing page dengan demo GIF/video.
- Closed testing Play Store.
- Feedback loop dalam app: laporkan bug, request fitur, kirim rating UX.
- Template prompt untuk komunitas: bahasa Indonesia, English, bisnis, akademik, coding.

## Masukan Strategis untuk Membuat Morfoboard Lebih Keren

1. Jadikan Morfoboard bukan sekadar “keyboard + tombol AI”, tapi “writing co-pilot layer” untuk seluruh Android.

   Diferensiasinya bukan jumlah fitur, tapi seberapa cepat pengguna bisa memperbaiki niat menulis tanpa keluar dari aplikasi yang sedang dipakai.

2. Fokus ke micro-interaction yang super cepat.

   Keyboard harus terasa instan. Kalau AI butuh 2-5 detik, UI harus memberi feedback yang elegan: loading compact, bisa cancel, hasil muncul di bottom sheet yang tidak mengganggu mengetik.

3. Buat “AI actions” berbasis konteks, bukan menu panjang.

   Saat teks pendek: tampilkan Fix dan Translate. Saat teks formal panjang: tampilkan Summarize, Make Polite, Shorten. Saat ada campuran Indo-English: tampilkan Naturalize. Ini membuat Morfoboard terasa pintar tanpa terlihat ribet.

4. Jadikan privasi sebagai selling point.

   Banyak keyboard populer terasa menyeramkan karena semua yang diketik melewati keyboard. Morfoboard bisa unggul dengan private mode, BYO endpoint, transparansi request, dan policy “password/OTP/banking fields tidak diproses AI”.

5. Bangun “Indonesian-first intelligence”.

   Banyak AI writing tool bagus untuk English, tapi kurang natural untuk Bahasa Indonesia, Jaksel style, formal kantor Indonesia, chat marketplace, caption UMKM, atau bahasa guru/siswa. Morfoboard bisa punya moat di gaya bahasa lokal.

6. Tambahkan mode “template cepat”.

   Contoh: balas customer, follow-up invoice, izin sakit, caption produk, chat dosen/guru, lamaran kerja, complaint sopan. Ini bisa jadi fitur premium yang sangat praktis.

7. Jangan terlalu banyak fitur visual dulu.

   Keyboard yang bagus menang di feel: layout presisi, spacing enak, typo rendah, backspace nyaman, prediction/action cepat. Visual premium penting, tapi typing accuracy lebih penting.

8. Siapkan jalan menuju on-device AI.

   Untuk versi awal backend proxy sudah benar. Tapi roadmap jangka panjang bisa hybrid: typo ringan dan suggestion pendek di device, rewrite/translate kompleks ke server. Ini akan mengurangi latency dan biaya.

9. Buat “Morfoboard for Teams”.

   B2B-nya menarik: perusahaan bisa punya tone guide, glossary, forbidden words, customer support templates, dan model endpoint sendiri. Keyboard jadi brand communication layer.

10. Ukur metrik yang benar.

   Jangan hanya MAU/DAU. Ukur: AI action success rate, replacement rate, cancel rate, latency p95, crash-free sessions, average characters processed, dan repeat action per user. Itu metrik yang benar-benar menunjukkan value keyboard.

## Prinsip Produk

- Fast first: typing tidak boleh kalah cepat dari keyboard biasa.
- Privacy by design: jangan proses teks sensitif tanpa izin eksplisit.
- Context-aware: fitur muncul sesuai kebutuhan, bukan semua ditampilkan sekaligus.
- Indonesian-friendly: natural untuk bahasa Indonesia formal, santai, dan campuran.
- User-controlled AI: backend/model/key bisa dikendalikan, bukan vendor lock-in.

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

### Keyboard tidak muncul

- Pastikan app punya launcher activity.
- Aktifkan keyboard dari Android settings.
- Jalankan:

```bash
adb shell ime list -a
adb shell ime enable com.morfoboard.app/.ime.MorfoboardIME
adb shell ime set com.morfoboard.app/.ime.MorfoboardIME
```

### AI action gagal

- Pastikan device online.
- Pastikan sudah login Google.
- Pastikan backend reachable dari device.
- Pastikan `GOOGLE_CLIENT_ID` backend sama dengan Web Client ID Android sign-in.
- Cek backend logs dan response dari AI router.

### Backend tidak bisa memanggil model

- Cek `NINE_ROUTER_URL`.
- Cek `NINE_ROUTER_API_KEY`.
- Cek timeout dan payload size.
- Coba request manual ke endpoint OpenAI-compatible.

## License

MIT
