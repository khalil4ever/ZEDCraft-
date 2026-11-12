package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.BuildConfig
import com.example.data.remote.ChannelHealthReport
import com.example.data.remote.TopicSearchPatterns
import com.example.data.remote.YouTubeApiClient
import com.example.data.remote.YtAnalyticsOverview
import com.example.data.remote.YtChannelDetail
import com.example.data.remote.YtCommentItem
import com.example.data.remote.YtVideoDetail
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerationConfig
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "ZedCraftAiService"
private const val AI_TIMEOUT_MS = 20_000L
const val FRIENDLY_ZD_ERROR = "ZD is having trouble connecting. Please try again."

class ZdAiException(
    val userMessage: String,
    val isConfigError: Boolean = false,
    cause: Throwable? = null
) : Exception(userMessage, cause)

private suspend inline fun <T> runAiCatching(block: () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (ce: CancellationException) {
        if (ce is TimeoutCancellationException) {
            Result.failure(ZdAiException("ZD AI timed out waiting for a reply. Please tap Retry.", cause = ce))
        } else {
            throw ce
        }
    } catch (t: Throwable) {
        Result.failure(t)
    }
}

private suspend fun Call.executeCancellable(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation {
        runCatching { cancel() }
    }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) {
                cont.resume(response)
            } else {
                response.close()
            }
        }
    })
}

class ZedCraftAiService(
    private val apiKeyProvider: () -> String? = { runCatching { BuildConfig.GEMINI_API_KEY }.getOrNull() }
) {

    // Short-timeout client for structured/JSON single-turn calls
    private val directJsonClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    // Streaming SSE client: short connect timeout, per-chunk readTimeout, generous total callTimeout so long answers never abort at 15s
    private val directStreamClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    fun hasValidDirectApiKey(): Boolean = getDirectGeminiApiKey() != null

    internal fun getDirectGeminiApiKey(): String? {
        val key = runCatching { apiKeyProvider()?.trim() }.getOrNull()
        return if (!key.isNullOrBlank() && !key.startsWith("YOUR_") && key != "null") key else null
    }

    private fun mapHttpFailure(code: Int, body: String): ZdAiException {
        val apiMsg = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault("")
        return when (code) {
            401, 403 -> ZdAiException(
                userMessage = "Gemini API key was rejected (HTTP $code). Verify GEMINI_API_KEY in AI Studio Secrets.",
                isConfigError = true
            )
            400 -> if (apiMsg.contains("API key", ignoreCase = true)) {
                ZdAiException(
                    userMessage = "Gemini API key appears invalid. Check GEMINI_API_KEY in AI Studio Secrets.",
                    isConfigError = true
                )
            } else {
                ZdAiException("ZD could not process that request right now. Please tap Retry.")
            }
            429 -> ZdAiException("AI rate limit reached right now. Wait a few seconds and tap Retry.")
            503, 502, 504 -> ZdAiException("AI servers are temporarily busy. Please tap Retry.")
            else -> ZdAiException(FRIENDLY_ZD_ERROR)
        }
    }

    private fun mapNetworkFailure(e: Exception): ZdAiException {
        return when (e) {
            is ZdAiException -> e
            is UnknownHostException, is ConnectException -> ZdAiException(
                "No internet connection or AI service unreachable. Check your connection and tap Retry.",
                cause = e
            )
            is SocketTimeoutException -> ZdAiException(
                "ZD AI took too long to respond. Please tap Retry.",
                cause = e
            )
            else -> ZdAiException(FRIENDLY_ZD_ERROR, cause = e)
        }
    }

    private fun buildContentsJsonArray(
        promptText: String,
        bitmap: Bitmap? = null,
        chatHistory: List<Pair<String, String>> = emptyList()
    ): JSONArray {
        val contentsArr = JSONArray()
        // Include valid prior turns as native Gemini user/model alternating context
        chatHistory
            .filter { it.second.isNotBlank() }
            .takeLast(8)
            .forEach { (role, msg) ->
                val geminiRole = if (role.equals("user", ignoreCase = true)) "user" else "model"
                contentsArr.put(
                    JSONObject()
                        .put("role", geminiRole)
                        .put("parts", JSONArray().put(JSONObject().put("text", msg.take(1200))))
                )
            }

        val userParts = JSONArray()
        if (bitmap != null) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            val base64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
            val inlineData = JSONObject()
                .put("mimeType", "image/jpeg")
                .put("data", base64)
            userParts.put(JSONObject().put("inlineData", inlineData))
        }
        userParts.put(JSONObject().put("text", promptText))
        contentsArr.put(
            JSONObject()
                .put("role", "user")
                .put("parts", userParts)
        )
        return contentsArr
    }

    /**
     * Direct call to Google AI Studio Gemini REST API using the user's GEMINI_API_KEY.
     * Prioritizes verified fast models (~0.6s response) with automatic failover and clear error classification.
     */
    private suspend fun callDirectGoogleAiStudioApi(
        systemInstructionText: String,
        promptText: String,
        bitmap: Bitmap? = null,
        responseMimeType: String? = null,
        chatHistory: List<Pair<String, String>> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        val apiKey = getDirectGeminiApiKey()
            ?: throw ZdAiException(
                userMessage = "Gemini API key is missing. Configure GEMINI_API_KEY in AI Studio Secrets.",
                isConfigError = true
            )

        var lastFailure: ZdAiException? = null
        val contentsArr = buildContentsJsonArray(promptText, bitmap, chatHistory)

        for (model in ZedCraftAiConfig.DIRECT_MODEL_CHAIN) {
            coroutineContext.ensureActive()
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                val payload = JSONObject().apply {
                    put("contents", contentsArr)
                    if (systemInstructionText.isNotBlank()) {
                        put(
                            "systemInstruction",
                            JSONObject().put(
                                "parts",
                                JSONArray().put(JSONObject().put("text", systemInstructionText))
                            )
                        )
                    }
                    val genConfig = JSONObject()
                    if (responseMimeType == "application/json") {
                        genConfig.put("responseMimeType", "application/json")
                    }
                    put("generationConfig", genConfig)
                }

                val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder().url(url).post(body).build()

                directJsonClient.newCall(request).executeCancellable().use { response ->
                    val respStr = response.body?.string().orEmpty()
                    if (response.isSuccessful && respStr.isNotBlank()) {
                        val root = JSONObject(respStr)
                        val candidates = root.optJSONArray("candidates")
                        val firstCandidate = candidates?.optJSONObject(0)
                        val contentObj = firstCandidate?.optJSONObject("content")
                        val parts = contentObj?.optJSONArray("parts")
                        val candidateText = parts?.optJSONObject(0)?.optString("text").orEmpty().trim()
                        if (candidateText.isNotBlank()) {
                            Log.i(TAG, "Direct Google AI Studio API succeeded using $model")
                            return@withContext candidateText
                        }
                    } else {
                        Log.w(TAG, "Direct model $model returned HTTP ${response.code}")
                        val mapped = mapHttpFailure(response.code, respStr)
                        if (mapped.isConfigError) throw mapped
                        lastFailure = mapped
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (zd: ZdAiException) {
                if (zd.isConfigError) throw zd
                lastFailure = zd
            } catch (e: Exception) {
                Log.w(TAG, "Exception calling direct model $model: ${e.message}")
                val mapped = mapNetworkFailure(e)
                lastFailure = mapped
                if (e is UnknownHostException || e is ConnectException) {
                    throw mapped
                }
            }
        }
        throw lastFailure ?: ZdAiException(FRIENDLY_ZD_ERROR)
    }

    private data class RawModelResult(
        val text: String,
        val modelUsed: String,
        val fallbackNotice: String?
    )

    private fun isFirebaseInitialized(): Boolean {
        return runCatching {
            FirebaseApp.getInstance()
            true
        }.getOrDefault(false)
    }

    /**
     * Executes a prompt on direct Google AI Studio first, then falls back to Firebase AI Logic if initialized.
     * Preserves real error causes (`ZdAiException`) so users never see generic confusion.
     */
    private suspend fun executeMainModelWithFallback(
        systemInstructionText: String,
        responseSchema: Schema? = null,
        promptContent: Content,
        chatHistory: List<Pair<String, String>> = emptyList()
    ): RawModelResult {
        val promptText = promptContent.parts.filterIsInstance<com.google.firebase.ai.type.TextPart>().joinToString("\n") { it.text }
        val bitmap = promptContent.parts.filterIsInstance<com.google.firebase.ai.type.ImagePart>().firstOrNull()?.image

        var directFailure: ZdAiException? = null
        try {
            val directResult = callDirectGoogleAiStudioApi(
                systemInstructionText = systemInstructionText,
                promptText = promptText,
                bitmap = bitmap,
                responseMimeType = if (responseSchema != null) "application/json" else null,
                chatHistory = chatHistory
            )
            if (directResult.isNotBlank()) {
                return RawModelResult(
                    text = directResult,
                    modelUsed = "ZD AI (Direct Studio)",
                    fallbackNotice = null
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (zd: ZdAiException) {
            directFailure = zd
            if (zd.isConfigError && !isFirebaseInitialized()) {
                throw zd
            }
        }

        if (!isFirebaseInitialized()) {
            throw directFailure ?: ZdAiException(FRIENDLY_ZD_ERROR)
        }

        val mainConfig: GenerationConfig = generationConfig {
            if (responseSchema != null) {
                responseMimeType = "application/json"
                this.responseSchema = responseSchema
            }
        }

        var lastException: Exception? = directFailure
        for (modelName in listOf(ZedCraftAiConfig.ZD_MAIN_MODEL, ZedCraftAiConfig.LITE_MODEL).distinct()) {
            coroutineContext.ensureActive()
            try {
                val model = Firebase.ai.generativeModel(
                    modelName = modelName,
                    generationConfig = mainConfig,
                    systemInstruction = content { text(systemInstructionText) }
                )
                val response = withTimeout(AI_TIMEOUT_MS) {
                    model.generateContent(promptContent)
                }
                val text = response.text?.trim().orEmpty()
                if (text.isNotBlank()) {
                    return RawModelResult(
                        text = text,
                        modelUsed = "ZD AI",
                        fallbackNotice = null
                    )
                }
            } catch (e: CancellationException) {
                if (e !is TimeoutCancellationException) throw e
                lastException = e
                Log.w(TAG, "Firebase AI model ($modelName) timed out", e)
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Firebase AI model ($modelName) failed: ${e.message}", e)
            }
        }

        Log.e(TAG, "All ZD AI attempts failed", lastException)
        throw directFailure ?: ZdAiException(FRIENDLY_ZD_ERROR, cause = lastException)
    }

    /**
     * Executes a fast tool request on direct Google AI Studio first, with Firebase AI fallback if initialized.
     */
    private suspend fun executeFastModel(
        systemInstructionText: String,
        responseSchema: Schema,
        promptContent: Content
    ): RawModelResult {
        val promptText = promptContent.parts.filterIsInstance<com.google.firebase.ai.type.TextPart>().joinToString("\n") { it.text }
        val bitmap = promptContent.parts.filterIsInstance<com.google.firebase.ai.type.ImagePart>().firstOrNull()?.image
        var directFailure: ZdAiException? = null
        try {
            val directResult = callDirectGoogleAiStudioApi(
                systemInstructionText = systemInstructionText,
                promptText = promptText,
                bitmap = bitmap,
                responseMimeType = "application/json"
            )
            if (directResult.isNotBlank()) {
                return RawModelResult(
                    text = directResult,
                    modelUsed = "ZD AI (Direct Studio)",
                    fallbackNotice = null
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (zd: ZdAiException) {
            directFailure = zd
            if (zd.isConfigError && !isFirebaseInitialized()) {
                throw zd
            }
        }

        if (!isFirebaseInitialized()) {
            throw directFailure ?: ZdAiException(FRIENDLY_ZD_ERROR)
        }

        val config = generationConfig {
            responseMimeType = "application/json"
            this.responseSchema = responseSchema
        }

        var lastError: Exception? = directFailure
        for (modelName in listOf(ZedCraftAiConfig.FAST_MODEL, ZedCraftAiConfig.LITE_MODEL).distinct()) {
            coroutineContext.ensureActive()
            try {
                val model = Firebase.ai.generativeModel(
                    modelName = modelName,
                    generationConfig = config,
                    systemInstruction = content { text(systemInstructionText) }
                )
                val response = withTimeout(AI_TIMEOUT_MS) {
                    model.generateContent(promptContent)
                }
                val text = response.text?.trim().orEmpty()
                if (text.isNotBlank()) {
                    return RawModelResult(
                        text = text,
                        modelUsed = "ZD AI",
                        fallbackNotice = null
                    )
                }
            } catch (e: CancellationException) {
                if (e !is TimeoutCancellationException) throw e
                lastError = e
                Log.w(TAG, "Firebase AI fast model ($modelName) timed out", e)
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Firebase AI fast model ($modelName) failed: ${e.message}", e)
            }
        }

        Log.e(TAG, "Fast tool AI call failed after retries", lastError)
        throw directFailure ?: ZdAiException(FRIENDLY_ZD_ERROR, cause = lastError)
    }

    // -----------------------------------------------------------------------
    // 1. THUMBNAIL SCORER (Multimodal Gemini Vision)
    // -----------------------------------------------------------------------
    private val thumbnailScoreSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "overallScore" to Schema.integer(description = "Overall thumbnail score out of 100"),
                "faceEmotionScore" to Schema.integer(description = "Face & emotion clarity score 0-100"),
                "faceEmotionNote" to Schema.string(description = "Specific observation on face/subject emotion"),
                "textReadabilityScore" to Schema.integer(description = "Text readability & size score 0-100"),
                "textReadabilityNote" to Schema.string(description = "Specific observation on thumbnail text count, font weight, and contrast"),
                "contrastColorPopScore" to Schema.integer(description = "Contrast and color pop score 0-100"),
                "contrastColorPopNote" to Schema.string(description = "Specific observation on foreground vs background separation"),
                "compositionFocalScore" to Schema.integer(description = "Composition and single focal point score 0-100"),
                "compositionFocalNote" to Schema.string(description = "Rule of thirds and eye-path assessment"),
                "clutterScore" to Schema.integer(description = "Simplicity / low-clutter score 0-100 (100 = clean, 0 = cluttered)"),
                "clutterNote" to Schema.string(description = "Count of competing visual elements"),
                "mobileLegibilityScore" to Schema.integer(description = "Legibility at small mobile feed size 0-100"),
                "mobileLegibilityNote" to Schema.string(description = "How well details read at 120dp phone feed width"),
                "curiosityAppealScore" to Schema.integer(description = "Curiosity gap & click appeal score 0-100"),
                "curiosityAppealNote" to Schema.string(description = "Visual story and curiosity tension"),
                "titleSynergyScore" to Schema.integer(description = "How well the thumbnail and title work as a pair without repeating words 0-100"),
                "titleSynergyVerdict" to Schema.string(description = "Assessment of Title + Thumbnail synergy"),
                "problemAreas" to Schema.array(
                    items = Schema.string(),
                    description = "Specific problem zones or visual weaknesses on the thumbnail"
                ),
                "actionableFixes" to Schema.array(
                    items = Schema.string(),
                    description = "Specific, actionable design fixes to increase CTR"
                )
            )
        )
    }

    suspend fun scoreThumbnailWithVision(
        bitmap: Bitmap,
        pairedVideoTitle: String,
        nicheContext: String = ""
    ): Result<ThumbnailScoreResult> = runCatching {
        val scaledBitmap = scaleBitmapForVision(bitmap, maxDimension = 1024)
        val prompt = buildString {
            appendLine("Analyze this YouTube thumbnail image like a world-class YouTube Packaging Strategist.")
            if (pairedVideoTitle.isNotBlank()) {
                appendLine("Paired Video Title: \"$pairedVideoTitle\"")
                appendLine("Check strictly whether the thumbnail text/visuals complement the title or waste space repeating the exact same words.")
            } else {
                appendLine("No paired title provided; evaluate standalone visual hook and suggest how a title should pair with it.")
            }
            if (nicheContext.isNotBlank()) {
                appendLine("Channel Niche Context: $nicheContext")
            }
            appendLine("Be honest, critical, and ultra-specific about colors, text legibility at tiny mobile size, bottom-right timestamp collision zone, facial expression or focal object, and visual clutter.")
        }

        val raw = executeFastModel(
            systemInstructionText = ZedCraftAiConfig.ZD_SYSTEM_INSTRUCTION,
            responseSchema = thumbnailScoreSchema,
            promptContent = content {
                image(scaledBitmap)
                text(prompt)
            }
        )

        val json = JSONObject(raw.text)
        val breakdown = listOf(
            SubMetricScore("Face & Subject Emotion", json.optInt("faceEmotionScore", 75), json.optString("faceEmotionNote")),
            SubMetricScore("Text Readability & Size", json.optInt("textReadabilityScore", 75), json.optString("textReadabilityNote")),
            SubMetricScore("Contrast & Color Pop", json.optInt("contrastColorPopScore", 75), json.optString("contrastColorPopNote")),
            SubMetricScore("Composition & Focal Point", json.optInt("compositionFocalScore", 75), json.optString("compositionFocalNote")),
            SubMetricScore("Visual Simplicity (No Clutter)", json.optInt("clutterScore", 75), json.optString("clutterNote")),
            SubMetricScore("Mobile-Feed Legibility", json.optInt("mobileLegibilityScore", 75), json.optString("mobileLegibilityNote")),
            SubMetricScore("Curiosity & Click Appeal", json.optInt("curiosityAppealScore", 75), json.optString("curiosityAppealNote"))
        )

        ThumbnailScoreResult(
            overallScore = json.optInt("overallScore", 75).coerceIn(0, 100),
            breakdown = breakdown,
            titleSynergyScore = json.optInt("titleSynergyScore", 78).coerceIn(0, 100),
            titleSynergyVerdict = json.optString("titleSynergyVerdict", "Ensure thumbnail text complements rather than repeats the title."),
            problemAreas = jsonArrayToList(json.optJSONArray("problemAreas")),
            actionableFixes = jsonArrayToList(json.optJSONArray("actionableFixes")),
            modelUsed = raw.modelUsed,
            fallbackNotice = raw.fallbackNotice
        )
    }

    // -----------------------------------------------------------------------
    // 2. THUMBNAIL A/B COMPARE (Two Thumbnails Head-to-Head via Vision)
    // -----------------------------------------------------------------------
    private val thumbnailAbSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "winner" to Schema.enumeration(values = listOf("A", "B"), description = "Winning thumbnail: A or B"),
                "scoreA" to Schema.integer(description = "Overall score for Thumbnail A (0-100)"),
                "scoreB" to Schema.integer(description = "Overall score for Thumbnail B (0-100)"),
                "headlineVerdict" to Schema.string(description = "One-sentence summary of why the winner beats the runner-up"),
                "reasonsWinnerWon" to Schema.array(
                    items = Schema.string(),
                    description = "Specific visual reasons the winning thumbnail wins on mobile feeds"
                ),
                "categoryComparisons" to Schema.array(
                    items = Schema.string(),
                    description = "Head-to-head breakdown across Contrast, Mobile Text Legibility, Focal Point, and Curiosity"
                ),
                "howToMakeWinnerEvenBetter" to Schema.array(
                    items = Schema.string(),
                    description = "Actionable tweaks to push the winning thumbnail even higher"
                ),
                "titlePairSynergy" to Schema.string(description = "Which thumbnail pairs better with the video title and why")
            )
        )
    }

    suspend fun compareThumbnailsAb(
        bitmapA: Bitmap,
        bitmapB: Bitmap,
        videoTitle: String
    ): Result<ThumbnailAbResult> = runCatching {
        val scaledA = scaleBitmapForVision(bitmapA, maxDimension = 800)
        val scaledB = scaleBitmapForVision(bitmapB, maxDimension = 800)
        val prompt = buildString {
            appendLine("Compare Thumbnail A (first image) and Thumbnail B (second image) head-to-head for a YouTube video.")
            if (videoTitle.isNotBlank()) {
                appendLine("Video Title: \"$videoTitle\"")
            }
            appendLine("Pick a clear winner (A or B) based on 150ms mobile feed scan speed, contrast, emotional clarity, curiosity gap, and synergy with the title.")
        }

        val raw = executeFastModel(
            systemInstructionText = ZedCraftAiConfig.ZD_SYSTEM_INSTRUCTION,
            responseSchema = thumbnailAbSchema,
            promptContent = content {
                text("Thumbnail A:")
                image(scaledA)
                text("Thumbnail B:")
                image(scaledB)
                text(prompt)
            }
        )

        val json = JSONObject(raw.text)
        ThumbnailAbResult(
            winner = json.optString("winner", "A").uppercase().let { if (it == "B") "B" else "A" },
            scoreA = json.optInt("scoreA", 76).coerceIn(0, 100),
            scoreB = json.optInt("scoreB", 72).coerceIn(0, 100),
            headlineVerdict = json.optString("headlineVerdict"),
            reasonsWinnerWon = jsonArrayToList(json.optJSONArray("reasonsWinnerWon")),
            categoryComparisons = jsonArrayToList(json.optJSONArray("categoryComparisons")),
            howToMakeWinnerEvenBetter = jsonArrayToList(json.optJSONArray("howToMakeWinnerEvenBetter")),
            titlePairSynergy = json.optString("titlePairSynergy"),
            modelUsed = raw.modelUsed,
            fallbackNotice = raw.fallbackNotice
        )
    }

    // -----------------------------------------------------------------------
    // 3. TITLE OPTIMIZER (Score out of 100 + 10 Better Title Suggestions)
    // -----------------------------------------------------------------------
    private val titleOptimizerSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "overallScore" to Schema.integer(description = "Overall title score 0-100"),
                "clarityScore" to Schema.integer(description = "Instant clarity of topic/value 0-100"),
                "clarityNote" to Schema.string(),
                "curiosityScore" to Schema.integer(description = "Curiosity gap without deceptive clickbait 0-100"),
                "curiosityNote" to Schema.string(),
                "keywordPlacementScore" to Schema.integer(description = "Keywords & search relevance score 0-100"),
                "keywordPlacementNote" to Schema.string(),
                "lengthScore" to Schema.integer(description = "Length & mobile fit score 0-100"),
                "lengthNote" to Schema.string(),
                "clickAppealScore" to Schema.integer(description = "Click appeal, emotional pull & specificity 0-100"),
                "clickAppealNote" to Schema.string(),
                "improvedTitles" to Schema.array(
                    description = "Exactly 10 original, better title suggestions, each with its own score out of 100, tagBadge ('Best for Search', 'Best for Clicks', or 'Balanced'), and a short reason",
                    items = Schema.obj(
                        properties = mapOf(
                            "title" to Schema.string(description = "The improved original title (never copy a competitor word-for-word; 42-60 chars ideal)"),
                            "score" to Schema.integer(description = "Score out of 100"),
                            "tagBadge" to Schema.enumeration(
                                values = listOf("Best for Search", "Best for Clicks", "Balanced"),
                                description = "Primary strategic strength of this title"
                            ),
                            "angleType" to Schema.string(description = "e.g., Curiosity Gap, Search Winner, Small-Channel Outlier Angle, Contrarian, Direct Proof"),
                            "reason" to Schema.string(description = "Short specific reason why this title ranks or earns clicks based on the real search patterns")
                        )
                    )
                )
            )
        )
    }

    private fun buildRealPatternsPromptBlock(searchPatterns: TopicSearchPatterns?): String {
        if (searchPatterns == null || !searchPatterns.isLiveYouTubeData || searchPatterns.topVideos.isEmpty()) {
            return "Note: No live YouTube search data available for this request. Generate original, accurate AI suggestions without inventing search volume or view statistics."
        }
        return buildString {
            appendLine("=== REAL YOUTUBE DATA API v3 TOP-RANKING VIDEOS & PATTERNS ===")
            appendLine("Search Topic: \"${searchPatterns.query}\" (${searchPatterns.totalVideosAnalyzed} top videos analyzed)")
            appendLine("Average Top-Result Views: ${YouTubeApiClient.formatCompact(searchPatterns.avgViews)} | Median Views: ${YouTubeApiClient.formatCompact(searchPatterns.medianViews)} | Max Views: ${YouTubeApiClient.formatCompact(searchPatterns.maxViews)}")
            appendLine("Average Winning Title Length: ${searchPatterns.avgTitleLength} chars | Videos Using Numbers/Years: ${searchPatterns.numberUsageCount}/${searchPatterns.totalVideosAnalyzed}")
            if (searchPatterns.commonKeywords.isNotEmpty()) {
                appendLine("Most Common Keywords in Top Results: ${searchPatterns.commonKeywords.joinToString(", ")}")
            }
            if (searchPatterns.repeatingPhrases.isNotEmpty()) {
                appendLine("Repeating Phrases Across Top Results: ${searchPatterns.repeatingPhrases.joinToString(" | ")}")
            }
            if (searchPatterns.strongOpeningWords.isNotEmpty()) {
                appendLine("Strong Opening Words in Winners: ${searchPatterns.strongOpeningWords.joinToString(", ")}")
            }
            if (searchPatterns.smallChannelOutlierGaps.isNotEmpty()) {
                appendLine("Small-Channel Outlier Gaps (Angles where smaller channels rank): ${searchPatterns.smallChannelOutlierGaps.joinToString(" | ")}")
            }
            appendLine("Top Ranking Competitor Videos (DO NOT copy word-for-word; synthesize fresh original angles):")
            searchPatterns.topVideos.take(8).forEachIndexed { idx, v ->
                appendLine("${idx + 1}. \"${v.title}\" (${YouTubeApiClient.formatCompact(v.viewCount)} views, ${YouTubeApiClient.formatCompact(v.channelSubscribers)} subs, uploaded ${v.publishedAt.take(10)})")
            }
        }
    }

    suspend fun scoreAndOptimizeTitle(
        rawTitle: String,
        videoContextOrNiche: String = "",
        searchPatterns: TopicSearchPatterns? = null
    ): Result<TitleOptimizationResult> = runCatching {
        val cleanTitle = rawTitle.trim()
        require(cleanTitle.isNotBlank()) { "Please enter a video title to score and optimize." }

        val prompt = buildString {
            appendLine("Score this YouTube video title out of 100 across Clarity, Curiosity, Keywords, Length, and Click Appeal, then generate 10 original Better Title Suggestions:")
            appendLine("User's Draft / Video Title: \"$cleanTitle\" (${cleanTitle.length} characters)")
            if (videoContextOrNiche.isNotBlank()) {
                appendLine("Video Topic / Niche Context: $videoContextOrNiche")
            }
            appendLine()
            appendLine(buildRealPatternsPromptBlock(searchPatterns))
            appendLine()
            appendLine("CRITICAL RULES:")
            appendLine("1. Return JSON matching the schema with: overallScore, clarityScore, clarityNote, curiosityScore, curiosityNote, keywordPlacementScore, keywordPlacementNote, lengthScore, lengthNote, clickAppealScore, clickAppealNote, and improvedTitles (array of 10 objects with title, score, tagBadge, angleType, reason).")
            appendLine("2. Generate 10 distinct, original title options. NEVER copy any competitor's title word-for-word. Make them fresh, accurate to the user's video, and non-clickbait.")
            appendLine("3. Assign each title a tagBadge of exactly 'Best for Search', 'Best for Clicks', or 'Balanced' (include a healthy mix of all three across the 10 options).")
            appendLine("4. Keep most titles between 44 and 60 characters so they do not cut off on mobile feeds.")
        }

        val raw = executeFastModel(
            systemInstructionText = ZedCraftAiConfig.ZD_SYSTEM_INSTRUCTION,
            responseSchema = titleOptimizerSchema,
            promptContent = content { text(prompt) }
        )

        val json = JSONObject(escapeFenceIfNeeded(raw.text))
        val breakdown = listOf(
            SubMetricScore("Clarity", json.optInt("clarityScore", 76), json.optString("clarityNote", "Clear topic framing.")),
            SubMetricScore("Curiosity", json.optInt("curiosityScore", 74), json.optString("curiosityNote", "Can sharpen open loop.")),
            SubMetricScore("Keywords", json.optInt("keywordPlacementScore", 78), json.optString("keywordPlacementNote", "Primary keyword alignment.")),
            SubMetricScore("Length (${cleanTitle.length} chars)", json.optInt("lengthScore", if (cleanTitle.length in 40..60) 92 else 70), json.optString("lengthNote", if (cleanTitle.length > 60) "May truncate on mobile feeds (>60 chars)." else "Fits mobile feeds cleanly.")),
            SubMetricScore("Click Appeal", json.optInt("clickAppealScore", 75), json.optString("clickAppealNote", "Stronger proof/specificity lifts CTR."))
        )

        val suggestionsArr = json.optJSONArray("improvedTitles") ?: JSONArray()
        val suggestions = mutableListOf<SuggestedTitleOption>()
        val validBadges = setOf("Best for Search", "Best for Clicks", "Balanced")
        for (i in 0 until suggestionsArr.length()) {
            val item = suggestionsArr.optJSONObject(i) ?: continue
            val t = item.optString("title").trim()
            if (t.isBlank()) continue
            val rawBadge = item.optString("tagBadge", "").trim()
            val badge = if (rawBadge in validBadges) {
                rawBadge
            } else {
                when (i % 3) {
                    0 -> "Best for Search"
                    1 -> "Best for Clicks"
                    else -> "Balanced"
                }
            }
            suggestions.add(
                SuggestedTitleOption(
                    title = t,
                    score = item.optInt("score", (94 - i).coerceIn(82, 97)).coerceIn(0, 100),
                    angleType = item.optString("angleType", badge),
                    tagBadge = badge,
                    reason = item.optString("reason", "Uses top-ranking keyword structure with a clean curiosity hook.")
                )
            )
        }

        // Guarantee 10 options even if the model returned fewer
        val fallbackTemplates = listOf(
            Triple("$cleanTitle: What Nobody Tells You (2026)", "Best for Clicks", "Opens a specific curiosity gap while keeping the core topic front-loaded."),
            Triple("How to Master $cleanTitle (Step-by-Step Guide)", "Best for Search", "Targets high-intent tutorial search queries with clear promise."),
            Triple("I Tested $cleanTitle for 30 Days — Honest Results", "Best for Clicks", "First-person proof and timeframe pattern that drives high browse CTR."),
            Triple("$cleanTitle Explained in 10 Minutes", "Balanced", "Combines exact search keyword with a concrete time-to-value hook."),
            Triple("7 $cleanTitle Mistakes Costing You Results", "Best for Clicks", "Uses a specific number and loss-aversion framing proven in top results."),
            Triple("The Ultimate $cleanTitle Blueprint for Beginners", "Best for Search", "Captures foundational evergreen search demand."),
            Triple("Why 90% of People Get $cleanTitle Wrong", "Best for Clicks", "Contrarian small-channel outlier hook that stands out in feeds."),
            Triple("$cleanTitle: Full Breakdown & Real Examples", "Balanced", "Balances search discoverability with tangible proof."),
            Triple("Before You Try $cleanTitle, Watch This First", "Best for Clicks", "High-urgency pre-decision framing that earns clicks from active searchers."),
            Triple("$cleanTitle — Complete 2026 Strategy", "Best for Search", "Clean, authoritative search title under 60 characters.")
        )
        var fallbackIdx = 0
        while (suggestions.size < 10 && fallbackIdx < fallbackTemplates.size) {
            val (tplTitle, tplBadge, tplReason) = fallbackTemplates[fallbackIdx++]
            if (suggestions.none { it.title.equals(tplTitle, ignoreCase = true) }) {
                suggestions.add(
                    SuggestedTitleOption(
                        title = tplTitle.take(68),
                        score = (91 - suggestions.size).coerceIn(80, 94),
                        angleType = tplBadge,
                        tagBadge = tplBadge,
                        reason = tplReason
                    )
                )
            }
        }

        val mobilePreview = if (cleanTitle.length > 60) {
            cleanTitle.take(58).trimEnd() + "..."
        } else {
            cleanTitle
        }

        TitleOptimizationResult(
            originalTitle = cleanTitle,
            overallScore = json.optInt("overallScore", 74).coerceIn(0, 100),
            breakdown = breakdown,
            mobilePreviewText = mobilePreview,
            truncatesOnMobile = cleanTitle.length > 60,
            tenImprovedTitles = suggestions.take(10),
            modelUsed = raw.modelUsed,
            fallbackNotice = raw.fallbackNotice
        )
    }

    // -----------------------------------------------------------------------
    // 4. DESCRIPTION & CHAPTERS OPTIMIZER (Score + 3 Ready-to-Use Variations)
    // -----------------------------------------------------------------------
    private val descriptionOptimizerSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "currentDraftScore" to Schema.integer(description = "Score of the user's current description draft 0-100"),
                "currentDraftDiagnosis" to Schema.string(description = "Short diagnosis of the current description's hook, keywords, chapters, and hashtags"),
                "overallScore" to Schema.integer(description = "Score of the optimized description 0-100"),
                "firstTwoLinesHook" to Schema.string(description = "Strong first 2 lines (130-160 chars) shown above 'Show more' and in search results"),
                "fullOptimizedDescription" to Schema.string(description = "Complete ready-to-apply YouTube description with strong first 2 lines, natural keywords, 00:00 chapters, CTA, and 3 hashtags at the end"),
                "generatedChapters" to Schema.array(
                    items = Schema.string(),
                    description = "Timestamps starting with 00:00 Intro"
                ),
                "recommendedCtaBlock" to Schema.string(description = "Next-video watch link & subscribe CTA block"),
                "threeHashtags" to Schema.array(
                    items = Schema.string(),
                    description = "Exactly 3 relevant #hashtags"
                ),
                "variations" to Schema.array(
                    description = "3 ready-to-use description variations: 'Search-focused', 'Click-focused', and 'Balanced'",
                    items = Schema.obj(
                        properties = mapOf(
                            "variationLabel" to Schema.string(description = "'Search-focused', 'Click-focused', or 'Balanced'"),
                            "score" to Schema.integer(description = "Score of this variation 0-100"),
                            "firstTwoLinesPreview" to Schema.string(description = "First 2 lines shown before 'Show more'"),
                            "fullDescription" to Schema.string(description = "Full ready-to-use description including first 2 lines, natural keywords, 00:00 chapters, CTA, and 3 hashtags at the end"),
                            "naturalKeywordsUsed" to Schema.array(items = Schema.string(), description = "Real search keywords woven naturally into this variation")
                        )
                    )
                ),
                "improvementsSummary" to Schema.array(
                    items = Schema.string(),
                    description = "Bullet points explaining what was improved"
                )
            )
        )
    }

    suspend fun optimizeDescriptionAndChapters(
        videoTitleOrTopic: String,
        existingDescription: String,
        transcriptOrOutline: String,
        searchPatterns: TopicSearchPatterns? = null
    ): Result<DescriptionOptimizationResult> = runCatching {
        require(videoTitleOrTopic.isNotBlank() || existingDescription.isNotBlank()) {
            "Enter a video title/topic or paste an existing description."
        }

        // Compute deterministic baseline score for the existing description if present
        val deterministicDraftScore = if (existingDescription.isBlank()) {
            45
        } else {
            val hasChapters = existingDescription.contains("0:00") || existingDescription.contains("00:00")
            val hashtagCount = Regex("#[a-zA-Z0-9_]+").findAll(existingDescription).count()
            val lenScore = when {
                existingDescription.length >= 300 -> 38
                existingDescription.length >= 140 -> 26
                else -> 14
            }
            (40 + lenScore + (if (hasChapters) 12 else 0) + (if (hashtagCount in 1..5) 10 else 0)).coerceIn(35, 92)
        }

        val prompt = buildString {
            appendLine("Score the user's current YouTube description out of 100 and generate 3 ready-to-use optimized description variations ('Search-focused', 'Click-focused', and 'Balanced'):")
            appendLine("Video Title / Topic: $videoTitleOrTopic")
            if (existingDescription.isNotBlank()) {
                appendLine("User's Current Description (${existingDescription.length} chars):\n$existingDescription")
            } else {
                appendLine("User's Current Description: (Empty draft)")
            }
            if (transcriptOrOutline.isNotBlank()) {
                appendLine("Transcript / Captions / Video Notes (use this to generate accurate 00:00 chapters):\n$transcriptOrOutline")
            }
            appendLine()
            appendLine(buildRealPatternsPromptBlock(searchPatterns))
            appendLine()
            appendLine("CRITICAL RULES:")
            appendLine("1. Return JSON with currentDraftScore, currentDraftDiagnosis, overallScore, firstTwoLinesHook, fullOptimizedDescription, generatedChapters, recommendedCtaBlock, threeHashtags, variations (array of 3 objects: variationLabel, score, firstTwoLinesPreview, fullDescription, naturalKeywordsUsed), and improvementsSummary.")
            appendLine("2. Never copy any competitor's description word-for-word.")
            appendLine("3. Every variation MUST include: strong first 2 lines (shown before 'Show more'), natural keywords from the real search data, 00:00 timestamps/chapters placeholders, a clear call to action, and 3 hashtags at the very end.")
        }

        val raw = executeFastModel(
            systemInstructionText = ZedCraftAiConfig.ZD_SYSTEM_INSTRUCTION,
            responseSchema = descriptionOptimizerSchema,
            promptContent = content { text(prompt) }
        )

        val json = JSONObject(escapeFenceIfNeeded(raw.text))
        val chapters = jsonArrayToList(json.optJSONArray("generatedChapters")).ifEmpty {
            listOf(
                "00:00 Intro & Key Hook",
                "01:15 Core Breakdown: $videoTitleOrTopic",
                "04:00 Step-by-Step Strategy & Proof",
                "07:30 Common Mistakes to Avoid",
                "09:45 Final Verdict & Next Steps"
            )
        }
        val hashtags = jsonArrayToList(json.optJSONArray("threeHashtags"))
            .map { if (it.startsWith("#")) it else "#${it.replace(" ", "")}" }
            .take(3)
            .ifEmpty {
                searchPatterns?.topHashtags?.take(3)?.ifEmpty { null }
                    ?: listOf("#YouTubeGrowth", "#CreatorTips", "#ZEDCraft")
            }
        val ctaBlock = json.optString("recommendedCtaBlock").ifBlank {
            "Subscribe for weekly deep dives and watch the next breakdown linked on screen!"
        }
        val primaryFullDesc = json.optString("fullOptimizedDescription").ifBlank {
            buildString {
                appendLine(json.optString("firstTwoLinesHook").ifBlank { "Learn everything you need to know about $videoTitleOrTopic with practical, tested strategies." })
                appendLine()
                appendLine("Chapters:")
                chapters.forEach { appendLine(it) }
                appendLine()
                appendLine(ctaBlock)
                appendLine()
                append(hashtags.joinToString(" "))
            }
        }

        val variationsArr = json.optJSONArray("variations") ?: JSONArray()
        val parsedVariations = mutableListOf<DescriptionVariationOption>()
        for (i in 0 until variationsArr.length()) {
            val vObj = variationsArr.optJSONObject(i) ?: continue
            val fullText = vObj.optString("fullDescription").trim()
            if (fullText.isBlank()) continue
            val firstTwo = vObj.optString("firstTwoLinesPreview").ifBlank {
                fullText.lines().filter { it.isNotBlank() }.take(2).joinToString(" ").take(160)
            }
            parsedVariations.add(
                DescriptionVariationOption(
                    variationLabel = vObj.optString("variationLabel", when (i) {
                        0 -> "Search-focused"
                        1 -> "Click-focused"
                        else -> "Balanced"
                    }),
                    score = vObj.optInt("score", 94 - i).coerceIn(0, 100),
                    firstTwoLinesPreview = firstTwo,
                    fullDescription = fullText,
                    naturalKeywordsUsed = jsonArrayToList(vObj.optJSONArray("naturalKeywordsUsed"))
                        .ifEmpty { searchPatterns?.commonKeywords?.take(5).orEmpty() },
                    chapters = chapters,
                    callToAction = ctaBlock,
                    threeHashtags = hashtags
                )
            )
        }

        val kws = searchPatterns?.commonKeywords?.take(5).orEmpty()
        if (parsedVariations.isEmpty()) {
            parsedVariations.add(
                DescriptionVariationOption(
                    variationLabel = "Search-focused",
                    score = 95,
                    firstTwoLinesPreview = json.optString("firstTwoLinesHook").ifBlank {
                        primaryFullDesc.lines().filter { it.isNotBlank() }.take(2).joinToString(" ").take(160)
                    },
                    fullDescription = primaryFullDesc,
                    naturalKeywordsUsed = kws,
                    chapters = chapters,
                    callToAction = ctaBlock,
                    threeHashtags = hashtags
                )
            )
        }
        if (parsedVariations.size == 1) {
            val clickHook = "Most people miss the #1 detail in $videoTitleOrTopic — here is the exact breakdown and real test results you can use today."
            val clickFull = buildString {
                appendLine(clickHook)
                appendLine()
                appendLine("In this video, we break down $videoTitleOrTopic step by step${if (kws.isNotEmpty()) " covering ${kws.joinToString(", ")}" else ""} so you can skip the trial and error.")
                appendLine()
                appendLine("Timestamps / Chapters:")
                chapters.forEach { appendLine(it) }
                appendLine()
                appendLine("👉 $ctaBlock")
                appendLine()
                append(hashtags.joinToString(" "))
            }
            parsedVariations.add(
                DescriptionVariationOption(
                    variationLabel = "Click-focused",
                    score = 93,
                    firstTwoLinesPreview = clickHook,
                    fullDescription = clickFull,
                    naturalKeywordsUsed = kws,
                    chapters = chapters,
                    callToAction = ctaBlock,
                    threeHashtags = hashtags
                )
            )
        }
        if (parsedVariations.size == 2) {
            val balancedHook = "Complete guide to $videoTitleOrTopic: what works right now, key takeaways, and step-by-step timestamps."
            val balancedFull = buildString {
                appendLine(balancedHook)
                appendLine()
                appendLine(primaryFullDesc)
            }
            parsedVariations.add(
                DescriptionVariationOption(
                    variationLabel = "Balanced",
                    score = 92,
                    firstTwoLinesPreview = balancedHook,
                    fullDescription = balancedFull,
                    naturalKeywordsUsed = kws,
                    chapters = chapters,
                    callToAction = ctaBlock,
                    threeHashtags = hashtags
                )
            )
        }

        DescriptionOptimizationResult(
            overallScore = json.optInt("overallScore", 94).coerceIn(0, 100),
            currentDraftScore = json.optInt("currentDraftScore", deterministicDraftScore).coerceIn(0, 100),
            currentDraftDiagnosis = json.optString("currentDraftDiagnosis").ifBlank {
                if (existingDescription.isBlank()) {
                    "No existing description provided — adding a strong 2-line search hook, 00:00 chapters, and 3 hashtags unlocks Search snippet visibility."
                } else {
                    "Evaluated hook above 'Show more', keyword placement, 00:00 chapters, and bottom hashtags."
                }
            },
            firstTwoLinesHook = parsedVariations.first().firstTwoLinesPreview,
            fullOptimizedDescription = parsedVariations.first().fullDescription,
            generatedChapters = chapters,
            recommendedCtaBlock = ctaBlock,
            threeHashtags = hashtags,
            variations = parsedVariations.take(3),
            improvementsSummary = jsonArrayToList(json.optJSONArray("improvementsSummary")),
            modelUsed = raw.modelUsed,
            fallbackNotice = raw.fallbackNotice
        )
    }

    // -----------------------------------------------------------------------
    // 5. TAGS & HASHTAGS GENERATOR (Score + 3 Hashtags + Commonality Tag List)
    // -----------------------------------------------------------------------
    private val tagsGeneratorSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "currentTagsScore" to Schema.integer(description = "Score of the user's current tags & hashtags 0-100"),
                "currentTagsDiagnosis" to Schema.string(description = "Short assessment of current tags coverage and character usage"),
                "recommendedTags" to Schema.array(
                    items = Schema.string(),
                    description = "15 to 22 high-relevance YouTube tags (exact topic, long-tail search phrases, and related keywords)"
                ),
                "threeBestHashtags" to Schema.array(
                    items = Schema.string(),
                    description = "The 3 best #hashtags for the video description"
                ),
                "strategyExplanation" to Schema.string(description = "How these tags combine real ranking video tags with search intent")
            )
        )
    }

    suspend fun generateTagsAndHashtags(
        topicOrTitle: String,
        realRankingTags: List<String>,
        searchPatterns: TopicSearchPatterns? = null
    ): Result<TagsGenerationResult> = runCatching {
        val cleanTopic = topicOrTitle.trim()
        require(cleanTopic.isNotBlank()) { "Enter a video topic or title to generate tags." }

        val competitorFreqList = searchPatterns?.tagFrequencies.orEmpty()
        val liveTopTags = competitorFreqList.filter { it.isFromLiveSearch }.map { it.tag }
        val combinedSeedTags = (liveTopTags + realRankingTags).distinctBy { it.lowercase() }

        val deterministicTagsScore = when {
            realRankingTags.size >= 10 -> 86
            realRankingTags.size >= 5 -> 72
            realRankingTags.isNotEmpty() -> 58
            else -> 42
        }

        val prompt = buildString {
            appendLine("Score the user's current tags out of 100 and generate an optimal YouTube tag set (within YouTube's 500-character total limit) plus the 3 best description hashtags for:")
            appendLine("Topic / Title: \"$cleanTopic\"")
            if (realRankingTags.isNotEmpty()) {
                appendLine("User's Current Tags (${realRankingTags.size}): ${realRankingTags.joinToString(", ")}")
            }
            if (competitorFreqList.isNotEmpty()) {
                appendLine("Real Tags Used by Top-Ranking Videos on YouTube:")
                competitorFreqList.take(15).forEach { tf ->
                    appendLine("- \"${tf.tag}\" (${tf.commonalityLabel})")
                }
            }
            appendLine("Return JSON with currentTagsScore, currentTagsDiagnosis, recommendedTags, threeBestHashtags, and strategyExplanation.")
        }

        val raw = executeFastModel(
            systemInstructionText = ZedCraftAiConfig.ZD_SYSTEM_INSTRUCTION,
            responseSchema = tagsGeneratorSchema,
            promptContent = content { text(prompt) }
        )

        val json = JSONObject(escapeFenceIfNeeded(raw.text))
        val aiGeneratedTags = jsonArrayToList(json.optJSONArray("recommendedTags"))
        val rawCandidateTags = (combinedSeedTags.take(12) + aiGeneratedTags)
            .map { it.trim().removePrefix("#") }
            .filter { it.isNotBlank() && it.length in 2..48 }
            .distinctBy { it.lowercase() }

        // Enforce YouTube's strict 500-character tag limit (including commas) and build SmartTagItem list with commonality
        val fittedTags = mutableListOf<String>()
        val smartItems = mutableListOf<SmartTagItem>()
        var charTotal = 0
        val totalVideos = searchPatterns?.totalVideosAnalyzed ?: 0

        for (tag in rawCandidateTags) {
            val addition = if (fittedTags.isEmpty()) tag.length else tag.length + 2
            if (charTotal + addition <= 480) {
                fittedTags.add(tag)
                charTotal += addition

                val matchedFreq = competitorFreqList.firstOrNull { it.tag.equals(tag, ignoreCase = true) }
                if (matchedFreq != null && matchedFreq.isFromLiveSearch && matchedFreq.usedInTopCount > 0) {
                    smartItems.add(
                        SmartTagItem(
                            tag = tag,
                            usedInTopVideosCount = matchedFreq.usedInTopCount,
                            totalTopVideosAnalyzed = matchedFreq.totalVideosAnalyzed,
                            commonalityLabel = matchedFreq.commonalityLabel,
                            isFromLiveYouTubeData = true
                        )
                    )
                } else if (searchPatterns?.isLiveYouTubeData == true && searchPatterns.commonKeywords.any { tag.contains(it, ignoreCase = true) }) {
                    val kwMatches = searchPatterns.topVideos.count { v ->
                        v.title.contains(tag, ignoreCase = true) || v.tags.any { t -> t.equals(tag, ignoreCase = true) }
                    }
                    if (kwMatches > 0) {
                        smartItems.add(
                            SmartTagItem(
                                tag = tag,
                                usedInTopVideosCount = kwMatches,
                                totalTopVideosAnalyzed = totalVideos,
                                commonalityLabel = "Used in $kwMatches/$totalVideos top videos",
                                isFromLiveYouTubeData = true
                            )
                        )
                    } else {
                        smartItems.add(
                            SmartTagItem(
                                tag = tag,
                                usedInTopVideosCount = 0,
                                totalTopVideosAnalyzed = totalVideos,
                                commonalityLabel = "Related search keyword",
                                isFromLiveYouTubeData = false
                            )
                        )
                    }
                } else {
                    smartItems.add(
                        SmartTagItem(
                            tag = tag,
                            usedInTopVideosCount = 0,
                            totalTopVideosAnalyzed = totalVideos,
                            commonalityLabel = if (searchPatterns?.isLiveYouTubeData == true) "Related search keyword" else "AI suggested tag",
                            isFromLiveYouTubeData = false
                        )
                    )
                }
            }
        }

        val hashtags = jsonArrayToList(json.optJSONArray("threeBestHashtags"))
            .map { if (it.startsWith("#")) it else "#${it.replace(" ", "")}" }
            .take(3)
            .ifEmpty {
                searchPatterns?.topHashtags?.take(3)?.ifEmpty { null }
                    ?: listOf("#YouTubeGrowth", "#CreatorTips", "#ZEDCraft")
            }

        TagsGenerationResult(
            topic = cleanTopic,
            currentTagsScore = json.optInt("currentTagsScore", deterministicTagsScore).coerceIn(0, 100),
            currentTagsDiagnosis = json.optString("currentTagsDiagnosis").ifBlank {
                "Built ${fittedTags.size} tags ($charTotal/500 chars) combining top-ranking competitor tags and long-tail search variations."
            },
            recommendedTags = fittedTags,
            smartTagItems = smartItems,
            totalCharacterCount = charTotal,
            threeBestHashtags = hashtags,
            competitorTagsIncluded = smartItems.count { it.isFromLiveYouTubeData },
            strategyExplanation = json.optString("strategyExplanation"),
            modelUsed = raw.modelUsed
        )
    }

    private fun escapeFenceIfNeeded(rawText: String): String {
        val trimmed = rawText.trim()
        if (trimmed.startsWith("```")) {
            return trimmed
                .removePrefix("```json")
                .removePrefix("```JSON")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
        }
        return trimmed
    }

    // -----------------------------------------------------------------------
    // 6. AI TREND & VIDEO IDEA FINDER
    // -----------------------------------------------------------------------
    private val videoIdeasSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "nicheSummary" to Schema.string(description = "Strategic summary of the creator's positioning and current trend opportunity"),
                "ideas" to Schema.array(
                    description = "6 high-opportunity video ideas with packaging concepts",
                    items = Schema.obj(
                        properties = mapOf(
                            "title" to Schema.string(description = "High-CTR non-clickbait video title"),
                            "thumbnailConcept" to Schema.string(description = "Visual scene, composition, and emotion for the thumbnail"),
                            "thumbnailTextOverlay" to Schema.string(description = "2-4 words of bold thumbnail text that complement (not repeat) the title"),
                            "hookFirst15Seconds" to Schema.string(description = "Visual + spoken hook for the first 15 seconds"),
                            "targetPillar" to Schema.string(description = "e.g., Outlier Reach, Search Evergreen, or Authority Depth"),
                            "whyItFitsChannel" to Schema.string(description = "Why this idea works based on the channel's winners or current trends"),
                            "opportunityScore" to Schema.integer(description = "Estimated opportunity score 0-100")
                        )
                    )
                )
            )
        )
    }

    suspend fun generateChannelVideoIdeas(
        nicheOrTopic: String,
        channel: YtChannelDetail?,
        topChannelVideos: List<YtVideoDetail>,
        trendingVideos: List<YtVideoDetail>
    ): Result<VideoIdeasResult> = runCatching {
        val prompt = buildString {
            appendLine("Generate 6 high-opportunity YouTube video ideas with complete Title + Thumbnail + 15-second Hook packaging.")
            appendLine("Creator Niche / Focus: ${nicheOrTopic.ifBlank { channel?.title ?: "General Creator" }}")
            if (channel != null) {
                appendLine("Connected Channel: ${channel.title} (${channel.handle}, ${channel.subscriberCount} subscribers)")
            }
            if (topChannelVideos.isNotEmpty()) {
                appendLine("Channel's Best-Performing Real Videos:")
                topChannelVideos.take(5).forEach { v ->
                    appendLine("- \"${v.title}\" (${v.viewCount} views, ${String.format("%.1f%%", v.engagementRatePercent)} engagement)")
                }
            }
            if (trendingVideos.isNotEmpty()) {
                appendLine("Current Real Trending Formats on YouTube:")
                trendingVideos.take(5).forEach { v ->
                    appendLine("- \"${v.title}\" by ${v.channelTitle} (${v.viewCount} views)")
                }
            }
            appendLine("Ensure each idea has a distinct title, a visual thumbnail concept whose overlay text complements (never repeats) the title, and a 15-second retention hook.")
        }

        val raw = executeMainModelWithFallback(
            systemInstructionText = ZedCraftAiConfig.ZD_SYSTEM_INSTRUCTION,
            responseSchema = videoIdeasSchema,
            promptContent = content { text(prompt) }
        )

        val json = JSONObject(raw.text)
        val ideasArr = json.optJSONArray("ideas") ?: JSONArray()
        val ideas = mutableListOf<GeneratedVideoIdea>()
        for (i in 0 until ideasArr.length()) {
            val obj = ideasArr.optJSONObject(i) ?: continue
            ideas.add(
                GeneratedVideoIdea(
                    title = obj.optString("title"),
                    thumbnailConcept = obj.optString("thumbnailConcept"),
                    thumbnailTextOverlay = obj.optString("thumbnailTextOverlay"),
                    hookFirst15Seconds = obj.optString("hookFirst15Seconds"),
                    targetPillar = obj.optString("targetPillar", "Outlier Growth"),
                    whyItFitsChannel = obj.optString("whyItFitsChannel"),
                    opportunityScore = obj.optInt("opportunityScore", 88).coerceIn(0, 100)
                )
            )
        }

        VideoIdeasResult(
            nicheSummary = json.optString("nicheSummary"),
            ideas = ideas,
            modelUsed = raw.modelUsed,
            fallbackNotice = raw.fallbackNotice
        )
    }

    // -----------------------------------------------------------------------
    // 7. ZD AI ASSISTANT CHAT (Skill Routing + Channel Context + Tool Cards)
    // -----------------------------------------------------------------------
    private val zdChatSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "replyMarkdown" to Schema.string(description = "Direct, actionable, step-by-step response from ZD grounded in real channel data when available"),
                "invokeTool" to Schema.enumeration(
                    values = listOf("NONE", "SCORE_TITLE", "GENERATE_TAGS", "CHANNEL_HEALTH", "CHANNEL_ANALYTICS", "VIDEO_AUDIT"),
                    description = "Which ZEDCraft built-in tool card to attach to this response"
                ),
                "toolTargetInput" to Schema.string(description = "The title, keyword, or video title extracted from the user prompt for the tool card"),
                "toolScore" to Schema.integer(description = "Score 0-100 for the attached tool card if invokeTool != NONE"),
                "toolSummaryBullets" to Schema.array(
                    items = Schema.string(),
                    description = "3-4 rich data bullets for the attached tool card"
                ),
                "toolCopyableOutput" to Schema.string(description = "Copy-ready title, tags list, or checklist for the tool card"),
                "followUpPrompts" to Schema.array(
                    items = Schema.string(),
                    description = "3 specific next questions the user can tap"
                )
            )
        )
    }

    suspend fun sendZdAssistantMessage(
        userPrompt: String,
        explicitSkillId: String? = null,
        connectedChannel: YtChannelDetail?,
        recentVideos: List<YtVideoDetail>,
        analyticsOverview: YtAnalyticsOverview?,
        healthReport: ChannelHealthReport?,
        recentChatHistory: List<Pair<String, String>>,
        attachedThumbnail: Bitmap? = null
    ): Result<ZdAssistantResponse> = runCatching {
        val routedSkill = ZdSkillPack.routeSkillForPrompt(userPrompt, explicitSkillId)

        val systemInstruction = buildString {
            appendLine(ZedCraftAiConfig.ZD_SYSTEM_INSTRUCTION)
            appendLine()
            appendLine("=== ACTIVE ZD SKILL MODULE ===")
            appendLine(routedSkill.instructions)
            appendLine()
            if (connectedChannel != null) {
                appendLine("=== REAL CONNECTED YOUTUBE CHANNEL DATA (DO NOT INVENT STATS) ===")
                appendLine("Channel: ${connectedChannel.title} (${connectedChannel.handle}) • ID: ${connectedChannel.channelId}")
                appendLine("Subscribers: ${connectedChannel.subscriberCount} • Total Views: ${connectedChannel.viewCount} • Total Videos: ${connectedChannel.videoCount}")
                if (healthReport != null) {
                    appendLine("ZEDCraft Channel Health Score: ${healthReport.overallHealthScore}/100")
                    appendLine("- Upload Consistency: ${healthReport.uploadConsistencyScore}/100 (${String.format("%.1f", healthReport.uploadsPerMonth)} uploads/mo)")
                    appendLine("- Packaging & Views Momentum: ${healthReport.ctrAndPackagingScore}/100")
                    appendLine("- Best Upload Window: ${healthReport.bestUploadSchedule}")
                    appendLine("- Diagnosis: ${healthReport.dropDiagnosisSummary}")
                }
                if (analyticsOverview != null) {
                    appendLine("Analytics (${analyticsOverview.daysRange}d): ${analyticsOverview.totalViews} views, ${analyticsOverview.watchTimeMinutes} watch mins, AVD ${analyticsOverview.averageViewDurationSeconds}s (${String.format("%.1f%%", analyticsOverview.averageViewPercentage)} APV).")
                    appendLine("Data Source: ${analyticsOverview.dataSourceNote}")
                }
                if (recentVideos.isNotEmpty()) {
                    appendLine("Recent Uploads (Most Recent First):")
                    recentVideos.take(8).forEachIndexed { idx, v ->
                        appendLine("${idx + 1}. \"${v.title}\" [${if (v.isShort) "Short" else "Long-form"}] — ${v.viewCount} views, ${v.likeCount} likes, ${v.commentCount} comments (${String.format("%.2f%%", v.engagementRatePercent)} eng), published ${v.publishedAt.take(10)}")
                    }
                }
            } else {
                appendLine("=== CHANNEL CONNECTION STATUS ===")
                appendLine("No YouTube channel is currently connected. State clearly when you are giving general YouTube best-practice guidance, and remind the user they can connect their channel in the Home or Profile tab for personalized channel analytics.")
            }
        }

        val conversationPrompt = buildString {
            if (recentChatHistory.isNotEmpty()) {
                appendLine("Recent Conversation Context:")
                recentChatHistory.takeLast(6).forEach { (role, text) ->
                    appendLine("${role.uppercase()}: ${text.take(600)}")
                }
                appendLine()
            }
            appendLine("User Request: $userPrompt")
            appendLine("Respond as ZD using Skill #${routedSkill.number} (${routedSkill.title}). If the user asks to score a title, generate tags, check channel health, audit a video, or inspect analytics, populate invokeTool and the tool card fields with real numbers/output.")
        }

        val promptContent = if (attachedThumbnail != null) {
            val scaled = scaleBitmapForVision(attachedThumbnail, 800)
            content {
                image(scaled)
                text(conversationPrompt)
            }
        } else {
            content {
                text(conversationPrompt)
            }
        }

        val raw = executeMainModelWithFallback(
            systemInstructionText = systemInstruction,
            responseSchema = zdChatSchema,
            promptContent = promptContent
        )

        val json = JSONObject(raw.text)
        val invokeToolStr = json.optString("invokeTool", "NONE")
        val toolType = when (invokeToolStr) {
            "SCORE_TITLE" -> ZdToolType.SCORE_TITLE
            "GENERATE_TAGS" -> ZdToolType.GENERATE_TAGS
            "CHANNEL_HEALTH" -> ZdToolType.CHANNEL_HEALTH
            "CHANNEL_ANALYTICS" -> ZdToolType.CHANNEL_ANALYTICS
            "VIDEO_AUDIT" -> ZdToolType.VIDEO_AUDIT
            else -> if (attachedThumbnail != null) ZdToolType.SCORE_THUMBNAIL else null
        }

        val toolCard = if (toolType != null) {
            ZdToolExecutionCard(
                toolType = toolType,
                title = json.optString("toolTargetInput").ifBlank { toolType.displayName },
                score = json.optInt("toolScore", healthReport?.overallHealthScore ?: 85).coerceIn(0, 100),
                badgeText = "ZD Tool • ${toolType.displayName}",
                bulletPoints = jsonArrayToList(json.optJSONArray("toolSummaryBullets")),
                copyableText = json.optString("toolCopyableOutput")
            )
        } else null

        val followUps = jsonArrayToList(json.optJSONArray("followUpPrompts"))
            .take(3)
            .ifEmpty { routedSkill.examplePrompts.take(3) }

        ZdAssistantResponse(
            replyMarkdown = json.optString("replyMarkdown"),
            routedSkill = routedSkill,
            toolCard = toolCard,
            followUpPrompts = followUps,
            modelUsed = raw.modelUsed,
            fallbackNotice = raw.fallbackNotice
        )
    }

    private fun scaleBitmapForVision(source: Bitmap, maxDimension: Int): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= maxDimension && h <= maxDimension) return source
        val ratio = w.toFloat() / h.toFloat()
        val (newW, newH) = if (w > h) {
            maxDimension to (maxDimension / ratio).toInt().coerceAtLeast(1)
        } else {
            (maxDimension * ratio).toInt().coerceAtLeast(1) to maxDimension
        }
        return Bitmap.createScaledBitmap(source, newW, newH, true)
    }

    // -----------------------------------------------------------------------
    // 8. FULL METADATA & CUSTOM TITLE SCORER + COACH HELPERS FOR VIEWMODEL
    // -----------------------------------------------------------------------

    suspend fun generateOptimizedTitlesAndMetadata(
        topicOrDraftTitle: String,
        niche: String,
        currentDescription: String,
        currentTags: List<String>,
        channelContext: String,
        searchPatterns: TopicSearchPatterns? = null
    ): Result<TitleOptimizerResponse> = runCatching {
        val titleResult = scoreAndOptimizeTitle(
            rawTitle = topicOrDraftTitle,
            videoContextOrNiche = "$niche | $channelContext",
            searchPatterns = searchPatterns
        ).getOrThrow()

        val descResult = runCatching {
            optimizeDescriptionAndChapters(
                videoTitleOrTopic = topicOrDraftTitle,
                existingDescription = currentDescription,
                transcriptOrOutline = "",
                searchPatterns = searchPatterns
            ).getOrThrow()
        }.getOrNull()

        val tagsResult = runCatching {
            generateTagsAndHashtags(
                topicOrTitle = topicOrDraftTitle,
                realRankingTags = currentTags,
                searchPatterns = searchPatterns
            ).getOrThrow()
        }.getOrNull()

        val diagnosisText = titleResult.breakdown.joinToString(" • ") { m ->
            "${m.label}: ${m.score}/100 (${m.note})"
        }

        val isLiveYt = searchPatterns?.isLiveYouTubeData == true
        val mappedSuggestions = titleResult.tenImprovedTitles.map { s ->
            val subBase = (s.score / 5).coerceIn(14, 20)
            TitleSuggestionItem(
                title = s.title,
                framework = s.angleType,
                tagBadge = s.tagBadge,
                score = s.score,
                clarityScore = subBase,
                curiosityScore = subBase,
                keywordScore = (subBase - 1).coerceAtLeast(14),
                lengthScore = if (s.title.length in 38..60) 20 else 16,
                clickAppealScore = subBase,
                characterCount = s.title.length,
                whyItWorks = s.reason,
                isBackedByLiveYouTubeData = isLiveYt
            )
        }

        TitleOptimizerResponse(
            originalScore = titleResult.overallScore,
            originalDiagnosis = diagnosisText,
            suggestedTitles = mappedSuggestions,
            optimizedDescription = descResult?.fullOptimizedDescription
                ?: currentDescription.ifBlank { "Watch this deep dive on $topicOrDraftTitle!" },
            suggestedChapters = descResult?.generatedChapters ?: listOf("00:00 Intro", "01:30 Core Breakdown", "05:00 Key Takeaways"),
            recommendedTags = tagsResult?.recommendedTags ?: currentTags,
            hashtags = descResult?.threeHashtags ?: tagsResult?.threeBestHashtags ?: listOf("#YouTubeGrowth", "#CreatorTips", "#ZEDCraft"),
            isBackedByLiveYouTubeData = isLiveYt,
            dataSourceBadge = searchPatterns?.dataSourceLabel ?: "AI suggestion (no live YouTube data)",
            quotaFallbackNotice = searchPatterns?.quotaExhaustedOrOfflineNote,
            searchPatternsSummary = searchPatterns?.patternSummaryBullets.orEmpty(),
            topCompetitorReferenceTitles = searchPatterns?.topVideos?.take(5)?.map {
                "${it.title} (${YouTubeApiClient.formatCompact(it.viewCount)} views • ${YouTubeApiClient.formatCompact(it.channelSubscribers)} subs)"
            }.orEmpty(),
            descriptionScore = descResult?.currentDraftScore ?: 78,
            descriptionDiagnosis = descResult?.currentDraftDiagnosis ?: "Optimized first 2 lines, natural search keywords, 00:00 chapters, and 3 hashtags.",
            descriptionVariations = descResult?.variations.orEmpty(),
            tagsAndHashtagsScore = tagsResult?.currentTagsScore ?: 80,
            tagsDiagnosis = tagsResult?.currentTagsDiagnosis ?: "Recommended tags built from top ranking results within YouTube's 500-character limit.",
            smartTagSuggestions = tagsResult?.smartTagItems.orEmpty()
        )
    }

    suspend fun scoreSingleCustomTitle(
        customTitle: String,
        niche: String,
        channelContext: String,
        searchPatterns: TopicSearchPatterns? = null
    ): Result<TitleSuggestionItem> = runCatching {
        val res = scoreAndOptimizeTitle(
            rawTitle = customTitle,
            videoContextOrNiche = "$niche | $channelContext",
            searchPatterns = searchPatterns
        ).getOrThrow()

        val clarity = res.breakdown.firstOrNull { it.label.startsWith("Clarity") }?.score ?: 78
        val curiosity = res.breakdown.firstOrNull { it.label.startsWith("Curiosity") }?.score ?: 78
        val keywords = res.breakdown.firstOrNull { it.label.startsWith("Keywords") }?.score ?: 78
        val length = res.breakdown.firstOrNull { it.label.startsWith("Length") }?.score ?: 78
        val clickAppeal = res.breakdown.firstOrNull { it.label.startsWith("Click") }?.score ?: 78
        val summaryWhy = res.breakdown.joinToString(" • ") { "${it.label}: ${it.note}" }

        TitleSuggestionItem(
            title = customTitle,
            framework = "Your Custom Title",
            tagBadge = if (keywords >= curiosity) "Best for Search" else "Best for Clicks",
            score = res.overallScore,
            clarityScore = (clarity / 5).coerceIn(1, 20),
            curiosityScore = (curiosity / 5).coerceIn(1, 20),
            keywordScore = (keywords / 5).coerceIn(1, 20),
            lengthScore = (length / 5).coerceIn(1, 20),
            clickAppealScore = (clickAppeal / 5).coerceIn(1, 20),
            characterCount = customTitle.length,
            whyItWorks = summaryWhy,
            isBackedByLiveYouTubeData = searchPatterns?.isLiveYouTubeData == true
        )
    }

    suspend fun critiqueThumbnailWithVision(
        primaryBitmap: Bitmap,
        videoTitle: String,
        comparisonBitmap: Bitmap? = null
    ): Result<ThumbnailVisionResponse> = runCatching {
        val scoreRes = scoreThumbnailWithVision(
            bitmap = primaryBitmap,
            pairedVideoTitle = videoTitle
        ).getOrThrow()

        val abNote = if (comparisonBitmap != null) {
            runCatching {
                val ab = compareThumbnailsAb(primaryBitmap, comparisonBitmap, videoTitle).getOrThrow()
                "A/B Winner: Thumbnail ${ab.winner} (${ab.scoreA} vs ${ab.scoreB}) — ${ab.headlineVerdict}"
            }.getOrNull()
        } else null

        val readability = scoreRes.breakdown.firstOrNull { it.label.contains("Readability") }?.score ?: 78
        val contrast = scoreRes.breakdown.firstOrNull { it.label.contains("Contrast") }?.score ?: 78
        val emotion = scoreRes.breakdown.firstOrNull { it.label.contains("Emotion") }?.score ?: 78
        val mobileNote = scoreRes.breakdown.firstOrNull { it.label.contains("Mobile") }?.note
            ?: scoreRes.titleSynergyVerdict

        ThumbnailVisionResponse(
            overallScore = scoreRes.overallScore,
            readabilityScore = readability,
            contrastAndColorScore = contrast,
            emotionalHookScore = emotion,
            titleSynergyScore = scoreRes.titleSynergyScore,
            mobileSquintTestVerdict = if (abNote != null) "$mobileNote\n\n$abNote" else mobileNote,
            timestampZoneWarning = scoreRes.problemAreas.firstOrNull()
                ?: "Keep the bottom-right corner clear of text so the YouTube duration badge doesn't cover it.",
            actionableImprovements = scoreRes.actionableFixes
        )
    }

    suspend fun generateScriptAndHooks(
        topic: String,
        targetDurationMinutes: Int,
        tone: String,
        channelContext: String
    ): Result<String> = runAiCatching {
        val prompt = """
            Write a high-retention YouTube video script & hook blueprint for:
            - Topic / Title: "$topic"
            - Target Duration: $targetDurationMinutes minutes
            - Tone & Pacing: $tone
            - Channel Context: $channelContext

            Include:
            1. **3 Opening 30-Second Hook Options** (Curiosity Gap, Proof-First, Pattern Interrupt) with exact spoken lines + visual B-roll cues.
            2. **Minute-by-Minute Retention Outline** with mid-video re-hooks every 90 seconds.
            3. **Binge-Loop Ending Script** (final 20 seconds that bridges directly into the viewer's next video without saying "thanks for watching").
        """.trimIndent()

        val raw = executeMainModelWithFallback(
            systemInstructionText = ZedCraftAiConfig.ZD_SYSTEM_INSTRUCTION,
            responseSchema = null,
            promptContent = content { text(prompt) }
        )
        raw.text
    }

    suspend fun generateShortsRepurposePlan(
        videoTitle: String,
        videoDescriptionOrTranscript: String
    ): Result<String> = runAiCatching {
        val prompt = """
            Create 3 high-retention vertical YouTube Shorts (<60s each) repurposed from this video:
            - Source Video: "$videoTitle"
            - Notes / Description / Transcript: ${videoDescriptionOrTranscript.take(1500)}

            For each Short provide:
            1. **Short Title (<50 chars)**
            2. **First 2-Second Visual & Verbal Hook**
            3. **Word-for-Word 45-Second Script**
            4. **Seamless Loop Ending** (final sentence flows grammatically back into the first sentence)
            5. **Related Video Bridge CTA** (how to point viewers to the full long-form video).
        """.trimIndent()

        val raw = executeMainModelWithFallback(
            systemInstructionText = ZedCraftAiConfig.ZD_SYSTEM_INSTRUCTION,
            responseSchema = null,
            promptContent = content { text(prompt) }
        )
        raw.text
    }

    suspend fun analyzeCommentsAndAudienceSentiment(
        videoTitle: String,
        comments: List<YtCommentItem>
    ): Result<String> = runAiCatching {
        val sample = comments.take(35).joinToString("\n") { "- ${it.authorName} (${it.likeCount} likes): ${it.textDisplay.take(180)}" }
        val prompt = """
            Analyze these real viewer comments on the YouTube video "$videoTitle":
            $sample

            Provide:
            1. **Overall Audience Sentiment & Emotional Reaction**
            2. **Top Unanswered Viewer Questions & Pain Points**
            3. **3 Sequel Video Ideas Requested by Viewers** (with clickable titles)
            4. **Recommended Pinned Comment** to boost replies right now.
        """.trimIndent()

        val raw = executeMainModelWithFallback(
            systemInstructionText = ZedCraftAiConfig.ZD_SYSTEM_INSTRUCTION,
            responseSchema = null,
            promptContent = content { text(prompt) }
        )
        raw.text
    }

    private suspend fun streamDirectGoogleAiStudioApi(
        systemInstructionText: String,
        promptText: String,
        chatHistory: List<Pair<String, String>> = emptyList(),
        onStreamingUpdate: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val apiKey = getDirectGeminiApiKey()
            ?: throw ZdAiException(
                userMessage = "Gemini API key is missing. Configure GEMINI_API_KEY in AI Studio Secrets.",
                isConfigError = true
            )

        var lastFailure: ZdAiException? = null
        val contentsArr = buildContentsJsonArray(promptText, bitmap = null, chatHistory = chatHistory)

        for (model in ZedCraftAiConfig.DIRECT_MODEL_CHAIN) {
            coroutineContext.ensureActive()
            val accumulated = StringBuilder()
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse&key=$apiKey"
                val payload = JSONObject().apply {
                    put("contents", contentsArr)
                    if (systemInstructionText.isNotBlank()) {
                        put(
                            "systemInstruction",
                            JSONObject().put(
                                "parts",
                                JSONArray().put(JSONObject().put("text", systemInstructionText))
                            )
                        )
                    }
                }

                val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder().url(url).post(body).build()

                directStreamClient.newCall(request).executeCancellable().use { response ->
                    if (!response.isSuccessful) {
                        val errBody = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
                        Log.w(TAG, "Direct SSE stream $model returned HTTP ${response.code}")
                        val mapped = mapHttpFailure(response.code, errBody)
                        if (mapped.isConfigError) throw mapped
                        lastFailure = mapped
                        return@use
                    }
                    val reader = response.body?.charStream()?.buffered() ?: return@use
                    reader.forEachLine { line ->
                        coroutineContext.ensureActive()
                        val trimmed = line.trim()
                        if (trimmed.startsWith("data:")) {
                            val jsonStr = trimmed.removePrefix("data:").trim()
                            if (jsonStr.isNotEmpty() && jsonStr != "[DONE]") {
                                runCatching {
                                    val root = JSONObject(jsonStr)
                                    val chunkText = root.optJSONArray("candidates")
                                        ?.optJSONObject(0)
                                        ?.optJSONObject("content")
                                        ?.optJSONArray("parts")
                                        ?.optJSONObject(0)
                                        ?.optString("text")
                                        .orEmpty()
                                    if (chunkText.isNotEmpty()) {
                                        accumulated.append(chunkText)
                                        onStreamingUpdate(accumulated.toString())
                                    }
                                }
                            }
                        }
                    }
                }

                val finalResult = accumulated.toString().trim()
                if (finalResult.isNotBlank()) {
                    Log.i(TAG, "Direct SSE stream succeeded using $model (${finalResult.length} chars)")
                    return@withContext finalResult
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (zd: ZdAiException) {
                if (zd.isConfigError) throw zd
                lastFailure = zd
            } catch (e: Exception) {
                // If stream already delivered meaningful content before socket drop, keep it rather than throwing it away
                val partial = accumulated.toString().trim()
                if (partial.length >= 40) {
                    Log.w(TAG, "SSE stream ended early on $model with ${partial.length} chars preserved: ${e.message}")
                    return@withContext partial
                }
                Log.w(TAG, "Exception streaming direct model $model: ${e.message}")
                val mapped = mapNetworkFailure(e)
                lastFailure = mapped
                if (e is UnknownHostException || e is ConnectException) {
                    throw mapped
                }
            }
        }
        throw lastFailure ?: ZdAiException(FRIENDLY_ZD_ERROR)
    }

    suspend fun askZdAssistant(
        userPrompt: String,
        channelContext: String,
        chatHistory: List<Pair<String, String>>,
        onStreamingUpdate: ((String) -> Unit)? = null
    ): Result<String> = runAiCatching {
        val systemText = buildString {
            appendLine(ZedCraftAiConfig.ZD_SYSTEM_INSTRUCTION)
            appendLine()
            appendLine("=== CONNECTED CHANNEL CONTEXT ===")
            appendLine(channelContext)
        }

        if (onStreamingUpdate != null) {
            var streamFailure: ZdAiException? = null
            try {
                val streamed = streamDirectGoogleAiStudioApi(
                    systemInstructionText = systemText,
                    promptText = userPrompt,
                    chatHistory = chatHistory,
                    onStreamingUpdate = onStreamingUpdate
                )
                if (streamed.isNotBlank()) {
                    return@runAiCatching streamed
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (zd: ZdAiException) {
                streamFailure = zd
                if (zd.isConfigError && !isFirebaseInitialized()) {
                    throw zd
                }
                if (zd.cause is UnknownHostException || zd.cause is ConnectException) {
                    throw zd
                }
            }

            // Fallback to non-streaming call (direct JSON / Firebase AI) if SSE stream had an issue
            try {
                val raw = executeMainModelWithFallback(
                    systemInstructionText = systemText,
                    responseSchema = null,
                    promptContent = content { text(userPrompt) },
                    chatHistory = chatHistory
                )
                onStreamingUpdate.invoke(raw.text)
                return@runAiCatching raw.text
            } catch (ce: CancellationException) {
                throw ce
            } catch (fallbackErr: Exception) {
                throw streamFailure ?: fallbackErr
            }
        }

        val raw = executeMainModelWithFallback(
            systemInstructionText = systemText,
            responseSchema = null,
            promptContent = content { text(userPrompt) },
            chatHistory = chatHistory
        )
        raw.text
    }

    private fun jsonArrayToList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { idx ->
            arr.optString(idx).trim().takeIf { it.isNotBlank() }
        }
    }

    companion object {
        const val FRIENDLY_ZD_ERROR = "ZD is having trouble connecting. Please try again."

        fun extractFriendlyError(throwable: Throwable?): String {
            return when (throwable) {
                is ZdAiException -> throwable.userMessage
                is UnknownHostException, is ConnectException ->
                    "No internet connection or AI service unreachable. Check your connection and tap Retry."
                is SocketTimeoutException, is TimeoutCancellationException ->
                    "ZD AI timed out waiting for a reply. Please tap Retry."
                else -> throwable?.localizedMessage
                    ?.takeIf { it.isNotBlank() && !it.contains("Exception", ignoreCase = true) && it.length <= 160 }
                    ?: FRIENDLY_ZD_ERROR
            }
        }
    }
}
