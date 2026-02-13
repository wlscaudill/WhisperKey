using System.Runtime.InteropServices;
using WhisperKeys.Audio;
using WhisperKeys.Input;
using WhisperKeys.Settings;
using WhisperKeys.Transcription;

namespace WhisperKeys.App;

public enum AppState
{
    Idle,
    OpeningMic,
    Recording,
    Processing,
    Loading,
    Error
}

public class TrayApplication : Form
{
    [DllImport("user32.dll")]
    private static extern short GetAsyncKeyState(int vKey);

    private readonly NotifyIcon _trayIcon;
    private readonly ContextMenuStrip _contextMenu;
    private readonly ToolStripMenuItem _enabledItem;
    private readonly ToolStripMenuItem _statusItem;

    private readonly HotkeyManager _hotkeyManager;
    private readonly AudioRecorder _audioRecorder;
    private readonly WhisperTranscriber _transcriber;
    private readonly TextInjector _textInjector;
    private readonly ModelManager _modelManager;
    private readonly System.Windows.Forms.Timer _pttTimer;
    private AppSettings _settings;

    private AppState _state = AppState.Loading;
    private bool _isEnabled = true;

    public TrayApplication(AppSettings settings)
    {
        _settings = settings;
        _hotkeyManager = new HotkeyManager();
        _audioRecorder = new AudioRecorder { DeviceNumber = settings.AudioDeviceNumber };
        _transcriber = new WhisperTranscriber();
        _modelManager = new ModelManager();
        _textInjector = new TextInjector(_settings);

        // Make form invisible
        ShowInTaskbar = false;
        WindowState = FormWindowState.Minimized;
        FormBorderStyle = FormBorderStyle.None;
        Opacity = 0;

        // Build context menu
        _statusItem = new ToolStripMenuItem("Loading...") { Enabled = false };
        _enabledItem = new ToolStripMenuItem("Enabled", null, OnEnabledToggle) { Checked = true };

        _contextMenu = new ContextMenuStrip();
        _contextMenu.Items.Add(_statusItem);
        _contextMenu.Items.Add(new ToolStripSeparator());
        _contextMenu.Items.Add(_enabledItem);
        _contextMenu.Items.Add("Settings...", null, OnSettingsClick);
        _contextMenu.Items.Add(new ToolStripSeparator());
        _contextMenu.Items.Add("Exit", null, OnExitClick);

        // Create tray icon
        _trayIcon = new NotifyIcon
        {
            Icon = CreateIcon(Color.Gray),
            Text = "WhisperKeys - Loading...",
            Visible = true,
            ContextMenuStrip = _contextMenu
        };

        _trayIcon.DoubleClick += OnSettingsClick;

        // PTT key-release polling timer
        _pttTimer = new System.Windows.Forms.Timer { Interval = 50 };
        _pttTimer.Tick += OnPttTimerTick;

        // Wire events
        _hotkeyManager.HotkeyPressed += OnHotkeyPressed;
        _audioRecorder.RecordingStarted += () => BeginInvoke(() => SetState(AppState.Recording));
        _transcriber.StatusChanged += status => BeginInvoke(() => UpdateStatus(status));
    }

    protected override void OnLoad(EventArgs e)
    {
        base.OnLoad(e);
        Logger.Log("TrayApplication loaded");

        RegisterHotkeyWithRetry();

        // Load model async
        _ = LoadModelAsync();
    }

    private void RegisterHotkeyWithRetry()
    {
        const int maxAttempts = 5;
        int attempt = 0;

        while (attempt < maxAttempts)
        {
            attempt++;

            if (_hotkeyManager.Register(Handle, _settings.Hotkey))
            {
                Logger.Log($"Hotkey registered: {_settings.Hotkey}");
                return;
            }

            Logger.Error($"Failed to register hotkey {_settings.Hotkey} (attempt {attempt}/{maxAttempts})");

            using var conflictDlg = new HotkeyConflictDialog(_settings.Hotkey);
            var result = conflictDlg.ShowDialog();

            if (result == DialogResult.OK)
            {
                var previousHotkey = _settings.Hotkey.ToString();
                _settings.Hotkey = conflictDlg.ResultHotkey;
                SettingsManager.Save(_settings);

                // If the user chose override (same hotkey), try once more then bail
                if (_settings.Hotkey.ToString() == previousHotkey)
                {
                    if (_hotkeyManager.Register(Handle, _settings.Hotkey))
                    {
                        Logger.Log($"Hotkey registered after override: {_settings.Hotkey}");
                        return;
                    }

                    Logger.Error($"Hotkey {_settings.Hotkey} still unavailable after override — " +
                        "likely a system-reserved shortcut on this Windows version");

                    MessageBox.Show(
                        $"The hotkey {_settings.Hotkey} is reserved by Windows on this system " +
                        "and cannot be overridden.\n\n" +
                        "Please choose a different hotkey (e.g. Ctrl+Shift+H).",
                        "WhisperKeys",
                        MessageBoxButtons.OK,
                        MessageBoxIcon.Warning);

                    // Fall through to show the conflict dialog again with a new attempt
                    continue;
                }

                // User chose a different hotkey — loop will retry registration
                continue;
            }

            // User cancelled — run without hotkey
            Logger.Log("User declined to change hotkey, running without hotkey");
            _trayIcon.ShowBalloonTip(3000, "WhisperKeys",
                "Running without a hotkey. Open Settings to configure one.",
                ToolTipIcon.Warning);
            return;
        }

        // Exhausted all attempts
        Logger.Error($"Failed to register any hotkey after {maxAttempts} attempts");
        _trayIcon.ShowBalloonTip(3000, "WhisperKeys",
            "Could not register a hotkey. Open Settings to configure one.",
            ToolTipIcon.Warning);
    }

    private async Task LoadModelAsync()
    {
        SetState(AppState.Loading);

        var modelPath = _modelManager.GetModelPath(_settings.ModelFileName);
        Logger.Log($"Model path: {modelPath}");

        if (!_modelManager.IsModelDownloaded(_settings.ModelFileName))
        {
            Logger.Log($"Model not found, downloading {_settings.ModelFileName}...");
            UpdateStatus($"Downloading {_settings.ModelFileName}...");
            _trayIcon.ShowBalloonTip(3000, "WhisperKeys",
                $"Downloading model {_settings.ModelFileName}...", ToolTipIcon.Info);

            try
            {
                var progress = new Progress<double>(p =>
                    BeginInvoke(() => UpdateStatus($"Downloading... {p:P0}")));
                await _modelManager.DownloadModelAsync(_settings.ModelFileName, progress);
                Logger.Log("Model download complete");
            }
            catch (Exception ex)
            {
                Logger.Error("Model download failed", ex);
                SetState(AppState.Error);
                _trayIcon.ShowBalloonTip(5000, "WhisperKeys",
                    $"Failed to download model: {ex.Message}", ToolTipIcon.Error);
                return;
            }
        }

        try
        {
            Logger.Log("Loading whisper model...");
            await _transcriber.LoadModelAsync(modelPath, _settings.Language);
            Logger.Log("Model loaded successfully");
            SetState(AppState.Idle);
            _trayIcon.ShowBalloonTip(2000, "WhisperKeys",
                $"Ready! Press {_settings.Hotkey} to record.", ToolTipIcon.Info);
        }
        catch (Exception ex)
        {
            Logger.Error("Model load failed", ex);
            SetState(AppState.Error);
            _trayIcon.ShowBalloonTip(5000, "WhisperKeys",
                $"Failed to load model: {ex.Message}", ToolTipIcon.Error);
        }
    }

    private void OnHotkeyPressed()
    {
        Logger.Log($"Hotkey pressed, state={_state}, enabled={_isEnabled}, mode={_settings.Mode}");
        if (!_isEnabled || _state == AppState.Loading || _state == AppState.Processing)
            return;

        if (_settings.Mode == RecordingMode.Toggle)
        {
            if (_state == AppState.Recording)
            {
                _ = StopAndTranscribeAsync();
            }
            else if (_state == AppState.Idle)
            {
                StartRecording();
            }
        }
        else // PushToTalk: key-down starts, key-up stops
        {
            if (_state == AppState.Idle)
            {
                StartRecording();
                _pttTimer.Start();
                Logger.Log("PTT timer started, polling for key release");
            }
        }
    }

    private async void OnPttTimerTick(object? sender, EventArgs e)
    {
        // Check if the main hotkey key is still held down
        // GetAsyncKeyState returns negative (high bit set) if key is currently pressed
        bool keyStillHeld = (GetAsyncKeyState(_settings.Hotkey.Key) & 0x8000) != 0;

        if (!keyStillHeld)
        {
            _pttTimer.Stop();
            Logger.Log("PTT key released");

            if (_state == AppState.Recording || _state == AppState.OpeningMic)
            {
                await StopAndTranscribeAsync();
            }
        }
    }

    private void StartRecording()
    {
        try
        {
            Logger.Log("Starting recording...");
            SetState(AppState.OpeningMic);
            _audioRecorder.StartRecording();
            // State flips to Recording once first audio data arrives (see constructor wiring)
        }
        catch (Exception ex)
        {
            Logger.Error("Recording failed to start", ex);
            SetState(AppState.Error);
            _trayIcon.ShowBalloonTip(3000, "WhisperKeys",
                $"Recording failed: {ex.Message}", ToolTipIcon.Error);
            SetState(AppState.Idle);
        }
    }

    private async Task StopAndTranscribeAsync()
    {
        SetState(AppState.Processing);

        try
        {
            var audioData = _audioRecorder.StopRecording();
            Logger.Log($"Recording stopped, {audioData.Length} samples ({audioData.Length / 16000.0:F1}s)");

            if (audioData.Length == 0)
            {
                Logger.Log("No audio data captured");
                SetState(AppState.Idle);
                return;
            }

            Logger.Log("Transcribing...");
            var text = await _transcriber.TranscribeAsync(audioData);
            Logger.Log($"Transcription result: \"{text}\"");

            if (!string.IsNullOrWhiteSpace(text))
            {
                _textInjector.InjectText(text);
                Logger.Log("Text injected");
            }
        }
        catch (Exception ex)
        {
            Logger.Error("Transcription failed", ex);
            _trayIcon.ShowBalloonTip(3000, "WhisperKeys",
                $"Transcription failed: {ex.Message}", ToolTipIcon.Error);
        }
        finally
        {
            SetState(AppState.Idle);
        }
    }

    private void SetState(AppState state)
    {
        _state = state;
        var (icon, tooltip, status) = state switch
        {
            AppState.Idle => (Color.LimeGreen, "WhisperKeys - Ready", "Ready"),
            AppState.OpeningMic => (Color.Yellow, "WhisperKeys - Opening mic...", "Opening mic..."),
            AppState.Recording => (Color.Red, "WhisperKeys - Recording...", "Recording..."),
            AppState.Processing => (Color.Orange, "WhisperKeys - Processing...", "Processing..."),
            AppState.Loading => (Color.Gray, "WhisperKeys - Loading...", "Loading..."),
            AppState.Error => (Color.DarkRed, "WhisperKeys - Error", "Error"),
            _ => (Color.Gray, "WhisperKeys", "Unknown")
        };

        _trayIcon.Icon = CreateIcon(icon);
        _trayIcon.Text = tooltip;
        _statusItem.Text = status;
    }

    private void UpdateStatus(string status)
    {
        _statusItem.Text = status;
        _trayIcon.Text = $"WhisperKeys - {status}";
    }

    private static Icon CreateIcon(Color color)
    {
        var bitmap = new Bitmap(16, 16);
        using var g = Graphics.FromImage(bitmap);
        g.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
        g.Clear(Color.Transparent);

        // Draw a filled circle as the icon
        using var brush = new SolidBrush(color);
        g.FillEllipse(brush, 1, 1, 14, 14);

        // Draw a small microphone shape in white
        using var pen = new Pen(Color.White, 1.5f);
        g.DrawLine(pen, 8, 3, 8, 9);    // Mic body
        g.DrawArc(pen, 5, 6, 6, 5, 0, 180); // Mic base arc
        g.DrawLine(pen, 8, 11, 8, 13);   // Mic stand

        var handle = bitmap.GetHicon();
        return Icon.FromHandle(handle);
    }

    private void OnEnabledToggle(object? sender, EventArgs e)
    {
        _isEnabled = !_isEnabled;
        _enabledItem.Checked = _isEnabled;

        if (_isEnabled)
        {
            _hotkeyManager.Register(Handle, _settings.Hotkey);
            SetState(AppState.Idle);
        }
        else
        {
            _pttTimer.Stop();
            if (_state == AppState.Recording)
            {
                _audioRecorder.StopRecording();
            }
            _hotkeyManager.Unregister();
            _trayIcon.Icon = CreateIcon(Color.Gray);
            _statusItem.Text = "Disabled";
            _trayIcon.Text = "WhisperKeys - Disabled";
        }
    }

    private void OnSettingsClick(object? sender, EventArgs e)
    {
        using var form = new SettingsForm(_settings, _modelManager);
        if (form.ShowDialog() == DialogResult.OK)
        {
            var oldSettings = _settings;
            _settings = form.CurrentSettings;
            SettingsManager.Save(_settings);

            // Re-register hotkey if changed
            if (oldSettings.Hotkey.ToString() != _settings.Hotkey.ToString())
            {
                _hotkeyManager.Register(Handle, _settings.Hotkey);
            }

            // Update audio device if changed
            if (oldSettings.AudioDeviceNumber != _settings.AudioDeviceNumber)
            {
                _audioRecorder.DeviceNumber = _settings.AudioDeviceNumber;
                Logger.Log($"Audio device changed to {_settings.AudioDeviceNumber}");
            }

            // Reload model if changed
            if (oldSettings.ModelFileName != _settings.ModelFileName ||
                oldSettings.Language != _settings.Language)
            {
                _ = LoadModelAsync();
            }
        }
    }

    private void OnExitClick(object? sender, EventArgs e)
    {
        _trayIcon.Visible = false;
        Application.Exit();
    }

    protected override void WndProc(ref Message m)
    {
        _hotkeyManager.ProcessMessage(ref m);
        base.WndProc(ref m);
    }

    protected override void Dispose(bool disposing)
    {
        if (disposing)
        {
            _pttTimer.Stop();
            _pttTimer.Dispose();
            _hotkeyManager.Dispose();
            _audioRecorder.Dispose();
            _transcriber.Dispose();
            _trayIcon.Dispose();
            _contextMenu.Dispose();
        }
        base.Dispose(disposing);
    }
}
