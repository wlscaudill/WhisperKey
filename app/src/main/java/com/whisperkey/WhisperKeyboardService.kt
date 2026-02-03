package com.whisperkey

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import com.whisperkey.ui.VoiceInputView

/**
 * Main input method service for WhisperKey.
 * Handles voice input and text insertion into the current input field.
 */
class WhisperKeyboardService : InputMethodService() {

    private var voiceInputView: VoiceInputView? = null
    private var whisperEngine: WhisperEngine? = null
    private var audioRecorder: AudioRecorder? = null
    private var emojiManager: EmojiManager? = null

    override fun onCreate() {
        super.onCreate()
        whisperEngine = WhisperEngine(this)
        audioRecorder = AudioRecorder()
        emojiManager = EmojiManager(this)
    }

    override fun onCreateInputView(): View {
        voiceInputView = VoiceInputView(this).apply {
            setOnRecordingListener(object : VoiceInputView.OnRecordingListener {
                override fun onRecordingStarted() {
                    startRecording()
                }

                override fun onRecordingStopped() {
                    stopRecording()
                }
            })
        }
        return voiceInputView!!
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        voiceInputView?.reset()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        stopRecording()
    }

    override fun onDestroy() {
        super.onDestroy()
        audioRecorder?.release()
        whisperEngine?.release()
    }

    private fun startRecording() {
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
        audioRecorder?.stop()?.let { audioData ->
            processAudio(audioData)
        }
    }

    private fun processAudio(audioData: FloatArray) {
        voiceInputView?.showProcessing()

        whisperEngine?.transcribe(audioData) { result ->
            runOnMainThread {
                handleTranscriptionResult(result)
            }
        }
    }

    private fun handleTranscriptionResult(text: String) {
        if (text.isNotBlank()) {
            val processedText = emojiManager?.processText(text) ?: text
            commitText(processedText)
        }
        voiceInputView?.reset()
    }

    private fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    private fun runOnMainThread(action: () -> Unit) {
        voiceInputView?.post(action)
    }
}
