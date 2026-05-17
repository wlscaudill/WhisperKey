# WhisperKey - Local Speech-to-Text Android Keyboard

## Project Overview

Create an Android Input Method Editor (IME) keyboard that uses OpenAI's Whisper model running fully locally on-device to perform speech-to-text transcription.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                    Android App                          │
├─────────────────────────────────────────────────────────┤
│  ┌─────────────────┐    ┌─────────────────────────┐    │
│  │  VoiceInputView │    │   Settings Activity     │    │
│  │   (IME View)    │    │   - Recording mode      │    │
│  │   - Mic button  │    │   - Model selection     │    │
│  │   - Waveform    │    │   - Model downloads     │    │
│  │   - Status      │    │                         │    │
│  └────────┬────────┘    └───────────┬─────────────┘    │
│           │                         │                   │
│  ┌────────▼────────┐    ┌───────────▼─────────────┐    │
│  │  Audio Capture  │    │    Model Manager        │    │
│  │  (AudioRecord)  │    │  - Download on demand   │    │
│  └────────┬────────┘    │  - English-only models  │    │
│           │             └─────────────────────────┘    │
│  ┌────────▼────────┐                                   │
│  │ Whisper Engine  │                                   │
│  │ (whisper.cpp)   │                                   │
│  │ via JNI/NDK     │                                   │
│  └─────────────────┘                                   │
└─────────────────────────────────────────────────────────┘
```

---

## Phase 1: Project Setup & Foundation

### 1.1 Android Project Structure
- [ ] Create new Android project with Kotlin
- [ ] Configure Gradle for NDK/CMake support
- [ ] Set minimum SDK (API 26 / Android 8.0 for optimal audio APIs)
- [ ] Set up project modules:
  - `app` - Main application module
  - `whisper` - Native library module for whisper.cpp

### 1.2 Dependencies & Build Configuration
- [ ] Add whisper.cpp as a git submodule or vendored dependency
- [ ] Configure CMakeLists.txt for native build
- [ ] Set up JNI bindings structure
- [ ] Configure ProGuard/R8 rules

### 1.3 Project Files Structure
```
WhisperKey/
├── app/
│   ├── src/main/
│   │   ├── java/com/whisperkey/
│   │   │   ├── WhisperKeyboardService.kt    # Main IME service
│   │   │   ├── WhisperEngine.kt             # Kotlin wrapper for native code
│   │   │   ├── AudioRecorder.kt             # Audio capture utility
│   │   │   ├── ModelManager.kt              # Model download & management
│   │   │   ├── EmojiManager.kt              # Emoji hotkey preferences
│   │   │   ├── SettingsActivity.kt          # Configuration UI
│   │   │   └── ui/
│   │   │       ├── VoiceInputView.kt        # Voice-focused keyboard view
│   │   │       └── EmojiPickerDialog.kt     # Emoji selection dialog
│   │   ├── cpp/
│   │   │   ├── whisper_jni.cpp              # JNI bridge
│   │   │   └── CMakeLists.txt
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   ├── voice_input_view.xml     # Voice keyboard layout
│   │   │   │   └── emoji_hotkey_item.xml    # Settings emoji row
│   │   │   ├── xml/
│   │   │   │   ├── method.xml               # IME declaration
│   │   │   │   └── preferences.xml          # Settings screen
│   │   │   └── values/
│   │   │       └── default_emojis.xml       # Default emoji set
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── whisper.cpp/                              # Git submodule
└── Plan.md
```

---

## Phase 2: Whisper Integration (Native Layer)

### 2.1 whisper.cpp Setup
- [ ] Clone/add whisper.cpp repository
- [ ] Configure CMake to build whisper.cpp for Android ABIs (arm64-v8a, armeabi-v7a)
- [ ] Enable optimizations:
  - NEON SIMD for ARM
  - Consider NNAPI or GPU acceleration options

### 2.2 JNI Bridge Implementation
- [ ] Create JNI functions:
  - `initModel(modelPath: String): Long` - Load model, return context handle
  - `transcribe(contextHandle: Long, audioData: FloatArray): String` - Run inference
  - `releaseModel(contextHandle: Long)` - Free resources
- [ ] Handle threading (inference should run off UI thread)
- [ ] Implement error handling and logging

### 2.3 Kotlin Wrapper
- [ ] Create `WhisperEngine` class:
  ```kotlin
  class WhisperEngine {
      fun loadModel(modelPath: String): Boolean
      suspend fun transcribe(audioData: FloatArray): Result<String>
      fun release()
  }
  ```
- [ ] Use coroutines for async inference
- [ ] Manage model lifecycle with Android lifecycle awareness

### 2.4 Model Management (Download on Demand)
- [ ] Create `ModelManager.kt` for downloading models:
  ```kotlin
  enum class WhisperModel(val fileName: String, val sizeBytes: Long, val sha256: String) {
      TINY("ggml-tiny.en.bin", 75_000_000, "<sha256>"),    // English-only, fastest
      BASE("ggml-base.en.bin", 142_000_000, "<sha256>"),   // English-only, balanced
      SMALL("ggml-small.en.bin", 466_000_000, "<sha256>")  // English-only, most accurate
  }

  enum class StorageLocation {
      INTERNAL,    // App's internal files directory
      EXTERNAL     // SD card / external storage
  }
  ```
- [ ] Use English-only models (`*.en.bin`) for better performance
- [ ] Download from Hugging Face: `https://huggingface.co/ggerganov/whisper.cpp/resolve/main/`
- [ ] **Storage location selection:**
  - Internal storage: `context.filesDir` (default, always available)
  - External/SD card: `context.getExternalFilesDir(null)` (app-specific, no permission needed on API 19+)
  - Detect available storage locations and free space
  - Handle SD card removal gracefully (fall back to internal or prompt re-download)
- [ ] Implement download with progress callback
- [ ] Support download cancellation and resumption
- [ ] **Security: Verify SHA256 checksum after download**
  - Reject and delete file if checksum doesn't match
  - Show error to user with option to retry
- [ ] No model bundled with app - user downloads their preferred model

---

## Phase 3: Audio Capture System

### 3.1 AudioRecorder Implementation
- [ ] Use `AudioRecord` API for low-latency capture
- [ ] Configure audio format:
  - Sample rate: 16kHz (Whisper requirement)
  - Channel: Mono
  - Encoding: 16-bit PCM
- [ ] Implement circular buffer for audio data
- [ ] Add voice activity detection (VAD) for automatic start/stop

### 3.2 Audio Processing Pipeline
- [ ] Convert PCM int16 to float32 (Whisper input format)
- [ ] Implement noise reduction (optional, improves accuracy)
- [ ] Handle audio normalization

### 3.3 Recording States
- [ ] Idle - Not recording
- [ ] Recording - Capturing audio
- [ ] Processing - Running Whisper inference
- [ ] Implement state machine with visual feedback

---

## Phase 4: Input Method Editor (IME)

### 4.1 IME Service Setup
- [ ] Create `WhisperKeyboardService` extending `InputMethodService`
- [ ] Declare IME in `AndroidManifest.xml`:
  ```xml
  <service
      android:name=".WhisperKeyboardService"
      android:permission="android.permission.BIND_INPUT_METHOD">
      <intent-filter>
          <action android:name="android.view.InputMethod"/>
      </intent-filter>
      <meta-data
          android:name="android.view.im"
          android:resource="@xml/method"/>
  </service>
  ```
- [ ] Create `res/xml/method.xml` with IME configuration

### 4.2 Voice-Focused Layout
- [ ] Design minimal voice-only layout with emoji hotkeys:
  ```
  ┌─────────────────────────────────────────┐
  │         Status: "Tap to speak"          │
  │                                         │
  │      ┌─────────────────────────┐        │
  │      │   Waveform Visualizer   │        │
  │      └─────────────────────────┘        │
  │                                         │
  │            ┌───────────┐                │
  │            │    🎤     │                │
  │            │ MIC BTN   │                │
  │            └───────────┘                │
  │                                         │
  │  [1] [2] [3] [4] [5] [6] [7] [8] [9] [0]│  ← 10 Emoji Hotkeys
  │                                         │
  │   [⌫ Backspace]          [⚙ Settings]  │
  └─────────────────────────────────────────┘
  ```
- [ ] Large, prominent microphone button (center)
- [ ] **10 configurable emoji hotkeys**
  - Default emojis: 👍 ❤️ 😂 😊 🙏 😢 😮 🎉 🔥 ✅
  - Tap to insert emoji at cursor
  - Long-press to see/change emoji (optional quick-edit)
  - Configurable in settings
- [ ] Backspace button for corrections
- [ ] Settings button to access configuration
- [ ] Support both portrait and landscape orientations

### 4.3 IME Lifecycle
- [ ] Handle `onCreateInputView()` - Inflate keyboard
- [ ] Handle `onStartInput()` - Prepare for text field
- [ ] Handle `onFinishInput()` - Cleanup
- [ ] Manage connection to text field via `InputConnection`

### 4.4 Text Insertion
- [ ] Use `getCurrentInputConnection()` to get text field
- [ ] Implement `commitText()` for inserting transcribed text
- [ ] Handle text field attributes (password fields, etc.)

---

## Phase 5: User Interface (Voice-Focused)

### 5.1 VoiceInputView Design
- [ ] Create custom `VoiceInputView` extending View (traditional Views for stability)
- [ ] Design states:
  - Idle state (default mic, "Tap to speak" or "Hold to speak")
  - Recording state (pulsing mic, active waveform, "Listening...")
  - Processing state (spinner/loading, "Processing...")
  - Error state (error message display)
  - No Model state ("Download model in settings")
- [ ] Implement haptic feedback on mic tap

### 5.2 Visual Feedback
- [ ] Mic button color/animation change when recording
- [ ] Real-time audio amplitude waveform visualization
- [ ] Status text updates for each state

### 5.3 Settings Screen
- [ ] **Recording Mode**: Tap-to-toggle OR Hold-to-record (ListPreference)
- [ ] **Model Selection**: Choose active model from downloaded models
- [ ] **Storage Location**: Internal storage OR SD card (ListPreference)
  - Show available space for each option
  - Only show SD card option if external storage is available
  - Warn user if changing location (existing models stay in old location)
- [ ] **Model Downloads**:
  - List available models (Tiny ~75MB, Base ~142MB, Small ~466MB)
  - Download button with progress indicator
  - Show which models are downloaded and where
  - Delete downloaded models option
- [ ] **Emoji Hotkeys**: Configure the 10 emoji shortcuts
  - List showing slots 1-10 with current emoji
  - Tap slot to open emoji picker
  - Reset to defaults option
  - Default set: 👍 ❤️ 😂 😊 🙏 😢 😮 🎉 🔥 ✅
- [ ] About section (version, privacy note: all processing is local)

### 5.4 Model Download UI
- [ ] Download progress dialog/screen
- [ ] Cancel download option
- [ ] Show "Verifying..." step after download completes (SHA256 check)
- [ ] Error handling with retry (network errors, checksum failures)
- [ ] Clear error message if verification fails ("Download corrupted, please retry")

---

## Phase 6: Integration & Polish

### 6.1 Recording Flow (Both Modes)
**Tap-to-Toggle Mode:**
```
User taps mic → Start recording → User taps again
    → Stop recording → Process with Whisper → Insert text
```

**Hold-to-Record Mode:**
```
User presses & holds mic → Start recording → User releases
    → Stop recording → Process with Whisper → Insert text
```

- [ ] Implement tap-to-toggle mode
- [ ] Implement hold-to-record mode
- [ ] Read preference from settings to determine active mode
- [ ] Update status text based on mode ("Tap to speak" vs "Hold to speak")
- [ ] Add timeout for maximum recording length (e.g., 30 seconds)

### 6.2 Performance Optimization
- [ ] Run inference on background thread/coroutine
- [ ] Implement streaming transcription if possible
- [ ] Cache model in memory while keyboard is active
- [ ] Release model when keyboard is hidden (memory management)

### 6.3 Error Handling
- [ ] Handle microphone permission denied
- [ ] Handle model loading failures
- [ ] Handle transcription failures gracefully
- [ ] Handle SD card removed/unavailable (prompt to re-download to internal or reinsert card)
- [ ] Show user-friendly error messages

### 6.4 Permissions
- [ ] Request `RECORD_AUDIO` permission
- [ ] Handle runtime permission flow
- [ ] Explain why permission is needed

---

## Phase 7: Testing & Release

### 7.1 Testing
- [ ] Unit tests for audio processing
- [ ] Integration tests for Whisper engine
- [ ] UI tests for keyboard interaction
- [ ] Test on various Android versions (8.0+)
- [ ] Test on different device types (phones, tablets)

### 7.2 Performance Benchmarks
- [ ] Measure transcription latency
- [ ] Monitor memory usage
- [ ] Track battery consumption
- [ ] Test with different model sizes

### 7.3 Release Preparation
- [ ] Create app icons and graphics
- [ ] Write Play Store description
- [ ] Prepare privacy policy (handles voice data)
- [ ] Configure release signing

---

## Phase 8: Performance, QWERTY Expansion & Layout Profiles

> Added 2026-05-16. Phases 1–7 describe the original bring-up; this phase covers ongoing performance and keyboard-customization work.

### 8.1 Performance — Free Wins (no GPU)

These changes don't require new backends or hardware-specific code. Expected combined speedup: ~2–3× on keyboard-length recordings.

- [x] **Quantized model variants in `ModelManager`**
  - Added `tiny_q5_1`, `base_q5_1`, `base_q4_0` to `MODELS` map and `strings.xml` arrays
  - All three keys added to `ENGLISH_ONLY_MODELS` set
  - **Default still `base` (f16)** — `Q3` (TODO.md) decides whether to flip the default to a quantized variant
- [x] **Verify whisper.cpp version, update CMakeLists define**
  - Submodule was found to already be at v1.8.3-74-gaa1bc0d1 (despite stale v1.7.4 references in docs)
  - All source files in `CMakeLists.txt` confirmed present in v1.8.3 — no reconciliation needed
  - Bumped `WHISPER_VERSION` define in CMakeLists.txt from `1.7.4` → `1.8.3`
- [x] **Fix `strcat` loop in `jni.c`**
  - Replaced O(n²) concatenation with running write pointer + `memcpy` (O(n))
  - See `jni.c` segment-collection block in `nativeTranscribe`

### 8.2 Performance — GPU & NPU (deferred)

| Backend | Coverage | Expected gain | Effort |
|---------|----------|---------------|--------|
| **Vulkan** (`ggml-vulkan`) | Most modern Android GPUs (Adreno, Mali, Xclipse) | 1.3–2× — phone GPUs are memory-bandwidth-limited | Add Vulkan SDK headers, build the `ggml-vulkan` translation unit, multi-backend selection at runtime |
| **OpenCL** (`ggml-opencl`) | Best on Snapdragon (Adreno); Mali support spotty | Often beats Vulkan on Adreno | Similar to Vulkan but vendor-flavored |
| **Qualcomm QNN / Hexagon NPU** | Snapdragon 8 Gen 2/3+ only | 5–10× at low power | Largest — separate inference path, ONNX → QNN model conversion, parallel maintenance |

Sequence: prototype Vulkan first on a test device and benchmark vs the post-8.1 CPU baseline. Only chase OpenCL or NPU if results don't satisfy.

**Note on `flash_attn`:** the param already defaults to `true` in `whisper_context_default_params()`, but is short-circuited by `src/whisper.cpp:1140` unless `use_gpu` is also true. Once a GPU backend is built in, flash attention activates automatically — no `jni.c` change needed beyond ensuring `cparams.use_gpu = true` and `cparams.flash_attn = true` (both true by default).

### 8.3 QWERTY Symbol Layout Expansion — **DONE (2026-05-17)**

Resolved to two symbol pages (Gboard-style). Row-3 left slot reuses the shift position:
- letters mode: `⇧` (shift)
- symbol page 1: `=\<` (flip to page 2)
- symbol page 2: `?123` (flip back to page 1)

Bottom-row toggle stays as `?123` ↔ `ABC` between letters and symbols.

All requested missing symbols (`\ / = | < > [ ] { } ~ ^ % \``) now live somewhere in the Default profile pages. Tab not included; no good slot and not yet a felt need.

### 8.4 Keyboard Layout Profile System — **DONE (2026-05-17)**

Resolved as **built-in presets only**. Implemented:

- `KeyboardProfile` data class + `KeyboardProfiles` singleton with three presets:
  - **Default** — Gboard-style symbol pages
  - **Developer** — iOS-style coder grouping (brackets/escapes clustered on page 2 row 1)
  - **Writer** — em-dash + smart quotes promoted to page 1 row 3
- New `keyboard_profile` ListPreference (defaults to `"default"`) in `preferences.xml`
- Matching `keyboard_profile_entries` / `_values` arrays in `strings.xml`
- `QwertyKeyboardView` reads the active profile at construction via `PreferenceManager`. Changes take effect next time the keyboard view inflates (acceptable; could later be live via a pref-change listener).

### 8.5 Cross-References

- `Android/PERF.md` — detailed analysis of CPU bottlenecks and future optimizations
- `Android/TODO.md` — actionable task list and the three open design questions

---

## Technical Decisions & Rationale

### Why whisper.cpp?
- Pure C/C++ implementation, easy to build for Android via NDK
- Optimized for CPU inference with SIMD support
- Active community and regular updates
- Smaller footprint than Python alternatives

### Why not use Android's built-in speech recognition?
- Built-in requires network connectivity (Google's servers)
- Privacy concerns with sending voice data to cloud
- Less control over model and behavior
- WhisperKey's value proposition is fully local operation

### Recommended Initial Model: `tiny` or `base`
- `tiny`: ~75MB, fastest inference, acceptable accuracy
- `base`: ~142MB, good balance of speed and accuracy
- Can offer larger models as optional downloads

### Minimum Android Version: API 26 (Android 8.0)
- Good AudioRecord API support
- Covers ~95% of active devices
- Modern NDK features available

---

## Implementation Order

1. **Phase 1** - Project setup, Gradle, NDK configuration
2. **Phase 2** - whisper.cpp integration, JNI bridge (key milestone - verify transcription works)
3. **Phase 3** - Audio capture system
4. **Phase 4** - Basic IME service that can show UI and insert text
5. **Phase 5** - Voice-focused UI with all states, settings with model downloads
6. **Phase 6** - Integration, both recording modes, error handling
7. **Phase 7** - Testing and release preparation

---

## Design Decisions (Confirmed)

| Decision | Choice |
|----------|--------|
| Recording Mode | Both tap-to-toggle AND hold-to-record (user selectable in settings) |
| Model Bundling | Download on demand when selected in settings |
| Keyboard Style | Voice-focused minimal interface (no QWERTY) |
| Target Language | English only (initially) |
| UI Framework | Traditional Android Views (most stable for IME) |

---

## Resources & References

- [whisper.cpp GitHub](https://github.com/ggerganov/whisper.cpp)
- [whisper.cpp Android Example](https://github.com/ggerganov/whisper.cpp/tree/master/examples/whisper.android) - Reference implementation
- [Whisper Models on Hugging Face](https://huggingface.co/ggerganov/whisper.cpp) - Download source
- [Android IME Documentation](https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method)
- [AudioRecord API](https://developer.android.com/reference/android/media/AudioRecord)
- [Android NDK Guide](https://developer.android.com/ndk/guides)
