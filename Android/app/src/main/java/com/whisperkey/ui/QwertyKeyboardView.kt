package com.whisperkey.ui

import android.content.Context
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import com.whisperkey.R

/**
 * Full QWERTY keyboard view with shift and symbols layers.
 * Built programmatically for easy key label switching.
 */
class QwertyKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    interface OnKeyboardActionListener {
        fun onTextInput(text: String)
        fun onDelete()
        fun onEnter()
        fun onSwitchToVoice()
    }

    private var listener: OnKeyboardActionListener? = null
    private var isShifted = false
    private var isCapsLock = false
    private var isSymbols = false

    private val letterRows = listOf(
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf("z", "x", "c", "v", "b", "n", "m")
    )

    private val symbolRows = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("@", "#", "$", "_", "&", "-", "+", "(", ")"),
        listOf("*", "\"", "'", ":", ";", "!", "?")
    )

    private val keyButtons = mutableListOf<MutableList<Button>>()
    private lateinit var shiftButton: Button
    private lateinit var symbolsButton: Button

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
        orientation = VERTICAL
        setBackgroundColor(context.getColor(R.color.keyboard_background))
        buildKeyboard()
    }

    private fun buildKeyboard() {
        val keyMargin = dpToPx(2)

        // Build 3 rows of character keys
        for (i in letterRows.indices) {
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
                gravity = Gravity.CENTER
                val hPad = if (i == 1) dpToPx(16) else dpToPx(4)
                setPadding(hPad, dpToPx(2), hPad, dpToPx(2))
            }

            val buttons = mutableListOf<Button>()

            // Row 3: add shift button before letter keys
            if (i == 2) {
                shiftButton = Button(context).apply {
                    text = "\u21E7" // ⇧
                    layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1.2f).apply {
                        marginStart = keyMargin
                        marginEnd = keyMargin
                    }
                    setBackgroundResource(R.drawable.utility_button_background)
                    setTextColor(context.getColor(R.color.keyboard_text))
                    textSize = 18f
                    setPadding(0, 0, 0, 0)
                    minWidth = 0
                    minHeight = 0
                    setOnClickListener { toggleShift() }
                    setOnLongClickListener { toggleCapsLock(); true }
                }
                row.addView(shiftButton)
            }

            // Add character keys
            for (key in letterRows[i]) {
                val btn = Button(context).apply {
                    text = key
                    layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                        marginStart = keyMargin
                        marginEnd = keyMargin
                    }
                    setBackgroundResource(R.drawable.keyboard_key_background)
                    setTextColor(context.getColor(R.color.keyboard_text))
                    textSize = 16f
                    isAllCaps = false
                    setPadding(0, 0, 0, 0)
                    minWidth = 0
                    minHeight = 0
                    setOnClickListener {
                        val char = (it as Button).text.toString()
                        listener?.onTextInput(char)
                        if (isShifted && !isCapsLock) {
                            isShifted = false
                            updateKeys()
                        }
                    }
                }
                buttons.add(btn)
                row.addView(btn)
            }

            // Row 3: add backspace button after letter keys
            if (i == 2) {
                val backspaceButton = ImageButton(context).apply {
                    layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1.5f).apply {
                        marginStart = keyMargin
                        marginEnd = keyMargin
                    }
                    setBackgroundResource(R.drawable.utility_button_background)
                    setImageResource(R.drawable.ic_backspace)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
                    contentDescription = context.getString(R.string.backspace_description)

                    setOnTouchListener { _, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                isBackspacePressed = true
                                listener?.onDelete()
                                backspaceHandler.postDelayed(
                                    backspaceRepeatRunnable,
                                    BACKSPACE_INITIAL_DELAY
                                )
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
                }
                row.addView(backspaceButton)
            }

            keyButtons.add(buttons)
            addView(row)
        }

        // Bottom row: ?123, mic, comma, space, period, enter
        val bottomRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            gravity = Gravity.CENTER
            setPadding(dpToPx(4), dpToPx(2), dpToPx(4), dpToPx(8))
        }

        val bottomHeight = dpToPx(48)
        val bottomMargin = dpToPx(3)

        // ?123 / ABC toggle
        symbolsButton = Button(context).apply {
            text = "?123"
            layoutParams = LayoutParams(0, bottomHeight, 1.2f).apply {
                marginStart = bottomMargin
                marginEnd = bottomMargin
            }
            setBackgroundResource(R.drawable.utility_button_background)
            setTextColor(context.getColor(R.color.keyboard_text))
            textSize = 12f
            isAllCaps = false
            setPadding(0, 0, 0, 0)
            minWidth = 0
            minHeight = 0
            setOnClickListener { toggleSymbols() }
        }
        bottomRow.addView(symbolsButton)

        // Voice mode button
        val voiceButton = ImageButton(context).apply {
            layoutParams = LayoutParams(0, bottomHeight, 1f).apply {
                marginStart = bottomMargin
                marginEnd = bottomMargin
            }
            setBackgroundResource(R.drawable.utility_button_background)
            setImageResource(R.drawable.ic_mic)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10))
            contentDescription = context.getString(R.string.mic_button_description)
            imageTintList = ColorStateList.valueOf(context.getColor(R.color.icon_tint))
            setOnClickListener { listener?.onSwitchToVoice() }
        }
        bottomRow.addView(voiceButton)

        // Comma
        val commaButton = Button(context).apply {
            text = ","
            layoutParams = LayoutParams(0, bottomHeight, 1f).apply {
                marginStart = bottomMargin
                marginEnd = bottomMargin
            }
            setBackgroundResource(R.drawable.utility_button_background)
            setTextColor(context.getColor(R.color.keyboard_text))
            textSize = 16f
            setPadding(0, 0, 0, 0)
            minWidth = 0
            minHeight = 0
            setOnClickListener { listener?.onTextInput(",") }
        }
        bottomRow.addView(commaButton)

        // Space
        val spaceButton = Button(context).apply {
            text = context.getString(R.string.space_button)
            layoutParams = LayoutParams(0, bottomHeight, 2.8f).apply {
                marginStart = bottomMargin
                marginEnd = bottomMargin
            }
            setBackgroundResource(R.drawable.utility_button_background)
            setTextColor(context.getColor(R.color.keyboard_text))
            textSize = 12f
            isAllCaps = false
            setPadding(0, 0, 0, 0)
            minWidth = 0
            minHeight = 0
            setOnClickListener { listener?.onTextInput(" ") }
        }
        bottomRow.addView(spaceButton)

        // Period
        val periodButton = Button(context).apply {
            text = "."
            layoutParams = LayoutParams(0, bottomHeight, 1f).apply {
                marginStart = bottomMargin
                marginEnd = bottomMargin
            }
            setBackgroundResource(R.drawable.utility_button_background)
            setTextColor(context.getColor(R.color.keyboard_text))
            textSize = 16f
            setPadding(0, 0, 0, 0)
            minWidth = 0
            minHeight = 0
            setOnClickListener { listener?.onTextInput(".") }
        }
        bottomRow.addView(periodButton)

        // Enter - always inserts newline on QWERTY keyboard
        val enterButton = Button(context).apply {
            text = context.getString(R.string.enter_button)
            layoutParams = LayoutParams(0, bottomHeight, 1.5f).apply {
                marginStart = bottomMargin
                marginEnd = bottomMargin
            }
            setBackgroundResource(R.drawable.utility_button_background)
            setTextColor(context.getColor(R.color.keyboard_text))
            textSize = 12f
            isAllCaps = false
            setPadding(0, 0, 0, 0)
            minWidth = 0
            minHeight = 0
            setOnClickListener { listener?.onEnter() }
        }
        bottomRow.addView(enterButton)

        addView(bottomRow)
    }

    private fun toggleShift() {
        if (isSymbols) return
        isShifted = !isShifted
        isCapsLock = false
        updateKeys()
    }

    private fun toggleCapsLock() {
        if (isSymbols) return
        isCapsLock = !isCapsLock
        isShifted = isCapsLock
        updateKeys()
    }

    private fun toggleSymbols() {
        isSymbols = !isSymbols
        isShifted = false
        isCapsLock = false
        updateKeys()
        symbolsButton.text = if (isSymbols) "ABC" else "?123"
        shiftButton.visibility = if (isSymbols) INVISIBLE else VISIBLE
    }

    private fun updateKeys() {
        val rows = if (isSymbols) symbolRows else letterRows
        for (i in rows.indices) {
            for (j in rows[i].indices) {
                if (j < keyButtons[i].size) {
                    val key = rows[i][j]
                    keyButtons[i][j].text = if (isShifted && !isSymbols) key.uppercase() else key
                }
            }
        }
        // Update shift button appearance
        if (!isSymbols) {
            shiftButton.text = if (isCapsLock) "\u21EA" else "\u21E7" // ⇪ or ⇧
            shiftButton.isActivated = isShifted || isCapsLock
        }
    }

    fun setOnKeyboardActionListener(listener: OnKeyboardActionListener) {
        this.listener = listener
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
