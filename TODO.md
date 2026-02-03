# WhisperKey Storage Location Fixes

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
