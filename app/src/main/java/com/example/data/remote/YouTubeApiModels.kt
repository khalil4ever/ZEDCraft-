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
    val categoryId: String = "",
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
}

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
    val impressions: Long?, // Nullable if API does not expose impressions in report
    val impressionsCtrPercent: Double?, // Nullable when not exposed
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
    val opportunityScore: Int, // 0..100 higher is better opportunity
    val demandScore: Int, // 0..100 based on actual views & recent upload velocity
    val competitionScore: Int, // 0..100 higher means harder competition
    val avgViewsTopResults: Long,
    val medianViewsTopResults: Long,
    val maxViewsTopResults: Long,
    val avgChannelSubscribers: Long,
    val smallChannelOutliersCount: Int, // Channels <50K subs ranking with >avg views
    val topVideos: List<YtVideoDetail>,
    val extractedTagsFromWinners: List<String>,
    val relatedKeywords: List<String>,
    val risingTopics: List<String>,
    val methodologyNote: String = "Computed by ZEDCraft from real public YouTube search & video statistics. Not an official YouTube search volume metric."
)

data class AuditCheckItem(
    val category: String,
    val passed: Boolean,
    val score: Int, // 0..100
    val currentStatus: String,
    val actionableFix: String,
    val priority: String // "HIGH", "MEDIUM", "LOW"
)

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
)

data class PolicyRiskCheck(
    val id: String,
    val title: String,
    val status: String, // "SAFE", "REVIEW", "WARNING"
    val observation: String,
    val howToFix: String,
    val policyReference: String
)

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
