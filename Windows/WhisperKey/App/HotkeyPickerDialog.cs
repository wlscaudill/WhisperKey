using WhisperKeys.Settings;

namespace WhisperKeys.App;

public class HotkeyPickerDialog : Form
{
    public HotkeySettings SelectedHotkey { get; private set; }

    private readonly TextBox _hotkeyDisplay;
    private readonly CheckBox _winCheck;
    private readonly CheckBox _ctrlCheck;
    private readonly CheckBox _altCheck;
    private readonly CheckBox _shiftCheck;
    private readonly Label _instructionLabel;
    private readonly Button _okBtn;

    private Keys _capturedKey = Keys.None;

    public HotkeyPickerDialog(HotkeySettings current)
    {
        SelectedHotkey = new HotkeySettings
        {
            Key = current.Key,
            Win = current.Win,
            Ctrl = current.Ctrl,
            Alt = current.Alt,
            Shift = current.Shift
        };

        Text = "Choose a Hotkey";
        Size = new Size(460, 320);
        FormBorderStyle = FormBorderStyle.FixedDialog;
        MaximizeBox = false;
        MinimizeBox = false;
        StartPosition = FormStartPosition.CenterScreen;
        KeyPreview = true;
        Padding = new Padding(20);

        int x = 24;
        int y = 24;

        _instructionLabel = new Label
        {
            Text = "Select modifiers and press a key below:",
            Location = new Point(x, y),
            AutoSize = true
        };
        y += 36;

        // Modifier checkboxes — spaced out
        _winCheck = new CheckBox { Text = "Win", Location = new Point(x, y), AutoSize = true, Checked = current.Win };
        _ctrlCheck = new CheckBox { Text = "Ctrl", Location = new Point(x + 80, y), AutoSize = true, Checked = current.Ctrl };
        _altCheck = new CheckBox { Text = "Alt", Location = new Point(x + 160, y), AutoSize = true, Checked = current.Alt };
        _shiftCheck = new CheckBox { Text = "Shift", Location = new Point(x + 240, y), AutoSize = true, Checked = current.Shift };
        y += 40;

        _capturedKey = (Keys)current.Key;

        // Key capture box
        var keyLabel = new Label { Text = "Key:", Location = new Point(x, y + 4), AutoSize = true };
        _hotkeyDisplay = new TextBox
        {
            Location = new Point(x + 60, y),
            Width = 240,
            Height = 28,
            ReadOnly = true,
            Text = ((Keys)current.Key).ToString(),
            Font = new Font(Font, FontStyle.Bold)
        };
        _hotkeyDisplay.Enter += (_, _) => _instructionLabel.Text = "Press any key...";
        _hotkeyDisplay.Leave += (_, _) => _instructionLabel.Text = "Select modifiers and press a key below:";
        y += 44;

        // Preview label
        var previewLabel = new Label
        {
            Text = "Preview:",
            Location = new Point(x, y + 2),
            AutoSize = true
        };
        var _previewDisplay = new Label
        {
            Location = new Point(x + 80, y + 2),
            AutoSize = true,
            Font = new Font(Font, FontStyle.Bold),
            Text = current.ToString()
        };
        y += 48;

        // Update preview when anything changes
        EventHandler updatePreview = (_, _) =>
        {
            var preview = BuildHotkey();
            _previewDisplay.Text = preview.ToString();
        };
        _winCheck.CheckedChanged += updatePreview;
        _ctrlCheck.CheckedChanged += updatePreview;
        _altCheck.CheckedChanged += updatePreview;
        _shiftCheck.CheckedChanged += updatePreview;

        // Buttons
        _okBtn = new Button { Text = "OK", Location = new Point(220, y), Width = 100, Height = 32 };
        _okBtn.Click += (_, _) =>
        {
            if (!_winCheck.Checked && !_ctrlCheck.Checked && !_altCheck.Checked && !_shiftCheck.Checked)
            {
                MessageBox.Show("Please select at least one modifier (Win, Ctrl, Alt, or Shift).",
                    "WhisperKey", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                return;
            }
            SelectedHotkey = BuildHotkey();
            DialogResult = DialogResult.OK;
            Close();
        };

        var cancelBtn = new Button { Text = "Cancel", Location = new Point(332, y), Width = 100, Height = 32, DialogResult = DialogResult.Cancel };

        Controls.AddRange(new Control[] {
            _instructionLabel,
            _winCheck, _ctrlCheck, _altCheck, _shiftCheck,
            keyLabel, _hotkeyDisplay,
            previewLabel, _previewDisplay,
            _okBtn, cancelBtn
        });

        AcceptButton = _okBtn;
        CancelButton = cancelBtn;

        KeyDown += OnFormKeyDown;
    }

    private void OnFormKeyDown(object? sender, KeyEventArgs e)
    {
        // Ignore modifier-only presses
        if (e.KeyCode is Keys.ControlKey or Keys.ShiftKey or Keys.Menu or Keys.LWin or Keys.RWin)
            return;

        _capturedKey = e.KeyCode;
        _hotkeyDisplay.Text = e.KeyCode.ToString();
        _instructionLabel.Text = "Select modifiers and press a key below:";

        // Trigger preview update
        _winCheck.Checked = _winCheck.Checked; // fires CheckedChanged

        e.Handled = true;
        e.SuppressKeyPress = true;
    }

    private HotkeySettings BuildHotkey()
    {
        return new HotkeySettings
        {
            Key = (int)_capturedKey,
            Win = _winCheck.Checked,
            Ctrl = _ctrlCheck.Checked,
            Alt = _altCheck.Checked,
            Shift = _shiftCheck.Checked
        };
    }
}
