using System.Runtime.InteropServices;
using System.Text;
using WhisperKeys.App;

namespace WhisperKeys.Input;

/// <summary>
/// Handles sending paste commands to the active window.
///
/// Strategy:
///   - Console/terminal windows and Chromium/Electron apps don't respond
///     to WM_PASTE — use SendInput Ctrl+V instead.
///   - Scintilla controls (Notepad++ etc.) need SCI_PASTE (msg 2179).
///   - All other apps use WM_PASTE via SendMessage.
///
/// Only one method is used per paste to avoid duplicates.
/// </summary>
public static class InputSender
{
    private const uint SCI_PASTE = 2179;

    /// <summary>
    /// Window class names (exact match) that don't respond to WM_PASTE and need simulated Ctrl+V.
    /// </summary>
    private static readonly string[] SendInputClassNames =
    [
        // Console / terminal hosts
        "ConsoleWindowClass",            // Classic cmd.exe / PowerShell console
        "CASCADIA_HOSTING_WINDOW_CLASS", // Windows Terminal
        "PseudoConsoleWindow",           // Pseudo-console windows
        "mintty",                        // Git Bash (mintty)
        "VirtualConsoleClass",           // ConEmu / Cmder

        // Chromium / Electron apps (Signal, Slack, Discord, VS Code, Teams, Chrome, Edge)
        "Chrome_WidgetWin_1",            // Standard Chromium top-level window
        "Chrome_WidgetWin_0",            // Alternate Chromium top-level window
    ];

    /// <summary>
    /// Window class prefixes that don't respond to WM_PASTE and need simulated Ctrl+V.
    /// These have dynamic suffixes (e.g. GUIDs) so we match by prefix.
    /// </summary>
    private static readonly string[] SendInputClassPrefixes =
    [
        "HwndWrapper[",                  // WPF apps (Fork, Visual Studio, etc.)
    ];

    public static void SendPaste()
    {
        // Log foreground window
        var hwnd = GetForegroundWindow();
        var title = GetWindowTitle(hwnd);
        var windowClass = GetWindowClassName(hwnd);
        Logger.Log($"SendPaste: foreground=0x{hwnd:X} class=\"{windowClass}\" \"{title}\"");

        // Verify clipboard has content
        if (Clipboard.ContainsText())
        {
            var clip = Clipboard.GetText();
            Logger.Log($"Clipboard verified: \"{clip[..Math.Min(clip.Length, 50)]}\"");
        }
        else
        {
            Logger.Error("Clipboard does NOT contain text!");
        }

        // Release modifiers unconditionally
        ReleaseModifiers();
        Thread.Sleep(30);

        // Console/terminal and Chromium/Electron windows: use SendInput Ctrl+V (WM_PASTE doesn't work)
        if (NeedsSendInput(windowClass))
        {
            Logger.Log($"SendInput target detected (class={windowClass}), sending Ctrl+V");
            SendCtrlV();
            return;
        }

        // Find the focused control for non-console windows
        var target = GetFocusedControl(hwnd);
        var targetClass = GetWindowClassName(target);
        Logger.Log($"Target control: 0x{target:X} class=\"{targetClass}\"");

        // Scintilla controls (Notepad++ etc.): use SCI_PASTE
        if (targetClass.Contains("Scintilla", StringComparison.OrdinalIgnoreCase))
        {
            Logger.Log("Scintilla detected, sending SCI_PASTE");
            SendMessage(target, SCI_PASTE, IntPtr.Zero, IntPtr.Zero);
            return;
        }

        // All other apps: WM_PASTE
        Logger.Log("Sending WM_PASTE");
        SendMessage(target, WM_PASTE, IntPtr.Zero, IntPtr.Zero);
    }

    private static bool NeedsSendInput(string windowClass)
    {
        foreach (var name in SendInputClassNames)
        {
            if (windowClass.Equals(name, StringComparison.OrdinalIgnoreCase))
                return true;
        }
        foreach (var prefix in SendInputClassPrefixes)
        {
            if (windowClass.StartsWith(prefix, StringComparison.OrdinalIgnoreCase))
                return true;
        }
        return false;
    }

    private static IntPtr GetFocusedControl(IntPtr foregroundWindow)
    {
        var foregroundThread = GetWindowThreadProcessId(foregroundWindow, out _);
        var currentThread = GetCurrentThreadId();

        if (foregroundThread == currentThread)
            return GetFocus();

        if (!AttachThreadInput(currentThread, foregroundThread, true))
        {
            Logger.Log("AttachThreadInput failed, using foreground window");
            return foregroundWindow;
        }

        try
        {
            var focused = GetFocus();
            return focused != IntPtr.Zero ? focused : foregroundWindow;
        }
        finally
        {
            AttachThreadInput(currentThread, foregroundThread, false);
        }
    }

    private static void SendCtrlV()
    {
        var inputs = new INPUT[4];
        inputs[0].type = INPUT_KEYBOARD;
        inputs[0].u.ki.wVk = VK_LCONTROL;
        inputs[1].type = INPUT_KEYBOARD;
        inputs[1].u.ki.wVk = VK_V;
        inputs[2].type = INPUT_KEYBOARD;
        inputs[2].u.ki.wVk = VK_V;
        inputs[2].u.ki.dwFlags = KEYEVENTF_KEYUP;
        inputs[3].type = INPUT_KEYBOARD;
        inputs[3].u.ki.wVk = VK_LCONTROL;
        inputs[3].u.ki.dwFlags = KEYEVENTF_KEYUP;

        var size = Marshal.SizeOf<INPUT>();
        var sent = SendInput((uint)inputs.Length, inputs, size);
        Logger.Log($"SendInput: {sent}/{inputs.Length} (size={size})");
    }

    private static void ReleaseModifiers()
    {
        SendKeyUp(VK_LWIN);
        SendKeyUp(VK_RWIN);
        SendKeyUp(VK_LCONTROL);
        SendKeyUp(VK_RCONTROL);
        SendKeyUp(VK_LMENU);
        SendKeyUp(VK_RMENU);
        SendKeyUp(VK_LSHIFT);
        SendKeyUp(VK_RSHIFT);
    }

    private static void SendKeyUp(ushort vk)
    {
        var input = new INPUT
        {
            type = INPUT_KEYBOARD,
            u = { ki = new KEYBDINPUT { wVk = vk, dwFlags = KEYEVENTF_KEYUP } }
        };
        SendInput(1, [input], Marshal.SizeOf<INPUT>());
    }

    private static string GetWindowTitle(IntPtr hwnd)
    {
        var sb = new StringBuilder(256);
        GetWindowText(hwnd, sb, sb.Capacity);
        return sb.ToString();
    }

    private static string GetWindowClassName(IntPtr hwnd)
    {
        var sb = new StringBuilder(256);
        GetClassName(hwnd, sb, sb.Capacity);
        return sb.ToString();
    }

    #region Win32 P/Invoke

    private const uint INPUT_KEYBOARD = 1;
    private const ushort VK_LCONTROL = 0xA2;
    private const ushort VK_RCONTROL = 0xA3;
    private const ushort VK_LMENU = 0xA4;
    private const ushort VK_RMENU = 0xA5;
    private const ushort VK_LSHIFT = 0xA0;
    private const ushort VK_RSHIFT = 0xA1;
    private const ushort VK_LWIN = 0x5B;
    private const ushort VK_RWIN = 0x5C;
    private const ushort VK_V = 0x56;
    private const uint KEYEVENTF_KEYUP = 0x0002;
    private const uint WM_PASTE = 0x0302;

    [DllImport("user32.dll")]
    private static extern short GetAsyncKeyState(int vKey);

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);

    [DllImport("user32.dll")]
    private static extern IntPtr GetForegroundWindow();

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern int GetWindowText(IntPtr hWnd, StringBuilder text, int count);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern int GetClassName(IntPtr hWnd, StringBuilder lpClassName, int nMaxCount);

    [DllImport("user32.dll")]
    private static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint processId);

    [DllImport("kernel32.dll")]
    private static extern uint GetCurrentThreadId();

    [DllImport("user32.dll")]
    private static extern bool AttachThreadInput(uint idAttach, uint idAttachTo, bool fAttach);

    [DllImport("user32.dll")]
    private static extern IntPtr GetFocus();

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern IntPtr SendMessage(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam);

    [StructLayout(LayoutKind.Sequential)]
    private struct INPUT
    {
        public uint type;
        public INPUTUNION u;
    }

    [StructLayout(LayoutKind.Explicit)]
    private struct INPUTUNION
    {
        [FieldOffset(0)] public MOUSEINPUT mi;
        [FieldOffset(0)] public KEYBDINPUT ki;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct MOUSEINPUT
    {
        public int dx;
        public int dy;
        public uint mouseData;
        public uint dwFlags;
        public uint time;
        public IntPtr dwExtraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct KEYBDINPUT
    {
        public ushort wVk;
        public ushort wScan;
        public uint dwFlags;
        public uint time;
        public IntPtr dwExtraInfo;
    }

    #endregion
}
