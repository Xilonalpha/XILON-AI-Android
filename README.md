# XILON AI Android

A new independent Android application for discovering and eventually running local AI models.

## Implemented
- Kotlin / Jetpack Compose Android project (minSdk 29)
- Device information: Android version, supported ABIs, RAM, free storage
- Live Hugging Face GGUF-tagged model catalog (up to 50 results)
- Sort by recency or downloads, refresh and network-error handling
- GitHub Actions debug APK build workflow

## Not implemented yet
- GGUF file metadata validation and device compatibility estimation
- Download manager and file verification
- llama.cpp/JNI inference
- Offline chat and model management

**Important:** GGUF-tagged models are *not* guaranteed to be compatible with a given phone. The catalog currently labels all entries as unverified.

## Build
Use Android Studio with JDK 17 and Android SDK 35, or the GitHub Actions workflow in this repository. Workflow uses Gradle 8.10.2; wrapper scripts are not included yet.
