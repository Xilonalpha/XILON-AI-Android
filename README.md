# XILON AI Android

Independent Android application for discovering and eventually running local AI models.

## Current implementation (v0.7 development)
- Kotlin + Jetpack Compose UI (Android 10+)
- Device information: RAM, ABI, free app storage
- Live Hugging Face GGUF-tagged model catalog
- Per-file size and conservative RAM estimates
- GGUF download to app-private storage with HTTP Range resume, cancellation and progress
- GGUF magic/version check, file length check, SHA-256 calculation
- LFS SHA-256 comparison when available in Hub metadata
- Installed model listing and confirmation before deletion

## Important limitations
- No llama.cpp integration yet: downloaded GGUF files cannot run in chat.
- Resource estimates are NOT guarantees of model compatibility.
- The Hub model list is currently limited to 50 results per request.
- Downloads run on a background thread tied to the process, not a persistent WorkManager worker.
- SHA-256 validation is against a trusted remote digest only when supplied by the Hub.
- No confirmed successful GitHub Actions build or on-device test yet.

## Build
Use JDK 17, Android SDK 35 and Gradle 8.10.2. The GitHub Actions workflow attempts `gradle --no-daemon :app:assembleDebug`. Gradle wrapper scripts are not yet included.
