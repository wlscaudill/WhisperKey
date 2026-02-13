package com.whisperkey.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.whisperkey.EmojiManager
import com.whisperkey.R

/**
 * Dialog for managing emoji hotkeys and keyboard emojis.
 */
class EmojiPickerDialog(context: Context) : Dialog(context) {

    private lateinit var emojiManager: EmojiManager
    private lateinit var adapter: EmojiHotkeyAdapter
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_emoji_picker)

        // Make dialog full width
        window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )

        emojiManager = EmojiManager(context)

        setupViews()
        loadHotkeys()
    }

    private fun setupViews() {
        recyclerView = findViewById(R.id.emoji_recycler)
        recyclerView.layoutManager = LinearLayoutManager(context)

        adapter = EmojiHotkeyAdapter(
            onEdit = { hotkey -> showEditDialog(hotkey) },
            onDelete = { hotkey -> confirmDelete(hotkey) },
            onToggleKeyboard = { hotkey -> toggleKeyboardEmoji(hotkey) },
            keyboardEmojis = emojiManager.getKeyboardEmojis()
        )
        recyclerView.adapter = adapter

        findViewById<View>(R.id.add_button).setOnClickListener {
            showEditDialog(null)
        }

        findViewById<View>(R.id.close_button).setOnClickListener {
            dismiss()
        }
    }

    private fun loadHotkeys() {
        adapter.updateKeyboardEmojis(emojiManager.getKeyboardEmojis())
        adapter.submitList(emojiManager.getHotkeys())
    }

    private fun toggleKeyboardEmoji(hotkey: EmojiManager.EmojiHotkey) {
        val currentEmojis = emojiManager.getKeyboardEmojis().toMutableList()

        if (currentEmojis.contains(hotkey.emoji)) {
            // Remove from keyboard - find its position and replace with a default
            val index = currentEmojis.indexOf(hotkey.emoji)
            val defaults = EmojiManager.DEFAULT_KEYBOARD_EMOJIS
            val replacement = defaults.firstOrNull { !currentEmojis.contains(it) } ?: "😀"
            currentEmojis[index] = replacement
        } else {
            // Add to keyboard - find first slot not already used by a hotkey emoji
            // or replace the last one
            val hotkeyEmojis = emojiManager.getHotkeys().map { it.emoji }
            val freeIndex = currentEmojis.indexOfFirst { !hotkeyEmojis.contains(it) }
            val indexToReplace = if (freeIndex >= 0) freeIndex else 9
            currentEmojis[indexToReplace] = hotkey.emoji
        }

        emojiManager.setKeyboardEmojis(currentEmojis)
        adapter.updateKeyboardEmojis(currentEmojis)
    }

    private fun showEditDialog(hotkey: EmojiManager.EmojiHotkey?) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_edit_emoji, null)
        val triggerInput = view.findViewById<EditText>(R.id.trigger_input)
        val emojiInput = view.findViewById<EditText>(R.id.emoji_input)

        hotkey?.let {
            triggerInput.setText(it.trigger)
            emojiInput.setText(it.emoji)
        }

        AlertDialog.Builder(context)
            .setTitle(if (hotkey == null) R.string.emoji_hotkey_add else R.string.save)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val trigger = triggerInput.text.toString().trim()
                val emoji = emojiInput.text.toString().trim()

                if (trigger.isNotEmpty() && emoji.isNotEmpty()) {
                    // Remove old entry if editing
                    hotkey?.let { emojiManager.removeHotkey(it.trigger) }
                    emojiManager.setHotkey(trigger, emoji)
                    loadHotkeys()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(hotkey: EmojiManager.EmojiHotkey) {
        AlertDialog.Builder(context)
            .setTitle(R.string.delete)
            .setMessage("Delete hotkey '${hotkey.trigger}'?")
            .setPositiveButton(R.string.delete) { _, _ ->
                emojiManager.removeHotkey(hotkey.trigger)
                loadHotkeys()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * Adapter for emoji hotkey list with keyboard toggle.
     */
    private class EmojiHotkeyAdapter(
        private val onEdit: (EmojiManager.EmojiHotkey) -> Unit,
        private val onDelete: (EmojiManager.EmojiHotkey) -> Unit,
        private val onToggleKeyboard: (EmojiManager.EmojiHotkey) -> Unit,
        private var keyboardEmojis: List<String>
    ) : RecyclerView.Adapter<EmojiHotkeyAdapter.ViewHolder>() {

        private var items: List<EmojiManager.EmojiHotkey> = emptyList()

        fun submitList(list: List<EmojiManager.EmojiHotkey>) {
            items = list
            notifyDataSetChanged()
        }

        fun updateKeyboardEmojis(emojis: List<String>) {
            keyboardEmojis = emojis
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_emoji_hotkey, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val triggerText: TextView = itemView.findViewById(R.id.trigger_text)
            private val emojiText: TextView = itemView.findViewById(R.id.emoji_text)
            private val editButton: ImageButton = itemView.findViewById(R.id.edit_button)
            private val deleteButton: ImageButton = itemView.findViewById(R.id.delete_button)
            private val keyboardCheckbox: CheckBox? = itemView.findViewById(R.id.keyboard_checkbox)

            fun bind(hotkey: EmojiManager.EmojiHotkey) {
                emojiText.text = hotkey.emoji
                triggerText.text = "say \"${hotkey.trigger}\""

                editButton.setOnClickListener { onEdit(hotkey) }
                deleteButton.setOnClickListener { onDelete(hotkey) }

                keyboardCheckbox?.apply {
                    isChecked = keyboardEmojis.contains(hotkey.emoji)
                    setOnClickListener { onToggleKeyboard(hotkey) }
                }
            }
        }
    }
}
