# WhisperKey Performance Analysis

## Audio Processing Flow

```
AudioRecorder (capture) → WhisperEngine (Kotlin) → JNI → whisper.cpp → Result
```

## Bottlenecks Identified

| Priority | Issue | Location | Impact |
|----------|-------|----------|--------|
| **HIGH** | Thread count capped at 4 | WhisperEngine.kt:22 | Modern phones have 8+ cores |
| **HIGH** | Missing whisper speed parameters | jni.c | Not using `flash_attn`, timestamps enabled |
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

Note: `flash_attn` parameter is not available in current whisper.cpp version (v1.7.4). Upgrading whisper.cpp could enable this optimization.

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
