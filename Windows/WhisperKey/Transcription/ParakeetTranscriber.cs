using SherpaOnnx;
using WhisperKeys.App;
using WhisperKeys.Settings;

namespace WhisperKeys.Transcription;

public class ParakeetTranscriber : ITranscriber
{
    private OfflineRecognizer? _recognizer;
    private readonly SemaphoreSlim _semaphore = new(1, 1);
    private bool _isLoaded;
    private bool _disposed;
    private ParakeetFamily _loadedFamily;

    public bool IsLoaded => _isLoaded;

    public event Action<string>? StatusChanged;

    public async Task LoadAsync(string resourcePath, AppSettings settings)
    {
        ObjectDisposedException.ThrowIf(_disposed, this);

        if (!Directory.Exists(resourcePath))
            throw new DirectoryNotFoundException($"Parakeet model bundle not found: {resourcePath}");

        var tokens = Path.Combine(resourcePath, "tokens.txt");
        if (!File.Exists(tokens))
            throw new FileNotFoundException($"Parakeet bundle is missing tokens.txt at {tokens}");

        // Family is inferred from what files are present in the bundle.
        var encoder = ModelManager.GlobOne(resourcePath, "encoder*.onnx");
        var decoder = ModelManager.GlobOne(resourcePath, "decoder*.onnx");
        var joiner  = ModelManager.GlobOne(resourcePath, "joiner*.onnx");
        var ctcModel = ModelManager.GlobOne(resourcePath, "model*.onnx");

        bool isTransducer = encoder != null && decoder != null && joiner != null;
        bool isNeMoCtc = !isTransducer && ctcModel != null;

        if (!isTransducer && !isNeMoCtc)
            throw new InvalidDataException(
                $"Parakeet bundle at {resourcePath} does not contain encoder/decoder/joiner nor a single model*.onnx");

        await _semaphore.WaitAsync();
        try
        {
            _recognizer?.Dispose();
            _recognizer = null;
            _isLoaded = false;

            StatusChanged?.Invoke("Loading model...");
            Logger.Log($"Parakeet: loading bundle at {resourcePath} ({(isTransducer ? "transducer" : "nemo_ctc")})");

            var config = new OfflineRecognizerConfig();
            config.FeatConfig.SampleRate = 16000;
            config.FeatConfig.FeatureDim = 80;

            config.ModelConfig.Tokens = tokens;
            config.ModelConfig.NumThreads = settings.ThreadCount > 0 ? settings.ThreadCount : 4;
            config.ModelConfig.Provider = "cpu";
            config.ModelConfig.Debug = 0;

            if (isTransducer)
            {
                config.ModelConfig.Transducer.Encoder = encoder!;
                config.ModelConfig.Transducer.Decoder = decoder!;
                config.ModelConfig.Transducer.Joiner = joiner!;
                config.ModelConfig.ModelType = "nemo_transducer";
                _loadedFamily = ParakeetFamily.Transducer;
            }
            else
            {
                config.ModelConfig.NeMoCtc.Model = ctcModel!;
                _loadedFamily = ParakeetFamily.NeMoCtc;
            }

            config.DecodingMethod = settings.GreedyDecoding ? "greedy_search" : "modified_beam_search";
            config.MaxActivePaths = 4;

            _recognizer = new OfflineRecognizer(config);
            _isLoaded = true;
            StatusChanged?.Invoke("Model loaded");
        }
        finally
        {
            _semaphore.Release();
        }
    }

    public async Task<string> TranscribeAsync(float[] audioPcm16kMono)
    {
        ObjectDisposedException.ThrowIf(_disposed, this);

        if (!_isLoaded || _recognizer == null)
            throw new InvalidOperationException("Model not loaded. Call LoadAsync first.");

        await _semaphore.WaitAsync();
        try
        {
            StatusChanged?.Invoke("Transcribing...");

            var result = await Task.Run(() =>
            {
                var stream = _recognizer.CreateStream();
                try
                {
                    stream.AcceptWaveform(16000, audioPcm16kMono);
                    _recognizer.Decode(stream);
                    return stream.Result.Text ?? string.Empty;
                }
                finally
                {
                    stream.Dispose();
                }
            });

            StatusChanged?.Invoke("Ready");
            return result.Trim();
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
            _recognizer?.Dispose();
            _recognizer = null;
            _isLoaded = false;
        }
        finally
        {
            _semaphore.Release();
            _semaphore.Dispose();
        }
    }
}
