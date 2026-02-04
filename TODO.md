# WhisperKey TODO

## Upcoming Tasks

- [x] Make emojis not overflow the edge to need scrolling, feel free to redesign the keyboard a little
- [x] increase size of text for tap to start and listening and the like so you can see what is happening.
- [x] Clean up emoji hotkey management; add a flag to the emojis that are converted from text to have it show on the keyboard; store in settings the emojis list so they will not be overwritten on update
- [x] add space and enter/go/whatever buttons
- [x] add button to switch to 10 key and button on that to switch back (maybe tabs)
- [x] add button to switch to fully capable qwerty and button on that to switch back (maybe tabs)

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
