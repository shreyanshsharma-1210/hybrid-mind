# Android Studio Setup Guide

## ✅ Configuration Status

Your project is **pre-configured** and ready to build!

| Configuration | Status |
|--------------|--------|
| Gemini API Key | ✅ **Complete** - Already set in `MainActivity.kt` |
| Firebase Config | ✅ **Complete** - `google-services.json` added |
| Project Structure | ✅ **Complete** - All files in place |

---

## Step 1: Open Project in Android Studio

1. Launch **Android Studio**
2. Click **"Open"** (or File → Open)
3. Navigate to: `d:\Project\HybridMind`
4. Click **"OK"**

---

## Step 2: Wait for Initial Sync

Android Studio will automatically:
- ✅ Index the project files
- ✅ Download Gradle wrapper (if needed)
- ✅ Sync Gradle dependencies
- ⚠️ **This may take 5-10 minutes on first open**

**Look for**: Bottom status bar will show "Gradle sync in progress..."

**Expected Result**: "Gradle build finished" with green checkmark ✅

---

## Step 3: Verify Configuration (Optional)

All configuration is already complete, but you can verify:

### ✅ 3.1 Firebase Configuration
- Open `app/google-services.json` in Android Studio
- Should contain your Firebase project configuration
- Package name should be: `com.example.hybridmind`

### ✅ 3.2 Gemini API Key
- Open `MainActivity.kt` (Ctrl + Shift + N)
- Line 47 should have: `val geminiApiKey = "AIzaSy..."`
- Should NOT be placeholder text

If everything looks good, **you're ready to build!**

---

## Step 4: Configure Android Device/Emulator

### Option A: Use Physical Device
1. Enable **Developer Options** on your Android phone:
   - Settings → About Phone → Tap "Build Number" 7 times
2. Enable **USB Debugging**:
   - Settings → System → Developer Options → USB Debugging
3. Connect phone via USB
4. Allow USB debugging on phone prompt

### Option B: Create Emulator
1. Click **Device Manager** icon (phone icon in toolbar)
2. Click **"Create Device"**
3. Select **Pixel 5** or similar
4. Select **API 33** (Android 13) or higher
5. Click **"Finish"**

---

## Step 5: Build & Run

### First Build
1. Click **"Build"** → **"Make Project"** (Ctrl + F9)
2. Wait for build to complete
3. Check **"Build"** tab at bottom for errors

### Run the App
1. Select device/emulator from dropdown (top toolbar)
2. Click **Run** button ▶️ (green triangle)
3. Or press **Shift + F10**

**Wait**: First run may take 2-3 minutes to install.

---

## Step 6: App First Launch Flow

When the app launches, you'll see this flow:

### Screen 1: Authentication
- Enter any email/password
- Click "Sign In / Sign Up"
- If account doesn't exist, it will auto-create

### Screen 2: Model Download (Gatekeeper)
- Select "Standard (Gemma 2B)" or "Advanced (Gemma 4B)"
- Click "Download"
- ⚠️ **Note**: URLs are placeholders, download will fail
  - See **Troubleshooting** section below

### Screen 3: Chat Dashboard
- Type a message
- If online → Uses Gemini API
- If offline → Uses MediaPipe (requires real model)

---

## Troubleshooting

### Problem: "google-services.json is missing"
**Solution**: Follow Step 3.1 above. File must be in `app/` folder, not `app/src/`.

### Problem: Gradle sync fails
**Solution**:
1. File → Invalidate Caches → Invalidate and Restart
2. Wait for Android Studio to restart
3. Re-sync Gradle

### Problem: "SDK not found"
**Solution**:
1. File → Project Structure → SDK Location
2. Set Android SDK path (usually `C:\Users\<username>\AppData\Local\Android\Sdk`)
3. Install SDK Platform 34 if missing (Tools → SDK Manager)

### Problem: Model download fails (expected)
**Why**: URLs are placeholders (`https://example.com/gemma-2b.bin`)

**To Test Online Mode Only**:
1. Turn on WiFi/Data
2. Skip download screen by editing `DownloadScreen.kt`:
   ```kotlin
   // Line 23: Comment out download check
   // LaunchedEffect(Unit) { ... }
   ```
3. Messages will use Gemini API (online only)

**To Add Real Models**:
1. Download Gemma 2B/4B from [Hugging Face](https://huggingface.co/google/gemma-2b-it)
2. Convert to `.bin` format (MediaPipe compatible)
3. Update URLs in `DownloadScreen.kt` lines 139-140

---

## Quick Checklist

Before running:
- [ ] `google-services.json` is in `app/` folder
- [ ] Gemini API key is set in `MainActivity.kt`
- [ ] Gradle sync completed successfully
- [ ] Device/emulator is connected
- [ ] Firebase Authentication is enabled in console

---

## Expected Build Time

| Task | Duration |
|------|----------|
| First Gradle sync | 5-10 min |
| First build | 3-5 min |
| Subsequent builds | 30-60 sec |
| App install | 1-2 min |

---

## What Happens on Build

1. **Gradle**: Downloads dependencies (~200MB first time)
2. **KSP**: Generates Room database code
3. **Compile**: Compiles Kotlin to bytecode
4. **Package**: Creates APK file
5. **Install**: Pushes APK to device

---

## Next Steps After Successful Run

Once the app is running:

1. **Test Online Mode**:
   - Send message with WiFi on
   - Should respond via Gemini

2. **Test Offline Indicator**:
   - Turn off WiFi in device settings
   - Top bar should show "Offline - Local"
   - (Messages will fail without real model)

3. **Check Database**:
   - Open Device File Explorer in Android Studio
   - Navigate to `/data/data/com.example.hybridmind/databases/`
   - You should see `hybridmind_database`

4. **Test Authentication Persistence**:
   - Close app
   - Reopen
   - Should skip login screen (already authenticated)

---

## Additional Tools in Android Studio

- **Logcat** (Alt + 6): View runtime logs
- **Device File Explorer**: Browse app files
- **Profiler**: Monitor CPU/memory usage
- **Layout Inspector**: Debug UI issues

---

**Project Path**: `d:\Project\HybridMind`

**Need help?** Check [SETUP.md](./SETUP.md) or [README.md](./README.md)
