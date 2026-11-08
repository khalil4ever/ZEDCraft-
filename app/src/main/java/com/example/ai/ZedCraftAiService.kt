package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.remote.ChannelHealthReport
import com.example.data.remote.YtAnalyticsOverview
import com.example.data.remote.YtChannelDetail
import com.example.data.remote.YtCommentItem
import com.example.data.remote.YtVideoDetail
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerationConfig
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

import com.example.BuildConfig
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val TAG = "ZedCraftAiService"
private const val AI_TIMEOUT_MS = 45_000L
const val FRIENDLY_ZD_ERROR = "ZD is having trouble connecting. Please try again."

class ZedCraftAiService {

    private val directHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    private fun getDirectGeminiApiKey(): String? {
        val key = runCatching { BuildConfig.GEMINI_API_KEY.trim() }.getOrNull()
        return if (!key.isNullOrBlank() && !key.startsWith("YOUR_") && key != "null") key else null
    }

    /**
     * Direct call to Google AI Studio Gemini REST API using the user's GEMINI_API_KEY.
     * This bypasses Firebase App Check token requirements completely!
     */
    private suspend fun callDirectGoogleAiStudioApi(
        systemInstructionText: String,
        promptText: String,
        bitmap: Bitmap? = null,
        responseMimeType: String? = null
    ): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val apiKey = getDirectGeminiApiKey() ?: return@withContext null
        try {
            val model = ZedCraftAiConfig.FAST_MODEL
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

            val contentsArr = JSONArray()
            val userContent = JSONObject().put("role", "user")
            val partsArr = JSONArray()

            if (bitmap != null) {
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                val base64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
                val inlineData = JSONObject()
                    .put("mimeType", "image/jpeg")
                    .put("data", base64)
                partsArr.put(JSONObject().put("inlineData", inlineData))
            }

            partsArr.put(JSONObject().put("text", promptText))
            userContent.put("parts", partsArr)
            contentsArr.put(userContent)

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
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            directHttpClient.newCall(request).execute().use { response ->
                val respStr = response.body?.string().orEmpty()
                if (response.isSuccessful && respStr.isNotBlank()) {
                    val root = JSONObject(respStr)
                    val candidates = root.optJSONArray("candidates")
                    val firstCandidate = candidates?.optJSONObject(0)
                    val contentObj = firstCandidate?.optJSONObject("content")
                    val parts = contentObj?.optJSONArray("parts")
                    val candidateText = parts?.optJSONObject(0)?.optString("text").orEmpty().trim()
                    if (candidateText.isNotBlank()) {
                        Log.i(TAG, "Direct Google AI Studio API call succeeded via GEMINI_API_KEY!")
                        return@withContext candidateText
                    }
                } else {
                    Log.w(TAG, "Direct Google AI Studio API error (${response.code}): $respStr")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception calling direct Google AI Studio API", e)
        }
        null
    }

    private data class RawModelResult(
        val text: String,
        val modelUsed: String,
        val fallbackNotice: String?
    )

    /**
     * Executes a prompt on ZD_MAIN_MODEL:
     * 1. Tries ZD_MAIN_MODEL.
     * 2. If it fails, retries ZD_MAIN_MODEL once.
     * 3. If it fails again, falls back to FAST_MODEL (gemini-3.8-flash) with retry.
     * Technical details are logged to Logcat only; user-facing errors always use FRIENDLY_ZD_ERROR.
     */
    private suspend fun executeMainModelWithFallback(
        systemInstructionText: String,
        responseSchema: Schema? = null,
        promptContent: Content
    ): RawModelResult {
        val mainConfig: GenerationConfig = generationConfig {
            if (responseSchema != null) {
                responseMimeType = "application/json"
                this.responseSchema = responseSchema
            }
        }

        // Attempt 1 & Retry 1 on ZD_MAIN_MODEL
        for (attempt in 1..2) {
            try {
                val mainModel = Firebase.ai.generativeModel(
                    modelName = ZedCraftAiConfig.ZD_MAIN_MODEL,
                    generationConfig = mainConfig,
                    systemInstruction = content { text(systemInstructionText) }
                )
                val response = withTimeout(AI_TIMEOUT_MS) {
                    mainModel.generateContent(promptContent)
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
                Log.w(TAG, "Main model timed out on attempt $attempt", e)
            } catch (e: Exception) {
                Log.w(TAG, "Main model failed on attempt $attempt: ${e.message}", e)
            }
            if (attempt == 1) {
                delay(500L)
            }
        }

        // Fallback to gemini-3.8-flash (FAST_MODEL) with retry
        val fastConfig: GenerationConfig = generationConfig {
            if (responseSchema != null) {
                responseMimeType = "application/json"
                this.responseSchema = responseSchema
            }
        }

        var lastException: Exception? = null
        for (fallbackAttempt in 1..2) {
            try {
                val fastModel = Firebase.ai.generativeModel(
                    modelName = ZedCraftAiConfig.FAST_MODEL,
                    generationConfig = fastConfig,
                    systemInstruction = content { text(systemInstructionText) }
                )
                val fallbackResp = withTimeout(AI_TIMEOUT_MS) {
                    fastModel.generateContent(promptContent)
                }
                val fallbackText = fallbackResp.text?.trim().orEmpty()
                if (fallbackText.isNotBlank()) {
                    return RawModelResult(
                        text = fallbackText,
                        modelUsed = "ZD AI",
                        fallbackNotice = null
                    )
                }
            } catch (e: CancellationException) {
                if (e !is TimeoutCancellationException) throw e
                lastException = e
                Log.e(TAG, "Fallback model (${ZedCraftAiConfig.FAST_MODEL}) timed out on attempt $fallbackAttempt", e)
            } catch (e: Exception) {
                lastException = e
                Log.e(TAG, "Fallback model (${ZedCraftAiConfig.FAST_MODEL}) failed on attempt $fallbackAttempt: ${e.message}", e)
            }
            if (fallbackAttempt == 1) {
                delay(500L)
            }
        }

        // Direct Google AI Studio Gemini API fallback using BuildConfig.GEMINI_API_KEY
        val directResult = callDirectGoogleAiStudioApi(
            systemInstructionText = systemInstructionText,
            promptText = promptContent.parts.filterIsInstance<com.google.firebase.ai.type.TextPart>().joinToString("\n") { it.text },
            responseMimeType = if (responseSchema != null) "application/json" else null
        )
        if (!directResult.isNullOrBlank()) {
            return RawModelResult(
                text = directResult,
                modelUsed = "ZD AI (Direct Studio)",
                fallbackNotice = null
            )
        }

        Log.e(TAG, "All ZD AI attempts, gemini-3.8-flash fallbacks and direct API failed", lastException)
        throw IllegalStateException(FRIENDLY_ZD_ERROR)
    }

    /**
     * Executes a fast tool request on FAST_MODEL (gemini-3.8-flash) with automatic retry once,
     * and LITE_MODEL fallback if needed. Technical errors stay in Logcat only.
     */
    private suspend fun executeFastModel(
        systemInstructionText: String,
        responseSchema: Schema,
        promptContent: Content
    ): RawModelResult {
        val config = generationConfig {
            responseMimeType = "application/json"
            this.responseSchema = responseSchema
        }

        var lastError: Exception? = null
        for (attempt in 1..2) {
            try {
                val model = Firebase.ai.generativeModel(
                    modelName = ZedCraftAiConfig.FAST_MODEL,
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
                Log.w(TAG, "Fast model timed out on attempt $attempt", e)
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Fast model failed on attempt $attempt: ${e.message}", e)
            }
            if (attempt == 1) {
                delay(500L)
            }
        }

        // Secondary fallback to LITE_MODEL
        try {
            val liteModel = Firebase.ai.generativeModel(
                modelName = ZedCraftAiConfig.LITE_MODEL,
                generationConfig = config,
                systemInstruction = content { text(systemInstructionText) }
            )
            val response = withTimeout(AI_TIMEOUT_MS) {
                liteModel.generateContent(promptContent)
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
            Log.e(TAG, "Lite model fallback timed out", e)
        } catch (e: Exception) {
            Log.e(TAG, "Lite model fallback failed: ${e.message}", e)
        }

        // Direct Google AI Studio Gemini API fallback using BuildConfig.GEMINI_API_KEY
        val directResult = callDirectGoogleAiStudioApi(
            systemInstructionText = systemInstructionText,
            promptText = promptContent.parts.filterIsInstance<com.google.firebase.ai.type.TextPart>().joinToString("\n") { it.text },
            responseMimeType = "application/json"
        )
        if (!directResult.isNullOrBlank()) {
            return RawModelResult(
                text = directResult,
                modelUsed = "ZD AI (Direct Studio)",
                fallbackNotice = null
            )
        }

        Log.e(TAG, "Fast tool AI call failed after retries and direct API", lastError)
        throw IllegalStateException(FRIENDLY_ZD_ERROR)
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
    // 3. TITLE OPTIMIZER (Score out of 100: Clarity, Curiosity, Keywords, Length, Click Appeal + 5 Better Titles)
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
                    description = "5 better titles (plus up to 5 bonus angles), each with its own score out of 100 and a short reason",
                    items = Schema.obj(
                        properties = mapOf(
                            "title" to Schema.string(description = "The improved title (42-64 chars ideal)"),
                            "score" to Schema.integer(description = "Estimated score 0-100"),
                            "angleType" to Schema.string(description = "e.g., Curiosity Gap, Search Winner, Contrarian, Direct Proof, Timeframe"),
                            "reason" to Schema.string(description = "Short reason why this title earns higher CTR honestly")
                        )
                    )
                )
            )
        )
    }

    suspend fun scoreAndOptimizeTitle(
        rawTitle: String,
        videoContextOrNiche: String = ""
    ): Result<TitleOptimizationResult> = runCatching {
        val cleanTitle = rawTitle.trim()
        require(cleanTitle.isNotBlank()) { "Please enter a video title to score and optimize." }

        val prompt = buildString {
            appendLine("Score this YouTube video title out of 100 across Clarity, Curiosity, Keywords, Length, and Click Appeal, then generate 5 better titles:")
            appendLine("Title: \"$cleanTitle\" (${cleanTitle.length} characters)")
            if (videoContextOrNiche.isNotBlank()) {
                appendLine("Video Topic / Niche Context: $videoContextOrNiche")
            }
            appendLine("Generate at least 5 better titles (each with its own score out of 100 and a short, specific reason). Keep titles honest, high-curiosity, and punchy on mobile feeds (<64 chars preferred).")
        }

        val raw = executeFastModel(
            systemInstructionText = ZedCraftAiConfig.ZD_SYSTEM_INSTRUCTION,
            responseSchema = titleOptimizerSchema,
            promptContent = content { text(prompt) }
        )

        val json = JSONObject(raw.text)
        val breakdown = listOf(
            SubMetricScore("Clarity", json.optInt("clarityScore", 75), json.optString("clarityNote")),
            SubMetricScore("Curiosity", json.optInt("curiosityScore", 75), json.optString("curiosityNote")),
            SubMetricScore("Keywords", json.optInt("keywordPlacementScore", 75), json.optString("keywordPlacementNote")),
            SubMetricScore("Length (${cleanTitle.length} chars)", json.optInt("lengthScore", 75), json.optString("lengthNote")),
            SubMetricScore("Click Appeal", json.optInt("clickAppealScore", 75), json.optString("clickAppealNote"))
        )

        val suggestionsArr = json.optJSONArray("improvedTitles") ?: JSONArray()
        val suggestions = mutableListOf<SuggestedTitleOption>()
        for (i in 0 until suggestionsArr.length()) {
            val item = suggestionsArr.optJSONObject(i) ?: continue
            suggestions.add(
                SuggestedTitleOption(
                    title = item.optString("title"),
                    score = item.optInt("score", 90).coerceIn(0, 100),
                    angleType = item.optString("angleType", "High Appeal"),
                    reason = item.optString("reason")
                )
            )
        }

        val mobilePreview = if (cleanTitle.length > 58) {
            cleanTitle.take(56).trimEnd() + "..."
        } else {
            cleanTitle
        }

        TitleOptimizationResult(
            originalTitle = cleanTitle,
            overallScore = json.optInt("overallScore", 74).coerceIn(0, 100),
            breakdown = breakdown,
            mobilePreviewText = mobilePreview,
            truncatesOnMobile = cleanTitle.length > 58,
            tenImprovedTitles = suggestions.take(10),
            modelUsed = raw.modelUsed,
            fallbackNotice = raw.fallbackNotice
        )
    }

    // -----------------------------------------------------------------------
    // 4. DESCRIPTION & CHAPTERS OPTIMIZER
    // -----------------------------------------------------------------------
    private val descriptionOptimizerSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "overallScore" to Schema.integer(description = "Score of the optimized description 0-100"),
                "firstTwoLinesHook" to Schema.string(description = "Strong first 2 lines (130-160 chars) shown above 'Show more' and in search results"),
                "fullOptimizedDescription" to Schema.string(description = "Complete ready-to-apply YouTube description with hook, natural keyword overview, chapters, CTA, and 3 hashtags"),
                "generatedChapters" to Schema.array(
                    items = Schema.string(),
                    description = "Timestamps starting with 00:00 Intro"
                ),
                "recommendedCtaBlock" to Schema.string(description = "Next-video watch link & subscribe CTA block"),
                "threeHashtags" to Schema.array(
                    items = Schema.string(),
                    description = "Exactly 3 relevant #hashtags"
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
        transcriptOrOutline: String
    ): Result<DescriptionOptimizationResult> = runCatching {
        require(videoTitleOrTopic.isNotBlank() || existingDescription.isNotBlank()) {
            "Enter a video title/topic or paste an existing description."
        }
        val prompt = buildString {
            appendLine("Optimize this YouTube video description for Search discovery, viewer conversion, and Google Key Moments:")
            appendLine("Video Title / Topic: $videoTitleOrTopic")
            if (existingDescription.isNotBlank()) {
                appendLine("Existing Description:\n$existingDescription")
            }
            if (transcriptOrOutline.isNotBlank()) {
                appendLine("Transcript / Captions / Video Notes (use this to generate accurate 00:00 chapters):\n$transcriptOrOutline")
            } else {
                appendLine("Generate a logical 00:00 chapter structure with timestamps that the creator can customize.")
            }
            appendLine("Rules: Strong hook in the first 2 lines before 'Show more', natural keywords (never spammy comma lists), clear chapters starting at 00:00, clean CTA placement, and the 3 best hashtags at the bottom.")
        }

        val raw = executeFastModel(
            systemInstructionText = ZedCraftAiConfig.ZD_SYSTEM_INSTRUCTION,
            responseSchema = descriptionOptimizerSchema,
            promptContent = content { text(prompt) }
        )

        val json = JSONObject(raw.text)
        DescriptionOptimizationResult(
            overallScore = json.optInt("overallScore", 92).coerceIn(0, 100),
            firstTwoLinesHook = json.optString("firstTwoLinesHook"),
            fullOptimizedDescription = json.optString("fullOptimizedDescription"),
            generatedChapters = jsonArrayToList(json.optJSONArray("generatedChapters")),
            recommendedCtaBlock = json.optString("recommendedCtaBlock"),
            threeHashtags = jsonArrayToList(json.optJSONArray("threeHashtags")).take(3),
            improvementsSummary = jsonArrayToList(json.optJSONArray("improvementsSummary")),
            modelUsed = raw.modelUsed,
            fallbackNotice = raw.fallbackNotice
        )
    }

    // -----------------------------------------------------------------------
    // 5. TAGS & HASHTAGS GENERATOR
    // -----------------------------------------------------------------------
    private val tagsGeneratorSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "recommendedTags" to Schema.array(
                    items = Schema.string(),
                    description = "15 to 22 high-relevance YouTube tags (exact topic, long-tail search phrases, and common variations)"
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
        realRankingTags: List<String>
    ): Result<TagsGenerationResult> = runCatching {
        val cleanTopic = topicOrTitle.trim()
        require(cleanTopic.isNotBlank()) { "Enter a video topic or title to generate tags." }

        val prompt = buildString {
            appendLine("Generate an optimal YouTube tag set (within YouTube's 500-character total limit) and the 3 best description hashtags for:")
            appendLine("Topic / Title: \"$cleanTopic\"")
            if (realRankingTags.isNotEmpty()) {
                appendLine("Real tags extracted from top-ranking YouTube videos for this topic: ${realRankingTags.joinToString(", ")}")
                appendLine("Prioritize the highest-intent real tags above and supplement with smart long-tail variations and common spelling variations.")
            }
        }

        val raw = executeFastModel(
            systemInstructionText = ZedCraftAiConfig.ZD_SYSTEM_INSTRUCTION,
            responseSchema = tagsGeneratorSchema,
            promptContent = content { text(prompt) }
        )

        val json = JSONObject(raw.text)
        val rawCandidateTags = (realRankingTags.take(8) + jsonArrayToList(json.optJSONArray("recommendedTags")))
            .map { it.trim().removePrefix("#") }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }

        // Enforce YouTube's strict 500-character tag box limit (including commas)
        val fittedTags = mutableListOf<String>()
        var charTotal = 0
        for (tag in rawCandidateTags) {
            val addition = if (fittedTags.isEmpty()) tag.length else tag.length + 2
            if (charTotal + addition <= 480) {
                fittedTags.add(tag)
                charTotal += addition
            }
        }

        val hashtags = jsonArrayToList(json.optJSONArray("threeBestHashtags"))
            .map { if (it.startsWith("#")) it else "#${it.replace(" ", "")}" }
            .take(3)
            .ifEmpty { listOf("#YouTubeGrowth", "#CreatorTips", "#ZEDCraft") }

        TagsGenerationResult(
            topic = cleanTopic,
            recommendedTags = fittedTags,
            totalCharacterCount = charTotal,
            threeBestHashtags = hashtags,
            competitorTagsIncluded = realRankingTags.size,
            strategyExplanation = json.optString("strategyExplanation"),
            modelUsed = raw.modelUsed
        )
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
        channelContext: String
    ): Result<TitleOptimizerResponse> = runCatching {
        val titleResult = scoreAndOptimizeTitle(
            rawTitle = topicOrDraftTitle,
            videoContextOrNiche = "$niche | $channelContext"
        ).getOrThrow()

        val descResult = runCatching {
            optimizeDescriptionAndChapters(
                videoTitleOrTopic = topicOrDraftTitle,
                existingDescription = currentDescription,
                transcriptOrOutline = ""
            ).getOrThrow()
        }.getOrNull()

        val tagsResult = runCatching {
            generateTagsAndHashtags(
                topicOrTitle = topicOrDraftTitle,
                realRankingTags = currentTags
            ).getOrThrow()
        }.getOrNull()

        val diagnosisText = titleResult.breakdown.joinToString(" • ") { m ->
            "${m.label}: ${m.score}/100 (${m.note})"
        }

        val mappedSuggestions = titleResult.tenImprovedTitles.map { s ->
            val subBase = (s.score / 5).coerceIn(14, 20)
            TitleSuggestionItem(
                title = s.title,
                framework = s.angleType,
                score = s.score,
                clarityScore = subBase,
                curiosityScore = subBase,
                keywordScore = (subBase - 1).coerceAtLeast(14),
                lengthScore = if (s.title.length in 38..62) 20 else 16,
                clickAppealScore = subBase,
                characterCount = s.title.length,
                whyItWorks = s.reason
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
            hashtags = descResult?.threeHashtags ?: tagsResult?.threeBestHashtags ?: listOf("#YouTubeGrowth", "#CreatorTips", "#ZEDCraft")
        )
    }

    suspend fun scoreSingleCustomTitle(
        customTitle: String,
        niche: String,
        channelContext: String
    ): Result<TitleSuggestionItem> = runCatching {
        val res = scoreAndOptimizeTitle(
            rawTitle = customTitle,
            videoContextOrNiche = "$niche | $channelContext"
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
            score = res.overallScore,
            clarityScore = (clarity / 5).coerceIn(1, 20),
            curiosityScore = (curiosity / 5).coerceIn(1, 20),
            keywordScore = (keywords / 5).coerceIn(1, 20),
            lengthScore = (length / 5).coerceIn(1, 20),
            clickAppealScore = (clickAppeal / 5).coerceIn(1, 20),
            characterCount = customTitle.length,
            whyItWorks = summaryWhy
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
    ): Result<String> = runCatching {
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
    ): Result<String> = runCatching {
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
    ): Result<String> = runCatching {
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

    suspend fun askZdAssistant(
        userPrompt: String,
        channelContext: String,
        chatHistory: List<Pair<String, String>>
    ): Result<String> = runCatching {
        val systemText = buildString {
            appendLine(ZedCraftAiConfig.ZD_SYSTEM_INSTRUCTION)
            appendLine()
            appendLine("=== CONNECTED CHANNEL CONTEXT ===")
            appendLine(channelContext)
        }
        val conversationText = buildString {
            if (chatHistory.isNotEmpty()) {
                appendLine("Recent Conversation History:")
                chatHistory.takeLast(6).forEach { (role, msg) ->
                    appendLine("${role.uppercase()}: ${msg.take(600)}")
                }
                appendLine()
            }
            appendLine("User Request: $userPrompt")
        }

        val raw = executeMainModelWithFallback(
            systemInstructionText = systemText,
            responseSchema = null,
            promptContent = content { text(conversationText) }
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
    }
}
