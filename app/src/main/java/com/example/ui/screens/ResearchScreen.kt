package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ai.ZedCraftAiConfig
import com.example.data.remote.YouTubeApiClient
import com.example.data.remote.YtVideoDetail
import com.example.ui.components.AnimatedScoreRing
import com.example.ui.components.DataBadge
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorBanner
import com.example.ui.components.GlassCard
import com.example.ui.components.LoadingBanner
import com.example.ui.components.SectionHeader
import com.example.ui.components.SegmentedPillBar
import com.example.ui.components.SubScoreBar
import com.example.ui.theme.ZedCyan
import com.example.ui.theme.ZedSuccess
import com.example.ui.theme.ZedViolet
import com.example.ui.theme.ZedWarning
import com.example.ui.viewmodel.DetailDestination
import com.example.ui.viewmodel.ZedCraftUiState
import com.example.ui.viewmodel.ZedCraftViewModel
import java.util.Locale

@Composable
fun ResearchScreen(
    uiState: ZedCraftUiState,
    viewModel: ZedCraftViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        SegmentedPillBar(
            options = listOf(
                "Keywords" to Icons.Default.Search,
                "Trends" to Icons.Default.TrendingUp,
                "Competitors" to Icons.Default.Groups,
                "Ideas" to Icons.Default.Lightbulb
            ),
            selectedIndex = uiState.researchSubTab,
            onSelect = { viewModel.selectResearchSubTab(it) }
        )
        Spacer(modifier = Modifier.height(10.dp))

        when (uiState.researchSubTab) {
            0 -> KeywordResearchPane(uiState, viewModel)
            1 -> TrendFinderPane(uiState, viewModel)
            2 -> CompetitorTrackerPane(uiState, viewModel)
            else -> IdeaFinderPane(uiState, viewModel)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeywordResearchPane(
    uiState: ZedCraftUiState,
    viewModel: ZedCraftViewModel
) {
    val clipboard = LocalClipboardManager.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeader(
                title = "Real YouTube Keyword Research",
                subtitle = "Uses real YouTube search results & channel/video telemetry to compute Opportunity vs Competition.",
                badgeText = "YouTube Data API v3",
                isAiBadge = false
            )
        }

        item {
            GlassCard {
                OutlinedTextField(
                    value = uiState.keywordInput,
                    onValueChange = { viewModel.updateKeywordInput(it) },
                    label = { Text("Search Keyword or Topic (e.g., how to edit youtube shorts)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("keyword_research_input"),
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.runKeywordResearch(forceRefresh = false) },
                        enabled = !uiState.isResearchingKeyword,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("run_keyword_research_btn"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Analyze Keyword", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { viewModel.runKeywordResearch(forceRefresh = true) },
                        enabled = !uiState.isResearchingKeyword && uiState.keywordInput.isNotBlank(),
                        modifier = Modifier.height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Bypass Cache & Refresh")
                    }
                }
            }
        }

        if (uiState.isResearchingKeyword) {
            item {
                LoadingBanner(message = "Fetching top-ranking YouTube videos, channel sizes, and tags...")
            }
        }

        uiState.keywordError?.let { err ->
            item {
                ErrorBanner(
                    message = err,
                    onActionLabel = if (!uiState.isYouTubeApiKeyConfigured) "Open Setup Guide" else null,
                    onAction = if (!uiState.isYouTubeApiKeyConfigured) {
                        { viewModel.openDetail(DetailDestination.SetupGuide) }
                    } else null
                )
            }
        }

        val res = uiState.keywordResult
        if (res == null && !uiState.isResearchingKeyword) {
            item {
                EmptyStateCard(
                    title = "Discover Breakout Topics",
                    message = "Enter any keyword to see real ranking videos, average views, channel sizes, small-channel outliers, and extracted tags.",
                    actionLabel = if (!uiState.isYouTubeApiKeyConfigured) "Configure YouTube API Key" else null,
                    onAction = if (!uiState.isYouTubeApiKeyConfigured) {
                        { viewModel.openDetail(DetailDestination.SetupGuide) }
                    } else null
                )
            }
        }

        if (res != null) {
            item {
                GlassCard(accentBorderColor = ZedCyan.copy(alpha = 0.55f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedScoreRing(
                            score = res.opportunityScore,
                            label = "Opportunity Score",
                            isAiEstimate = false
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            DataBadge(text = "ZEDCraft Computed Score", isAi = false)
                            Spacer(modifier = Modifier.height(6.dp))
                            SubScoreBar(
                                label = "Search Demand & Velocity",
                                score = res.demandScore,
                                note = "Avg Views: ${YouTubeApiClient.formatCompact(res.avgViewsTopResults)} • Max: ${YouTubeApiClient.formatCompact(res.maxViewsTopResults)}"
                            )
                            SubScoreBar(
                                label = "Ranking Competition",
                                score = res.competitionScore,
                                note = "Avg Channel Subs: ${YouTubeApiClient.formatCompact(res.avgChannelSubscribers)} • Small Outliers (<50K subs): ${res.smallChannelOutliersCount}"
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = res.methodologyNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (res.relatedKeywords.isNotEmpty() || res.risingTopics.isNotEmpty()) {
                item {
                    GlassCard {
                        Text(
                            text = "Related Search Suggestions & Rising Angles",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            (res.relatedKeywords + res.risingTopics).distinct().forEach { kw ->
                                AssistChip(
                                    onClick = {
                                        viewModel.updateKeywordInput(kw)
                                        viewModel.runKeywordResearch()
                                    },
                                    label = { Text(kw, style = MaterialTheme.typography.bodySmall) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.TrendingUp,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp),
                                            tint = ZedCyan
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (res.extractedTagsFromWinners.isNotEmpty()) {
                item {
                    GlassCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Tags Extracted from Top-Ranking Videos",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Real public tags used by top results for '${res.keyword}'",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    val joined = res.extractedTagsFromWinners.joinToString(", ")
                                    clipboard.setText(AnnotatedString(joined))
                                    viewModel.showToast("Copied ${res.extractedTagsFromWinners.size} ranking tags!")
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy All")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            res.extractedTagsFromWinners.forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                    modifier = Modifier.clickable {
                                        clipboard.setText(AnnotatedString(tag))
                                        viewModel.showToast("Copied tag: $tag")
                                    }
                                ) {
                                    Text(
                                        text = "#$tag",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                SectionHeader(
                    title = "Top-Ranking Videos (${res.topVideos.size})",
                    subtitle = "Tap any video to run a full ZEDCraft SEO Audit & inspect its packaging."
                )
            }

            items(res.topVideos, key = { it.videoId }) { video ->
                RankingVideoCard(
                    video = video,
                    onAudit = {
                        viewModel.openDetail(DetailDestination.VideoAnalyzerAndAudit(video.videoId))
                    },
                    onScoreThumbnail = {
                        viewModel.selectOptimizeSubTab(0)
                        viewModel.loadThumbnailFromVideoUrlOrDetail(video.videoId, video.title)
                    }
                )
            }
        }
    }
}

@Composable
private fun TrendFinderPane(
    uiState: ZedCraftUiState,
    viewModel: ZedCraftViewModel
) {
    val regions = listOf(
        "US" to "United States",
        "GB" to "UK",
        "CA" to "Canada",
        "IN" to "India",
        "AU" to "Australia",
        "DE" to "Germany"
    )
    val categories = listOf(
        "0" to "All Categories",
        "20" to "Gaming",
        "28" to "Science & Tech",
        "24" to "Entertainment",
        "27" to "Education",
        "10" to "Music"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(
                title = "Real-Time Trending Videos",
                subtitle = "Live mostPopular chart from YouTube Data API v3 with outlier velocity multipliers.",
                badgeText = "YouTube Data API v3",
                isAiBadge = false
            )
        }

        item {
            GlassCard {
                Text(
                    text = "Region",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    regions.forEach { (code, label) ->
                        FilterChip(
                            selected = uiState.selectedTrendRegion == code,
                            onClick = {
                                viewModel.updateTrendFilters(code, uiState.selectedTrendCategoryId)
                            },
                            label = { Text("$code • $label") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { (catId, label) ->
                        FilterChip(
                            selected = uiState.selectedTrendCategoryId == catId,
                            onClick = {
                                viewModel.updateTrendFilters(uiState.selectedTrendRegion, catId)
                            },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ZedViolet.copy(alpha = 0.22f),
                                selectedLabelColor = ZedViolet
                            )
                        )
                    }
                }
            }
        }

        if (uiState.isLoadingTrends) {
            item {
                LoadingBanner(message = "Loading trending chart (${uiState.selectedTrendRegion})...")
            }
        }

        uiState.trendsError?.let { err ->
            item {
                ErrorBanner(
                    message = err,
                    onActionLabel = if (!uiState.isYouTubeApiKeyConfigured) "Open Setup Guide" else "Retry",
                    onAction = {
                        if (!uiState.isYouTubeApiKeyConfigured) {
                            viewModel.openDetail(DetailDestination.SetupGuide)
                        } else {
                            viewModel.loadTrendingVideos(forceRefresh = true)
                        }
                    }
                )
            }
        }

        if (uiState.trendingVideos.isEmpty() && !uiState.isLoadingTrends) {
            item {
                EmptyStateCard(
                    title = "Load Live YouTube Trends",
                    message = "Fetch real trending videos in your region and category to spot rising formats and packaging trends.",
                    actionLabel = "Fetch Trending Chart",
                    onAction = { viewModel.loadTrendingVideos(forceRefresh = true) }
                )
            }
        }

        items(uiState.trendingVideos, key = { it.videoId }) { video ->
            RankingVideoCard(
                video = video,
                onAudit = {
                    viewModel.openDetail(DetailDestination.VideoAnalyzerAndAudit(video.videoId))
                },
                onScoreThumbnail = {
                    viewModel.selectOptimizeSubTab(0)
                    viewModel.loadThumbnailFromVideoUrlOrDetail(video.videoId, video.title)
                }
            )
        }
    }
}

@Composable
private fun CompetitorTrackerPane(
    uiState: ZedCraftUiState,
    viewModel: ZedCraftViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeader(
                title = "Competitor Channel Benchmark",
                subtitle = "Track public channels side-by-side against your connected channel.",
                badgeText = "YouTube Data API v3",
                isAiBadge = false
            )
        }

        item {
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.competitorInput,
                        onValueChange = { viewModel.updateCompetitorInput(it) },
                        label = { Text("Add @handle, Channel ID, or URL") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("competitor_input"),
                        shape = RoundedCornerShape(14.dp)
                    )
                    Button(
                        onClick = { viewModel.addCompetitorChannel() },
                        enabled = !uiState.isAddingCompetitor,
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("add_competitor_btn"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Competitor")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Track")
                    }
                }
            }
        }

        if (uiState.isAddingCompetitor) {
            item {
                LoadingBanner(message = "Looking up competitor channel statistics & recent uploads...")
            }
        }

        uiState.competitorError?.let { err ->
            item { ErrorBanner(message = err) }
        }

        val myChannel = uiState.activeChannel
        if (myChannel != null) {
            item {
                GlassCard(accentBorderColor = ZedCyan.copy(alpha = 0.6f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = myChannel.thumbnailUrl,
                            contentDescription = myChannel.title,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            DataBadge(text = "Your Connected Channel", isAi = false)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = myChannel.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${YouTubeApiClient.formatCompact(myChannel.subscriberCount)} subs • ${YouTubeApiClient.formatCompact(myChannel.viewCount)} total views • ${myChannel.videoCount} videos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (uiState.trackedCompetitors.isEmpty() && !uiState.isAddingCompetitor) {
            item {
                EmptyStateCard(
                    title = "No Competitors Tracked Yet",
                    message = "Add any public YouTube channel by @handle (e.g. @mkbhd, @MrBeast) to compare subscriber scale, total views, and recent upload averages."
                )
            }
        }

        items(uiState.trackedCompetitors, key = { it.channelId }) { comp ->
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = comp.thumbnailUrl,
                        contentDescription = comp.title,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = comp.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (comp.handle.isNotBlank()) {
                            Text(
                                text = comp.handle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.removeCompetitor(comp.channelId) }) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Remove ${comp.title}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    CompetitorStatColumn("Subscribers", YouTubeApiClient.formatCompact(comp.subscriberCount))
                    CompetitorStatColumn("Total Views", YouTubeApiClient.formatCompact(comp.viewCount))
                    CompetitorStatColumn("Uploads", comp.videoCount.toString())
                    CompetitorStatColumn("Recent Avg", YouTubeApiClient.formatCompact(comp.recentAvgViews))
                }
                if (myChannel != null && myChannel.subscriberCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val ratio = comp.subscriberCount.toDouble() / myChannel.subscriberCount.toDouble()
                    Text(
                        text = String.format(
                            Locale.US,
                            "Comparison: %.1fx your subscriber count • Recent avg %s views/video",
                            ratio,
                            YouTubeApiClient.formatCompact(comp.recentAvgViews)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CompetitorStatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun IdeaFinderPane(
    uiState: ZedCraftUiState,
    viewModel: ZedCraftViewModel
) {
    val clipboard = LocalClipboardManager.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeader(
                title = "AI Breakout Video Idea Generator",
                subtitle = "Generates 6 complete video concepts with titles, thumbnail visuals, and 15-second hooks.",
                badgeText = ZedCraftAiConfig.ZD_MAIN_MODEL,
                isAiBadge = true
            )
        }

        item {
            GlassCard {
                OutlinedTextField(
                    value = uiState.ideaNicheInput,
                    onValueChange = { viewModel.updateIdeaNicheInput(it) },
                    label = {
                        Text(
                            if (uiState.activeChannel != null) {
                                "Topic or Niche (defaults to ${uiState.activeChannel.title})"
                            } else {
                                "Your Channel Niche or Video Topic"
                            }
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("idea_niche_input"),
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { viewModel.generateAiVideoIdeas() },
                    enabled = !uiState.isGeneratingIdeas,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("generate_video_ideas_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate 6 High-CTR Video Ideas", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (uiState.isGeneratingIdeas) {
            item {
                LoadingBanner(message = "Running ${ZedCraftAiConfig.ZD_MAIN_MODEL} content strategy synthesis...")
            }
        }

        uiState.ideasError?.let { err ->
            item { ErrorBanner(message = err) }
        }

        val ideasRes = uiState.videoIdeasResult
        if (ideasRes == null && !uiState.isGeneratingIdeas) {
            item {
                EmptyStateCard(
                    title = "Never Run Out of Outlier Video Ideas",
                    message = "ZEDCraft combines your channel's top performers and live trends to craft 6 complete video packages (Title + Thumbnail Concept + 15s Hook)."
                )
            }
        }

        if (ideasRes != null) {
            if (ideasRes.fallbackNotice != null) {
                item {
                    DataBadge(text = ideasRes.fallbackNotice, isAi = true, colorOverride = ZedWarning)
                }
            }
            items(ideasRes.ideas) { idea ->
                GlassCard(accentBorderColor = ZedViolet.copy(alpha = 0.5f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DataBadge(text = "${idea.targetPillar} Pillar", isAi = true)
                        DataBadge(
                            text = "AI Estimate: ${idea.opportunityScore}/100",
                            isAi = true,
                            colorOverride = ZedSuccess
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = idea.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Thumbnail Visual: ${idea.thumbnailConcept}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Thumbnail Text (Max 4 words): \"${idea.thumbnailTextOverlay}\"",
                            style = MaterialTheme.typography.labelMedium,
                            color = ZedCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "First 15s Hook: ${idea.hookFirst15Seconds}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Why it works now: ${idea.whyItFitsChannel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.selectOptimizeSubTab(1)
                                viewModel.updateTitleInputs(idea.title, uiState.ideaNicheInput)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Send to Title Optimizer")
                        }
                        IconButton(
                            onClick = {
                                val text = "${idea.title}\nThumbnail: ${idea.thumbnailConcept} (${idea.thumbnailTextOverlay})\nHook: ${idea.hookFirst15Seconds}"
                                clipboard.setText(AnnotatedString(text))
                                viewModel.showToast("Copied video idea package!")
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Idea")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RankingVideoCard(
    video: YtVideoDetail,
    onAudit: () -> Unit,
    onScoreThumbnail: () -> Unit
) {
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            AsyncImage(
                model = video.highResThumbnailUrl.ifBlank { video.thumbnailUrl },
                contentDescription = video.title,
                modifier = Modifier
                    .width(126.dp)
                    .height(72.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
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
                Text(
                    text = "${video.channelTitle} • ${YouTubeApiClient.formatCompact(video.viewCount)} views",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (video.channelSubscribers > 0) {
                        val mult = video.outlierMultiplier
                        DataBadge(
                            text = String.format(Locale.US, "%.1fx Subs", mult),
                            isAi = false,
                            colorOverride = if (mult >= 1.5) ZedSuccess else ZedCyan
                        )
                    }
                    DataBadge(
                        text = String.format(Locale.US, "%.1f%% Eng", video.engagementRatePercent),
                        isAi = false
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onAudit,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("1-Tap SEO Audit", style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(
                onClick = onScoreThumbnail,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 10.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Tag, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Score Thumbnail", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
