package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ai.ThumbnailVisionResponse
import com.example.ai.TitleOptimizerResponse
import com.example.ai.TitleSuggestionItem
import com.example.data.local.VideoRevisionBackupEntity
import com.example.data.remote.YouTubeApiClient
import com.example.data.remote.YouTubeWriteOutcome
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
import com.example.ui.viewmodel.OptimizeSubTab
import com.example.ui.viewmodel.ZedCraftUiState
import com.example.ui.viewmodel.ZedCraftViewModel

private sealed interface PendingYouTubeWriteAction {
    data class ApplyTitle(val video: YtVideoDetail, val newTitle: String) : PendingYouTubeWriteAction
    data class ApplyDescription(
        val video: YtVideoDetail,
        val newDescription: String,
        val includeTags: List<String>? = null
    ) : PendingYouTubeWriteAction
    data class ApplyTags(val video: YtVideoDetail, val newTags: List<String>) : PendingYouTubeWriteAction
    data class ApplyThumbnail(val video: YtVideoDetail) : PendingYouTubeWriteAction
    data class UndoRestore(val video: YtVideoDetail, val backup: VideoRevisionBackupEntity) : PendingYouTubeWriteAction
}

@Composable
fun OptimizeScreen(
    uiState: ZedCraftUiState,
    viewModel: ZedCraftViewModel
) {
    val context = LocalContext.current
    val backups by viewModel.videoRevisionBackups.collectAsState()
    var pendingWriteAction by remember { mutableStateOf<PendingYouTubeWriteAction?>(null) }

    val selectedVideo = uiState.selectedTargetVideo
    val activeVideoBackup = remember(selectedVideo, backups) {
        selectedVideo?.let { vid -> backups.firstOrNull { it.videoId == vid.videoId } }
    }

    val launchOAuth = rememberYouTubeOAuthLauncher(
        onAuthorized = { token -> viewModel.connectWithOAuthToken(token) },
        onError = { err -> viewModel.setOAuthError(err) }
    )

    // Confirmation Dialog: "This will change your live video on YouTube."
    pendingWriteAction?.let { action ->
        LiveYouTubeWriteConfirmDialog(
            action = action,
            onConfirm = {
                pendingWriteAction = null
                when (action) {
                    is PendingYouTubeWriteAction.ApplyTitle ->
                        viewModel.applyTitleToYouTube(action.video.videoId, action.newTitle)
                    is PendingYouTubeWriteAction.ApplyDescription ->
                        viewModel.applyDescriptionToYouTube(
                            action.video.videoId,
                            action.newDescription,
                            action.includeTags
                        )
                    is PendingYouTubeWriteAction.ApplyTags ->
                        viewModel.applyTagsToYouTube(action.video.videoId, action.newTags)
                    is PendingYouTubeWriteAction.ApplyThumbnail ->
                        viewModel.applyThumbnailToYouTube(action.video.videoId)
                    is PendingYouTubeWriteAction.UndoRestore ->
                        viewModel.undoLastVideoChange(action.video.videoId)
                }
            },
            onDismiss = { pendingWriteAction = null }
        )
    }

    // Friendly Phone Verification Dialog if YouTube rejects custom thumbnail upload
    if (uiState.showPhoneVerifyDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.clearApplyMessages() },
            containerColor = ZedDarkSurfaceElevated,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = ZedAmberWarning
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Channel Verification Needed",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            text = {
                Text(
                    text = "YouTube requires your channel to be verified by phone before uploading custom thumbnails. Visit youtube.com/verify to complete verification in 1 minute, then try again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ZedTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearApplyMessages()
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/verify"))
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ZedCyanPrimary,
                        contentColor = ZedDarkBackground
                    )
                ) {
                    Text("Open youtube.com/verify", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearApplyMessages() }) {
                    Text("Close", color = ZedTextSecondary)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ZedDarkBackground)
    ) {
        // Fixed Sub-Tab Pill Bar
        OptimizeSubTabRow(
            selected = uiState.selectedOptimizeSubTab,
            onSelect = { viewModel.selectOptimizeSubTab(it) }
        )

        // Scrollable Content Area for Active Sub-Tab
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Target Video Picker ("My Videos") + Undo Restore Header
            item {
                TargetYouTubeVideoSelectorCard(
                    videos = uiState.activeChannelVideos,
                    selectedVideo = selectedVideo,
                    isOAuthConnected = !uiState.oauthAccessToken.isNullOrBlank(),
                    backup = activeVideoBackup,
                    isApplying = uiState.isApplyingToYouTube,
                    onSelectVideo = { vid ->
                        viewModel.selectTargetVideoForOptimize(
                            video = vid,
                            subTab = uiState.selectedOptimizeSubTab,
                            navigateToOptimize = false,
                            autoRunOptimizer = false
                        )
                    },
                    onConnectYouTube = launchOAuth,
                    onRequestUndo = { vid, bkp ->
                        pendingWriteAction = PendingYouTubeWriteAction.UndoRestore(vid, bkp)
                    }
                )
            }

            // Live Apply Status Banner (Success / Applying / Error)
            if (uiState.isApplyingToYouTube || uiState.applySuccessOutcome != null || !uiState.applyError.isNullOrBlank()) {
                item {
                    LiveYouTubeApplyStatusCard(
                        isApplying = uiState.isApplyingToYouTube,
                        outcome = uiState.applySuccessOutcome,
                        errorMessage = uiState.applyError,
                        hasBackup = activeVideoBackup != null && selectedVideo != null,
                        onViewOnYouTube = { url ->
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        },
                        onUndo = {
                            if (selectedVideo != null && activeVideoBackup != null) {
                                pendingWriteAction = PendingYouTubeWriteAction.UndoRestore(selectedVideo, activeVideoBackup)
                            }
                        },
                        onDismiss = { viewModel.clearApplyMessages() }
                    )
                }
            }

            when (uiState.selectedOptimizeSubTab) {
                OptimizeSubTab.Title -> {
                    item {
                        TitleOptimizerSection(
                            uiState = uiState,
                            viewModel = viewModel,
                            context = context,
                            onRequestApplyTitle = { video, newTitle ->
                                pendingWriteAction = PendingYouTubeWriteAction.ApplyTitle(video, newTitle)
                            }
                        )
                    }
                }
                OptimizeSubTab.Description -> {
                    item {
                        DescriptionOptimizerSection(
                            uiState = uiState,
                            viewModel = viewModel,
                            context = context,
                            onRequestApplyDescription = { video, newDesc, tags ->
                                pendingWriteAction = PendingYouTubeWriteAction.ApplyDescription(video, newDesc, tags)
                            }
                        )
                    }
                }
                OptimizeSubTab.Tags -> {
                    item {
                        TagsAndHashtagsOptimizerSection(
                            uiState = uiState,
                            viewModel = viewModel,
                            context = context,
                            onRequestApplyTags = { video, newTags ->
                                pendingWriteAction = PendingYouTubeWriteAction.ApplyTags(video, newTags)
                            }
                        )
                    }
                }
                OptimizeSubTab.Thumbnail -> {
                    item {
                        ThumbnailVisionSection(
                            uiState = uiState,
                            viewModel = viewModel,
                            onRequestApplyThumbnail = { video ->
                                pendingWriteAction = PendingYouTubeWriteAction.ApplyThumbnail(video)
                            }
                        )
                    }
                }
                OptimizeSubTab.ScriptAndHook -> {
                    item {
                        ScriptAndHookSection(uiState = uiState, viewModel = viewModel, context = context)
                    }
                }
                OptimizeSubTab.ShortsAndRemix -> {
                    item {
                        ShortsRepurposeSection(uiState = uiState, viewModel = viewModel, context = context)
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveYouTubeWriteConfirmDialog(
    action: PendingYouTubeWriteAction,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val detailText = when (action) {
        is PendingYouTubeWriteAction.ApplyTitle ->
            "Video: \"${action.video.title}\"\n\nNew Title:\n\"${action.newTitle}\"\n\nYour previous title, description, and tags will be backed up locally so you can undo anytime."
        is PendingYouTubeWriteAction.ApplyDescription ->
            "Video: \"${action.video.title}\"\n\nThis will update your video's description${if (action.includeTags != null) " and tags" else ""} on YouTube while keeping your title, category, and language settings intact."
        is PendingYouTubeWriteAction.ApplyTags ->
            "Video: \"${action.video.title}\"\n\nThis will update your video's tags (${action.newTags.size} tags) on YouTube while keeping your title and description intact."
        is PendingYouTubeWriteAction.ApplyThumbnail ->
            "Video: \"${action.video.title}\"\n\nThis will upload and set your selected 16:9 image as the live custom thumbnail on YouTube."
        is PendingYouTubeWriteAction.UndoRestore ->
            "Restore \"${action.video.title}\" back to its previous title, description, and tags saved before your last edit?"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ZedDarkSurfaceElevated,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = ZedCyanPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "This will change your live video on YouTube.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        text = {
            Text(
                text = detailText,
                style = MaterialTheme.typography.bodySmall,
                color = ZedTextSecondary
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm_apply_youtube_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ZedCyanPrimary,
                    contentColor = ZedDarkBackground
                )
            ) {
                Text("Confirm & Apply to YouTube", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = ZedTextSecondary)
            }
        }
    )
}

@Composable
private fun TargetYouTubeVideoSelectorCard(
    videos: List<YtVideoDetail>,
    selectedVideo: YtVideoDetail?,
    isOAuthConnected: Boolean,
    backup: VideoRevisionBackupEntity?,
    isApplying: Boolean,
    onSelectVideo: (YtVideoDetail) -> Unit,
    onConnectYouTube: () -> Unit,
    onRequestUndo: (YtVideoDetail, VideoRevisionBackupEntity) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
        border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.38f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = null,
                        tint = ZedCyanPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (videos.isNotEmpty()) {
                            "My Videos • Pick a Video to Optimize"
                        } else {
                            "Connect Channel to Apply Changes to Your Videos"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (selectedVideo != null && backup != null && isOAuthConnected) {
                    OutlinedButton(
                        onClick = { onRequestUndo(selectedVideo, backup) },
                        enabled = !isApplying,
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("undo_restore_previous_button"),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ZedAmberWarning.copy(alpha = 0.65f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Undo / Restore previous",
                            tint = ZedAmberWarning,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Undo / Restore previous",
                            style = MaterialTheme.typography.labelSmall,
                            color = ZedAmberWarning,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (videos.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Sign in with your YouTube channel to load your uploaded videos and apply titles, descriptions, tags & thumbnails with 1 tap.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ZedTextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = onConnectYouTube,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ZedCyanPrimary,
                            contentColor = ZedDarkBackground
                        )
                    ) {
                        Text("Connect Channel", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(videos.take(20), key = { "picker_${it.videoId}" }) { vid ->
                        val isSelected = selectedVideo?.videoId == vid.videoId
                        Surface(
                            modifier = Modifier
                                .width(220.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelectVideo(vid) }
                                .testTag("optimize_pick_video_${vid.videoId}"),
                            color = if (isSelected) ZedCyanPrimary.copy(alpha = 0.16f) else ZedDarkSurfaceElevated,
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) ZedCyanPrimary else ZedCardBorder
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = vid.thumbnailUrl,
                                    contentDescription = vid.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(68.dp)
                                        .height(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = vid.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${YouTubeApiClient.formatCompact(vid.viewCount)} views • Score ${vid.quickSeoScore}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) ZedCyanPrimary else ZedTextSecondary,
                                        maxLines = 1
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

@Composable
private fun LiveYouTubeApplyStatusCard(
    isApplying: Boolean,
    outcome: YouTubeWriteOutcome?,
    errorMessage: String?,
    hasBackup: Boolean,
    onViewOnYouTube: (String) -> Unit,
    onUndo: () -> Unit,
    onDismiss: () -> Unit
) {
    if (isApplying) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
            border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.5.dp,
                    color = ZedCyanPrimary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Backing up current snippet & applying update to your live YouTube video...",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    } else if (outcome != null) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("youtube_apply_success_card"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
            border = BorderStroke(1.dp, ZedEmeraldSuccess.copy(alpha = 0.65f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = ZedEmeraldSuccess,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${outcome.updatedField} Updated Live on YouTube!",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = ZedEmeraldSuccess
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Dismiss", style = MaterialTheme.typography.labelSmall, color = ZedTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onViewOnYouTube(outcome.youtubeWatchUrl) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("view_updated_video_on_youtube_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ZedEmeraldSuccess,
                            contentColor = ZedDarkBackground
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("View on YouTube", fontWeight = FontWeight.Bold)
                    }

                    if (hasBackup) {
                        OutlinedButton(
                            onClick = onUndo,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ZedAmberWarning.copy(alpha = 0.6f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = ZedAmberWarning,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Undo / Restore", color = ZedAmberWarning, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    } else if (!errorMessage.isNullOrBlank()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ZedCoralError.copy(alpha = 0.14f)),
            border = BorderStroke(1.dp, ZedCoralError.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = ZedCoralError,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFFB4AB),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun OptimizeSubTabRow(
    selected: OptimizeSubTab,
    onSelect: (OptimizeSubTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ZedGlassSurface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OptimizeSubTab.entries.forEach { tab ->
            val isSelected = tab == selected
            val icon: ImageVector = when (tab) {
                OptimizeSubTab.Title -> Icons.Default.Title
                OptimizeSubTab.Description -> Icons.Default.Description
                OptimizeSubTab.Tags -> Icons.Default.Tag
                OptimizeSubTab.Thumbnail -> Icons.Default.Image
                OptimizeSubTab.ScriptAndHook -> Icons.Default.MovieCreation
                OptimizeSubTab.ShortsAndRemix -> Icons.Default.PlayCircleFilled
            }
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelect(tab) }
                    .testTag("optimize_subtab_${tab.name.lowercase()}"),
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

@Composable
private fun BeforeAfterPreviewCard(
    fieldLabel: String,
    beforeText: String,
    afterText: String,
    canApplyLive: Boolean,
    isApplying: Boolean,
    onApplyToYouTube: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("before_after_preview_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = ZedDarkSurfaceElevated),
        border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.55f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                    contentDescription = null,
                    tint = ZedCyanPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Before / After Preview ($fieldLabel)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = ZedCyanPrimary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                color = ZedDarkBackground.copy(alpha = 0.65f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ZedCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "CURRENT LIVE ON YOUTUBE",
                        style = MaterialTheme.typography.labelSmall,
                        color = ZedTextMuted,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = beforeText.ifBlank { "(Empty)" },
                        style = MaterialTheme.typography.bodySmall,
                        color = ZedTextSecondary,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = ZedEmeraldSuccess.copy(alpha = 0.10f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ZedEmeraldSuccess.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "NEW OPTIMIZED VERSION",
                        style = MaterialTheme.typography.labelSmall,
                        color = ZedEmeraldSuccess,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = afterText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onApplyToYouTube,
                enabled = canApplyLive && !isApplying && afterText.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("apply_to_youtube_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ZedEmeraldSuccess,
                    contentColor = ZedDarkBackground
                )
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (canApplyLive) {
                        "Apply to YouTube"
                    } else {
                        "Connect YouTube Channel to Apply Live"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SmartSuggestionsLoadingSkeleton(label: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
        border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = ZedCyanPrimary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = ZedCyanPrimary
                )
            }
            repeat(3) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ZedDarkSurfaceElevated.copy(alpha = 0.7f))
                )
            }
        }
    }
}

@Composable
private fun DataSourceAndPatternsCard(res: TitleOptimizerResponse) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ZedDarkSurfaceElevated),
        border = BorderStroke(
            1.dp,
            if (res.isBackedByLiveYouTubeData) ZedEmeraldSuccess.copy(alpha = 0.45f)
            else ZedAmberWarning.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (res.isBackedByLiveYouTubeData) {
                        ZedEmeraldSuccess.copy(alpha = 0.16f)
                    } else {
                        ZedAmberWarning.copy(alpha = 0.16f)
                    }
                ) {
                    Text(
                        text = res.dataSourceBadge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (res.isBackedByLiveYouTubeData) ZedEmeraldSuccess else ZedAmberWarning,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = if (res.isBackedByLiveYouTubeData) "Real YouTube API + AI Synthesis" else "AI Estimate Fallback",
                    style = MaterialTheme.typography.labelSmall,
                    color = ZedTextSecondary
                )
            }

            if (!res.quotaFallbackNotice.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = res.quotaFallbackNotice,
                    style = MaterialTheme.typography.bodySmall,
                    color = ZedAmberWarning
                )
            }

            if (res.searchPatternsSummary.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Winning Patterns Found in Top-Ranking Videos:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                res.searchPatternsSummary.take(4).forEach { pattern ->
                    Text(
                        text = "• $pattern",
                        style = MaterialTheme.typography.bodySmall,
                        color = ZedTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun TitleOptimizerSection(
    uiState: ZedCraftUiState,
    viewModel: ZedCraftViewModel,
    context: Context,
    onRequestApplyTitle: (YtVideoDetail, String) -> Unit
) {
    val targetVideo = uiState.selectedTargetVideo
    var selectedSuggestedTitle by remember(uiState.titleOptimizerResult) {
        mutableStateOf(uiState.titleOptimizerResult?.suggestedTitles?.firstOrNull()?.title)
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
                    text = "Score & Optimize Video Title",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Scores your current title out of 100, pulls real top-ranking YouTube videos for your topic, and generates 10 higher-CTR title suggestions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ZedTextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.optimizeTitleInput,
                    onValueChange = {
                        viewModel.updateOptimizeInputs(title = it, topic = it)
                    },
                    label = { Text("Current Video Title or Topic") },
                    placeholder = { Text("Pick a video above or type your title...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("optimize_title_input"),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ZedCyanPrimary,
                        unfocusedBorderColor = ZedCardBorder
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = uiState.optimizeNicheInput,
                    onValueChange = { viewModel.updateOptimizeInputs(niche = it) },
                    label = { Text("Channel Niche / Target Audience") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ZedCyanPrimary,
                        unfocusedBorderColor = ZedCardBorder
                    )
                )

                if (!uiState.optimizeError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.optimizeError,
                        style = MaterialTheme.typography.bodySmall,
                        color = ZedCoralError
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = { viewModel.runTitleDescTagsOptimizer(forceRefreshSearch = false) },
                    enabled = !uiState.isOptimizing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("run_title_optimizer_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ZedCyanPrimary,
                        contentColor = ZedDarkBackground
                    )
                ) {
                    if (uiState.isOptimizing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = ZedDarkBackground
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Analyzing Live YouTube Data & Scoring...")
                    } else {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Score & Get 10 Smart Title Suggestions", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (uiState.isOptimizing) {
            SmartSuggestionsLoadingSkeleton(
                label = "Scanning top-ranking YouTube results & building 10 smart title suggestions..."
            )
        }

        val res = uiState.titleOptimizerResult
        if (res != null) {
            // Current Title Score Breakdown Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
                border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Current Title Score",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${res.originalScore}/100",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (res.originalScore >= 75) ZedEmeraldSuccess else ZedAmberWarning
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = res.originalDiagnosis,
                        style = MaterialTheme.typography.bodySmall,
                        color = ZedTextSecondary
                    )
                }
            }

            DataSourceAndPatternsCard(res = res)

            // Before / After Preview Card for the Selected Suggested Title
            val previewTitle = selectedSuggestedTitle ?: res.suggestedTitles.firstOrNull()?.title.orEmpty()
            if (previewTitle.isNotBlank()) {
                BeforeAfterPreviewCard(
                    fieldLabel = "Video Title",
                    beforeText = targetVideo?.title ?: uiState.optimizeTitleInput,
                    afterText = previewTitle,
                    canApplyLive = targetVideo != null && !uiState.oauthAccessToken.isNullOrBlank(),
                    isApplying = uiState.isApplyingToYouTube,
                    onApplyToYouTube = {
                        if (targetVideo != null) {
                            onRequestApplyTitle(targetVideo, previewTitle)
                        }
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Better Title Suggestions (${res.suggestedTitles.size} Options)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Each option includes score, search/mobile preview, and cutoff check.",
                        style = MaterialTheme.typography.labelSmall,
                        color = ZedTextSecondary
                    )
                }
                OutlinedButton(
                    onClick = { viewModel.runTitleDescTagsOptimizer(forceRefreshSearch = true) },
                    enabled = !uiState.isOptimizing,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Regenerate Titles",
                        tint = ZedCyanPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Regenerate", style = MaterialTheme.typography.labelSmall, color = ZedCyanPrimary)
                }
            }

            res.suggestedTitles.forEachIndexed { idx, item ->
                val isSelected = selectedSuggestedTitle == item.title
                TitleSuggestionCard(
                    index = idx + 1,
                    item = item,
                    isSelected = isSelected,
                    canApplyLive = targetVideo != null && !uiState.oauthAccessToken.isNullOrBlank(),
                    onSelect = { selectedSuggestedTitle = item.title },
                    onUseThisTitle = {
                        selectedSuggestedTitle = item.title
                        viewModel.useSuggestedTitle(item.title)
                        Toast.makeText(context, "Loaded title into editor!", Toast.LENGTH_SHORT).show()
                    },
                    onApplyNow = {
                        selectedSuggestedTitle = item.title
                        if (targetVideo != null) {
                            onRequestApplyTitle(targetVideo, item.title)
                        }
                    },
                    onCopy = {
                        copyToClipboard(context, "YouTube Title", item.title)
                    },
                    onSave = {
                        viewModel.saveIdeaToVault(
                            category = "Title",
                            title = item.title,
                            content = "Tag: ${item.tagBadge} | Score: ${item.score}/100\nWhy it works: ${item.whyItWorks}",
                            tags = item.tagBadge
                        )
                        Toast.makeText(context, "Saved title to Vault!", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // Custom Title Scorer & Direct Apply Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
            border = BorderStroke(1.dp, ZedPurpleAccent.copy(alpha = 0.45f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Write & Score Your Own Title",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = ZedPurpleAccent
                )
                Text(
                    text = "Type your own custom title, get it scored out of 100 alongside 10 Better Title Suggestions below, or apply it directly to YouTube.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ZedTextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = uiState.customTitleDraft,
                    onValueChange = { viewModel.updateCustomTitleDraft(it) },
                    label = { Text("Your Custom Title (${uiState.customTitleDraft.length}/100 chars)") },
                    placeholder = { Text("Type your custom title idea here...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_title_draft_input"),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ZedPurpleAccent,
                        unfocusedBorderColor = ZedCardBorder
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.scoreCustomTitleDraft() },
                        enabled = !uiState.isScoringCustomTitle && uiState.customTitleDraft.trim().length >= 4,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("score_custom_title_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ZedPurpleAccent)
                    ) {
                        if (uiState.isScoringCustomTitle) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = ZedPurpleAccent
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scoring...", color = Color.White)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = ZedPurpleAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Score My Title", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = {
                            if (targetVideo != null && uiState.customTitleDraft.isNotBlank()) {
                                onRequestApplyTitle(targetVideo, uiState.customTitleDraft.trim())
                            }
                        },
                        enabled = targetVideo != null &&
                            !uiState.oauthAccessToken.isNullOrBlank() &&
                            uiState.customTitleDraft.trim().length >= 4 &&
                            !uiState.isApplyingToYouTube,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("apply_custom_title_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ZedEmeraldSuccess,
                            contentColor = ZedDarkBackground
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Apply to YouTube", fontWeight = FontWeight.Bold)
                    }
                }

                val customScore = uiState.customTitleScoreResult
                if (customScore != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    TitleSuggestionCard(
                        index = null,
                        item = customScore,
                        isSelected = true,
                        canApplyLive = targetVideo != null && !uiState.oauthAccessToken.isNullOrBlank(),
                        onSelect = {},
                        onUseThisTitle = {
                            viewModel.useSuggestedTitle(customScore.title)
                        },
                        onApplyNow = {
                            if (targetVideo != null) {
                                onRequestApplyTitle(targetVideo, customScore.title)
                            }
                        },
                        onCopy = { copyToClipboard(context, "Custom Title", customScore.title) },
                        onSave = {
                            viewModel.saveIdeaToVault(
                                category = "Title",
                                title = customScore.title,
                                content = "Score: ${customScore.score}/100\n${customScore.whyItWorks}",
                                tags = "Custom"
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TitleSuggestionCard(
    index: Int? = null,
    item: TitleSuggestionItem,
    isSelected: Boolean,
    canApplyLive: Boolean,
    onSelect: () -> Unit,
    onUseThisTitle: () -> Unit,
    onApplyNow: () -> Unit,
    onCopy: () -> Unit,
    onSave: () -> Unit
) {
    val tagColor = when {
        item.tagBadge.contains("Search", ignoreCase = true) -> ZedCyanPrimary
        item.tagBadge.contains("Clicks", ignoreCase = true) -> ZedPurpleAccent
        else -> ZedEmeraldSuccess
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) ZedDarkSurfaceElevated else ZedGlassSurface
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) ZedCyanPrimary else ZedCardBorder
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (index != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ZedDarkBackground
                        ) {
                            Text(
                                text = "#$index",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = ZedCyanPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = tagColor.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, tagColor.copy(alpha = 0.55f))
                    ) {
                        Text(
                            text = item.tagBadge.ifBlank { item.framework },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${item.characterCount} chars",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (!item.willCutOffWarning && item.characterCount <= 60) ZedEmeraldSuccess else ZedAmberWarning
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ZedEmeraldSuccess.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = "${item.score}/100",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = ZedEmeraldSuccess,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.whyItWorks,
                style = MaterialTheme.typography.bodySmall,
                color = ZedTextSecondary
            )

            // Character Cutoff Warning
            if (item.willCutOffWarning || item.characterCount > 60) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ZedAmberWarning.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, ZedAmberWarning.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = ZedAmberWarning,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = item.cutoffWarningText ?: "Over 60 chars (${item.characterCount}) — may truncate on mobile feeds",
                            style = MaterialTheme.typography.labelSmall,
                            color = ZedAmberWarning
                        )
                    }
                }
            }

            // Mobile-Feed & Search-Result Preview Box
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = ZedDarkBackground.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, ZedCardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Smartphone,
                            contentDescription = null,
                            tint = ZedCyanPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Mobile Feed: ",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = ZedCyanPrimary
                        )
                        Text(
                            text = item.mobileFeedPreview.ifBlank { item.title },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = ZedPurpleAccent,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Search Result: ",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = ZedPurpleAccent
                        )
                        Text(
                            text = item.searchResultPreview.ifBlank { item.title },
                            style = MaterialTheme.typography.labelSmall,
                            color = ZedTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            // 5-Factor Breakdown Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ScoreFactorMiniPill("Clarity ${item.clarityScore}/20", Modifier.weight(1f))
                ScoreFactorMiniPill("Curiosity ${item.curiosityScore}/20", Modifier.weight(1f))
                ScoreFactorMiniPill("Keywords ${item.keywordScore}/20", Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ScoreFactorMiniPill("Length ${item.lengthScore}/20", Modifier.weight(1f))
                ScoreFactorMiniPill("Click Appeal ${item.clickAppealScore}/20", Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onCopy,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ZedCardBorder),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = ZedCyanPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy", style = MaterialTheme.typography.labelSmall, color = ZedCyanPrimary)
                    }
                    OutlinedButton(
                        onClick = onUseThisTitle,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.6f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Use This Title",
                            tint = ZedCyanPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Use This Title", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                    IconButton(
                        onClick = onSave,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BookmarkAdd,
                            contentDescription = "Save",
                            tint = ZedTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (canApplyLive) {
                    Button(
                        onClick = onApplyNow,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ZedEmeraldSuccess,
                            contentColor = ZedDarkBackground
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Apply",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreFactorMiniPill(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = ZedDarkBackground.copy(alpha = 0.65f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, ZedCardBorder)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = ZedCyanPrimary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DescriptionOptimizerSection(
    uiState: ZedCraftUiState,
    viewModel: ZedCraftViewModel,
    context: Context,
    onRequestApplyDescription: (YtVideoDetail, String, List<String>?) -> Unit
) {
    val targetVideo = uiState.selectedTargetVideo
    val res = uiState.titleOptimizerResult
    val selectedVariationIndex = uiState.selectedDescriptionVariationIndex

    var editableOptimizedDesc by remember(res, selectedVariationIndex) {
        mutableStateOf(
            if (res != null) {
                val chosenVar = res.descriptionVariations.getOrNull(selectedVariationIndex)
                val body = chosenVar?.fullDescription ?: res.optimizedDescription
                buildString {
                    appendLine(body)
                    if (res.suggestedChapters.isNotEmpty() && !body.contains("00:00")) {
                        appendLine()
                        appendLine("Chapters:")
                        res.suggestedChapters.forEach { appendLine(it) }
                    }
                    if (res.hashtags.isNotEmpty() && !body.contains("#")) {
                        appendLine()
                        appendLine(res.hashtags.joinToString(" "))
                    }
                }.trim()
            } else {
                ""
            }
        )
    }

    LaunchedEffect(res, selectedVariationIndex) {
        if (res != null) {
            val chosenVar = res.descriptionVariations.getOrNull(selectedVariationIndex)
            val body = chosenVar?.fullDescription ?: res.optimizedDescription
            editableOptimizedDesc = buildString {
                appendLine(body)
                if (res.suggestedChapters.isNotEmpty() && !body.contains("00:00")) {
                    appendLine()
                    appendLine("Chapters:")
                    res.suggestedChapters.forEach { appendLine(it) }
                }
                if (res.hashtags.isNotEmpty() && !body.contains("#")) {
                    appendLine()
                    appendLine(res.hashtags.joinToString(" "))
                }
            }.trim()
        }
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
                    text = "Score & Optimize Video Description",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Scores your current description, extracts real search keywords from top-ranking videos, and builds 3 ready-to-use variations (first 2 lines hook, keywords, 00:00 chapters, CTA, and 3 hashtags).",
                    style = MaterialTheme.typography.bodySmall,
                    color = ZedTextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.optimizeTitleInput.ifBlank { uiState.optimizeTopicInput },
                    onValueChange = { viewModel.updateOptimizeInputs(topic = it, title = it) },
                    label = { Text("Video Title / Topic") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ZedCyanPrimary,
                        unfocusedBorderColor = ZedCardBorder
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = uiState.optimizeDescInput,
                    onValueChange = { viewModel.updateOptimizeInputs(description = it) },
                    label = { Text("Current Description / Key Talking Points") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ZedCyanPrimary,
                        unfocusedBorderColor = ZedCardBorder
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.runTitleDescTagsOptimizer(forceRefreshSearch = false) },
                    enabled = !uiState.isOptimizing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ZedCyanPrimary,
                        contentColor = ZedDarkBackground
                    )
                ) {
                    if (uiState.isOptimizing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = ZedDarkBackground
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scoring & Building Description Variations...")
                    } else {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Score & Generate Smart Descriptions", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (uiState.isOptimizing) {
            SmartSuggestionsLoadingSkeleton(
                label = "Building Search-focused, Click-focused, and Balanced descriptions from live YouTube patterns..."
            )
        }

        if (res != null) {
            // Description Score Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
                border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Current Description Score",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${res.descriptionScore}/100",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (res.descriptionScore >= 75) ZedEmeraldSuccess else ZedAmberWarning
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = res.descriptionDiagnosis,
                        style = MaterialTheme.typography.bodySmall,
                        color = ZedTextSecondary
                    )
                }
            }

            DataSourceAndPatternsCard(res = res)

            // 3 Variations Selector (Search-focused, Click-focused, Balanced)
            if (res.descriptionVariations.isNotEmpty()) {
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
                                text = "Smart Description Variations (${res.descriptionVariations.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            OutlinedButton(
                                onClick = { viewModel.runTitleDescTagsOptimizer(forceRefreshSearch = true) },
                                enabled = !uiState.isOptimizing,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.6f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Regenerate",
                                    tint = ZedCyanPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Regenerate", style = MaterialTheme.typography.labelSmall, color = ZedCyanPrimary)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            res.descriptionVariations.forEachIndexed { index, variation ->
                                val isSelected = index == selectedVariationIndex
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { viewModel.selectDescriptionVariation(index) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) ZedCyanPrimary.copy(alpha = 0.2f) else ZedDarkSurfaceElevated,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) ZedCyanPrimary else ZedCardBorder
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = variation.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) ZedCyanPrimary else Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${variation.score}/100",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ZedEmeraldSuccess
                                        )
                                    }
                                }
                            }
                        }

                        val activeVar = res.descriptionVariations.getOrNull(selectedVariationIndex)
                        if (activeVar != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = ZedDarkBackground.copy(alpha = 0.72f),
                                border = BorderStroke(1.dp, ZedCardBorder)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Above 'Show More' Preview (First 2 Lines):",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = ZedCyanPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = activeVar.firstTwoLinesHook,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White
                                    )
                                    if (activeVar.keywordsUsed.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Natural Search Keywords Included: ${activeVar.keywordsUsed.joinToString(", ")}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ZedEmeraldSuccess
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        copyToClipboard(context, "YouTube Description", editableOptimizedDesc)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, ZedCardBorder)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        tint = ZedCyanPrimary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copy", style = MaterialTheme.typography.labelMedium, color = ZedCyanPrimary)
                                }

                                Button(
                                    onClick = {
                                        viewModel.useSuggestedDescription(editableOptimizedDesc)
                                        Toast.makeText(context, "Loaded into Description Editor!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ZedCyanPrimary,
                                        contentColor = ZedDarkBackground
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Use This Description",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (res != null || editableOptimizedDesc.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
                border = BorderStroke(1.dp, ZedEmeraldSuccess.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ready-to-Use Optimized Description",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = ZedEmeraldSuccess
                        )
                        Row {
                            IconButton(
                                onClick = {
                                    copyToClipboard(context, "YouTube Description", editableOptimizedDesc)
                                }
                            ) {
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = ZedCyanPrimary)
                            }
                            IconButton(
                                onClick = {
                                    viewModel.saveIdeaToVault(
                                        category = "Description",
                                        title = uiState.optimizeTitleInput.ifBlank { "SEO Description" },
                                        content = editableOptimizedDesc
                                    )
                                    Toast.makeText(context, "Saved description to Vault!", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(imageVector = Icons.Default.BookmarkAdd, contentDescription = "Save", tint = ZedCyanPrimary)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = editableOptimizedDesc,
                        onValueChange = { editableOptimizedDesc = it },
                        minLines = 6,
                        maxLines = 14,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editable_optimized_description_input"),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ZedEmeraldSuccess,
                            unfocusedBorderColor = ZedCardBorder
                        )
                    )
                }
            }

            BeforeAfterPreviewCard(
                fieldLabel = "Video Description",
                beforeText = targetVideo?.description ?: uiState.optimizeDescInput,
                afterText = editableOptimizedDesc,
                canApplyLive = targetVideo != null && !uiState.oauthAccessToken.isNullOrBlank(),
                isApplying = uiState.isApplyingToYouTube,
                onApplyToYouTube = {
                    if (targetVideo != null) {
                        onRequestApplyDescription(targetVideo, editableOptimizedDesc, null)
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsAndHashtagsOptimizerSection(
    uiState: ZedCraftUiState,
    viewModel: ZedCraftViewModel,
    context: Context,
    onRequestApplyTags: (YtVideoDetail, List<String>) -> Unit
) {
    val targetVideo = uiState.selectedTargetVideo
    val res = uiState.titleOptimizerResult
    var editableTagsCsv by remember(res, uiState.optimizeTagsInput) {
        mutableStateOf(
            uiState.optimizeTagsInput.ifBlank {
                res?.recommendedTags?.joinToString(", ").orEmpty()
            }
        )
    }
    var customTagToAdd by remember { mutableStateOf("") }

    LaunchedEffect(res) {
        if (res != null && res.recommendedTags.isNotEmpty()) {
            editableTagsCsv = res.recommendedTags.joinToString(", ")
        }
    }

    val parsedTagList = remember(editableTagsCsv) {
        editableTagsCsv
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    val frequencyMap = remember(res) {
        res?.smartTagSuggestions?.associateBy({ it.tag.lowercase() }, { it }) ?: emptyMap()
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
                    text = "Score & Generate Smart Hashtags & Tags",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Pulls real tags from top-ranking YouTube videos in your topic, shows how common each tag is among top results, and builds 3 best hashtags + a 500-char tag list.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ZedTextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.optimizeTitleInput.ifBlank { uiState.optimizeTopicInput },
                    onValueChange = { viewModel.updateOptimizeInputs(topic = it, title = it) },
                    label = { Text("Video Topic / Working Title") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ZedCyanPrimary,
                        unfocusedBorderColor = ZedCardBorder
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.runTitleDescTagsOptimizer(forceRefreshSearch = false) },
                    enabled = !uiState.isOptimizing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ZedCyanPrimary,
                        contentColor = ZedDarkBackground
                    )
                ) {
                    if (uiState.isOptimizing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = ZedDarkBackground
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scanning Top Video Tags & Hashtags...")
                    } else {
                        Icon(imageVector = Icons.Default.Tag, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Score & Generate Hashtags + Tag Pack", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (uiState.isOptimizing) {
            SmartSuggestionsLoadingSkeleton(
                label = "Counting tag frequencies across top-ranking YouTube videos..."
            )
        }

        if (res != null) {
            // Tags & Hashtags Score Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
                border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Current Tags & Hashtags Score",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${res.tagsAndHashtagsScore}/100",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (res.tagsAndHashtagsScore >= 75) ZedEmeraldSuccess else ZedAmberWarning
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = res.tagsDiagnosis,
                        style = MaterialTheme.typography.bodySmall,
                        color = ZedTextSecondary
                    )
                }
            }

            DataSourceAndPatternsCard(res = res)

            // 3 Best Hashtags for the Description
            if (res.hashtags.isNotEmpty()) {
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "3 Best Hashtags for Description",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Tap any hashtag to copy one, or copy all 3.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ZedTextSecondary
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    copyToClipboard(context, "YouTube Hashtags", res.hashtags.take(3).joinToString(" "))
                                },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, ZedPurpleAccent),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy All Hashtags",
                                    tint = ZedPurpleAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy All 3", style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            res.hashtags.take(3).forEach { hashtag ->
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            copyToClipboard(context, "Hashtag", hashtag)
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    color = ZedPurpleAccent.copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, ZedPurpleAccent.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = hashtag,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (res != null || parsedTagList.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
                border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Recommended Tag List (${parsedTagList.size} tags • ${editableTagsCsv.length}/500 chars)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tap a tag to copy it, or tap × to remove it.",
                                style = MaterialTheme.typography.labelSmall,
                                color = ZedTextSecondary
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { copyToClipboard(context, "YouTube Tags", editableTagsCsv) },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, ZedCyanPrimary),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy All Tags",
                                    tint = ZedCyanPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy All", style = MaterialTheme.typography.labelSmall, color = ZedCyanPrimary)
                            }
                            IconButton(
                                onClick = { viewModel.runTitleDescTagsOptimizer(forceRefreshSearch = true) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Regenerate Tags",
                                    tint = ZedCyanPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Add a custom tag row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customTagToAdd,
                            onValueChange = { customTagToAdd = it },
                            placeholder = { Text("Add a tag (within 500-char limit)...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ZedCyanPrimary,
                                unfocusedBorderColor = ZedCardBorder
                            )
                        )
                        Button(
                            onClick = {
                                val clean = customTagToAdd.trim().removePrefix("#")
                                if (clean.isNotBlank()) {
                                    val updated = (parsedTagList + clean).distinct().joinToString(", ")
                                    if (updated.length <= 495) {
                                        editableTagsCsv = updated
                                        viewModel.addSmartTagToList(clean)
                                        customTagToAdd = ""
                                    } else {
                                        Toast.makeText(context, "Would exceed YouTube's 500-char tag limit", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = customTagToAdd.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ZedCyanPrimary,
                                contentColor = ZedDarkBackground
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Add Tag", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        parsedTagList.forEach { tag ->
                            val smartMeta = frequencyMap[tag.lowercase()]
                            val commonalityLabel = smartMeta?.commonalityLabel ?: "Topic keyword"
                            val isFromCompetitors = (smartMeta?.usedInTopVideosCount ?: 0) > 0
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ZedDarkSurfaceElevated,
                                border = BorderStroke(
                                    1.dp,
                                    if (isFromCompetitors) ZedCyanPrimary.copy(alpha = 0.55f) else ZedCardBorder
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clickable {
                                            copyToClipboard(context, "Tag", tag)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = tag,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = commonalityLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isFromCompetitors) ZedCyanPrimary else ZedTextSecondary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove $tag",
                                        tint = ZedTextSecondary,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable {
                                                val updated = parsedTagList
                                                    .filterNot { it.equals(tag, ignoreCase = true) }
                                                    .joinToString(", ")
                                                editableTagsCsv = updated
                                                viewModel.removeSmartTagFromList(tag)
                                            }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = editableTagsCsv,
                        onValueChange = { editableTagsCsv = it },
                        label = { Text("Comma-separated tags (${editableTagsCsv.length}/500 chars)") },
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editable_tags_input"),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ZedCyanPrimary,
                            unfocusedBorderColor = ZedCardBorder
                        )
                    )
                }
            }

            BeforeAfterPreviewCard(
                fieldLabel = "Video Tags",
                beforeText = targetVideo?.tags?.joinToString(", ").orEmpty(),
                afterText = parsedTagList.joinToString(", "),
                canApplyLive = targetVideo != null && !uiState.oauthAccessToken.isNullOrBlank(),
                isApplying = uiState.isApplyingToYouTube,
                onApplyToYouTube = {
                    if (targetVideo != null && parsedTagList.isNotEmpty()) {
                        onRequestApplyTags(targetVideo, parsedTagList)
                    }
                }
            )
        }
    }
}

@Composable
private fun ThumbnailVisionSection(
    uiState: ZedCraftUiState,
    viewModel: ZedCraftViewModel,
    onRequestApplyThumbnail: (YtVideoDetail) -> Unit
) {
    val context = LocalContext.current
    val targetVideo = uiState.selectedTargetVideo

    val primaryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val bmp = loadBitmapFromUri(context, uri)
            if (bmp != null) {
                viewModel.setSelectedThumbnails(primary = bmp)
            }
        }
    }

    val secondaryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val bmp = loadBitmapFromUri(context, uri)
            if (bmp != null) {
                viewModel.setSelectedThumbnails(
                    primary = uiState.selectedThumbnailBitmap,
                    secondary = bmp
                )
            }
        }
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
                    text = "Thumbnail Scorer & Live YouTube Uploader",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Pick a 16:9 JPG or PNG thumbnail (automatically optimized under 2MB). Score it with ZD Vision, then tap 'Apply to YouTube' to upload it with thumbnails.set.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ZedTextSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.optimizeTitleInput.ifBlank { targetVideo?.title ?: uiState.optimizeTopicInput },
                    onValueChange = { viewModel.updateOptimizeInputs(title = it, topic = it) },
                    label = { Text("Companion Video Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ZedCyanPrimary,
                        unfocusedBorderColor = ZedCardBorder
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ThumbnailPickerSlot(
                        modifier = Modifier.weight(1f),
                        label = "New Thumbnail (16:9)",
                        bitmap = uiState.selectedThumbnailBitmap,
                        onPick = {
                            primaryPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                    ThumbnailPickerSlot(
                        modifier = Modifier.weight(1f),
                        label = "Optional B Variant",
                        bitmap = uiState.secondThumbnailBitmap,
                        onPick = {
                            secondaryPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { viewModel.runThumbnailVisionCritique() },
                        enabled = !uiState.isOptimizing && uiState.selectedThumbnailBitmap != null,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("run_thumbnail_vision_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ZedCyanPrimary,
                            contentColor = ZedDarkBackground
                        )
                    ) {
                        if (uiState.isOptimizing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = ZedDarkBackground
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scoring...")
                        } else {
                            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Score Thumbnail", fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = {
                            if (targetVideo != null && uiState.selectedThumbnailBitmap != null) {
                                onRequestApplyThumbnail(targetVideo)
                            }
                        },
                        enabled = targetVideo != null &&
                            !uiState.oauthAccessToken.isNullOrBlank() &&
                            uiState.selectedThumbnailBitmap != null &&
                            !uiState.isApplyingToYouTube,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("apply_thumbnail_to_youtube_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ZedEmeraldSuccess,
                            contentColor = ZedDarkBackground
                        )
                    ) {
                        Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Apply to YouTube", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        val visionRes = uiState.thumbnailVisionResult
        if (visionRes != null) {
            ThumbnailVisionResultCard(res = visionRes)
        }
    }
}

@Composable
private fun ThumbnailPickerSlot(
    modifier: Modifier = Modifier,
    label: String,
    bitmap: Bitmap?,
    onPick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(115.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onPick() },
        color = ZedDarkSurfaceElevated,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.4f))
    ) {
        if (bitmap != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "12:48",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = label,
                    tint = ZedCyanPrimary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = ZedTextSecondary
                )
            }
        }
    }
}

@Composable
private fun ThumbnailVisionResultCard(res: ThumbnailVisionResponse) {
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
                Text(
                    text = "ZD Vision Thumbnail Score",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${res.overallScore}/100",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (res.overallScore >= 75) ZedEmeraldSuccess else ZedAmberWarning
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            VisionScoreBar("Text Readability at Mobile Size", res.readabilityScore)
            VisionScoreBar("Color Contrast & Pop", res.contrastAndColorScore)
            VisionScoreBar("Emotional Hook & Focal Point", res.emotionalHookScore)
            VisionScoreBar("Title + Thumbnail Curiosity Gap", res.titleSynergyScore)

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Mobile Squint Test Verdict:",
                style = MaterialTheme.typography.labelLarge,
                color = ZedCyanPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = res.mobileSquintTestVerdict,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Bottom-Right Timestamp Safety:",
                style = MaterialTheme.typography.labelLarge,
                color = ZedAmberWarning,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = res.timestampZoneWarning,
                style = MaterialTheme.typography.bodySmall,
                color = ZedTextSecondary
            )

            if (res.actionableImprovements.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Actionable CTR Improvements:",
                    style = MaterialTheme.typography.labelLarge,
                    color = ZedEmeraldSuccess,
                    fontWeight = FontWeight.Bold
                )
                res.actionableImprovements.forEach { step ->
                    Text(
                        text = "• $step",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VisionScoreBar(label: String, score: Int) {
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = ZedTextSecondary)
            Text(text = "$score/100", style = MaterialTheme.typography.labelSmall, color = ZedCyanPrimary, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(3.dp))
        LinearProgressIndicator(
            progress = { (score / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = if (score >= 75) ZedEmeraldSuccess else ZedCyanPrimary,
            trackColor = ZedDarkBackground
        )
    }
}

@Composable
private fun ScriptAndHookSection(
    uiState: ZedCraftUiState,
    viewModel: ZedCraftViewModel,
    context: Context
) {
    var durationMinutes by remember { mutableIntStateOf(10) }
    var tone by remember { mutableStateOf("High-Retention Educational & Fast-Paced") }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
            border = BorderStroke(1.dp, ZedCardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "30-Second Hook & Retention Script Writer",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Generates 3 opening hook scripts, B-roll visual cues, mid-video pattern interrupts, and a binge-loop ending.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ZedTextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.optimizeTopicInput.ifBlank { uiState.optimizeTitleInput },
                    onValueChange = { viewModel.updateOptimizeInputs(topic = it, title = it) },
                    label = { Text("Video Topic or Title") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ZedCyanPrimary,
                        unfocusedBorderColor = ZedCardBorder
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = tone,
                    onValueChange = { tone = it },
                    label = { Text("Pacing & Tone") },
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
                    onClick = { viewModel.runScriptAndHookGenerator(durationMinutes, tone) },
                    enabled = !uiState.isOptimizing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ZedCyanPrimary,
                        contentColor = ZedDarkBackground
                    )
                ) {
                    if (uiState.isOptimizing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = ZedDarkBackground
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Crafting Hooks & Script Outline...")
                    } else {
                        Icon(imageVector = Icons.Default.MovieCreation, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate Hooks & Script", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (!uiState.scriptOutputMarkdown.isNullOrBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
                border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Generated Hook & Script Blueprint",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = ZedCyanPrimary
                        )
                        Row {
                            IconButton(
                                onClick = {
                                    copyToClipboard(context, "Script Outline", uiState.scriptOutputMarkdown)
                                }
                            ) {
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = ZedCyanPrimary)
                            }
                            IconButton(
                                onClick = {
                                    viewModel.saveIdeaToVault(
                                        category = "Script & Hook",
                                        title = uiState.optimizeTopicInput.ifBlank { "Video Script" },
                                        content = uiState.scriptOutputMarkdown
                                    )
                                    Toast.makeText(context, "Saved script to Vault!", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(imageVector = Icons.Default.BookmarkAdd, contentDescription = "Save", tint = ZedCyanPrimary)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.scriptOutputMarkdown,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun ShortsRepurposeSection(
    uiState: ZedCraftUiState,
    viewModel: ZedCraftViewModel,
    context: Context
) {
    var transcriptNotes by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
            border = BorderStroke(1.dp, ZedCardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Long-Form to YouTube Shorts Repurpose Planner",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Turn any long-form upload into 3 vertical Shorts (<60s) with Seamless Loop Scripts and Related-Video CTAs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ZedTextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.optimizeTitleInput.ifBlank { uiState.optimizeTopicInput },
                    onValueChange = { viewModel.updateOptimizeInputs(title = it, topic = it) },
                    label = { Text("Source Long-Form Video Title") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ZedCyanPrimary,
                        unfocusedBorderColor = ZedCardBorder
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = transcriptNotes,
                    onValueChange = { transcriptNotes = it },
                    label = { Text("Paste Video Summary, Chapters, or Transcript") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ZedCyanPrimary,
                        unfocusedBorderColor = ZedCardBorder
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.runShortsRepurposeGenerator(transcriptNotes) },
                    enabled = !uiState.isOptimizing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ZedPurpleAccent,
                        contentColor = Color.White
                    )
                ) {
                    if (uiState.isOptimizing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Planning 3 Vertical Shorts...")
                    } else {
                        Icon(imageVector = Icons.Default.PlayCircleFilled, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate 3 Shorts Remix Scripts", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (!uiState.shortsPlanMarkdown.isNullOrBlank()) {
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
                            text = "Vertical Shorts Repurpose Plan",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = ZedPurpleAccent
                        )
                        IconButton(
                            onClick = { copyToClipboard(context, "Shorts Plan", uiState.shortsPlanMarkdown) }
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = ZedCyanPrimary)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.shortsPlanMarkdown,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
}

private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = true
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } catch (_: Exception) {
        null
    }
}
