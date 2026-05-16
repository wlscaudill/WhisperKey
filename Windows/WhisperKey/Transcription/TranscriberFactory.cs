namespace WhisperKeys.Transcription;

public static class TranscriberFactory
{
    public static ITranscriber Create(TranscriptionEngine engine) => engine switch
    {
        TranscriptionEngine.Whisper => new WhisperTranscriber(),
        TranscriptionEngine.Parakeet => new ParakeetTranscriber(),
        _ => throw new ArgumentOutOfRangeException(nameof(engine), engine, "Unknown transcription engine")
    };
}
