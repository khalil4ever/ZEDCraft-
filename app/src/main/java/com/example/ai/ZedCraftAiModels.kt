package com.example.ai

/**
 * Structured output models for ZEDCraft's AI tools and ZD Assistant.
 */

data class SubMetricScore(
    val label: String,
    val score: Int, // 0..100
    val note: String
)

data class ThumbnailScoreResult(
    val overallScore: Int, // 0..100
    val breakdown: List<SubMetricScore>,
    val titleSynergyScore: Int,
    val titleSynergyVerdict: String,
    val problemAreas: List<String>,
    val actionableFixes: List<String>,
    val modelUsed: String,
    val fallbackNotice: String? = null
)

data class ThumbnailAbResult(
    val winner: String, // "A" or "B"
    val scoreA: Int,
    val scoreB: Int,
    val headlineVerdict: String,
    val reasonsWinnerWon: List<String>,
    val categoryComparisons: List<String>,
    val howToMakeWinnerEvenBetter: List<String>,
    val titlePairSynergy: String,
    val modelUsed: String,
    val fallbackNotice: String? = null
)

data class SuggestedTitleOption(
    val title: String,
    val score: Int,
    val angleType: String,
    val tagBadge: String = "Balanced", // "Best for Search", "Best for Clicks", or "Balanced"
    val reason: String
)

data class TitleOptimizationResult(
    val originalTitle: String,
    val overallScore: Int, // 0..100
    val breakdown: List<SubMetricScore>,
    val mobilePreviewText: String,
    val truncatesOnMobile: Boolean,
    val tenImprovedTitles: List<SuggestedTitleOption>,
    val modelUsed: String,
    val fallbackNotice: String? = null
)

data class DescriptionVariationOption(
    val variationLabel: String, // e.g., "Search-focused", "Click-focused", "Balanced"
    val score: Int, // 0..100
    val firstTwoLinesPreview: String,
    val fullDescription: String,
    val naturalKeywordsUsed: List<String> = emptyList(),
    val chapters: List<String> = emptyList(),
    val callToAction: String = "",
    val threeHashtags: List<String> = emptyList()
) {
    val label: String get() = variationLabel
    val firstTwoLinesHook: String get() = firstTwoLinesPreview
    val keywordsUsed: List<String> get() = naturalKeywordsUsed
}

data class DescriptionOptimizationResult(
    val overallScore: Int, // 0..100
    val currentDraftScore: Int = 74,
    val currentDraftDiagnosis: String = "",
    val firstTwoLinesHook: String,
    val fullOptimizedDescription: String,
    val generatedChapters: List<String>,
    val recommendedCtaBlock: String,
    val threeHashtags: List<String>,
    val variations: List<DescriptionVariationOption> = emptyList(),
    val improvementsSummary: List<String>,
    val modelUsed: String,
    val fallbackNotice: String? = null
)

data class SmartTagItem(
    val tag: String,
    val usedInTopVideosCount: Int = 0,
    val totalTopVideosAnalyzed: Int = 0,
    val commonalityLabel: String = "Related keyword",
    val isFromLiveYouTubeData: Boolean = false
)

data class TagsGenerationResult(
    val topic: String,
    val currentTagsScore: Int = 76,
    val currentTagsDiagnosis: String = "",
    val recommendedTags: List<String>,
    val smartTagItems: List<SmartTagItem> = emptyList(),
    val totalCharacterCount: Int,
    val threeBestHashtags: List<String>,
    val competitorTagsIncluded: Int,
    val strategyExplanation: String,
    val modelUsed: String
)

data class GeneratedVideoIdea(
    val title: String,
    val thumbnailConcept: String,
    val thumbnailTextOverlay: String,
    val hookFirst15Seconds: String,
    val targetPillar: String,
    val whyItFitsChannel: String,
    val opportunityScore: Int
)

data class VideoIdeasResult(
    val nicheSummary: String,
    val ideas: List<GeneratedVideoIdea>,
    val modelUsed: String,
    val fallbackNotice: String? = null
)

data class ZdToolExecutionCard(
    val toolType: ZdToolType,
    val title: String,
    val score: Int? = null,
    val badgeText: String,
    val bulletPoints: List<String>,
    val copyableText: String? = null
)

data class ZdAssistantResponse(
    val replyMarkdown: String,
    val routedSkill: ZdSkillModule,
    val toolCard: ZdToolExecutionCard? = null,
    val followUpPrompts: List<String>,
    val modelUsed: String,
    val fallbackNotice: String? = null
)

// --- ViewModel & UI Compatibility Models ---

data class TitleSuggestionItem(
    val title: String,
    val framework: String,
    val tagBadge: String = "Balanced", // "Best for Search", "Best for Clicks", or "Balanced"
    val score: Int,
    val clarityScore: Int,
    val curiosityScore: Int,
    val keywordScore: Int,
    val lengthScore: Int,
    val clickAppealScore: Int,
    val characterCount: Int,
    val whyItWorks: String,
    val mobileFeedPreview: String = if (title.length > 60) title.take(58).trimEnd() + "..." else title,
    val searchResultPreview: String = if (title.length > 70) title.take(68).trimEnd() + "..." else title,
    val willCutOffWarning: Boolean = title.length > 60,
    val cutoffWarningText: String? = when {
        title.length > 70 -> "Will cut off in both Mobile Feed (>60 chars) & Search (>70 chars)"
        title.length > 60 -> "May cut off on Mobile Feed (>60 chars) — front-load key hook"
        else -> null
    },
    val isBackedByLiveYouTubeData: Boolean = true
)

data class TitleOptimizerResponse(
    val originalScore: Int,
    val originalDiagnosis: String,
    val suggestedTitles: List<TitleSuggestionItem>,
    val optimizedDescription: String,
    val suggestedChapters: List<String>,
    val recommendedTags: List<String>,
    val hashtags: List<String>,
    val isBackedByLiveYouTubeData: Boolean = false,
    val dataSourceBadge: String = "Backed by Live YouTube Search Data",
    val quotaFallbackNotice: String? = null,
    val searchPatternsSummary: List<String> = emptyList(),
    val topCompetitorReferenceTitles: List<String> = emptyList(),
    val descriptionScore: Int = 82,
    val descriptionDiagnosis: String = "",
    val descriptionVariations: List<DescriptionVariationOption> = emptyList(),
    val tagsAndHashtagsScore: Int = 80,
    val tagsDiagnosis: String = "",
    val smartTagSuggestions: List<SmartTagItem> = emptyList()
)

data class ThumbnailVisionResponse(
    val overallScore: Int,
    val readabilityScore: Int,
    val contrastAndColorScore: Int,
    val emotionalHookScore: Int,
    val titleSynergyScore: Int,
    val mobileSquintTestVerdict: String,
    val timestampZoneWarning: String,
    val actionableImprovements: List<String>
)
