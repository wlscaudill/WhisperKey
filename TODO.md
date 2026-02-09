# WhisperKey TODO

## Upcoming Tasks

- [x] Make emojis not overflow the edge to need scrolling, feel free to redesign the keyboard a little
- [x] increase size of text for tap to start and listening and the like so you can see what is happening.
- [x] Clean up emoji hotkey management; add a flag to the emojis that are converted from text to have it show on the keyboard; store in settings the emojis list so they will not be overwritten on update
- [x] add space and enter/go/whatever buttons
- [x] add button to switch to 10 key and button on that to switch back (maybe tabs)
- [x] add button to switch to fully capable qwerty and button on that to switch back (maybe tabs)
- [ ] keyboard needs to recognize if it does NOT have microphone permissions and prompt to fix
- [ ] Expose Whisper engine as a system RecognitionService so other keyboards (Gboard, Samsung, etc.) can use WhisperKey for voice-to-text
  - **Issue:** Implementation complete but WhisperKey is not appearing in Settings > Voice input picker. Needs investigation — may be a manifest issue, missing attribute, or Android version-specific behavior.
- [x] Copy/paste does not work while in the keyboard
  - **Fixed:** Long-press settings button shows clipboard menu (Select All, Cut, Copy, Paste)
- [x] Enter button should change to "Go" (or appropriate action label) based on input field type — currently always shows "Enter" even when the field expects a search/go action
  - **Fixed:** Enter button now shows Go/Search/Send/Next/Done based on EditorInfo.imeOptions, and performs the appropriate action

---

# Expose Whisper Engine as System RecognitionService

## Goal
Make WhisperKey appear in **Settings > Languages & input > Voice input** so users can select it as the voice-to-text provider for any keyboard, not just WhisperKey's own IME.

## Background
Android's voice input picker lists implementations of `android.speech.RecognitionService`, not IME voice subtypes. Google and Samsung register a `RecognitionService` with `BIND_RECOGNITION_SERVICE` permission. WhisperKey currently only has an `InputMethodService`. The Whisper engine runs fully on-device via whisper.cpp JNI, so no network dependency.

## Implementation Plan

### Step 1: Create `WhisperRecognitionService.kt`

**Path:** `app/src/main/java/com/whisperkey/WhisperRecognitionService.kt`

Extend `android.speech.RecognitionService` and implement its abstract methods. This service gets its own `WhisperEngine` and `AudioRecorder` instances (the model files on disk are shared, but each service loads independently — no IPC needed).

**Key methods to implement:**
- `onStartListening(intent, callback)` — Load model if needed, start `AudioRecorder`, capture audio
- `onStopListening(callback)` — Stop recording, run `WhisperEngine.transcribe()`, return results via `callback.results()`
- `onCancel(callback)` — Abort recording, clean up
- `onCreate()` — Initialize `WhisperEngine`, `AudioRecorder`, `ModelManager`
- `onDestroy()` — Release native resources

**Callback flow:**
```
Android SpeechRecognizer → WhisperRecognitionService
  onStartListening()
    ├─ callback.readyForSpeech(Bundle)
    ├─ callback.beginningOfSpeech()
    ├─ [recording...]
    ├─ callback.rmsChanged(float)          // waveform amplitude updates
  onStopListening()
    ├─ callback.endOfSpeech()
    ├─ whisperEngine.transcribe(audioData)
    ├─ callback.results(Bundle)            // SpeechRecognizer.RESULTS_RECOGNITION
  onCancel()
    └─ cleanup
```

**Result bundle format:**
```kotlin
val results = Bundle()
val matches = ArrayList<String>()
matches.add(transcribedText)
results.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, matches)
results.putFloatArray(SpeechRecognizer.CONFIDENCE_SCORES, floatArrayOf(1.0f))
callback.results(results)
```

**Error handling:**
- Model not downloaded → `callback.error(SpeechRecognizer.ERROR_SERVER)` (closest fit)
- No audio permission → `callback.error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)`
- Recording failure → `callback.error(SpeechRecognizer.ERROR_AUDIO)`
- Transcription failure → `callback.error(SpeechRecognizer.ERROR_NO_MATCH)`

### Step 2: Register in `AndroidManifest.xml`

Add the new service declaration alongside the existing IME service:

```xml
<service
    android:name=".WhisperRecognitionService"
    android:exported="true"
    android:label="@string/ime_name"
    android:permission="android.permission.BIND_RECOGNITION_SERVICE">
    <intent-filter>
        <action android:name="android.speech.RecognitionService" />
    </intent-filter>
    <meta-data
        android:name="android.speech"
        android:resource="@xml/recognition_service" />
</service>
```

### Step 3: Create `res/xml/recognition_service.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<recognition-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:settingsActivity="com.whisperkey.SettingsActivity" />
```

This tells Android where the user can configure the recognition service (model size, storage location, etc.) — reuses the existing `SettingsActivity`.

### Step 4: Handle model lifecycle in the RecognitionService

The `WhisperRecognitionService` needs to load the model on demand since it runs in a separate service lifecycle from the keyboard:

```kotlin
private fun ensureModelLoaded(): Boolean {
    if (whisperEngine.isReady()) return true
    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    val modelSize = prefs.getString("model_size", "base") ?: "base"
    val modelPath = modelManager.getDownloadedModelPath(modelSize) ?: return false
    val file = File(modelPath)
    if (!file.exists() || !file.canRead()) return false
    return whisperEngine.initialize(modelPath)
}
```

This reuses `ModelManager` and reads the same preferences as the keyboard service, so whichever model the user has downloaded and selected works for both.

### Step 5: Handle audio conflicts

`AudioRecord` with `MIC` source is exclusive — only one service can record at a time. This is fine in practice because:
- If WhisperKey IME is active and recording, no other keyboard is asking for voice input
- If another keyboard triggers the `RecognitionService`, WhisperKey IME isn't recording

No special handling needed, but `onStartListening` should catch `AudioRecord` init failures and return `ERROR_AUDIO`.

## Files Summary

| Action | File |
|--------|------|
| **Create** | `app/src/main/java/com/whisperkey/WhisperRecognitionService.kt` |
| **Create** | `app/src/main/res/xml/recognition_service.xml` |
| **Modify** | `app/src/main/AndroidManifest.xml` (add service declaration) |

## Testing

- Build and install
- Go to **Settings > Languages & input > Voice input** — WhisperKey should appear
- Select WhisperKey as voice input provider
- Switch to Gboard or Samsung keyboard
- Tap the mic button — should use WhisperKey's Whisper engine for transcription
- Verify results appear in the text field
- Test with no model downloaded — should get a graceful error
- Test cancellation mid-recording

### Implementation Plan for Keyboard Tabs

**Approach:** Create a ViewPager or ViewFlipper-based keyboard with 3 modes:
1. Voice Input (current default)
2. 10-Key Numeric
3. QWERTY

**Files to Create:**
- `NumericKeyboardView.kt` - 10-key layout (0-9, *, #, backspace, space)
- `QwertyKeyboardView.kt` - Full QWERTY with shift, symbols layer
- `res/layout/numeric_keyboard_view.xml`
- `res/layout/qwerty_keyboard_view.xml`

**Files to Modify:**
- `WhisperKeyboardService.kt` - Add ViewFlipper to switch between keyboard modes
- `VoiceInputView.kt` - Add mode switch button (e.g., "ABC" button to go to QWERTY)

**UI Design:**
- Voice mode: Add small "123" button → switches to 10-key
- 10-key mode: Add "ABC" button → switches to QWERTY, "🎤" button → back to voice
- QWERTY mode: Add "123" button → switches to 10-key, "🎤" button → back to voice

**Key Components:**
1. Mode enum: VOICE, NUMERIC, QWERTY
2. ViewFlipper containing all 3 keyboard views
3. Each keyboard has navigation buttons to other modes
4. Remember last text-input mode (NUMERIC or QWERTY) for quick toggle
- [x] when a model size or location is changed check for the presence of the model and prompt a confirmation box to download
- [x] when a model is downloaded change the text under download model to reflect that fact

---

# Storage Location Fixes (COMPLETED)

## Issues Addressed (COMPLETED)

1. **Folder selection now uses SAF (Storage Access Framework)** like DashCam
   - [x] Uses `ActivityResultContracts.OpenDocumentTree()` for folder picker
   - [x] Takes persistent URI permissions for access after restart
   - [x] Parses SAF URIs to get actual file paths for whisper.cpp

2. **Simplified storage options to just two choices:**
   - [x] Internal app storage (default)
   - [x] Choose a custom folder (via SAF folder picker)

3. **Fixed internal app storage model finding**
   - [x] Models now stored in `whisper_models` subdirectory under app files
   - [x] Added detailed logging to trace model lookup
   - [x] Consistent paths between download and lookup

## Implementation Summary

### StorageHelper.kt (NEW)
- `takePersistentPermission()` - Takes persistent SAF permissions
- `hasPersistentPermission()` - Checks if permissions exist
- `getStorageDisplayName()` - Human-readable name for storage location
- `getFilePathFromUri()` - Converts SAF URI to actual file path
- `findFileInStorage()` - Finds files using DocumentFile API

### ModelManager.kt (REWRITTEN)
- Removed `StorageLocation` enum (was INTERNAL/EXTERNAL/CUSTOM)
- Now just stores custom URI string or uses internal storage
- `getCustomStorageUri()` / `setCustomStorageUri()` - Manage custom folder
- `hasCustomStorage()` - Check if custom folder is set
- `getStorageDisplayName()` - Get display name for current storage
- `getDownloadedModelPath()` - Checks both internal and custom storage
- Uses `whisper_models` subdirectory for internal storage

### SettingsActivity.kt (REWRITTEN)
- Uses `ActivityResultContracts.OpenDocumentTree()` for SAF folder picker
- Shows dialog with options: "Internal App Storage" or "Choose Custom Folder..."
- Takes persistent permissions when folder selected
- Verifies folder is writable before accepting

### WhisperKeyboardService.kt (UPDATED)
- Updated to use new ModelManager API methods

## Testing Checklist

- [ ] Clean install app
- [ ] Download model to internal storage
- [ ] Verify keyboard finds and loads model
- [ ] Choose custom folder on SD card
- [ ] Download model to custom folder
- [ ] Verify keyboard finds and loads model from custom folder
- [ ] Verify model survives app restart (persistent permissions)

## Notes

- If you have models from before this update, they may be in the old directory
- Delete and re-download models after updating
- Internal storage path: `/data/data/com.whisperkey/files/whisper_models/`
- SAF URI format: `content://com.android.externalstorage.documents/tree/[volume]:[path]`
  - volume "primary" = internal storage
  - volume "XXXX-XXXX" = SD card
