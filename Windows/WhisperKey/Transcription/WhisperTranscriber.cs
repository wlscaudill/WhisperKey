using Whisper.net;

namespace WhisperKeys.Transcription;

public class WhisperTranscriber : IDisposable
{
    private WhisperFactory? _factory;
    private WhisperProcessor? _processor;
    private readonly SemaphoreSlim _semaphore = new(1, 1);
    private bool _isLoaded;
    private bool _disposed;

    public bool IsLoaded => _isLoaded;

    public event Action<string>? StatusChanged;

    public async Task LoadModelAsync(string modelPath, string language = "en")
    {
        ObjectDisposedException.ThrowIf(_disposed, this);

        await _semaphore.WaitAsync();
        try
        {
            if (!File.Exists(modelPath))
                throw new FileNotFoundException($"Model not found: {modelPath}");

            // Dispose previous model if loaded
            _processor?.Dispose();
            _factory?.Dispose();

            StatusChanged?.Invoke("Loading model...");

            _factory = WhisperFactory.FromPath(modelPath);

            _processor = _factory.CreateBuilder()
                .WithLanguage(language)
                .Build();

            _isLoaded = true;
            StatusChanged?.Invoke("Model loaded");
        }
        finally
        {
            _semaphore.Release();
        }
    }

    public async Task<string> TranscribeAsync(float[] audioData)
    {
        ObjectDisposedException.ThrowIf(_disposed, this);

        if (!_isLoaded || _processor == null)
            throw new InvalidOperationException("Model not loaded. Call LoadModelAsync first.");

        await _semaphore.WaitAsync();
        try
        {
            StatusChanged?.Invoke("Transcribing...");

            var segments = new List<string>();

            await foreach (var segment in _processor.ProcessAsync(audioData))
            {
                var text = segment.Text.Trim();
                if (!string.IsNullOrWhiteSpace(text))
                    segments.Add(text);
            }

            var result = string.Join(" ", segments).Trim();
            StatusChanged?.Invoke("Ready");
            return result;
        }
        finally
        {
            _semaphore.Release();
        }
    }

    public void Dispose()
    {
        if (_disposed) return;
        _disposed = true;

        _semaphore.Wait();
        try
        {
            _processor?.Dispose();
            _factory?.Dispose();
            _processor = null;
            _factory = null;
            _isLoaded = false;
        }
        finally
        {
            _semaphore.Release();
            _semaphore.Dispose();
        }
    }
}
