using WhisperKeys.Settings;

namespace WhisperKeys.Transcription;

public class ModelInfo
{
    public required string FileName { get; init; }
    public required string DisplayName { get; init; }
    public required string Size { get; init; }
    public required string Url { get; init; }
    public bool IsDownloaded { get; set; }
}

public class ModelManager
{
    private static readonly string BaseUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/";

    public static readonly ModelInfo[] AvailableModels =
    [
        // English-only models (faster, recommended for English)
        new() { FileName = "ggml-tiny.en.bin",   DisplayName = "Tiny (English)",    Size = "~75 MB",  Url = BaseUrl + "ggml-tiny.en.bin" },
        new() { FileName = "ggml-base.en.bin",   DisplayName = "Base (English)",    Size = "~150 MB", Url = BaseUrl + "ggml-base.en.bin" },
        new() { FileName = "ggml-small.en.bin",  DisplayName = "Small (English)",   Size = "~500 MB", Url = BaseUrl + "ggml-small.en.bin" },
        new() { FileName = "ggml-medium.en.bin", DisplayName = "Medium (English)",  Size = "~1.5 GB", Url = BaseUrl + "ggml-medium.en.bin" },

        // Multilingual models
        new() { FileName = "ggml-tiny.bin",      DisplayName = "Tiny (Multi)",      Size = "~75 MB",  Url = BaseUrl + "ggml-tiny.bin" },
        new() { FileName = "ggml-base.bin",      DisplayName = "Base (Multi)",      Size = "~150 MB", Url = BaseUrl + "ggml-base.bin" },
        new() { FileName = "ggml-small.bin",     DisplayName = "Small (Multi)",     Size = "~500 MB", Url = BaseUrl + "ggml-small.bin" },
        new() { FileName = "ggml-medium.bin",    DisplayName = "Medium (Multi)",    Size = "~1.5 GB", Url = BaseUrl + "ggml-medium.bin" },
        new() { FileName = "ggml-large-v3.bin",  DisplayName = "Large v3 (Multi)",  Size = "~3.1 GB", Url = BaseUrl + "ggml-large-v3.bin" },
        new() { FileName = "ggml-large-v3-turbo.bin", DisplayName = "Large v3 Turbo (Multi)", Size = "~1.6 GB", Url = BaseUrl + "ggml-large-v3-turbo.bin" },
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

    public List<ModelInfo> GetModelsWithStatus()
    {
        var models = new List<ModelInfo>();
        foreach (var model in AvailableModels)
        {
            models.Add(new ModelInfo
            {
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
