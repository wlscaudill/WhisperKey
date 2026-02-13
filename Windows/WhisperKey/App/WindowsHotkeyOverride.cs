using Microsoft.Win32;
using WhisperKeys.Settings;

namespace WhisperKeys.App;

/// <summary>
/// Disables built-in Windows Win+key hotkeys via registry so WhisperKeys can claim them.
/// Works by writing to HKCU\SOFTWARE\Microsoft\Windows\CurrentVersion\Explorer\Advanced\DisabledHotkeys.
/// Requires an Explorer restart to take effect.
/// </summary>
public static class WindowsHotkeyOverride
{
    private const string RegistryPath = @"SOFTWARE\Microsoft\Windows\CurrentVersion\Explorer\Advanced";
    private const string ValueName = "DisabledHotkeys";

    /// <summary>
    /// Returns true if the hotkey uses the Win modifier (the kind we can override).
    /// </summary>
    public static bool CanOverride(HotkeySettings hotkey)
    {
        // We can only override Win+<single letter> combos that are built-in Windows shortcuts
        if (!hotkey.Win) return false;
        var key = (Keys)hotkey.Key;
        return key >= Keys.A && key <= Keys.Z;
    }

    /// <summary>
    /// Disables the Windows built-in Win+key shortcut by adding the letter to the registry.
    /// </summary>
    public static void DisableWindowsHotkey(HotkeySettings hotkey)
    {
        var letter = ((Keys)hotkey.Key).ToString();

        using var key = Registry.CurrentUser.OpenSubKey(RegistryPath, writable: true);
        if (key == null) return;

        var current = key.GetValue(ValueName) as string ?? "";

        if (!current.Contains(letter, StringComparison.OrdinalIgnoreCase))
        {
            key.SetValue(ValueName, current + letter, RegistryValueKind.String);
            Logger.Log($"Added '{letter}' to DisabledHotkeys registry (was: \"{current}\")");
        }
    }

    /// <summary>
    /// Re-enables a previously disabled Windows hotkey.
    /// </summary>
    public static void RestoreWindowsHotkey(HotkeySettings hotkey)
    {
        var letter = ((Keys)hotkey.Key).ToString();

        using var key = Registry.CurrentUser.OpenSubKey(RegistryPath, writable: true);
        if (key == null) return;

        var current = key.GetValue(ValueName) as string ?? "";
        var updated = current.Replace(letter, "", StringComparison.OrdinalIgnoreCase);

        if (updated != current)
        {
            key.SetValue(ValueName, updated, RegistryValueKind.String);
            Logger.Log($"Removed '{letter}' from DisabledHotkeys registry");
        }
    }

    /// <summary>
    /// Restarts Explorer so the registry change takes effect immediately.
    /// </summary>
    public static void RestartExplorer()
    {
        Logger.Log("Restarting Explorer...");
        try
        {
            // Kill explorer
            var killInfo = new System.Diagnostics.ProcessStartInfo("taskkill", "/f /im explorer.exe")
            {
                CreateNoWindow = true,
                UseShellExecute = false
            };
            var kill = System.Diagnostics.Process.Start(killInfo);
            kill?.WaitForExit(5000);

            // Restart explorer
            System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo("explorer.exe")
            {
                UseShellExecute = true
            });

            // Give Explorer time to restart
            Thread.Sleep(2000);
            Logger.Log("Explorer restarted");
        }
        catch (Exception ex)
        {
            Logger.Error("Failed to restart Explorer", ex);
        }
    }
}
