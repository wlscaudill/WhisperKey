using Whisper.net.LibraryLoader;
using WhisperKeys.App;
using WhisperKeys.Settings;

namespace WhisperKeys.Transcription;

public static class WhisperRuntimeSetup
{
    public static void Configure(ComputeBackend backend)
    {
        RuntimeOptions.RuntimeLibraryOrder = backend switch
        {
            ComputeBackend.Cpu => new List<RuntimeLibrary>
            {
                RuntimeLibrary.Cpu, RuntimeLibrary.CpuNoAvx
            },
            ComputeBackend.Cuda => new List<RuntimeLibrary>
            {
                RuntimeLibrary.Cuda,
                RuntimeLibrary.Cpu, RuntimeLibrary.CpuNoAvx
            },
            ComputeBackend.Vulkan => new List<RuntimeLibrary>
            {
                RuntimeLibrary.Vulkan,
                RuntimeLibrary.Cpu, RuntimeLibrary.CpuNoAvx
            },
            // Auto: CUDA > Vulkan > CPU (default priority)
            _ => new List<RuntimeLibrary>
            {
                RuntimeLibrary.Cuda, RuntimeLibrary.Vulkan,
                RuntimeLibrary.Cpu, RuntimeLibrary.CpuNoAvx
            }
        };

        Logger.Log($"Whisper runtime order set for backend={backend}: " +
            string.Join(", ", RuntimeOptions.RuntimeLibraryOrder));
    }

    public static void LogLoadedRuntime()
    {
        var loaded = RuntimeOptions.LoadedLibrary;
        if (loaded.HasValue)
            Logger.Log($"Whisper runtime loaded: {loaded.Value}");
        else
            Logger.Log("Whisper runtime: not yet loaded");
    }
}
