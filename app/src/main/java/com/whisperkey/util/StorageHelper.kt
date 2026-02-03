package com.whisperkey.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * Helper class for storage operations, particularly for SAF (Storage Access Framework).
 */
object StorageHelper {

    private const val TAG = "StorageHelper"

    /**
     * Take persistent permissions for a tree URI so we can access it after app restart.
     */
    fun takePersistentPermission(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            Logger.i(TAG, "Took persistent permission for: $uri")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to take persistent permission: ${e.message}", e)
        }
    }

    /**
     * Check if we have persistent permission for a URI.
     */
    fun hasPersistentPermission(context: Context, uriString: String): Boolean {
        return try {
            val uri = Uri.parse(uriString)
            context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission && it.isWritePermission
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error checking persistent permission: ${e.message}")
            false
        }
    }

    /**
     * Get a human-readable display name for a storage URI.
     */
    fun getStorageDisplayName(context: Context, uriString: String?): String {
        if (uriString == null) return "Internal App Storage"

        return try {
            val uri = Uri.parse(uriString)
            val docFile = DocumentFile.fromTreeUri(context, uri)
            val name = docFile?.name
            if (name != null) {
                extractPathFromUri(uri) ?: name
            } else {
                extractPathFromUri(uri) ?: "Custom Location"
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to get display name for URI: ${e.message}")
            "Custom Location"
        }
    }

    /**
     * Extract a readable path description from a SAF URI.
     * Returns something like "SD Card/WhisperKey" or "Internal/Download/WhisperKey"
     */
    fun extractPathFromUri(uri: Uri): String? {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            Logger.d(TAG, "Document ID: $docId")

            val parts = docId.split(":")
            if (parts.size == 2) {
                val storage = when {
                    parts[0] == "primary" -> "Internal"
                    parts[0].matches(Regex("[0-9A-F]{4}-[0-9A-F]{4}")) -> "SD Card"
                    else -> parts[0]
                }
                val path = parts[1].ifEmpty { "Root" }
                "$storage/$path"
            } else {
                docId
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to extract path from URI: ${e.message}")
            null
        }
    }

    /**
     * Convert a SAF tree URI to an actual file path.
     * This works for external storage documents where the URI pattern is:
     * content://com.android.externalstorage.documents/tree/[volume]:[path]
     *
     * @return File path or null if conversion not possible
     */
    fun getFilePathFromUri(context: Context, uriString: String): String? {
        return try {
            val uri = Uri.parse(uriString)
            val docId = DocumentsContract.getTreeDocumentId(uri)
            Logger.d(TAG, "Converting URI to path, docId: $docId")

            val parts = docId.split(":")
            if (parts.size == 2) {
                val volumeId = parts[0]
                val relativePath = parts[1]

                val basePath = when (volumeId) {
                    "primary" -> "/storage/emulated/0"
                    else -> "/storage/$volumeId"
                }

                val fullPath = if (relativePath.isEmpty()) {
                    basePath
                } else {
                    "$basePath/$relativePath"
                }

                Logger.d(TAG, "Converted URI to path: $fullPath")

                // Verify the path exists or can be created
                val dir = File(fullPath)
                if (dir.exists() || dir.mkdirs()) {
                    fullPath
                } else {
                    Logger.e(TAG, "Cannot access or create directory: $fullPath")
                    null
                }
            } else {
                Logger.e(TAG, "Unexpected document ID format: $docId")
                null
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to convert URI to path: ${e.message}", e)
            null
        }
    }

    /**
     * Check if a custom storage location (URI) is valid and writable.
     */
    fun isStorageValid(context: Context, uriString: String?): Boolean {
        if (uriString == null) return false

        return try {
            val uri = Uri.parse(uriString)
            val docFile = DocumentFile.fromTreeUri(context, uri)
            val isValid = docFile?.canWrite() == true
            Logger.d(TAG, "Storage valid check for $uriString: $isValid")
            isValid
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to validate storage URI: ${e.message}")
            false
        }
    }

    /**
     * Create a file in the custom storage location using DocumentFile.
     * @return The created DocumentFile or null if failed
     */
    fun createFileInStorage(context: Context, uriString: String, fileName: String, mimeType: String = "application/octet-stream"): DocumentFile? {
        return try {
            val uri = Uri.parse(uriString)
            val parentDoc = DocumentFile.fromTreeUri(context, uri)

            if (parentDoc == null || !parentDoc.canWrite()) {
                Logger.e(TAG, "Cannot write to storage location")
                return null
            }

            // Check if file already exists
            val existingFile = parentDoc.findFile(fileName)
            if (existingFile != null) {
                Logger.d(TAG, "File already exists, deleting: $fileName")
                existingFile.delete()
            }

            val newFile = parentDoc.createFile(mimeType, fileName)
            Logger.d(TAG, "Created file: ${newFile?.uri}")
            newFile
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to create file in storage: ${e.message}", e)
            null
        }
    }

    /**
     * Find a file in the custom storage location.
     * @return The DocumentFile or null if not found
     */
    fun findFileInStorage(context: Context, uriString: String, fileName: String): DocumentFile? {
        return try {
            val uri = Uri.parse(uriString)
            val parentDoc = DocumentFile.fromTreeUri(context, uri)
            parentDoc?.findFile(fileName)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to find file in storage: ${e.message}")
            null
        }
    }
}
