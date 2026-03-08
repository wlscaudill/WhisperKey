using WhisperKeys.App;
using WhisperKeys.Settings;

namespace WhisperKeys.Input;

public class TextInjector
{
    public AppSettings Settings { get; set; }

    public TextInjector(AppSettings settings)
    {
        Settings = settings;
    }

    public void InjectText(string text)
    {
        if (string.IsNullOrWhiteSpace(text))
            return;

        DataObject? savedClipboard = null;

        try
        {
            if (Settings.RestoreClipboard)
            {
                savedClipboard = DeepCopyClipboard();
            }

            Clipboard.SetText(text);
            Thread.Sleep(50); // Allow clipboard to settle

            InputSender.SendPaste();

            if (Settings.RestoreClipboard && savedClipboard != null)
            {
                // Must wait long enough for the target app to process the
                // queued Ctrl+V before we restore the clipboard
                Thread.Sleep(500);
                Clipboard.SetDataObject(savedClipboard, true);
            }
        }
        catch (Exception ex)
        {
            Logger.Error("Text injection failed", ex);
        }
    }

    /// <summary>
    /// Deep-copies all clipboard data into a new DataObject.
    /// Clipboard.GetDataObject() returns a COM proxy that becomes stale when
    /// the clipboard changes — this extracts the actual data so it survives.
    /// </summary>
    private static DataObject? DeepCopyClipboard()
    {
        IDataObject? source;
        try
        {
            source = Clipboard.GetDataObject();
        }
        catch (Exception ex)
        {
            Logger.Error("Failed to read clipboard for save", ex);
            return null;
        }

        if (source == null)
            return null;

        var copy = new DataObject();
        bool hasAnyData = false;

        foreach (var format in source.GetFormats())
        {
            try
            {
                var data = source.GetData(format);
                if (data != null)
                {
                    copy.SetData(format, data);
                    hasAnyData = true;
                }
            }
            catch
            {
                // Some formats (e.g. delayed-render or app-specific) may fail
                // to retrieve — skip them rather than losing everything
            }
        }

        return hasAnyData ? copy : null;
    }
}
