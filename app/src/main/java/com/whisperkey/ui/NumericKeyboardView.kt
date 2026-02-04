package com.whisperkey.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import com.whisperkey.R

/**
 * Numeric 10-key keyboard view.
 * Provides a simple number pad with mode switching to voice and QWERTY.
 */
class NumericKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    interface OnKeyboardActionListener {
        fun onTextInput(text: String)
        fun onDelete()
        fun onEnter()
        fun onSwitchToVoice()
        fun onSwitchToQwerty()
    }

    private var listener: OnKeyboardActionListener? = null

    // Backspace repeat handling
    private val backspaceHandler = Handler(Looper.getMainLooper())
    private var isBackspacePressed = false
    private val backspaceRepeatRunnable = object : Runnable {
        override fun run() {
            if (isBackspacePressed) {
                listener?.onDelete()
                backspaceHandler.postDelayed(this, BACKSPACE_REPEAT_INTERVAL)
            }
        }
    }

    companion object {
        private const val BACKSPACE_INITIAL_DELAY = 400L
        private const val BACKSPACE_REPEAT_INTERVAL = 50L
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.numeric_keyboard_view, this, true)
        setupButtons()
    }

    private fun setupButtons() {
        // Number keys
        val numberKeys = listOf(
            R.id.key_0, R.id.key_1, R.id.key_2, R.id.key_3, R.id.key_4,
            R.id.key_5, R.id.key_6, R.id.key_7, R.id.key_8, R.id.key_9
        )
        for (id in numberKeys) {
            findViewById<Button>(id).setOnClickListener { view ->
                listener?.onTextInput((view as Button).text.toString())
            }
        }

        // Dot and comma
        findViewById<Button>(R.id.key_dot).setOnClickListener {
            listener?.onTextInput(".")
        }
        findViewById<Button>(R.id.key_comma).setOnClickListener {
            listener?.onTextInput(",")
        }

        // ABC button → QWERTY
        findViewById<Button>(R.id.btn_abc).setOnClickListener {
            listener?.onSwitchToQwerty()
        }

        // Voice button → Voice mode
        findViewById<ImageButton>(R.id.btn_voice).setOnClickListener {
            listener?.onSwitchToVoice()
        }

        // Space
        findViewById<Button>(R.id.btn_space).setOnClickListener {
            listener?.onTextInput(" ")
        }

        // Backspace with repeat
        findViewById<ImageButton>(R.id.btn_backspace).setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isBackspacePressed = true
                    listener?.onDelete()
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

        // Enter
        findViewById<Button>(R.id.btn_enter).setOnClickListener {
            listener?.onEnter()
        }
    }

    fun setOnKeyboardActionListener(listener: OnKeyboardActionListener) {
        this.listener = listener
    }
}
