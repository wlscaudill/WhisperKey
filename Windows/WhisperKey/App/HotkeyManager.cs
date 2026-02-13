using System.Runtime.InteropServices;
using WhisperKeys.Settings;

namespace WhisperKeys.App;

public class HotkeyManager : IDisposable
{
    private const int HOTKEY_ID = 1;
    private const int WM_HOTKEY = 0x0312;

    [DllImport("user32.dll")]
    private static extern bool RegisterHotKey(IntPtr hWnd, int id, uint fsModifiers, uint vk);

    [DllImport("user32.dll")]
    private static extern bool UnregisterHotKey(IntPtr hWnd, int id);

    private IntPtr _windowHandle;
    private bool _isRegistered;

    public event Action? HotkeyPressed;

    public bool Register(IntPtr windowHandle, HotkeySettings hotkey)
    {
        _windowHandle = windowHandle;

        // Unregister previous hotkey if any
        if (_isRegistered)
            Unregister();

        _isRegistered = RegisterHotKey(
            _windowHandle,
            HOTKEY_ID,
            hotkey.GetModifiers(),
            (uint)hotkey.Key);

        if (!_isRegistered)
        {
            System.Diagnostics.Debug.WriteLine(
                $"Failed to register hotkey {hotkey}. Error: {Marshal.GetLastWin32Error()}");
        }

        return _isRegistered;
    }

    public void Unregister()
    {
        if (_isRegistered)
        {
            UnregisterHotKey(_windowHandle, HOTKEY_ID);
            _isRegistered = false;
        }
    }

    public void ProcessMessage(ref Message m)
    {
        if (m.Msg == WM_HOTKEY && m.WParam.ToInt32() == HOTKEY_ID)
        {
            HotkeyPressed?.Invoke();
        }
    }

    public void Dispose()
    {
        Unregister();
    }
}
