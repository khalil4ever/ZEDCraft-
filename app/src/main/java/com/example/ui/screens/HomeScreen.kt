package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleFilled
import com.example.ui.theme.ZedTextPrimary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.remote.ChannelHealthReport
import com.example.data.remote.YouTubeApiClient
import com.example.data.remote.YtAnalyticsSummary
import com.example.data.remote.YtChannelDetail
import com.example.data.remote.YtVideoDetail
import com.example.ui.theme.ZedAmberWarning
import com.example.ui.theme.ZedCardBorder
import com.example.ui.theme.ZedCoralError
import com.example.ui.theme.ZedCyanPrimary
import com.example.ui.theme.ZedDarkBackground
import com.example.ui.theme.ZedDarkSurfaceElevated
import com.example.ui.theme.ZedEmeraldSuccess
import com.example.ui.theme.ZedGlassSurface
import com.example.ui.theme.ZedPurpleAccent
import com.example.ui.theme.ZedTextMuted
import com.example.ui.theme.ZedTextSecondary
import com.example.ui.viewmodel.DetailDestination
import com.example.ui.viewmodel.OptimizeSubTab
import com.example.ui.viewmodel.PrimaryTab
import com.example.ui.viewmodel.ZedCraftUiState
import com.example.ui.viewmodel.ZedCraftViewModel
import java.util.Locale

@Composable
fun HomeScreen(
    uiState: ZedCraftUiState,
    viewModel: ZedCraftViewModel
) {
    var quickVideoUrl by remember { mutableStateOf("") }
    var quickChannelHandle by remember { mutableStateOf("") }

    val launchOAuth = rememberYouTubeOAuthLauncher(
        onAuthorized = { token -> viewModel.connectWithOAuthToken(token) },
        onError = { err -> viewModel.setOAuthError(err) }
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ZedDarkBackground),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 14.dp,
            bottom = 36.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Active Channel Header OR Connect Channel Card
        item {
            if (uiState.activeChannel != null) {
                ConnectedChannelBannerCard(
                    channel = uiState.activeChannel,
                    health = uiState.channelHealthReport,
                    isLoading = uiState.isLoadingChannel,
                    onRefresh = { viewModel.refreshActiveChannel() },
                    onSwitchChannel = { viewModel.setShowOnboardingSheet(true) },
                    onOpenHealthAudit = { viewModel.openDetailScreen(DetailDestination.ChannelHealthAudit) }
                )
            } else {
                UnconnectedChannelHeroCard(
                    isLoading = uiState.isLoadingChannel,
                    channelHandleInput = quickChannelHandle,
                    onChannelHandleChange = { quickChannelHandle = it },
                    onConnectPublic = { viewModel.connectChannelByInput(quickChannelHandle) },
                    onConnectOAuth = launchOAuth,
                    errorMessage = uiState.channelError
                )
            }
        }

        // 2. MY VIDEOS (Core Feature: Pick any video to score, optimize & apply directly to YouTube)
        if (uiState.activeChannelVideos.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "My Videos (${uiState.activeChannelVideos.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Pick a video to score, optimize & apply changes live to YouTube",
                            style = MaterialTheme.typography.labelSmall,
                            color = ZedTextSecondary
                        )
                    }
                    OutlinedButton(
                        onClick = { viewModel.refreshActiveChannel() },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.4f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Videos",
                            tint = ZedCyanPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Refresh",
                            style = MaterialTheme.typography.labelSmall,
                            color = ZedCyanPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            items(
                items = uiState.activeChannelVideos.take(12),
                key = { "my_video_${it.videoId}" }
            ) { video ->
                MyUploadedVideoOptimizeCard(
                    video = video,
                    isSelectedTarget = uiState.selectedTargetVideo?.videoId == video.videoId,
                    onOptimizeTitle = {
                        viewModel.selectTargetVideoForOptimize(
                            video = video,
                            subTab = OptimizeSubTab.Title,
                            navigateToOptimize = true,
                            autoRunOptimizer = true
                        )
                    },
                    onOptimizeDescTags = {
                        viewModel.selectTargetVideoForOptimize(
                            video = video,
                            subTab = OptimizeSubTab.Description,
                            navigateToOptimize = true,
                            autoRunOptimizer = true
                        )
                    },
                    onOptimizeThumbnail = {
                        viewModel.selectTargetVideoForOptimize(
                            video = video,
                            subTab = OptimizeSubTab.Thumbnail,
                            navigateToOptimize = true,
                            autoRunOptimizer = false
                        )
                    },
                    onDeepAudit = {
                        viewModel.analyzeVideoDeeply(video.videoId)
                    }
                )
            }
        }

        // 3. Owner YouTube Analytics Summary (when connected via OAuth)
        if (uiState.analyticsSummary != null) {
            item {
                OwnerAnalyticsCard(summary = uiState.analyticsSummary)
            }
        }

        // 4. Quick Video Deep Analyzer Bar
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
                border = BorderStroke(1.dp, ZedCardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = "Video Deep Analyzer",
                            tint = ZedCyanPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Instant Video SEO & Packaging Audit",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = quickVideoUrl,
                            onValueChange = { quickVideoUrl = it },
                            placeholder = {
                                Text(
                                    text = "Paste YouTube Video URL or 11-char ID",
                                    color = ZedTextMuted,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("home_quick_video_audit_input"),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ZedCyanPrimary,
                                unfocusedBorderColor = ZedCardBorder,
                                focusedContainerColor = ZedDarkBackground.copy(alpha = 0.6f),
                                unfocusedContainerColor = ZedDarkBackground.copy(alpha = 0.6f)
                            )
                        )
                        Button(
                            onClick = {
                                if (quickVideoUrl.isNotBlank()) {
                                    viewModel.analyzeVideoDeeply(quickVideoUrl)
                                }
                            },
                            enabled = quickVideoUrl.isNotBlank(),
                            modifier = Modifier
                                .height(52.dp)
                                .testTag("home_quick_video_audit_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ZedCyanPrimary,
                                contentColor = ZedDarkBackground
                            )
                        ) {
                            Text("Audit", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 5. Creator Power Tools Grid
        item {
            Text(
                text = "Creator Growth Suite",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickSuiteToolCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.AutoAwesome,
                        title = "Title & Thumbnail Lab",
                        subtitle = "Score & Apply to YouTube",
                        accent = ZedCyanPrimary,
                        onClick = { viewModel.selectTab(PrimaryTab.Optimize) }
                    )
                    QuickSuiteToolCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Search,
                        title = "Keyword & Trend Radar",
                        subtitle = "Opportunity vs Difficulty",
                        accent = ZedPurpleAccent,
                        onClick = { viewModel.selectTab(PrimaryTab.Research) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickSuiteToolCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.AttachMoney,
                        title = "Revenue & Sponsor Calc",
                        subtitle = "RPM & Brand Deal Rates",
                        accent = ZedEmeraldSuccess,
                        onClick = { viewModel.openDetailScreen(DetailDestination.RevenueAndSponsorCalculator) }
                    )
                    QuickSuiteToolCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Checklist,
                        title = "Launch & YPP Checklist",
                        subtitle = "Pre-publish & 500/1K tiers",
                        accent = ZedAmberWarning,
                        onClick = { viewModel.openDetailScreen(DetailDestination.LaunchChecklist) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickSuiteToolCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.HealthAndSafety,
                        title = "Channel Health Audit",
                        subtitle = "360° Score & Drop Diagnosis",
                        accent = ZedCyanPrimary,
                        onClick = { viewModel.openDetailScreen(DetailDestination.ChannelHealthAudit) }
                    )
                    QuickSuiteToolCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Bookmark,
                        title = "Saved Ideas Vault",
                        subtitle = "Offline Scripts & Hooks",
                        accent = ZedPurpleAccent,
                        onClick = { viewModel.openDetailScreen(DetailDestination.SavedVault) }
                    )
                }
            }
        }

        // 6. Channel Health & Drop Diagnosis Summary (when channel is connected)
        val health = uiState.channelHealthReport
        if (health != null) {
            item {
                ChannelHealthSummaryCard(
                    health = health,
                    onViewFullAudit = { viewModel.openDetailScreen(DetailDestination.ChannelHealthAudit) }
                )
            }

            // Underperforming Videos Diagnosis
            if (health.underperformingVideos.isNotEmpty()) {
                item {
                    Text(
                        text = "Underperforming Uploads (Fix Packaging Now)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ZedAmberWarning
                    )
                }
                items(
                    items = health.underperformingVideos,
                    key = { "under_${it.first.videoId}" }
                ) { (video, diagnosis) ->
                    VideoPerformanceRowCard(
                        video = video,
                        diagnosisBadge = diagnosis,
                        isWarning = true,
                        onClickAudit = { viewModel.analyzeVideoDeeply(video.videoId) },
                        onClickOptimize = {
                            viewModel.selectTargetVideoForOptimize(
                                video = video,
                                subTab = OptimizeSubTab.Title,
                                navigateToOptimize = true,
                                autoRunOptimizer = true
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MyUploadedVideoOptimizeCard(
    video: YtVideoDetail,
    isSelectedTarget: Boolean,
    onOptimizeTitle: () -> Unit,
    onOptimizeDescTags: () -> Unit,
    onOptimizeThumbnail: () -> Unit,
    onDeepAudit: () -> Unit
) {
    val score = video.quickSeoScore
    val scoreColor = when {
        score >= 80 -> ZedEmeraldSuccess
        score >= 60 -> ZedCyanPrimary
        else -> ZedAmberWarning
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOptimizeTitle() }
            .testTag("my_video_card_${video.videoId}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
        border = BorderStroke(
            width = if (isSelectedTarget) 1.5.dp else 1.dp,
            color = if (isSelectedTarget) ZedCyanPrimary else ZedCardBorder
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(124.dp)
                        .height(70.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ZedDarkSurfaceElevated)
                ) {
                    AsyncImage(
                        model = video.thumbnailUrl,
                        contentDescription = video.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp),
                        color = Color.Black.copy(alpha = 0.78f),
                        shape = RoundedCornerShape(5.dp)
                    ) {
                        Text(
                            text = if (video.isShort) "SHORT" else YouTubeApiClient.formatDuration(video.durationSeconds),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                tint = ZedCyanPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${YouTubeApiClient.formatCompact(video.viewCount)} views",
                                style = MaterialTheme.typography.labelSmall,
                                color = ZedCyanPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Surface(
                            color = scoreColor.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, scoreColor.copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = "Score $score/100",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = scoreColor,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Direct Optimize Action Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOptimizeTitle,
                    modifier = Modifier
                        .weight(1.2f)
                        .height(36.dp)
                        .testTag("optimize_title_btn_${video.videoId}"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ZedCyanPrimary,
                        contentColor = ZedDarkBackground
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Title,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Optimize Title",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onOptimizeDescTags,
                    modifier = Modifier
                        .weight(1.1f)
                        .height(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ZedPurpleAccent.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = ZedPurpleAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Desc & Tags",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedButton(
                    onClick = onOptimizeThumbnail,
                    modifier = Modifier
                        .weight(0.95f)
                        .height(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ZedEmeraldSuccess.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = ZedEmeraldSuccess,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Thumb",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                IconButton(
                    onClick = onDeepAudit,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ZedDarkSurfaceElevated)
                ) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = "Deep Audit Video",
                        tint = ZedCyanPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectedChannelBannerCard(
    channel: YtChannelDetail,
    health: ChannelHealthReport?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onSwitchChannel: () -> Unit,
    onOpenHealthAudit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
        border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = channel.thumbnailUrl,
                    contentDescription = channel.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .border(2.dp, ZedCyanPrimary, CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = channel.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (channel.isOAuthMine) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "OAuth Connected",
                                tint = ZedEmeraldSuccess,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = channel.handle,
                        style = MaterialTheme.typography.bodySmall,
                        color = ZedCyanPrimary
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    enabled = !isLoading,
                    modifier = Modifier.testTag("refresh_channel_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = ZedCyanPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Channel Stats",
                            tint = ZedTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // KPI Stat Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KpiMetricBox(
                    modifier = Modifier.weight(1f),
                    label = "Subscribers",
                    value = YouTubeApiClient.formatCompact(channel.subscriberCount),
                    accent = ZedCyanPrimary
                )
                KpiMetricBox(
                    modifier = Modifier.weight(1f),
                    label = "Total Views",
                    value = YouTubeApiClient.formatCompact(channel.viewCount),
                    accent = ZedPurpleAccent
                )
                KpiMetricBox(
                    modifier = Modifier.weight(1f),
                    label = "Videos",
                    value = YouTubeApiClient.formatCompact(channel.videoCount),
                    accent = ZedEmeraldSuccess
                )
                if (health != null) {
                    KpiMetricBox(
                        modifier = Modifier.weight(1f),
                        label = "Health",
                        value = "${health.overallHealthScore}/100",
                        accent = if (health.overallHealthScore >= 75) ZedEmeraldSuccess else ZedAmberWarning
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenHealthAudit,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.5f))
                ) {
                    Text("Full Health Report", color = ZedCyanPrimary, style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onSwitchChannel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ZedCardBorder)
                ) {
                    Text("Switch Channel", color = ZedTextSecondary, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun UnconnectedChannelHeroCard(
    isLoading: Boolean,
    channelHandleInput: String,
    onChannelHandleChange: (String) -> Unit,
    onConnectPublic: () -> Unit,
    onConnectOAuth: () -> Unit,
    errorMessage: String?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
        border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.35f))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                ZedPurpleAccent.copy(alpha = 0.36f),
                                ZedDarkSurfaceElevated,
                                ZedCyanPrimary.copy(alpha = 0.16f)
                            )
                        )
                    )
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        ZedCyanPrimary,
                                        ZedPurpleAccent
                                    )
                                )
                            )
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(18.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "ZEDCraft Studio",
                            tint = ZedDarkBackground,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Connect Your YouTube Channel",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = ZedTextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Load your videos, score titles & thumbnails with ZD, and apply changes directly to YouTube.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ZedTextSecondary
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Button(
                    onClick = onConnectOAuth,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("home_oauth_connect_button"),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ZedCyanPrimary,
                        contentColor = ZedDarkBackground
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircleFilled,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Connect YouTube Channel",
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = channelHandleInput,
                        onValueChange = onChannelHandleChange,
                        placeholder = {
                            Text(
                                text = "Or inspect @handle / Channel ID",
                                color = ZedTextMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_channel_input"),
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ZedCyanPrimary,
                            unfocusedBorderColor = ZedCardBorder,
                            focusedContainerColor = ZedDarkBackground.copy(alpha = 0.6f),
                            unfocusedContainerColor = ZedDarkBackground.copy(alpha = 0.6f)
                        )
                    )
                    OutlinedButton(
                        onClick = onConnectPublic,
                        enabled = !isLoading && channelHandleInput.isNotBlank(),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("home_connect_channel_button"),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.55f))
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = ZedCyanPrimary
                            )
                        } else {
                            Text("Audit", fontWeight = FontWeight.Bold, color = ZedCyanPrimary)
                        }
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = ZedCoralError
                    )
                }
            }
        }
    }
}

@Composable
private fun KpiMetricBox(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    accent: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = ZedDarkSurfaceElevated,
        border = BorderStroke(1.dp, ZedCardBorder)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = accent,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = ZedTextSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun OwnerAnalyticsCard(summary: YtAnalyticsSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
        border = BorderStroke(1.dp, ZedEmeraldSuccess.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = "YouTube Analytics Last 28 Days",
                    tint = ZedEmeraldSuccess,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "YouTube Analytics (Last 28 Days)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = ZedEmeraldSuccess
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KpiMetricBox(
                    modifier = Modifier.weight(1f),
                    label = "28d Views",
                    value = YouTubeApiClient.formatCompact(summary.last28DaysViews),
                    accent = ZedCyanPrimary
                )
                KpiMetricBox(
                    modifier = Modifier.weight(1f),
                    label = "Watch Hrs",
                    value = String.format(Locale.US, "%.1fK", summary.last28DaysWatchTimeMinutes / 60.0 / 1000.0),
                    accent = ZedPurpleAccent
                )
                KpiMetricBox(
                    modifier = Modifier.weight(1f),
                    label = "Avg View %",
                    value = String.format(Locale.US, "%.1f%%", summary.avgViewPercentage),
                    accent = ZedEmeraldSuccess
                )
                KpiMetricBox(
                    modifier = Modifier.weight(1f),
                    label = "Net Subs",
                    value = "+${YouTubeApiClient.formatCompact(summary.subscribersGained - summary.subscribersLost)}",
                    accent = ZedCyanPrimary
                )
            }
            if (summary.topTrafficSources.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Top Traffic Sources:",
                    style = MaterialTheme.typography.labelSmall,
                    color = ZedTextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                summary.topTrafficSources.take(3).forEach { (source, views) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = source, style = MaterialTheme.typography.bodySmall, color = Color.White)
                        Text(
                            text = "${YouTubeApiClient.formatCompact(views)} views",
                            style = MaterialTheme.typography.bodySmall,
                            color = ZedCyanPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelHealthSummaryCard(
    health: ChannelHealthReport,
    onViewFullAudit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
        border = BorderStroke(1.dp, ZedCardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Channel Health & Algorithm Diagnosis",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.1f", health.uploadsPerMonth)} uploads/mo • ${health.shortsRatioPercent}% Shorts • ${String.format(Locale.US, "%.1f%%", health.avgEngagementPercent)} avg engagement",
                        style = MaterialTheme.typography.bodySmall,
                        color = ZedTextSecondary
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ZedCyanPrimary.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, ZedCyanPrimary)
                ) {
                    Text(
                        text = "${health.overallHealthScore}/100",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = ZedCyanPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            HealthProgressRow("Upload Consistency", health.uploadConsistencyScore)
            HealthProgressRow("CTR & Packaging Readiness", health.ctrAndPackagingScore)
            HealthProgressRow("Retention & Chapter Structure", health.retentionAndDurationScore)
            HealthProgressRow("Audience Engagement Velocity", health.engagementLoyaltyScore)

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = ZedDarkSurfaceElevated,
                border = BorderStroke(1.dp, ZedCardBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "Best Upload Schedule",
                            tint = ZedCyanPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Best Upload Windows: ${health.bestUploadSchedule}",
                            style = MaterialTheme.typography.labelMedium,
                            color = ZedCyanPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = health.dropDiagnosisSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = ZedTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun HealthProgressRow(label: String, score: Int) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = ZedTextSecondary)
            Text(
                text = "$score%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (score >= 75) ZedEmeraldSuccess else if (score >= 55) ZedCyanPrimary else ZedAmberWarning
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (score / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = if (score >= 75) ZedEmeraldSuccess else if (score >= 55) ZedCyanPrimary else ZedAmberWarning,
            trackColor = ZedDarkBackground
        )
    }
}

@Composable
private fun QuickSuiteToolCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
        border = BorderStroke(1.dp, ZedCardBorder)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = ZedTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun VideoPerformanceRowCard(
    video: YtVideoDetail,
    diagnosisBadge: String,
    isWarning: Boolean,
    onClickAudit: () -> Unit,
    onClickOptimize: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClickAudit() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
        border = BorderStroke(
            1.dp,
            if (isWarning) ZedAmberWarning.copy(alpha = 0.4f) else ZedCardBorder
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(116.dp)
                        .height(66.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ZedDarkSurfaceElevated)
                ) {
                    AsyncImage(
                        model = video.thumbnailUrl,
                        contentDescription = video.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp),
                        color = Color.Black.copy(alpha = 0.78f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (video.isShort) "SHORT" else YouTubeApiClient.formatDuration(video.durationSeconds),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${YouTubeApiClient.formatCompact(video.viewCount)} views",
                            style = MaterialTheme.typography.labelMedium,
                            color = ZedCyanPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Score ${video.quickSeoScore}/100",
                            style = MaterialTheme.typography.labelSmall,
                            color = ZedEmeraldSuccess
                        )
                    }
                }
                IconButton(onClick = onClickOptimize) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Optimize Video",
                        tint = ZedCyanPrimary
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = if (isWarning) ZedAmberWarning.copy(alpha = 0.12f) else ZedEmeraldSuccess.copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isWarning) Icons.Default.Warning else Icons.Default.PlayCircleFilled,
                        contentDescription = null,
                        tint = if (isWarning) ZedAmberWarning else ZedEmeraldSuccess,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = diagnosisBadge,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isWarning) ZedAmberWarning else ZedEmeraldSuccess
                    )
                }
            }
        }
    }
}
