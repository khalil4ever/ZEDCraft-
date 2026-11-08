package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ui.components.AnimatedScoreRing
import com.example.ui.components.CopyableChipRow
import com.example.ui.components.DataBadge
import com.example.ui.components.ErrorBanner
import com.example.ui.components.GlassCard
import com.example.ui.components.LoadingBanner
import com.example.ui.components.SectionHeader
import com.example.ui.components.SegmentedPillBar
import com.example.ui.components.SubScoreBar
import com.example.ui.components.scoreColor
import com.example.ui.theme.ZedCyan
import com.example.ui.theme.ZedError
import com.example.ui.theme.ZedSuccess
import com.example.ui.theme.ZedViolet
import com.example.ui.theme.ZedWarning
import com.example.ui.viewmodel.ZedCraftUiState
import com.example.ui.viewmodel.ZedCraftViewModel

@Composable
fun OptimizeScreen(
    uiState: ZedCraftUiState,
    viewModel: ZedCraftViewModel
) {
    val clipboard = LocalClipboardManager.current
    val onCopy: (String, String) -> Unit = { label, text ->
        clipboard.setText(AnnotatedString(text))
        viewModel.showToast("Copied $label to clipboard!")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        SegmentedPillBar(
            options = listOf(
                "Thumb" to Icons.Default.Image,
                "Title" to Icons.Default.Title,
                "Desc" to Icons.Default.Description,
                "Tags" to Icons.Default.Tag
            ),
            selectedIndex = uiState.optimizeSubTab,
            onSelect = { viewModel.selectOptimizeSubTab(it) }
        )
        Spacer(modifier = Modifier.height(12.dp))

        when (uiState.optimizeSubTab) {
            0 -> ThumbnailScorerTab(uiState = uiState, viewModel = viewModel)
            1 -> TitleOptimizerTab(uiState = uiState, viewModel = viewModel, onCopy = onCopy)
            2 -> DescriptionOptimizerTab(uiState = uiState, viewModel = viewModel, onCopy = onCopy)
            3 -> TagsGeneratorTab(uiState = uiState, viewModel = viewModel, onCopy = onCopy)
        }
    }
}

@Composable
private fun ThumbnailScorerTab(
    uiState: ZedCraftUiState,
    viewModel: ZedCraftViewModel
) {
    val context = LocalContext.current
    var ytLinkInput by remember { mutableStateOf("") }

    val pickerA = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.loadBitmapFromUri(context, uri, isSlotB = false)
    }

    val pickerB = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.loadBitmapFromUri(context, uri, isSlotB = true)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            GlassCard {
                SectionHeader(
                    title = "Gemini Vision Thumbnail Scorer",
                    subtitle = "Upload a thumbnail, pick from your connected videos, or paste a YouTube link.",
                    badgeText = "AI Estimate",
                    isAiBadge = true
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Mode Toggle: Single Score vs Head-to-Head A/B Compare
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !uiState.isAbCompareMode,
                        onClick = { viewModel.setAbCompareMode(false) },
                        label = { Text("Single Thumbnail Score") },
                        leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    FilterChip(
                        selected = uiState.isAbCompareMode,
                        onClick = { viewModel.setAbCompareMode(true) },
                        label = { Text("A/B Head-to-Head") },
                        leadingIcon = { Icon(Icons.Default.Compare, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Paired Video Title Input (for Title + Thumbnail Synergy check)
                OutlinedTextField(
                    value = uiState.thumbnailVideoTitle,
                    onValueChange = { viewModel.setThumbnailVideoTitle(it) },
                    label = { Text("Paired Video Title (Checks Title + Thumbnail Synergy)") },
                    placeholder = { Text("e.g. I Built a $100 Home Studio That Looks Like $10,000") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("thumbnail_paired_title_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Upload Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            pickerA.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.weight(1f).testTag("pick_thumbnail_a_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (uiState.isAbCompareMode) "Pick Thumb A" else "Upload Image")
                    }
                    if (uiState.isAbCompareMode) {
                        OutlinedButton(
                            onClick = {
                                pickerB.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.weight(1f).testTag("pick_thumbnail_b_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pick Thumb B")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Load from YouTube Link
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = ytLinkInput,
                        onValueChange = { ytLinkInput = it },
                        placeholder = { Text("Or paste YouTube video link / ID") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedButton(
                        onClick = {
                            if (ytLinkInput.isNotBlank()) {
                                viewModel.loadThumbnailFromVideoUrlOrDetail(
                                    ytLinkInput,
                                    isSlotB = uiState.isAbCompareMode && uiState.thumbnailBitmapA != null && uiState.thumbnailBitmapB == null
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Fetch")
                    }
                }

                // Quick pick from Connected Channel's Recent Videos
                if (uiState.activeChannelVideos.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "OR PICK FROM CONNECTED VIDEOS",
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
                        uiState.activeChannelVideos.take(8).forEach { v ->
                            Column(
                                modifier = Modifier
                                    .width(120.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        viewModel.loadThumbnailFromVideoUrlOrDetail(
                                            v.videoId,
                                            v.title,
                                            isSlotB = uiState.isAbCompareMode && uiState.thumbnailBitmapA != null && uiState.thumbnailBitmapB == null
                                        )
                                    }
                            ) {
                                AsyncImage(
                                    model = v.thumbnailUrl,
                                    contentDescription = v.title,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(68.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Text(
                                    text = v.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Thumbnail Preview + Real Phone-Feed Tiny Size Preview (120dp)
                if (uiState.thumbnailBitmapA != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (uiState.isAbCompareMode) "THUMBNAIL A" else "FULL PREVIEW",
                                style = MaterialTheme.typography.labelSmall,
                                color = ZedCyan
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Image(
                                bitmap = uiState.thumbnailBitmapA.asImageBitmap(),
                                contentDescription = "Thumbnail A",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }

                        if (uiState.isAbCompareMode && uiState.thumbnailBitmapB != null) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "THUMBNAIL B",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ZedViolet
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Image(
                                    bitmap = uiState.thumbnailBitmapB.asImageBitmap(),
                                    contentDescription = "Thumbnail B",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        } else {
                            // Real Phone-Feed Tiny Legibility Preview
                            Column(
                                modifier = Modifier.width(124.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(13.dp), tint = ZedCyan)
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "FEED SIZE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ZedCyan
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Box {
                                    Image(
                                        bitmap = uiState.thumbnailBitmapA.asImageBitmap(),
                                        contentDescription = "Mobile feed size preview",
                                        modifier = Modifier
                                            .width(120.dp)
                                            .height(68.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, ZedCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    // Simulated YouTube duration badge in bottom-right corner
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.82f),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                    ) {
                                        Text(
                                            text = "12:48",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "Actual mobile size + timestamp overlay",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = { viewModel.runThumbnailScorer() },
                    enabled = !uiState.isScoringThumbnail,
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("run_thumbnail_scorer_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.isAbCompareMode) "Run Vision A/B Comparison" else "Score Thumbnail with Gemini Vision",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (uiState.isScoringThumbnail) {
            item {
                LoadingBanner("Analyzing visual contrast, focal hierarchy, mobile legibility & title synergy...")
            }
        }

        if (uiState.thumbnailError != null) {
            item {
                ErrorBanner(message = uiState.thumbnailError)
            }
        }

        // Single Thumbnail Score Result
        val res = uiState.thumbnailScoreResult
        if (!uiState.isAbCompareMode && res != null) {
            item {
                GlassCard(accentBorderColor = scoreColor(res.overallScore).copy(alpha = 0.65f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedScoreRing(
                            score = res.overallScore,
                            label = "Thumbnail Score",
                            isAiEstimate = true
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            DataBadge(text = res.estimateLabel, isAi = true)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Title + Thumbnail Synergy: ${res.titleSynergyScore}/100",
                                style = MaterialTheme.typography.titleMedium,
                                color = scoreColor(res.titleSynergyScore)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = res.titleSynergyVerdict,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    res.breakdown.forEach { metric ->
                        SubScoreBar(label = metric.label, score = metric.score, note = metric.note)
                    }

                    if (res.problemAreas.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "MARKED PROBLEM AREAS",
                            style = MaterialTheme.typography.labelSmall,
                            color = ZedWarning
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        res.problemAreas.forEach { prob ->
                            Row(
                                modifier = Modifier.padding(vertical = 3.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    tint = ZedWarning,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = prob,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    if (res.actionableFixes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "SPECIFIC ACTIONABLE FIXES",
                            style = MaterialTheme.typography.labelSmall,
                            color = ZedSuccess
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        res.actionableFixes.forEachIndexed { idx, fix ->
                            Text(
                                text = "${idx + 1}. $fix",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }

        // A/B Compare Result
        val abRes = uiState.thumbnailAbResult
        if (uiState.isAbCompareMode && abRes != null) {
            item {
                GlassCard(accentBorderColor = ZedSuccess.copy(alpha = 0.7f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DataBadge(text = "Winner: Thumbnail ${abRes.winner}", isAi = true, colorOverride = ZedSuccess)
                        DataBadge(text = "AI Estimate", isAi = true)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        AnimatedScoreRing(score = abRes.scoreA, label = "Thumbnail A", size = 84.dp)
                        AnimatedScoreRing(score = abRes.scoreB, label = "Thumbnail B", size = 84.dp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = abRes.headlineVerdict,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "WHY THUMBNAIL ${abRes.winner} WINS",
                        style = MaterialTheme.typography.labelSmall,
                        color = ZedCyan
                    )
                    abRes.reasonsWinnerWon.forEach { r ->
                        Text(
                            text = "• $r",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "TITLE + THUMBNAIL PAIR SYNERGY",
                        style = MaterialTheme.typography.labelSmall,
                        color = ZedViolet
                    )
                    Text(
                        text = abRes.titlePairSynergy,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun TitleOptimizerTab(
    uiState: ZedCraftUiState,
    viewModel: ZedCraftViewModel,
    onCopy: (String, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            GlassCard {
                SectionHeader(
                    title = "Title Optimizer & 10 Non-Clickbait Angles",
                    subtitle = "Score length, keyword placement, curiosity gap, emotional pull, and mobile truncation.",
                    badgeText = "AI Estimate",
                    isAiBadge = true
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = uiState.titleInput,
                    onValueChange = { viewModel.updateTitleInputs(it) },
                    label = { Text("Draft Video Title (${uiState.titleInput.length}/100 chars)") },
                    placeholder = { Text("e.g. How I Learned Kotlin Compose in 30 Days") },
                    modifier = Modifier.fillMaxWidth().testTag("title_optimizer_input"),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.titleNicheContext,
                    onValueChange = { viewModel.updateTitleInputs(uiState.titleInput, it) },
                    label = { Text("Optional Video Promise / Niche Context") },
                    placeholder = { Text("What does the video actually deliver?") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.runTitleOptimizer() },
                    enabled = !uiState.isOptimizingTitle,
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("run_title_optimizer_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Score Title & Generate 10 Better Titles", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (uiState.isOptimizingTitle) {
            item { LoadingBanner("Scoring title packaging and crafting 10 high-CTR alternatives...") }
        }

        if (uiState.titleError != null) {
            item { ErrorBanner(message = uiState.titleError) }
        }

        val res = uiState.titleResult
        if (res != null) {
            item {
                GlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedScoreRing(
                            score = res.overallScore,
                            label = "Title Score",
                            isAiEstimate = true
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "MOBILE FEED & SEARCH PREVIEW",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (res.truncatesOnMobile) ZedWarning else ZedSuccess
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = res.mobilePreviewText,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${uiState.activeChannel?.title ?: "Your Channel"} • 24K views • 2 hours ago",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    res.breakdown.forEach { m ->
                        SubScoreBar(label = m.label, score = m.score, note = m.note)
                    }
                }
            }

            item {
                SectionHeader(
                    title = "10 Improved Titles",
                    subtitle = "Tap Copy on any title to use it.",
                    badgeText = "Non-Clickbait",
                    isAiBadge = true
                )
            }

            items(res.tenImprovedTitles.size) { index ->
                val option = res.tenImprovedTitles[index]
                GlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DataBadge(text = option.angleType, isAi = true)
                        Text(
                            text = "${option.score}/100",
                            style = MaterialTheme.typography.labelLarge,
                            color = scoreColor(option.score),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    CopyableChipRow(
                        title = "Option #${index + 1} (${option.title.length} chars)",
                        textToCopy = option.title,
                        onCopy = { onCopy("Title #${index + 1}", it) }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = option.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DescriptionOptimizerTab(
    uiState: ZedCraftUiState,
    viewModel: ZedCraftViewModel,
    onCopy: (String, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            GlassCard {
                SectionHeader(
                    title = "Description & Chapters Optimizer",
                    subtitle = "Strong first 2 lines (above 'Show more'), natural SEO keywords, auto-chapters from transcript, and hashtags.",
                    badgeText = "AI Optimizer",
                    isAiBadge = true
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = uiState.descTitleInput,
                    onValueChange = {
                        viewModel.updateDescriptionInputs(it, uiState.descExistingInput, uiState.descTranscriptInput)
                    },
                    label = { Text("Video Title & Primary Keyword") },
                    modifier = Modifier.fillMaxWidth().testTag("desc_title_input"),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.descExistingInput,
                    onValueChange = {
                        viewModel.updateDescriptionInputs(uiState.descTitleInput, it, uiState.descTranscriptInput)
                    },
                    label = { Text("Existing Description (Optional)") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.descTranscriptInput,
                    onValueChange = {
                        viewModel.updateDescriptionInputs(uiState.descTitleInput, uiState.descExistingInput, it)
                    },
                    label = { Text("Paste Transcript / Captions / Video Notes (Auto-Generates 00:00 Chapters)") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.runDescriptionOptimizer() },
                    enabled = !uiState.isOptimizingDescription,
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("run_desc_optimizer_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Optimize Description & Auto-Generate Chapters", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (uiState.isOptimizingDescription) {
            item { LoadingBanner("Writing above-the-fold hook, natural SEO paragraphs, and 00:00 chapters...") }
        }

        if (uiState.descriptionError != null) {
            item { ErrorBanner(message = uiState.descriptionError) }
        }

        val res = uiState.descriptionResult
        if (res != null) {
            item {
                GlassCard {
                    CopyableChipRow(
                        title = "FIRST 2 LINES (SHOWN BEFORE 'SHOW MORE')",
                        textToCopy = res.firstTwoLinesHook,
                        onCopy = { onCopy("First 2 Lines Hook", it) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    CopyableChipRow(
                        title = "AUTO-GENERATED 00:00 CHAPTERS",
                        textToCopy = res.generatedChapters.joinToString("\n"),
                        onCopy = { onCopy("Chapters", it) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    CopyableChipRow(
                        title = "FULL READY-TO-PASTE DESCRIPTION",
                        textToCopy = res.fullOptimizedDescription,
                        onCopy = { onCopy("Full Description", it) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsGeneratorTab(
    uiState: ZedCraftUiState,
    viewModel: ZedCraftViewModel,
    onCopy: (String, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            GlassCard {
                SectionHeader(
                    title = "Tags & Hashtags Generator",
                    subtitle = "Extracts real tags from top-ranking videos for your topic + Gemini optimization within YouTube's 500-char limit.",
                    badgeText = "Data API + AI",
                    isAiBadge = false
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = uiState.tagsTopicInput,
                    onValueChange = { viewModel.updateTagsInput(it) },
                    label = { Text("Video Topic or Title") },
                    placeholder = { Text("e.g. Android Jetpack Compose Tutorial 2026") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("tags_topic_input"),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.runTagsGenerator() },
                    enabled = !uiState.isGeneratingTags,
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("run_tags_generator_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Tag, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate Tags & Top 3 Hashtags", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (uiState.isGeneratingTags) {
            item { LoadingBanner("Fetching top-ranking video tags & fitting within YouTube's 500-char limit...") }
        }

        if (uiState.tagsError != null) {
            item { ErrorBanner(message = uiState.tagsError) }
        }

        val res = uiState.tagsResult
        if (res != null) {
            item {
                val joinedTags = res.recommendedTags.joinToString(", ")
                GlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recommended Tag Set (${res.recommendedTags.size} tags)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        DataBadge(
                            text = "${res.totalCharacterCount}/${res.characterLimitMax} chars",
                            colorOverride = ZedSuccess
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        res.recommendedTags.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                border = BorderStroke(1.dp, ZedCyan.copy(alpha = 0.4f)),
                                modifier = Modifier.clickable { onCopy("Tag '$tag'", tag) }
                            ) {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { onCopy("All ${res.recommendedTags.size} Tags", joinedTags) },
                        modifier = Modifier.fillMaxWidth().testTag("copy_all_tags_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy All Tags in One Tap (${res.totalCharacterCount} chars)")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    CopyableChipRow(
                        title = "3 BEST HASHTAGS FOR DESCRIPTION",
                        textToCopy = res.threeBestHashtags.joinToString(" "),
                        onCopy = { onCopy("3 Hashtags", it) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = res.strategyExplanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
