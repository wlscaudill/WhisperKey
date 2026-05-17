# WhisperKey Performance Analysis

## Audio Processing Flow

```
AudioRecorder (capture) → WhisperEngine (Kotlin) → JNI → whisper.cpp → Result
```

## Bottlenecks Identified

| Priority | Issue | Location | Impact |
|----------|-------|----------|--------|
| **HIGH** | Thread count capped at 4 | WhisperEngine.kt:22 | Modern phones have 8+ cores |
| **HIGH** | Missing whisper speed parameters | jni.c | Timestamps enabled (single_segment fixed); `flash_attn` requires GPU backend |
| **MEDIUM** | O(n²) string concatenation | jni.c:130 | Uses `strcat()` in loop |
| **LOW** | Synchronized buffer per read | AudioRecorder.kt:119 | Lock contention |

## Fixes Applied

### 1. Increased Thread Limit (WhisperEngine.kt)

**Before:**
```kotlin
Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
```

**After:**
```kotlin
Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
```

Allows modern 8-core phones to use all available cores. Automatically scales down on older devices.

### 2. Optimized Whisper Parameters (jni.c)

Changed `single_segment` from `false` to `true` - treats audio as single segment, faster for short keyboard recordings.

Note: `flash_attn` is present in `whisper_context_params` (whisper.cpp v1.8.3) and defaults to `true`, but `src/whisper.cpp:1140` short-circuits it unless `use_gpu` is also true. Our build is CPU-only (`GGML_USE_CPU`, no Vulkan/OpenCL), so flash attention currently does nothing. It will become useful once a GPU backend is added — see Plan.md Phase 8.2.

## Potential Future Optimizations

### O(n²) String Concatenation
Current code uses `strcat()` in a loop which is O(n²). Could be replaced with pointer arithmetic for O(n).

### Lock-Free Audio Buffer
Current `AudioRecorder` uses synchronized blocks. Could use a lock-free ring buffer for lower latency.

### GPU Acceleration
Currently CPU-only inference. Modern Android devices have capable GPUs that could accelerate inference via OpenCL or Vulkan compute.

### Model Selection
- **tiny.en** (~78 MB) - Fastest, good for short phrases
- **base.en** (~148 MB) - Balanced (current default)
- **small.en** (~488 MB) - Most accurate, slowest

For keyboard use cases, tiny.en may provide the best speed/accuracy tradeoff.
