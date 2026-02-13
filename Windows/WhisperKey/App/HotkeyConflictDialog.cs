using WhisperKeys.Settings;

namespace WhisperKeys.App;

/// <summary>
/// Dialog shown when a hotkey is already in use.
/// Offers: Override (disable Windows binding), Choose different, or Skip.
/// Returns DialogResult.OK to retry registration with ResultHotkey,
/// or DialogResult.Cancel to skip.
/// </summary>
public class HotkeyConflictDialog : Form
{
    public HotkeySettings ResultHotkey { get; private set; }

    public HotkeyConflictDialog(HotkeySettings conflicting)
    {
        ResultHotkey = conflicting;
        bool canOverride = WindowsHotkeyOverride.CanOverride(conflicting);

        Text = "WhisperKeys - Hotkey Conflict";
        Size = new Size(520, canOverride ? 310 : 260);
        FormBorderStyle = FormBorderStyle.FixedDialog;
        MaximizeBox = false;
        MinimizeBox = false;
        StartPosition = FormStartPosition.CenterScreen;
        Padding = new Padding(20);

        int x = 24;
        int btnW = 452;
        var y = 24;

        var msgLabel = new Label
        {
            Text = $"The hotkey  {conflicting}  is already in use by another application.",
            Location = new Point(x, y),
            Size = new Size(btnW, 32),
            Font = new Font(Font, FontStyle.Bold)
        };
        y += 40;

        Controls.Add(msgLabel);

        // Option 1: Override (only for Win+letter combos)
        if (canOverride)
        {
            var overrideBtn = new Button
            {
                Text = $"Disable Windows' {conflicting} and use it for WhisperKeys",
                Location = new Point(x, y),
                Size = new Size(btnW, 40)
            };
            overrideBtn.Click += (_, _) =>
            {
                Logger.Log($"User chose to override Windows hotkey {conflicting}");

                WindowsHotkeyOverride.DisableWindowsHotkey(conflicting);

                var restartResult = MessageBox.Show(
                    "The Windows hotkey has been disabled in the registry.\n\n" +
                    "Explorer needs to restart for the change to take effect.\n" +
                    "Restart Explorer now?\n\n" +
                    "(Your taskbar will briefly disappear and reappear.)",
                    "WhisperKeys",
                    MessageBoxButtons.YesNo,
                    MessageBoxIcon.Question);

                if (restartResult == DialogResult.Yes)
                {
                    WindowsHotkeyOverride.RestartExplorer();
                }

                ResultHotkey = conflicting;
                DialogResult = DialogResult.OK;
                Close();
            };
            y += 52;
            Controls.Add(overrideBtn);
        }

        // Option 2: Choose different
        var chooseBtn = new Button
        {
            Text = "Choose a different hotkey",
            Location = new Point(x, y),
            Size = new Size(btnW, 40)
        };
        chooseBtn.Click += (_, _) =>
        {
            using var picker = new HotkeyPickerDialog(conflicting);
            if (picker.ShowDialog() == DialogResult.OK)
            {
                Logger.Log($"User picked new hotkey: {picker.SelectedHotkey}");
                ResultHotkey = picker.SelectedHotkey;
                DialogResult = DialogResult.OK;
                Close();
            }
        };
        y += 52;
        Controls.Add(chooseBtn);

        // Option 3: Skip
        var skipBtn = new Button
        {
            Text = "Skip — run without a hotkey for now",
            Location = new Point(x, y),
            Size = new Size(btnW, 40)
        };
        skipBtn.Click += (_, _) =>
        {
            DialogResult = DialogResult.Cancel;
            Close();
        };
        Controls.Add(skipBtn);
    }
}
