using WhisperKeys.App;
using WhisperKeys.Audio;
using WhisperKeys.Transcription;

namespace WhisperKeys.Settings;

public class SettingsForm : Form
{
    private readonly ModelManager _modelManager;
    public AppSettings CurrentSettings { get; private set; }

    // General tab controls
    private RadioButton _toggleMode = null!;
    private RadioButton _pushToTalkMode = null!;
    private TextBox _hotkeyDisplay = null!;
    private Button _changeHotkeyBtn = null!;
    private ComboBox _languageCombo = null!;
    private ComboBox _audioDeviceCombo = null!;
    private CheckBox _restoreClipboardCheck = null!;
    private CheckBox _startWithWindowsCheck = null!;

    // Model tab controls
    private ComboBox _modelCombo = null!;
    private ListView _availableModelsList = null!;
    private Button _downloadBtn = null!;
    private Button _deleteBtn = null!;
    private ProgressBar _downloadProgress = null!;
    private Label _downloadStatusLabel = null!;

    private CancellationTokenSource? _downloadCts;

    public SettingsForm(AppSettings settings, ModelManager modelManager)
    {
        CurrentSettings = CloneSettings(settings);
        _modelManager = modelManager;
        InitializeUI();
        LoadSettingsToUI();
    }

    private void InitializeUI()
    {
        Text = "WhisperKey Settings";
        Size = new Size(700, 720);
        Padding = new Padding(16);
        FormBorderStyle = FormBorderStyle.FixedDialog;
        MaximizeBox = false;
        MinimizeBox = false;
        StartPosition = FormStartPosition.CenterScreen;

        var tabControl = new TabControl
        {
            Dock = DockStyle.Fill,
            Padding = new Point(12, 6)
        };

        tabControl.TabPages.Add(CreateGeneralTab());
        tabControl.TabPages.Add(CreateModelTab());

        var buttonPanel = new FlowLayoutPanel
        {
            Dock = DockStyle.Bottom,
            FlowDirection = FlowDirection.RightToLeft,
            Height = 60,
            Padding = new Padding(16, 12, 16, 12)
        };

        var cancelBtn = new Button { Text = "Cancel", DialogResult = DialogResult.Cancel, Width = 120, Height = 32 };
        var okBtn = new Button { Text = "OK", Width = 120, Height = 32 };
        okBtn.Click += OnOkClick;

        buttonPanel.Controls.Add(cancelBtn);
        buttonPanel.Controls.Add(okBtn);

        Controls.Add(tabControl);
        Controls.Add(buttonPanel);

        AcceptButton = okBtn;
        CancelButton = cancelBtn;
    }

    private TabPage CreateGeneralTab()
    {
        var page = new TabPage("General") { Padding = new Padding(20) };

        int y = 24;
        int labelX = 24;
        int controlX = 160;
        int controlW = 460;

        // Recording mode
        page.Controls.Add(new Label { Text = "Recording Mode:", Location = new Point(labelX, y), AutoSize = true });
        y += 32;

        _toggleMode = new RadioButton { Text = "Toggle (press to start, press to stop)", Location = new Point(labelX + 20, y), AutoSize = true };
        y += 34;
        _pushToTalkMode = new RadioButton { Text = "Push-to-Talk (hold to record, release to stop)", Location = new Point(labelX + 20, y), AutoSize = true };
        y += 48;

        page.Controls.Add(_toggleMode);
        page.Controls.Add(_pushToTalkMode);

        // Hotkey
        page.Controls.Add(new Label { Text = "Hotkey:", Location = new Point(labelX, y + 4), AutoSize = true });
        _hotkeyDisplay = new TextBox { Location = new Point(controlX, y), Width = 240, Height = 28, ReadOnly = true };
        _changeHotkeyBtn = new Button { Text = "Change...", Location = new Point(controlX + 252, y - 1), Width = 120, Height = 30 };
        _changeHotkeyBtn.Click += OnChangeHotkeyClick;
        y += 48;

        page.Controls.Add(_hotkeyDisplay);
        page.Controls.Add(_changeHotkeyBtn);

        // Language
        page.Controls.Add(new Label { Text = "Language:", Location = new Point(labelX, y + 4), AutoSize = true });
        _languageCombo = new ComboBox
        {
            Location = new Point(controlX, y),
            Width = 240,
            DropDownStyle = ComboBoxStyle.DropDownList
        };
        _languageCombo.Items.AddRange(new object[] { "English", "Auto-detect" });
        y += 48;

        page.Controls.Add(_languageCombo);

        // Audio device
        page.Controls.Add(new Label { Text = "Microphone:", Location = new Point(labelX, y + 4), AutoSize = true });
        _audioDeviceCombo = new ComboBox
        {
            Location = new Point(controlX, y),
            Width = controlW,
            DropDownStyle = ComboBoxStyle.DropDownList
        };
        PopulateAudioDevices();
        y += 48;

        page.Controls.Add(_audioDeviceCombo);

        // Restore clipboard
        _restoreClipboardCheck = new CheckBox
        {
            Text = "Restore clipboard contents after paste",
            Location = new Point(labelX, y),
            AutoSize = true
        };
        y += 36;

        page.Controls.Add(_restoreClipboardCheck);

        // Start with Windows
        _startWithWindowsCheck = new CheckBox
        {
            Text = "Start with Windows",
            Location = new Point(labelX, y),
            AutoSize = true
        };
        page.Controls.Add(_startWithWindowsCheck);

        return page;
    }

    private TabPage CreateModelTab()
    {
        var page = new TabPage("Model") { Padding = new Padding(20) };

        int y = 24;
        int labelX = 24;
        int controlX = 160;
        int fullW = 580;

        // Current model
        page.Controls.Add(new Label { Text = "Active Model:", Location = new Point(labelX, y + 4), AutoSize = true });
        _modelCombo = new ComboBox
        {
            Location = new Point(controlX, y),
            Width = fullW - controlX + labelX,
            DropDownStyle = ComboBoxStyle.DropDownList
        };
        y += 48;

        page.Controls.Add(_modelCombo);

        // Available models list
        page.Controls.Add(new Label { Text = "Available Models:", Location = new Point(labelX, y), AutoSize = true });
        y += 28;

        _availableModelsList = new ListView
        {
            Location = new Point(labelX, y),
            Size = new Size(fullW, 250),
            View = View.Details,
            FullRowSelect = true,
            MultiSelect = false
        };
        _availableModelsList.Columns.Add("Model", 260);
        _availableModelsList.Columns.Add("Size", 120);
        _availableModelsList.Columns.Add("Status", 170);
        y += 262;

        page.Controls.Add(_availableModelsList);

        // Download/Delete buttons
        int btnW = (fullW - 16) / 2;
        _downloadBtn = new Button { Text = "Download Selected", Location = new Point(labelX, y), Width = btnW, Height = 34 };
        _downloadBtn.Click += OnDownloadClick;

        _deleteBtn = new Button { Text = "Delete Selected", Location = new Point(labelX + btnW + 16, y), Width = btnW, Height = 34 };
        _deleteBtn.Click += OnDeleteClick;
        y += 46;

        page.Controls.Add(_downloadBtn);
        page.Controls.Add(_deleteBtn);

        // Progress
        _downloadProgress = new ProgressBar
        {
            Location = new Point(labelX, y),
            Size = new Size(fullW, 24),
            Visible = false
        };
        y += 32;

        _downloadStatusLabel = new Label
        {
            Location = new Point(labelX, y),
            AutoSize = true,
            Text = ""
        };

        page.Controls.Add(_downloadProgress);
        page.Controls.Add(_downloadStatusLabel);

        RefreshModelList();

        return page;
    }

    private void PopulateAudioDevices()
    {
        _audioDeviceCombo.Items.Clear();
        _audioDeviceCombo.Items.Add("(System Default)");

        var devices = AudioRecorder.GetAvailableDevices();
        foreach (var device in devices)
        {
            _audioDeviceCombo.Items.Add($"{device.Name}");
        }
    }

    private void LoadSettingsToUI()
    {
        // Mode
        if (CurrentSettings.Mode == RecordingMode.Toggle)
            _toggleMode.Checked = true;
        else
            _pushToTalkMode.Checked = true;

        // Hotkey
        _hotkeyDisplay.Text = CurrentSettings.Hotkey.ToString();

        // Language
        _languageCombo.SelectedIndex = CurrentSettings.Language == "en" ? 0 : 1;

        // Audio device: index 0 = default (-1), index 1+ = device 0, 1, ...
        var deviceIndex = CurrentSettings.AudioDeviceNumber + 1;
        if (deviceIndex >= 0 && deviceIndex < _audioDeviceCombo.Items.Count)
            _audioDeviceCombo.SelectedIndex = deviceIndex;
        else
            _audioDeviceCombo.SelectedIndex = 0;

        // Clipboard
        _restoreClipboardCheck.Checked = CurrentSettings.RestoreClipboard;

        // Start with Windows
        _startWithWindowsCheck.Checked = CurrentSettings.StartWithWindows;
    }

    private void SaveUIToSettings()
    {
        CurrentSettings.Mode = _toggleMode.Checked ? RecordingMode.Toggle : RecordingMode.PushToTalk;
        CurrentSettings.Language = _languageCombo.SelectedIndex == 0 ? "en" : "auto";
        CurrentSettings.AudioDeviceNumber = _audioDeviceCombo.SelectedIndex - 1; // -1 = default
        CurrentSettings.RestoreClipboard = _restoreClipboardCheck.Checked;
        CurrentSettings.StartWithWindows = _startWithWindowsCheck.Checked;

        if (_modelCombo.SelectedItem is string selectedModel)
        {
            CurrentSettings.ModelFileName = selectedModel;
        }
    }

    private void RefreshModelList()
    {
        _availableModelsList.Items.Clear();
        _modelCombo.Items.Clear();

        var models = _modelManager.GetModelsWithStatus();
        foreach (var model in models)
        {
            var item = new ListViewItem(model.DisplayName);
            item.SubItems.Add(model.Size);
            item.SubItems.Add(model.IsDownloaded ? "Downloaded" : "Not downloaded");
            item.Tag = model;
            _availableModelsList.Items.Add(item);

            if (model.IsDownloaded)
            {
                _modelCombo.Items.Add(model.FileName);
            }
        }

        // Select current model in combo
        var idx = _modelCombo.Items.IndexOf(CurrentSettings.ModelFileName);
        if (idx >= 0) _modelCombo.SelectedIndex = idx;
        else if (_modelCombo.Items.Count > 0) _modelCombo.SelectedIndex = 0;
    }

    private void OnChangeHotkeyClick(object? sender, EventArgs e)
    {
        using var picker = new HotkeyPickerDialog(CurrentSettings.Hotkey);
        if (picker.ShowDialog() == DialogResult.OK)
        {
            CurrentSettings.Hotkey = picker.SelectedHotkey;
            _hotkeyDisplay.Text = CurrentSettings.Hotkey.ToString();
        }
    }

    private async void OnDownloadClick(object? sender, EventArgs e)
    {
        if (_availableModelsList.SelectedItems.Count == 0)
        {
            MessageBox.Show("Please select a model to download.", "WhisperKey",
                MessageBoxButtons.OK, MessageBoxIcon.Information);
            return;
        }

        var modelInfo = (ModelInfo)_availableModelsList.SelectedItems[0].Tag!;
        if (modelInfo.IsDownloaded)
        {
            MessageBox.Show("This model is already downloaded.", "WhisperKey",
                MessageBoxButtons.OK, MessageBoxIcon.Information);
            return;
        }

        _downloadBtn.Enabled = false;
        _downloadProgress.Visible = true;
        _downloadProgress.Value = 0;
        _downloadStatusLabel.Text = $"Downloading {modelInfo.DisplayName}...";

        _downloadCts = new CancellationTokenSource();

        try
        {
            var progress = new Progress<double>(p =>
            {
                _downloadProgress.Value = (int)(p * 100);
                _downloadStatusLabel.Text = $"Downloading {modelInfo.DisplayName}... {p:P0}";
            });

            await _modelManager.DownloadModelAsync(modelInfo.FileName, progress, _downloadCts.Token);
            _downloadStatusLabel.Text = "Download complete!";
            RefreshModelList();
        }
        catch (OperationCanceledException)
        {
            _downloadStatusLabel.Text = "Download cancelled.";
        }
        catch (Exception ex)
        {
            _downloadStatusLabel.Text = $"Download failed: {ex.Message}";
            MessageBox.Show($"Download failed: {ex.Message}", "WhisperKey",
                MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
        finally
        {
            _downloadBtn.Enabled = true;
            _downloadProgress.Visible = false;
            _downloadCts?.Dispose();
            _downloadCts = null;
        }
    }

    private void OnDeleteClick(object? sender, EventArgs e)
    {
        if (_availableModelsList.SelectedItems.Count == 0)
        {
            MessageBox.Show("Please select a model to delete.", "WhisperKey",
                MessageBoxButtons.OK, MessageBoxIcon.Information);
            return;
        }

        var modelInfo = (ModelInfo)_availableModelsList.SelectedItems[0].Tag!;
        if (!modelInfo.IsDownloaded)
        {
            MessageBox.Show("This model is not downloaded.", "WhisperKey",
                MessageBoxButtons.OK, MessageBoxIcon.Information);
            return;
        }

        var result = MessageBox.Show(
            $"Delete {modelInfo.DisplayName}?", "WhisperKey",
            MessageBoxButtons.YesNo, MessageBoxIcon.Question);

        if (result == DialogResult.Yes)
        {
            _modelManager.DeleteModel(modelInfo.FileName);
            RefreshModelList();
        }
    }

    private void OnOkClick(object? sender, EventArgs e)
    {
        SaveUIToSettings();
        DialogResult = DialogResult.OK;
        Close();
    }

    private static AppSettings CloneSettings(AppSettings source)
    {
        return new AppSettings
        {
            Hotkey = new HotkeySettings
            {
                Key = source.Hotkey.Key,
                Win = source.Hotkey.Win,
                Ctrl = source.Hotkey.Ctrl,
                Alt = source.Hotkey.Alt,
                Shift = source.Hotkey.Shift
            },
            ModelFileName = source.ModelFileName,
            Mode = source.Mode,
            Language = source.Language,
            RestoreClipboard = source.RestoreClipboard,
            SilenceTimeoutMs = source.SilenceTimeoutMs,
            AudioDeviceNumber = source.AudioDeviceNumber,
            StartWithWindows = source.StartWithWindows
        };
    }

    protected override void Dispose(bool disposing)
    {
        if (disposing)
        {
            _downloadCts?.Cancel();
            _downloadCts?.Dispose();
        }
        base.Dispose(disposing);
    }
}
