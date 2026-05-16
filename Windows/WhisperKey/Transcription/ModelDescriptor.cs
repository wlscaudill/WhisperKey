namespace WhisperKeys.Transcription;

public enum ParakeetFamily
{
    Transducer,
    NeMoCtc
}

public class ModelDescriptor
{
    public TranscriptionEngine Engine { get; init; } = TranscriptionEngine.Whisper;

    /// <summary>
    /// Identifier on disk. For Whisper this is the .bin file name (e.g. "ggml-base.en.bin").
    /// For Parakeet this is the extracted bundle directory name (e.g. "sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8").
    /// </summary>
    public required string FileName { get; init; }

    public required string DisplayName { get; init; }
    public required string Size { get; init; }
    public required string Url { get; init; }
    public bool IsDownloaded { get; set; }

    /// <summary>
    /// Parakeet only: which sherpa-onnx model family this bundle uses. Null for Whisper.
    /// </summary>
    public ParakeetFamily? Family { get; init; }

    /// <summary>
    /// Languages this model can transcribe. Drives the language picker in Settings.
    /// "auto" means the engine itself does language detection (Whisper multilingual).
    /// </summary>
    public string[] Languages { get; init; } = ["en"];
}
