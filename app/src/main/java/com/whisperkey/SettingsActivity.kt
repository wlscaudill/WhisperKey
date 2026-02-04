package com.whisperkey

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.whisperkey.util.Logger
import java.io.File

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
            setupStoragePreferences()
            setupEmojiPreferences()
            setupAboutPreferences()
            setupDebugPreferences()
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

            findPreference<Preference>("delete_model")?.apply {
                setOnPreferenceClickListener {
                    confirmDeleteModel()
                    true
                }
            }
        }

        private fun setupStoragePreferences() {
            findPreference<Preference>("storage_location")?.apply {
                updateStorageSummary()
                setOnPreferenceClickListener {
                    showStorageOptions()
                    true
                }
            }
        }

        private fun updateStorageSummary() {
            findPreference<Preference>("storage_location")?.summary = modelManager.getStorageDisplayName()
        }

        private fun showStorageOptions() {
            val isUsingSdCard = modelManager.isUsingSdCard()
            val sdCardAvailable = modelManager.isSdCardAvailable()

            val options = mutableListOf<String>()
            val optionIds = mutableListOf<Int>()

            // Option 0: Internal storage
            if (isUsingSdCard) {
                options.add("Internal App Storage")
            } else {
                options.add("Internal App Storage (current)")
            }
            optionIds.add(0)

            // Option 1: SD Card (if available)
            if (sdCardAvailable) {
                if (isUsingSdCard) {
                    options.add("SD Card (current)")
                } else {
                    options.add("SD Card")
                }
                optionIds.add(1)
            }

            AlertDialog.Builder(requireContext())
                .setTitle(R.string.pref_storage_location_title)
                .setItems(options.toTypedArray()) { _, which ->
                    when (optionIds[which]) {
                        0 -> {
                            // Internal storage
                            if (isUsingSdCard) {
                                modelManager.setUseSdCard(false)
                                updateStorageSummary()
                                Toast.makeText(requireContext(), "Storage set to Internal", Toast.LENGTH_SHORT).show()
                                Logger.i("Settings", "Switched to internal storage")
                            }
                        }
                        1 -> {
                            // SD Card
                            if (!isUsingSdCard) {
                                modelManager.setUseSdCard(true)
                                updateStorageSummary()
                                val sdPath = modelManager.getSdCardModelsDirectory()?.absolutePath
                                Toast.makeText(requireContext(), "Storage set to SD Card", Toast.LENGTH_SHORT).show()
                                Logger.i("Settings", "Switched to SD card storage: $sdPath")
                            }
                        }
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        private fun updateModelDownloadSummary(modelSize: String) {
            val isDownloaded = modelManager.isModelDownloaded(modelSize)
            findPreference<Preference>("download_model")?.summary = if (isDownloaded) {
                val path = modelManager.getDownloadedModelPath(modelSize)
                val location = if (path?.contains("/storage/emulated/0/Android/data") == true ||
                    path?.startsWith(requireContext().filesDir.absolutePath) == true) {
                    "Internal"
                } else {
                    "Custom folder"
                }
                "${getString(R.string.model_download_complete)} ($location)"
            } else {
                getString(R.string.pref_download_model_summary)
            }

            findPreference<Preference>("delete_model")?.isEnabled = isDownloaded
        }

        private fun downloadSelectedModel() {
            val modelSize = preferenceManager.sharedPreferences?.getString("model_size", "base") ?: "base"

            Logger.i("Settings", "=== Starting Model Download ===")
            Logger.i("Settings", "Model: $modelSize")
            Logger.i("Settings", "Storage: ${modelManager.getStorageDisplayName()}")
            Logger.i("Settings", "Target path: ${modelManager.getModelDownloadPath(modelSize)}")

            findPreference<Preference>("download_model")?.summary = getString(R.string.model_downloading)

            modelManager.downloadModel(modelSize) { success ->
                activity?.runOnUiThread {
                    if (success) {
                        Logger.i("Settings", "Download completed successfully")
                        Toast.makeText(requireContext(), R.string.model_download_complete, Toast.LENGTH_SHORT).show()
                    } else {
                        Logger.e("Settings", "Download failed")
                        Toast.makeText(requireContext(), R.string.model_download_failed, Toast.LENGTH_SHORT).show()
                    }
                    updateModelDownloadSummary(modelSize)
                }
            }
        }

        private fun confirmDeleteModel() {
            val modelSize = preferenceManager.sharedPreferences?.getString("model_size", "base") ?: "base"

            if (!modelManager.isModelDownloaded(modelSize)) {
                Toast.makeText(requireContext(), R.string.model_not_downloaded, Toast.LENGTH_SHORT).show()
                return
            }

            AlertDialog.Builder(requireContext())
                .setTitle(R.string.pref_delete_model_title)
                .setMessage(R.string.confirm_delete_model)
                .setPositiveButton(R.string.delete) { _, _ ->
                    deleteModel(modelSize)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        private fun deleteModel(modelSize: String) {
            Logger.i("Settings", "Deleting model: $modelSize")
            val success = modelManager.deleteModel(modelSize)
            if (success) {
                Logger.i("Settings", "Model deleted successfully")
                Toast.makeText(requireContext(), R.string.model_deleted, Toast.LENGTH_SHORT).show()
            } else {
                Logger.e("Settings", "Failed to delete model")
            }
            updateModelDownloadSummary(modelSize)
        }

        private fun setupEmojiPreferences() {
            findPreference<Preference>("manage_emoji_hotkeys")?.setOnPreferenceClickListener {
                showEmojiHotkeyManager()
                true
            }
        }

        private fun showEmojiHotkeyManager() {
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

        private fun setupDebugPreferences() {
            findPreference<Preference>("show_logs")?.setOnPreferenceClickListener {
                showLogs()
                true
            }

            findPreference<Preference>("clear_logs")?.setOnPreferenceClickListener {
                Logger.clear()
                Toast.makeText(requireContext(), R.string.logs_cleared, Toast.LENGTH_SHORT).show()
                true
            }

            findPreference<Preference>("model_info")?.setOnPreferenceClickListener {
                showModelInfo()
                true
            }
        }

        private fun showLogs() {
            val logs = Logger.getLogsAsString()

            if (logs.isEmpty()) {
                Toast.makeText(requireContext(), R.string.no_logs, Toast.LENGTH_SHORT).show()
                return
            }

            val scrollView = ScrollView(requireContext())
            val textView = TextView(requireContext()).apply {
                text = logs
                setPadding(32, 32, 32, 32)
                setTextIsSelectable(true)
                textSize = 12f
            }
            scrollView.addView(textView)

            AlertDialog.Builder(requireContext())
                .setTitle(R.string.logs_title)
                .setView(scrollView)
                .setPositiveButton(R.string.ok, null)
                .setNeutralButton(R.string.copy_to_clipboard) { _, _ ->
                    copyToClipboard(logs)
                }
                .show()
        }

        private fun showModelInfo() {
            val modelSize = preferenceManager.sharedPreferences?.getString("model_size", "base") ?: "base"
            val modelInfo = ModelManager.MODELS[modelSize]
            val isDownloaded = modelManager.isModelDownloaded(modelSize)
            val path = modelManager.getDownloadedModelPath(modelSize)

            val info = buildString {
                appendLine("=== Model Info ===")
                appendLine("Selected: $modelSize")
                appendLine("Name: ${modelInfo?.displayName ?: "Unknown"}")
                appendLine("Expected Size: ${modelInfo?.sizeBytes?.let { modelManager.formatBytes(it) } ?: "Unknown"}")
                appendLine()

                appendLine("=== Download Status ===")
                appendLine("Downloaded: ${if (isDownloaded) "Yes" else "No"}")
                if (path != null) {
                    appendLine("Path: $path")
                    val file = File(path)
                    appendLine("File exists: ${file.exists()}")
                    if (file.exists()) {
                        appendLine("File size: ${modelManager.formatBytes(file.length())}")
                        appendLine("Readable: ${file.canRead()}")
                    }
                }
                appendLine()

                appendLine("=== Storage Settings ===")
                appendLine("Current: ${modelManager.getStorageDisplayName()}")
                appendLine("Using SD Card: ${modelManager.isUsingSdCard()}")
                appendLine("SD Card available: ${modelManager.isSdCardAvailable()}")
                appendLine("Internal dir: ${modelManager.getInternalModelsDirectory().absolutePath}")
                val sdDir = modelManager.getSdCardModelsDirectory()
                if (sdDir != null) {
                    appendLine("SD Card dir: ${sdDir.absolutePath}")
                    appendLine("SD Card writable: ${sdDir.canWrite()}")
                }
                appendLine("Available space: ${modelManager.formatBytes(modelManager.getAvailableSpace())}")

                appendLine()
                appendLine("=== Whisper Engine ===")
                try {
                    val engine = WhisperEngine(requireContext())
                    appendLine("System info: ${engine.getSystemInfo()}")
                    engine.release()
                } catch (e: Exception) {
                    appendLine("Error: ${e.message}")
                }
            }

            val scrollView = ScrollView(requireContext())
            val textView = TextView(requireContext()).apply {
                text = info
                setPadding(32, 32, 32, 32)
                setTextIsSelectable(true)
                textSize = 11f
                typeface = android.graphics.Typeface.MONOSPACE
            }
            scrollView.addView(textView)

            AlertDialog.Builder(requireContext())
                .setTitle(R.string.model_info_title)
                .setView(scrollView)
                .setPositiveButton(R.string.ok, null)
                .setNeutralButton(R.string.copy_to_clipboard) { _, _ ->
                    copyToClipboard(info)
                }
                .show()
        }

        private fun copyToClipboard(text: String) {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("WhisperKey", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
        }

        private fun showLicenses() {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.pref_licenses_title)
                .setMessage("whisper.cpp - MIT License\nOkHttp - Apache 2.0 License")
                .setPositiveButton(R.string.ok, null)
                .show()
        }
    }
}
