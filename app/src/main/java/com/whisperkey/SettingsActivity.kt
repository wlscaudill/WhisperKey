package com.whisperkey

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.whisperkey.util.Logger
import com.whisperkey.util.StorageHelper
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

        // SAF folder picker launcher
        private val folderPickerLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            uri?.let { handleFolderSelected(it) }
        }

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
            val currentHasCustom = modelManager.hasCustomStorage()

            val options = if (currentHasCustom) {
                arrayOf(
                    "Internal App Storage",
                    "Change Custom Folder...",
                    "Reset to Internal Storage"
                )
            } else {
                arrayOf(
                    "Internal App Storage (current)",
                    "Choose Custom Folder..."
                )
            }

            AlertDialog.Builder(requireContext())
                .setTitle(R.string.pref_storage_location_title)
                .setItems(options) { _, which ->
                    when {
                        // Internal storage selected (always first option)
                        which == 0 && !currentHasCustom -> {
                            // Already internal, do nothing
                        }
                        // Choose/Change custom folder
                        (which == 1 && !currentHasCustom) || (which == 1 && currentHasCustom) -> {
                            openFolderPicker()
                        }
                        // Reset to internal (only when custom is set)
                        which == 2 && currentHasCustom -> {
                            resetToInternalStorage()
                        }
                        which == 0 && currentHasCustom -> {
                            // "Internal App Storage" when custom is set - do nothing, show as info
                        }
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        private fun openFolderPicker() {
            Logger.i("Settings", "Opening folder picker")
            folderPickerLauncher.launch(null)
        }

        private fun handleFolderSelected(uri: Uri) {
            Logger.i("Settings", "Folder selected: $uri")

            // Take persistent permission
            StorageHelper.takePersistentPermission(requireContext(), uri)

            // Verify we can get a file path from this URI
            val filePath = StorageHelper.getFilePathFromUri(requireContext(), uri.toString())
            if (filePath == null) {
                Logger.e("Settings", "Cannot access selected folder as file path")
                Toast.makeText(
                    requireContext(),
                    "Cannot use this folder. Please select a folder on internal or external storage.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            // Verify the folder is writable
            val testDir = File(filePath)
            if (!testDir.exists()) {
                testDir.mkdirs()
            }
            if (!testDir.canWrite()) {
                Logger.e("Settings", "Selected folder is not writable: $filePath")
                Toast.makeText(
                    requireContext(),
                    "Cannot write to selected folder",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            // Save the custom storage URI
            modelManager.setCustomStorageUri(uri.toString())
            updateStorageSummary()

            val displayName = StorageHelper.getStorageDisplayName(requireContext(), uri.toString())
            Toast.makeText(
                requireContext(),
                "Storage set to: $displayName",
                Toast.LENGTH_SHORT
            ).show()

            Logger.i("Settings", "Custom storage set: $displayName ($filePath)")
        }

        private fun resetToInternalStorage() {
            modelManager.setCustomStorageUri(null)
            updateStorageSummary()
            Toast.makeText(requireContext(), "Reset to Internal App Storage", Toast.LENGTH_SHORT).show()
            Logger.i("Settings", "Reset to internal storage")
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
                appendLine("Display name: ${modelManager.getStorageDisplayName()}")
                appendLine("Custom URI: ${modelManager.getCustomStorageUri() ?: "Not set"}")
                appendLine("Internal dir: ${modelManager.getInternalModelsDirectory().absolutePath}")
                appendLine("Available space: ${modelManager.formatBytes(modelManager.getAvailableSpace())}")

                val customUri = modelManager.getCustomStorageUri()
                if (customUri != null) {
                    appendLine()
                    appendLine("=== Custom Storage ===")
                    appendLine("URI: $customUri")
                    val customPath = StorageHelper.getFilePathFromUri(requireContext(), customUri)
                    appendLine("File path: ${customPath ?: "Cannot resolve"}")
                    if (customPath != null) {
                        val customDir = File(customPath)
                        appendLine("Exists: ${customDir.exists()}")
                        appendLine("Writable: ${customDir.canWrite()}")
                    }
                    appendLine("Has permission: ${StorageHelper.hasPersistentPermission(requireContext(), customUri)}")
                }

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
