using WhisperKeys.Settings;

namespace WhisperKeys.Transcription;

public interface ITranscriber : IDisposable
{
    bool IsLoaded { get; }

    event Action<string>? StatusChanged;

    /// <param name="resourcePath">For Whisper: path to the .bin model file.
    /// For Parakeet: path to the extracted bundle directory.</param>
    Task LoadAsync(string resourcePath, AppSettings settings);

    Task<string> TranscribeAsync(float[] audioPcm16kMono);
}
