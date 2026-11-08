package com.example.ai

data class SubMetricScore(
    val label: String,
    val score: Int,
    val note: String
)

data class ThumbnailScoreResult(
    val overallScore: Int,
    val breakdown: List<SubMetricScore>,
    val titleSynergyScore: Int,
    val titleSynergyVerdict: String,
    val problemAreas: List<String>,
    val actionableFixes: List<String>,
    val modelUsed: String,
    val fallbackNotice: String? = null,
    val estimateLabel: String = "AI Estimate (Not an official YouTube metric)"
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
    val reason: String
)

data class TitleSuggestionItem(
    val title: String,
    val framework: String,
    val score: Int,
    val clarityScore: Int,
    val curiosityScore: Int,
    val keywordScore: Int,
    val lengthScore: Int,
    val clickAppealScore: Int,
    val characterCount: Int = title.length,
    val whyItWorks: String
)

data class TitleOptimizerResponse(
    val originalScore: Int,
    val originalDiagnosis: String,
    val suggestedTitles: List<TitleSuggestionItem>,
    val optimizedDescription: String,
    val suggestedChapters: List<String>,
    val recommendedTags: List<String>,
    val hashtags: List<String>
)

data class TitleOptimizationResult(
    val originalTitle: String,
    val overallScore: Int,
    val breakdown: List<SubMetricScore>,
    val mobilePreviewText: String,
    val truncatesOnMobile: Boolean,
    val tenImprovedTitles: List<SuggestedTitleOption>,
    val modelUsed: String,
    val fallbackNotice: String? = null
)

data class DescriptionOptimizationResult(
    val overallScore: Int,
    val firstTwoLinesHook: String,
    val fullOptimizedDescription: String,
    val generatedChapters: List<String>,
    val recommendedCtaBlock: String,
    val threeHashtags: List<String>,
    val improvementsSummary: List<String>,
    val modelUsed: String,
    val fallbackNotice: String? = null
)

data class TagsGenerationResult(
    val topic: String,
    val recommendedTags: List<String>,
    val totalCharacterCount: Int,
    val characterLimitMax: Int = 500,
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
    val score: Int,
    val badgeText: String,
    val bulletPoints: List<String>,
    val copyableText: String = ""
)

data class ZdAssistantResponse(
    val replyMarkdown: String,
    val routedSkill: ZdSkillModule,
    val toolCard: ZdToolExecutionCard? = null,
    val followUpPrompts: List<String>,
    val modelUsed: String,
    val fallbackNotice: String? = null
)
