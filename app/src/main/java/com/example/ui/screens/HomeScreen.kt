package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.remote.YouTubeApiClient
import com.example.data.remote.YtVideoDetail
import com.example.ui.components.AnimatedScoreRing
import com.example.ui.components.DataBadge
import com.example.ui.components.ErrorBanner
import com.example.ui.components.GlassCard
import com.example.ui.components.LoadingBanner
import com.example.ui.components.SectionHeader
import com.example.ui.components.SubScoreBar
import com.example.ui.theme.ZedCyan
import com.example.ui.theme.ZedSuccess
import com.example.ui.theme.ZedViolet
import com.example.ui.theme.ZedWarning
import com.example.ui.viewmodel.DetailDestination
import com.example.ui.viewmodel.PrimaryTab
import com.example.ui.viewmodel.ZedCraftUiState
import com.example.ui.viewmodel.ZedCraftViewModel
import java.util.Locale

@Composable
fun HomeScreen(
    uiState: ZedCraftUiState,
    viewModel: ZedCraftViewModel,
    onRequestYouTubeOAuth: () -> Unit
) {
    var channelLookupInput by remember { mutableStateOf("") }
    var quickVideoLinkInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Setup Notice Banner (Only shown when YOUTUBE_API_KEY is missing)
        if (!uiState.isYouTubeApiKeyConfigured && uiState.oauthAccessToken.isNullOrBlank()) {
            item {
                GlassCard(
                    accentBorderColor = ZedWarning.copy(alpha = 0.75f),
                    onClick = { viewModel.openDetail(DetailDestination.SetupGuide) }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = ZedWarning,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "YouTube Data API v3 Key Setup",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "AI tools (Gemini 4 Argon & 3.8 Flash) are active! Add YOUTUBE_API_KEY in AI Studio Secrets or authorize Google OAuth for live YouTube stats.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.openDetail(DetailDestination.SetupGuide) },
                            modifier = Modifier.weight(1f).testTag("open_setup_guide_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("View Setup Steps")
                        }
                        OutlinedButton(
                            onClick = onRequestYouTubeOAuth,
                            modifier = Modifier.weight(1f).testTag("authorize_yt_oauth_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Authorize OAuth")
                        }
                    }
                }
            }
        }

        // Multi-Channel Switcher Row (When user has connected channels)
        if (uiState.connectedChannels.isNotEmpty()) {
            item {
                Column {
                    Text(
                        text = "CONNECTED CHANNELS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.connectedChannels.forEach { ch ->
                            val isSelected = uiState.activeChannel?.channelId == ch.channelId
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.clickable { viewModel.switchActiveChannel(ch) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = ch.thumbnailUrl,
                                        contentDescription = ch.title,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = ch.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (uiState.isChannelLoading) {
            item {
                LoadingBanner("Loading real YouTube channel data, uploads & health metrics...")
            }
        }

        if (uiState.channelErrorMessage != null) {
            item {
                ErrorBanner(message = uiState.channelErrorMessage)
            }
        }

        // Connected Channel Dashboard OR Connect Channel Card
        val channel = uiState.activeChannel
        val health = uiState.channelHealthReport
        val analytics = uiState.analyticsOverview

        if (channel == null) {
            item {
                GlassCard(accentBorderColor = ZedCyan.copy(alpha = 0.55f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_zedcraft_logo),
                            contentDescription = "ZEDCraft Logo",
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Connect Your YouTube Channel",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Authorize via Google OAuth 2.0 (read-only) or enter any @handle / Channel ID.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onRequestYouTubeOAuth,
                        modifier = Modifier.fillMaxWidth().testTag("home_oauth_connect_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connect My Channel (Google OAuth Read-Only)")
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = channelLookupInput,
                            onValueChange = { channelLookupInput = it },
                            placeholder = { Text("@handle, UC... or video link") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("home_channel_input"),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Button(
                            onClick = { viewModel.connectChannelByQuery(channelLookupInput) },
                            modifier = Modifier.height(54.dp).testTag("home_channel_load_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Load")
                        }
                    }
                }
            }
        } else {
            // Active Channel Hero & Health Score Card
            item {
                GlassCard(accentBorderColor = ZedCyan.copy(alpha = 0.6f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = channel.thumbnailUrl,
                            contentDescription = channel.title,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = channel.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "${channel.handle} • ${if (channel.isOAuthMine) "OAuth Connected" else "Public Channel API"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = ZedCyan
                            )
                        }
                        IconButton(onClick = { viewModel.connectChannelByQuery(channel.channelId) }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Channel",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Top KPI Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KpiMetricPill(
                            label = "Subscribers",
                            value = YouTubeApiClient.formatCompact(channel.subscriberCount),
                            modifier = Modifier.weight(1f)
                        )
                        KpiMetricPill(
                            label = "Total Views",
                            value = YouTubeApiClient.formatCompact(channel.viewCount),
                            modifier = Modifier.weight(1f)
                        )
                        KpiMetricPill(
                            label = "Uploads",
                            value = YouTubeApiClient.formatCompact(channel.videoCount),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (health != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AnimatedScoreRing(
                                score = health.overallHealthScore,
                                label = "Channel Health",
                                size = 90.dp
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                SubScoreBar("Upload Consistency", health.uploadConsistencyScore)
                                SubScoreBar("CTR & View Momentum", health.ctrAndPackagingScore)
                                SubScoreBar("Retention & Engagement", health.engagementLoyaltyScore)
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { viewModel.openDetail(DetailDestination.ChannelHealthCheck) },
                            modifier = Modifier.fillMaxWidth().testTag("open_health_check_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.HealthAndSafety, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Full Channel Health & Policy Risk Check")
                        }
                    }
                }
            }

            // Analytics Overview Card
            if (analytics != null) {
                item {
                    GlassCard {
                        SectionHeader(
                            title = "Channel Analytics & Growth Trends",
                            subtitle = analytics.dataSourceNote,
                            badgeText = if (analytics.isRealYouTubeAnalyticsApi) "Analytics API v2" else "Public Telemetry",
                            isAiBadge = false
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            KpiMetricPill(
                                label = "Recent Views",
                                value = YouTubeApiClient.formatCompact(analytics.totalViews),
                                modifier = Modifier.weight(1f)
                            )
                            KpiMetricPill(
                                label = "Watch Mins",
                                value = YouTubeApiClient.formatCompact(analytics.watchTimeMinutes),
                                modifier = Modifier.weight(1f)
                            )
                            KpiMetricPill(
                                label = "Avg Duration",
                                value = YouTubeApiClient.formatDuration(analytics.averageViewDurationSeconds),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Shorts vs Long-Form Performance
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "SHORTS VS LONG-FORM PERFORMANCE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ZedCyan
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = analytics.shortsPerformanceSummary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Best Upload Days & Times from real channel history
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = ZedSuccess,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Best Upload Window: ${analytics.bestUploadDays.joinToString(", ")} • ${analytics.bestUploadHoursUtc.firstOrNull() ?: "15:00 UTC"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (analytics.topTrafficSources.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "TRAFFIC SOURCES BREAKDOWN",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            analytics.topTrafficSources.forEach { slice ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = slice.sourceName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%.1f%% (%s)", slice.percentage, YouTubeApiClient.formatCompact(slice.views)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ZedCyan
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { (slice.percentage / 100.0).toFloat().coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = ZedCyan,
                                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick Video Link Analyzer & One-Tap SEO Audit Bar
        item {
            GlassCard(accentBorderColor = ZedViolet.copy(alpha = 0.55f)) {
                SectionHeader(
                    title = "Video Analyzer & One-Tap SEO Audit",
                    subtitle = "Paste any public YouTube video link or ID for full stats, tags, and ZEDCraft Score.",
                    badgeText = "Data API v3",
                    isAiBadge = false
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = quickVideoLinkInput,
                        onValueChange = { quickVideoLinkInput = it },
                        placeholder = { Text("Paste youtube.com/watch?v=... or youtu.be/...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("quick_video_audit_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Button(
                        onClick = {
                            if (quickVideoLinkInput.isNotBlank()) {
                                viewModel.openDetail(DetailDestination.VideoAnalyzerAndAudit(quickVideoLinkInput))
                            }
                        },
                        modifier = Modifier.height(54.dp).testTag("quick_video_audit_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ZedViolet)
                    ) {
                        Text("Audit")
                    }
                }
            }
        }

        // Quick Tools Grid (All 100% Free)
        item {
            SectionHeader(
                title = "Creator Growth Suite",
                subtitle = "Every tool is 100% unlocked—no paywalls, no Pro tiers.",
                badgeText = "All Free",
                isAiBadge = false
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickToolCard(
                    title = "Thumbnail Scorer",
                    subtitle = "Vision AI + A/B Test",
                    icon = Icons.Default.Image,
                    accent = ZedCyan,
                    modifier = Modifier.weight(1f),
                    testTag = "quick_tool_thumbnail",
                    onClick = { viewModel.selectOptimizeSubTab(0) }
                )
                QuickToolCard(
                    title = "Title Optimizer",
                    subtitle = "Score + 10 Angles",
                    icon = Icons.Default.Title,
                    accent = ZedViolet,
                    modifier = Modifier.weight(1f),
                    testTag = "quick_tool_title",
                    onClick = { viewModel.selectOptimizeSubTab(1) }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickToolCard(
                    title = "Keyword Research",
                    subtitle = "Opportunity vs Comp",
                    icon = Icons.Default.Search,
                    accent = ZedSuccess,
                    modifier = Modifier.weight(1f),
                    testTag = "quick_tool_keywords",
                    onClick = { viewModel.selectResearchSubTab(0) }
                )
                QuickToolCard(
                    title = "Tags & Hashtags",
                    subtitle = "Real Winner Tags",
                    icon = Icons.Default.Tag,
                    accent = ZedWarning,
                    modifier = Modifier.weight(1f),
                    testTag = "quick_tool_tags",
                    onClick = { viewModel.selectOptimizeSubTab(3) }
                )
            }
        }

        // Connected Channel Videos Ranked by Performance
        if (uiState.activeChannelVideos.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Recent & Top-Performing Uploads",
                    subtitle = "Tap any video to run a full SEO Audit or load its thumbnail into the Vision Scorer.",
                    badgeText = "${uiState.activeChannelVideos.size} Videos",
                    isAiBadge = false
                )
            }
            items(uiState.activeChannelVideos.take(8), key = { it.videoId }) { video ->
                ChannelVideoRowCard(
                    video = video,
                    onAuditClick = {
                        viewModel.openDetail(DetailDestination.VideoAnalyzerAndAudit(video.videoId))
                    },
                    onScoreThumbnailClick = {
                        viewModel.loadThumbnailFromVideoUrlOrDetail(video.videoId, video.title)
                        viewModel.selectOptimizeSubTab(0)
                    }
                )
            }
        }
    }
}

@Composable
private fun KpiMetricPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuickToolCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    testTag: String,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = modifier.testTag(testTag),
        accentBorderColor = accent.copy(alpha = 0.45f),
        onClick = onClick
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ChannelVideoRowCard(
    video: YtVideoDetail,
    onAuditClick: () -> Unit,
    onScoreThumbnailClick: () -> Unit
) {
    GlassCard(onClick = onAuditClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                modifier = Modifier
                    .width(112.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${YouTubeApiClient.formatCompact(video.viewCount)} views",
                        style = MaterialTheme.typography.labelSmall,
                        color = ZedCyan
                    )
                    Text(
                        text = "• ${String.format(Locale.US, "%.1f%% eng", video.engagementRatePercent)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (video.isShort) {
                        DataBadge(text = "Short", isAi = false)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onAuditClick,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 6.dp, horizontal = 10.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("SEO Audit", style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(
                onClick = onScoreThumbnailClick,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 6.dp, horizontal = 10.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Score Thumb", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
