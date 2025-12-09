# HybridMind - Setup Guide

## 🚀 Quick Start

Follow these steps to get HybridMind up and running:

### Step 1: Firebase Configuration

1. **Create a Firebase Project**:
   - Go to [Firebase Console](https://console.firebase.google.com)
   - Click "Add Project" or select an existing project
   - Follow the setup wizard

2. **Enable Authentication**:
   - In Firebase Console, go to **Authentication** → **Sign-in method**
   - Enable **Email/Password** provider
   - (Optional) Enable **Google** sign-in

3. **Add Android App**:
   - In Firebase Console, click "Add app" → Select Android
   - **Android package name**: `com.example.hybridmind`
   - Download `google-services.json`

4. **Place Configuration File**:
   - Copy the downloaded `google-services.json` file
   - Place it in: `app/google-services.json`
   - ⚠️ **Location is critical**: It must be directly in the `app/` folder, not in subdirectories

### Step 2: Gemini API Key

1. **Get API Key**:
   - Visit [Google AI Studio](https://makersuite.google.com/app/apikey)
   - Click "Create API Key"
   - Copy the generated key

2. **Update MainActivity.kt**:
   - Open: `app/src/main/java/com/example/hybridmind/MainActivity.kt`
   - Find line 27: `val geminiApiKey = "YOUR_GEMINI_API_KEY"`
   - Replace with: `val geminiApiKey = "YOUR_ACTUAL_API_KEY_HERE"`

### Step 3: Build the Project

```powershell
# Navigate to project directory
cd d:\Project\HybridMind

# Build the debug APK
.\gradlew assembleDebug

# Or run directly on connected device/emulator
.\gradlew installDebug
```

### Step 4: (Optional) Add Real Model Files

The app uses placeholder URLs for model downloads. For production:

**Option A - Update URLs**:
- Edit `app/src/main/java/com/example/hybridmind/ui/download/DownloadScreen.kt`
- Update lines with `https://example.com/gemma-2b.bin` to real Hugging Face URLs

**Option B - Manual Placement**:
- Download Gemma models from Hugging Face
- Place in device storage (will need to adjust `ModelDownloader.kt` paths)

---

## 📋 Pre-flight Checklist

Before building, ensure:

- [ ] `app/google-services.json` exists
- [ ] Gemini API key is set in `MainActivity.kt`
- [ ] Android SDK 26+ is installed
- [ ] Gradle wrapper is executable

---

## 🧪 Testing the Hybrid Feature

1. **Online Mode**:
   - Launch app with WiFi/Data enabled
   - Send a message
   - Should see "Online - Gemini" in top bar
   - Response comes from Gemini API

2. **Offline Mode**:
   - Turn off WiFi/Data in device settings
   - Send a message
   - Should see "Offline - Local" in top bar
   - Response comes from MediaPipe (after model download)
   - Message marked as "Private (Offline)" in history

3. **Verify Privacy**:
   - Check chat history sidebar
   - Offline chats should have badge: "Private (Offline)"
   - These messages will never sync to cloud

---

## 🛠️ Troubleshooting

### Build Errors

**"google-services.json is missing"**:
- Ensure file is in `app/` folder, not `app/src/`

**"Gemini API error"**:
- Verify API key is correct
- Check internet connectivity
- Ensure Gemini API is enabled in your Google Cloud project

**"Room database error"**:
- Clear app data: Settings → Apps → HybridMind → Clear Data
- Rebuild: `.\gradlew clean assembleDebug`

### Runtime Errors

**"Offline model not initialized"**:
- Complete model download from the Download screen
- Check device has sufficient storage
- Verify RAM availability (4GB model needs 8GB+ RAM)

**"Authentication failed"**:
- Verify Firebase configuration
- Check Email/Password is enabled in Firebase Console
- Try creating a new account

---

## 📱 Running on Emulator

```powershell
# List available emulators
emulator -list-avds

# Start an emulator (replace 'Pixel_5_API_33' with your AVD name)
emulator -avd Pixel_5_API_33

# Install and run
.\gradlew installDebug
```

---

## 🎯 Next Steps After Setup

1. **Customize UI**: Modify colors in `ui/theme/Theme.kt`
2. **Add Firestore**: Implement cloud sync for online messages
3. **Implement Streaming**: Add streaming responses for better UX
4. **Add Voice Input**: Integrate speech recognition
5. **Export Chats**: Allow users to export conversations

---

## 📞 Support

If you encounter issues:
1. Check [README.md](./README.md) for architecture overview
2. Review [walkthrough.md](../walkthrough.md) for implementation details
3. Verify all configuration files are in place

---

**Project Location**: `d:\Project\HybridMind`

**Last Updated**: 2025-12-06
