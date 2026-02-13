using System.Runtime.InteropServices;
using WhisperKeys.Settings;

namespace WhisperKeys.App;

public class HotkeyManager : IDisposable
{
    private const int HOTKEY_ID = 1;
    private const int WM_HOTKEY = 0x0312;

    [DllImport("user32.dll", SetLastError = true)]
    private static extern bool RegisterHotKey(IntPtr hWnd, int id, uint fsModifiers, uint vk);

    [DllImport("user32.dll", SetLastError = true)]
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

        // Try with MOD_NOREPEAT first
        _isRegistered = RegisterHotKey(
            _windowHandle,
            HOTKEY_ID,
            hotkey.GetModifiers(),
            (uint)hotkey.Key);

        if (!_isRegistered)
        {
            var error = Marshal.GetLastWin32Error();
            Logger.Log($"RegisterHotKey failed for {hotkey} with MOD_NOREPEAT (error {error}), retrying without MOD_NOREPEAT");

            // Fallback: try without MOD_NOREPEAT for older Win10 builds
            _isRegistered = RegisterHotKey(
                _windowHandle,
                HOTKEY_ID,
                hotkey.GetModifiersCompat(),
                (uint)hotkey.Key);

            if (!_isRegistered)
            {
                error = Marshal.GetLastWin32Error();
                Logger.Error($"RegisterHotKey failed for {hotkey} without MOD_NOREPEAT (error {error}). " +
                    $"1405=hotkey already registered, 87=invalid parameter");
            }
            else
            {
                Logger.Log($"RegisterHotKey succeeded for {hotkey} without MOD_NOREPEAT");
            }
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
