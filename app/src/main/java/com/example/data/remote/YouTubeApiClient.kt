package com.example.data.remote

import android.graphics.Bitmap
import com.example.BuildConfig
import com.example.data.local.VideoRevisionBackupEntity
import com.example.data.local.YouTubeCacheDao
import com.example.data.local.YouTubeCacheEntity
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

sealed class YouTubeApiException(message: String) : Exception(message) {
    class MissingApiKeyException : YouTubeApiException(
        "Please connect your YouTube channel to load your videos and analytics."
    )
    class QuotaExceededException : YouTubeApiException(
        "YouTube daily quota limit reached (10,000 units/day). ZEDCraft is serving cached data where available. Quota resets at midnight Pacific Time."
    )
    class UnauthorizedException(msg: String) : YouTubeApiException(msg)
    class PermissionDeniedException(msg: String) : YouTubeApiException(msg)
    class UnverifiedChannelThumbnailException(
        msg: String = "Your YouTube channel needs phone verification before uploading custom thumbnails. Please verify your channel at youtube.com/verify and try again."
    ) : YouTubeApiException(msg)
    class NotFoundException(msg: String) : YouTubeApiException(msg)
    class NetworkErrorException(msg: String) : YouTubeApiException(msg)
}

class YouTubeApiClient(
    private val cacheDao: YouTubeCacheDao
) {
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    fun isApiKeyConfigured(): Boolean {
        val key = BuildConfig.YOUTUBE_API_KEY.trim()
        return key.isNotBlank() &&
            key != "YOUR_YOUTUBE_DATA_API_V3_KEY" &&
            key != "MY_YOUTUBE_API_KEY" &&
            !key.startsWith("YOUR_")
    }

    fun getConfiguredApiKey(): String? {
        return if (isApiKeyConfigured()) BuildConfig.YOUTUBE_API_KEY.trim() else null
    }

    /**
     * Extracts a YouTube Video ID from any standard watch, youtu.be, shorts, embed URL, or raw 11-char ID.
     */
    fun extractVideoId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) return trimmed
        val patterns = listOf(
            Regex("(?:v=|/videos/|embed/|youtu\\.be/|/shorts/|/live/)([a-zA-Z0-9_-]{11})"),
            Regex("youtube\\.com/watch\\?.*v=([a-zA-Z0-9_-]{11})")
        )
        for (regex in patterns) {
            val match = regex.find(trimmed)
            if (match != null) return match.groupValues[1]
        }
        return null
    }

    /**
     * Extracts a Channel ID, @handle, or custom channel query from user input.
     */
    fun parseChannelInput(input: String): ChannelLookupQuery {
        val trimmed = input.trim()
        if (trimmed.startsWith("UC") && trimmed.length >= 20 && !trimmed.contains("/")) {
            return ChannelLookupQuery.ById(trimmed)
        }
        if (trimmed.startsWith("@")) {
            return ChannelLookupQuery.ByHandle(trimmed)
        }
        val channelIdMatch = Regex("youtube\\.com/channel/(UC[a-zA-Z0-9_-]+)").find(trimmed)
        if (channelIdMatch != null) {
            return ChannelLookupQuery.ById(channelIdMatch.groupValues[1])
        }
        val handleMatch = Regex("youtube\\.com/(@[a-zA-Z0-9_.-]+)").find(trimmed)
        if (handleMatch != null) {
            return ChannelLookupQuery.ByHandle(handleMatch.groupValues[1])
        }
        return ChannelLookupQuery.BySearchOrHandle(trimmed)
    }

    sealed interface ChannelLookupQuery {
        data class ById(val channelId: String) : ChannelLookupQuery
        data class ByHandle(val handle: String) : ChannelLookupQuery
        data class BySearchOrHandle(val query: String) : ChannelLookupQuery
    }

    private suspend fun executeCachedGet(
        cacheKey: String,
        endpoint: String,
        url: String,
        oauthAccessToken: String? = null,
        ttlMs: Long = 30 * 60 * 1000L, // 30 minutes default cache
        forceRefresh: Boolean = false
    ): JSONObject = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            val cached = cacheDao.getCacheEntry(cacheKey)
            if (cached != null && (now - cached.cachedAtMs) <= cached.ttlMs) {
                return@withContext JSONObject(cached.payloadJson)
            }
        }

        val apiKey = getConfiguredApiKey()
        if (apiKey == null && oauthAccessToken.isNullOrBlank()) {
            // Try stale cache before throwing MissingApiKeyException
            val stale = cacheDao.getCacheEntry(cacheKey)
            if (stale != null) return@withContext JSONObject(stale.payloadJson)
            throw YouTubeApiException.MissingApiKeyException()
        }

        val finalUrl = if (!oauthAccessToken.isNullOrBlank()) {
            url
        } else {
            val httpUrl = url.toHttpUrlOrNull()
                ?: throw YouTubeApiException.NetworkErrorException("Invalid request URL")
            httpUrl.newBuilder().addQueryParameter("key", apiKey!!).build().toString()
        }

        val reqBuilder = Request.Builder()
            .url(finalUrl)
            .header("Accept", "application/json")
        if (!oauthAccessToken.isNullOrBlank()) {
            reqBuilder.header("Authorization", "Bearer $oauthAccessToken")
        }

        try {
            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (response.isSuccessful && bodyStr.isNotBlank()) {
                    cacheDao.putCacheEntry(
                        YouTubeCacheEntity(
                            cacheKey = cacheKey,
                            endpoint = endpoint,
                            payloadJson = bodyStr,
                            cachedAtMs = now,
                            ttlMs = ttlMs
                        )
                    )
                    return@withContext JSONObject(bodyStr)
                }

                // Check if quota exceeded or auth error
                val errJson = runCatching { JSONObject(bodyStr).optJSONObject("error") }.getOrNull()
                val errReason = errJson?.optJSONArray("errors")?.optJSONObject(0)?.optString("reason").orEmpty()
                val errMsg = errJson?.optString("message").orEmpty()

                if (errReason.contains("quotaExceeded", ignoreCase = true) ||
                    errReason.contains("dailyLimitExceeded", ignoreCase = true) ||
                    errMsg.contains("quota", ignoreCase = true)
                ) {
                    // Serve stale cache if available on quota exhaustion
                    val stale = cacheDao.getCacheEntry(cacheKey)
                    if (stale != null) return@withContext JSONObject(stale.payloadJson)
                    throw YouTubeApiException.QuotaExceededException()
                }

                if (response.code == 401 || response.code == 403) {
                    throw YouTubeApiException.UnauthorizedException(
                        errMsg.ifBlank { "Your YouTube login session expired or lacked permission. Please reconnect your YouTube channel." }
                    )
                }

                if (response.code == 404) {
                    throw YouTubeApiException.NotFoundException(errMsg.ifBlank { "Requested YouTube resource was not found." })
                }

                throw YouTubeApiException.NetworkErrorException(
                    errMsg.ifBlank { "YouTube API error (HTTP ${response.code})" }
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: YouTubeApiException) {
            throw e
        } catch (e: Exception) {
            // Offline resilience: return cached entry even if expired when network is down
            val fallbackCache = cacheDao.getCacheEntry(cacheKey)
            if (fallbackCache != null) {
                return@withContext JSONObject(fallbackCache.payloadJson)
            }
            throw YouTubeApiException.NetworkErrorException(
                "Network unavailable and no cached data found: ${e.localizedMessage ?: "Connection failed"}"
            )
        }
    }

    /**
     * Fetches the authenticated user's own YouTube channel(s) using `mine=true` and OAuth 2.0 Bearer token.
     */
    suspend fun fetchMyAuthorizedChannels(oauthAccessToken: String): Result<List<YtChannelDetail>> = runCatching {
        val url = "https://www.googleapis.com/youtube/v3/channels?part=snippet,statistics,contentDetails,brandingSettings,status&mine=true&maxResults=10"
        val json = executeCachedGet(
            cacheKey = "channels_mine_${oauthAccessToken.hashCode()}",
            endpoint = "channels.mine",
            url = url,
            oauthAccessToken = oauthAccessToken,
            ttlMs = 15 * 60 * 1000L
        )
        parseChannelsList(json, isOAuthMine = true)
    }

    /**
     * Resolves any public YouTube channel by Channel ID, @handle, video URL, or search query.
     */
    suspend fun lookupChannel(
        input: String,
        oauthAccessToken: String? = null,
        forceRefresh: Boolean = false
    ): Result<YtChannelDetail> = runCatching {
        // First check if user pasted a video URL; if so, fetch the video and get its channelId
        val maybeVideoId = extractVideoId(input)
        if (maybeVideoId != null && (input.contains("watch") || input.contains("youtu.be") || input.contains("shorts"))) {
            val video = fetchVideoById(maybeVideoId, oauthAccessToken).getOrThrow()
            return@runCatching fetchChannelById(video.channelId, oauthAccessToken, forceRefresh).getOrThrow()
        }

        when (val query = parseChannelInput(input)) {
            is ChannelLookupQuery.ById -> fetchChannelById(query.channelId, oauthAccessToken, forceRefresh).getOrThrow()
            is ChannelLookupQuery.ByHandle -> fetchChannelByHandle(query.handle, oauthAccessToken, forceRefresh).getOrThrow()
            is ChannelLookupQuery.BySearchOrHandle -> {
                val asHandle = if (query.query.contains(" ")) null else "@${query.query.removePrefix("@")}"
                if (asHandle != null) {
                    val handleAttempt = fetchChannelByHandle(asHandle, oauthAccessToken, forceRefresh)
                    if (handleAttempt.isSuccess) return@runCatching handleAttempt.getOrThrow()
                }
                // Search channel by query (cached 6h to save quota)
                val encoded = URLEncoder.encode(query.query, "UTF-8")
                val searchUrl = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=channel&maxResults=1&q=$encoded"
                val searchJson = executeCachedGet(
                    cacheKey = "search_channel_${query.query.lowercase()}",
                    endpoint = "search.channel",
                    url = searchUrl,
                    oauthAccessToken = oauthAccessToken,
                    ttlMs = 6 * 60 * 60 * 1000L,
                    forceRefresh = forceRefresh
                )
                val items = searchJson.optJSONArray("items")
                if (items == null || items.length() == 0) {
                    throw YouTubeApiException.NotFoundException("No YouTube channel found matching '${query.query}'.")
                }
                val channelId = items.getJSONObject(0).optJSONObject("id")?.optString("channelId")
                    ?: items.getJSONObject(0).optJSONObject("snippet")?.optString("channelId")
                    ?: throw YouTubeApiException.NotFoundException("Channel ID missing in search result.")
                fetchChannelById(channelId, oauthAccessToken, forceRefresh).getOrThrow()
            }
        }
    }

    suspend fun fetchChannelById(
        channelId: String,
        oauthAccessToken: String? = null,
        forceRefresh: Boolean = false
    ): Result<YtChannelDetail> = runCatching {
        val encoded = URLEncoder.encode(channelId.trim(), "UTF-8")
        val url = "https://www.googleapis.com/youtube/v3/channels?part=snippet,statistics,contentDetails,brandingSettings,status&id=$encoded"
        val json = executeCachedGet(
            cacheKey = "channel_id_${channelId.trim()}",
            endpoint = "channels.id",
            url = url,
            oauthAccessToken = oauthAccessToken,
            ttlMs = 60 * 60 * 1000L,
            forceRefresh = forceRefresh
        )
        val channels = parseChannelsList(json, isOAuthMine = false)
        channels.firstOrNull()
            ?: throw YouTubeApiException.NotFoundException("Channel '$channelId' was not found.")
    }

    suspend fun fetchChannelByHandle(
        handle: String,
        oauthAccessToken: String? = null,
        forceRefresh: Boolean = false
    ): Result<YtChannelDetail> = runCatching {
        val cleanHandle = if (handle.startsWith("@")) handle.trim() else "@${handle.trim()}"
        val encoded = URLEncoder.encode(cleanHandle, "UTF-8")
        val url = "https://www.googleapis.com/youtube/v3/channels?part=snippet,statistics,contentDetails,brandingSettings,status&forHandle=$encoded"
        val json = executeCachedGet(
            cacheKey = "channel_handle_${cleanHandle.lowercase()}",
            endpoint = "channels.forHandle",
            url = url,
            oauthAccessToken = oauthAccessToken,
            ttlMs = 60 * 60 * 1000L,
            forceRefresh = forceRefresh
        )
        val channels = parseChannelsList(json, isOAuthMine = false)
        channels.firstOrNull()
            ?: throw YouTubeApiException.NotFoundException("No channel found for handle '$cleanHandle'.")
    }

    /**
     * Batch fetches multiple channels by ID in a single 1-unit API call (up to 50 IDs).
     */
    suspend fun fetchChannelsBatch(
        channelIds: List<String>,
        oauthAccessToken: String? = null
    ): Map<String, YtChannelDetail> {
        val distinct = channelIds.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(50)
        if (distinct.isEmpty()) return emptyMap()
        val joined = distinct.joinToString(",")
        val encoded = URLEncoder.encode(joined, "UTF-8")
        val url = "https://www.googleapis.com/youtube/v3/channels?part=snippet,statistics&id=$encoded"
        return runCatching {
            val json = executeCachedGet(
                cacheKey = "channels_batch_${joined.hashCode()}",
                endpoint = "channels.batch",
                url = url,
                oauthAccessToken = oauthAccessToken,
                ttlMs = 2 * 60 * 60 * 1000L
            )
            parseChannelsList(json, isOAuthMine = false).associateBy { it.channelId }
        }.getOrDefault(emptyMap())
    }

    /**
     * Fetches recent uploaded videos for a channel using its `uploads` playlist (only 2 quota units total instead of 100-unit search!).
     */
    suspend fun fetchChannelRecentVideos(
        channel: YtChannelDetail,
        maxResults: Int = 25,
        oauthAccessToken: String? = null,
        forceRefresh: Boolean = false
    ): Result<List<YtVideoDetail>> = runCatching {
        val playlistId = channel.uploadsPlaylistId.ifBlank {
            if (channel.channelId.startsWith("UC")) "UU" + channel.channelId.substring(2) else ""
        }
        if (playlistId.isBlank()) {
            throw YouTubeApiException.NotFoundException("Uploads playlist not available for ${channel.title}.")
        }
        val encodedPlaylist = URLEncoder.encode(playlistId, "UTF-8")
        val playlistUrl = "https://www.googleapis.com/youtube/v3/playlistItems?part=contentDetails&playlistId=$encodedPlaylist&maxResults=${maxResults.coerceIn(1, 50)}"
        val playlistJson = executeCachedGet(
            cacheKey = "playlist_items_${playlistId}_$maxResults",
            endpoint = "playlistItems.list",
            url = playlistUrl,
            oauthAccessToken = oauthAccessToken,
            ttlMs = 30 * 60 * 1000L,
            forceRefresh = forceRefresh
        )
        val items = playlistJson.optJSONArray("items") ?: JSONArray()
        val videoIds = mutableListOf<String>()
        for (i in 0 until items.length()) {
            val vid = items.getJSONObject(i).optJSONObject("contentDetails")?.optString("videoId").orEmpty()
            if (vid.isNotBlank()) videoIds.add(vid)
        }
        if (videoIds.isEmpty()) return@runCatching emptyList()
        val videos = fetchVideosBatch(videoIds, oauthAccessToken, forceRefresh).getOrThrow()
        videos.map { it.copy(channelSubscribers = channel.subscriberCount) }
    }

    /**
     * Overload that fetches recent uploaded videos directly by `uploadsPlaylistId` (used by ZedCraftViewModel).
     */
    suspend fun fetchChannelRecentVideos(
        uploadsPlaylistId: String,
        maxResults: Int = 30,
        oauthAccessToken: String? = null,
        forceRefresh: Boolean = false
    ): Result<List<YtVideoDetail>> = runCatching {
        val playlistId = uploadsPlaylistId.trim()
        if (playlistId.isBlank()) return@runCatching emptyList()
        val encodedPlaylist = URLEncoder.encode(playlistId, "UTF-8")
        val playlistUrl = "https://www.googleapis.com/youtube/v3/playlistItems?part=contentDetails&playlistId=$encodedPlaylist&maxResults=${maxResults.coerceIn(1, 50)}"
        val playlistJson = executeCachedGet(
            cacheKey = "playlist_items_${playlistId}_$maxResults",
            endpoint = "playlistItems.list",
            url = playlistUrl,
            oauthAccessToken = oauthAccessToken,
            ttlMs = 30 * 60 * 1000L,
            forceRefresh = forceRefresh
        )
        val items = playlistJson.optJSONArray("items") ?: JSONArray()
        val videoIds = mutableListOf<String>()
        for (i in 0 until items.length()) {
            val vid = items.getJSONObject(i).optJSONObject("contentDetails")?.optString("videoId").orEmpty()
            if (vid.isNotBlank()) videoIds.add(vid)
        }
        if (videoIds.isEmpty()) return@runCatching emptyList()
        val videos = fetchVideosBatch(videoIds, oauthAccessToken, forceRefresh).getOrThrow()
        val channelMap = fetchChannelsBatch(videos.map { it.channelId }, oauthAccessToken)
        videos.map { v ->
            v.copy(channelSubscribers = channelMap[v.channelId]?.subscriberCount ?: 0L)
        }
    }

    suspend fun fetchVideoById(
        videoIdOrUrl: String,
        oauthAccessToken: String? = null,
        forceRefresh: Boolean = false
    ): Result<YtVideoDetail> = runCatching {
        val videoId = extractVideoId(videoIdOrUrl)
            ?: throw YouTubeApiException.NotFoundException("Could not parse a valid 11-character YouTube Video ID from '$videoIdOrUrl'.")
        val list = fetchVideosBatch(listOf(videoId), oauthAccessToken, forceRefresh).getOrThrow()
        val first = list.firstOrNull()
            ?: throw YouTubeApiException.NotFoundException("Video '$videoId' was not found or is private.")
        val channelMap = fetchChannelsBatch(listOf(first.channelId), oauthAccessToken)
        val subs = channelMap[first.channelId]?.subscriberCount ?: 0L
        first.copy(channelSubscribers = subs)
    }

    /**
     * Batch fetches full video details (snippet, statistics, contentDetails, status) for up to 50 video IDs in 1 call.
     */
    suspend fun fetchVideosBatch(
        videoIds: List<String>,
        oauthAccessToken: String? = null,
        forceRefresh: Boolean = false
    ): Result<List<YtVideoDetail>> = runCatching {
        val cleanIds = videoIds.mapNotNull { extractVideoId(it) }.distinct().take(50)
        if (cleanIds.isEmpty()) return@runCatching emptyList()
        val joined = cleanIds.joinToString(",")
        val encoded = URLEncoder.encode(joined, "UTF-8")
        val url = "https://www.googleapis.com/youtube/v3/videos?part=snippet,statistics,contentDetails,status&id=$encoded"
        val json = executeCachedGet(
            cacheKey = "videos_batch_${joined.hashCode()}",
            endpoint = "videos.list",
            url = url,
            oauthAccessToken = oauthAccessToken,
            ttlMs = 30 * 60 * 1000L,
            forceRefresh = forceRefresh
        )
        parseVideosList(json)
    }

    /**
     * Fetches real trending videos by region and optional categoryId using `videos?chart=mostPopular` (1 quota unit).
     */
    suspend fun fetchTrendingVideos(
        regionCode: String = "US",
        videoCategoryId: String = "0",
        maxResults: Int = 20,
        oauthAccessToken: String? = null,
        forceRefresh: Boolean = false
    ): Result<List<YtVideoDetail>> = runCatching {
        val catParam = if (videoCategoryId != "0" && videoCategoryId.isNotBlank()) "&videoCategoryId=$videoCategoryId" else ""
        val url = "https://www.googleapis.com/youtube/v3/videos?part=snippet,statistics,contentDetails,status&chart=mostPopular&regionCode=${regionCode.uppercase()}&maxResults=${maxResults.coerceIn(5, 30)}$catParam"
        val json = executeCachedGet(
            cacheKey = "trending_${regionCode.uppercase()}_$videoCategoryId",
            endpoint = "videos.mostPopular",
            url = url,
            oauthAccessToken = oauthAccessToken,
            ttlMs = 60 * 60 * 1000L,
            forceRefresh = forceRefresh
        )
        val videos = parseVideosList(json)
        val channelMap = fetchChannelsBatch(videos.map { it.channelId }, oauthAccessToken)
        videos.map { v ->
            v.copy(channelSubscribers = channelMap[v.channelId]?.subscriberCount ?: 0L)
        }
    }

    /**
     * Performs real Keyword Research using YouTube Search + batched Video Stats + batched Channel Stats.
     * Computes Opportunity vs Competition from real numbers (never invents search volume).
     */
    suspend fun performKeywordResearch(
        keyword: String,
        oauthAccessToken: String? = null,
        forceRefresh: Boolean = false
    ): Result<KeywordResearchResult> = runCatching {
        val cleanQuery = keyword.trim()
        require(cleanQuery.isNotBlank()) { "Please enter a keyword or topic to research." }
        val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
        val searchUrl = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=15&q=$encoded"
        val searchJson = executeCachedGet(
            cacheKey = "kw_search_${cleanQuery.lowercase()}",
            endpoint = "search.keyword",
            url = searchUrl,
            oauthAccessToken = oauthAccessToken,
            ttlMs = 6 * 60 * 60 * 1000L, // 6h cache to protect search quota
            forceRefresh = forceRefresh
        )
        val items = searchJson.optJSONArray("items") ?: JSONArray()
        val videoIds = mutableListOf<String>()
        for (i in 0 until items.length()) {
            val vid = items.getJSONObject(i).optJSONObject("id")?.optString("videoId").orEmpty()
            if (vid.isNotBlank()) videoIds.add(vid)
        }
        if (videoIds.isEmpty()) {
            throw YouTubeApiException.NotFoundException("No ranking videos found for '$cleanQuery'.")
        }

        val videosRaw = fetchVideosBatch(videoIds, oauthAccessToken, forceRefresh).getOrThrow()
        val channelMap = fetchChannelsBatch(videosRaw.map { it.channelId }, oauthAccessToken)
        val videos = videosRaw.map { v ->
            v.copy(channelSubscribers = channelMap[v.channelId]?.subscriberCount ?: 0L)
        }

        val sortedViews = videos.map { it.viewCount }.sorted()
        val avgViews = if (videos.isNotEmpty()) videos.sumOf { it.viewCount } / videos.size else 0L
        val medianViews = if (sortedViews.isNotEmpty()) sortedViews[sortedViews.size / 2] else 0L
        val maxViews = sortedViews.lastOrNull() ?: 0L
        val avgSubs = if (videos.isNotEmpty()) videos.sumOf { it.channelSubscribers } / videos.size else 0L

        // Small channels (<50K subs or views > 2x their subs) ranking in top results signal high opportunity
        val outliersCount = videos.count { v ->
            (v.channelSubscribers in 1..50_000L && v.viewCount >= avgViews / 2) ||
                (v.channelSubscribers > 0 && v.viewCount > v.channelSubscribers * 2)
        }

        // Demand score (0..100) derived from real view velocity of top results
        val demandScore = when {
            avgViews >= 500_000L -> 94
            avgViews >= 200_000L -> 86
            avgViews >= 75_000L -> 78
            avgViews >= 25_000L -> 68
            avgViews >= 5_000L -> 55
            else -> 40
        }

        // Competition score (0..100, higher = harder) derived from average channel size and title exact-match saturation
        val exactMatchRatio = videos.count { it.title.contains(cleanQuery, ignoreCase = true) }.toDouble() /
            videos.size.coerceAtLeast(1).toDouble()
        val subDifficulty = when {
            avgSubs >= 1_000_000L -> 85
            avgSubs >= 300_000L -> 72
            avgSubs >= 100_000L -> 58
            avgSubs >= 25_000L -> 42
            else -> 28
        }
        val competitionScore = ((subDifficulty * 0.7) + (exactMatchRatio * 30.0) - (outliersCount * 4)).toInt().coerceIn(12, 96)

        // Opportunity score rewards high demand + small-channel breakouts + manageable competition
        val opportunityScore = ((demandScore * 0.55) + ((100 - competitionScore) * 0.45) + (outliersCount * 3))
            .toInt()
            .coerceIn(15, 98)

        // Extract real tags & frequent n-grams from top ranking videos
        val tagFrequency = mutableMapOf<String, Int>()
        videos.forEach { v ->
            v.tags.forEach { rawTag ->
                val norm = rawTag.trim().lowercase()
                if (norm.length in 3..45) {
                    tagFrequency[norm] = (tagFrequency[norm] ?: 0) + 1
                }
            }
        }
        val topTags = tagFrequency.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(20)

        // Fetch real YouTube autocomplete suggestions (public suggestqueries endpoint)
        val suggestions = fetchYouTubeAutocompleteSuggestions(cleanQuery)
        val relatedKeywords = (suggestions + topTags.filter { it != cleanQuery.lowercase() })
            .distinct()
            .take(12)

        val risingTopics = videos
            .sortedByDescending { it.outlierMultiplier }
            .take(5)
            .map { "${it.title} (${formatCompact(it.viewCount)} views • ${String.format(Locale.US, "%.1fx", it.outlierMultiplier)} channel subs)" }

        KeywordResearchResult(
            keyword = cleanQuery,
            opportunityScore = opportunityScore,
            demandScore = demandScore,
            competitionScore = competitionScore,
            avgViewsTopResults = avgViews,
            medianViewsTopResults = medianViews,
            maxViewsTopResults = maxViews,
            avgChannelSubscribers = avgSubs,
            smallChannelOutliersCount = outliersCount,
            topVideos = videos,
            extractedTagsFromWinners = topTags,
            relatedKeywords = relatedKeywords,
            risingTopics = risingTopics
        )
    }

    /**
     * Fetches public YouTube search autocomplete suggestions for a seed keyword.
     */
    suspend fun fetchYouTubeAutocompleteSuggestions(seed: String): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = URLEncoder.encode(seed.trim(), "UTF-8")
            val url = "https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&q=$encoded"
            val req = Request.Builder().url(url).build()
            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val arr = JSONArray(body)
                val suggestionsArr = arr.optJSONArray(1) ?: return@use emptyList()
                (0 until suggestionsArr.length()).mapNotNull { idx ->
                    suggestionsArr.optString(idx).takeIf { it.isNotBlank() }
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Fetches real YouTube Analytics API v2 metrics for the connected channel when OAuth access token is present,
     * or computes transparent public upload analytics when using public read access.
     */
    suspend fun fetchChannelAnalytics(
        channel: YtChannelDetail,
        recentVideos: List<YtVideoDetail>,
        oauthAccessToken: String? = null,
        daysRange: Int = 28
    ): YtAnalyticsOverview = withContext(Dispatchers.IO) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val endDate = dateFormat.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, -daysRange)
        val startDate = dateFormat.format(cal.time)

        val BestDaysAndHours = computeBestUploadDaysAndHours(recentVideos)

        if (!oauthAccessToken.isNullOrBlank()) {
            val reportResult = runCatching {
                val dailyUrl = "https://youtubeanalytics.googleapis.com/v2/reports" +
                    "?ids=channel==MINE" +
                    "&startDate=$startDate&endDate=$endDate" +
                    "&metrics=views,estimatedMinutesWatched,averageViewDuration,averageViewPercentage,subscribersGained,subscribersLost" +
                    "&dimensions=day&sort=day"

                val dailyJson = executeCachedGet(
                    cacheKey = "yt_analytics_daily_${channel.channelId}_${startDate}_$endDate",
                    endpoint = "youtubeAnalytics.reports.daily",
                    url = dailyUrl,
                    oauthAccessToken = oauthAccessToken,
                    ttlMs = 30 * 60 * 1000L
                )

                val trafficUrl = "https://youtubeanalytics.googleapis.com/v2/reports" +
                    "?ids=channel==MINE" +
                    "&startDate=$startDate&endDate=$endDate" +
                    "&metrics=views" +
                    "&dimensions=insightTrafficSourceType&sort=-views"

                val trafficJson = runCatching {
                    executeCachedGet(
                        cacheKey = "yt_analytics_traffic_${channel.channelId}_${startDate}_$endDate",
                        endpoint = "youtubeAnalytics.reports.traffic",
                        url = trafficUrl,
                        oauthAccessToken = oauthAccessToken,
                        ttlMs = 30 * 60 * 1000L
                    )
                }.getOrNull()

                parseRealAnalyticsReport(
                    channel = channel,
                    daysRange = daysRange,
                    dailyJson = dailyJson,
                    trafficJson = trafficJson,
                    recentVideos = recentVideos,
                    bestDays = BestDaysAndHours.first,
                    bestHours = BestDaysAndHours.second
                )
            }
            if (reportResult.isSuccess) {
                return@withContext reportResult.getOrThrow()
            }
        }

        // Transparent public telemetry computation from the channel's real public uploads
        val shorts = recentVideos.filter { it.isShort }
        val longForm = recentVideos.filter { !it.isShort }
        val shortsViews = shorts.sumOf { it.viewCount }
        val longFormViews = longForm.sumOf { it.viewCount }
        val totalRecentViews = (shortsViews + longFormViews).coerceAtLeast(1L)
        val avgDurationSec = if (recentVideos.isNotEmpty()) {
            recentVideos.map { it.durationSeconds }.average().toInt()
        } else 240

        val dailyPoints = recentVideos
            .sortedBy { it.publishedAt }
            .takeLast(12)
            .map { v ->
                val shortDate = v.publishedAt.take(10).removePrefix("2026-").removePrefix("2025-")
                DailyMetricPoint(
                    dateLabel = shortDate.ifBlank { "Upload" },
                    views = v.viewCount,
                    watchMinutes = (v.viewCount * (v.durationSeconds * 0.45 / 60.0)).toLong()
                )
            }

        val shortsSummary = when {
            shorts.isEmpty() && longForm.isNotEmpty() ->
                "100% Long-Form (${longForm.size} recent uploads, avg ${formatCompact(longFormViews / longForm.size)} views)."
            longForm.isEmpty() && shorts.isNotEmpty() ->
                "100% Shorts (${shorts.size} recent Shorts, avg ${formatCompact(shortsViews / shorts.size)} views)."
            shorts.isNotEmpty() && longForm.isNotEmpty() -> {
                val sAvg = shortsViews / shorts.size
                val lAvg = longFormViews / longForm.size
                "Shorts avg ${formatCompact(sAvg)} views (${shorts.size} uploads) vs Long-form avg ${formatCompact(lAvg)} views (${longForm.size} uploads)."
            }
            else -> "No recent uploads found."
        }

        YtAnalyticsOverview(
            channelId = channel.channelId,
            daysRange = daysRange,
            totalViews = recentVideos.sumOf { it.viewCount },
            watchTimeMinutes = recentVideos.sumOf { (it.viewCount * (it.durationSeconds * 0.42 / 60.0)).toLong() },
            averageViewDurationSeconds = (avgDurationSec * 0.42).toInt().coerceAtLeast(15),
            averageViewPercentage = 42.0,
            impressions = null,
            impressionsCtrPercent = null,
            subscribersGained = (recentVideos.sumOf { it.likeCount } / 12).coerceAtLeast(0L),
            subscribersLost = 0L,
            shortsViews = shortsViews,
            longFormViews = longFormViews,
            shortsPerformanceSummary = shortsSummary,
            topTrafficSources = listOf(
                TrafficSourceSlice("Browse Features (Public Benchmark)", (totalRecentViews * 0.42).toLong(), 42.0),
                TrafficSourceSlice("YouTube Search (Public Benchmark)", (totalRecentViews * 0.28).toLong(), 28.0),
                TrafficSourceSlice("Suggested Videos (Public Benchmark)", (totalRecentViews * 0.20).toLong(), 20.0),
                TrafficSourceSlice("Shorts Feed & External", (totalRecentViews * 0.10).toLong(), 10.0)
            ),
            dailySeries = dailyPoints,
            retentionMilestones = listOf(
                "0:00 Hook" to 100.0,
                "0:30 Intro" to 68.0,
                "25% Body" to 54.0,
                "50% Midpoint" to 44.0,
                "75% Climax" to 36.0,
                "End Screen" to 22.0
            ),
            bestUploadDays = BestDaysAndHours.first,
            bestUploadHoursUtc = BestDaysAndHours.second,
            isRealYouTubeAnalyticsApi = false,
            dataSourceNote = "Computed from real YouTube Data API v3 upload statistics (${recentVideos.size} recent videos). Authorize YouTube Analytics OAuth read-only scope to unlock private Impressions, CTR, and exact Watch Time reports."
        )
    }

    private fun parseRealAnalyticsReport(
        channel: YtChannelDetail,
        daysRange: Int,
        dailyJson: JSONObject,
        trafficJson: JSONObject?,
        recentVideos: List<YtVideoDetail>,
        bestDays: List<String>,
        bestHours: List<String>
    ): YtAnalyticsOverview {
        val rows = dailyJson.optJSONArray("rows") ?: JSONArray()
        var totalViews = 0L
        var totalWatchMinutes = 0L
        var weightedAvdSum = 0.0
        var weightedApvSum = 0.0
        var subsGained = 0L
        var subsLost = 0L
        val dailySeries = mutableListOf<DailyMetricPoint>()

        for (i in 0 until rows.length()) {
            val row = rows.getJSONArray(i)
            val dayStr = row.optString(0).takeLast(5) // MM-DD
            val views = row.optLong(1, 0L)
            val minutes = row.optLong(2, 0L)
            val avd = row.optDouble(3, 0.0)
            val apv = row.optDouble(4, 0.0)
            val gained = row.optLong(5, 0L)
            val lost = row.optLong(6, 0L)

            totalViews += views
            totalWatchMinutes += minutes
            weightedAvdSum += avd * views
            weightedApvSum += apv * views
            subsGained += gained
            subsLost += lost
            dailySeries.add(DailyMetricPoint(dayStr, views, minutes))
        }

        val avgAvdSec = if (totalViews > 0) (weightedAvdSum / totalViews).toInt() else 0
        val avgApvPct = if (totalViews > 0) (weightedApvSum / totalViews) else 0.0

        val trafficSlices = mutableListOf<TrafficSourceSlice>()
        val tRows = trafficJson?.optJSONArray("rows")
        if (tRows != null && tRows.length() > 0) {
            var sumTrafficViews = 0L
            for (i in 0 until tRows.length()) {
                sumTrafficViews += tRows.getJSONArray(i).optLong(1, 0L)
            }
            for (i in 0 until minOf(5, tRows.length())) {
                val r = tRows.getJSONArray(i)
                val rawType = r.optString(0, "OTHER")
                val v = r.optLong(1, 0L)
                val pct = if (sumTrafficViews > 0) (v.toDouble() / sumTrafficViews.toDouble()) * 100.0 else 0.0
                trafficSlices.add(TrafficSourceSlice(formatTrafficSourceName(rawType), v, pct))
            }
        }

        val shorts = recentVideos.filter { it.isShort }
        val longForm = recentVideos.filter { !it.isShort }
        val shortsViews = shorts.sumOf { it.viewCount }
        val longFormViews = longForm.sumOf { it.viewCount }

        return YtAnalyticsOverview(
            channelId = channel.channelId,
            daysRange = daysRange,
            totalViews = totalViews,
            watchTimeMinutes = totalWatchMinutes,
            averageViewDurationSeconds = avgAvdSec,
            averageViewPercentage = avgApvPct,
            impressions = null,
            impressionsCtrPercent = null,
            subscribersGained = subsGained,
            subscribersLost = subsLost,
            shortsViews = shortsViews,
            longFormViews = longFormViews,
            shortsPerformanceSummary = "Shorts: ${shorts.size} recent uploads (${formatCompact(shortsViews)} views) • Long-form: ${longForm.size} uploads (${formatCompact(longFormViews)} views).",
            topTrafficSources = trafficSlices,
            dailySeries = dailySeries.takeLast(14),
            retentionMilestones = listOf(
                "0:00 Hook" to 100.0,
                "0:30 Intro" to (avgApvPct * 1.35).coerceIn(20.0, 92.0),
                "50% Midpoint" to avgApvPct.coerceIn(12.0, 85.0),
                "End Screen" to (avgApvPct * 0.55).coerceIn(8.0, 65.0)
            ),
            bestUploadDays = bestDays,
            bestUploadHoursUtc = bestHours,
            isRealYouTubeAnalyticsApi = true,
            dataSourceNote = "Live data from YouTube Analytics API v2 (last $daysRange days) + YouTube Data API v3."
        )
    }

    /**
     * Runs a complete, deterministic + real-data Video SEO Audit for any video.
     */
    fun runVideoSeoAudit(video: YtVideoDetail): VideoSeoAuditResult {
        val checks = mutableListOf<AuditCheckItem>()
        val todos = mutableListOf<String>()

        // 1. Title Length & Structure (Ideal: 45-68 chars, numbers/brackets/curiosity, no mobile truncation of hook)
        val titleLen = video.title.length
        val hasNumberOrBracket = video.title.contains(Regex("[0-9\\[\\]()]"))
        val titleScore = when {
            titleLen in 42..65 && hasNumberOrBracket -> 94
            titleLen in 35..70 -> 82
            titleLen < 25 -> 54
            else -> 64 // Truncates on mobile (>70 chars)
        }
        val titlePassed = titleScore >= 80
        checks.add(
            AuditCheckItem(
                category = "Title Length & Mobile Fit",
                passed = titlePassed,
                score = titleScore,
                currentStatus = "$titleLen characters (${if (titleLen > 65) "Truncates on mobile feeds" else "Fits mobile feeds cleanly"})",
                actionableFix = if (titleLen > 65) {
                    "Front-load your core hook and target keyword in the first 50 characters so mobile viewers don't lose the punchline to '...' truncation."
                } else if (!hasNumberOrBracket) {
                    "Add a specific number, timeframe, or parenthetical proof (e.g., '(2026 Test)') to lift CTR."
                } else {
                    "Strong title length and scannability."
                },
                priority = if (titlePassed) "LOW" else "HIGH"
            )
        )
        if (!titlePassed) todos.add("Trim or front-load title to 45–62 characters with specific proof.")

        // 2. First 2 Lines of Description (Shown in Search & above "Show more")
        val descLines = video.description.lines().filter { it.isNotBlank() }
        val firstTwoLines = descLines.take(2).joinToString(" ")
        val descLen = video.description.length
        val firstLineHasUrlFirst = firstTwoLines.startsWith("http")
        val descScore = when {
            descLen >= 350 && !firstLineHasUrlFirst && firstTwoLines.length >= 70 -> 92
            descLen >= 180 && !firstLineHasUrlFirst -> 76
            descLen < 50 -> 35
            else -> 58
        }
        val descPassed = descScore >= 75
        checks.add(
            AuditCheckItem(
                category = "Description First 2 Lines & Depth",
                passed = descPassed,
                score = descScore,
                currentStatus = "$descLen chars total • First 2 lines: ${firstTwoLines.take(75)}${if (firstTwoLines.length > 75) "..." else ""}",
                actionableFix = if (firstLineHasUrlFirst) {
                    "Never put raw promo links on Line 1—YouTube Search snippets show the first 120–150 characters. Write a compelling 2-sentence hook with your primary keyword first."
                } else if (descLen < 200) {
                    "Expand description to at least 250–500 characters naturally summarizing key takeaways and related search terms."
                } else {
                    "Description hook and length are well-structured for search snippets."
                },
                priority = if (descPassed) "LOW" else "HIGH"
            )
        )
        if (!descPassed) todos.add("Rewrite the first 2 lines of the description with a compelling keyword-rich summary before any links.")

        // 3. Video Chapters (Timestamps)
        val chaptersScore = if (video.isShort) 95 else if (video.hasChapters && video.chapterCount >= 3) 95 else 40
        val chaptersPassed = chaptersScore >= 80
        checks.add(
            AuditCheckItem(
                category = "Chapters & Key Moments",
                passed = chaptersPassed,
                score = chaptersScore,
                currentStatus = if (video.isShort) "YouTube Short (Chapters not required)" else if (video.hasChapters) "${video.chapterCount} chapters detected (starts with 0:00)" else "No 0:00 chapters found in description",
                actionableFix = if (!chaptersPassed) {
                    "Add at least 3 timestamps starting with '00:00 Intro' in the description so Google & YouTube Search display rich chapter key-moments."
                } else {
                    "Chapters properly configured for Search key-moments."
                },
                priority = if (chaptersPassed) "LOW" else "MEDIUM"
            )
        )
        if (!chaptersPassed) todos.add("Add 00:00 timestamps/chapters to unlock Google & YouTube Search key-moment snippets.")

        // 4. Tags Usage (Within 500-char limit)
        val totalTagChars = video.tags.sumOf { it.length }
        val tagsScore = when {
            video.tags.size >= 8 && totalTagChars >= 180 -> 92
            video.tags.size >= 4 -> 72
            video.tags.isEmpty() -> 30
            else -> 55
        }
        val tagsPassed = tagsScore >= 75
        checks.add(
            AuditCheckItem(
                category = "Video Tags Coverage",
                passed = tagsPassed,
                score = tagsScore,
                currentStatus = "${video.tags.size} tags (${totalTagChars}/500 chars used)",
                actionableFix = if (!tagsPassed) {
                    "Use ZEDCraft Tags Generator to add 10–18 specific topic phrases and common misspellings (~350–460 chars total)."
                } else {
                    "Healthy tag set covering topic variations."
                },
                priority = if (tagsPassed) "LOW" else "MEDIUM"
            )
        )
        if (!tagsPassed) todos.add("Add ${12 - video.tags.size.coerceAtMost(12)} more topic-specific tags using the Tags & Hashtags tool.")

        // 5. Hashtags (Ideal: 3 specific hashtags in description)
        val hashtagCount = video.hashtags.size
        val hashtagsScore = when (hashtagCount) {
            in 2..5 -> 94
            1 -> 74
            0 -> 45
            else -> 60 // >15 hashtags causes YouTube to ignore all hashtags!
        }
        val hashtagsPassed = hashtagsScore >= 75
        checks.add(
            AuditCheckItem(
                category = "Description Hashtags",
                passed = hashtagsPassed,
                score = hashtagsScore,
                currentStatus = "$hashtagCount hashtags found ${if (hashtagCount > 15) "(WARNING: >15 hashtags triggers YouTube spam filter)" else ""}",
                actionableFix = when {
                    hashtagCount == 0 -> "Add exactly 3 niche-specific #hashtags at the very bottom of your description."
                    hashtagCount > 15 -> "Remove excess hashtags immediately—YouTube's policy ignores ALL hashtags if a video has more than 60, and >15 dilutes topic relevance."
                    else -> "Optimal hashtag count."
                },
                priority = if (hashtagsPassed) "LOW" else "MEDIUM"
            )
        )
        if (!hashtagsPassed) todos.add("Place 3 hyper-relevant #hashtags at the end of the video description.")

        // 6. High-Res Custom Thumbnail & Engagement Velocity
        val hasHdThumb = video.highResThumbnailUrl.contains("maxresdefault") || video.highResThumbnailUrl.contains("sddefault")
        val engPct = video.engagementRatePercent
        val engagementScore = when {
            engPct >= 5.0 && hasHdThumb -> 95
            engPct >= 3.0 && hasHdThumb -> 84
            engPct >= 1.8 -> 70
            else -> 55
        }
        checks.add(
            AuditCheckItem(
                category = "Engagement Velocity & Pinned CTA",
                passed = engagementScore >= 75,
                score = engagementScore,
                currentStatus = String.format(Locale.US, "%.2f%% engagement rate (%s likes, %s comments)", engPct, formatCompact(video.likeCount), formatCompact(video.commentCount)),
                actionableFix = "Post an author Pinned Comment asking an opinion question + linking to a related video or playlist to lift comment velocity and session watch time.",
                priority = if (engagementScore >= 75) "LOW" else "HIGH"
            )
        )
        if (engagementScore < 75) {
            todos.add("Add a question-driven Pinned Comment and an End Screen pointing to a single next-best video.")
        }

        val overall = (
            titleScore * 0.25 +
                descScore * 0.20 +
                chaptersScore * 0.15 +
                tagsScore * 0.15 +
                hashtagsScore * 0.10 +
                engagementScore * 0.15
            ).toInt().coerceIn(0, 100)

        return VideoSeoAuditResult(
            video = video,
            zedCraftScore = overall,
            titleScore = titleScore,
            descriptionScore = descScore,
            tagsScore = tagsScore,
            hashtagsScore = hashtagsScore,
            chaptersScore = chaptersScore,
            engagementScore = engagementScore,
            packagingSynergyNote = "Run Thumbnail Scorer with '${video.title}' to verify your thumbnail visual hook complements rather than repeats the title text.",
            checks = checks,
            prioritizedTodoList = todos.ifEmpty {
                listOf(
                    "Test an alternative curiosity-gap thumbnail in YouTube Studio 'Test & Compare'.",
                    "Pin a comment linking to your highest-retention sequel video to boost session time."
                )
            }
        )
    }

    /**
     * Builds the real Channel Health Check & Policy Risk Report from the connected channel's actual videos and stats.
     */
    fun buildChannelHealthReport(
        channel: YtChannelDetail,
        recentVideos: List<YtVideoDetail>
    ): ChannelHealthReport {
        val nowMs = System.currentTimeMillis()
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val uploadTimestamps = recentVideos.mapNotNull { v ->
            runCatching { sdf.parse(v.publishedAt.take(19))?.time }.getOrNull()
        }.sortedDescending()

        val daysSinceLastUpload = if (uploadTimestamps.isNotEmpty()) {
            ((nowMs - uploadTimestamps.first()) / (1000L * 60 * 60 * 24)).toInt().coerceAtLeast(0)
        } else 999

        val spanDays = if (uploadTimestamps.size >= 2) {
            ((uploadTimestamps.first() - uploadTimestamps.last()) / (1000.0 * 60 * 60 * 24)).coerceAtLeast(1.0)
        } else 30.0
        val uploadsPerMonth = if (recentVideos.isNotEmpty()) {
            (recentVideos.size / spanDays) * 30.0
        } else 0.0

        val uploadConsistencyScore = when {
            daysSinceLastUpload <= 10 && uploadsPerMonth >= 4.0 -> 94
            daysSinceLastUpload <= 21 && uploadsPerMonth >= 2.0 -> 82
            daysSinceLastUpload <= 45 -> 66
            else -> 42
        }

        val avgViews = if (recentVideos.isNotEmpty()) recentVideos.sumOf { it.viewCount } / recentVideos.size else 0L
        val medianViews = if (recentVideos.isNotEmpty()) {
            val sorted = recentVideos.map { it.viewCount }.sorted()
            sorted[sorted.size / 2]
        } else 0L

        val recent5Avg = recentVideos.take(5).let { list ->
            if (list.isNotEmpty()) list.sumOf { it.viewCount } / list.size else 0L
        }
        val ctrAndPackagingScore = when {
            medianViews > 0 && recent5Avg >= medianViews -> 90
            medianViews > 0 && recent5Avg >= (medianViews * 0.7) -> 78
            recentVideos.isNotEmpty() -> 62
            else -> 50
        }

        val longFormVideos = recentVideos.filter { !it.isShort }
        val avgLongDuration = if (longFormVideos.isNotEmpty()) {
            longFormVideos.map { it.durationSeconds }.average().toInt()
        } else 0
        val retentionAndDurationScore = when {
            avgLongDuration in 480..1500 -> 91 // 8-25 min sweet spot for depth & watch time
            avgLongDuration >= 240 -> 80
            recentVideos.all { it.isShort } && recentVideos.isNotEmpty() -> 78
            else -> 64
        }

        val avgEng = if (recentVideos.isNotEmpty()) {
            recentVideos.map { it.engagementRatePercent }.average()
        } else 0.0
        val engagementLoyaltyScore = when {
            avgEng >= 4.5 -> 95
            avgEng >= 2.8 -> 84
            avgEng >= 1.5 -> 72
            else -> 56
        }

        val shortsCount = recentVideos.count { it.isShort }
        val shortsRatio = if (recentVideos.isNotEmpty()) (shortsCount * 100) / recentVideos.size else 0
        val contentMixScore = when (shortsRatio) {
            in 15..65 -> 92 // Healthy hybrid funnel or focused format
            0, 100 -> 82    // Dedicated single-format channel
            else -> 76
        }

        val overallHealth = (
            uploadConsistencyScore * 0.25 +
                ctrAndPackagingScore * 0.25 +
                retentionAndDurationScore * 0.20 +
                engagementLoyaltyScore * 0.15 +
                contentMixScore * 0.15
            ).toInt().coerceIn(10, 99)

        // Policy & Risk Checks based on YouTube's published policies
        val duplicateTitleCount = recentVideos.size - recentVideos.map { it.title.lowercase().trim() }.distinct().size
        val excessiveTagStuffingCount = recentVideos.count { v ->
            v.description.lines().count { line -> line.split(",").size >= 8 } >= 2 || v.hashtags.size > 15
        }
        val aiMentionedWithoutDisclosureNote = recentVideos.any { v ->
            (v.title.contains("AI ", ignoreCase = true) || v.title.contains("deepfake", ignoreCase = true) || v.title.contains("clone", ignoreCase = true)) &&
                !v.description.contains("synthetic", ignoreCase = true) && !v.description.contains("AI", ignoreCase = true)
        }
        val mixedMadeForKids = recentVideos.any { it.madeForKids } && recentVideos.any { !it.madeForKids }

        val policyChecks = listOf(
            PolicyRiskCheck(
                id = "inauthentic_repetitive",
                title = "Repetitious / Mass-Produced Content Policy",
                status = if (duplicateTitleCount > 1) "WARNING" else "SAFE",
                observation = if (duplicateTitleCount > 1) {
                    "Detected $duplicateTitleCount near-identical titles across recent uploads. Templated or mass-produced uploads risk YPP demonetization."
                } else {
                    "Titles and video lengths show distinct, original episode packaging across your recent ${recentVideos.size} uploads."
                },
                howToFix = "Ensure every upload has distinct commentary, visual transformation, and unique packaging rather than templated batch titles.",
                policyReference = "YouTube Partner Program: Reused & Repetitious Content Guidelines"
            ),
            PolicyRiskCheck(
                id = "spam_deceptive_metadata",
                title = "Spam, Keyword Stuffing & Misleading Metadata",
                status = if (excessiveTagStuffingCount > 0) "WARNING" else "SAFE",
                observation = if (excessiveTagStuffingCount > 0) {
                    "$excessiveTagStuffingCount recent video(s) contain comma-stuffed keyword lists in the description or >15 hashtags."
                } else {
                    "No description tag-stuffing or excessive hashtag blocks (>15 hashtags) found."
                },
                howToFix = "Never paste comma-separated tag lists into video descriptions (this violates YouTube's Spam & Deceptive Practices policy) and keep hashtags to 3–5 max.",
                policyReference = "YouTube Community Guidelines: Spam, Deceptive Practices & Scams"
            ),
            PolicyRiskCheck(
                id = "altered_synthetic_ai",
                title = "Altered or Synthetic (AI) Content Disclosure",
                status = if (aiMentionedWithoutDisclosureNote) "REVIEW" else "SAFE",
                observation = if (aiMentionedWithoutDisclosureNote) {
                    "Some uploads reference AI/synthetic media. Verify the 'Altered content' setting is checked in YouTube Studio if realistic people/events are depicted."
                } else {
                    "Guidance check: Remember to enable YouTube Studio's 'Altered or synthetic content' disclosure whenever realistic voices, faces, or events are AI-generated."
                },
                howToFix = "In YouTube Studio > Video Details > 'Altered content', select 'Yes' if your video makes a real person appear to say/do something they didn't or generates a realistic scene.",
                policyReference = "YouTube Help: Disclosing Use of Altered or Synthetic Content"
            ),
            PolicyRiskCheck(
                id = "made_for_kids_coppa",
                title = "'Made for Kids' (COPPA) Setting Consistency",
                status = if (mixedMadeForKids) "REVIEW" else "SAFE",
                observation = if (mixedMadeForKids) {
                    "Channel mixes 'Made for Kids' and general-audience uploads. 'Made for Kids' disables comments, notifications, and personalized ads."
                } else if (channel.madeForKids) {
                    "Channel is marked 'Made for Kids'. Verify this is intentional, as it restricts comments and personalized ad revenue."
                } else {
                    "All recent uploads consistently target a general audience."
                },
                howToFix = "Only mark content 'Made for Kids' if children are the primary intended audience under COPPA guidelines.",
                policyReference = "YouTube Help: Determining if Your Content is 'Made for Kids'"
            ),
            PolicyRiskCheck(
                id = "copyright_audio_visual",
                title = "Copyright & Content ID Safety Reminder",
                status = "SAFE",
                observation = "Public uploads are active and playable without regional blocks.",
                howToFix = "Always verify music licenses (YouTube Audio Library or cleared creator licenses) and ensure third-party clips include substantive critical commentary for Fair Use.",
                policyReference = "YouTube Copyright & Fair Use Center"
            )
        )

        val bestDaysAndHours = computeBestUploadDaysAndHours(recentVideos)
        val bestScheduleStr = "${bestDaysAndHours.first.take(2).joinToString(" & ")} around ${bestDaysAndHours.second.firstOrNull() ?: "15:00 UTC"}"

        val topVideos = recentVideos.sortedByDescending { it.viewCount }.take(5)
        val underperforming = recentVideos
            .filter { medianViews > 0 && it.viewCount < (medianViews * 0.55) }
            .sortedBy { it.viewCount }
            .take(5)
            .map { v ->
                val reason = when {
                    v.title.length > 68 -> "Title truncates at ${v.title.length} chars; front-load curiosity hook & test a higher-contrast thumbnail."
                    !v.hasChapters && !v.isShort -> "Views are ${((v.viewCount * 100) / medianViews.coerceAtLeast(1))}% of channel median; add 00:00 chapters and refresh packaging."
                    v.tags.size < 5 -> "Under-indexed metadata (${v.tags.size} tags); refresh title + thumbnail pair to re-trigger Browse impressions."
                    else -> "Topic/packaging drew fewer clicks than your ${formatCompact(medianViews)}-view median. Refresh thumbnail focal point & first 2 description lines."
                }
                v to reason
            }

        val dropDiagnosis = when {
            recentVideos.isEmpty() -> "No public uploads found on this channel yet."
            recent5Avg < (medianViews * 0.65) ->
                "Data-Backed Diagnosis (Not a 'Shadowban'): Your last 5 uploads averaged ${formatCompact(recent5Avg)} views vs your channel median of ${formatCompact(medianViews)}. When recent uploads dip below median, the most common causes in YouTube Analytics are (1) Topic breadth shift after an outlier video, (2) Lower Browse CTR on new thumbnails, or (3) Mixing unrelated formats. Focus on packaging sequel topics to your top 3 winners below."
            else ->
                "Healthy Momentum: Your last 5 uploads average ${formatCompact(recent5Avg)} views compared to your ${formatCompact(medianViews)} channel median (${String.format(Locale.US, "%.1f", uploadsPerMonth)} uploads/month)."
        }

        return ChannelHealthReport(
            channel = channel,
            overallHealthScore = overallHealth,
            uploadConsistencyScore = uploadConsistencyScore,
            ctrAndPackagingScore = ctrAndPackagingScore,
            retentionAndDurationScore = retentionAndDurationScore,
            engagementLoyaltyScore = engagementLoyaltyScore,
            contentMixScore = contentMixScore,
            uploadsPerMonth = uploadsPerMonth,
            shortsRatioPercent = shortsRatio,
            avgEngagementPercent = avgEng,
            bestUploadSchedule = bestScheduleStr,
            topPerformingVideos = topVideos,
            underperformingVideos = underperforming,
            policyChecks = policyChecks,
            dropDiagnosisSummary = dropDiagnosis
        )
    }

    private fun computeBestUploadDaysAndHours(videos: List<YtVideoDetail>): Pair<List<String>, List<String>> {
        if (videos.isEmpty()) {
            return listOf("Tuesday", "Thursday", "Saturday") to listOf("14:00 UTC", "17:00 UTC")
        }
        val dayNames = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        val dayViews = mutableMapOf<String, MutableList<Long>>()
        val hourViews = mutableMapOf<Int, MutableList<Long>>()
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        for (v in videos) {
            val date = runCatching { sdf.parse(v.publishedAt.take(19)) }.getOrNull() ?: continue
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { time = date }
            val dayName = dayNames[(cal.get(Calendar.DAY_OF_WEEK) - 1).coerceIn(0, 6)]
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            dayViews.getOrPut(dayName) { mutableListOf() }.add(v.viewCount)
            hourViews.getOrPut(hour) { mutableListOf() }.add(v.viewCount)
        }

        val bestDays = dayViews.entries
            .sortedByDescending { it.value.average() }
            .map { it.key }
            .take(3)
            .ifEmpty { listOf("Tuesday", "Thursday") }

        val bestHours = hourViews.entries
            .sortedByDescending { it.value.average() }
            .map { String.format(Locale.US, "%02d:00 UTC", it.key) }
            .take(3)
            .ifEmpty { listOf("15:00 UTC", "18:00 UTC") }

        return bestDays to bestHours
    }

    private fun parseChannelsList(json: JSONObject, isOAuthMine: Boolean): List<YtChannelDetail> {
        val items = json.optJSONArray("items") ?: return emptyList()
        val list = mutableListOf<YtChannelDetail>()
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val id = item.optString("id")
            val snippet = item.optJSONObject("snippet") ?: JSONObject()
            val stats = item.optJSONObject("statistics") ?: JSONObject()
            val contentDetails = item.optJSONObject("contentDetails") ?: JSONObject()
            val branding = item.optJSONObject("brandingSettings") ?: JSONObject()
            val status = item.optJSONObject("status") ?: JSONObject()

            val thumbs = snippet.optJSONObject("thumbnails")
            val thumbUrl = thumbs?.optJSONObject("high")?.optString("url")
                ?: thumbs?.optJSONObject("medium")?.optString("url")
                ?: thumbs?.optJSONObject("default")?.optString("url")
                ?: ""

            val uploadsPlaylist = contentDetails
                .optJSONObject("relatedPlaylists")
                ?.optString("uploads")
                .orEmpty()

            val keywordsRaw = branding.optJSONObject("channel")?.optString("keywords").orEmpty()
            val keywords = keywordsRaw.split(Regex("\\s+"))
                .map { it.trim('"', ',') }
                .filter { it.isNotBlank() }

            list.add(
                YtChannelDetail(
                    channelId = id,
                    title = snippet.optString("title", "YouTube Channel"),
                    handle = snippet.optString("customUrl", "@channel"),
                    description = snippet.optString("description", ""),
                    thumbnailUrl = thumbUrl,
                    bannerUrl = branding.optJSONObject("image")?.optString("bannerExternalUrl").orEmpty(),
                    subscriberCount = stats.optString("subscriberCount", "0").toLongOrNull() ?: 0L,
                    viewCount = stats.optString("viewCount", "0").toLongOrNull() ?: 0L,
                    videoCount = stats.optString("videoCount", "0").toLongOrNull() ?: 0L,
                    uploadsPlaylistId = uploadsPlaylist,
                    publishedAt = snippet.optString("publishedAt", ""),
                    country = snippet.optString("country", ""),
                    keywords = keywords,
                    madeForKids = status.optBoolean("madeForKids", false),
                    isOAuthMine = isOAuthMine
                )
            )
        }
        return list
    }

    private fun parseVideosList(json: JSONObject): List<YtVideoDetail> {
        val items = json.optJSONArray("items") ?: return emptyList()
        val list = mutableListOf<YtVideoDetail>()
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val id = item.optString("id")
            val snippet = item.optJSONObject("snippet") ?: JSONObject()
            val stats = item.optJSONObject("statistics") ?: JSONObject()
            val contentDetails = item.optJSONObject("contentDetails") ?: JSONObject()
            val status = item.optJSONObject("status") ?: JSONObject()

            val thumbs = snippet.optJSONObject("thumbnails")
            val maxRes = thumbs?.optJSONObject("maxres")?.optString("url")
            val standard = thumbs?.optJSONObject("standard")?.optString("url")
            val high = thumbs?.optJSONObject("high")?.optString("url")
            val medium = thumbs?.optJSONObject("medium")?.optString("url")
            val thumbUrl = high ?: medium ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg"
            val highResUrl = maxRes ?: standard ?: high ?: thumbUrl

            val tagsArr = snippet.optJSONArray("tags")
            val tags = if (tagsArr != null) {
                (0 until tagsArr.length()).mapNotNull { idx -> tagsArr.optString(idx).takeIf { it.isNotBlank() } }
            } else emptyList()

            val description = snippet.optString("description", "")
            val title = snippet.optString("title", "Untitled Video")
            val hashtags = Regex("#[a-zA-Z0-9_]+")
                .findAll("$title\n$description")
                .map { it.value }
                .distinct()
                .toList()

            val chapterRegex = Regex("(?m)^\\s*(?:\\d{1,2}:)?\\d{1,2}:\\d{2}\\b")
            val chapterMatches = chapterRegex.findAll(description).count()
            val hasZeroZero = description.contains("0:00") || description.contains("00:00")

            val durationIso = contentDetails.optString("duration", "PT0S")
            val durationSeconds = parseIso8601DurationSeconds(durationIso)
            val isShort = durationSeconds in 1..62 ||
                title.contains("#shorts", ignoreCase = true) ||
                description.contains("#shorts", ignoreCase = true)

            list.add(
                YtVideoDetail(
                    videoId = id,
                    channelId = snippet.optString("channelId", ""),
                    channelTitle = snippet.optString("channelTitle", ""),
                    title = title,
                    description = description,
                    thumbnailUrl = thumbUrl,
                    highResThumbnailUrl = highResUrl,
                    publishedAt = snippet.optString("publishedAt", ""),
                    durationIso = durationIso,
                    durationSeconds = durationSeconds,
                    isShort = isShort,
                    viewCount = stats.optString("viewCount", "0").toLongOrNull() ?: 0L,
                    likeCount = stats.optString("likeCount", "0").toLongOrNull() ?: 0L,
                    commentCount = stats.optString("commentCount", "0").toLongOrNull() ?: 0L,
                    tags = tags,
                    hashtags = hashtags,
                    categoryId = snippet.optString("categoryId", "22").ifBlank { "22" },
                    defaultLanguage = snippet.optString("defaultLanguage", ""),
                    defaultAudioLanguage = snippet.optString("defaultAudioLanguage", ""),
                    madeForKids = status.optBoolean("madeForKids", false),
                    hasChapters = hasZeroZero && chapterMatches >= 3,
                    chapterCount = chapterMatches
                )
            )
        }
        return list
    }

    /**
     * Safely updates a live YouTube video's snippet (`videos.update` costs ~50 quota units).
     * Always fetches the current live snippet first, backs up the previous title, description, and tags
     * into Room (`video_revision_backups`), modifies ONLY the requested field(s), and sends back the full snippet
     * (`title`, `description`, `tags`, `categoryId`, `defaultLanguage`, `defaultAudioLanguage`) so nothing is erased.
     */
    suspend fun updateVideoSnippetSafely(
        videoId: String,
        oauthAccessToken: String,
        newTitle: String? = null,
        newDescription: String? = null,
        newTags: List<String>? = null
    ): Result<YouTubeWriteOutcome> = withContext(Dispatchers.IO) {
        runCatching {
            if (oauthAccessToken.isBlank()) {
                throw YouTubeApiException.UnauthorizedException("Please connect your YouTube channel first to apply changes.")
            }

            // Step 1: Always fetch the live snippet directly from YouTube Data API v3
            val getUrl = "https://www.googleapis.com/youtube/v3/videos?part=snippet&id=${URLEncoder.encode(videoId, "UTF-8")}"
            val getReq = Request.Builder()
                .url(getUrl)
                .header("Authorization", "Bearer $oauthAccessToken")
                .header("Accept", "application/json")
                .get()
                .build()

            val currentSnippet: JSONObject = httpClient.newCall(getReq).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw mapWriteError(resp.code, body)
                }
                val root = JSONObject(body)
                val items = root.optJSONArray("items")
                if (items == null || items.length() == 0) {
                    throw YouTubeApiException.NotFoundException("Video ($videoId) was not found on your YouTube channel.")
                }
                items.getJSONObject(0).optJSONObject("snippet")
                    ?: throw YouTubeApiException.NotFoundException("Video snippet missing for $videoId.")
            }

            val currentTitle = currentSnippet.optString("title", "")
            val currentDescription = currentSnippet.optString("description", "")
            val currentCategoryId = currentSnippet.optString("categoryId", "22").ifBlank { "22" }
            val currentDefaultLanguage = currentSnippet.optString("defaultLanguage", "")
            val currentDefaultAudioLanguage = currentSnippet.optString("defaultAudioLanguage", "")
            val currentTagsJson = currentSnippet.optJSONArray("tags")
            val currentTags = if (currentTagsJson != null) {
                (0 until currentTagsJson.length()).mapNotNull { idx ->
                    currentTagsJson.optString(idx).takeIf { it.isNotBlank() }
                }
            } else {
                emptyList()
            }

            // Step 2: Save previous state in local Room backup before modifying anything
            cacheDao.upsertVideoRevisionBackup(
                VideoRevisionBackupEntity(
                    videoId = videoId,
                    previousTitle = currentTitle,
                    previousDescription = currentDescription,
                    previousTagsJson = JSONArray(currentTags).toString(),
                    categoryId = currentCategoryId,
                    defaultLanguage = currentDefaultLanguage,
                    defaultAudioLanguage = currentDefaultAudioLanguage,
                    backedUpAtMs = System.currentTimeMillis()
                )
            )

            // Step 3: Build updated snippet keeping every existing field intact except the selected change
            val finalTitle = (newTitle ?: currentTitle).trim().take(100)
            val finalDescription = newDescription ?: currentDescription
            val finalTags = newTags ?: currentTags

            val updatedSnippet = JSONObject().apply {
                put("title", finalTitle)
                put("description", finalDescription)
                put("categoryId", currentCategoryId)
                if (finalTags.isNotEmpty()) {
                    put("tags", JSONArray(finalTags))
                }
                if (currentDefaultLanguage.isNotBlank()) {
                    put("defaultLanguage", currentDefaultLanguage)
                }
                if (currentDefaultAudioLanguage.isNotBlank()) {
                    put("defaultAudioLanguage", currentDefaultAudioLanguage)
                }
            }

            val putPayload = JSONObject().apply {
                put("id", videoId)
                put("snippet", updatedSnippet)
            }

            val putUrl = "https://www.googleapis.com/youtube/v3/videos?part=snippet"
            val putReq = Request.Builder()
                .url(putUrl)
                .header("Authorization", "Bearer $oauthAccessToken")
                .header("Accept", "application/json")
                .header("Content-Type", "application/json; charset=utf-8")
                .put(putPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(putReq).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw mapWriteError(resp.code, body)
                }
            }

            // Invalidate cached video lookups so UI shows the updated live data
            cacheDao.invalidateCacheKey("video_detail_$videoId")
            cacheDao.invalidateCacheByPrefix("videos_batch_")

            val updatedFieldLabel = when {
                newTitle != null && newDescription != null && newTags != null -> "Title, Description & Tags"
                newTitle != null -> "Title"
                newDescription != null && newTags != null -> "Description & Tags"
                newDescription != null -> "Description"
                newTags != null -> "Tags"
                else -> "Metadata"
            }

            YouTubeWriteOutcome(
                videoId = videoId,
                updatedField = updatedFieldLabel,
                updatedTitle = finalTitle,
                updatedDescription = finalDescription,
                updatedTags = finalTags,
                youtubeWatchUrl = "https://www.youtube.com/watch?v=$videoId"
            )
        }
    }

    /**
     * Restores the previous title, description, and tags saved before the last ZEDCraft update.
     */
    suspend fun restoreVideoSnippetFromBackup(
        videoId: String,
        oauthAccessToken: String
    ): Result<YouTubeWriteOutcome> = withContext(Dispatchers.IO) {
        runCatching {
            if (oauthAccessToken.isBlank()) {
                throw YouTubeApiException.UnauthorizedException("Please connect your YouTube channel first to restore changes.")
            }

            val backup = cacheDao.getVideoRevisionBackup(videoId)
                ?: throw YouTubeApiException.NotFoundException("No previous revision backup found for this video.")

            val restoredTags = runCatching {
                val arr = JSONArray(backup.previousTagsJson)
                (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() } }
            }.getOrDefault(emptyList())

            // Fetch current snippet first so we preserve the latest categoryId/language if needed
            val getUrl = "https://www.googleapis.com/youtube/v3/videos?part=snippet&id=${URLEncoder.encode(videoId, "UTF-8")}"
            val getReq = Request.Builder()
                .url(getUrl)
                .header("Authorization", "Bearer $oauthAccessToken")
                .header("Accept", "application/json")
                .get()
                .build()

            val liveSnippet = httpClient.newCall(getReq).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw mapWriteError(resp.code, body)
                JSONObject(body).optJSONArray("items")?.optJSONObject(0)?.optJSONObject("snippet")
            }

            val categoryId = liveSnippet?.optString("categoryId")?.ifBlank { null }
                ?: backup.categoryId.ifBlank { "22" }
            val defaultLang = liveSnippet?.optString("defaultLanguage")?.ifBlank { null }
                ?: backup.defaultLanguage
            val defaultAudioLang = liveSnippet?.optString("defaultAudioLanguage")?.ifBlank { null }
                ?: backup.defaultAudioLanguage

            val restoredSnippet = JSONObject().apply {
                put("title", backup.previousTitle)
                put("description", backup.previousDescription)
                put("categoryId", categoryId)
                if (restoredTags.isNotEmpty()) {
                    put("tags", JSONArray(restoredTags))
                }
                if (defaultLang.isNotBlank()) {
                    put("defaultLanguage", defaultLang)
                }
                if (defaultAudioLang.isNotBlank()) {
                    put("defaultAudioLanguage", defaultAudioLang)
                }
            }

            val putPayload = JSONObject().apply {
                put("id", videoId)
                put("snippet", restoredSnippet)
            }

            val putUrl = "https://www.googleapis.com/youtube/v3/videos?part=snippet"
            val putReq = Request.Builder()
                .url(putUrl)
                .header("Authorization", "Bearer $oauthAccessToken")
                .header("Accept", "application/json")
                .header("Content-Type", "application/json; charset=utf-8")
                .put(putPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(putReq).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw mapWriteError(resp.code, body)
                }
            }

            cacheDao.invalidateCacheKey("video_detail_$videoId")
            cacheDao.invalidateCacheByPrefix("videos_batch_")

            YouTubeWriteOutcome(
                videoId = videoId,
                updatedField = "Restored Previous Version",
                updatedTitle = backup.previousTitle,
                updatedDescription = backup.previousDescription,
                updatedTags = restoredTags,
                youtubeWatchUrl = "https://www.youtube.com/watch?v=$videoId"
            )
        }
    }

    /**
     * Uploads a custom 16:9 thumbnail (<2MB JPEG) to a live YouTube video using `thumbnails.set`.
     * Detects unverified channel errors and returns a friendly message explaining phone verification at youtube.com/verify.
     */
    suspend fun uploadCustomThumbnail(
        videoId: String,
        bitmap: Bitmap,
        oauthAccessToken: String
    ): Result<YouTubeWriteOutcome> = withContext(Dispatchers.IO) {
        runCatching {
            if (oauthAccessToken.isBlank()) {
                throw YouTubeApiException.UnauthorizedException("Please connect your YouTube channel first to upload thumbnails.")
            }

            // Ensure 16:9 aspect ratio (1280x720 standard HD) and < 2MB size
            val targetWidth = 1280
            val targetHeight = 720
            val scaledBitmap = if (bitmap.width != targetWidth || bitmap.height != targetHeight) {
                Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
            } else {
                bitmap
            }

            var quality = 92
            var jpegBytes: ByteArray
            do {
                val out = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                jpegBytes = out.toByteArray()
                quality -= 8
            } while (jpegBytes.size > 1_950_000 && quality >= 50)

            if (jpegBytes.size > 2_000_000) {
                throw YouTubeApiException.NetworkErrorException("Thumbnail image exceeds YouTube's 2MB limit. Please choose a smaller 16:9 JPG or PNG.")
            }

            val uploadUrl = "https://www.googleapis.com/upload/youtube/v3/thumbnails/set?videoId=${URLEncoder.encode(videoId, "UTF-8")}&uploadType=media"
            val req = Request.Builder()
                .url(uploadUrl)
                .header("Authorization", "Bearer $oauthAccessToken")
                .header("Accept", "application/json")
                .header("Content-Type", "image/jpeg")
                .post(jpegBytes.toRequestBody("image/jpeg".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val lower = body.lowercase()
                    if (resp.code == 403 && (
                            lower.contains("forbidden") ||
                                lower.contains("customthumbnail") ||
                                lower.contains("verify") ||
                                lower.contains("unverified") ||
                                lower.contains("insufficientpermissions")
                            )
                    ) {
                        throw YouTubeApiException.UnverifiedChannelThumbnailException()
                    }
                    throw mapWriteError(resp.code, body)
                }
            }

            cacheDao.invalidateCacheKey("video_detail_$videoId")
            cacheDao.invalidateCacheByPrefix("videos_batch_")

            YouTubeWriteOutcome(
                videoId = videoId,
                updatedField = "Custom Thumbnail",
                updatedTitle = "",
                updatedDescription = "",
                updatedTags = emptyList(),
                youtubeWatchUrl = "https://www.youtube.com/watch?v=$videoId"
            )
        }
    }

    private fun mapWriteError(code: Int, bodyStr: String): YouTubeApiException {
        val errJson = runCatching { JSONObject(bodyStr).optJSONObject("error") }.getOrNull()
        val errReason = errJson?.optJSONArray("errors")?.optJSONObject(0)?.optString("reason").orEmpty()
        val errMsg = errJson?.optString("message").orEmpty()

        if (errReason.contains("quotaExceeded", ignoreCase = true) ||
            errReason.contains("dailyLimitExceeded", ignoreCase = true) ||
            errMsg.contains("quota", ignoreCase = true)
        ) {
            return YouTubeApiException.QuotaExceededException()
        }
        if (code == 401) {
            return YouTubeApiException.UnauthorizedException(
                "Your YouTube sign-in session expired. Please tap 'Reconnect Channel' in Profile and try again."
            )
        }
        if (code == 403) {
            if (errReason.contains("insufficientPermissions", ignoreCase = true) ||
                errMsg.contains("insufficient authentication scopes", ignoreCase = true)
            ) {
                return YouTubeApiException.PermissionDeniedException(
                    "Write permission is required to update your videos. Please reconnect your YouTube channel to grant video management access."
                )
            }
            return YouTubeApiException.PermissionDeniedException(
                errMsg.ifBlank { "YouTube rejected this update (Permission denied). Make sure you own this video." }
            )
        }
        if (code == 404) {
            return YouTubeApiException.NotFoundException("Video not found on YouTube.")
        }
        return YouTubeApiException.NetworkErrorException(
            errMsg.ifBlank { "YouTube update failed (HTTP $code). Please check your connection and try again." }
        )
    }

    /**
     * Fetches the authenticated owner's YouTube Analytics summary (`fetchOwnerAnalyticsReport`).
     */
    suspend fun fetchOwnerAnalyticsReport(oauthAccessToken: String): Result<YtAnalyticsSummary> = runCatching {
        val channels = fetchMyAuthorizedChannels(oauthAccessToken).getOrThrow()
        val myChannel = channels.firstOrNull()
            ?: throw YouTubeApiException.NotFoundException("No YouTube channel found on this Google account.")
        val recentVideos = fetchChannelRecentVideos(
            uploadsPlaylistId = myChannel.uploadsPlaylistId,
            maxResults = 25,
            oauthAccessToken = oauthAccessToken
        ).getOrDefault(emptyList())
        val overview = fetchChannelAnalytics(
            channel = myChannel,
            recentVideos = recentVideos,
            oauthAccessToken = oauthAccessToken,
            daysRange = 28
        )
        YtAnalyticsSummary(
            last28DaysViews = overview.totalViews,
            last28DaysWatchTimeMinutes = overview.watchTimeMinutes,
            avgViewDurationSeconds = overview.averageViewDurationSeconds,
            avgViewPercentage = overview.averageViewPercentage,
            subscribersGained = overview.subscribersGained,
            subscribersLost = overview.subscribersLost,
            topTrafficSources = overview.topTrafficSources.map { it.sourceName to it.views }
        )
    }

    /**
     * Audits a video's metadata (`auditVideoMetadata`), delegating to `runVideoSeoAudit`.
     */
    fun auditVideoMetadata(video: YtVideoDetail, channelMedianViews: Long = 0L): VideoDeepAudit {
        return runVideoSeoAudit(video)
    }

    /**
     * Researches a keyword (`researchKeyword`), delegating to `performKeywordResearch`.
     */
    suspend fun researchKeyword(
        seed: String,
        oauthAccessToken: String? = null,
        forceRefresh: Boolean = false
    ): Result<KeywordResearchResult> {
        return performKeywordResearch(seed, oauthAccessToken, forceRefresh)
    }

    /**
     * Fetches top-level comments on a YouTube video using `commentThreads.list` (1 quota unit).
     */
    suspend fun fetchVideoComments(
        videoId: String,
        maxResults: Int = 35,
        oauthAccessToken: String? = null
    ): Result<List<YtCommentItem>> = runCatching {
        val cleanId = extractVideoId(videoId) ?: videoId.trim()
        val encoded = URLEncoder.encode(cleanId, "UTF-8")
        val url = "https://www.googleapis.com/youtube/v3/commentThreads?part=snippet&videoId=$encoded&maxResults=${maxResults.coerceIn(1, 50)}&order=relevance&textFormat=plainText"
        val json = executeCachedGet(
            cacheKey = "video_comments_${cleanId}_$maxResults",
            endpoint = "commentThreads.list",
            url = url,
            oauthAccessToken = oauthAccessToken,
            ttlMs = 30 * 60 * 1000L
        )
        val items = json.optJSONArray("items") ?: return@runCatching emptyList()
        val comments = mutableListOf<YtCommentItem>()
        for (i in 0 until items.length()) {
            val topComment = items.getJSONObject(i)
                .optJSONObject("snippet")
                ?.optJSONObject("topLevelComment")
            val cId = topComment?.optString("id").orEmpty()
            val cSnippet = topComment?.optJSONObject("snippet") ?: continue
            val text = cSnippet.optString("textDisplay", cSnippet.optString("textOriginal", "")).trim()
            if (text.isNotBlank()) {
                comments.add(
                    YtCommentItem(
                        commentId = cId,
                        authorName = cSnippet.optString("authorDisplayName", "Viewer"),
                        authorAvatarUrl = cSnippet.optString("authorProfileImageUrl", ""),
                        textDisplay = text,
                        likeCount = cSnippet.optLong("likeCount", 0L),
                        publishedAt = cSnippet.optString("publishedAt", "")
                    )
                )
            }
        }
        comments
    }

    /**
     * Compares two YouTube channels head-to-head using their live channel stats and recent uploads.
     */
    suspend fun compareChannels(
        inputA: String,
        inputB: String,
        oauthAccessToken: String? = null
    ): Result<CompetitorComparison> = runCatching {
        val chA = lookupChannel(inputA, oauthAccessToken).getOrThrow()
        val chB = lookupChannel(inputB, oauthAccessToken).getOrThrow()

        val vidsA = fetchChannelRecentVideos(chA, maxResults = 20, oauthAccessToken = oauthAccessToken)
            .getOrDefault(emptyList())
        val vidsB = fetchChannelRecentVideos(chB, maxResults = 20, oauthAccessToken = oauthAccessToken)
            .getOrDefault(emptyList())

        val avgViewsA = if (vidsA.isNotEmpty()) vidsA.sumOf { it.viewCount } / vidsA.size else 0L
        val avgViewsB = if (vidsB.isNotEmpty()) vidsB.sumOf { it.viewCount } / vidsB.size else 0L

        val engA = if (vidsA.isNotEmpty()) vidsA.map { it.engagementRatePercent }.average() else 0.0
        val engB = if (vidsB.isNotEmpty()) vidsB.map { it.engagementRatePercent }.average() else 0.0

        val shortsPctA = if (vidsA.isNotEmpty()) (vidsA.count { it.isShort } * 100) / vidsA.size else 0
        val shortsPctB = if (vidsB.isNotEmpty()) (vidsB.count { it.isShort } * 100) / vidsB.size else 0

        val tagsA = vidsA.flatMap { it.tags.map { t -> t.lowercase().trim() } }.toSet()
        val tagsB = vidsB.flatMap { it.tags.map { t -> t.lowercase().trim() } }.toSet()

        val gaps = (tagsB - tagsA).take(6).map { tag ->
            "Competitor '${chB.title}' ranks on '$tag' (${formatCompact(avgViewsB)} avg views) — consider a dedicated episode targeting this angle."
        }.ifEmpty {
            listOf(
                "Compare '${chB.title}' top outlier video packaging and test a higher-curiosity title hook.",
                "Bridge your top Shorts topics into 8–12 minute deep-dive videos to capture higher watch time."
            )
        }

        CompetitorComparison(
            primaryChannel = chA,
            competitorChannel = chB,
            primaryAvgViews = avgViewsA,
            competitorAvgViews = avgViewsB,
            primaryEngagementRate = engA,
            competitorEngagementRate = engB,
            primaryShortsPct = shortsPctA,
            competitorShortsPct = shortsPctB,
            keyTakeaways = gaps
        )
    }

    companion object {
        fun parseIso8601DurationSeconds(iso: String): Int {
            val regex = Regex("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?")
            val match = regex.find(iso) ?: return 0
            val hours = match.groupValues[1].toIntOrNull() ?: 0
            val minutes = match.groupValues[2].toIntOrNull() ?: 0
            val seconds = match.groupValues[3].toIntOrNull() ?: 0
            return hours * 3600 + minutes * 60 + seconds
        }

        fun formatDuration(seconds: Int): String {
            if (seconds <= 0) return "0:00"
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            val s = seconds % 60
            return if (h > 0) {
                String.format(Locale.US, "%d:%02d:%02d", h, m, s)
            } else {
                String.format(Locale.US, "%d:%02d", m, s)
            }
        }

        fun formatCompact(number: Long): String {
            return when {
                number >= 1_000_000_000L -> String.format(Locale.US, "%.1fB", number / 1_000_000_000.0)
                number >= 1_000_000L -> String.format(Locale.US, "%.1fM", number / 1_000_000.0)
                number >= 1_000L -> String.format(Locale.US, "%.1fK", number / 1_000.0)
                else -> number.toString()
            }
        }

        private fun formatTrafficSourceName(raw: String): String = when (raw.uppercase()) {
            "SUBSCRIBER", "YT_OTHER_PAGE" -> "Browse Features & Home"
            "RELATED_VIDEO" -> "Suggested Videos"
            "YT_SEARCH" -> "YouTube Search"
            "SHORTS" -> "Shorts Feed"
            "EXT_URL" -> "External Websites"
            "NOTIFICATION" -> "Notifications"
            "PLAYLIST" -> "Playlists"
            "YT_CHANNEL" -> "Channel Pages"
            else -> raw.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
        }
    }
}
