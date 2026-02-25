# GoldPulse (Android, Kotlin + Jetpack Compose)

GoldPulse tracks live gold prices (XAU) and alerts when price change exceeds your threshold.

## Features
- **Kotlin + Jetpack Compose** UI with subtle animations (fade + alpha transitions)
- **Live gold price** from free API: `https://api.gold-api.com/price/XAU/{CURRENCY}`
- **Repository abstraction** (`GoldRepository`) with Retrofit + OkHttp
- **Chart** via MPAndroidChart (line trend from locally stored history)
- **Settings** persisted with DataStore:
  - Alert threshold percentage
  - Target currency (USD/EUR/GBP/AED)
  - Check interval (default 10 minutes)
- **Background checks** via WorkManager (self-rescheduling worker)
- **Notifications** when threshold is exceeded
- **Multilingual resources**: English, German, Arabic

## Project Structure
- `app/src/main/java/com/goldpulse/data/network` → Retrofit/OkHttp API
- `app/src/main/java/com/goldpulse/data/repository` → repository implementation
- `app/src/main/java/com/goldpulse/data/local` → DataStore preferences
- `app/src/main/java/com/goldpulse/worker` → WorkManager worker + scheduler
- `app/src/main/java/com/goldpulse/ui` → Compose screens/components/theme

## Build & Run
1. Open folder `GoldPulse` in **Android Studio Iguana+** (or newer).
2. Let Gradle sync dependencies.
3. Run on emulator/device (Android 8.0+).
4. On Android 13+, allow notification permission.

### Notes on background interval
- App default is **10 minutes**.
- Instead of `PeriodicWorkRequest` (min 15 min), this app uses **OneTimeWorkRequest re-scheduling** to support a 10-minute cadence.
- Actual execution can still vary depending on Android battery optimizations/Doze.

## Dependencies (main)
- Compose Material3
- Retrofit + Gson Converter
- OkHttp + Logging Interceptor
- DataStore Preferences
- WorkManager
- MPAndroidChart

## API
- Free source used: `gold-api.com`
- Endpoint pattern: `/price/XAU/{currency}`

## Quick status
- ✅ Complete Android project scaffold with Gradle files
- ✅ Compose UI + settings + chart + refresh
- ✅ DataStore persistence
- ✅ Retrofit repository abstraction
- ✅ Worker-based periodic checks and notifications
- ✅ Localization resources for EN/DE/AR
