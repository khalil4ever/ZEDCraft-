package com.example.data.remote

data class YtChannelDetail(
    val channelId: String,
    val title: String,
    val handle: String,
    val description: String,
    val thumbnailUrl: String,
    val bannerUrl: String = "",
    val subscriberCount: Long,
    val viewCount: Long,
    val videoCount: Long,
    val uploadsPlaylistId: String = "",
    val publishedAt: String = "",
    val country: String = "",
    val keywords: List<String> = emptyList(),
    val madeForKids: Boolean = false,
    val isOAuthMine: Boolean = false
)

data class YtVideoDetail(
    val videoId: String,
    val channelId: String,
    val channelTitle: String,
    val title: String,
    val description: String,
    val thumbnailUrl: String,
    val highResThumbnailUrl: String,
    val publishedAt: String,
    val durationIso: String,
    val durationSeconds: Int,
    val isShort: Boolean,
    val viewCount: Long,
    val likeCount: Long,
    val commentCount: Long,
    val tags: List<String>,
    val hashtags: List<String>,
    val categoryId: String = "22",
    val defaultLanguage: String = "",
    val defaultAudioLanguage: String = "",
    val madeForKids: Boolean = false,
    val hasChapters: Boolean = false,
    val chapterCount: Int = 0,
    val channelSubscribers: Long = 0L
) {
    val engagementRatePercent: Double
        get() = if (viewCount > 0) {
            ((likeCount + commentCount).toDouble() / viewCount.toDouble()) * 100.0
        } else 0.0

    val outlierMultiplier: Double
        get() = if (channelSubscribers > 0) {
            viewCount.toDouble() / channelSubscribers.toDouble()
        } else 1.0

    val quickSeoScore: Int
        get() {
            val titlePts = when (title.length) {
                in 42..65 -> 30
                in 30..72 -> 24
                else -> 15
            }
            val descPts = when {
                description.length >= 300 -> 25
                description.length >= 120 -> 18
                description.isNotBlank() -> 10
                else -> 4
            }
            val tagsPts = when {
                tags.size >= 8 -> 25
                tags.size >= 3 -> 16
                else -> 6
            }
            val chapterAndHashPts = (if (hasChapters || isShort) 10 else 4) +
                (if (hashtags.size in 1..6) 10 else 4)
            return (titlePts + descPts + tagsPts + chapterAndHashPts).coerceIn(10, 99)
        }
}

data class YouTubeWriteOutcome(
    val videoId: String,
    val updatedField: String,
    val updatedTitle: String,
    val updatedDescription: String,
    val updatedTags: List<String>,
    val youtubeWatchUrl: String = "https://www.youtube.com/watch?v=$videoId",
    val hasBackupToRestore: Boolean = true
) {
    val updatedFieldLabel: String
        get() = updatedField
}

data class YtCommentItem(
    val commentId: String,
    val authorName: String,
    val authorAvatarUrl: String,
    val textDisplay: String,
    val likeCount: Long,
    val publishedAt: String
)

data class YtAnalyticsSummary(
    val last28DaysViews: Long,
    val last28DaysWatchTimeMinutes: Long,
    val avgViewDurationSeconds: Int,
    val avgViewPercentage: Double,
    val subscribersGained: Long,
    val subscribersLost: Long,
    val topTrafficSources: List<Pair<String, Long>> = emptyList()
)

data class TrafficSourceSlice(
    val sourceName: String,
    val views: Long,
    val percentage: Double
)

data class DailyMetricPoint(
    val dateLabel: String,
    val views: Long,
    val watchMinutes: Long
)

data class YtAnalyticsOverview(
    val channelId: String,
    val daysRange: Int = 28,
    val totalViews: Long,
    val watchTimeMinutes: Long,
    val averageViewDurationSeconds: Int,
    val averageViewPercentage: Double,
    val impressions: Long?,
    val impressionsCtrPercent: Double?,
    val subscribersGained: Long,
    val subscribersLost: Long,
    val shortsViews: Long,
    val longFormViews: Long,
    val shortsPerformanceSummary: String,
    val topTrafficSources: List<TrafficSourceSlice>,
    val dailySeries: List<DailyMetricPoint>,
    val retentionMilestones: List<Pair<String, Double>>,
    val bestUploadDays: List<String>,
    val bestUploadHoursUtc: List<String>,
    val isRealYouTubeAnalyticsApi: Boolean,
    val dataSourceNote: String
)

data class KeywordResearchResult(
    val keyword: String,
    val opportunityScore: Int,
    val demandScore: Int,
    val competitionScore: Int,
    val avgViewsTopResults: Long,
    val medianViewsTopResults: Long,
    val maxViewsTopResults: Long,
    val avgChannelSubscribers: Long,
    val smallChannelOutliersCount: Int,
    val topVideos: List<YtVideoDetail>,
    val extractedTagsFromWinners: List<String>,
    val relatedKeywords: List<String>,
    val risingTopics: List<String>,
    val methodologyNote: String = "Computed by ZEDCraft from real public YouTube search & video statistics."
) {
    val seedKeyword: String get() = keyword
    val demandSignalScore: Int get() = demandScore
    val competitionDifficultyScore: Int get() = competitionScore
    val avgTopViews: Long get() = avgViewsTopResults
    val medianTopViews: Long get() = medianViewsTopResults
    val autocompleteTerms: List<String> get() = relatedKeywords
    val topRankingVideos: List<YtVideoDetail> get() = topVideos
    val verdict: String
        get() = when {
            opportunityScore >= 75 -> "High Opportunity • Strong demand with manageable competition"
            opportunityScore >= 55 -> "Moderate Opportunity • Differentiate with a sharper curiosity hook"
            else -> "Competitive Keyword • Target long-tail variations below"
        }
}

data class AuditCheckItem(
    val category: String,
    val passed: Boolean,
    val score: Int,
    val currentStatus: String,
    val actionableFix: String,
    val priority: String
) {
    val label: String get() = category
    val status: String get() = if (passed) "PASS" else if (score >= 60) "WARN" else "FIX"
    val detail: String get() = "$currentStatus — $actionableFix"
}

data class VideoSeoAuditResult(
    val video: YtVideoDetail,
    val zedCraftScore: Int,
    val titleScore: Int,
    val descriptionScore: Int,
    val tagsScore: Int,
    val hashtagsScore: Int,
    val chaptersScore: Int,
    val engagementScore: Int,
    val packagingSynergyNote: String,
    val checks: List<AuditCheckItem>,
    val prioritizedTodoList: List<String>
) {
    val overallSeoScore: Int get() = zedCraftScore
}

typealias VideoDeepAudit = VideoSeoAuditResult

data class CompetitorComparison(
    val primaryChannel: YtChannelDetail,
    val competitorChannel: YtChannelDetail,
    val primaryAvgViews: Long,
    val competitorAvgViews: Long,
    val primaryEngagementRate: Double,
    val competitorEngagementRate: Double,
    val primaryShortsPct: Int,
    val competitorShortsPct: Int,
    val keyTakeaways: List<String>
)

data class PolicyRiskCheck(
    val id: String,
    val title: String,
    val status: String,
    val observation: String,
    val howToFix: String,
    val policyReference: String
) {
    val area: String get() = title
    val guidance: String get() = "$observation $howToFix"
}

data class ChannelHealthReport(
    val channel: YtChannelDetail,
    val overallHealthScore: Int,
    val uploadConsistencyScore: Int,
    val ctrAndPackagingScore: Int,
    val retentionAndDurationScore: Int,
    val engagementLoyaltyScore: Int,
    val contentMixScore: Int,
    val uploadsPerMonth: Double,
    val shortsRatioPercent: Int,
    val avgEngagementPercent: Double,
    val bestUploadSchedule: String,
    val topPerformingVideos: List<YtVideoDetail>,
    val underperformingVideos: List<Pair<YtVideoDetail, String>>,
    val policyChecks: List<PolicyRiskCheck>,
    val dropDiagnosisSummary: String
)

data class CompetitorTagFrequency(
    val tag: String,
    val usedInTopCount: Int,
    val totalVideosAnalyzed: Int,
    val isFromLiveSearch: Boolean = true
) {
    val commonalityLabel: String
        get() = if (isFromLiveSearch && usedInTopCount > 0 && totalVideosAnalyzed > 0) {
            "Used in $usedInTopCount/$totalVideosAnalyzed top videos"
        } else {
            "Related keyword"
        }
}

data class TopicSearchPatterns(
    val query: String,
    val isLiveYouTubeData: Boolean,
    val dataSourceLabel: String,
    val quotaExhaustedOrOfflineNote: String? = null,
    val topVideos: List<YtVideoDetail> = emptyList(),
    val avgViews: Long = 0L,
    val medianViews: Long = 0L,
    val maxViews: Long = 0L,
    val avgChannelSubscribers: Long = 0L,
    val avgTitleLength: Int = 54,
    val numberUsageCount: Int = 0,
    val totalVideosAnalyzed: Int = 0,
    val commonKeywords: List<String> = emptyList(),
    val repeatingPhrases: List<String> = emptyList(),
    val strongOpeningWords: List<String> = emptyList(),
    val smallChannelOutlierGaps: List<String> = emptyList(),
    val tagFrequencies: List<CompetitorTagFrequency> = emptyList(),
    val topHashtags: List<String> = emptyList(),
    val patternSummaryBullets: List<String> = emptyList()
)

