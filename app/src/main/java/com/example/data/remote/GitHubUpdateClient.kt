package com.example.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.BuildConfig
import java.io.File
import java.io.FileOutputStream
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
)

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
     * Checks if enough time has elapsed since the last silent background update check
     * (throttled to once every 3 hours so we never spam GitHub API).
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
                            // Delete if it matches current or older version, or is older than 24h
                            val ageMs = System.currentTimeMillis() - file.lastModified()
                            if (file.name.contains(currentVer) || ageMs > 24 * 60 * 60 * 1000L) {
                                file.delete()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun getGitHubToken(): String? {
        val token = runCatching { BuildConfig.GITHUB_TOKEN.trim() }.getOrNull()
        return if (!token.isNullOrBlank() && !token.startsWith("YOUR_") && token != "null") token else null
    }

    private fun addGitHubHeaders(builder: Request.Builder): Request.Builder {
        builder.header("Accept", "application/vnd.github+json")
        builder.header("User-Agent", "ZEDCraft-Android/${BuildConfig.VERSION_NAME}")
        val token = getGitHubToken()
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        return builder
    }

    /**
     * Checks `https://api.github.com/repos/khalil4ever/ZEDCraft-/releases/latest` first,
     * reading `tag_name`, `body`, and `.apk` asset `browser_download_url`.
     * Compares `tag_name` against `BuildConfig.VERSION_NAME` using semantic version comparison.
     */
    suspend fun checkForUpdates(
        repoInput: String = DEFAULT_GITHUB_REPO,
        isSilent: Boolean = false
    ): Result<GitHubReleaseInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanedRepo = normalizeRepoPath(repoInput)
            val currentVersion = BuildConfig.VERSION_NAME

            if (isSilent) {
                markSilentCheckCompleted()
            }

            // 1. Primary endpoint: /releases/latest
            val latestReleaseUrl = "https://api.github.com/repos/$cleanedRepo/releases/latest"
            val latestReq = addGitHubHeaders(Request.Builder().url(latestReleaseUrl)).build()

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

            // 2. Fallback: /releases list (find newest non-draft release that has an APK asset attached)
            val releasesUrl = "https://api.github.com/repos/$cleanedRepo/releases?per_page=10"
            val releasesReq = addGitHubHeaders(Request.Builder().url(releasesUrl)).build()

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

            // If we found a latest release without an APK asset, still return it rather than tags
            if (latestReleaseInfoWithoutApk != null) {
                return@runCatching latestReleaseInfoWithoutApk!!
            }

            // 3. Fallback: tags list
            val tagsUrl = "https://api.github.com/repos/$cleanedRepo/tags?per_page=5"
            val tagsReq = addGitHubHeaders(Request.Builder().url(tagsUrl)).build()

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
                releaseTitle = "ZEDCraft $tag",
                releaseNotes = "You are running the latest version of ZEDCraft.",
                publishedAt = "Up to date",
                htmlUrl = "https://github.com/$cleanedRepo/releases",
                apkDownloadUrl = null,
                apkFileName = null,
                apkSizeBytes = 0L,
                isUpdateAvailable = isNewer,
                currentVersion = currentVersion
            )
        }
    }

    /**
     * Downloads the release `.apk` inside the app (never opening the browser) into the app's
     * cache/updates folder and emits real-time progress (percentage, downloaded MB, total MB).
     */
    suspend fun downloadUpdateApk(
        apkUrl: String,
        versionTag: String,
        onProgress: (ApkDownloadProgress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val ctx = context ?: throw IllegalStateException("Application context unavailable for APK download.")
            val baseDir = ctx.externalCacheDir ?: ctx.cacheDir
            val updatesDir = File(baseDir, "updates").apply {
                if (!exists()) mkdirs()
            }

            val sanitizedTag = versionTag.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val targetFile = File(updatesDir, "ZEDCraft-$sanitizedTag.apk")
            val tempFile = File(updatesDir, "ZEDCraft-$sanitizedTag.apk.part")
            if (tempFile.exists()) {
                tempFile.delete()
            }

            val reqBuilder = Request.Builder()
                .url(apkUrl)
                .header("Accept", "application/octet-stream, application/vnd.android.package-archive, */*")
                .header("User-Agent", "ZEDCraft-Android/${BuildConfig.VERSION_NAME}")

            val token = getGitHubToken()
            if (!token.isNullOrBlank()) {
                reqBuilder.header("Authorization", "Bearer $token")
            }

            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Download failed (HTTP ${response.code}). Please try again.")
                }
                val body = response.body ?: throw IllegalStateException("Empty APK response from server.")
                val totalBytes = body.contentLength()
                val totalMb = if (totalBytes > 0) totalBytes.toFloat() / (1024f * 1024f) else 0f

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var downloadedBytes = 0L
                        var bytesRead: Int
                        var lastEmitMs = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            coroutineContext.ensureActive()
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            val now = System.currentTimeMillis()
                            if (now - lastEmitMs >= 80L || downloadedBytes == totalBytes) {
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
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            if (!targetFile.exists() || targetFile.length() < 10_000L) {
                throw IllegalStateException("Downloaded APK file is incomplete or corrupted.")
            }

            targetFile
        }.onFailure { err ->
            if (err is CancellationException) throw err
            Log.e(TAG, "In-app APK download failed", err)
        }
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
        val name = obj.optString("name", tagName).ifBlank { tagName }
        val notes = obj.optString("body", "").ifBlank {
            "New ZEDCraft update ($tagName) with performance improvements and new features."
        }
        val publishedAt = obj.optString("published_at", "").take(10).ifBlank { "Latest" }
        val htmlUrl = obj.optString("html_url", "https://github.com/$repo/releases")

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
            releaseTitle = name,
            releaseNotes = notes,
            publishedAt = publishedAt,
            htmlUrl = htmlUrl,
            apkDownloadUrl = apkUrl,
            apkFileName = apkName,
            apkSizeBytes = apkSize,
            isUpdateAvailable = isUpdateAvailable,
            currentVersion = currentVersion
        )
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
        private const val SILENT_CHECK_INTERVAL_MS = 3 * 60 * 60 * 1000L // 3 hours

        /**
         * Proper semantic version comparison (e.g., "v1.2.1" > "1.2.0", "v1.10.0" > "v1.2.9").
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
