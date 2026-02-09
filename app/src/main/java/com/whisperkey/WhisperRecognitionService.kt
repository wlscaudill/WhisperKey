package com.whisperkey

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import androidx.preference.PreferenceManager
import com.whisperkey.util.Logger
import java.io.File

/**
 * Exposes the Whisper engine as a system-level voice input provider.
 * This allows other keyboards (Gboard, Samsung, etc.) to use WhisperKey
 * for speech-to-text by selecting it in Settings > Voice input.
 */
class WhisperRecognitionService : RecognitionService() {

    companion object {
        private const val TAG = "WhisperRecognitionService"
    }

    private var whisperEngine: WhisperEngine? = null
    private var audioRecorder: AudioRecorder? = null
    private var modelManager: ModelManager? = null
    private var isModelLoaded = false
    private var activeCallback: Callback? = null

    override fun onCreate() {
        super.onCreate()
        Logger.i(TAG, "Service created")
        whisperEngine = WhisperEngine(this)
        audioRecorder = AudioRecorder()
        modelManager = ModelManager(this)
    }

    override fun onDestroy() {
        Logger.i(TAG, "Service destroyed")
        audioRecorder?.release()
        whisperEngine?.release()
        audioRecorder = null
        whisperEngine = null
        modelManager = null
        super.onDestroy()
    }

    override fun onStartListening(intent: Intent?, callback: Callback) {
        Logger.i(TAG, "onStartListening")
        activeCallback = callback

        if (!ensureModelLoaded()) {
            Logger.e(TAG, "Model not loaded, cannot start listening")
            try {
                callback.error(SpeechRecognizer.ERROR_SERVER)
            } catch (e: Exception) {
                Logger.e(TAG, "Error sending error callback: ${e.message}", e)
            }
            return
        }

        try {
            callback.readyForSpeech(Bundle())
        } catch (e: Exception) {
            Logger.e(TAG, "Error in readyForSpeech callback: ${e.message}", e)
        }

        try {
            audioRecorder?.start { waveformData ->
                // Calculate RMS for the rmsChanged callback
                var sum = 0f
                for (sample in waveformData) {
                    sum += sample * sample
                }
                val rms = Math.sqrt((sum / waveformData.size).toDouble()).toFloat()
                // Scale to roughly 0-10 range expected by SpeechRecognizer
                val scaledRms = (rms * 20f).coerceIn(0f, 10f)
                try {
                    callback.rmsChanged(scaledRms)
                } catch (e: Exception) {
                    // Callback may be dead if client disconnected
                }
            }

            try {
                callback.beginningOfSpeech()
            } catch (e: Exception) {
                Logger.e(TAG, "Error in beginningOfSpeech callback: ${e.message}", e)
            }
        } catch (e: SecurityException) {
            Logger.e(TAG, "No audio permission: ${e.message}", e)
            try {
                callback.error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
            } catch (e2: Exception) {
                Logger.e(TAG, "Error sending error callback: ${e2.message}", e2)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to start recording: ${e.message}", e)
            try {
                callback.error(SpeechRecognizer.ERROR_AUDIO)
            } catch (e2: Exception) {
                Logger.e(TAG, "Error sending error callback: ${e2.message}", e2)
            }
        }
    }

    override fun onStopListening(callback: Callback) {
        Logger.i(TAG, "onStopListening")

        val audioData = audioRecorder?.stop()

        try {
            callback.endOfSpeech()
        } catch (e: Exception) {
            Logger.e(TAG, "Error in endOfSpeech callback: ${e.message}", e)
        }

        if (audioData == null || audioData.isEmpty()) {
            Logger.w(TAG, "No audio data captured")
            try {
                callback.error(SpeechRecognizer.ERROR_NO_MATCH)
            } catch (e: Exception) {
                Logger.e(TAG, "Error sending error callback: ${e.message}", e)
            }
            return
        }

        Logger.d(TAG, "Transcribing ${audioData.size} samples")
        whisperEngine?.transcribe(audioData) { result ->
            if (result.isBlank()) {
                Logger.w(TAG, "Transcription returned empty result")
                try {
                    callback.error(SpeechRecognizer.ERROR_NO_MATCH)
                } catch (e: Exception) {
                    Logger.e(TAG, "Error sending error callback: ${e.message}", e)
                }
            } else {
                Logger.i(TAG, "Transcription result: \"${result.take(100)}\"")
                val results = Bundle()
                val matches = ArrayList<String>()
                matches.add(result.trim())
                results.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, matches)
                results.putFloatArray(SpeechRecognizer.CONFIDENCE_SCORES, floatArrayOf(1.0f))
                try {
                    callback.results(results)
                } catch (e: Exception) {
                    Logger.e(TAG, "Error sending results callback: ${e.message}", e)
                }
            }
        }

        activeCallback = null
    }

    override fun onCancel(callback: Callback) {
        Logger.i(TAG, "onCancel")
        audioRecorder?.stop()
        activeCallback = null
    }

    private fun ensureModelLoaded(): Boolean {
        if (whisperEngine?.isReady() == true) return true

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val modelSize = prefs.getString("model_size", "base") ?: "base"
        val modelPath = modelManager?.getDownloadedModelPath(modelSize) ?: return false
        val file = File(modelPath)
        if (!file.exists() || !file.canRead()) return false

        isModelLoaded = whisperEngine?.initialize(modelPath) ?: false
        Logger.i(TAG, "Model load result: $isModelLoaded (size: $modelSize)")
        return isModelLoaded
    }
}
