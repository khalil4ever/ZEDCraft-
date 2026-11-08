package com.example.data.remote

import android.content.Context
import com.example.BuildConfig
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class GitHubUpdateInfo(
    val currentVersion: String,
    val latestVersionTag: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val publishedAt: String,
    val apkDownloadUrl: String?,
    val isUpdateAvailable: Boolean,
    val statusSummary: String
)

class GitHubUpdateClient(context: Context) {

    private val prefs = context.getSharedPreferences("zedcraft_github_prefs", Context.MODE_PRIVATE)

    // Internal hidden repository & token resolution (never displayed in the UI)
    fun getConfiguredRepo(): String {
        val fromBuild = runCatching { BuildConfig.GITHUB_REPO.trim() }.getOrDefault("")
        if (fromBuild.isNotBlank() && !fromBuild.startsWith("YOUR_")) {
            return normalizeRepoSlug(fromBuild)
        }
        val saved = prefs.getString("github_repo", null)
        if (!saved.isNullOrBlank()) return normalizeRepoSlug(saved)
        return "khalil4ever/ZEDCraft-"
    }

    fun getConfiguredToken(): String {
        val fromBuild = runCatching { BuildConfig.GITHUB_TOKEN.trim() }.getOrDefault("")
        if (fromBuild.isNotBlank() && !fromBuild.startsWith("YOUR_")) {
            return fromBuild
        }
        return prefs.getString("github_token", null).orEmpty()
    }

    private fun normalizeRepoSlug(raw: String): String {
        return raw.trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("github.com/")
            .removeSuffix(".git")
            .trim('/')
    }

    /**
     * Checks the hidden release repository in the background without exposing
     * repository names, URLs, or credentials in the UI.
     */
    suspend fun checkForUpdates(): Result<GitHubUpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val slug = getConfiguredRepo()
            val token = getConfiguredToken()
            val currentVer = BuildConfig.VERSION_NAME

            val repoUrl = "https://api.github.com/repos/$slug"
            val repoResp = executeGet(repoUrl, token)
            if (repoResp.first == 404) {
                throw IllegalStateException(
                    "Update server is not reachable yet. Please verify your update configuration."
                )
            } else if (repoResp.first !in 200..299) {
                throw IllegalStateException("Update check failed (HTTP ${repoResp.first}). Please try again later.")
            }

            // Check latest formal Release
            val releaseUrl = "https://api.github.com/repos/$slug/releases/latest"
            val releaseResp = executeGet(releaseUrl, token)

            // Check latest commit metadata as fallback
            val commitsUrl = "https://api.github.com/repos/$slug/commits?per_page=1"
            val commitsResp = executeGet(commitsUrl, token)
            var latestSha = ""
            var latestCommitMsg = ""
            var commitDate = ""

            if (commitsResp.first in 200..299) {
                val arr = runCatching { JSONArray(commitsResp.second) }.getOrNull()
                val firstCommit = arr?.optJSONObject(0)
                if (firstCommit != null) {
                    latestSha = firstCommit.optString("sha", "").take(7)
                    val commitObj = firstCommit.optJSONObject("commit")
                    latestCommitMsg = commitObj?.optString("message", "")?.lines()?.firstOrNull().orEmpty()
                    commitDate = commitObj?.optJSONObject("committer")?.optString("date", "")?.take(10).orEmpty()
                }
            }

            if (releaseResp.first in 200..299) {
                val relJson = JSONObject(releaseResp.second)
                val tag = relJson.optString("tag_name", "v$currentVer")
                val name = relJson.optString("name", tag)
                val body = relJson.optString("body", "Bug fixes and performance improvements.")
                val pubDate = relJson.optString("published_at", "").take(10)

                var apkUrl: String? = null
                val assets = relJson.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val assetName = asset.optString("name", "")
                        if (assetName.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url")
                            break
                        }
                    }
                }

                val cleanTag = tag.removePrefix("v").removePrefix("V").trim()
                val cleanCurrent = currentVer.removePrefix("v").removePrefix("V").trim()
                val isNewer = cleanTag.isNotBlank() && cleanTag != cleanCurrent && compareVersions(cleanTag, cleanCurrent) > 0

                GitHubUpdateInfo(
                    currentVersion = "v$cleanCurrent",
                    latestVersionTag = tag,
                    releaseTitle = if (isNewer) "Update Available: $name" else "ZEDCraft is Up to Date",
                    releaseNotes = body,
                    publishedAt = pubDate.ifBlank { commitDate },
                    apkDownloadUrl = apkUrl,
                    isUpdateAvailable = isNewer,
                    statusSummary = if (isNewer) {
                        "A new version of ZEDCraft ($tag) is ready to download."
                    } else {
                        "You are running the latest version of ZEDCraft (v$cleanCurrent)."
                    }
                )
            } else {
                GitHubUpdateInfo(
                    currentVersion = "v$currentVer",
                    latestVersionTag = if (latestSha.isNotBlank()) "v$currentVer ($latestSha)" else "v$currentVer",
                    releaseTitle = "ZEDCraft is Up to Date",
                    releaseNotes = if (latestCommitMsg.isNotBlank()) {
                        "Latest build note: $latestCommitMsg"
                    } else {
                        "All features and AI models are up to date."
                    },
                    publishedAt = commitDate,
                    apkDownloadUrl = null,
                    isUpdateAvailable = false,
                    statusSummary = "You are running the latest version of ZEDCraft (v$currentVer)."
                )
            }
        }
    }

    private fun executeGet(urlStr: String, token: String): Pair<Int, String> {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 12000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            if (token.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer $token")
            }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.let {
            BufferedReader(InputStreamReader(it)).use { r -> r.readText() }
        }.orEmpty()
        return code to body
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".", "-").mapNotNull { it.toIntOrNull() }
        val parts2 = v2.split(".", "-").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1.compareTo(p2)
        }
        return v1.compareTo(v2)
    }
}
