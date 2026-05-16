using WhisperKeys.Settings;

namespace WhisperKeys.Transcription;

public class ModelManager
{
    private static readonly string WhisperBaseUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/";

    public static readonly ModelDescriptor[] AvailableModels =
    [
        // English-only Whisper models (faster, recommended for English)
        new() { Engine = TranscriptionEngine.Whisper, FileName = "ggml-tiny.en.bin",         DisplayName = "Whisper Tiny (English)",         Size = "~75 MB",  Url = WhisperBaseUrl + "ggml-tiny.en.bin" },
        new() { Engine = TranscriptionEngine.Whisper, FileName = "ggml-base.en.bin",         DisplayName = "Whisper Base (English)",         Size = "~150 MB", Url = WhisperBaseUrl + "ggml-base.en.bin" },
        new() { Engine = TranscriptionEngine.Whisper, FileName = "ggml-small.en.bin",        DisplayName = "Whisper Small (English)",        Size = "~500 MB", Url = WhisperBaseUrl + "ggml-small.en.bin" },
        new() { Engine = TranscriptionEngine.Whisper, FileName = "ggml-medium.en.bin",       DisplayName = "Whisper Medium (English)",       Size = "~1.5 GB", Url = WhisperBaseUrl + "ggml-medium.en.bin" },

        // Multilingual Whisper models
        new() { Engine = TranscriptionEngine.Whisper, FileName = "ggml-tiny.bin",            DisplayName = "Whisper Tiny (Multi)",           Size = "~75 MB",  Url = WhisperBaseUrl + "ggml-tiny.bin" },
        new() { Engine = TranscriptionEngine.Whisper, FileName = "ggml-base.bin",            DisplayName = "Whisper Base (Multi)",           Size = "~150 MB", Url = WhisperBaseUrl + "ggml-base.bin" },
        new() { Engine = TranscriptionEngine.Whisper, FileName = "ggml-small.bin",           DisplayName = "Whisper Small (Multi)",          Size = "~500 MB", Url = WhisperBaseUrl + "ggml-small.bin" },
        new() { Engine = TranscriptionEngine.Whisper, FileName = "ggml-medium.bin",          DisplayName = "Whisper Medium (Multi)",         Size = "~1.5 GB", Url = WhisperBaseUrl + "ggml-medium.bin" },
        new() { Engine = TranscriptionEngine.Whisper, FileName = "ggml-large-v3.bin",        DisplayName = "Whisper Large v3 (Multi)",       Size = "~3.1 GB", Url = WhisperBaseUrl + "ggml-large-v3.bin" },
        new() { Engine = TranscriptionEngine.Whisper, FileName = "ggml-large-v3-turbo.bin",  DisplayName = "Whisper Large v3 Turbo (Multi)", Size = "~1.6 GB", Url = WhisperBaseUrl + "ggml-large-v3-turbo.bin" },
    ];

    private readonly string _modelsDir;

    public ModelManager()
    {
        _modelsDir = SettingsManager.GetModelsDirectory();
    }

    public string GetModelPath(string fileName)
    {
        return Path.Combine(_modelsDir, fileName);
    }

    public bool IsModelDownloaded(string fileName)
    {
        var path = GetModelPath(fileName);
        return File.Exists(path) && new FileInfo(path).Length > 0;
    }

    public List<ModelDescriptor> GetModelsWithStatus()
    {
        var models = new List<ModelDescriptor>();
        foreach (var model in AvailableModels)
        {
            models.Add(new ModelDescriptor
            {
                Engine = model.Engine,
                FileName = model.FileName,
                DisplayName = model.DisplayName,
                Size = model.Size,
                Url = model.Url,
                IsDownloaded = IsModelDownloaded(model.FileName)
            });
        }
        return models;
    }

    public async Task DownloadModelAsync(string fileName, IProgress<double>? progress = null, CancellationToken cancellationToken = default)
    {
        var modelInfo = AvailableModels.FirstOrDefault(m => m.FileName == fileName)
            ?? throw new ArgumentException($"Unknown model: {fileName}");

        var targetPath = GetModelPath(fileName);
        var tempPath = targetPath + ".tmp";

        using var httpClient = new HttpClient();
        httpClient.Timeout = TimeSpan.FromMinutes(30);

        using var response = await httpClient.GetAsync(modelInfo.Url, HttpCompletionOption.ResponseHeadersRead, cancellationToken);
        response.EnsureSuccessStatusCode();

        var totalBytes = response.Content.Headers.ContentLength ?? -1;

        await using var contentStream = await response.Content.ReadAsStreamAsync(cancellationToken);
        await using var fileStream = new FileStream(tempPath, FileMode.Create, FileAccess.Write, FileShare.None, 8192, true);

        var buffer = new byte[81920];
        long totalRead = 0;
        int bytesRead;

        while ((bytesRead = await contentStream.ReadAsync(buffer, cancellationToken)) > 0)
        {
            await fileStream.WriteAsync(buffer.AsMemory(0, bytesRead), cancellationToken);
            totalRead += bytesRead;

            if (totalBytes > 0)
            {
                progress?.Report((double)totalRead / totalBytes);
            }
        }

        fileStream.Close();

        // Move temp file to final location
        if (File.Exists(targetPath))
            File.Delete(targetPath);
        File.Move(tempPath, targetPath);

        progress?.Report(1.0);
    }

    public void DeleteModel(string fileName)
    {
        var path = GetModelPath(fileName);
        if (File.Exists(path))
            File.Delete(path);
    }
}
