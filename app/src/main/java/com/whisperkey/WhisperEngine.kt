package com.whisperkey

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages the Whisper speech recognition engine.
 * Handles model loading and audio transcription using whisper.cpp.
 */
class WhisperEngine(private val context: Context) {

    companion object {
        private const val SAMPLE_RATE = 16000
        private val NUM_THREADS = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)

        init {
            System.loadLibrary("whisper")
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var contextHandle: Long = 0
    private var isInitialized = false

    /**
     * Initializes the Whisper engine with the specified model.
     * @param modelPath Path to the Whisper model file
     * @return true if initialization was successful
     */
    fun initialize(modelPath: String): Boolean {
        if (isInitialized) {
            release()
        }

        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            return false
        }

        contextHandle = nativeInit(modelPath)
        isInitialized = contextHandle != 0L
        return isInitialized
    }

    /**
     * Transcribes audio data to text.
     * @param audioData Audio samples as float array (16kHz, mono)
     * @param callback Callback to receive the transcription result
     */
    fun transcribe(audioData: FloatArray, callback: (String) -> Unit) {
        scope.launch {
            val result = transcribeInternal(audioData)
            withContext(Dispatchers.Main) {
                callback(result)
            }
        }
    }

    /**
     * Transcribes audio data synchronously.
     * @param audioData Audio samples as float array (16kHz, mono)
     * @return Transcribed text
     */
    suspend fun transcribeSuspend(audioData: FloatArray): String {
        return withContext(Dispatchers.Default) {
            transcribeInternal(audioData)
        }
    }

    private fun transcribeInternal(audioData: FloatArray): String {
        if (!isInitialized) {
            return ""
        }
        return nativeTranscribe(contextHandle, audioData, NUM_THREADS)
    }

    /**
     * Checks if the engine is ready for transcription.
     */
    fun isReady(): Boolean {
        return isInitialized && nativeIsLoaded(contextHandle)
    }

    /**
     * Releases all native resources.
     */
    fun release() {
        if (contextHandle != 0L) {
            nativeRelease(contextHandle)
            contextHandle = 0
        }
        isInitialized = false
        scope.cancel()
    }

    /**
     * Gets the default model directory path.
     */
    fun getModelDirectory(): File {
        return File(context.filesDir, "models")
    }

    /**
     * Gets system info from whisper.cpp (for debugging).
     */
    fun getSystemInfo(): String {
        return nativeGetSystemInfo()
    }

    // Native method declarations
    private external fun nativeInit(modelPath: String): Long
    private external fun nativeRelease(contextHandle: Long)
    private external fun nativeTranscribe(contextHandle: Long, audioData: FloatArray, numThreads: Int): String
    private external fun nativeIsLoaded(contextHandle: Long): Boolean
    private external fun nativeGetSystemInfo(): String
}
