using WhisperKeys.App;
using WhisperKeys.Settings;

namespace WhisperKeys;

static class Program
{
    private static Mutex? _mutex;

    [STAThread]
    static void Main()
    {
        Logger.Init();

        // Single instance check
        const string mutexName = "WhisperKeys_SingleInstance";
        _mutex = new Mutex(true, mutexName, out bool createdNew);

        if (!createdNew)
        {
            Logger.Log("Another instance already running, exiting.");
            MessageBox.Show("WhisperKeys is already running.", "WhisperKeys",
                MessageBoxButtons.OK, MessageBoxIcon.Information);
            return;
        }

        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);
        Application.SetHighDpiMode(HighDpiMode.SystemAware);

        Application.ThreadException += (_, ex) => Logger.Error("Unhandled UI exception", ex.Exception);
        AppDomain.CurrentDomain.UnhandledException += (_, ex) =>
            Logger.Error("Unhandled domain exception", ex.ExceptionObject as Exception);

        var settings = SettingsManager.Load();
        Logger.Log($"Settings loaded: model={settings.ModelFileName}, mode={settings.Mode}, hotkey={settings.Hotkey}");

        using var app = new TrayApplication(settings);
        Application.Run(app);
        Logger.Log("Application exiting.");
    }
}
