package com.whisperkey

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

/**
 * Settings activity for WhisperKey configuration.
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    /**
     * Fragment for displaying preferences.
     */
    class SettingsFragment : PreferenceFragmentCompat() {

        private lateinit var modelManager: ModelManager

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            modelManager = ModelManager(requireContext())

            setupModelPreferences()
            setupEmojiPreferences()
            setupAboutPreferences()
        }

        private fun setupModelPreferences() {
            findPreference<ListPreference>("model_size")?.apply {
                setOnPreferenceChangeListener { _, newValue ->
                    updateModelDownloadSummary(newValue as String)
                    true
                }
            }

            findPreference<Preference>("download_model")?.apply {
                setOnPreferenceClickListener {
                    downloadSelectedModel()
                    true
                }
                updateModelDownloadSummary(
                    preferenceManager.sharedPreferences?.getString("model_size", "base") ?: "base"
                )
            }
        }

        private fun updateModelDownloadSummary(modelSize: String) {
            findPreference<Preference>("download_model")?.summary = if (modelManager.isModelDownloaded(modelSize)) {
                getString(R.string.model_download_complete)
            } else {
                getString(R.string.pref_download_model_summary)
            }
        }

        private fun downloadSelectedModel() {
            val modelSize = preferenceManager.sharedPreferences?.getString("model_size", "base") ?: "base"

            findPreference<Preference>("download_model")?.summary = getString(R.string.model_downloading)

            modelManager.downloadModel(modelSize) { success ->
                activity?.runOnUiThread {
                    findPreference<Preference>("download_model")?.summary = if (success) {
                        getString(R.string.model_download_complete)
                    } else {
                        getString(R.string.model_download_failed)
                    }
                }
            }
        }

        private fun setupEmojiPreferences() {
            findPreference<Preference>("manage_emoji_hotkeys")?.setOnPreferenceClickListener {
                showEmojiHotkeyManager()
                true
            }
        }

        private fun showEmojiHotkeyManager() {
            // Open emoji hotkey management dialog
            com.whisperkey.ui.EmojiPickerDialog(requireContext()).show()
        }

        private fun setupAboutPreferences() {
            findPreference<Preference>("app_version")?.summary = try {
                val packageInfo = requireContext().packageManager.getPackageInfo(
                    requireContext().packageName, 0
                )
                "${packageInfo.versionName} (${packageInfo.longVersionCode})"
            } catch (e: Exception) {
                "Unknown"
            }

            findPreference<Preference>("open_source_licenses")?.setOnPreferenceClickListener {
                showLicenses()
                true
            }
        }

        private fun showLicenses() {
            // Show open source licenses dialog
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.pref_licenses_title)
                .setMessage("whisper.cpp - MIT License\nOkHttp - Apache 2.0 License")
                .setPositiveButton(R.string.ok, null)
                .show()
        }
    }
}
