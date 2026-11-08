package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

object WorkType {
    const val THUMBNAIL_SCORE = "THUMBNAIL_SCORE"
    const val TITLE_OPTIMIZER = "TITLE_OPTIMIZER"
    const val DESCRIPTION_OPTIMIZER = "DESCRIPTION_OPTIMIZER"
    const val TAGS_GENERATOR = "TAGS_GENERATOR"
    const val KEYWORD_RESEARCH = "KEYWORD_RESEARCH"
    const val VIDEO_AUDIT = "VIDEO_AUDIT"
    const val CHANNEL_HEALTH = "CHANNEL_HEALTH"
    const val TREND_IDEA = "TREND_IDEA"
    const val AB_COMPARE = "AB_COMPARE"

    fun labelFor(type: String): String = when (type) {
        THUMBNAIL_SCORE -> "Thumbnail Score"
        TITLE_OPTIMIZER -> "Title Optimizer"
        DESCRIPTION_OPTIMIZER -> "Description Optimizer"
        TAGS_GENERATOR -> "Tags & Hashtags"
        KEYWORD_RESEARCH -> "Keyword Research"
        VIDEO_AUDIT -> "SEO Video Audit"
        CHANNEL_HEALTH -> "Channel Health Check"
        TREND_IDEA -> "Trend & Idea Finder"
        AB_COMPARE -> "Thumbnail A/B Test"
        else -> "Optimization"
    }
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val activeChannelId: String = "",
    val activeChannelTitle: String = "",
    val niche: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ConnectedChannel(
    @DocumentId val id: String = "",
    val userId: String = "",
    val channelId: String = "",
    val title: String = "",
    val handle: String = "",
    val thumbnailUrl: String = "",
    val subscriberCount: Long = 0L,
    val viewCount: Long = 0L,
    val videoCount: Long = 0L,
    val healthScore: Int = 0,
    @get:PropertyName("isOAuthConnected")
    @set:PropertyName("isOAuthConnected")
    var isOAuthConnected: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SavedWorkItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val workType: String = WorkType.TITLE_OPTIMIZER,
    val title: String = "",
    val subtitle: String = "",
    val score: Int = 0,
    val payloadJson: String = "{}",
    val channelId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CompetitorChannel(
    @DocumentId val id: String = "",
    val userId: String = "",
    val channelId: String = "",
    val title: String = "",
    val handle: String = "",
    val thumbnailUrl: String = "",
    val subscriberCount: Long = 0L,
    val viewCount: Long = 0L,
    val videoCount: Long = 0L,
    val recentAvgViews: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ZdChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sessionId: String = "default",
    val role: String = "user",
    val content: String = "",
    val skillId: String = "general",
    val toolCardJson: String = "",
    val modelUsed: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
