using SharpCompress.Readers;
using WhisperKeys.App;
using WhisperKeys.Settings;

namespace WhisperKeys.Transcription;

public class ModelManager
{
    private static readonly string WhisperBaseUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/";
    private static readonly string ParakeetBaseUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/";

    // Languages supported by sherpa-onnx parakeet-tdt-0.6b-v3 multilingual bundle.
    private static readonly string[] ParakeetV3Languages =
    [
        "bg", "hr", "cs", "da", "nl", "en", "et", "fi", "fr", "de", "el", "hu",
        "it", "lv", "lt", "mt", "pl", "pt", "ro", "sk", "sl", "es", "sv", "ru", "uk"
    ];

    public static readonly ModelDescriptor[] AvailableModels =
    [
        // English-only Whisper models (faster, recommended for English)
        new() { Engine = TranscriptionEngine.Whisper, FileName = "ggml-tiny.en.bin",         DisplayName = "Whisper Tiny (English)",         Size = "~75 MB",  Url = WhisperBaseUrl + "ggml-tiny.en.bin",         Languages = ["en"] },
        new() { Engine = TranscriptionEngine.Whisper, FileName = "ggml-base.en.bin",         DisplayName = "Whisper Base (English)",         Size = "~150 MB", Url = WhisperBaseUrl + "ggml-base.en.bin",         Languages = ["en"] },
        new() { Engine = TranscriptionEngine.Whisper, FileName = "ggml-small.en.bin",        DisplayName = "Whisper Small (English)",        Size = "~500 MB", Url = WhisperBaseUrl + "ggml-small.en.bin",        Languages = ["en"] },
        new() { Engine = TranscriptionEngine.Whisper, FileName = "ggml-medium.en.bin",       DisplayName = "Whisper Medium (English)",       Size = "~1.5 GB", Url = WhisperBaseUrl + "ggml-medium.en.bin",       Languages = ["en"] },

        // Multilingual Whisper models
        new() { Engine = TranscriptionEngine.Whisper, FileName = "ggml-tiny.bin",            DisplayName = "Whisper Tiny (Multi)",           Size = "~75 MB",  Url = WhisperBaseUrl + "ggml-tiny.bin",            Languages = ["en", "auto"] },
        new() { Engine = TranscriptionEngine.Whisper, FileName = "ggml-base.bin",            DisplayName = "Whisper Base (Multi)",           Size = "~150 MB", Url = WhisperBaseUrl + "ggml-base.bin",            Languages = ["en", "auto"] },
        new() { Engine = TranscriptionEngine.Whisper, FileName = "ggml-small.bin",           DisplayName = "Whisper Small (Multi)",          Size = "~500 MB", Url = WhisperBaseUrl + "ggml-small.bin",           Languages = ["en", "auto"] },
        new() { Engine = TranscriptionEngine.Whisper, FileName = "ggml-medium.bin",          DisplayName = "Whisper Medium (Multi)",         Size = "~1.5 GB", Url = WhisperBaseUrl + "ggml-medium.bin",          Languages = ["en", "auto"] },
        new() { Engine = TranscriptionEngine.Whisper, FileName = "ggml-large-v3.bin",        DisplayName = "Whisper Large v3 (Multi)",       Size = "~3.1 GB", Url = WhisperBaseUrl + "ggml-large-v3.bin",        Languages = ["en", "auto"] },
        new() { Engine = TranscriptionEngine.Whisper, FileName = "ggml-large-v3-turbo.bin",  DisplayName = "Whisper Large v3 Turbo (Multi)", Size = "~1.6 GB", Url = WhisperBaseUrl + "ggml-large-v3-turbo.bin",  Languages = ["en", "auto"] },

        // Parakeet (sherpa-onnx) — CPU-only via the prebuilt NuGet package
        new()
        {
            Engine = TranscriptionEngine.Parakeet,
            FileName = "sherpa-onnx-nemo-parakeet_tdt_ctc_110m-en-36000-int8",
            DisplayName = "Parakeet TDT-CTC 110M (English, int8)",
            Size = "~126 MB",
            Url = ParakeetBaseUrl + "sherpa-onnx-nemo-parakeet_tdt_ctc_110m-en-36000-int8.tar.bz2",
            Family = ParakeetFamily.NeMoCtc,
            Languages = ["en"]
        },
        new()
        {
            Engine = TranscriptionEngine.Parakeet,
            FileName = "sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8",
            DisplayName = "Parakeet TDT 0.6B v2 (English, int8)",
            Size = "~630 MB",
            Url = ParakeetBaseUrl + "sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8.tar.bz2",
            Family = ParakeetFamily.Transducer,
            Languages = ["en"]
        },
        new()
        {
            Engine = TranscriptionEngine.Parakeet,
            FileName = "sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8",
            DisplayName = "Parakeet TDT 0.6B v3 (Multilingual, int8)",
            Size = "~640 MB",
            Url = ParakeetBaseUrl + "sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8.tar.bz2",
            Family = ParakeetFamily.Transducer,
            Languages = ParakeetV3Languages
        },
    ];

    private readonly string _modelsDir;

    public ModelManager()
    {
        _modelsDir = SettingsManager.GetModelsDirectory();
    }

    public ModelDescriptor? FindModel(TranscriptionEngine engine, string fileName) =>
        AvailableModels.FirstOrDefault(m => m.Engine == engine && m.FileName == fileName);

    public string GetModelPath(ModelDescriptor model)
    {
        return Path.Combine(_modelsDir, model.FileName);
    }

    public bool IsModelDownloaded(ModelDescriptor model)
    {
        var path = GetModelPath(model);

        if (model.Engine == TranscriptionEngine.Whisper)
        {
            return File.Exists(path) && new FileInfo(path).Length > 0;
        }

        if (!Directory.Exists(path)) return false;

        // Bundle must contain tokens.txt plus the engine-specific files.
        if (!File.Exists(Path.Combine(path, "tokens.txt"))) return false;

        return model.Family switch
        {
            ParakeetFamily.Transducer =>
                GlobOne(path, "encoder*.onnx") != null &&
                GlobOne(path, "decoder*.onnx") != null &&
                GlobOne(path, "joiner*.onnx") != null,
            ParakeetFamily.NeMoCtc =>
                GlobOne(path, "model*.onnx") != null,
            _ => false
        };
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
                Family = model.Family,
                Languages = model.Languages,
                IsDownloaded = IsModelDownloaded(model)
            });
        }
        return models;
    }

    public Task DownloadModelAsync(ModelDescriptor model, IProgress<double>? progress = null,
        IProgress<string>? status = null, CancellationToken cancellationToken = default)
    {
        return model.Engine == TranscriptionEngine.Whisper
            ? DownloadWhisperAsync(model, progress, cancellationToken)
            : DownloadParakeetAsync(model, progress, status, cancellationToken);
    }

    private async Task DownloadWhisperAsync(ModelDescriptor model, IProgress<double>? progress,
        CancellationToken cancellationToken)
    {
        var targetPath = GetModelPath(model);
        var tempPath = targetPath + ".tmp";

        using var httpClient = new HttpClient();
        httpClient.Timeout = TimeSpan.FromMinutes(30);

        using var response = await httpClient.GetAsync(model.Url, HttpCompletionOption.ResponseHeadersRead, cancellationToken);
        response.EnsureSuccessStatusCode();

        var totalBytes = response.Content.Headers.ContentLength ?? -1;

        await using (var contentStream = await response.Content.ReadAsStreamAsync(cancellationToken))
        await using (var fileStream = new FileStream(tempPath, FileMode.Create, FileAccess.Write, FileShare.None, 8192, true))
        {
            var buffer = new byte[81920];
            long totalRead = 0;
            int bytesRead;

            while ((bytesRead = await contentStream.ReadAsync(buffer, cancellationToken)) > 0)
            {
                await fileStream.WriteAsync(buffer.AsMemory(0, bytesRead), cancellationToken);
                totalRead += bytesRead;
                if (totalBytes > 0) progress?.Report((double)totalRead / totalBytes);
            }
        }

        if (File.Exists(targetPath)) File.Delete(targetPath);
        File.Move(tempPath, targetPath);
        progress?.Report(1.0);
    }

    private async Task DownloadParakeetAsync(ModelDescriptor model, IProgress<double>? progress,
        IProgress<string>? status, CancellationToken cancellationToken)
    {
        var bundleDir = GetModelPath(model);
        var tempArchive = bundleDir + ".tar.bz2.tmp";
        var tempExtractDir = bundleDir + ".extract.tmp";

        // Clean any stale temp state from a previous failed run.
        if (File.Exists(tempArchive)) File.Delete(tempArchive);
        if (Directory.Exists(tempExtractDir)) Directory.Delete(tempExtractDir, recursive: true);

        try
        {
            status?.Report("Downloading…");

            using var httpClient = new HttpClient();
            httpClient.Timeout = TimeSpan.FromMinutes(30);

            using (var response = await httpClient.GetAsync(model.Url, HttpCompletionOption.ResponseHeadersRead, cancellationToken))
            {
                response.EnsureSuccessStatusCode();

                var totalBytes = response.Content.Headers.ContentLength ?? -1;

                await using var contentStream = await response.Content.ReadAsStreamAsync(cancellationToken);
                await using var fileStream = new FileStream(tempArchive, FileMode.Create, FileAccess.Write, FileShare.None, 81920, true);

                var buffer = new byte[81920];
                long totalRead = 0;
                int bytesRead;

                while ((bytesRead = await contentStream.ReadAsync(buffer, cancellationToken)) > 0)
                {
                    await fileStream.WriteAsync(buffer.AsMemory(0, bytesRead), cancellationToken);
                    totalRead += bytesRead;
                    // Reserve 0–90% of the bar for download, 90–100% for extraction.
                    if (totalBytes > 0) progress?.Report(0.9 * totalRead / totalBytes);
                }
            }

            status?.Report("Extracting…");

            Directory.CreateDirectory(tempExtractDir);
            await Task.Run(() => ExtractTarBz2(tempArchive, tempExtractDir, cancellationToken), cancellationToken);

            // sherpa-onnx archives contain a single top-level directory matching the bundle name.
            // Resolve which directory actually holds the model files (tokens.txt is required for every variant).
            string? sourceDir = null;
            if (File.Exists(Path.Combine(tempExtractDir, "tokens.txt")))
            {
                sourceDir = tempExtractDir;
            }
            else
            {
                foreach (var sub in Directory.EnumerateDirectories(tempExtractDir))
                {
                    if (File.Exists(Path.Combine(sub, "tokens.txt")))
                    {
                        sourceDir = sub;
                        break;
                    }
                }
            }

            if (sourceDir == null)
                throw new InvalidDataException("Extracted archive does not contain a tokens.txt — bundle layout unexpected.");

            // Replace any existing bundle dir atomically-ish.
            if (Directory.Exists(bundleDir)) Directory.Delete(bundleDir, recursive: true);
            Directory.Move(sourceDir, bundleDir);

            progress?.Report(1.0);
            status?.Report("Ready");
        }
        finally
        {
            try { if (File.Exists(tempArchive)) File.Delete(tempArchive); } catch (Exception ex) { Logger.Debug($"Cleanup of temp archive failed: {ex.Message}"); }
            try { if (Directory.Exists(tempExtractDir)) Directory.Delete(tempExtractDir, recursive: true); } catch (Exception ex) { Logger.Debug($"Cleanup of temp extract dir failed: {ex.Message}"); }
        }
    }

    private static void ExtractTarBz2(string archivePath, string destDir, CancellationToken cancellationToken)
    {
        using var fileStream = File.OpenRead(archivePath);
        using var reader = ReaderFactory.Open(fileStream);
        while (reader.MoveToNextEntry())
        {
            cancellationToken.ThrowIfCancellationRequested();
            if (reader.Entry.IsDirectory) continue;

            var entryKey = reader.Entry.Key;
            if (string.IsNullOrEmpty(entryKey)) continue;

            var safeRelative = entryKey.Replace('\\', '/').TrimStart('/');
            var fullPath = Path.GetFullPath(Path.Combine(destDir, safeRelative));
            if (!fullPath.StartsWith(Path.GetFullPath(destDir), StringComparison.Ordinal))
                throw new InvalidDataException($"Archive entry escapes destination: {entryKey}");

            Directory.CreateDirectory(Path.GetDirectoryName(fullPath)!);
            using var entryStream = reader.OpenEntryStream();
            using var output = File.Create(fullPath);
            entryStream.CopyTo(output);
        }
    }

    public void DeleteModel(ModelDescriptor model)
    {
        var path = GetModelPath(model);

        if (model.Engine == TranscriptionEngine.Whisper)
        {
            if (File.Exists(path)) File.Delete(path);
        }
        else
        {
            if (Directory.Exists(path)) Directory.Delete(path, recursive: true);
        }
    }

    internal static string? GlobOne(string dir, string searchPattern)
    {
        return Directory.EnumerateFiles(dir, searchPattern, SearchOption.TopDirectoryOnly).FirstOrDefault();
    }
}
