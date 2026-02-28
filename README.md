# WhisperKey

WhisperKey is a local, privacy-first voice-to-text input tool powered by [whisper.cpp](https://github.com/ggerganov/whisper.cpp). All speech recognition runs entirely on-device with no data sent to the cloud.

Available as an **Android keyboard** and a **Windows system tray application**.

## Android

WhisperKey for Android is a custom Input Method Editor (IME) that replaces your keyboard with a voice-first input experience.

### Features

- **Voice input** with real-time waveform visualization
- **QWERTY keyboard** with shift and symbols layers
- **Numeric 10-key pad** for number entry
- **Customizable emoji hotkeys** (10 quick-access buttons on the voice keyboard, plus voice-to-emoji triggers)
- **Two recording modes**: tap-to-toggle or hold-to-record
- **System speech recognizer**: other keyboards (Gboard, Samsung, etc.) can use WhisperKey as their voice engine
- **Multiple Whisper model sizes**: download and switch between models from within the app
- **Flexible storage**: store models on internal storage or SD card
- **Clipboard support**: cut, copy, paste, and select all
- **Dynamic Enter key**: adapts to the input field (Go, Search, Send, Done)
- **Haptic feedback** (configurable)
- Requires Android 8.0+ (API 26)

### Installing

Download the latest APK from [GitHub Releases](https://github.com/wlscaudill/WhisperKey/releases) (look for the `android-v*` tag). Transfer it to your device and install — you may need to enable "Install from unknown sources" in your device settings.

You can also check for updates from within the app: **Settings > About > Check for Updates**.

### Building from Source

1. Open the `Android/` folder in **Android Studio**
2. Let Gradle sync and download dependencies
3. Build the project: **Build > Build Bundle(s) / APK(s) > Build APK(s)**
4. The generated APK will be in `Android/app/build/outputs/apk/debug/`

To create a release build and publish to GitHub:

```powershell
cd Android
.\Build.ps1 -Release
```

This bumps the version, builds a release APK, and creates a GitHub release tagged `android-v{version}` with the APK attached.

### Enabling the Keyboard

After installing:

1. Open **Settings > System > Languages & input > On-screen keyboard** (path varies by device)
2. Enable **WhisperKey**
3. When typing in any app, tap the keyboard icon in the navigation bar to switch to WhisperKey
4. On first use, the app will download your selected Whisper model

You can also open the WhisperKey app directly to configure model size, recording mode, emoji hotkeys, and other settings.

---

## Windows

WhisperKey for Windows runs as a system tray application. Press a global hotkey to record your voice, and the transcribed text is automatically typed into whatever application has focus.

### Features

- **Global hotkey** (default: Win+H) to trigger voice recording from any application
- **Two recording modes**: toggle (press to start/stop) or push-to-talk (hold to record)
- **Automatic text injection** via clipboard paste into the active window
- **System tray icon** with color-coded status (green = ready, red = recording, orange = processing)
- **10 Whisper model sizes** available, from Tiny (~75 MB) to Large v3 (~3.1 GB)
- **English-only and multilingual** model variants
- **Configurable microphone** selection
- **Clipboard restoration**: optionally restores your clipboard contents after pasting transcribed text
- **Hotkey conflict resolution**: detects conflicts with Windows shortcuts and offers to override them or pick a different key
- **Update checker**: automatically checks for new releases on startup (can be disabled in Settings), or manually via the tray menu
- Requires Windows 10+

### Installing

Download the latest zip from [GitHub Releases](https://github.com/wlscaudill/WhisperKey/releases) (look for the `windows-v*` tag). Extract it to a folder of your choice and run `WhisperKey.exe`.

### Prerequisites (building from source)

- [.NET 8.0 SDK](https://dotnet.microsoft.com/download/dotnet/8.0) (build and runtime)
- [Microsoft Visual C++ Redistributable](https://learn.microsoft.com/en-us/cpp/windows/latest-supported-vc-redist) (required by the native whisper.cpp runtime)

### Building from Source

From a PowerShell terminal in the `Windows/` directory:

```powershell
# Debug build only
.\Build.ps1

# Build and run immediately
.\Build.ps1 -Run

# Release build and publish to Windows/publish/
.\Build.ps1 -Configuration Release -Publish

# Kill running instance, rebuild, publish, and relaunch
.\Build.ps1 -Restart
```

To create a release build and publish to GitHub:

```powershell
.\Build.ps1 -Release
```

This bumps the version, builds, publishes, zips the output, and creates a GitHub release tagged `windows-v{version}` with the zip attached.

### Running

1. Run `WhisperKey.exe` from the `publish/` directory (or extracted release zip)
2. On first launch, the app will download the default Whisper model (~150 MB for base English)
3. A tray icon appears in your system tray — right-click it for options
4. Press your configured hotkey (default: Win+H) to start recording, press again to stop
5. The transcribed text is automatically pasted into the active application

### Settings

Right-click the tray icon and select **Settings** (or double-click the icon) to configure:

- **Recording mode** (toggle or push-to-talk)
- **Hotkey** (any modifier + key combination)
- **Language** (English or auto-detect)
- **Microphone** device
- **Clipboard restoration** after paste
- **Start with Windows** — auto-launch on login
- **Whisper model** — download, delete, and switch between models
- **Check for updates on startup** — disable if you build from source

Settings are stored in `%LocalAppData%\WhisperKey\settings.json`. Models are downloaded to `%LocalAppData%\WhisperKey\Models\`.

---

## Project Structure

```
WhisperKey/
  Android/          Android keyboard app (Kotlin)
  Windows/          Windows tray app (C# / .NET 8.0)
  whisper.cpp/      Whisper.cpp library (git submodule)
```

## License

This project uses [whisper.cpp](https://github.com/ggerganov/whisper.cpp) which is licensed under the MIT License. Whisper models are from OpenAI and subject to their license terms.
