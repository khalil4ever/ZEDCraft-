package com.example.data.remote

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.BuildConfig
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class GitHubReleaseInfo(
    val repository: String,
    val latestTag: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val publishedAt: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val apkFileName: String?,
    val apkSizeBytes: Long,
    val isUpdateAvailable: Boolean,
    val currentVersion: String,
    val recentCommitsSummary: List<String> = emptyList()
) {
    val cleanVersionNumber: String
        get() = latestTag.trim().removePrefix("v").removePrefix("V").ifBlank { currentVersion }
}

data class ApkDownloadProgress(
    val percent: Int,
    val downloadedMb: Float,
    val totalMb: Float
)

class GitHubUpdateClient(
    private val context: Context? = null
) {
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val prefs by lazy {
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Checks whether the device currently has an active internet connection.
     */
    fun isDeviceOnline(): Boolean {
        val ctx = context ?: return true
        return runCatching {
            val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return@runCatching false
            val activeNetwork = cm.activeNetwork ?: return@runCatching false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return@runCatching false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(false)
    }

    /**
     * Checks if enough time has elapsed since the last silent background update check.
     */
    fun shouldRunSilentCheck(): Boolean {
        val p = prefs ?: return true
        val lastCheck = p.getLong(KEY_LAST_SILENT_CHECK_MS, 0L)
        val now = System.currentTimeMillis()
        return (now - lastCheck) >= SILENT_CHECK_INTERVAL_MS
    }

    fun markSilentCheckCompleted() {
        prefs?.edit()?.putLong(KEY_LAST_SILENT_CHECK_MS, System.currentTimeMillis())?.apply()
    }

    /**
     * Deletes any previously downloaded APK files from the app's cache / external files update directory
     * so old update installers do not consume storage after an update completes.
     */
    fun cleanupOldApks() {
        val ctx = context ?: return
        runCatching {
            val dirs = listOfNotNull(
                File(ctx.cacheDir, "updates"),
                ctx.externalCacheDir?.let { File(it, "updates") },
                ctx.getExternalFilesDir(null)?.let { File(it, "updates") }
            )
            val currentVer = BuildConfig.VERSION_NAME.removePrefix("v").removePrefix("V").trim()
            for (dir in dirs) {
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.forEach { file ->
                        if (file.extension.equals("apk", ignoreCase = true)) {
                            val ageMs = System.currentTimeMillis() - file.lastModified()
                            if (file.name.contains(currentVer) || ageMs > 24 * 60 * 60 * 1000L) {
                                file.delete()
                            }
                        } else if (file.name.endsWith(".part", ignoreCase = true)) {
                            val ageMs = System.currentTimeMillis() - file.lastModified()
                            if (ageMs > 12 * 60 * 60 * 1000L) {
                                file.delete()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun getAuthToken(): String? {
        val token = runCatching { BuildConfig.GITHUB_TOKEN.trim() }.getOrNull()
        return if (!token.isNullOrBlank() && !token.startsWith("YOUR_") && token != "null") token else null
    }

    private fun addUpdateHeaders(builder: Request.Builder): Request.Builder {
        builder.header("Accept", "application/vnd.github+json")
        builder.header("User-Agent", "ZEDCraft-Android/${BuildConfig.VERSION_NAME}")
        val token = getAuthToken()
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        return builder
    }

    /**
     * Checks the update source for a newer version of ZEDCraft using semantic version comparison.
     */
    suspend fun checkForUpdates(
        repoInput: String = DEFAULT_GITHUB_REPO,
        isSilent: Boolean = false
    ): Result<GitHubReleaseInfo> = withContext(Dispatchers.IO) {
        runCatching {
            if (!isDeviceOnline()) {
                throw IOException("No internet connection.")
            }

            val cleanedRepo = normalizeRepoPath(repoInput)
            val currentVersion = BuildConfig.VERSION_NAME

            if (isSilent) {
                markSilentCheckCompleted()
            }

            // 1. Primary endpoint: /releases/latest
            val latestReleaseUrl = "https://api.github.com/repos/$cleanedRepo/releases/latest"
            val latestReq = addUpdateHeaders(Request.Builder().url(latestReleaseUrl)).build()

            var latestReleaseInfoWithoutApk: GitHubReleaseInfo? = null

            httpClient.newCall(latestReq).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful && body.isNotBlank()) {
                    val obj = JSONObject(body)
                    if (!obj.optBoolean("draft", false)) {
                        val info = parseReleaseObject(cleanedRepo, currentVersion, obj)
                        if (!info.apkDownloadUrl.isNullOrBlank()) {
                            return@runCatching info
                        } else {
                            latestReleaseInfoWithoutApk = info
                        }
                    }
                }
            }

            // 2. Fallback: /releases list (find newest non-draft update that has an APK asset attached)
            val releasesUrl = "https://api.github.com/repos/$cleanedRepo/releases?per_page=10"
            val releasesReq = addUpdateHeaders(Request.Builder().url(releasesUrl)).build()

            httpClient.newCall(releasesReq).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful && body.isNotBlank()) {
                    val arr = JSONArray(body)
                    for (i in 0 until arr.length()) {
                        val rel = arr.getJSONObject(i)
                        if (!rel.optBoolean("draft", false)) {
                            val parsed = parseReleaseObject(cleanedRepo, currentVersion, rel)
                            if (!parsed.apkDownloadUrl.isNullOrBlank()) {
                                return@runCatching parsed
                            }
                        }
                    }
                }
            }

            if (latestReleaseInfoWithoutApk != null) {
                return@runCatching latestReleaseInfoWithoutApk!!
            }

            // 3. Fallback: tags list
            val tagsUrl = "https://api.github.com/repos/$cleanedRepo/tags?per_page=5"
            val tagsReq = addUpdateHeaders(Request.Builder().url(tagsUrl)).build()

            var latestTagFromTags: String? = null
            httpClient.newCall(tagsReq).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful && body.isNotBlank()) {
                    val arr = JSONArray(body)
                    if (arr.length() > 0) {
                        latestTagFromTags = arr.getJSONObject(0).optString("name").takeIf { it.isNotBlank() }
                    }
                }
            }

            val tag = latestTagFromTags ?: "v$currentVersion"
            val isNewer = latestTagFromTags != null && compareVersions(tag, currentVersion) > 0

            GitHubReleaseInfo(
                repository = cleanedRepo,
                latestTag = tag,
                releaseTitle = "ZEDCraft ${tag.removePrefix("v").removePrefix("V")}",
                releaseNotes = "You are running the latest version of ZEDCraft.",
                publishedAt = "Up to date",
                htmlUrl = "",
                apkDownloadUrl = null,
                apkFileName = null,
                apkSizeBytes = 0L,
                isUpdateAvailable = isNewer,
                currentVersion = currentVersion
            )
        }
    }

    /**
     * Downloads the update `.apk` inside the app (with HTTP Range resume support, storage checks,
     * and ZIP/APK magic header integrity verification) and emits real-time progress.
     */
    suspend fun downloadUpdateApk(
        apkUrl: String,
        versionTag: String,
        expectedSizeBytes: Long = 0L,
        onProgress: (ApkDownloadProgress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val ctx = context ?: throw IllegalStateException("Application context unavailable for update download.")
            if (!isDeviceOnline()) {
                throw IOException("Connection lost")
            }

            val baseDir = ctx.externalCacheDir ?: ctx.cacheDir
            val updatesDir = File(baseDir, "updates").apply {
                if (!exists()) mkdirs()
            }

            // Check storage space before downloading
            val availableBytes = runCatching { StatFs(updatesDir.absolutePath).availableBytes }.getOrDefault(Long.MAX_VALUE)
            val requiredBytes = if (expectedSizeBytes > 0L) expectedSizeBytes + (15L * 1024L * 1024L) else 60L * 1024L * 1024L
            if (availableBytes < requiredBytes) {
                throw IllegalStateException("Not enough storage space to download this update. Please free up at least ${requiredBytes / (1024 * 1024)} MB and try again.")
            }

            val sanitizedTag = versionTag.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val targetFile = File(updatesDir, "ZEDCraft-$sanitizedTag.apk")
            val tempFile = File(updatesDir, "ZEDCraft-$sanitizedTag.apk.part")

            // If targetFile already exists and passes verification, return it immediately
            if (targetFile.exists() && isValidApkFile(targetFile, expectedSizeBytes)) {
                val mb = targetFile.length().toFloat() / (1024f * 1024f)
                onProgress(ApkDownloadProgress(percent = 100, downloadedMb = mb, totalMb = mb))
                return@runCatching targetFile
            }

            var existingBytes = if (tempFile.exists()) tempFile.length().coerceAtLeast(0L) else 0L
            if (expectedSizeBytes > 0L && existingBytes >= expectedSizeBytes) {
                // Reset if previous part file is larger than expected
                tempFile.delete()
                existingBytes = 0L
            }

            val reqBuilder = Request.Builder()
                .url(apkUrl)
                .header("Accept", "application/octet-stream, application/vnd.android.package-archive, */*")
                .header("User-Agent", "ZEDCraft-Android/${BuildConfig.VERSION_NAME}")

            val token = getAuthToken()
            if (!token.isNullOrBlank()) {
                reqBuilder.header("Authorization", "Bearer $token")
            }
            if (existingBytes > 0L) {
                reqBuilder.header("Range", "bytes=$existingBytes-")
            }

            try {
                httpClient.newCall(reqBuilder.build()).execute().use { response ->
                    if (response.code == 416) {
                        // Range not satisfiable; delete .part and retry cleanly
                        tempFile.delete()
                        throw IOException("Connection lost")
                    }
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Update download failed (HTTP ${response.code}). Please tap Retry.")
                    }

                    val body = response.body ?: throw IllegalStateException("Empty update response from server.")
                    val isResumed = response.code == 206 && existingBytes > 0L
                    val startOffset = if (isResumed) existingBytes else 0L
                    val remainingBytes = body.contentLength()
                    val totalBytes = when {
                        remainingBytes > 0L -> startOffset + remainingBytes
                        expectedSizeBytes > 0L -> expectedSizeBytes
                        else -> -1L
                    }
                    val totalMb = if (totalBytes > 0L) totalBytes.toFloat() / (1024f * 1024f) else 0f

                    if (startOffset > 0L && totalBytes > 0L) {
                        val initMb = startOffset.toFloat() / (1024f * 1024f)
                        val initPct = ((startOffset * 100L) / totalBytes).toInt().coerceIn(0, 99)
                        onProgress(ApkDownloadProgress(percent = initPct, downloadedMb = initMb, totalMb = totalMb))
                    }

                    body.byteStream().use { input ->
                        FileOutputStream(tempFile, isResumed).use { output ->
                            val buffer = ByteArray(16 * 1024)
                            var downloadedBytes = startOffset
                            var bytesRead: Int
                            var lastEmitMs = 0L

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                coroutineContext.ensureActive()
                                output.write(buffer, 0, bytesRead)
                                downloadedBytes += bytesRead

                                val now = System.currentTimeMillis()
                                if (now - lastEmitMs >= 70L || (totalBytes > 0L && downloadedBytes >= totalBytes)) {
                                    lastEmitMs = now
                                    val downloadedMb = downloadedBytes.toFloat() / (1024f * 1024f)
                                    val pct = if (totalBytes > 0L) {
                                        ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                                    } else {
                                        -1
                                    }
                                    onProgress(
                                        ApkDownloadProgress(
                                            percent = pct,
                                            downloadedMb = downloadedMb,
                                            totalMb = if (totalMb > 0f) totalMb else downloadedMb
                                        )
                                    )
                                }
                            }
                            output.flush()
                        }
                    }

                    // Verify full download length against server contentLength
                    if (totalBytes > 0L && tempFile.length() < totalBytes) {
                        throw IOException("Connection lost")
                    }
                }
            } catch (ioe: IOException) {
                throw IllegalStateException("Connection lost. Check your internet connection and tap Retry to resume.")
            }

            // Verify APK file integrity (ZIP/APK magic bytes and minimum size) before installing
            if (!isValidApkFile(tempFile, expectedSizeBytes)) {
                tempFile.delete()
                throw IllegalStateException("Downloaded update file was incomplete or corrupted. Please tap Retry.")
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            targetFile
        }.onFailure { err ->
            if (err is CancellationException) throw err
            Log.e(TAG, "In-app update download failed", err)
        }
    }

    /**
     * Verifies that the downloaded file is non-empty, matches expected size (if known),
     * and begins with standard APK/ZIP magic bytes (`PK\x03\x04`).
     */
    private fun isValidApkFile(file: File, expectedSizeBytes: Long): Boolean {
        if (!file.exists() || file.length() < 100_000L) return false
        if (expectedSizeBytes > 0L && file.length() < (expectedSizeBytes * 0.98).toLong()) {
            return false
        }
        return runCatching {
            val header = ByteArray(4)
            FileInputStream(file).use { fis ->
                if (fis.read(header) != 4) return@runCatching false
            }
            header[0] == 0x50.toByte() &&
                header[1] == 0x4B.toByte() &&
                header[2] == 0x03.toByte() &&
                header[3] == 0x04.toByte()
        }.getOrDefault(false)
    }

    /**
     * Checks whether the user has granted permission to install packages from this source on Android 8.0+.
     */
    fun canRequestPackageInstalls(ctx: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Builds the system Settings intent for `ACTION_MANAGE_UNKNOWN_APP_SOURCES` for this package.
     */
    fun buildManageUnknownSourcesIntent(ctx: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${ctx.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /**
     * Builds a proper `FileProvider` `content://` URI Intent with `FLAG_GRANT_READ_URI_PERMISSION`
     * to launch Android's native Package Installer for the downloaded `.apk`.
     */
    fun buildInstallApkIntent(ctx: Context, apkFile: File): Intent {
        val authority = "${ctx.packageName}.fileprovider"
        val contentUri: Uri = FileProvider.getUriForFile(ctx, authority, apkFile)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun parseReleaseObject(
        repo: String,
        currentVersion: String,
        obj: JSONObject
    ): GitHubReleaseInfo {
        val tagName = obj.optString("tag_name", "v$currentVersion").ifBlank { "v$currentVersion" }
        val cleanVer = tagName.removePrefix("v").removePrefix("V")
        val rawName = obj.optString("name", "ZEDCraft $cleanVer").ifBlank { "ZEDCraft $cleanVer" }
        val rawNotes = obj.optString("body", "").ifBlank {
            "New ZEDCraft update ($cleanVer) with performance improvements and smart suggestions."
        }
        val cleanNotes = sanitizeUpdateNotes(rawNotes)
        val publishedAt = obj.optString("published_at", "").take(10).ifBlank { "Latest" }

        var apkUrl: String? = null
        var apkName: String? = null
        var apkSize = 0L
        val assets = obj.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val assetName = asset.optString("name", "")
                val downloadUrl = asset.optString("browser_download_url", "")
                if (assetName.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotBlank()) {
                    apkUrl = downloadUrl
                    apkName = assetName
                    apkSize = asset.optLong("size", 0L)
                    break
                }
            }
        }

        val isUpdateAvailable = compareVersions(tagName, currentVersion) > 0

        return GitHubReleaseInfo(
            repository = repo,
            latestTag = tagName,
            releaseTitle = sanitizeUpdateNotes(rawName),
            releaseNotes = cleanNotes,
            publishedAt = publishedAt,
            htmlUrl = "",
            apkDownloadUrl = apkUrl,
            apkFileName = apkName,
            apkSizeBytes = apkSize,
            isUpdateAvailable = isUpdateAvailable,
            currentVersion = currentVersion
        )
    }

    private fun sanitizeUpdateNotes(text: String): String {
        return text
            .replace(Regex("(?i)github\\s+release\\s*"), "ZEDCraft update ")
            .replace(Regex("(?i)github\\s+repository"), "ZEDCraft")
            .replace(Regex("(?i)github"), "ZEDCraft")
            .replace(Regex("(?i)repositories"), "updates")
            .replace(Regex("(?i)repository"), "app")
            .replace(Regex("(?i)releases"), "updates")
            .replace(Regex("(?i)release\\s+notes"), "What's new")
            .trim()
    }

    private fun normalizeRepoPath(input: String): String {
        val trimmed = input.trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("github.com/")
            .removeSuffix(".git")
            .trim('/')
        val parts = trimmed.split("/").filter { it.isNotBlank() }
        return if (parts.size >= 2) {
            "${parts[0]}/${parts[1]}"
        } else {
            DEFAULT_GITHUB_REPO
        }
    }

    companion object {
        private const val TAG = "ZedCraftAutoUpdate"
        const val DEFAULT_GITHUB_REPO = "khalil4ever/ZEDCraft-"
        private const val PREFS_NAME = "zedcraft_auto_update_prefs"
        private const val KEY_LAST_SILENT_CHECK_MS = "last_silent_check_ms"
        private const val SILENT_CHECK_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes fallback interval

        /**
         * Proper semantic version comparison (e.g., "v1.2.1" > "1.2.0", "v1.10.0" > "v1.9.0").
         */
        fun compareVersions(remoteTag: String, localVersion: String): Int {
            val cleanRemote = remoteTag.trim().removePrefix("v").removePrefix("V").substringBefore("-")
            val cleanLocal = localVersion.trim().removePrefix("v").removePrefix("V").substringBefore("-")
            if (cleanRemote == cleanLocal) return 0

            val remoteParts = cleanRemote.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            val localParts = cleanLocal.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            val maxLen = maxOf(remoteParts.size, localParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val l = localParts.getOrElse(i) { 0 }
                if (r != l) return r.compareTo(l)
            }
            return 0
        }
    }
}
