# ✦ Spark Dating App

A production-ready, open-source dating application for Android — built with Kotlin, Jetpack Compose, and Supabase. **100% free to run** at development scale.

---

## Free Stack

| Layer | Service | Cost |
|---|---|---|
| Database + Auth + Realtime | [Supabase](https://supabase.com) | Free tier |
| Storage (photos) | Supabase Storage | Free tier (1 GB) |
| Push notifications | Firebase Cloud Messaging | Free |
| Crash reporting | Firebase Crashlytics | Free |
| CI/CD | GitHub Actions | Free (public repo) |
| Admin dashboard hosting | [Vercel](https://vercel.com) | Free tier |
| App distribution | Google Play | $25 one-time |

---

## Architecture

```
spark/
├── app/                        # Main Android application module
├── core/
│   ├── model/                  # Domain models (pure Kotlin, no Android)
│   ├── network/                # Supabase client + DI
│   ├── database/               # Room cache + DataStore preferences
│   ├── auth/                   # Auth repository
│   └── ui/                     # Design system (theme, components)
├── feature/
│   ├── auth/                   # Login, Register, Welcome
│   ├── onboarding/             # Multi-step profile creation
│   ├── profile/                # My profile, Profile detail, Edit
│   ├── discovery/              # Swipe card deck
│   ├── matching/               # Matches grid, Likes screen
│   ├── chat/                   # Real-time messaging
│   ├── notifications/          # In-app notification centre
│   ├── settings/               # App preferences
│   ├── subscription/           # Google Play Billing / Premium
│   └── safety/                 # Report, Block, Safety centre
├── supabase/
│   ├── migrations/             # SQL schema (run in order)
│   └── functions/              # Edge Functions (Deno/TypeScript)
├── admin/                      # React + TypeScript admin dashboard
├── docs/                       # Architecture and API documentation
└── .github/workflows/          # CI/CD pipelines
```

**Pattern:** Clean Architecture · MVVM · Repository · Dependency Inversion · Hilt DI

---

## Quick Start

### 1. Supabase setup (5 min)

1. Create a free project at [supabase.com](https://supabase.com)
2. Go to **SQL Editor** and run migrations in order:
   ```
   supabase/migrations/001_initial_schema.sql
   supabase/migrations/002_storage.sql
   ```
3. Copy your **Project URL** and **anon key** from Project Settings → API

### 2. Firebase setup (5 min)

1. Create a project at [console.firebase.google.com](https://console.firebase.google.com)
2. Add an Android app with package name `com.spark.dating`
3. Download `google-services.json` → place in `app/`
4. Enable **Cloud Messaging** in the Firebase console

### 3. Android configuration

```bash
# Copy the example config
cp local.properties.example local.properties
```

Edit `local.properties`:
```properties
supabase.url=https://YOUR_PROJECT.supabase.co
supabase.anon_key=YOUR_ANON_KEY
```

### 4. Build and run

```bash
# Debug build (dev flavour)
./gradlew assembleDevDebug

# Or open in Android Studio and press Run
```

---

## Admin Dashboard Setup

```bash
cd admin
cp .env.example .env
# Fill in VITE_SUPABASE_URL and VITE_SUPABASE_SERVICE_ROLE_KEY

npm install
npm run dev        # http://localhost:5173
npm run build      # production build → deploy to Vercel
```

Grant admin access to a user by updating their role in Supabase:
```sql
UPDATE public.users SET role = 'moderator' WHERE email = 'admin@yourteam.com';
```

---

## Deploy Supabase Edge Functions

```bash
# Install Supabase CLI
npm install -g supabase

supabase login
supabase link --project-ref YOUR_PROJECT_REF

supabase functions deploy send-notification
supabase functions deploy verify-subscription

# Set secrets
supabase secrets set FCM_SERVER_KEY=your_fcm_key
supabase secrets set GOOGLE_PLAY_SERVICE_ACCOUNT_JSON='{"type":"service_account",...}'
```

---

## CI/CD Setup

### Required GitHub Secrets

| Secret | Where to get it |
|---|---|
| `SUPABASE_URL` | Supabase → Project Settings → API |
| `SUPABASE_ANON_KEY` | Supabase → Project Settings → API |
| `ANDROID_KEYSTORE_BASE64` | `base64 -i your.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | Your keystore password |
| `ANDROID_KEY_ALIAS` | Your key alias |
| `ANDROID_KEY_PASSWORD` | Your key password |
| `PLAY_SERVICE_ACCOUNT_JSON` | Google Play Console → Setup → API access |

### Generating a release keystore (one-time)

```bash
keytool -genkeypair \
  -alias spark-release \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -keystore spark-release.jks

# Encode for GitHub Secrets
base64 -i spark-release.jks | pbcopy   # macOS
# Paste as ANDROID_KEYSTORE_BASE64
```

---

## Running Tests

```bash
# Unit tests
./gradlew testDevDebugUnitTest

# All modules
./gradlew test --continue

# Lint
./gradlew lintDevDebug

# Detekt static analysis
./gradlew detekt
```

---

## Known Limitations / Next Steps

1. **EditProfileScreen** — form is stubbed; wire up the same fields as onboarding
2. **Google Sign-In OAuth** — requires SHA-1 fingerprint registered in Firebase + Supabase
3. **Image upload** — currently passes URI string; needs proper byte-array upload with compression
4. **Typing indicators** — Realtime channel exists; broadcast event not yet wired
5. **Push notification trigger** — Edge Function is deployed but DB trigger via `pg_net` needs enabling in Supabase
6. **Billing** — `BillingClient` wired to ViewModel but `launchBillingFlow` needs Activity reference passed via ViewModelStore
7. **Profile editing** — Mirrors onboarding flow; pre-populate fields from existing profile
8. **Photo reordering** — Drag-and-drop reorder for profile photos

---

## Security Notes

- Passwords are never stored — Supabase Auth handles hashing (bcrypt)
- Tokens stored in Android EncryptedSharedPreferences via Supabase SDK
- All API calls go through Row Level Security — users cannot access other users' private data
- Exact GPS coordinates are never sent to other clients — only approximate distance
- Age is verified server-side; the client cannot bypass it
- Admin endpoints (service role key) are only used in the admin dashboard — never in the Android app
- No secrets are committed — all credentials flow via environment variables

---

## License

MIT — see [LICENSE](LICENSE)
