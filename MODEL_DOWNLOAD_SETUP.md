# Model Download Setup for Production

## Overview

For production distribution, models are downloaded from Google Drive links instead of being bundled in the APK. This keeps the APK size small (~20 MB instead of 1.4 GB).

---

## Setup Instructions

### Step 1: Upload Model to Google Drive

1. **Upload the model file** to your Google Drive:
   - File: `gemma-2b-it-cpu-int4.bin` (1.35 GB)
   - Location: You can find it in `app/src/main/assets/` (from your local development build)

2. **Make it publicly accessible**:
   - Right-click file → Share
   - Change to "Anyone with the link"
   - Permission: Viewer

### Step 2: Get Direct Download Link

Google Drive share links look like:
```
https://drive.google.com/file/d/FILE_ID/view?usp=sharing
```

Convert to **direct download link**:
```
https://drive.google.com/uc?export=download&id=FILE_ID
```

**Example:**
- Share link: `https://drive.google.com/file/d/1ABC123XYZ/view?usp=sharing`
- Direct download: `https://drive.google.com/uc?export=download&id=1ABC123XYZ`

### Step 3: Update DownloadScreen.kt

Open: `app/src/main/java/com/example/hybridmind/ui/download/DownloadScreen.kt`

Find the button onClick handler (around line 107) and replace:

```kotlin
val url = when (model) {
    "gemma-2b" -> "YOUR_GOOGLE_DRIVE_LINK_FOR_GEMMA_2B"
    "gemma-4b" -> "YOUR_GOOGLE_DRIVE_LINK_FOR_GEMMA_4B"
    else -> return@launch
}
```

With your actual links:

```kotlin
val url = when (model) {
    "gemma-2b" -> "https://drive.google.com/uc?export=download&id=YOUR_FILE_ID"
    "gemma-4b" -> "https://drive.google.com/uc?export=download&id=YOUR_FILE_ID"  // Optional
    else -> return@launch
}
```

### Step 4: Remove Assets Folder (Optional for Production)

To reduce APK size for production builds:

1. **Remove the model from assets**:
   ```
   Delete: app/src/main/assets/gemma-2b-it-cpu-int4.bin
   ```

2. **Keep the folder structure** (optional):
   - Keep `assets/` folder with a `.gitkeep` file
   - Or remove entirely if no other assets

---

## Testing

### Local Testing (With Assets)
If you want to keep the model in assets for local testing:
- Keep the file in `app/src/main/assets/`
- The app will use it automatically (no download needed)

### Production Testing (Download)
1. Build the APK without the model in assets
2. Install on device
3. App will show download screen
4. Click "Standard (Gemma 2B)"
5. Click "Download"
6. Model downloads from Google Drive
7. App proceeds to chat

---

## Alternative Hosting Options

Instead of Google Drive, you can host on:

### 1. **Firebase Storage**
```kotlin
"https://firebasestorage.googleapis.com/v0/b/YOUR_BUCKET/o/gemma-2b.bin?alt=media"
```

### 2. **AWS S3**
```kotlin
"https://YOUR_BUCKET.s3.amazonaws.com/gemma-2b-it-cpu-int4.bin"
```

### 3. **Direct Server**
```kotlin
"https://your-server.com/models/gemma-2b-it-cpu-int4.bin"
```

---

## APK Size Comparison

| Build Type | APK Size | User Download |
|------------|----------|---------------|
| **With Assets** | ~1.4 GB | APK only |
| **Without Assets** | ~20 MB | APK + Model (on first launch) |

**Recommended**: Without assets for production (Google Play limits APKs to 100 MB for instant apps, 150 MB for standard)

---

## Current Configuration

**File**: `DownloadScreen.kt` (line ~107)

**Status**: 
- ✅ Placeholder URLs set
- ⚠️ Need to replace with your Google Drive links
- ✅ Download functionality ready

**Assets Folder**:
- Status: Currently has model (for local testing)
- Action: Remove before production build

---

## Quick Checklist

Before releasing to production:

- [ ] Upload model to Google Drive
- [ ] Get direct download link
- [ ] Update URLs in `DownloadScreen.kt`
- [ ] Remove model from `assets/` folder
- [ ] Build release APK
- [ ] Test download on clean install
- [ ] Verify offline mode works after download

---

## Example Google Drive Setup

1. File uploaded: ✅
   ```
   Name: gemma-2b-it-cpu-int4.bin
   Size: 1.35 GB
   Link: https://drive.google.com/file/d/1ABC123/view
   ```

2. Direct download link: ✅
   ```
   https://drive.google.com/uc?export=download&id=1ABC123
   ```

3. Updated in code: ✅
   ```kotlin
   "gemma-2b" -> "https://drive.google.com/uc?export=download&id=1ABC123"
   ```

4. Tested: ✅
   - Clean install
   - Download triggered
   - Model downloaded successfully
   - Offline mode works

---

**Last Updated**: 2025-12-06  
**Location**: `d:\Project\HybridMind\MODEL_DOWNLOAD_SETUP.md`
