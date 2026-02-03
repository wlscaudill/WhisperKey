package com.whisperkey.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import com.whisperkey.R

/**
 * Custom view for voice input UI in the keyboard.
 * Displays recording button, waveform visualization, and status text.
 */
class VoiceInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    interface OnRecordingListener {
        fun onRecordingStarted()
        fun onRecordingStopped()
    }

    private var recordingListener: OnRecordingListener? = null
    private var isRecording = false
    private var isProcessing = false

    private val recordButton: ImageButton
    private val statusText: TextView
    private val waveformView: WaveformView

    init {
        LayoutInflater.from(context).inflate(R.layout.voice_input_view, this, true)

        recordButton = findViewById(R.id.record_button)
        statusText = findViewById(R.id.status_text)
        waveformView = findViewById(R.id.waveform_view)

        setupRecordButton()
    }

    private fun setupRecordButton() {
        recordButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!isProcessing) {
                        startRecording()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isRecording) {
                        stopRecording()
                    }
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Sets the recording listener.
     */
    fun setOnRecordingListener(listener: OnRecordingListener) {
        recordingListener = listener
    }

    /**
     * Updates the waveform visualization with new audio data.
     */
    fun updateWaveform(audioData: FloatArray) {
        waveformView.updateWaveform(audioData)
    }

    /**
     * Shows the processing state.
     */
    fun showProcessing() {
        isProcessing = true
        isRecording = false
        statusText.text = context.getString(R.string.voice_input_processing)
        waveformView.visibility = View.GONE
        recordButton.isEnabled = false
    }

    /**
     * Shows an error state.
     */
    fun showError(message: String? = null) {
        isProcessing = false
        isRecording = false
        statusText.text = message ?: context.getString(R.string.voice_input_error)
        waveformView.visibility = View.GONE
        recordButton.isEnabled = true
    }

    /**
     * Resets the view to the initial state.
     */
    fun reset() {
        isRecording = false
        isProcessing = false
        statusText.text = context.getString(R.string.voice_input_hint)
        waveformView.visibility = View.GONE
        waveformView.clear()
        recordButton.isEnabled = true
    }

    private fun startRecording() {
        isRecording = true
        statusText.text = context.getString(R.string.voice_input_listening)
        waveformView.visibility = View.VISIBLE
        waveformView.clear()
        recordingListener?.onRecordingStarted()
    }

    private fun stopRecording() {
        isRecording = false
        recordingListener?.onRecordingStopped()
    }

    /**
     * Inner view for drawing audio waveform.
     */
    class WaveformView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
    ) : View(context, attrs, defStyleAttr) {

        private val paint = Paint().apply {
            color = context.getColor(R.color.waveform_color)
            strokeWidth = 4f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        private var waveformData: FloatArray = FloatArray(0)
        private val maxSamples = 100

        fun updateWaveform(data: FloatArray) {
            // Downsample to maxSamples points
            val step = maxOf(1, data.size / maxSamples)
            waveformData = FloatArray(minOf(maxSamples, data.size)) { i ->
                data[minOf(i * step, data.size - 1)]
            }
            invalidate()
        }

        fun clear() {
            waveformData = FloatArray(0)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            if (waveformData.isEmpty()) return

            val centerY = height / 2f
            val maxAmplitude = height / 2f * 0.8f
            val stepX = width.toFloat() / waveformData.size

            for (i in 0 until waveformData.size - 1) {
                val x1 = i * stepX
                val y1 = centerY + waveformData[i] * maxAmplitude
                val x2 = (i + 1) * stepX
                val y2 = centerY + waveformData[i + 1] * maxAmplitude

                canvas.drawLine(x1, y1, x2, y2, paint)
            }
        }
    }
}
