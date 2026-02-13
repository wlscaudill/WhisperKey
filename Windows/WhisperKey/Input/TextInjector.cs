using WhisperKeys.App;
using WhisperKeys.Settings;

namespace WhisperKeys.Input;

public class TextInjector
{
    private readonly AppSettings _settings;

    public TextInjector(AppSettings settings)
    {
        _settings = settings;
    }

    public void InjectText(string text)
    {
        if (string.IsNullOrWhiteSpace(text))
            return;

        IDataObject? previousClipboard = null;

        try
        {
            if (_settings.RestoreClipboard)
            {
                previousClipboard = Clipboard.GetDataObject();
            }

            Clipboard.SetText(text);
            Thread.Sleep(50); // Allow clipboard to settle

            InputSender.SendPaste();

            if (_settings.RestoreClipboard && previousClipboard != null)
            {
                // Must wait long enough for the target app to process the
                // queued Ctrl+V before we restore the clipboard
                Thread.Sleep(500);
                Clipboard.SetDataObject(previousClipboard, true);
            }
        }
        catch (Exception ex)
        {
            Logger.Error("Text injection failed", ex);
        }
    }
}
