package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.remote.ChannelHealthReport
import com.example.data.remote.YtAnalyticsOverview
import com.example.data.remote.YtChannelDetail
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
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "ZedCraftAiService"
private const val AI_TIMEOUT_MS = 60_000L

class ZedCraftAiService {

    private data class RawModelResult(
        val text: String,
        val modelUsed: String,
        val fallbackNotice: String?
    )

    /**
     * Executes a prompt on ZD_MAIN_MODEL (gemini-4-argon) with ThinkingLevel.HIGH and no maxOutputTokens.
     * If ZD_MAIN_MODEL returns a model-not-found, billing, or access error, automatically falls back to FAST_MODEL
     * and surfaces a transparent notice.
     */
    private suspend fun executeMainModelWithFallback(
        systemInstructionText: String,
        responseSchema: Schema? = null,
        promptContent: Content
    ): RawModelResult {
        val mainConfig: GenerationConfig = generationConfig {
            thinkingConfig = thinkingConfig {
                thinkingLevel = ThinkingLevel.HIGH
            }
            if (responseSchema != null) {
                responseMimeType = "application/json"
                this.responseSchema = responseSchema
            }
        }

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
                    modelUsed = "${ZedCraftAiConfig.ZD_MAIN_MODEL} (High Thinking)",
                    fallbackNotice = null
                )
            }
        } catch (e: CancellationException) {
            if (e !is TimeoutCancellationException) throw e
            Log.w(TAG, "Main model timed out, falling back to FAST_MODEL", e)
        } catch (e: Exception) {
            Log.w(TAG, "ZD_MAIN_MODEL (${ZedCraftAiConfig.ZD_MAIN_MODEL}) unavailable, falling back to ${ZedCraftAiConfig.FAST_MODEL}: ${e.message}")
        }

        // Automatic fallback to FAST_MODEL (gemini-3.8-flash)
        val fastConfig: GenerationConfig = generationConfig {
            if (responseSchema != null) {
                responseMimeType = "application/json"
                this.responseSchema = responseSchema
            }
        }
        val fastModel = Firebase.ai.generativeModel(
            modelName = ZedCraftAiConfig.FAST_MODEL,
            generationConfig = fastConfig,
            systemInstruction = content { text(systemInstructionText) }
        )
        val fallbackResp = withTimeout(AI_TIMEOUT_MS) {
            fastModel.generateContent(promptContent)
        }
        val fallbackText = fallbackResp.text?.trim()
            ?: throw IllegalStateException("AI model returned an empty response.")

        return RawModelResult(
            text = fallbackText,
            modelUsed = ZedCraftAiConfig.FAST_MODEL,
            fallbackNotice = "Switched to ${ZedCraftAiConfig.FAST_MODEL} because ${ZedCraftAiConfig.ZD_MAIN_MODEL} was unavailable on this project tier."
        )
    }

    /**
     * Executes a fast tool request directly on FAST_MODEL (gemini-3.8-flash) to save quota and latency.
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
        val model = Firebase.ai.generativeModel(
            modelName = ZedCraftAiConfig.FAST_MODEL,
            generationConfig = config,
            systemInstruction = content { text(systemInstructionText) }
        )
        val response = withTimeout(AI_TIMEOUT_MS) {
            model.generateContent(promptContent)
        }
        val text = response.text?.trim()
            ?: throw IllegalStateException("Model returned an empty response.")
        return RawModelResult(
            text = text,
            modelUsed = ZedCraftAiConfig.FAST_MODEL,
            fallbackNotice = null
        )
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
    // 3. TITLE OPTIMIZER (Score + Breakdown + 10 Non-Clickbait Alternatives)
    // -----------------------------------------------------------------------
    private val titleOptimizerSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "overallScore" to Schema.integer(description = "Overall title score 0-100"),
                "lengthScore" to Schema.integer(description = "Length & mobile truncation score 0-100"),
                "lengthNote" to Schema.string(),
                "keywordPlacementScore" to Schema.integer(description = "Front-loaded keyword placement score 0-100"),
                "keywordPlacementNote" to Schema.string(),
                "curiosityScore" to Schema.integer(description = "Curiosity gap without deceptive clickbait 0-100"),
                "curiosityNote" to Schema.string(),
                "clarityScore" to Schema.integer(description = "Instant clarity of topic/value 0-100"),
                "clarityNote" to Schema.string(),
                "emotionalPullScore" to Schema.integer(description = "Emotional stakes or urgency 0-100"),
                "emotionalPullNote" to Schema.string(),
                "powerWordsScore" to Schema.integer(description = "Specificity, numbers, or contrast words 0-100"),
                "powerWordsNote" to Schema.string(),
                "improvedTitles" to Schema.array(
                    description = "Exactly 10 improved title options across Search, Browse Curiosity, Contrarian, and Proof angles",
                    items = Schema.obj(
                        properties = mapOf(
                            "title" to Schema.string(description = "The improved title (42-64 chars ideal)"),
                            "score" to Schema.integer(description = "Estimated packaging score 0-100"),
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
            appendLine("Score and optimize this YouTube video title:")
            appendLine("Title: \"$cleanTitle\" (${cleanTitle.length} characters)")
            if (videoContextOrNiche.isNotBlank()) {
                appendLine("Video Topic / Niche Context: $videoContextOrNiche")
            }
            appendLine("Generate 10 improved titles. Each must be honest (no deceptive clickbait that breaks viewer trust), punchy on mobile feeds (<64 chars preferred), and include a variety of angles (Curiosity Gap, Search Intent, Specific Number/Timeframe, Contrarian/Mistake, Transformation).")
        }

        val raw = executeFastModel(
            systemInstructionText = ZedCraftAiConfig.ZD_SYSTEM_INSTRUCTION,
            responseSchema = titleOptimizerSchema,
            promptContent = content { text(prompt) }
        )

        val json = JSONObject(raw.text)
        val breakdown = listOf(
            SubMetricScore("Length & Mobile Fit (${cleanTitle.length} chars)", json.optInt("lengthScore", 75), json.optString("lengthNote")),
            SubMetricScore("Keyword Placement", json.optInt("keywordPlacementScore", 75), json.optString("keywordPlacementNote")),
            SubMetricScore("Curiosity Gap", json.optInt("curiosityScore", 75), json.optString("curiosityNote")),
            SubMetricScore("Value Clarity", json.optInt("clarityScore", 75), json.optString("clarityNote")),
            SubMetricScore("Emotional Pull", json.optInt("emotionalPullScore", 75), json.optString("emotionalPullNote")),
            SubMetricScore("Specificity & Power Words", json.optInt("powerWordsScore", 75), json.optString("powerWordsNote"))
        )

        val suggestionsArr = json.optJSONArray("improvedTitles") ?: JSONArray()
        val suggestions = mutableListOf<SuggestedTitleOption>()
        for (i in 0 until suggestionsArr.length()) {
            val item = suggestionsArr.optJSONObject(i) ?: continue
            suggestions.add(
                SuggestedTitleOption(
                    title = item.optString("title"),
                    score = item.optInt("score", 88).coerceIn(0, 100),
                    angleType = item.optString("angleType", "High CTR"),
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
                "fullOptimizedDescription" to Schema.string(description = "Complete ready-to-paste YouTube description with hook, natural keyword overview, chapters, CTA, and 3 hashtags"),
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
                appendLine("Generate a logical 00:00 chapter structure with placeholder timestamps that the creator can adjust.")
            }
            appendLine("Rules: Strong first 2 lines before 'Show more', natural keyword weaving (never spammy comma lists), clear chapters starting at 00:00, clean CTA placement, and the 3 best hashtags at the bottom.")
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
    // 5. TAGS & HASHTAGS GENERATOR (Real Competitor Tags + Gemini)
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
            val addition = if (fittedTags.isEmpty()) tag.length else tag.length + 2 // ", "
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
    // 6. AI TREND & VIDEO IDEA FINDER (Powered by ZD_MAIN_MODEL with High Thinking)
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

    private fun jsonArrayToList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { idx ->
            arr.optString(idx).trim().takeIf { it.isNotBlank() }
        }
    }
}
