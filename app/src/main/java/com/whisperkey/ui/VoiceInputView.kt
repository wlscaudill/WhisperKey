package com.whisperkey.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.Button
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

    interface OnActionListener {
        fun onBackspaceClick()
        fun onSpaceClick()
        fun onEnterClick()
        fun onSettingsClick()
        fun onEmojiClick(emoji: String)
    }

    private var recordingListener: OnRecordingListener? = null
    private var actionListener: OnActionListener? = null
    private var isRecording = false
    private var isProcessing = false
    private var holdToRecord = false // false = tap to toggle, true = hold to record

    // Backspace repeat handling
    private val backspaceHandler = Handler(Looper.getMainLooper())
    private var isBackspacePressed = false
    private val backspaceRepeatRunnable = object : Runnable {
        override fun run() {
            if (isBackspacePressed) {
                actionListener?.onBackspaceClick()
                backspaceHandler.postDelayed(this, BACKSPACE_REPEAT_INTERVAL)
            }
        }
    }

    companion object {
        private const val BACKSPACE_INITIAL_DELAY = 400L // ms before repeat starts
        private const val BACKSPACE_REPEAT_INTERVAL = 50L // ms between repeats
    }

    private val micButton: ImageButton
    private val statusText: TextView
    private val waveformContainer: FrameLayout
    private val backspaceButton: ImageButton
    private val spaceButton: Button
    private val enterButton: Button
    private val settingsButton: ImageButton

    private val waveformView: WaveformView

    init {
        LayoutInflater.from(context).inflate(R.layout.voice_input_view, this, true)

        micButton = findViewById(R.id.mic_button)
        statusText = findViewById(R.id.status_text)
        waveformContainer = findViewById(R.id.waveform_container)
        backspaceButton = findViewById(R.id.backspace_button)
        spaceButton = findViewById(R.id.space_button)
        enterButton = findViewById(R.id.enter_button)
        settingsButton = findViewById(R.id.settings_button)

        // Create and add waveform view programmatically
        waveformView = WaveformView(context)
        waveformContainer.removeAllViews()
        waveformContainer.addView(waveformView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        setupButtons()
        setupEmojiButtons()
    }

    private fun setupButtons() {
        // Mic button - supports both tap-to-toggle and hold-to-record modes
        micButton.setOnTouchListener { _, event ->
            if (isProcessing) return@setOnTouchListener false

            if (holdToRecord) {
                // Hold to record mode
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startRecording()
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
            } else {
                // Tap to toggle mode
                when (event.action) {
                    MotionEvent.ACTION_UP -> {
                        if (isRecording) {
                            stopRecording()
                        } else {
                            startRecording()
                        }
                        true
                    }
                    else -> false
                }
            }
        }

        // Backspace with repeat when held
        backspaceButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isBackspacePressed = true
                    actionListener?.onBackspaceClick() // Immediate delete
                    backspaceHandler.postDelayed(backspaceRepeatRunnable, BACKSPACE_INITIAL_DELAY)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isBackspacePressed = false
                    backspaceHandler.removeCallbacks(backspaceRepeatRunnable)
                    true
                }
                else -> false
            }
        }

        spaceButton.setOnClickListener {
            actionListener?.onSpaceClick()
        }

        enterButton.setOnClickListener {
            actionListener?.onEnterClick()
        }

        settingsButton.setOnClickListener {
            actionListener?.onSettingsClick()
        }
    }

    private fun setupEmojiButtons() {
        // Set up click listeners for emoji buttons
        val emojiIds = listOf(
            R.id.emoji_1, R.id.emoji_2, R.id.emoji_3, R.id.emoji_4, R.id.emoji_5,
            R.id.emoji_6, R.id.emoji_7, R.id.emoji_8, R.id.emoji_9, R.id.emoji_10
        )

        for (id in emojiIds) {
            findViewById<Button>(id)?.setOnClickListener { view ->
                if (view is Button) {
                    actionListener?.onEmojiClick(view.text.toString())
                }
            }
        }
    }

    /**
     * Updates the emoji at the given slot (1-10).
     */
    fun setEmoji(slot: Int, emoji: String) {
        val emojiIds = listOf(
            R.id.emoji_1, R.id.emoji_2, R.id.emoji_3, R.id.emoji_4, R.id.emoji_5,
            R.id.emoji_6, R.id.emoji_7, R.id.emoji_8, R.id.emoji_9, R.id.emoji_10
        )
        if (slot in 1..10) {
            findViewById<Button>(emojiIds[slot - 1])?.text = emoji
        }
    }

    /**
     * Sets the recording mode.
     * @param holdToRecord true for hold-to-record, false for tap-to-toggle
     */
    fun setRecordingMode(holdToRecord: Boolean) {
        this.holdToRecord = holdToRecord
        updateStatusText()
    }

    private fun updateStatusText() {
        if (!isRecording && !isProcessing) {
            statusText.text = if (holdToRecord) {
                context.getString(R.string.voice_input_hold_hint)
            } else {
                context.getString(R.string.voice_input_hint)
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
     * Sets the action listener for backspace, settings, and emoji clicks.
     */
    fun setOnActionListener(listener: OnActionListener) {
        actionListener = listener
    }

    /**
     * Updates the waveform visualization with new amplitude.
     */
    fun updateWaveform(amplitude: Float) {
        waveformView.addAmplitude(amplitude)
    }

    /**
     * Shows the processing state.
     */
    fun showProcessing() {
        isProcessing = true
        isRecording = false
        statusText.text = context.getString(R.string.voice_input_processing)
        waveformContainer.visibility = View.INVISIBLE
        micButton.isEnabled = false
        micButton.isActivated = false
    }

    /**
     * Shows an error state.
     */
    fun showError(message: String? = null) {
        isProcessing = false
        isRecording = false
        statusText.text = message ?: context.getString(R.string.voice_input_error)
        waveformContainer.visibility = View.INVISIBLE
        micButton.isEnabled = true
        micButton.isActivated = false
    }

    /**
     * Shows the "no model" state.
     */
    fun showNoModel() {
        isProcessing = false
        isRecording = false
        statusText.text = context.getString(R.string.model_not_found)
        waveformContainer.visibility = View.INVISIBLE
        micButton.isEnabled = false
        micButton.isActivated = false
    }

    /**
     * Resets the view to the initial state.
     */
    fun reset() {
        isRecording = false
        isProcessing = false
        updateStatusText()
        waveformContainer.visibility = View.INVISIBLE
        waveformView.clear()
        micButton.isEnabled = true
        micButton.isActivated = false
    }

    private fun startRecording() {
        isRecording = true
        micButton.isActivated = true
        statusText.text = context.getString(R.string.voice_input_listening)
        waveformContainer.visibility = View.VISIBLE
        waveformView.clear()
        recordingListener?.onRecordingStarted()
    }

    private fun stopRecording() {
        isRecording = false
        micButton.isActivated = false
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
            strokeWidth = 3f
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        private val amplitudes = mutableListOf<Float>()
        private val maxBars = 50

        fun addAmplitude(amplitude: Float) {
            amplitudes.add(amplitude.coerceIn(0f, 1f))
            if (amplitudes.size > maxBars) {
                amplitudes.removeAt(0)
            }
            invalidate()
        }

        fun clear() {
            amplitudes.clear()
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            if (amplitudes.isEmpty()) return

            val barWidth = width.toFloat() / maxBars
            val centerY = height / 2f
            val maxAmplitude = height / 2f * 0.9f

            for ((index, amplitude) in amplitudes.withIndex()) {
                val barHeight = amplitude * maxAmplitude
                val x = index * barWidth + barWidth / 2

                canvas.drawRect(
                    x - barWidth / 4,
                    centerY - barHeight,
                    x + barWidth / 4,
                    centerY + barHeight,
                    paint
                )
            }
        }
    }
}
