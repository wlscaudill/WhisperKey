package com.whisperkey

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Handles audio recording for voice input.
 * Records 16kHz mono audio suitable for Whisper transcription.
 */
class AudioRecorder {

    companion object {
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var isRecording = false

    private val audioBuffer = mutableListOf<Short>()
    private var waveformCallback: ((FloatArray) -> Unit)? = null

    private val bufferSize: Int by lazy {
        val minSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        maxOf(minSize, SAMPLE_RATE) // At least 1 second buffer
    }

    /**
     * Starts audio recording.
     * @param onWaveformUpdate Callback for real-time waveform updates
     */
    fun start(onWaveformUpdate: ((FloatArray) -> Unit)? = null) {
        if (isRecording) return

        waveformCallback = onWaveformUpdate
        audioBuffer.clear()

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                audioRecord = null
                return
            }

            audioRecord?.startRecording()
            isRecording = true

            recordingJob = scope.launch {
                recordAudio()
            }
        } catch (e: SecurityException) {
            audioRecord?.release()
            audioRecord = null
        }
    }

    /**
     * Stops recording and returns the recorded audio data.
     * @return Audio samples as float array normalized to [-1, 1]
     */
    fun stop(): FloatArray? {
        if (!isRecording) return null

        isRecording = false
        recordingJob?.cancel()
        recordingJob = null

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        return convertToFloatArray(audioBuffer.toShortArray())
    }

    /**
     * Releases all resources.
     */
    fun release() {
        stop()
        scope.cancel()
    }

    /**
     * Checks if currently recording.
     */
    fun isRecording(): Boolean = isRecording

    private suspend fun recordAudio() {
        val buffer = ShortArray(bufferSize / 2)

        while (isRecording && scope.isActive) {
            val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: -1

            if (readCount > 0) {
                synchronized(audioBuffer) {
                    for (i in 0 until readCount) {
                        audioBuffer.add(buffer[i])
                    }
                }

                // Send waveform update
                waveformCallback?.let { callback ->
                    val waveformData = buffer.copyOf(readCount)
                    val floatData = convertToFloatArray(waveformData)
                    callback(floatData)
                }
            }
        }
    }

    private fun convertToFloatArray(shortArray: ShortArray): FloatArray {
        return FloatArray(shortArray.size) { i ->
            shortArray[i] / 32768.0f
        }
    }
}
