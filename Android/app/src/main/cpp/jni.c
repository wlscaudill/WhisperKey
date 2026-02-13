#include <jni.h>
#include <android/log.h>
#include <stdlib.h>
#include <string.h>
#include "whisper.h"

#define UNUSED(x) (void)(x)
#define TAG "WhisperKey"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

// Initialize whisper context from file path
JNIEXPORT jlong JNICALL
Java_com_whisperkey_WhisperEngine_nativeInit(
        JNIEnv *env,
        jobject thiz,
        jstring model_path_str) {
    UNUSED(thiz);

    const char *model_path = (*env)->GetStringUTFChars(env, model_path_str, NULL);
    LOGI("Loading whisper model from: %s", model_path);

    struct whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context *context = whisper_init_from_file_with_params(model_path, cparams);

    (*env)->ReleaseStringUTFChars(env, model_path_str, model_path);

    if (context == NULL) {
        LOGE("Failed to load whisper model");
        return 0;
    }

    LOGI("Whisper model loaded successfully");
    return (jlong) context;
}

// Release whisper context
JNIEXPORT void JNICALL
Java_com_whisperkey_WhisperEngine_nativeRelease(
        JNIEnv *env,
        jobject thiz,
        jlong context_ptr) {
    UNUSED(env);
    UNUSED(thiz);

    if (context_ptr == 0) {
        return;
    }

    struct whisper_context *context = (struct whisper_context *) context_ptr;
    whisper_free(context);
    LOGI("Whisper context released");
}

// Transcribe audio data
JNIEXPORT jstring JNICALL
Java_com_whisperkey_WhisperEngine_nativeTranscribe(
        JNIEnv *env,
        jobject thiz,
        jlong context_ptr,
        jfloatArray audio_data,
        jint num_threads) {
    UNUSED(thiz);

    if (context_ptr == 0) {
        LOGE("Transcribe called with null context");
        return (*env)->NewStringUTF(env, "");
    }

    struct whisper_context *context = (struct whisper_context *) context_ptr;

    // Get audio data from Java array
    jfloat *audio_data_arr = (*env)->GetFloatArrayElements(env, audio_data, NULL);
    jsize audio_data_length = (*env)->GetArrayLength(env, audio_data);

    LOGI("Transcribing %d samples with %d threads", audio_data_length, num_threads);

    // Set up transcription parameters - optimized for speed
    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.language = "en";
    params.n_threads = num_threads;
    params.offset_ms = 0;
    params.no_context = true;
    params.single_segment = true;      // Faster for short audio (keyboard input)
    params.suppress_blank = true;
    params.suppress_nst = true;

    // Run transcription
    whisper_reset_timings(context);
    int result = whisper_full(context, params, audio_data_arr, audio_data_length);

    (*env)->ReleaseFloatArrayElements(env, audio_data, audio_data_arr, JNI_ABORT);

    if (result != 0) {
        LOGE("Transcription failed with code: %d", result);
        return (*env)->NewStringUTF(env, "");
    }

    // Collect all segments into a single string
    int n_segments = whisper_full_n_segments(context);
    LOGI("Transcription complete: %d segments", n_segments);

    // Calculate total length needed
    size_t total_len = 0;
    for (int i = 0; i < n_segments; i++) {
        const char *text = whisper_full_get_segment_text(context, i);
        if (text != NULL) {
            total_len += strlen(text);
        }
    }

    // Allocate buffer and concatenate segments
    char *full_text = (char *) malloc(total_len + 1);
    if (full_text == NULL) {
        LOGE("Failed to allocate memory for transcription result");
        return (*env)->NewStringUTF(env, "");
    }
    full_text[0] = '\0';

    for (int i = 0; i < n_segments; i++) {
        const char *text = whisper_full_get_segment_text(context, i);
        if (text != NULL) {
            strcat(full_text, text);
        }
    }

    // Trim leading whitespace
    char *trimmed = full_text;
    while (*trimmed == ' ' || *trimmed == '\n' || *trimmed == '\t') {
        trimmed++;
    }

    jstring jresult = (*env)->NewStringUTF(env, trimmed);
    free(full_text);

    LOGI("Transcription result: %s", trimmed);
    return jresult;
}

// Check if model is loaded
JNIEXPORT jboolean JNICALL
Java_com_whisperkey_WhisperEngine_nativeIsLoaded(
        JNIEnv *env,
        jobject thiz,
        jlong context_ptr) {
    UNUSED(env);
    UNUSED(thiz);

    return context_ptr != 0 ? JNI_TRUE : JNI_FALSE;
}

// Get system info string (for debugging)
JNIEXPORT jstring JNICALL
Java_com_whisperkey_WhisperEngine_nativeGetSystemInfo(
        JNIEnv *env,
        jobject thiz) {
    UNUSED(thiz);

    const char *sysinfo = whisper_print_system_info();
    return (*env)->NewStringUTF(env, sysinfo);
}
