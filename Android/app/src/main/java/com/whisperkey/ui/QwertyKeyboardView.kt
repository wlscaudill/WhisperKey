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
import androidx.preference.PreferenceManager
import com.whisperkey.R

/**
 * Full QWERTY keyboard view with shift, two-page symbols, and selectable profiles.
 * Built programmatically so per-row key counts can vary between modes/profiles.
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

    // 0 = letters, 1 = symbol page 1, 2 = symbol page 2
    private var symbolPage = 0

    private val profile: KeyboardProfile = KeyboardProfiles.byId(
        PreferenceManager.getDefaultSharedPreferences(context)
            .getString("keyboard_profile", "default")
    )

    private val characterRows = mutableListOf<LinearLayout>()
    private lateinit var leftToggleButton: Button
    private lateinit var symbolsButton: Button

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
        private const val KEY_MARGIN_DP = 2
        private const val TAG_BACKSPACE = "backspace"
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(context.getColor(R.color.keyboard_background))
        buildKeyboard()
        populateCharacterRows()
        updateLeftToggleButton()
        updateSymbolsButton()
    }

    private fun buildKeyboard() {
        // Build 3 character rows. Each row's content is populated by populateCharacterRows().
        for (i in 0..2) {
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
                gravity = Gravity.CENTER
                setPadding(dpToPx(4), dpToPx(2), dpToPx(4), dpToPx(2))
            }

            // Row 3 has the left toggle (shift / =\< / ?123) and backspace as fixed bookends.
            if (i == 2) {
                leftToggleButton = Button(context).apply {
                    text = "⇧"
                    layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1.2f).apply {
                        marginStart = KEY_MARGIN_DP.dp()
                        marginEnd = KEY_MARGIN_DP.dp()
                    }
                    setBackgroundResource(R.drawable.utility_button_background)
                    setTextColor(context.getColor(R.color.keyboard_text))
                    textSize = 16f
                    isAllCaps = false
                    setPadding(0, 0, 0, 0)
                    minWidth = 0
                    minHeight = 0
                    setOnClickListener { onLeftToggleClicked() }
                    setOnLongClickListener {
                        if (symbolPage == 0) {
                            toggleCapsLock()
                            true
                        } else false
                    }
                }
                row.addView(leftToggleButton)
            }

            characterRows.add(row)

            if (i == 2) {
                // Append backspace at the end of row 3 (will be added *after* the
                // character keys are populated, so we add it here as a placeholder
                // and trust populateCharacterRows to keep it in place).
                val backspaceButton = ImageButton(context).apply {
                    layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1.5f).apply {
                        marginStart = KEY_MARGIN_DP.dp()
                        marginEnd = KEY_MARGIN_DP.dp()
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
                    tag = TAG_BACKSPACE
                }
                row.addView(backspaceButton)
            }

            addView(row)
        }

        // Bottom row: ?123/ABC, mic, comma, space, period, enter
        val bottomRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            gravity = Gravity.CENTER
            setPadding(dpToPx(4), dpToPx(2), dpToPx(4), dpToPx(8))
        }

        val bottomHeight = dpToPx(48)
        val bottomMargin = dpToPx(3)

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
            setOnClickListener { onSymbolsButtonClicked() }
        }
        bottomRow.addView(symbolsButton)

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

    /**
     * Clear and rebuild the character keys in each row based on the current mode.
     * Row 3's left toggle and backspace are preserved as fixed bookends.
     */
    private fun populateCharacterRows() {
        val rows = when (symbolPage) {
            1 -> profile.symbolPage1Rows
            2 -> profile.symbolPage2Rows
            else -> profile.letterRows
        }

        for (i in 0..2) {
            val row = characterRows[i]
            val keys = rows[i]

            // Row 1 (middle, a-l in letters) gets extra side padding when it has 9 keys
            // so they stay centered like Gboard. With 10 keys, drop back to the standard
            // narrow padding so keys aren't squeezed.
            val hPad = if (i == 1 && keys.size <= 9) dpToPx(16) else dpToPx(4)
            row.setPadding(hPad, dpToPx(2), hPad, dpToPx(2))

            if (i == 2) {
                // Remove only the character keys (everything between the leftToggle and backspace).
                while (row.childCount > 2) {
                    row.removeViewAt(1)
                }
                // Insert character buttons before the backspace (which is the last view).
                for ((idx, key) in keys.withIndex()) {
                    row.addView(makeCharacterButton(key), 1 + idx)
                }
            } else {
                row.removeAllViews()
                for (key in keys) {
                    row.addView(makeCharacterButton(key))
                }
            }
        }
    }

    private fun makeCharacterButton(key: String): Button {
        return Button(context).apply {
            text = displayLabel(key)
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = KEY_MARGIN_DP.dp()
                marginEnd = KEY_MARGIN_DP.dp()
            }
            setBackgroundResource(R.drawable.keyboard_key_background)
            setTextColor(context.getColor(R.color.keyboard_text))
            textSize = 16f
            isAllCaps = false
            setPadding(0, 0, 0, 0)
            minWidth = 0
            minHeight = 0
            setOnClickListener {
                listener?.onTextInput(key.let { k -> if (symbolPage == 0 && isShifted) k.uppercase() else k })
                if (symbolPage == 0 && isShifted && !isCapsLock) {
                    isShifted = false
                    refreshLetterCaseLabels()
                }
            }
        }
    }

    private fun displayLabel(key: String): String =
        if (symbolPage == 0 && (isShifted || isCapsLock)) key.uppercase() else key

    private fun refreshLetterCaseLabels() {
        if (symbolPage != 0) return
        for (i in 0..2) {
            val row = characterRows[i]
            val start = if (i == 2) 1 else 0
            val end = if (i == 2) row.childCount - 1 else row.childCount
            for (j in start until end) {
                val btn = row.getChildAt(j) as? Button ?: continue
                val raw = profile.letterRows[i].getOrNull(j - start) ?: continue
                btn.text = displayLabel(raw)
            }
        }
    }

    private fun onLeftToggleClicked() {
        when (symbolPage) {
            0 -> toggleShift()
            1 -> { symbolPage = 2; refreshAfterModeChange() }
            2 -> { symbolPage = 1; refreshAfterModeChange() }
        }
    }

    private fun onSymbolsButtonClicked() {
        symbolPage = if (symbolPage == 0) 1 else 0
        isShifted = false
        isCapsLock = false
        refreshAfterModeChange()
    }

    private fun refreshAfterModeChange() {
        populateCharacterRows()
        updateLeftToggleButton()
        updateSymbolsButton()
    }

    private fun toggleShift() {
        if (symbolPage != 0) return
        isShifted = !isShifted
        isCapsLock = false
        refreshLetterCaseLabels()
        updateLeftToggleButton()
    }

    private fun toggleCapsLock() {
        if (symbolPage != 0) return
        isCapsLock = !isCapsLock
        isShifted = isCapsLock
        refreshLetterCaseLabels()
        updateLeftToggleButton()
    }

    private fun updateLeftToggleButton() {
        when (symbolPage) {
            0 -> {
                leftToggleButton.text = if (isCapsLock) "⇪" else "⇧"
                leftToggleButton.isActivated = isShifted || isCapsLock
                leftToggleButton.textSize = 18f
            }
            1 -> {
                leftToggleButton.text = "=\\<"
                leftToggleButton.isActivated = false
                leftToggleButton.textSize = 12f
            }
            2 -> {
                leftToggleButton.text = "?123"
                leftToggleButton.isActivated = false
                leftToggleButton.textSize = 12f
            }
        }
    }

    private fun updateSymbolsButton() {
        symbolsButton.text = if (symbolPage == 0) "?123" else "ABC"
    }

    fun setOnKeyboardActionListener(listener: OnKeyboardActionListener) {
        this.listener = listener
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
    private fun Int.dp(): Int = dpToPx(this)
}
