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
        private const val HOTKEY_SEPARATOR = "|"
        private const val ENTRY_SEPARATOR = ";;;"
    }

    data class EmojiHotkey(
        val trigger: String,
        val emoji: String
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

        // Load defaults
        val defaults = context.resources.getStringArray(R.array.default_emoji_hotkeys)
        for (entry in defaults) {
            val parts = entry.split(HOTKEY_SEPARATOR)
            if (parts.size == 2) {
                hotkeys[parts[0].lowercase()] = parts[1]
            }
        }

        // Load custom hotkeys (override defaults)
        val customString = preferences.getString(PREF_CUSTOM_HOTKEYS, "") ?: ""
        if (customString.isNotEmpty()) {
            val entries = customString.split(ENTRY_SEPARATOR)
            for (entry in entries) {
                val parts = entry.split(HOTKEY_SEPARATOR)
                if (parts.size == 2) {
                    hotkeys[parts[0].lowercase()] = parts[1]
                }
            }
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
        saveCustomHotkeys()
    }

    /**
     * Removes an emoji hotkey.
     * @param trigger Trigger word to remove
     */
    fun removeHotkey(trigger: String) {
        hotkeys.remove(trigger.lowercase())
        saveCustomHotkeys()
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

    private fun saveCustomHotkeys() {
        val defaults = context.resources.getStringArray(R.array.default_emoji_hotkeys)
            .associate {
                val parts = it.split(HOTKEY_SEPARATOR)
                parts[0].lowercase() to parts[1]
            }

        // Only save non-default entries
        val customEntries = hotkeys.filter { (trigger, emoji) ->
            defaults[trigger] != emoji
        }.map { (trigger, emoji) ->
            "$trigger$HOTKEY_SEPARATOR$emoji"
        }.joinToString(ENTRY_SEPARATOR)

        preferences.edit()
            .putString(PREF_CUSTOM_HOTKEYS, customEntries)
            .apply()
    }
}
