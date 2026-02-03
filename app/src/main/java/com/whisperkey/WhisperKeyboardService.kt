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
            voiceInputView?.updateWaveform(audioData)
        }
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
