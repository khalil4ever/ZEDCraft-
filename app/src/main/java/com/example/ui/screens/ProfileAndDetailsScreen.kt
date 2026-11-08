package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.VerifiedUser
import com.example.BuildConfig
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ai.ZedCraftAiConfig
import com.example.data.model.SavedWorkItem
import com.example.data.model.WorkType
import com.example.data.remote.YouTubeApiClient
import com.example.ui.components.AnimatedScoreRing
import com.example.ui.components.DataBadge
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorBanner
import com.example.ui.components.GlassCard
import com.example.ui.components.LoadingBanner
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.SubScoreBar
import com.example.ui.components.scoreColor
import com.example.ui.theme.ZedCyan
import com.example.ui.theme.ZedError
import com.example.ui.theme.ZedSuccess
import com.example.ui.theme.ZedViolet
import com.example.ui.theme.ZedWarning
import com.example.ui.viewmodel.DetailDestination
import com.example.ui.viewmodel.ZedCraftUiState
import com.example.ui.viewmodel.ZedCraftViewModel
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    uiState: ZedCraftUiState,
    cachedItemsCount: Int,
    viewModel: ZedCraftViewModel,
    onRequestYouTubeOAuth: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { androidx.credentials.CredentialManager.create(context) }

    var selectedWorkFilter by rememberSaveable { mutableStateOf("ALL") }
    var channelLookupQuery by rememberSaveable { mutableStateOf("") }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    val workFilters = listOf(
        "ALL" to "All Saved",
        WorkType.THUMBNAIL_SCORE to "Thumbnails",
        WorkType.TITLE_OPTIMIZER to "Titles",
        WorkType.DESCRIPTION_OPTIMIZER to "Descriptions",
        WorkType.TAGS_GENERATOR to "Tags",
        WorkType.KEYWORD_RESEARCH to "Keywords",
        WorkType.VIDEO_AUDIT to "Audits",
        WorkType.TREND_IDEA to "Ideas"
    )

    val filteredWork = remember(uiState.savedWorkItems, selectedWorkFilter) {
        if (selectedWorkFilter == "ALL") uiState.savedWorkItems
        else uiState.savedWorkItems.filter { it.workType == selectedWorkFilter }
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Delete All ZEDCraft Data?") },
            text = {
                Text(
                    "This permanently deletes your saved optimizations, competitor benchmarks, ZD chat history, connected channel records, and local YouTube API cache. You can reconnect your channel at any time."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAllDialog = false
                        viewModel.deleteAllUserAndLocalData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ZedError)
                ) {
                    Text("Delete Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Account & Channel Switcher Card
        item {
            SectionHeader(
                title = "Account & Channels",
                subtitle = "Manage your Google Sign-In, connected YouTube channels, and OAuth access."
            )
        }

        item {
            GlassCard(accentBorderColor = ZedCyan.copy(alpha = 0.5f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = ZedCyan,
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (uiState.isSignedInWithGoogle) uiState.userDisplayName else "Guest Creator Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (uiState.isSignedInWithGoogle) {
                                "${uiState.userEmail} • Cloud Sync Active"
                            } else {
                                "Sign in with Google to sync saved work & authorize YouTube Analytics."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (uiState.isSignedInWithGoogle) {
                        OutlinedButton(
                            onClick = {
                                signOutFromZedCraft(
                                    credentialManager = credentialManager,
                                    scope = scope,
                                    onSignOutComplete = {
                                        viewModel.showToast("Signed out of Google.")
                                    }
                                )
                            }
                        ) {
                            Text("Sign Out")
                        }
                    } else {
                        Button(
                            onClick = {
                                onGoogleSignInClicked(
                                    context = context,
                                    credentialManager = credentialManager,
                                    onAuthSuccess = {
                                        viewModel.showToast("Signed in with Google!")
                                        onRequestYouTubeOAuth()
                                    },
                                    onAuthError = { msg ->
                                        viewModel.showToast(msg)
                                    },
                                    scope = scope
                                )
                            }
                        ) {
                            Text("Google Sign-In")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(12.dp))

                // Multi-Channel Switcher
                if (uiState.connectedChannels.isNotEmpty()) {
                    Text(
                        text = "Connected Channels (${uiState.connectedChannels.size}) — Tap to Switch:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    uiState.connectedChannels.forEach { ch ->
                        val isCurrent = uiState.activeChannel?.channelId == ch.channelId
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            else MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                            border = BorderStroke(
                                1.dp,
                                if (isCurrent) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { viewModel.switchActiveChannel(ch) }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = ch.thumbnailUrl,
                                    contentDescription = ch.title,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = ch.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${YouTubeApiClient.formatCompact(ch.subscriberCount)} subs • Health ${ch.healthScore}/100",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isCurrent) {
                                    DataBadge(text = "Active", isAi = false, colorOverride = ZedSuccess)
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                IconButton(onClick = { viewModel.disconnectChannel(ch.channelId) }) {
                                    Icon(
                                        Icons.Default.LinkOff,
                                        contentDescription = "Disconnect ${ch.title}",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Add or Authorize Another Channel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = channelLookupQuery,
                        onValueChange = { channelLookupQuery = it },
                        label = { Text("Connect Channel by @handle or ID") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Button(
                        onClick = {
                            viewModel.connectChannelByQuery(channelLookupQuery)
                            channelLookupQuery = ""
                        },
                        modifier = Modifier.height(52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Connect")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onRequestYouTubeOAuth,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Authorize YouTube Read-Only OAuth 2.0")
                }
            }
        }

        // 2. Saved Work & Version Comparison Section
        item {
            SectionHeader(
                title = "Saved Work & History (${uiState.savedWorkItems.size})",
                subtitle = "Every score, title rewrite, tag set, keyword report, and audit is saved automatically.",
                badgeText = if (uiState.isSignedInWithGoogle) "Cloud + Offline" else "Local Room DB",
                isAiBadge = false
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                workFilters.forEach { (key, label) ->
                    FilterChip(
                        selected = selectedWorkFilter == key,
                        onClick = { selectedWorkFilter = key },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }

        // Side-by-Side Compare Card when 1 or 2 items are selected
        if (uiState.selectedCompareItemA != null || uiState.selectedCompareItemB != null) {
            item {
                val itemA = uiState.selectedCompareItemA
                val itemB = uiState.selectedCompareItemB
                GlassCard(accentBorderColor = ZedViolet) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CompareArrows, contentDescription = null, tint = ZedViolet)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Version Comparison",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        TextButton(onClick = { viewModel.clearCompareSelection() }) {
                            Text("Clear")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CompareSlotColumn(
                            slotLabel = "Version A",
                            item = itemA,
                            modifier = Modifier.weight(1f)
                        )
                        CompareSlotColumn(
                            slotLabel = "Version B",
                            item = itemB,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        if (filteredWork.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No Saved Optimizations Yet",
                    message = "Run the Thumbnail Scorer, Title Optimizer, Keyword Research, or Video Audit—your results will appear here for comparison and export."
                )
            }
        } else {
            items(filteredWork, key = { it.id }) { item ->
                val isSelectedForCompare =
                    uiState.selectedCompareItemA?.id == item.id || uiState.selectedCompareItemB?.id == item.id

                GlassCard(
                    accentBorderColor = if (isSelectedForCompare) ZedViolet else null,
                    onClick = { viewModel.openDetail(DetailDestination.SavedWorkDetail(item)) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DataBadge(text = item.workType, isAi = item.workType != WorkType.KEYWORD_RESEARCH)
                        if (item.score > 0) {
                            DataBadge(
                                text = "Score: ${item.score}/100",
                                isAi = true,
                                colorOverride = scoreColor(item.score)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.toggleSelectForCompare(item) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.CompareArrows, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (isSelectedForCompare) "Selected" else "Compare",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Row {
                            IconButton(
                                onClick = {
                                    val exportText = "${item.title}\n${item.subtitle}\n\n${item.payloadJson}"
                                    clipboard.setText(AnnotatedString(exportText))
                                    viewModel.showToast("Copied saved item!")
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = ZedCyan)
                            }
                            IconButton(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "ZEDCraft: ${item.title}")
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "ZEDCraft (${item.workType}) — ${item.title}\n${item.subtitle}\n\n${item.payloadJson}"
                                        )
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Export ZEDCraft Work"))
                                }
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Export/Share")
                            }
                            IconButton(onClick = { viewModel.deleteSavedWorkItem(item) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
        }

        // 3. App Settings, Setup Guide, Cache & Privacy
        item {
            SectionHeader(
                title = "Settings, API Setup & Privacy",
                subtitle = "Configure theme, inspect API keys, manage offline cache, or delete data."
            )
        }

        item {
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (uiState.isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = null,
                            tint = ZedCyan
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Dark Studio Theme",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Switch between Dark Studio and Daylight mode",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = uiState.isDarkTheme,
                        onCheckedChange = { viewModel.toggleDarkTheme() },
                        modifier = Modifier.testTag("theme_switch")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(12.dp))

                // App Updates Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.openDetail(DetailDestination.GitHubUpdater) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.SystemUpdateAlt, contentDescription = null, tint = ZedCyan)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "App Updates",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Installed v${BuildConfig.VERSION_NAME} • Official Release Channel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Button(
                        onClick = {
                            viewModel.openDetail(DetailDestination.GitHubUpdater)
                            viewModel.checkForGitHubUpdates()
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("profile_check_updates_button")
                    ) {
                        Text("Check Updates", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Setup Guide Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.openDetail(DetailDestination.SetupGuide) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Build, contentDescription = null, tint = ZedCyan)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "YouTube API & OAuth Setup Guide",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (uiState.isYouTubeApiKeyConfigured) {
                                "YOUTUBE_API_KEY configured • Tap for Google Cloud & OAuth details"
                            } else {
                                "YOUTUBE_API_KEY not detected — Tap for step-by-step setup instructions"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.isYouTubeApiKeyConfigured) ZedSuccess else ZedWarning
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Privacy Policy & YouTube Terms Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.openDetail(DetailDestination.PrivacyAndTerms) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Policy, contentDescription = null, tint = ZedViolet)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Privacy Policy & YouTube API Terms",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Read-only scope disclosures, Google Privacy Policy & data transparency",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Local Cache Management
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Storage, contentDescription = null, tint = ZedCyan)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Local YouTube Quota Cache",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$cachedItemsCount API responses cached locally to save YouTube quota",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    OutlinedButton(onClick = { viewModel.clearLocalApiCache() }) {
                        Text("Clear Cache")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = { showDeleteAllDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ZedError.copy(alpha = 0.18f),
                        contentColor = ZedError
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete All Stored Data (Cloud + Local)", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CompareSlotColumn(
    slotLabel: String,
    item: SavedWorkItem?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = slotLabel,
                style = MaterialTheme.typography.labelSmall,
                color = ZedViolet,
                fontWeight = FontWeight.Bold
            )
            if (item == null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Tap 'Compare' on any saved card below",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Score: ${item.score}/100",
                    style = MaterialTheme.typography.labelMedium,
                    color = scoreColor(item.score),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ============================================================================
// DETAIL SCREENS WITH BACKHANDLER
// ============================================================================

@Composable
private fun DetailTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("detail_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoAnalyzerAndAuditScreen(
    uiState: ZedCraftUiState,
    viewModel: ZedCraftViewModel
) {
    BackHandler { viewModel.closeDetail() }
    val clipboard = LocalClipboardManager.current

    Column(modifier = Modifier.fillMaxSize()) {
        DetailTopBar(
            title = "Video Analyzer & One-Tap SEO Audit",
            subtitle = "Real YouTube Data API v3 telemetry + ZEDCraft 6-Point Packaging Audit",
            onBack = { viewModel.closeDetail() }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                GlassCard {
                    OutlinedTextField(
                        value = uiState.analyzedVideoInput,
                        onValueChange = { viewModel.updateAnalyzedVideoInput(it) },
                        label = { Text("Paste any YouTube Video Link or 11-char Video ID") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("video_audit_url_input"),
                        shape = RoundedCornerShape(14.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.analyzeAndAuditVideo() },
                        enabled = !uiState.isAnalyzingVideo,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("run_video_audit_btn"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Run Real Video Analyzer & SEO Audit", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (uiState.isAnalyzingVideo) {
                item {
                    LoadingBanner(message = "Fetching video snippet, statistics, contentDetails & running SEO checks...")
                }
            }

            uiState.videoAnalyzerError?.let { err ->
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

            val video = uiState.analyzedVideo
            val audit = uiState.videoAuditResult
            if (video != null && audit != null) {
                item {
                    GlassCard(accentBorderColor = ZedCyan.copy(alpha = 0.6f)) {
                        AsyncImage(
                            model = video.highResThumbnailUrl.ifBlank { video.thumbnailUrl },
                            contentDescription = video.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                                .clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = video.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${video.channelTitle} • Published ${video.publishedAt.take(10)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            CompetitorStatPill("Views", YouTubeApiClient.formatCompact(video.viewCount))
                            CompetitorStatPill("Likes", YouTubeApiClient.formatCompact(video.likeCount))
                            CompetitorStatPill("Comments", YouTubeApiClient.formatCompact(video.commentCount))
                            CompetitorStatPill(
                                "Engagement",
                                String.format(Locale.US, "%.2f%%", video.engagementRatePercent)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.selectOptimizeSubTab(0)
                                    viewModel.loadThumbnailFromVideoUrlOrDetail(video.videoId, video.title)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Vision Score Thumbnail")
                            }
                            OutlinedButton(
                                onClick = {
                                    viewModel.selectOptimizeSubTab(1)
                                    viewModel.updateTitleInputs(video.title, video.channelTitle)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Title, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Optimize Title")
                            }
                        }
                    }
                }

                // Overall Audit Score & Prioritized To-Do List
                item {
                    GlassCard(accentBorderColor = ZedViolet.copy(alpha = 0.55f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AnimatedScoreRing(
                                score = audit.zedCraftScore,
                                label = "ZEDCraft Audit Score",
                                isAiEstimate = false
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                DataBadge(text = "ZEDCraft Computed Score", isAi = false)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = audit.packagingSynergyNote,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Prioritized Action Checklist:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        audit.prioritizedTodoList.forEachIndexed { idx, todo ->
                            Text(
                                text = "${idx + 1}. $todo",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 3.dp)
                            )
                        }
                    }
                }

                // 6 Individual Audit Check Cards
                items(audit.checks) { check ->
                    GlassCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = check.category,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            StatusPill(status = if (check.passed) "PASSED" else check.priority)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        SubScoreBar(
                            label = check.currentStatus,
                            score = check.score,
                            note = check.actionableFix
                        )
                    }
                }

                // Video Public Tags
                if (video.tags.isNotEmpty()) {
                    item {
                        GlassCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Extracted Video Tags (${video.tags.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                OutlinedButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(video.tags.joinToString(", ")))
                                        viewModel.showToast("Copied all ${video.tags.size} video tags!")
                                    }
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy Tags")
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                video.tags.forEach { tag ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = tag,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompetitorStatPill(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = ZedCyan
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ChannelHealthCheckScreen(
    uiState: ZedCraftUiState,
    viewModel: ZedCraftViewModel
) {
    BackHandler { viewModel.closeDetail() }
    val report = uiState.channelHealthReport

    Column(modifier = Modifier.fillMaxSize()) {
        DetailTopBar(
            title = "Channel Health & Policy Risk Check",
            subtitle = "Data-backed diagnostics + YouTube Community & Monetization Policy checks",
            onBack = { viewModel.closeDetail() }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (report == null) {
                item {
                    EmptyStateCard(
                        title = "No Channel Connected Yet",
                        message = "Connect your YouTube channel from the Home or Profile tab to generate a complete Channel Health & Policy Risk Report.",
                        actionLabel = "Go to Home",
                        onAction = { viewModel.closeDetail() }
                    )
                }
            } else {
                item {
                    GlassCard(accentBorderColor = ZedCyan.copy(alpha = 0.6f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AnimatedScoreRing(
                                score = report.overallHealthScore,
                                label = "Channel Health",
                                isAiEstimate = false
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = report.channel.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Uploads/mo: ${report.uploadsPerMonth} • Shorts Mix: ${report.shortsRatioPercent}% • Avg Eng: ${report.avgEngagementPercent}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                DataBadge(text = "Best Schedule: ${report.bestUploadSchedule}", isAi = false)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        SubScoreBar("Upload Consistency", report.uploadConsistencyScore)
                        SubScoreBar("CTR & Packaging Momentum", report.ctrAndPackagingScore)
                        SubScoreBar("Retention & Duration Depth", report.retentionAndDurationScore)
                        SubScoreBar("Engagement & Audience Loyalty", report.engagementLoyaltyScore)
                        SubScoreBar("Shorts vs Long-Form Content Mix", report.contentMixScore)
                    }
                }

                // Data-Backed Drop Diagnosis (No "shadowban" myths)
                item {
                    GlassCard(accentBorderColor = ZedViolet.copy(alpha = 0.55f)) {
                        SectionHeader(
                            title = "Real Data View-Drop & Reach Diagnosis",
                            subtitle = "Why reach changes based on real YouTube discovery signals",
                            badgeText = "Honest Telemetry",
                            isAiBadge = false
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = report.dropDiagnosisSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Policy & Monetization Risk Checks
                item {
                    SectionHeader(
                        title = "YouTube Policy & Monetization Risk Checks",
                        subtitle = "Proactive scan for Repetitious Content, Metadata Spam, AI Disclosure, COPPA, and Copyright."
                    )
                }

                items(report.policyChecks, key = { it.id }) { policy ->
                    GlassCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = policy.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            StatusPill(status = policy.status)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = policy.observation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "How to stay compliant: ${policy.howToFix}",
                            style = MaterialTheme.typography.bodySmall,
                            color = ZedCyan
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Reference: ${policy.policyReference}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Underperforming Videos & Fix Suggestions
                if (report.underperformingVideos.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Underperforming Uploads & Recovery Fixes",
                            subtitle = "Tap any video to open a 1-Tap SEO Audit or rewrite its packaging."
                        )
                    }
                    items(report.underperformingVideos, key = { it.first.videoId }) { (video, fixNote) ->
                        GlassCard(
                            onClick = {
                                viewModel.openDetail(DetailDestination.VideoAnalyzerAndAudit(video.videoId))
                            }
                        ) {
                            Text(
                                text = video.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${YouTubeApiClient.formatCompact(video.viewCount)} views • Published ${video.publishedAt.take(10)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Recovery Action: $fixNote",
                                style = MaterialTheme.typography.bodySmall,
                                color = ZedWarning
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SetupGuideScreen(
    uiState: ZedCraftUiState,
    viewModel: ZedCraftViewModel
) {
    BackHandler { viewModel.closeDetail() }

    Column(modifier = Modifier.fillMaxSize()) {
        DetailTopBar(
            title = "ZEDCraft Setup & API Configuration",
            subtitle = "Configure YouTube Data API v3, Analytics API, OAuth 2.0 & Gemini AI",
            onBack = { viewModel.closeDetail() }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                GlassCard(accentBorderColor = ZedCyan) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Live Environment Status",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        StatusPill(status = if (uiState.isYouTubeApiKeyConfigured) "PASSED" else "REVIEW")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    SetupCheckRow(
                        label = "YouTube Data API v3 Key (YOUTUBE_API_KEY)",
                        isReady = uiState.isYouTubeApiKeyConfigured,
                        detail = if (uiState.isYouTubeApiKeyConfigured) {
                            "Injected via BuildConfig from AI Studio Secrets"
                        } else {
                            "Add YOUTUBE_API_KEY in the AI Studio Secrets panel to unlock live YouTube Data API v3 requests"
                        }
                    )
                    SetupCheckRow(
                        label = "ZD Main AI Model (${ZedCraftAiConfig.ZD_MAIN_MODEL})",
                        isReady = true,
                        detail = "Provisioned via Firebase AI Logic with automatic fallback to ${ZedCraftAiConfig.FAST_MODEL}"
                    )
                    SetupCheckRow(
                        label = "ZEDCraft Fast Tool Model (${ZedCraftAiConfig.FAST_MODEL})",
                        isReady = true,
                        detail = "Active for Multimodal Thumbnail Vision, Title Optimizer, Description & Tag tools"
                    )
                    SetupCheckRow(
                        label = "Google OAuth 2.0 Read-Only Scopes",
                        isReady = uiState.oauthAccessToken != null,
                        detail = "Scopes: youtube.readonly & yt-analytics.readonly (Never requests write access)"
                    )
                }
            }

            item {
                GlassCard {
                    Text(
                        text = "Step 1: Add YOUTUBE_API_KEY in AI Studio Secrets",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "1. Open Google Cloud Console -> APIs & Services -> Library.\n" +
                            "2. Enable 'YouTube Data API v3' and 'YouTube Analytics API'.\n" +
                            "3. Go to Credentials -> Create Credentials -> API Key.\n" +
                            "4. In AI Studio, open the Secrets panel and set YOUTUBE_API_KEY to your key.\n" +
                            "5. All responses are automatically cached in Room SQLite to protect your daily 10,000-unit quota.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            item {
                GlassCard {
                    Text(
                        text = "Step 2: Connect Your Channel via Google OAuth 2.0",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "1. In Google Cloud Console -> OAuth consent screen, add the read-only scopes:\n" +
                            "   • https://www.googleapis.com/auth/youtube.readonly\n" +
                            "   • https://www.googleapis.com/auth/yt-analytics.readonly\n" +
                            "2. Tap 'Connect YouTube Channel' in ZEDCraft to sign in with Google and grant read-only access to your channel's own YouTube Analytics reports.\n" +
                            "3. You can also use every public tool (Thumbnail Scorer, Title Optimizer, Description & Chapters, Tags, Keyword Research, and ZD AI) immediately without connecting a channel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupCheckRow(
    label: String,
    isReady: Boolean,
    detail: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = if (isReady) ZedSuccess else ZedWarning,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PrivacyAndTermsScreen(
    viewModel: ZedCraftViewModel
) {
    BackHandler { viewModel.closeDetail() }

    Column(modifier = Modifier.fillMaxSize()) {
        DetailTopBar(
            title = "Privacy Policy & YouTube API Terms",
            subtitle = "Google User Data Policy, Read-Only Scopes & AI Transparency",
            onBack = { viewModel.closeDetail() }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                GlassCard {
                    Text(
                        text = "1. YouTube API Services Terms of Service",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = ZedCyan
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "By using ZEDCraft to connect or analyze YouTube channels and videos, you agree to be bound by the YouTube Terms of Service (https://www.youtube.com/t/terms) and the Google Privacy Policy (https://policies.google.com/privacy).",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            item {
                GlassCard {
                    Text(
                        text = "2. Strict Read-Only OAuth Scope Guarantee",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = ZedCyan
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "ZEDCraft requests ONLY read-only scopes (youtube.readonly and yt-analytics.readonly) when you choose to connect your channel. ZEDCraft never modifies, uploads, or deletes videos on your YouTube channel, and never sells or shares your channel data with third parties.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            item {
                GlassCard {
                    Text(
                        text = "3. Honest Telemetry vs. AI Estimate Labeling",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = ZedCyan
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• Real YouTube Data & Analytics Metrics (views, subscribers, likes, comments, watch time, average view duration, traffic sources) are labeled with 'YouTube Data API v3' or 'YouTube Analytics API'.\n" +
                            "• ZEDCraft Opportunity & Audit scores are labeled 'ZEDCraft Computed Score' with their formula explained.\n" +
                            "• Gemini Vision and language model evaluations are clearly labeled 'AI Estimate' and are never presented as official YouTube metrics.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            item {
                GlassCard {
                    Text(
                        text = "4. Revoking Access & Deleting Stored Data",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = ZedCyan
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "You can disconnect your YouTube channel and permanently delete all cloud and local data at any time from Profile -> 'Delete All Stored Data (Cloud + Local)', or revoke access via Google Account Security settings (https://security.google.com/settings/security/permissions).",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun SavedWorkDetailScreen(
    item: SavedWorkItem,
    viewModel: ZedCraftViewModel
) {
    BackHandler { viewModel.closeDetail() }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        DetailTopBar(
            title = item.title,
            subtitle = "${item.workType} • Score: ${item.score}/100",
            onBack = { viewModel.closeDetail() }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                GlassCard(accentBorderColor = ZedCyan) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DataBadge(text = item.workType, isAi = true)
                        DataBadge(
                            text = "Score: ${item.score}/100",
                            isAi = true,
                            colorOverride = scoreColor(item.score)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Saved Details:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.payloadJson,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val full = "${item.title}\n${item.subtitle}\n\n${item.payloadJson}"
                                clipboard.setText(AnnotatedString(full))
                                viewModel.showToast("Copied to clipboard!")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy All")
                        }
                        OutlinedButton(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "ZEDCraft: ${item.title}")
                                    putExtra(Intent.EXTRA_TEXT, "${item.title}\n${item.subtitle}\n\n${item.payloadJson}")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share ZEDCraft Item"))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GitHubUpdaterScreen(
    uiState: ZedCraftUiState,
    viewModel: ZedCraftViewModel
) {
    BackHandler { viewModel.closeDetail() }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        DetailTopBar(
            title = "Software Update",
            subtitle = "Official ZEDCraft Release Channel",
            onBack = { viewModel.closeDetail() }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                GlassCard(accentBorderColor = ZedCyan.copy(alpha = 0.6f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "ZEDCraft v${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Official Update Channel",
                                style = MaterialTheme.typography.bodySmall,
                                color = ZedCyan
                            )
                        }
                        DataBadge(text = "Verified Build", isAi = false)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { viewModel.checkForGitHubUpdates() },
                        enabled = !uiState.isCheckingGitHubUpdate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("check_github_updates_btn"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.SystemUpdateAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Check for Updates", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (uiState.isCheckingGitHubUpdate) {
                item {
                    LoadingBanner(message = "Checking for the latest ZEDCraft update...")
                }
            }

            uiState.githubUpdateError?.let { err ->
                item { ErrorBanner(message = err) }
            }

            val info = uiState.githubUpdateInfo
            if (info != null) {
                item {
                    GlassCard(
                        accentBorderColor = if (info.isUpdateAvailable) ZedSuccess else ZedViolet.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = info.releaseTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            StatusPill(status = if (info.isUpdateAvailable) "UPDATE" else "PASSED")
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = info.statusSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (info.isUpdateAvailable) ZedSuccess else MaterialTheme.colorScheme.onSurface
                        )
                        if (info.publishedAt.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Release Date: ${info.publishedAt} • Version: ${info.latestVersionTag}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = info.releaseNotes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!info.apkDownloadUrl.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.apkDownloadUrl))
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Download & Install Update")
                            }
                        }
                    }
                }
            }
        }
    }
}

