package com.whisperkey

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.whisperkey.util.Logger
import com.whisperkey.util.StorageHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Manages Whisper model downloads and storage.
 * Supports internal app storage and custom folder (including SD card via SAF).
 * Uses English-only models for better performance.
 */
class ModelManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"
        private const val BASE_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main"
        private const val MODELS_SUBDIR = "whisper_models"
        private const val PREF_USE_SDCARD = "use_sdcard_storage"

        // English-only models for better performance
        val MODELS = mapOf(
            "tiny" to ModelInfo(
                fileName = "ggml-tiny.en.bin",
                displayName = "Tiny (English)",
                sizeBytes = 77_691_713L,
                sha256 = "921e4cf8986f3f97f50f1a1ef0fce7eb8c0a6e27"
            ),
            "base" to ModelInfo(
                fileName = "ggml-base.en.bin",
                displayName = "Base (English)",
                sizeBytes = 147_951_465L,
                sha256 = "a03779c86df3323075f5e796b3f6d1b1"
            ),
            "small" to ModelInfo(
                fileName = "ggml-small.en.bin",
                displayName = "Small (English)",
                sizeBytes = 487_601_967L,
                sha256 = "db8a495a91d927739e50b3fc1ef8d61e"
            )
        )
    }

    data class ModelInfo(
        val fileName: String,
        val displayName: String,
        val sizeBytes: Long,
        val sha256: String
    )

    sealed class DownloadState {
        object Idle : DownloadState()
        data class Downloading(val progress: Float, val bytesDownloaded: Long, val totalBytes: Long) : DownloadState()
        object Verifying : DownloadState()
        data class Completed(val modelPath: String) : DownloadState()
        data class Error(val message: String) : DownloadState()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var isCancelled = false

    // ==================== Storage Location Methods ====================

    /**
     * Check if using SD card storage.
     */
    fun isUsingSdCard(): Boolean {
        return prefs.getBoolean(PREF_USE_SDCARD, false)
    }

    /**
     * Set whether to use SD card storage.
     */
    fun setUseSdCard(useSdCard: Boolean) {
        Logger.i(TAG, "Setting use SD card: $useSdCard")
        prefs.edit().putBoolean(PREF_USE_SDCARD, useSdCard).apply()
    }

    /**
     * Check if SD card storage is available.
     * Returns true if there's a secondary external storage (SD card).
     */
    fun isSdCardAvailable(): Boolean {
        val dirs = context.getExternalFilesDirs(null)
        // First dir is primary external (usually internal), second+ are SD cards
        return dirs.size > 1 && dirs[1] != null && (dirs[1]?.canWrite() == true)
    }

    /**
     * Get the SD card app directory for models.
     * Returns null if SD card is not available.
     */
    fun getSdCardModelsDirectory(): File? {
        val dirs = context.getExternalFilesDirs(null)
        if (dirs.size > 1 && dirs[1] != null) {
            val sdCardDir = dirs[1]
            val modelsDir = File(sdCardDir, MODELS_SUBDIR)
            if (!modelsDir.exists()) {
                val created = modelsDir.mkdirs()
                Logger.d(TAG, "Created SD card models directory: $created - ${modelsDir.absolutePath}")
            }
            return modelsDir
        }
        return null
    }

    /**
     * Get a human-readable name for the current storage location.
     */
    fun getStorageDisplayName(): String {
        return if (isUsingSdCard()) {
            val sdDir = getSdCardModelsDirectory()
            if (sdDir != null) "SD Card" else "SD Card (unavailable)"
        } else {
            "Internal App Storage"
        }
    }

    /**
     * Get the internal app storage directory for models.
     */
    fun getInternalModelsDirectory(): File {
        val dir = File(context.filesDir, MODELS_SUBDIR)
        if (!dir.exists()) {
            val created = dir.mkdirs()
            Logger.d(TAG, "Created internal models directory: $created - ${dir.absolutePath}")
        }
        return dir
    }

    /**
     * Get the current models directory based on storage setting.
     */
    fun getCurrentModelsDirectory(): File {
        return if (isUsingSdCard()) {
            getSdCardModelsDirectory() ?: getInternalModelsDirectory()
        } else {
            getInternalModelsDirectory()
        }
    }

    /**
     * Get available space for the current storage location.
     */
    fun getAvailableSpace(): Long {
        return getCurrentModelsDirectory().freeSpace
    }

    // ==================== Model Management Methods ====================

    /**
     * Check if a model is downloaded (checks both internal and custom storage).
     */
    fun isModelDownloaded(modelSize: String): Boolean {
        return getDownloadedModelPath(modelSize) != null
    }

    /**
     * Get the path to a downloaded model, checking all storage locations.
     * Returns null if model is not found.
     */
    fun getDownloadedModelPath(modelSize: String): String? {
        val modelInfo = MODELS[modelSize]
        if (modelInfo == null) {
            Logger.e(TAG, "Unknown model size: $modelSize")
            return null
        }

        Logger.d(TAG, "Looking for model: ${modelInfo.fileName}")

        // Check internal storage first
        val internalPath = File(getInternalModelsDirectory(), modelInfo.fileName)
        Logger.d(TAG, "Checking internal: ${internalPath.absolutePath}")
        Logger.d(TAG, "  exists: ${internalPath.exists()}, size: ${if (internalPath.exists()) internalPath.length() else 0}")

        if (internalPath.exists() && internalPath.length() > 0) {
            Logger.i(TAG, "Found model in internal storage: ${internalPath.absolutePath}")
            return internalPath.absolutePath
        }

        // Check SD card storage
        val sdCardDir = getSdCardModelsDirectory()
        if (sdCardDir != null) {
            val sdCardPath = File(sdCardDir, modelInfo.fileName)
            Logger.d(TAG, "Checking SD card: ${sdCardPath.absolutePath}")
            Logger.d(TAG, "  exists: ${sdCardPath.exists()}, size: ${if (sdCardPath.exists()) sdCardPath.length() else 0}")

            if (sdCardPath.exists() && sdCardPath.length() > 0) {
                Logger.i(TAG, "Found model on SD card: ${sdCardPath.absolutePath}")
                return sdCardPath.absolutePath
            }
        }

        Logger.w(TAG, "Model not found: $modelSize")
        return null
    }

    /**
     * Get the target path for downloading a model.
     */
    fun getModelDownloadPath(modelSize: String): String? {
        val modelInfo = MODELS[modelSize] ?: return null
        return File(getCurrentModelsDirectory(), modelInfo.fileName).absolutePath
    }

    /**
     * Downloads a model to the current storage location.
     */
    fun downloadModel(modelSize: String, onComplete: ((Boolean) -> Unit)? = null) {
        val modelInfo = MODELS[modelSize]
        if (modelInfo == null) {
            Logger.e(TAG, "Unknown model size: $modelSize")
            _downloadState.value = DownloadState.Error("Unknown model size: $modelSize")
            onComplete?.invoke(false)
            return
        }

        val outputPath = getModelDownloadPath(modelSize)
        if (outputPath == null) {
            Logger.e(TAG, "Cannot determine download path")
            _downloadState.value = DownloadState.Error("Cannot determine download path")
            onComplete?.invoke(false)
            return
        }

        isCancelled = false

        scope.launch {
            try {
                Logger.i(TAG, "=== Starting Download ===")
                Logger.i(TAG, "Model: $modelSize (${modelInfo.fileName})")
                Logger.i(TAG, "Expected size: ${formatBytes(modelInfo.sizeBytes)}")
                Logger.i(TAG, "Output path: $outputPath")

                _downloadState.value = DownloadState.Downloading(0f, 0, modelInfo.sizeBytes)

                val url = "$BASE_URL/${modelInfo.fileName}"
                val outputFile = File(outputPath)

                // Ensure parent directory exists
                outputFile.parentFile?.let { parent ->
                    if (!parent.exists()) {
                        val created = parent.mkdirs()
                        Logger.d(TAG, "Created parent directory: $created - ${parent.absolutePath}")
                    }
                }

                Logger.i(TAG, "Download URL: $url")
                Logger.i(TAG, "Parent exists: ${outputFile.parentFile?.exists()}")
                Logger.i(TAG, "Parent writable: ${outputFile.parentFile?.canWrite()}")

                // Download file - returns content length on success
                val expectedSize = downloadFile(url, outputFile, modelInfo.sizeBytes)

                if (expectedSize == null) {
                    if (isCancelled) {
                        Logger.w(TAG, "Download cancelled by user")
                        _downloadState.value = DownloadState.Error("Download cancelled")
                    } else {
                        Logger.e(TAG, "Download failed")
                        _downloadState.value = DownloadState.Error("Download failed")
                    }
                    outputFile.delete()
                    withContext(Dispatchers.Main) {
                        onComplete?.invoke(false)
                    }
                    return@launch
                }

                // Verify file size against server's content-length
                Logger.i(TAG, "Download complete, verifying...")
                _downloadState.value = DownloadState.Verifying
                val actualSize = outputFile.length()
                Logger.i(TAG, "Expected (from server): $expectedSize, Actual: $actualSize")

                if (actualSize != expectedSize) {
                    Logger.e(TAG, "File size mismatch!")
                    _downloadState.value = DownloadState.Error(
                        "File size mismatch. Expected $expectedSize, got $actualSize"
                    )
                    outputFile.delete()
                    withContext(Dispatchers.Main) {
                        onComplete?.invoke(false)
                    }
                    return@launch
                }

                Logger.i(TAG, "=== Download Complete ===")
                Logger.i(TAG, "Model saved to: ${outputFile.absolutePath}")
                _downloadState.value = DownloadState.Completed(outputFile.absolutePath)
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(true)
                }

            } catch (e: Exception) {
                Logger.e(TAG, "Download error: ${e.message}", e)
                _downloadState.value = DownloadState.Error(e.message ?: "Unknown error")
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(false)
                }
            }
        }
    }

    /**
     * Cancels the current download.
     */
    fun cancelDownload() {
        isCancelled = true
    }

    /**
     * Downloads a file and returns the content length on success, or null on failure.
     */
    private suspend fun downloadFile(url: String, outputFile: File, expectedSize: Long): Long? {
        return withContext(Dispatchers.IO) {
            try {
                Logger.d(TAG, "Creating HTTP request...")
                val request = Request.Builder()
                    .url(url)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    Logger.d(TAG, "Response code: ${response.code}")
                    if (!response.isSuccessful) {
                        Logger.e(TAG, "HTTP request failed: ${response.code}")
                        return@withContext null
                    }

                    val body = response.body
                    if (body == null) {
                        Logger.e(TAG, "Response body is null")
                        return@withContext null
                    }

                    val contentLength = body.contentLength().takeIf { it > 0 } ?: expectedSize
                    Logger.d(TAG, "Content-Length: $contentLength")

                    FileOutputStream(outputFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead = 0
                        var totalBytesRead = 0L
                        var lastLogTime = System.currentTimeMillis()

                        body.byteStream().use { input ->
                            while (!isCancelled && input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead

                                val progress = totalBytesRead.toFloat() / contentLength
                                _downloadState.value = DownloadState.Downloading(
                                    progress.coerceIn(0f, 1f),
                                    totalBytesRead,
                                    contentLength
                                )

                                // Log progress every 5 seconds
                                val now = System.currentTimeMillis()
                                if (now - lastLogTime > 5000) {
                                    Logger.d(TAG, "Progress: ${(progress * 100).toInt()}% (${formatBytes(totalBytesRead)})")
                                    lastLogTime = now
                                }
                            }
                        }
                    }

                    if (isCancelled) null else contentLength
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Download exception: ${e.message}", e)
                outputFile.delete()
                null
            }
        }
    }

    /**
     * Deletes a downloaded model from all storage locations.
     */
    fun deleteModel(modelSize: String): Boolean {
        val modelInfo = MODELS[modelSize] ?: return false
        var deleted = false

        // Delete from internal storage
        val internalFile = File(getInternalModelsDirectory(), modelInfo.fileName)
        if (internalFile.exists()) {
            Logger.d(TAG, "Deleting from internal: ${internalFile.absolutePath}")
            deleted = internalFile.delete() || deleted
        }

        // Delete from SD card storage
        val sdCardDir = getSdCardModelsDirectory()
        if (sdCardDir != null) {
            val sdCardFile = File(sdCardDir, modelInfo.fileName)
            if (sdCardFile.exists()) {
                Logger.d(TAG, "Deleting from SD card: ${sdCardFile.absolutePath}")
                deleted = sdCardFile.delete() || deleted
            }
        }

        return deleted
    }

    /**
     * Gets a list of all downloaded models.
     */
    fun getDownloadedModels(): List<String> {
        return MODELS.keys.filter { isModelDownloaded(it) }
    }

    /**
     * Formats bytes to human readable string.
     */
    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> String.format("%.1f GB", bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_000_000.0)
            bytes >= 1_000 -> String.format("%.1f KB", bytes / 1_000.0)
            else -> "$bytes B"
        }
    }

    /**
     * Resets the download state to idle.
     */
    fun resetState() {
        _downloadState.value = DownloadState.Idle
    }
}
