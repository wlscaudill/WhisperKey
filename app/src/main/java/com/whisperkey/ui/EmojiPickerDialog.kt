package com.whisperkey.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.whisperkey.EmojiManager
import com.whisperkey.R

/**
 * Dialog for managing emoji hotkeys.
 */
class EmojiPickerDialog(context: Context) : Dialog(context) {

    private lateinit var emojiManager: EmojiManager
    private lateinit var adapter: EmojiHotkeyAdapter
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_emoji_picker)

        emojiManager = EmojiManager(context)

        setupViews()
        loadHotkeys()
    }

    private fun setupViews() {
        recyclerView = findViewById(R.id.emoji_recycler)
        recyclerView.layoutManager = LinearLayoutManager(context)

        adapter = EmojiHotkeyAdapter(
            onEdit = { hotkey -> showEditDialog(hotkey) },
            onDelete = { hotkey -> confirmDelete(hotkey) }
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
        adapter.submitList(emojiManager.getHotkeys())
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
     * Adapter for emoji hotkey list.
     */
    private class EmojiHotkeyAdapter(
        private val onEdit: (EmojiManager.EmojiHotkey) -> Unit,
        private val onDelete: (EmojiManager.EmojiHotkey) -> Unit
    ) : RecyclerView.Adapter<EmojiHotkeyAdapter.ViewHolder>() {

        private var items: List<EmojiManager.EmojiHotkey> = emptyList()

        fun submitList(list: List<EmojiManager.EmojiHotkey>) {
            items = list
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

            fun bind(hotkey: EmojiManager.EmojiHotkey) {
                triggerText.text = hotkey.trigger
                emojiText.text = hotkey.emoji

                editButton.setOnClickListener { onEdit(hotkey) }
                deleteButton.setOnClickListener { onDelete(hotkey) }
            }
        }
    }
}
