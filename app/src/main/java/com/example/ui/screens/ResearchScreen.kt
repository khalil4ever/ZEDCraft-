package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.remote.CompetitorComparison
import com.example.data.remote.KeywordResearchResult
import com.example.data.remote.YouTubeApiClient
import com.example.ui.components.ZdMarkdownMessage
import com.example.ui.components.stripMarkdownToCleanText
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
import com.example.ui.viewmodel.ResearchSubTab
import com.example.ui.viewmodel.ZedCraftUiState
import com.example.ui.viewmodel.ZedCraftViewModel
import java.util.Locale

@Composable
fun ResearchScreen(
    uiState: ZedCraftUiState,
    viewModel: ZedCraftViewModel
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ZedDarkBackground)
    ) {
        ResearchSubTabBar(
            selected = uiState.selectedResearchSubTab,
            onSelect = { viewModel.selectResearchSubTab(it) }
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (uiState.selectedResearchSubTab) {
                ResearchSubTab.Keywords -> {
                    item {
                        KeywordExplorerSection(uiState = uiState, viewModel = viewModel, context = context)
                    }
                }
                ResearchSubTab.TrendsAndIdeas -> {
                    item {
                        TrendsAndViralIdeasSection(uiState = uiState, viewModel = viewModel, context = context)
                    }
                }
                ResearchSubTab.Competitors -> {
                    item {
                        CompetitorBenchmarkSection(uiState = uiState, viewModel = viewModel)
                    }
                }
                ResearchSubTab.Comments -> {
                    item {
                        CommentSentimentSection(uiState = uiState, viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResearchSubTabBar(
    selected: ResearchSubTab,
    onSelect: (ResearchSubTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ZedGlassSurface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ResearchSubTab.entries.forEach { tab ->
            val isSelected = tab == selected
            val icon: ImageVector = when (tab) {
                ResearchSubTab.Keywords -> Icons.Default.Search
                ResearchSubTab.TrendsAndIdeas -> Icons.AutoMirrored.Filled.TrendingUp
                ResearchSubTab.Competitors -> Icons.AutoMirrored.Filled.CompareArrows
                ResearchSubTab.Comments -> Icons.Default.Comment
            }
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelect(tab) }
                    .testTag("research_subtab_${tab.name.lowercase()}"),
                color = if (isSelected) ZedCyanPrimary else ZedDarkSurfaceElevated,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, if (isSelected) ZedCyanPrimary else ZedCardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = tab.label,
                        tint = if (isSelected) ZedDarkBackground else ZedTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) ZedDarkBackground else Color.White
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeywordExplorerSection(
    uiState: ZedCraftUiState,
    viewModel: ZedCraftViewModel,
    context: Context
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
            border = BorderStroke(1.dp, ZedCardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "YouTube Keyword Opportunity & Difficulty Radar",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Analyzes top ranking videos, view velocity, title match density, and real autocomplete suggestions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ZedTextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.keywordSeedInput,
                    onValueChange = { viewModel.updateKeywordSeedInput(it) },
                    placeholder = { Text("Enter keyword (e.g., AI video editing, Android Studio)") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = ZedCyanPrimary)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("keyword_seed_input"),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ZedCyanPrimary,
                        unfocusedBorderColor = ZedCardBorder
                    )
                )

                if (!uiState.researchError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.researchError,
                        style = MaterialTheme.typography.bodySmall,
                        color = ZedCoralError
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.runKeywordResearch() },
                    enabled = !uiState.isResearchingKeywords,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("run_keyword_research_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ZedCyanPrimary,
                        contentColor = ZedDarkBackground
                    )
                ) {
                    if (uiState.isResearchingKeywords) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = ZedDarkBackground
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scanning YouTube Search Landscape...")
                    } else {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analyze Keyword Opportunity", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        val kwRes: KeywordResearchResult? = uiState.keywordResearchResult
        if (kwRes != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
                border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "\"${kwRes.seedKeyword}\"",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = kwRes.verdict,
                                style = MaterialTheme.typography.bodySmall,
                                color = ZedCyanPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = ZedEmeraldSuccess.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, ZedEmeraldSuccess)
                        ) {
                            Text(
                                text = "${kwRes.opportunityScore}/100",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = ZedEmeraldSuccess,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    KeywordScoreMeter("Search Demand Signal", kwRes.demandSignalScore, ZedCyanPrimary)
                    KeywordScoreMeter("Competition Difficulty (Lower = Easier)", kwRes.competitionDifficultyScore, ZedAmberWarning)
                    KeywordScoreMeter("Overall Opportunity Score", kwRes.opportunityScore, ZedEmeraldSuccess)

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Top 10 Avg Views: ${YouTubeApiClient.formatCompact(kwRes.avgTopViews)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Median Views: ${YouTubeApiClient.formatCompact(kwRes.medianTopViews)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = ZedTextSecondary
                        )
                    }
                }
            }

            if (kwRes.autocompleteTerms.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
                    border = BorderStroke(1.dp, ZedCardBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "YouTube Autocomplete Long-Tail Queries",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = {
                                    val joined = kwRes.autocompleteTerms.joinToString("\n")
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cb?.setPrimaryClip(ClipData.newPlainText("Keywords", joined))
                                    Toast.makeText(context, "Copied long-tail keywords!", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = ZedCyanPrimary)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            kwRes.autocompleteTerms.forEach { term ->
                                Surface(
                                    modifier = Modifier.clickable {
                                        viewModel.updateKeywordSeedInput(term)
                                        viewModel.runKeywordResearch()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = ZedDarkSurfaceElevated,
                                    border = BorderStroke(1.dp, ZedCardBorder)
                                ) {
                                    Text(
                                        text = term,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = ZedCyanPrimary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (kwRes.topRankingVideos.isNotEmpty()) {
                Text(
                    text = "Top Ranking Videos for \"${kwRes.seedKeyword}\"",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                kwRes.topRankingVideos.take(6).forEach { v ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.analyzeVideoDeeply(v.videoId) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
                        border = BorderStroke(1.dp, ZedCardBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = v.thumbnailUrl,
                                contentDescription = v.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(110.dp)
                                    .height(62.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = v.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${v.channelTitle} • ${YouTubeApiClient.formatCompact(v.viewCount)} views",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ZedCyanPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeywordScoreMeter(label: String, score: Int, color: Color) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = ZedTextSecondary)
            Text(text = "$score/100", style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (score / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = ZedDarkBackground
        )
    }
}

@Composable
private fun TrendsAndViralIdeasSection(
    uiState: ZedCraftUiState,
    viewModel: ZedCraftViewModel,
    context: Context
) {
    var nicheQuery by remember {
        mutableStateOf(uiState.activeChannel?.title ?: uiState.optimizeNicheInput)
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
            border = BorderStroke(1.dp, ZedCardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Breakout Topic & Outlier Video Idea Generator",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Generates 10 high-demand, low-saturation video concepts with clickable titles (<60 chars), 3-word thumbnail text, and opening hooks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ZedTextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = nicheQuery,
                    onValueChange = { nicheQuery = it },
                    label = { Text("Your Niche, Topic, or Audience") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ZedCyanPrimary,
                        unfocusedBorderColor = ZedCardBorder
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.generateViralTopicIdeasForNiche(nicheQuery) },
                    enabled = !uiState.isGeneratingTrendIdeas,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ZedPurpleAccent,
                        contentColor = Color.White
                    )
                ) {
                    if (uiState.isGeneratingTrendIdeas) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Finding 10 Outlier Video Ideas...")
                    } else {
                        Icon(imageVector = Icons.Default.Lightbulb, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate 10 Breakout Video Ideas", fontWeight = FontWeight.Bold)
                    }
                }

                if (!uiState.researchError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.researchError,
                        style = MaterialTheme.typography.bodySmall,
                        color = ZedCoralError
                    )
                }
            }
        }

        if (!uiState.trendIdeasMarkdown.isNullOrBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
                border = BorderStroke(1.dp, ZedPurpleAccent.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "10 High-CTR Video Ideas",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = ZedPurpleAccent
                        )
                        IconButton(
                            onClick = {
                                viewModel.saveIdeaToVault(
                                    category = "Viral Ideas",
                                    title = "10 Breakout Ideas: $nicheQuery",
                                    content = stripMarkdownToCleanText(uiState.trendIdeasMarkdown)
                                )
                                Toast.makeText(context, "Saved ideas to Vault!", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(imageVector = Icons.Default.BookmarkAdd, contentDescription = "Save", tint = ZedCyanPrimary)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    ZdMarkdownMessage(
                        markdown = uiState.trendIdeasMarkdown,
                        isStreaming = false
                    )
                }
            }
        }
    }
}

@Composable
private fun CompetitorBenchmarkSection(
    uiState: ZedCraftUiState,
    viewModel: ZedCraftViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
            border = BorderStroke(1.dp, ZedCardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Head-to-Head Competitor Channel Benchmark",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Compare any two YouTube channels side-by-side: subscribers, average views per upload, engagement rate, and Shorts mix.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ZedTextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.competitorInputA.ifBlank { uiState.activeChannel?.handle.orEmpty() },
                    onValueChange = { viewModel.updateCompetitorInputs(it, uiState.competitorInputB) },
                    label = { Text("Channel A (@handle or Channel ID)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ZedCyanPrimary,
                        unfocusedBorderColor = ZedCardBorder
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = uiState.competitorInputB,
                    onValueChange = {
                        viewModel.updateCompetitorInputs(
                            uiState.competitorInputA.ifBlank { uiState.activeChannel?.handle.orEmpty() },
                            it
                        )
                    },
                    label = { Text("Competitor Channel B (@handle or Channel ID)") },
                    placeholder = { Text("e.g., @mkbhd, @MrBeast") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ZedCyanPrimary,
                        unfocusedBorderColor = ZedCardBorder
                    )
                )

                if (!uiState.researchError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.researchError,
                        style = MaterialTheme.typography.bodySmall,
                        color = ZedCoralError
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.runCompetitorComparison() },
                    enabled = !uiState.isComparingCompetitors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ZedCyanPrimary,
                        contentColor = ZedDarkBackground
                    )
                ) {
                    if (uiState.isComparingCompetitors) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = ZedDarkBackground
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Benchmarking Channels...")
                    } else {
                        Icon(imageVector = Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Compare Channels Side-by-Side", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        val comp: CompetitorComparison? = uiState.competitorComparison
        if (comp != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
                border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ChannelMiniHeader(
                            modifier = Modifier.weight(1f),
                            title = comp.primaryChannel.title,
                            handle = comp.primaryChannel.handle,
                            thumbUrl = comp.primaryChannel.thumbnailUrl,
                            accent = ZedCyanPrimary
                        )
                        Text(
                            text = "VS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = ZedAmberWarning,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        ChannelMiniHeader(
                            modifier = Modifier.weight(1f),
                            title = comp.competitorChannel.title,
                            handle = comp.competitorChannel.handle,
                            thumbUrl = comp.competitorChannel.thumbnailUrl,
                            accent = ZedPurpleAccent
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    ComparisonMetricRow(
                        label = "Subscribers",
                        leftVal = YouTubeApiClient.formatCompact(comp.primaryChannel.subscriberCount),
                        rightVal = YouTubeApiClient.formatCompact(comp.competitorChannel.subscriberCount)
                    )
                    ComparisonMetricRow(
                        label = "Avg Views (Recent)",
                        leftVal = YouTubeApiClient.formatCompact(comp.primaryAvgViews),
                        rightVal = YouTubeApiClient.formatCompact(comp.competitorAvgViews)
                    )
                    ComparisonMetricRow(
                        label = "Engagement Rate",
                        leftVal = String.format(Locale.US, "%.2f%%", comp.primaryEngagementRate),
                        rightVal = String.format(Locale.US, "%.2f%%", comp.competitorEngagementRate)
                    )
                    ComparisonMetricRow(
                        label = "Shorts Ratio",
                        leftVal = "${comp.primaryShortsPct}%",
                        rightVal = "${comp.competitorShortsPct}%"
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Key Strategic Takeaways:",
                        style = MaterialTheme.typography.labelLarge,
                        color = ZedEmeraldSuccess,
                        fontWeight = FontWeight.Bold
                    )
                    comp.keyTakeaways.forEach { bullet ->
                        Text(
                            text = "• $bullet",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            modifier = Modifier.padding(vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelMiniHeader(
    modifier: Modifier = Modifier,
    title: String,
    handle: String,
    thumbUrl: String,
    accent: Color
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = thumbUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = handle,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            maxLines = 1
        )
    }
}

@Composable
private fun ComparisonMetricRow(label: String, leftVal: String, rightVal: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        color = ZedDarkSurfaceElevated
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = leftVal, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = ZedCyanPrimary)
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = ZedTextSecondary)
            Text(text = rightVal, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = ZedPurpleAccent)
        }
    }
}

@Composable
private fun CommentSentimentSection(
    uiState: ZedCraftUiState,
    viewModel: ZedCraftViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
            border = BorderStroke(1.dp, ZedCardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Audience Comment Sentiment & Question Miner",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Fetches real viewer comments from any YouTube video and extracts unanswered questions and next-video ideas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ZedTextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.commentVideoUrlInput,
                    onValueChange = { viewModel.updateCommentVideoUrlInput(it) },
                    label = { Text("YouTube Video URL or ID (blank uses selected video)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ZedCyanPrimary,
                        unfocusedBorderColor = ZedCardBorder
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.runCommentSentimentExplorer() },
                    enabled = !uiState.isAnalyzingComments,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ZedCyanPrimary,
                        contentColor = ZedDarkBackground
                    )
                ) {
                    if (uiState.isAnalyzingComments) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = ZedDarkBackground
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Mining Comments & Sentiment...")
                    } else {
                        Icon(imageVector = Icons.Default.Comment, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analyze Viewer Comments", fontWeight = FontWeight.Bold)
                    }
                }

                if (!uiState.researchError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.researchError,
                        style = MaterialTheme.typography.bodySmall,
                        color = ZedCoralError
                    )
                }
            }
        }

        if (!uiState.commentSentimentMarkdown.isNullOrBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
                border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Audience Sentiment & Content Opportunities",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = ZedCyanPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ZdMarkdownMessage(
                        markdown = uiState.commentSentimentMarkdown,
                        isStreaming = false
                    )
                }
            }
        }
    }
}
