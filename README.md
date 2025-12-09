# HybridMind - Offline/Online AI Chatbot

A hybrid Android app that seamlessly switches between cloud-based Gemini AI and on-device MediaPipe LLM based on network connectivity.

## Features

- 🔄 **Hybrid Intelligence**: Automatically switches between Gemini (online) and Gemma (offline)
- 🔐 **Firebase Authentication**: Secure login with session persistence
- 💾 **Offline-First**: Local Room database with smart sync strategy
- 🔒 **Privacy Mode**: Offline messages never sync to cloud
- 🧹 **Auto-Cleanup**: Automatic pruning of old offline messages (90 days)
- 📱 **Material3 UI**: Modern, Gemini-inspired interface

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose (Material3)
- **Online AI**: Google Generative AI SDK (gemini-1.5-flash)
- **Offline AI**: MediaPipe LLM Inference (Gemma 2B/4B)
- **Database**: Room
- **Auth**: Firebase Authentication
- **Background**: WorkManager
- **Architecture**: Repository Pattern with Flow

## Setup Instructions

### 1. Prerequisites
- Android Studio Hedgehog or later
- Android SDK 26+
- Firebase project

### 2. Firebase Setup
1. Create a Firebase project at [console.firebase.google.com](https://console.firebase.google.com)
2. Enable Firebase Authentication (Email/Password and Google Sign-In)
3. Download `google-services.json` and place it in `app/` directory

### 3. API Keys
1. Get a Gemini API key from [Google AI Studio](https://makersuite.google.com/app/apikey)
2. Open `MainActivity.kt` and replace `YOUR_GEMINI_API_KEY` with your actual key

### 4. Model Files
The app will prompt you to download a model on first run. For testing with real models:
- Update URLs in `DownloadScreen.kt` to point to actual Gemma model files
- Alternatively, manually place a `.bin` file in the app's files directory

### 5. Build & Run
```bash
./gradlew assembleDebug
```

## Project Structure

```
app/src/main/java/com/example/hybridmind/
├── core/
│   └── NetworkMonitor.kt          # Connectivity tracking
├── data/
│   ├── ChatRepository.kt          # Hybrid routing logic
│   ├── ModelDownloader.kt         # Model download manager
│   └── local/
│       └── AppDatabase.kt         # Room database
├── ui/
│   ├── auth/
│   │   └── AuthScreen.kt          # Firebase login
│   ├── download/
│   │   └── DownloadScreen.kt      # Model selection
│   ├── chat/
│   │   └── ChatScreen.kt          # Main chat interface
│   └── theme/
│       └── Theme.kt               # Material3 theming
├── workers/
│   └── AutoPruneWorker.kt         # Background cleanup
└── MainActivity.kt                # App entry point
```

## Key Concepts

### Hybrid Router
The `ChatRepository` acts as the single source of truth, routing messages to either:
- **Online**: Gemini API + save to Room + sync to Firestore (future)
- **Offline**: MediaPipe LLM + save to Room with `is_offline_only=true` flag

### Privacy Rules
- Messages created while offline are marked as private
- They are **never synced** to the cloud, even when connectivity is restored
- Auto-pruned after 90 days to save space

### Memory Safety
- Checks available RAM before allowing 4GB model download
- MediaPipe initialized in background to avoid UI jank
- WakeLock during model downloads to prevent interruption

## TODO
- [ ] Add Firestore sync for online messages
- [ ] Implement multimodal support (image input)
- [ ] Add conversation export
- [ ] Implement streaming responses
- [ ] Add voice input

## License
MIT
