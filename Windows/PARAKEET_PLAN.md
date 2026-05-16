# Parakeet Support for WhisperKey (Windows)

Status: **Phase 0 complete** — investigation done, decisions locked in, no code
written yet. See "Phase 0 findings" below.

## Goal

Add NVIDIA Parakeet as a second transcription engine alongside the existing
Whisper.net pipeline. Users pick the engine (and a specific model) from the
Settings → Model tab. Parakeet should run fully local, on Windows x64, with no
Python dependency.

## Why Parakeet

- Parakeet-TDT-0.6B is currently top-of-leaderboard on Open ASR for English at
  ~600M parameters, with a real-time factor far better than Whisper large.
- The 110M `parakeet-tdt_ctc` variant is small enough to be competitive with
  `ggml-base.en` on CPU while being noticeably more accurate.
- Several users have asked for non-Whisper options; this also future-proofs the
  app for swapping engines (NeMo Canary, etc.) without another rewrite.

## Runtime choice

| Option | Pros | Cons | Verdict |
|---|---|---|---|
| **sherpa-onnx** (C# NuGet `org.k2fsa.sherpa.onnx`) | First-class Parakeet support, prebuilt ONNX bundles on HF, single-file native dlls, cross-platform, MIT, actively maintained by k2-fsa | Adds ~30 MB native dependencies; tokenizer is bundled per-model | **Chosen.** Lowest friction for C#. |
| Microsoft.ML.OnnxRuntime + custom decoder | Smallest dependency surface | Have to write TDT/RNNT beam-search decoder ourselves; Parakeet's tokens.txt + features pipeline is non-trivial | Reject. |
| Python NeMo sidecar | Reference implementation, easy GPU | Ships Python with the installer or requires user install. Kills the "single .exe" story | Reject. |
| ONNX exported from NeMo, run by us | Full control | Same decoder problem as option 2, plus we'd have to publish/host our own exports | Reject. |

Decision: **sherpa-onnx via NuGet, CPU only for v1.**

The pre-built `org.k2fsa.sherpa.onnx` NuGet package is **CPU-only on every
platform**. GPU acceleration (CUDA or DirectML) requires building sherpa-onnx
from source against a GPU-enabled ONNX Runtime — there is no GPU NuGet
artifact. (Confirmed from issues #1044, #1313, and the deepwiki GPU support
page.) Shipping custom-built GPU binaries would bloat the installer by
hundreds of MB and add a build pipeline we don't want to own.

This is acceptable because:
- The 110M `parakeet-tdt_ctc` (CTC, single ONNX file, ~126 MB) is fast on CPU
  and is the headline new option.
- The 0.6B TDT int8 variant is ~640 MB and still real-time on a modern CPU.
- Whisper users who want GPU keep using the existing Whisper.net CUDA/Vulkan
  runtimes we already ship — nothing regresses.

GPU support is tracked as a future enhancement, not v1 scope.

## Models to ship in the picker

All hosted on the **sherpa-onnx GitHub releases page** (tag `asr-models`),
distributed as `.tar.bz2` bundles. URLs are stable.

| Bundle | Engine config | Bundle size | Languages |
|---|---|---|---|
| `sherpa-onnx-nemo-parakeet_tdt_ctc_110m-en-36000-int8.tar.bz2` | NeMoCtc (single `model.int8.onnx`) | ~126 MB | English |
| `sherpa-onnx-nemo-parakeet-tdt-0.6b-v2.tar.bz2` | Transducer fp32 (`encoder.onnx`, `decoder.onnx`, `joiner.onnx`) | ~2.4 GB | English |
| `sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8.tar.bz2` | Transducer int8 (`encoder.int8.onnx`, etc.) | ~630 MB | English |
| `sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8.tar.bz2` | Transducer int8 | ~640 MB | 25 EU languages |

Base URL: `https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/<bundle>`

Important: **filenames inside the bundles differ between int8/fp32 variants**
(e.g. `encoder.int8.onnx` vs `encoder.onnx`). `ModelManager` must glob
`encoder*.onnx`, `decoder*.onnx`, `joiner*.onnx` to discover the right paths
rather than hard-coding names. The 110M CTC bundle ships a single
`model.int8.onnx` and is configured via `NeMoCtc.Model`, not `Transducer.*`.

v1 ship list: 110M CTC + 0.6B v2 int8 (the two best price/perf points).
v2 v3 multilingual and v2 fp32 added in Phase 4. Skip the 2.4 GB fp32 unless
users specifically ask — int8 is within 0.5% WER of fp32 in published results.

## Architecture changes

### 1. Introduce `ITranscriber`

Extract the surface `WhisperTranscriber` already exposes:

```csharp
public interface ITranscriber : IDisposable
{
    bool IsLoaded { get; }
    event Action<string>? StatusChanged;
    Task LoadModelAsync(ModelDescriptor model, AppSettings settings);
    Task<string> TranscribeAsync(float[] audioPcm16kMono);
}
```

`WhisperTranscriber` implements it (small refactor — its current `LoadModelAsync`
signature folds into `ModelDescriptor` + `AppSettings`). Add
`ParakeetTranscriber` implementing the same.

### 2. Model identity becomes `(Engine, FileName/BundleName)`

Today settings store a bare `ModelFileName` string and the Whisper code assumes
it's a `ggml-*.bin`. Generalize without breaking existing settings.json files:

```csharp
public enum TranscriptionEngine { Whisper, Parakeet }

public class ModelDescriptor
{
    public TranscriptionEngine Engine { get; init; }
    public required string FileName { get; init; }   // file name for Whisper, bundle dir name for Parakeet
    public required string DisplayName { get; init; }
    public required string Size { get; init; }
    public required string Url { get; init; }
    public string[] Languages { get; init; } = ["en"];
}
```

Settings change (revised for less migration risk):
- Keep `AppSettings.ModelFileName` (it remains the identifier — for Parakeet
  it's the bundle directory name). No rename, no JSON migration needed.
- Add `AppSettings.Engine = TranscriptionEngine.Whisper` (default Whisper, so
  existing settings.json files load cleanly without changes).
- All lookups become `ModelManager.AvailableModels.First(m => m.Engine == s.Engine && m.FileName == s.ModelFileName)`.

### 3. `ModelManager` becomes engine-aware

- `AvailableModels` becomes a flat list of `ModelDescriptor` covering both
  engines. UI groups by `Engine`.
- `GetModelPath` returns either a file path (Whisper) or a directory path
  (Parakeet bundle root).
- `IsModelDownloaded` checks file existence (Whisper) **or** presence of all
  expected files inside the bundle dir (Parakeet: `encoder.onnx`, `decoder.onnx`,
  `joiner.onnx`, `tokens.txt`).
- `DownloadModelAsync` for Parakeet must:
  1. Stream the `.tar.bz2` to a temp file with progress.
  2. Extract via `SharpZipLib` or `SharpCompress` (NuGet) into the target dir.
  3. Verify required files exist; delete temp archive.
- Keep download cancellation working; extraction phase should also honor the CT.

### 4. New `ParakeetTranscriber`

Wraps sherpa-onnx's `OfflineRecognizer`. The same class handles both
transducer and CTC bundles by populating different sub-configs (one of
`ModelConfig.Transducer` or `ModelConfig.NeMoCtc`, never both).

Verified API shape (sherpa-onnx 1.13.1):

```csharp
var config = new OfflineRecognizerConfig();
config.FeatConfig.SampleRate = 16000;
config.FeatConfig.FeatureDim = 80;
config.ModelConfig.Tokens = Path.Combine(bundleDir, "tokens.txt");
config.ModelConfig.NumThreads = settings.ThreadCount > 0 ? settings.ThreadCount : 4;
config.ModelConfig.Provider = "cpu";  // GPU not available via NuGet — see Runtime choice
config.ModelConfig.Debug = 0;

if (descriptor.Family == ParakeetFamily.Transducer)
{
    config.ModelConfig.Transducer.Encoder = GlobOne(bundleDir, "encoder*.onnx");
    config.ModelConfig.Transducer.Decoder = GlobOne(bundleDir, "decoder*.onnx");
    config.ModelConfig.Transducer.Joiner  = GlobOne(bundleDir, "joiner*.onnx");
    config.ModelConfig.ModelType = "nemo_transducer";
}
else // ParakeetFamily.NeMoCtc
{
    config.ModelConfig.NeMoCtc.Model = GlobOne(bundleDir, "model*.onnx");
}

config.DecodingMethod  = settings.GreedyDecoding ? "greedy_search" : "modified_beam_search";
config.MaxActivePaths  = 4;

var recognizer = new OfflineRecognizer(config);
```

Transcribe flow (no `InputFinished()` call needed for offline recognizer):

```csharp
using var stream = recognizer.CreateStream();
stream.AcceptWaveform(16000, audioPcm16kMono);
recognizer.Decode(stream);
string text = stream.Result.Text;
```

Threading: like `WhisperTranscriber`, guard load/transcribe with a
`SemaphoreSlim` so the existing TrayApplication state machine doesn't change.

### 5. `WhisperRuntimeSetup` becomes `RuntimeSetup`

`Program.cs` currently calls `WhisperRuntimeSetup.Configure(backend)` once at
startup. We need a small dispatcher that also configures whatever sherpa-onnx
needs (mostly nothing — it picks per-recognizer — but we should log loaded
providers symmetrically).

### 6. `TrayApplication` factory swap

Replace `WhisperTranscriber _transcriber` with `ITranscriber _transcriber` and
build it via `TranscriberFactory.Create(settings.Engine)`. When settings change
in a way that switches engines, dispose+rebuild like we already do for model
file changes (see `TrayApplication.cs:449`).

### 7. Settings UI

Model tab today is a single combo + listview. Change to:
- New "Engine" combo (Whisper / Parakeet) above the active-model combo.
- "Available Models" listview gets a 4th column "Engine" and a filter checkbox
  "Show only selected engine" (default on).
- Download/Delete buttons behave the same; download progress for Parakeet
  reports as "Downloading… → Extracting…".

### 8. Language handling

Parakeet-TDT 0.6B v2 is English-only — selecting it should force the language
combo to English and disable it (or hide it). v3 multilingual exposes its own
language set. Build a small `model.Languages` driven dropdown rather than the
current free-form Whisper language list.

## File-by-file change list

- `Windows/WhisperKey/WhisperKey.csproj` — add NuGet refs:
  `org.k2fsa.sherpa.onnx` 1.13.1 (pulls in `org.k2fsa.sherpa.onnx.runtime.win-x64`
  transitively) and `SharpCompress` for tar.bz2 extraction. No ONNX Runtime
  package is needed — sherpa-onnx bundles its own.
- `Transcription/ITranscriber.cs` — **new**, interface above.
- `Transcription/WhisperTranscriber.cs` — implement `ITranscriber`,
  signature changes only.
- `Transcription/ParakeetTranscriber.cs` — **new**.
- `Transcription/ModelManager.cs` — engine-aware list, dir-based detection,
  archive extraction for Parakeet.
- `Transcription/ModelDescriptor.cs` — **new** (replaces inline `ModelInfo`).
- `Transcription/RuntimeSetup.cs` — rename + extend `WhisperRuntimeSetup`.
- `Transcription/TranscriberFactory.cs` — **new**, returns `ITranscriber`.
- `Settings/AppSettings.cs` — add `Engine = TranscriptionEngine.Whisper`;
  keep `ModelFileName` as-is (now means "model identifier").
- `Settings/SettingsManager.cs` — no migration needed; new model dir layout
  for Parakeet bundles lives under `%LOCALAPPDATA%\WhisperKey\Models\` next to
  the ggml files (the bundle name itself disambiguates).
- `Settings/SettingsForm.cs` — engine selector, filtered model list, language
  combo driven by selected model.
- `App/TrayApplication.cs` — swap to `ITranscriber`, rebuild on engine change.
- `Program.cs` — call new `RuntimeSetup.Configure`.

## Phased execution

Tracked here so we can pick up across sessions. Mark each item `[x]` when done
and add brief notes about anything surprising.

### Phase 0 — investigation (do this before writing code)
- [x] Confirm `org.k2fsa.sherpa.onnx` NuGet package name, current version, and
  payload. → **`org.k2fsa.sherpa.onnx` 1.13.1** is a meta-package; native
  binaries ship in per-RID runtime packages, pulled transitively. The
  `win-x64` runtime is ~7.5 MB and **CPU-only**.
- [x] Decide GPU provider. → **CPU-only for v1.** No GPU NuGet exists; GPU
      would require building sherpa-onnx from source with a custom ONNX
      Runtime. Whisper.net.Runtime.Cuda continues to coexist for Whisper users.
- [x] Verify bundle URLs. → Hosted on **sherpa-onnx GitHub releases**, tag
      `asr-models`. See "Models to ship in the picker" table above for exact
      filenames. Note filenames inside bundles differ (`.int8.onnx` vs `.onnx`)
      so `ModelManager` must glob to discover paths.
- [x] Nail down the C# OfflineRecognizer API. → Confirmed against
      `dotnet-examples/offline-decode-files/Program.cs` on master. See the
      ParakeetTranscriber sketch above for the verified shape.

### Phase 1 — abstractions, no behavior change
- [ ] Introduce `ITranscriber` + `ModelDescriptor`.
- [ ] Refactor `WhisperTranscriber` and `ModelManager` to use them.
- [ ] Add settings migration; existing users still load `ggml-base.en.bin`.
- [ ] Build + run the existing flow end-to-end, confirm nothing regressed.

### Phase 2 — Parakeet engine
- [ ] Add NuGet deps and a `RuntimeSetup` provider-selection helper.
- [ ] Implement `ParakeetTranscriber` + `TranscriberFactory`.
- [ ] Extend `ModelManager` with archive download + extraction; add the 110M
      model first (smallest, fastest to iterate).
- [ ] Wire `TrayApplication` to build the right transcriber.

### Phase 3 — Settings UI
- [ ] Add engine selector + filtered model list.
- [ ] Model-driven language combo.
- [ ] Engine-switch triggers transcriber rebuild without restart.

### Phase 4 — additional models + polish
- [ ] Add v2 and v2-int8 entries.
- [ ] Add v3 multilingual entry + language list.
- [ ] Document Parakeet license note in the README (CC-BY-4.0 for NeMo
      Parakeet weights — needs attribution in About tab).
- [ ] Update version in `.csproj` and `Build.ps1` publish step.

### Phase 5 — verification
- [ ] Push-to-talk and Toggle modes both work with Parakeet.
- [ ] First-run download + extract works on a clean profile.
- [ ] Engine switch in Settings doesn't leak handles (verify by repeated
      Apply, watch process memory).
- [ ] CPU-only machine path verified (DirectML/CUDA absent).
- [ ] Existing Whisper users upgrade and see their model preserved.

## Risks / open questions

- **Native dependency size.** Smaller than feared: the sherpa-onnx win-x64
  runtime NuGet is ~7.5 MB. Total installer growth should be well under 20 MB.
- **No GPU acceleration for Parakeet in v1.** Documented above. Users wanting
  GPU should keep using Whisper with the existing CUDA/Vulkan runtimes. The
  110M CTC model is fast enough on modern CPUs that this is unlikely to be
  a real complaint.
- **Sample-rate assumption.** `AudioRecorder` already produces 16k mono float;
  sherpa-onnx expects exactly that. No change needed.
- **Long-form audio.** Parakeet-TDT v2 has a context limit (~24s in the paper,
  longer in practice). For very long dictations we may need chunking; out of
  scope for v1 since current hotkey UX records short bursts.
- **License attribution.** NeMo Parakeet weights are CC-BY-4.0. Add an
  attribution line in the About tab and a NOTICE file before shipping a
  release that includes Parakeet.

## Definition of done

- A user can install the new WhisperKey build, pick "Parakeet (small / 110M)"
  in Settings, click OK, watch it download+extract, and immediately dictate
  with the same hotkey UX as today.
- Switching back to a Whisper model in Settings works without restart.
- Existing settings.json files continue to load with their previous model.
- CI/publish step (`Build.ps1`) produces a single-file exe that runs on a
  machine without the .NET runtime installed.
