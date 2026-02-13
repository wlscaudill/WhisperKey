package com.whisperkey

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/**
 * Manages emoji hotkeys for voice-to-emoji conversion.
 */
class EmojiManager(private val context: Context) {

    companion object {
        private const val PREF_CUSTOM_HOTKEYS = "custom_emoji_hotkeys"
        private const val PREF_KEYBOARD_EMOJIS = "keyboard_emojis"
        private const val HOTKEY_SEPARATOR = "|"
        private const val ENTRY_SEPARATOR = ";;;"
        private const val KEYBOARD_EMOJI_COUNT = 10

        // Default keyboard emojis
        val DEFAULT_KEYBOARD_EMOJIS = listOf(
            "👍", "❤️", "😂", "😊", "🙏",
            "😢", "😮", "🎉", "🔥", "✅"
        )
    }

    data class EmojiHotkey(
        val trigger: String,
        val emoji: String,
        val showOnKeyboard: Boolean = false
    )

    private val preferences: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(context)
    }

    private var hotkeys: MutableMap<String, String> = mutableMapOf()
    private var isEnabled: Boolean = true

    init {
        loadHotkeys()
    }

    /**
     * Loads emoji hotkeys from preferences and defaults.
     */
    private fun loadHotkeys() {
        hotkeys.clear()

        val customString = preferences.getString(PREF_CUSTOM_HOTKEYS, null)
        if (customString != null && customString.isNotEmpty()) {
            // User has saved settings — use those exclusively
            val entries = customString.split(ENTRY_SEPARATOR)
            for (entry in entries) {
                val parts = entry.split(HOTKEY_SEPARATOR)
                if (parts.size == 2) {
                    hotkeys[parts[0].lowercase()] = parts[1]
                }
            }
        } else {
            // No saved settings — use defaults
            hotkeys.putAll(getDefaultHotkeys())
        }

        isEnabled = preferences.getBoolean("emoji_hotkeys_enabled", true)
    }

    /**
     * Processes text and replaces trigger words with emojis.
     * @param text Input text to process
     * @return Text with trigger words replaced by emojis
     */
    fun processText(text: String): String {
        if (!isEnabled) return text

        var result = text
        for ((trigger, emoji) in hotkeys) {
            // Match whole words only (case-insensitive)
            val pattern = "\\b${Regex.escape(trigger)}\\b".toRegex(RegexOption.IGNORE_CASE)
            result = result.replace(pattern, emoji)
        }
        return result
    }

    /**
     * Gets all current emoji hotkeys.
     */
    fun getHotkeys(): List<EmojiHotkey> {
        return hotkeys.map { (trigger, emoji) -> EmojiHotkey(trigger, emoji) }
            .sortedBy { it.trigger }
    }

    /**
     * Adds or updates an emoji hotkey.
     * @param trigger Trigger word
     * @param emoji Emoji to insert
     */
    fun setHotkey(trigger: String, emoji: String) {
        hotkeys[trigger.lowercase()] = emoji
        saveHotkeys()
    }

    /**
     * Removes an emoji hotkey.
     * @param trigger Trigger word to remove
     */
    fun removeHotkey(trigger: String) {
        hotkeys.remove(trigger.lowercase())
        saveHotkeys()
    }

    /**
     * Checks if a trigger word has an emoji mapping.
     * @param trigger Trigger word to check
     */
    fun hasHotkey(trigger: String): Boolean {
        return hotkeys.containsKey(trigger.lowercase())
    }

    /**
     * Gets the emoji for a trigger word.
     * @param trigger Trigger word
     * @return Emoji or null if not found
     */
    fun getEmoji(trigger: String): String? {
        return hotkeys[trigger.lowercase()]
    }

    /**
     * Enables or disables emoji hotkeys.
     * @param enabled Whether hotkeys should be enabled
     */
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        preferences.edit()
            .putBoolean("emoji_hotkeys_enabled", enabled)
            .apply()
    }

    /**
     * Checks if emoji hotkeys are enabled.
     */
    fun isEnabled(): Boolean = isEnabled

    /**
     * Resets hotkeys to defaults.
     */
    fun resetToDefaults() {
        preferences.edit()
            .remove(PREF_CUSTOM_HOTKEYS)
            .apply()
        loadHotkeys()
    }

    private fun getDefaultHotkeys(): Map<String, String> {
        return context.resources.getStringArray(R.array.default_emoji_hotkeys)
            .mapNotNull { entry ->
                val parts = entry.split(HOTKEY_SEPARATOR)
                if (parts.size == 2) parts[0].lowercase() to parts[1] else null
            }.toMap()
    }

    private fun saveHotkeys() {
        val entries = hotkeys.map { (trigger, emoji) ->
            "$trigger$HOTKEY_SEPARATOR$emoji"
        }.joinToString(ENTRY_SEPARATOR)

        preferences.edit()
            .putString(PREF_CUSTOM_HOTKEYS, entries)
            .apply()
    }

    // ==================== Keyboard Emoji Methods ====================

    /**
     * Gets the list of emojis to display on the keyboard.
     * @return List of 10 emojis for keyboard display
     */
    fun getKeyboardEmojis(): List<String> {
        val saved = preferences.getString(PREF_KEYBOARD_EMOJIS, null)
        return if (saved != null) {
            val emojis = saved.split(ENTRY_SEPARATOR)
            // Ensure we always have exactly 10 emojis
            if (emojis.size == KEYBOARD_EMOJI_COUNT) {
                emojis
            } else {
                DEFAULT_KEYBOARD_EMOJIS
            }
        } else {
            DEFAULT_KEYBOARD_EMOJIS
        }
    }

    /**
     * Sets an emoji at a specific keyboard position.
     * @param position Position (0-9)
     * @param emoji Emoji to set
     */
    fun setKeyboardEmoji(position: Int, emoji: String) {
        if (position !in 0 until KEYBOARD_EMOJI_COUNT) return
        val emojis = getKeyboardEmojis().toMutableList()
        emojis[position] = emoji
        saveKeyboardEmojis(emojis)
    }

    /**
     * Sets all keyboard emojis at once.
     * @param emojis List of 10 emojis
     */
    fun setKeyboardEmojis(emojis: List<String>) {
        if (emojis.size != KEYBOARD_EMOJI_COUNT) return
        saveKeyboardEmojis(emojis)
    }

    /**
     * Resets keyboard emojis to defaults.
     */
    fun resetKeyboardEmojis() {
        preferences.edit()
            .remove(PREF_KEYBOARD_EMOJIS)
            .apply()
    }

    private fun saveKeyboardEmojis(emojis: List<String>) {
        preferences.edit()
            .putString(PREF_KEYBOARD_EMOJIS, emojis.joinToString(ENTRY_SEPARATOR))
            .apply()
    }

    /**
     * Gets a list of all unique emojis from hotkeys for keyboard selection.
     */
    fun getAvailableEmojis(): List<String> {
        return hotkeys.values.distinct().sorted()
    }
}
