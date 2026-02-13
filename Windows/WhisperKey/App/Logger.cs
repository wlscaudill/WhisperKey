using WhisperKeys.Settings;

namespace WhisperKeys.App;

public static class Logger
{
    private static readonly string LogDir = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "WhisperKey");

    private static readonly string LogFile = Path.Combine(LogDir, "whisperkey.log");
    private static readonly object Lock = new();

    public static LogLevel Level = LogLevel.Normal;

    public static void Init()
    {
        Directory.CreateDirectory(LogDir);

        // Truncate if over 1MB
        if (File.Exists(LogFile) && new FileInfo(LogFile).Length > 1_000_000)
        {
            File.WriteAllText(LogFile, "");
        }

        Log("=== WhisperKey started ===");
    }

    public static void Log(string message)
    {
        lock (Lock)
        {
            try
            {
                var line = $"[{DateTime.Now:yyyy-MM-dd HH:mm:ss.fff}] {message}";
                File.AppendAllText(LogFile, line + Environment.NewLine);
                System.Diagnostics.Debug.WriteLine(line);
            }
            catch
            {
                // Don't throw from logging
            }
        }
    }

    public static void Debug(string message)
    {
        if (Level == LogLevel.Debug)
            Log(message);
    }

    public static void Error(string message, Exception? ex = null)
    {
        var text = ex != null ? $"ERROR: {message} - {ex}" : $"ERROR: {message}";
        Log(text);
    }
}
