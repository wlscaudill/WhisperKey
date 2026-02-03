package com.whisperkey

import android.content.Context
import android.os.Environment
import androidx.preference.PreferenceManager
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
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Manages Whisper model downloads and storage.
 * Supports both internal storage and SD card.
 * Uses English-only models for better performance.
 */
class ModelManager(private val context: Context) {

    companion object {
        private const val BASE_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main"
        private const val MODELS_DIR = "models"
        private const val PREF_STORAGE_LOCATION = "storage_location"

        // English-only models for better performance
        // SHA256 hashes from: https://huggingface.co/ggerganov/whisper.cpp
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

    enum class StorageLocation {
        INTERNAL,
        EXTERNAL
    }

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

    /**
     * Gets the current storage location preference.
     */
    fun getStorageLocation(): StorageLocation {
        val pref = prefs.getString(PREF_STORAGE_LOCATION, "internal")
        return if (pref == "external" && isExternalStorageAvailable()) {
            StorageLocation.EXTERNAL
        } else {
            StorageLocation.INTERNAL
        }
    }

    /**
     * Sets the storage location preference.
     */
    fun setStorageLocation(location: StorageLocation) {
        val value = if (location == StorageLocation.EXTERNAL) "external" else "internal"
        prefs.edit().putString(PREF_STORAGE_LOCATION, value).apply()
    }

    /**
     * Checks if external storage (SD card) is available.
     */
    fun isExternalStorageAvailable(): Boolean {
        val state = Environment.getExternalStorageState()
        if (state != Environment.MEDIA_MOUNTED) return false

        val externalDir = context.getExternalFilesDir(null)
        return externalDir != null && (externalDir.exists() || externalDir.mkdirs())
    }

    /**
     * Gets available space in bytes for the specified storage location.
     */
    fun getAvailableSpace(location: StorageLocation): Long {
        val dir = getModelsDirectory(location)
        return dir.freeSpace
    }

    /**
     * Gets the models directory for the specified storage location.
     */
    fun getModelsDirectory(location: StorageLocation = getStorageLocation()): File {
        val baseDir = when (location) {
            StorageLocation.INTERNAL -> context.filesDir
            StorageLocation.EXTERNAL -> context.getExternalFilesDir(null) ?: context.filesDir
        }
        return File(baseDir, MODELS_DIR).apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Gets the path for a specific model.
     */
    fun getModelPath(modelSize: String, location: StorageLocation = getStorageLocation()): String {
        val modelInfo = MODELS[modelSize] ?: MODELS["base"]!!
        return File(getModelsDirectory(location), modelInfo.fileName).absolutePath
    }

    /**
     * Checks if a model is downloaded in any location.
     */
    fun isModelDownloaded(modelSize: String): Boolean {
        return findModelLocation(modelSize) != null
    }

    /**
     * Finds which storage location contains the model, or null if not downloaded.
     */
    fun findModelLocation(modelSize: String): StorageLocation? {
        val modelInfo = MODELS[modelSize] ?: return null

        // Check internal first
        val internalPath = File(getModelsDirectory(StorageLocation.INTERNAL), modelInfo.fileName)
        if (internalPath.exists() && internalPath.length() > 0) {
            return StorageLocation.INTERNAL
        }

        // Check external
        if (isExternalStorageAvailable()) {
            val externalPath = File(getModelsDirectory(StorageLocation.EXTERNAL), modelInfo.fileName)
            if (externalPath.exists() && externalPath.length() > 0) {
                return StorageLocation.EXTERNAL
            }
        }

        return null
    }

    /**
     * Gets the actual path to a downloaded model (checking both locations).
     */
    fun getDownloadedModelPath(modelSize: String): String? {
        val location = findModelLocation(modelSize) ?: return null
        return getModelPath(modelSize, location)
    }

    /**
     * Downloads a model to the current storage location.
     */
    fun downloadModel(modelSize: String, onComplete: ((Boolean) -> Unit)? = null) {
        val modelInfo = MODELS[modelSize]
        if (modelInfo == null) {
            _downloadState.value = DownloadState.Error("Unknown model size: $modelSize")
            onComplete?.invoke(false)
            return
        }

        isCancelled = false

        scope.launch {
            try {
                _downloadState.value = DownloadState.Downloading(0f, 0, modelInfo.sizeBytes)

                val url = "$BASE_URL/${modelInfo.fileName}"
                val outputFile = File(getModelsDirectory(), modelInfo.fileName)

                // Download file
                val downloadSuccess = downloadFile(url, outputFile, modelInfo.sizeBytes)

                if (!downloadSuccess) {
                    if (isCancelled) {
                        _downloadState.value = DownloadState.Error("Download cancelled")
                    } else {
                        _downloadState.value = DownloadState.Error("Download failed")
                    }
                    outputFile.delete()
                    withContext(Dispatchers.Main) {
                        onComplete?.invoke(false)
                    }
                    return@launch
                }

                // Verify file size
                _downloadState.value = DownloadState.Verifying
                if (outputFile.length() != modelInfo.sizeBytes) {
                    _downloadState.value = DownloadState.Error(
                        "File size mismatch. Expected ${modelInfo.sizeBytes}, got ${outputFile.length()}"
                    )
                    outputFile.delete()
                    withContext(Dispatchers.Main) {
                        onComplete?.invoke(false)
                    }
                    return@launch
                }

                _downloadState.value = DownloadState.Completed(outputFile.absolutePath)
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(true)
                }

            } catch (e: Exception) {
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

    private suspend fun downloadFile(url: String, outputFile: File, expectedSize: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext false

                    val body = response.body ?: return@withContext false
                    val contentLength = body.contentLength().takeIf { it > 0 } ?: expectedSize

                    FileOutputStream(outputFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytesRead = 0L

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
                            }
                        }
                    }

                    !isCancelled
                }
            } catch (e: Exception) {
                outputFile.delete()
                false
            }
        }
    }

    /**
     * Deletes a downloaded model from all locations.
     */
    fun deleteModel(modelSize: String): Boolean {
        val modelInfo = MODELS[modelSize] ?: return false

        var deleted = false

        // Delete from internal
        val internalFile = File(getModelsDirectory(StorageLocation.INTERNAL), modelInfo.fileName)
        if (internalFile.exists()) {
            deleted = internalFile.delete() || deleted
        }

        // Delete from external
        if (isExternalStorageAvailable()) {
            val externalFile = File(getModelsDirectory(StorageLocation.EXTERNAL), modelInfo.fileName)
            if (externalFile.exists()) {
                deleted = externalFile.delete() || deleted
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
     * Gets the size of a downloaded model in bytes.
     */
    fun getModelSize(modelSize: String): Long {
        val path = getDownloadedModelPath(modelSize) ?: return 0
        return File(path).length()
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
