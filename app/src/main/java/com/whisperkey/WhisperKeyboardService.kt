package com.whisperkey

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.preference.PreferenceManager
import com.whisperkey.ui.VoiceInputView
import com.whisperkey.util.Logger

/**
 * Main input method service for WhisperKey.
 * Handles voice input and text insertion into the current input field.
 */
class WhisperKeyboardService : InputMethodService() {

    companion object {
        private const val TAG = "WhisperKeyboardService"
    }

    private var voiceInputView: VoiceInputView? = null
    private var whisperEngine: WhisperEngine? = null
    private var audioRecorder: AudioRecorder? = null
    private var emojiManager: EmojiManager? = null
    private var modelManager: ModelManager? = null
    private var isModelLoaded = false

    override fun onCreate() {
        super.onCreate()
        Logger.i(TAG, "WhisperKeyboardService onCreate")

        whisperEngine = WhisperEngine(this)
        audioRecorder = AudioRecorder()
        emojiManager = EmojiManager(this)
        modelManager = ModelManager(this)

        // Try to load model on startup
        loadModel()
    }

    private fun loadModel() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val modelSize = prefs.getString("model_size", "base") ?: "base"

        Logger.i(TAG, "=== Loading Model ===")
        Logger.i(TAG, "Model size: $modelSize")
        Logger.i(TAG, "Storage: ${modelManager?.getStorageDisplayName()}")
        Logger.i(TAG, "Using SD Card: ${modelManager?.isUsingSdCard()}")

        // Check if model is downloaded
        val isDownloaded = modelManager?.isModelDownloaded(modelSize) ?: false
        Logger.i(TAG, "Is model downloaded: $isDownloaded")

        val modelPath = modelManager?.getDownloadedModelPath(modelSize)
        Logger.i(TAG, "Resolved model path: $modelPath")

        if (modelPath != null) {
            val file = java.io.File(modelPath)
            Logger.i(TAG, "File exists: ${file.exists()}")
            Logger.i(TAG, "File size: ${file.length()}")
            Logger.i(TAG, "File readable: ${file.canRead()}")

            if (file.exists() && file.canRead()) {
                isModelLoaded = whisperEngine?.initialize(modelPath) ?: false
                Logger.i(TAG, "Whisper initialization result: $isModelLoaded")
            } else {
                Logger.e(TAG, "Model file not accessible!")
                isModelLoaded = false
            }
        } else {
            Logger.w(TAG, "Model not found in any location")
            isModelLoaded = false
        }
        Logger.i(TAG, "=== Model load complete, loaded: $isModelLoaded ===")
    }

    override fun onCreateInputView(): View {
        Logger.d(TAG, "onCreateInputView")

        voiceInputView = VoiceInputView(this).apply {
            setOnRecordingListener(object : VoiceInputView.OnRecordingListener {
                override fun onRecordingStarted() {
                    startRecording()
                }

                override fun onRecordingStopped() {
                    stopRecording()
                }
            })

            setOnActionListener(object : VoiceInputView.OnActionListener {
                override fun onBackspaceClick() {
                    Logger.d(TAG, "Backspace clicked")
                    currentInputConnection?.deleteSurroundingText(1, 0)
                }

                override fun onSettingsClick() {
                    Logger.d(TAG, "Settings clicked")
                    openSettings()
                }

                override fun onEmojiClick(emoji: String) {
                    Logger.d(TAG, "Emoji clicked: $emoji")
                    currentInputConnection?.commitText(emoji, 1)
                }
            })
        }
        return voiceInputView!!
    }

    private fun openSettings() {
        val intent = android.content.Intent(this, SettingsActivity::class.java)
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        Logger.d(TAG, "onStartInputView, restarting: $restarting")

        // Reload model if not loaded
        if (!isModelLoaded) {
            loadModel()
        }

        // Apply settings
        applySettings()

        // Update UI based on model status
        if (!isModelLoaded) {
            voiceInputView?.showNoModel()
        } else {
            voiceInputView?.reset()
        }
    }

    private fun applySettings() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        // Recording mode
        val recordingMode = prefs.getString("recording_mode", "tap_toggle")
        val holdToRecord = recordingMode == "hold_to_record"
        voiceInputView?.setRecordingMode(holdToRecord)
        Logger.d(TAG, "Recording mode: $recordingMode (holdToRecord: $holdToRecord)")
    }

    override fun onFinishInput() {
        super.onFinishInput()
        Logger.d(TAG, "onFinishInput")
        stopRecording()
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.i(TAG, "WhisperKeyboardService onDestroy")
        audioRecorder?.release()
        whisperEngine?.release()
    }

    private fun startRecording() {
        if (!isModelLoaded) {
            Logger.w(TAG, "Cannot start recording: model not loaded")
            voiceInputView?.showNoModel()
            return
        }

        Logger.i(TAG, "Starting recording")
        audioRecorder?.start { audioData ->
            // Calculate RMS amplitude from the audio samples
            val amplitude = calculateAmplitude(audioData)
            runOnMainThread {
                voiceInputView?.updateWaveform(amplitude)
            }
        }
    }

    /**
     * Calculates the RMS (root mean square) amplitude from audio samples.
     * Returns a value between 0 and 1.
     */
    private fun calculateAmplitude(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0f
        for (sample in samples) {
            sum += sample * sample
        }
        val rms = kotlin.math.sqrt(sum / samples.size)
        // Scale RMS to a more visually useful range (typical speech RMS is 0.05-0.3)
        return (rms * 3f).coerceIn(0f, 1f)
    }

    private fun stopRecording() {
        Logger.i(TAG, "Stopping recording")
        val audioData = audioRecorder?.stop()
        if (audioData != null) {
            Logger.i(TAG, "Captured ${audioData.size} samples (${audioData.size / 16000f}s)")
            processAudio(audioData)
        } else {
            Logger.w(TAG, "No audio data captured")
            voiceInputView?.reset()
        }
    }

    private fun processAudio(audioData: FloatArray) {
        Logger.i(TAG, "Processing audio...")
        voiceInputView?.showProcessing()

        whisperEngine?.transcribe(audioData) { result ->
            runOnMainThread {
                handleTranscriptionResult(result)
            }
        }
    }

    private fun handleTranscriptionResult(text: String) {
        Logger.i(TAG, "Transcription result: \"$text\"")
        if (text.isNotBlank()) {
            val processedText = emojiManager?.processText(text) ?: text
            Logger.d(TAG, "After emoji processing: \"$processedText\"")
            commitText(processedText)
        } else {
            Logger.w(TAG, "Empty transcription result")
        }
        voiceInputView?.reset()
    }

    private fun commitText(text: String) {
        Logger.d(TAG, "Committing text: \"$text\"")
        currentInputConnection?.commitText(text, 1)
    }

    private fun runOnMainThread(action: () -> Unit) {
        voiceInputView?.post(action)
    }
}
