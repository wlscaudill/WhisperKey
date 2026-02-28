package com.whisperkey

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import com.whisperkey.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class UpdateManager(private val context: Context) {

    companion object {
        private const val TAG = "UpdateManager"
        private const val RELEASES_URL = "https://api.github.com/repos/wlscaudill/WhisperKey/releases/latest"
        private const val TAG_PREFIX = "android-v"
        private const val APK_FILENAME = "WhisperKey-update.apk"
    }

    sealed class UpdateState {
        object Idle : UpdateState()
        object Checking : UpdateState()
        data class UpdateAvailable(val release: ReleaseInfo) : UpdateState()
        object UpToDate : UpdateState()
        data class Downloading(val progress: Float) : UpdateState()
        data class ReadyToInstall(val apkFile: File) : UpdateState()
        data class Error(val message: String) : UpdateState()
    }

    data class ReleaseInfo(
        val tagName: String,
        val version: String,
        val releaseNotes: String,
        val apkUrl: String
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var isCancelled = false

    fun checkForUpdate(onResult: (UpdateState) -> Unit) {
        isCancelled = false
        scope.launch {
            val state = try {
                Logger.i(TAG, "Checking for updates...")
                val request = Request.Builder()
                    .url(RELEASES_URL)
                    .header("Accept", "application/vnd.github+json")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    when (response.code) {
                        404 -> {
                            Logger.i(TAG, "No releases found (404)")
                            UpdateState.UpToDate
                        }
                        403 -> {
                            Logger.w(TAG, "Rate limited by GitHub API")
                            UpdateState.Error("GitHub API rate limit reached. Try again later.")
                        }
                        200 -> {
                            val body = response.body?.string()
                            if (body == null) {
                                UpdateState.Error("Empty response from GitHub")
                            } else {
                                parseRelease(body)
                            }
                        }
                        else -> {
                            Logger.e(TAG, "Unexpected response: ${response.code}")
                            UpdateState.Error("Could not check for updates (HTTP ${response.code})")
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Update check failed: ${e.message}", e)
                UpdateState.Error("Could not check for updates. Check your internet connection.")
            }

            withContext(Dispatchers.Main) {
                onResult(state)
            }
        }
    }

    private fun parseRelease(json: String): UpdateState {
        val release = JSONObject(json)
        val tagName = release.getString("tag_name")

        if (!tagName.startsWith(TAG_PREFIX)) {
            Logger.i(TAG, "Latest release tag '$tagName' is not an Android release")
            return UpdateState.UpToDate
        }

        val remoteVersion = tagName.removePrefix(TAG_PREFIX)
        val localVersion = getInstalledVersion()

        Logger.i(TAG, "Local version: $localVersion, Remote version: $remoteVersion")

        if (!isNewerVersion(remoteVersion, localVersion)) {
            Logger.i(TAG, "Already up to date")
            return UpdateState.UpToDate
        }

        val assets = release.getJSONArray("assets")
        var apkUrl: String? = null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.getString("name")
            if (name.endsWith(".apk")) {
                apkUrl = asset.getString("browser_download_url")
                break
            }
        }

        if (apkUrl == null) {
            Logger.w(TAG, "No APK asset found in release $tagName")
            return UpdateState.Error("Update found but no APK available for download.")
        }

        val releaseNotes = release.optString("body", "").trim()

        val info = ReleaseInfo(
            tagName = tagName,
            version = remoteVersion,
            releaseNotes = releaseNotes,
            apkUrl = apkUrl
        )
        Logger.i(TAG, "Update available: $remoteVersion ($apkUrl)")
        return UpdateState.UpdateAvailable(info)
    }

    fun downloadApk(url: String, onComplete: (UpdateState) -> Unit) {
        isCancelled = false
        scope.launch {
            val state = try {
                Logger.i(TAG, "Downloading APK from: $url")
                val request = Request.Builder().url(url).build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Logger.e(TAG, "APK download failed: ${response.code}")
                        UpdateState.Error("Download failed (HTTP ${response.code})")
                    } else {
                        val body = response.body
                            ?: return@use UpdateState.Error("Empty download response")
                        val contentLength = body.contentLength().takeIf { it > 0 } ?: -1L
                        val apkFile = File(context.cacheDir, APK_FILENAME)

                        FileOutputStream(apkFile).use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            var totalBytesRead = 0L

                            body.byteStream().use { input ->
                                while (!isCancelled && input.read(buffer).also { bytesRead = it } != -1) {
                                    output.write(buffer, 0, bytesRead)
                                    totalBytesRead += bytesRead
                                    if (contentLength > 0) {
                                        val progress = (totalBytesRead.toFloat() / contentLength).coerceIn(0f, 1f)
                                        withContext(Dispatchers.Main) {
                                            onComplete(UpdateState.Downloading(progress))
                                        }
                                    }
                                }
                            }
                        }

                        if (isCancelled) {
                            apkFile.delete()
                            UpdateState.Error("Download cancelled")
                        } else {
                            Logger.i(TAG, "APK downloaded to: ${apkFile.absolutePath}")
                            UpdateState.ReadyToInstall(apkFile)
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.e(TAG, "APK download error: ${e.message}", e)
                UpdateState.Error("Download failed. Check your internet connection.")
            }

            withContext(Dispatchers.Main) {
                onComplete(state)
            }
        }
    }

    fun getInstallIntent(apkFile: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun cancelDownload() {
        isCancelled = true
    }

    private fun getInstalledVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "0.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "0.0"
        }
    }

    internal fun isNewerVersion(remote: String, local: String): Boolean {
        val remoteParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val localParts = local.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(remoteParts.size, localParts.size)

        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }
}
