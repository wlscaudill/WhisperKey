# WhisperKeys - Local Speech-to-Text for Windows

## Overview

A lightweight Windows system tray application that captures speech via hotkey and injects transcribed text at the cursor position. Uses whisper.cpp via Whisper.net for fast, local transcription with no cloud dependencies.

## Priorities (per user)
1. **Fast** - minimal latency from speech to text
2. **Stable** - reliable operation without crashes
3. **Consistent** - works in any application that accepts text

## Tech Stack

| Component | Choice | Rationale |
|-----------|--------|-----------|
| Runtime | .NET 8 (LTS) | Modern, fast, single-file publish support |
| UI | WinForms | Simple, fast to build, minimal overhead |
| Transcription | Whisper.net | C# wrapper around whisper.cpp, native performance |
| Audio Capture | NAudio | Mature, well-documented Windows audio library |
| Text Injection | Clipboard + SendKeys | Most reliable cross-application method |
| Hotkey | Win32 RegisterHotKey | Works globally, even when app not focused |

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    System Tray App                       │
├─────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐  │
│  │   Hotkey    │  │   Audio     │  │   Transcriber   │  │
│  │   Listener  │──│   Recorder  │──│   (Whisper.net) │  │
│  └─────────────┘  └─────────────┘  └─────────────────┘  │
│         │                                    │          │
│         │         ┌─────────────┐           │          │
│         │         │    Text     │───────────┘          │
│         └────────▶│   Injector  │                      │
│                   └─────────────┘                      │
│                          │                              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐  │
│  │  Settings   │  │   Model     │  │    Settings     │  │
│  │    Form     │──│   Manager   │  │    (JSON)       │  │
│  └─────────────┘  └─────────────┘  └─────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

## Project Structure

```
WhisperKeys/
├── WhisperKeys.sln
├── src/
│   └── WhisperKeys/
│       ├── WhisperKeys.csproj
│       ├── Program.cs                 # Entry point
│       ├── App/
│       │   ├── TrayApplication.cs     # System tray icon & menu
│       │   └── HotkeyManager.cs       # Global hotkey registration
│       ├── Audio/
│       │   └── AudioRecorder.cs       # NAudio microphone capture
│       ├── Transcription/
│       │   ├── WhisperTranscriber.cs  # Whisper.net wrapper
│       │   └── ModelManager.cs        # Download & manage models
│       ├── Input/
│       │   └── TextInjector.cs        # Clipboard + paste injection
│       ├── Settings/
│       │   ├── AppSettings.cs         # Settings model
│       │   ├── SettingsManager.cs     # Load/save JSON settings
│       │   └── SettingsForm.cs        # WinForms settings UI
│       └── Resources/
│           └── icon.ico               # Tray icon
└── README.md
```

## Core Components

### 1. Program.cs - Entry Point
- Initialize single-instance mutex (prevent multiple instances)
- Load settings
- Initialize Whisper model (async, show loading indicator)
- Start system tray application
- Run message loop

### 2. TrayApplication.cs - System Tray
- NotifyIcon with context menu:
  - "Settings" - opens SettingsForm
  - "Enabled" - checkbox to toggle listening
  - Separator
  - "Exit"
- Double-click opens settings
- Icon changes to indicate state (idle, recording, processing)

### 3. HotkeyManager.cs - Global Hotkey
- Uses Win32 `RegisterHotKey` / `UnregisterHotKey`
- Default: Win+H
- Supports configurable modifier + key combinations
- Handles both push-to-talk and toggle modes:
  - **Push-to-talk**: KeyDown starts recording, KeyUp stops and transcribes
  - **Toggle**: First press starts, second press stops and transcribes

### 4. AudioRecorder.cs - Microphone Capture
- NAudio WaveInEvent for microphone input
- 16kHz sample rate, mono (Whisper's expected format)
- Buffers audio to MemoryStream
- Optional: Voice Activity Detection (VAD) for auto-stop in toggle mode
- Returns float[] PCM data for Whisper

### 5. WhisperTranscriber.cs - Transcription
- Whisper.net WhisperFactory and WhisperProcessor
- Loads model once at startup (lazy load on first use)
- Transcribe method: float[] audio → string text
- Configurable language (default: English, or auto-detect)
- Uses beam_size=5 for accuracy

### 6. ModelManager.cs - Model Downloads
- Models stored in: `%LOCALAPPDATA%\WhisperKeys\Models\`
- Download from Hugging Face (ggerganov/whisper.cpp repo)
- Available models:
  - ggml-tiny.en.bin (~75MB) - fastest
  - ggml-base.en.bin (~150MB) - good balance
  - ggml-small.en.bin (~500MB) - better accuracy
  - ggml-medium.en.bin (~1.5GB) - high accuracy
- Progress reporting for downloads
- Verify file integrity after download

### 7. TextInjector.cs - Text Output
- **Primary method**: Clipboard + Ctrl+V
  1. Save current clipboard contents
  2. Set clipboard to transcribed text
  3. Send Ctrl+V via SendKeys or SendInput
  4. Restore original clipboard (optional, configurable)
- This is the most reliable method across all applications
- Small delay (~50ms) between clipboard set and paste for reliability

### 8. AppSettings.cs - Configuration
```csharp
public class AppSettings
{
    public HotkeySettings Hotkey { get; set; } = new();
    public string ModelPath { get; set; } = "ggml-base.en.bin";
    public RecordingMode Mode { get; set; } = RecordingMode.PushToTalk;
    public string Language { get; set; } = "en";
    public bool RestoreClipboard { get; set; } = true;
    public int SilenceTimeoutMs { get; set; } = 1500; // For toggle mode
}

public class HotkeySettings
{
    public Keys Key { get; set; } = Keys.H;
    public bool Win { get; set; } = true;
    public bool Ctrl { get; set; } = false;
    public bool Alt { get; set; } = false;
    public bool Shift { get; set; } = false;
}

public enum RecordingMode { PushToTalk, Toggle }
```

### 9. SettingsForm.cs - Settings UI
WinForms dialog with tabs or sections:

**General Tab:**
- Recording mode: Radio buttons (Push-to-talk / Toggle)
- Hotkey: TextBox showing current hotkey + "Change" button
- Language: Dropdown (English, Auto-detect, others)
- [ ] Restore clipboard after paste

**Model Tab:**
- Current model: Dropdown of downloaded models
- Download section:
  - List of available models with sizes
  - Download button + progress bar
  - Delete button for downloaded models

**About Tab:**
- Version info
- Links

## NuGet Packages

```xml
<ItemGroup>
  <PackageReference Include="Whisper.net" Version="1.5.0" />
  <PackageReference Include="Whisper.net.Runtime" Version="1.5.0" />
  <PackageReference Include="NAudio" Version="2.2.1" />
  <PackageReference Include="InputSimulatorStandard" Version="1.0.0" />
  <PackageReference Include="System.Text.Json" Version="8.0.0" />
</ItemGroup>
```

## Key Implementation Details

### Hotkey Registration (Win32)
```csharp
[DllImport("user32.dll")]
private static extern bool RegisterHotKey(IntPtr hWnd, int id, uint fsModifiers, uint vk);

[DllImport("user32.dll")]
private static extern bool UnregisterHotKey(IntPtr hWnd, int id);

// MOD_WIN = 0x0008, MOD_NOREPEAT = 0x4000
```

### Audio Format for Whisper
```csharp
var waveFormat = new WaveFormat(16000, 16, 1); // 16kHz, 16-bit, mono
```

### Text Injection (most reliable approach)
```csharp
public void InjectText(string text)
{
    var previousClipboard = Clipboard.GetDataObject();

    Clipboard.SetText(text);
    Thread.Sleep(50); // Allow clipboard to settle

    SendKeys.SendWait("^v"); // Ctrl+V

    if (_settings.RestoreClipboard && previousClipboard != null)
    {
        Thread.Sleep(100);
        Clipboard.SetDataObject(previousClipboard);
    }
}
```

### Model URLs (Hugging Face)
```
https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin
https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin
https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.en.bin
https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-medium.en.bin
```

## Build & Publish

### Development
```bash
dotnet build
dotnet run
```

### Release (single-file, no runtime required)
```bash
dotnet publish -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true
```

This produces a single .exe (~50-80MB including .NET runtime and Whisper native libs).

### Optional: Startup with Windows
Add to `shell:startup` folder or registry key:
`HKCU\Software\Microsoft\Windows\CurrentVersion\Run`

## Implementation Order

1. **Phase 1: Core Pipeline**
   - [ ] Project setup with NuGet packages
   - [ ] AudioRecorder: capture mic to float[]
   - [ ] WhisperTranscriber: transcribe audio
   - [ ] TextInjector: paste text
   - [ ] Basic console test app to verify pipeline

2. **Phase 2: System Tray & Hotkey**
   - [ ] TrayApplication with icon and menu
   - [ ] HotkeyManager with Win32 registration
   - [ ] Wire up hotkey → record → transcribe → inject

3. **Phase 3: Settings**
   - [ ] AppSettings model and SettingsManager (JSON)
   - [ ] SettingsForm UI
   - [ ] Apply settings changes at runtime

4. **Phase 4: Model Management**
   - [ ] ModelManager: list, download, delete models
   - [ ] Add model management to SettingsForm
   - [ ] Progress reporting for downloads

5. **Phase 5: Polish**
   - [ ] Error handling and logging
   - [ ] Tray icon states (idle/recording/processing)
   - [ ] Installer or auto-start option
   - [ ] Testing across various applications

## Testing Verification

1. **Audio capture**: Record 5 seconds, save to WAV, verify playback
2. **Transcription**: Transcribe test WAV, verify output
3. **Text injection**: Test in Notepad, browser, VS Code, terminal
4. **Hotkey**: Verify hotkey works when app is not focused
5. **Settings persistence**: Change settings, restart app, verify loaded
6. **Model download**: Download a model, verify file exists and loads

## Performance Notes

- **Model loading**: ~1-3 seconds for base model, do async at startup
- **Transcription speed**: ~0.5-2x realtime on modern CPU with base model
- **tiny.en model**: Fastest, good for short phrases, ~2x realtime
- **base.en model**: Best balance, recommended default
- Consider GPU acceleration via Whisper.net.Runtime.Cuda if NVIDIA GPU present (future enhancement)
