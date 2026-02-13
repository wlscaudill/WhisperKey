namespace WhisperKeys.Settings;

public class AppSettings
{
    public HotkeySettings Hotkey { get; set; } = new();
    public string ModelFileName { get; set; } = "ggml-base.en.bin";
    public RecordingMode Mode { get; set; } = RecordingMode.Toggle;
    public string Language { get; set; } = "en";
    public bool RestoreClipboard { get; set; } = true;
    public int SilenceTimeoutMs { get; set; } = 1500;
    public int AudioDeviceNumber { get; set; } = -1; // -1 = system default
    public bool StartWithWindows { get; set; } = false;
}

public class HotkeySettings
{
    public int Key { get; set; } = (int)Keys.H;
    public bool Win { get; set; } = true;
    public bool Ctrl { get; set; } = false;
    public bool Alt { get; set; } = false;
    public bool Shift { get; set; } = false;

    public uint GetModifiers()
    {
        uint mods = 0;
        if (Win) mods |= 0x0008;   // MOD_WIN
        if (Ctrl) mods |= 0x0002;  // MOD_CONTROL
        if (Alt) mods |= 0x0001;   // MOD_ALT
        if (Shift) mods |= 0x0004; // MOD_SHIFT
        mods |= 0x4000;            // MOD_NOREPEAT
        return mods;
    }

    /// <summary>
    /// Returns modifiers without MOD_NOREPEAT for compatibility with older Windows builds.
    /// </summary>
    public uint GetModifiersCompat()
    {
        uint mods = 0;
        if (Win) mods |= 0x0008;   // MOD_WIN
        if (Ctrl) mods |= 0x0002;  // MOD_CONTROL
        if (Alt) mods |= 0x0001;   // MOD_ALT
        if (Shift) mods |= 0x0004; // MOD_SHIFT
        return mods;
    }

    public override string ToString()
    {
        var parts = new List<string>();
        if (Win) parts.Add("Win");
        if (Ctrl) parts.Add("Ctrl");
        if (Alt) parts.Add("Alt");
        if (Shift) parts.Add("Shift");
        parts.Add(((Keys)Key).ToString());
        return string.Join(" + ", parts);
    }
}

public enum RecordingMode
{
    PushToTalk,
    Toggle
}
