# Firebase Firestore Setup

## Overview

Firestore has been integrated to store chat sessions in the cloud when online. This enables:
- ✅ Chat history sync across devices
- ✅ Cloud backup of online conversations
- ✅ **Strict privacy**: Offline messages never sync to cloud

---

## How It Works

### Automatic Sync

**When Online:**
- Messages sent → Saved to Room (local) → Synced to Firestore (cloud)
- Sessions created → Saved to Room → Synced to Firestore

**When Offline:**
- Messages sent → Saved to Room only
- Session marked as `is_offline_only = true`
- **Never synced to cloud**, even when back online

### Privacy Rules

```kotlin
// In FirestoreRepository.kt
if (session.is_offline_only) {
    return // Privacy rule: offline sessions never sync
}
```

**What Gets Synced:**
- Online messages ✅
- Online sessions ✅

**What NEVER Gets Synced:**
- Offline messages ❌
- Offline-only sessions ❌

---

## Firestore Data Structure

```
users/
  └─ {userId}/
      └─ sessions/
          └─ {sessionId}/
              ├─ title: "Chat Title"
              ├─ is_offline_only: false
              ├─ last_updated: timestamp
              └─ messages/
                  └─ {messageId}/
                      ├─ role: "user" or "model"
                      ├─ content: "message text"
                      └─ timestamp: timestamp
```

---

## Firebase Console Setup

### Enable Firestore

1. Go to [Firebase Console](https://console.firebase.google.com)
2. Select your project
3. Click **"Firestore Database"** in left sidebar
4. Click **"Create database"**
5. Select **"Start in production mode"**
6. Choose a location (e.g., `us-central`)
7. Click **"Enable"**

### Security Rules

Firebase will ask for security rules. Use these:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Users can only read/write their own data
    match /users/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
      
      // Allow access to sessions subcollection
      match /sessions/{sessionId} {
        allow read, write: if request.auth != null && request.auth.uid == userId;
        
        // Allow access to messages subcollection
        match /messages/{messageId} {
          allow read, write: if request.auth != null && request.auth.uid == userId;
        }
      }
    }
  }
}
```

**This ensures:**
- Only authenticated users can access data
- Users can only see their own chats
- No one else can read your messages

---

## Implementation Files

### New Files:
- **[FirestoreRepository.kt](file:///d:/Project/HybridMind/app/src/main/java/com/example/hybridmind/data/cloud/FirestoreRepository.kt)** - Handles all Firestore operations

### Modified Files:
- **[ChatRepository.kt](file:///d:/Project/HybridMind/app/src/main/java/com/example/hybridmind/data/ChatRepository.kt)** - Added Firestore sync calls
- **[build.gradle.kts](file:///d:/Project/HybridMind/app/build.gradle.kts)** - Added Firestore dependency

---

## Testing

### Test Cloud Sync:

1. **Create online session:**
   - WiFi ON
   - Send message
   - Check Firebase Console → Firestore Database
   - Should see your message

2. **Create offline session:**
   - WiFi OFF
   - Send message
   - Check Firebase Console
   - Should NOT see this message (privacy preserved)

3. **Cross-device sync:**
   - Sign in on another device
   - Online chats should appear
   - Offline chats stay device-local

---

## Privacy Verification

### Offline Message Test:

```kotlin
// This is enforced in FirestoreRepository.kt
suspend fun syncMessage(sessionId: String, message: Message, isOfflineSession: Boolean) {
    if (isOfflineSession) {
        return // Privacy: Don't sync offline messages
    }
    // ... sync to Firestore
}
```

**Result:**
- Offline sessions = `is_offline_only: true`
- Firestore sync skipped
- Messages stay local forever

---

## Error Handling

Firestore sync failures don't break the app:

```kotlin
syncScope.launch {
    try {
        firestoreRepository.syncMessage(...)
    } catch (e: Exception) {
        // Silently fail - offline mode takes over
    }
}
```

**Graceful Degradation:**
- Sync fails → App continues working
- Messages saved locally in Room
- Will retry on next message

---

## Build Configuration

**Dependency Added:**
```kotlin
implementation("com.google.firebase:firebase-firestore")
```

**No additional setup needed** - uses same `google-services.json` as Firebase Auth.

---

## Quick Checklist

Before using Firestore:

- [ ] Enable Firestore in Firebase Console
- [ ] Set security rules (provided above)
- [ ] Sync project in Android Studio
- [ ] Test online message sync
- [ ] Verify offline privacy protection

---

**Status**: ✅ Firestore integration complete!

**Privacy**: ✅ Offline messages never sync to cloud!
