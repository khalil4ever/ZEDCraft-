package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.R
import com.example.data.local.ChecklistItemEntity
import com.example.data.local.SavedIdeaEntity
import com.example.data.remote.YouTubeApiClient
import com.example.ui.components.ZdMarkdownMessage
import com.example.ui.theme.ZedAmberWarning
import com.example.ui.theme.ZedCardBorder
import com.example.ui.theme.ZedCoralError
import com.example.ui.theme.ZedCyanPrimary
import com.example.ui.theme.ZedDarkBackground
import com.example.ui.theme.ZedDarkSurfaceElevated
import com.example.ui.theme.ZedEmeraldSuccess
import com.example.ui.theme.ZedGlassSurface
import com.example.ui.theme.ZedPurpleAccent
import com.example.ui.theme.ZedTextPrimary
import com.example.ui.theme.ZedTextMuted
import com.example.ui.theme.ZedTextSecondary
import com.example.ui.viewmodel.DetailDestination
import com.example.ui.viewmodel.OptimizeSubTab
import com.example.ui.viewmodel.ZedCraftUiState
import com.example.ui.viewmodel.ZedCraftViewModel
import java.util.Locale

@Composable
fun ProfileScreen(
    uiState: ZedCraftUiState,
    savedIdeasCount: Int,
    checklistCompletedCount: Int,
    checklistTotalCount: Int,
    viewModel: ZedCraftViewModel
) {
    val context = LocalContext.current
    val launchOAuth = rememberYouTubeOAuthLauncher(
        onAuthorized = { token -> viewModel.connectWithOAuthToken(token) },
        onError = { msg -> viewModel.setOAuthError(msg) }
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ZedDarkBackground),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Connected Channel Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
                border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    val ch = uiState.activeChannel
                    if (ch != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = ch.thumbnailUrl,
                                contentDescription = ch.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(ZedDarkSurfaceElevated)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ch.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${ch.handle} • ${YouTubeApiClient.formatCompact(ch.subscriberCount)} subscribers",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ZedCyanPrimary
                                )
                                Text(
                                    text = if (ch.isOAuthMine) {
                                        "Connected via YouTube Account • Live Video Updates Enabled"
                                    } else {
                                        "Public Channel Mode • Connect account to apply live video edits"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (ch.isOAuthMine) ZedEmeraldSuccess else ZedTextMuted
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.refreshActiveChannel() },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("profile_sync_channel_button"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = null,
                                    tint = ZedCyanPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Refresh Videos", color = ZedCyanPrimary)
                            }
                            OutlinedButton(
                                onClick = {
                                    viewModel.disconnectChannel()
                                    Toast.makeText(context, "YouTube channel disconnected", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("profile_disconnect_channel_button"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, ZedCoralError.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LinkOff,
                                    contentDescription = null,
                                    tint = ZedCoralError,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Disconnect channel", color = ZedCoralError)
                            }
                        }
                        if (!ch.isOAuthMine) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = launchOAuth,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ZedCyanPrimary,
                                    contentColor = ZedDarkBackground
                                )
                            ) {
                                Text(
                                    text = "Connect YouTube Channel for Live Video Edits",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "No YouTube Channel Connected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Connect your YouTube channel to load your uploaded videos and apply optimized titles, descriptions, tags, and thumbnails directly to YouTube.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ZedTextSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = launchOAuth,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("profile_connect_youtube_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ZedCyanPrimary,
                                    contentColor = ZedDarkBackground
                                )
                            ) {
                                Text("Connect YouTube Channel", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { viewModel.setShowOnboardingSheet(true) },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, ZedCardBorder)
                            ) {
                                Text("Public Lookup", color = ZedTextSecondary)
                            }
                        }
                    }
                }
            }
        }

        // Creator Power Tools Suite
        item {
            Text(
                text = "Creator Power Tools",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            ProfileNavRow(
                icon = Icons.Default.HealthAndSafety,
                title = "Channel Health & Policy Audit",
                subtitle = "360° health score, upload schedule & YPP compliance",
                accent = ZedEmeraldSuccess,
                onClick = { viewModel.openDetailScreen(DetailDestination.ChannelHealthAudit) }
            )
        }

        item {
            ProfileNavRow(
                icon = Icons.Default.AttachMoney,
                title = "Revenue & Sponsor Deal Calculator",
                subtitle = "Estimate AdSense RPM, Shorts revenue & sponsor deal pricing",
                accent = ZedAmberWarning,
                onClick = { viewModel.openDetailScreen(DetailDestination.RevenueAndSponsorCalculator) }
            )
        }

        item {
            ProfileNavRow(
                icon = Icons.Default.Checklist,
                title = "Pre-Publish & YPP Growth Checklists",
                subtitle = "$checklistCompletedCount of $checklistTotalCount creator milestones completed",
                accent = ZedCyanPrimary,
                onClick = { viewModel.openDetailScreen(DetailDestination.LaunchChecklist) }
            )
        }

        item {
            ProfileNavRow(
                icon = Icons.Default.Bookmark,
                title = "Saved Ideas & Templates Vault",
                subtitle = "$savedIdeasCount saved titles, scripts, hooks & keyword packs",
                accent = ZedPurpleAccent,
                onClick = { viewModel.openDetailScreen(DetailDestination.SavedVault) }
            )
        }

        // App Updates Card (In-App Auto-Update)
        item {
            Text(
                text = "App Updates",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
                border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ZedCyanPrimary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdateAlt,
                                    contentDescription = "App Updates",
                                    tint = ZedCyanPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "ZEDCraft App Updates",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Installed version: v${BuildConfig.VERSION_NAME}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ZedTextSecondary
                                )
                            }
                        }

                        Button(
                            onClick = { viewModel.checkForGitHubUpdates(isSilent = false) },
                            enabled = !uiState.isCheckingUpdate && !uiState.isDownloadingUpdate,
                            modifier = Modifier.testTag("check_github_updates_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ZedCyanPrimary,
                                contentColor = ZedDarkBackground
                            )
                        ) {
                            if (uiState.isCheckingUpdate) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = ZedDarkBackground
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Checking...", style = MaterialTheme.typography.labelMedium)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Check Updates",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    val rel = uiState.githubReleaseInfo
                    if (rel != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = ZedDarkSurfaceElevated,
                            border = BorderStroke(
                                1.dp,
                                if (rel.isUpdateAvailable) ZedEmeraldSuccess.copy(alpha = 0.5f) else ZedCardBorder
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = rel.releaseTitle,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (rel.isUpdateAvailable) ZedEmeraldSuccess else Color.White
                                    )
                                    Text(
                                        text = if (rel.isUpdateAvailable) "UPDATE AVAILABLE" else "UP TO DATE",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (rel.isUpdateAvailable) ZedEmeraldSuccess else ZedCyanPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = rel.releaseNotes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ZedTextSecondary,
                                    maxLines = 5,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (rel.isUpdateAvailable && !rel.apkDownloadUrl.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = { viewModel.startInAppUpdateDownload() },
                                        enabled = !uiState.isDownloadingUpdate,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("profile_install_update_now_button"),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ZedEmeraldSuccess,
                                            contentColor = ZedDarkBackground
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SystemUpdateAlt,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Update Now (${rel.latestTag})",
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (!uiState.updateCheckError.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = uiState.updateCheckError,
                            style = MaterialTheme.typography.bodySmall,
                            color = ZedAmberWarning
                        )
                    }
                }
            }
        }

        // Settings, Credits & Legal
        item {
            Text(
                text = "About & Preferences",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            ProfileNavRow(
                icon = Icons.Default.Info,
                title = "Credits",
                subtitle = "About ZEDCraft & Timothy Khalil Tembo",
                accent = ZedCyanPrimary,
                onClick = { viewModel.openDetailScreen(DetailDestination.Credits) }
            )
        }

        item {
            ProfileNavRow(
                icon = Icons.Default.Policy,
                title = "Privacy Policy & YouTube Terms",
                subtitle = "Data protection, local undo backups & official terms",
                accent = ZedEmeraldSuccess,
                onClick = { viewModel.openDetailScreen(DetailDestination.PrivacyAndTerms) }
            )
        }

        item {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DarkMode,
                                contentDescription = "Dark Theme",
                                tint = ZedCyanPrimary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Dark Cyan Glass Theme",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Default 2026 OLED studio contrast",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ZedTextSecondary
                                )
                            }
                        }
                        Switch(
                            checked = uiState.isDarkTheme,
                            onCheckedChange = { viewModel.toggleTheme(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ZedDarkBackground,
                                checkedTrackColor = ZedCyanPrimary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            viewModel.clearRoomCache()
                            Toast.makeText(context, "Offline cache refreshed", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ZedCardBorder)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = ZedTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear Cached Video Lookups", color = ZedTextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
        border = BorderStroke(1.dp, ZedCardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = ZedTextSecondary
                )
            }
        }
    }
}

// =========================================================================
// CREDITS SCREEN (Replaces Setup Screen)
// =========================================================================

@Composable
fun CreditsScreen(
    uiState: ZedCraftUiState,
    viewModel: ZedCraftViewModel
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ZedDarkBackground)
            .testTag("zedcraft_credits_screen"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // ZEDCraft Logo + Title + Subtitle
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    ZedCyanPrimary.copy(alpha = 0.25f),
                                    ZedPurpleAccent.copy(alpha = 0.25f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_zedcraft_logo),
                        contentDescription = "ZEDCraft Logo",
                        modifier = Modifier
                            .size(84.dp)
                            .clip(RoundedCornerShape(22.dp))
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "ZEDCraft",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = ZedTextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Slowly developed with passion by Timothy Khalil Tembo",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = ZedCyanPrimary,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Card: "About the creator"
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
                border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "About the creator",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ZedTextPrimary
                    )

                    CreatorBulletRow(text = "\uD83C\uDDFF\uD83C\uDDF2 Based in Zambia")
                    CreatorBulletRow(text = "Logo designer and AI educator")
                    CreatorBulletRow(text = "Into YouTube growth and social media growth")
                    CreatorBulletRow(text = "Founder of AI Visual CRAFT")
                }
            }
        }

        // Line: "Built to help creators grow, for free."
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = ZedDarkSurfaceElevated,
                border = BorderStroke(1.dp, ZedCardBorder)
            ) {
                Text(
                    text = "Built to help creators grow, for free.",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = ZedEmeraldSuccess,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 16.dp, horizontal = 18.dp)
                )
            }
        }

        // Check for Updates button
        item {
            val context = LocalContext.current
            OutlinedButton(
                onClick = { viewModel.checkForGitHubUpdates(isSilent = false) },
                enabled = !uiState.isCheckingUpdate && !uiState.isDownloadingUpdate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("credits_check_updates_button"),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.45f))
            ) {
                if (uiState.isCheckingUpdate) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = ZedCyanPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Checking for updates...", color = ZedCyanPrimary)
                } else {
                    Icon(
                        imageVector = Icons.Default.SystemUpdateAlt,
                        contentDescription = null,
                        tint = ZedCyanPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Check for Updates",
                        color = ZedCyanPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Footer: "© 2026 ZEDCraft" and current app version number
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "© 2026 ZEDCraft",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = ZedTextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelMedium,
                    color = ZedTextMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun CreatorBulletRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(ZedCyanPrimary)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
}

// =========================================================================
// DETAIL SCREEN: VIDEO DEEP ANALYZER
// =========================================================================

@Composable
fun VideoDeepAnalyzerScreen(
    uiState: ZedCraftUiState,
    viewModel: ZedCraftViewModel
) {
    var manualUrl by remember { mutableStateOf("") }
    val audit = uiState.inspectedVideoAudit

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ZedDarkBackground),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
                border = BorderStroke(1.dp, ZedCardBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = manualUrl,
                            onValueChange = { manualUrl = it },
                            placeholder = { Text("Paste another YouTube Video URL or ID", color = ZedTextMuted) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ZedCyanPrimary,
                                unfocusedBorderColor = ZedCardBorder
                            )
                        )
                        Button(
                            onClick = { viewModel.analyzeVideoDeeply(manualUrl) },
                            enabled = !uiState.isLoadingVideoAudit && manualUrl.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
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

        if (uiState.isLoadingVideoAudit) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = ZedCyanPrimary)
                }
            }
        }

        if (!uiState.videoAuditError.isNullOrBlank()) {
            item {
                Text(
                    text = uiState.videoAuditError,
                    color = ZedCoralError,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (audit != null) {
            val v = audit.video
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
                    border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        AsyncImage(
                            model = v.highResThumbnailUrl,
                            contentDescription = v.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = v.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${YouTubeApiClient.formatCompact(v.viewCount)} views • ${YouTubeApiClient.formatCompact(v.likeCount)} likes • ${YouTubeApiClient.formatCompact(v.commentCount)} comments • ${YouTubeApiClient.formatDuration(v.durationSeconds)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = ZedCyanPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ZEDCraft Packaging & SEO Score",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${audit.overallSeoScore}/100",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (audit.overallSeoScore >= 75) ZedEmeraldSuccess else ZedAmberWarning
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { audit.overallSeoScore / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (audit.overallSeoScore >= 75) ZedEmeraldSuccess else ZedAmberWarning,
                            trackColor = ZedDarkBackground
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                viewModel.selectTargetVideoForOptimize(
                                    video = v,
                                    subTab = OptimizeSubTab.Title,
                                    navigateToOptimize = true,
                                    autoRunOptimizer = true
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("analyzer_optimize_this_video_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ZedCyanPrimary,
                                contentColor = ZedDarkBackground
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Optimize & Apply Changes to This Video", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Smart Suggestions right under the SEO Audit Score
            if (uiState.isLoadingVideoSmartSuggestions) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
                        border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.4f))
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
                                    text = "Building Smart Suggestions from top-ranking YouTube results...",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = ZedCyanPrimary
                                )
                            }
                            repeat(3) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(ZedDarkSurfaceElevated)
                                )
                            }
                        }
                    }
                }
            }

            val smartSuggestions = uiState.inspectedVideoSmartSuggestions
            if (smartSuggestions != null) {
                item {
                    val context = LocalContext.current
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
                        border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Smart Suggestions (Title, Description & Tags)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = smartSuggestions.dataSourceBadge,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (smartSuggestions.isBackedByLiveYouTubeData) ZedEmeraldSuccess else ZedAmberWarning
                                    )
                                }
                                OutlinedButton(
                                    onClick = { viewModel.regenerateInspectedVideoSmartSuggestions() },
                                    enabled = !uiState.isLoadingVideoSmartSuggestions,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, ZedCyanPrimary),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
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

                            if (smartSuggestions.searchPatternsSummary.isNotEmpty()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = ZedDarkSurfaceElevated,
                                    border = BorderStroke(1.dp, ZedCardBorder)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = "Winning Patterns in Top Results:",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = ZedCyanPrimary
                                        )
                                        smartSuggestions.searchPatternsSummary.take(3).forEach { p ->
                                            Text(
                                                text = "• $p",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = ZedTextSecondary
                                            )
                                        }
                                    }
                                }
                            }

                            Text(
                                text = "Better Title Suggestions (${smartSuggestions.suggestedTitles.size} Options)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = ZedCyanPrimary
                            )

                            smartSuggestions.suggestedTitles.take(5).forEachIndexed { idx, item ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = ZedDarkSurfaceElevated,
                                    border = BorderStroke(1.dp, ZedCardBorder)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "#${idx + 1} • ${item.tagBadge}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = ZedPurpleAccent
                                            )
                                            Text(
                                                text = "${item.score}/100 • ${item.characterCount} chars",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = ZedEmeraldSuccess
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = item.whyItWorks,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ZedTextSecondary
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(
                                                onClick = {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    clipboard.setPrimaryClip(ClipData.newPlainText("Title", item.title))
                                                    Toast.makeText(context, "Copied title!", Toast.LENGTH_SHORT).show()
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, ZedCardBorder),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text("Copy", style = MaterialTheme.typography.labelSmall, color = ZedCyanPrimary)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.selectTargetVideoForOptimize(
                                                        video = v,
                                                        subTab = OptimizeSubTab.Title,
                                                        navigateToOptimize = true,
                                                        autoRunOptimizer = false
                                                    )
                                                    viewModel.useSuggestedTitle(item.title)
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.6f)),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text("Use This Title", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }

                            if (smartSuggestions.hashtags.isNotEmpty() || smartSuggestions.smartTagSuggestions.isNotEmpty()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = ZedDarkSurfaceElevated,
                                    border = BorderStroke(1.dp, ZedCardBorder)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "Recommended Hashtags: ${smartSuggestions.hashtags.take(3).joinToString(" ")}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = ZedPurpleAccent
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Top Ranking Tags: ${
                                                smartSuggestions.smartTagSuggestions.take(8)
                                                    .joinToString(", ") { "${it.tag} (${it.commonalityLabel})" }
                                            }",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ZedTextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Diagnostic Checks
            items(audit.checks) { check ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
                    border = BorderStroke(1.dp, ZedCardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        val iconColor = when (check.status) {
                            "PASS" -> ZedEmeraldSuccess
                            "WARN" -> ZedAmberWarning
                            else -> ZedCoralError
                        }
                        Icon(
                            imageVector = if (check.status == "PASS") Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = check.status,
                            tint = iconColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = check.label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = check.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = ZedTextSecondary
                            )
                        }
                    }
                }
            }

            // AI Deep Coaching Button & Result
            item {
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
                                text = "ZD Video Repackaging Plan",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = ZedPurpleAccent
                            )
                            Button(
                                onClick = { viewModel.generateAiCoachingForInspectedVideo() },
                                enabled = !uiState.isGeneratingVideoCoaching,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ZedPurpleAccent,
                                    contentColor = Color.White
                                )
                            ) {
                                if (uiState.isGeneratingVideoCoaching) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Analyzing...")
                                } else {
                                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Generate Fix Plan")
                                }
                            }
                        }

                        if (!uiState.videoAiCoachingSummary.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            ZdMarkdownMessage(
                                markdown = uiState.videoAiCoachingSummary,
                                isStreaming = false
                            )
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// DETAIL SCREEN: CHANNEL HEALTH & POLICY AUDIT
// =========================================================================

@Composable
fun ChannelHealthAuditScreen(
    uiState: ZedCraftUiState
) {
    val health = uiState.channelHealthReport
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ZedDarkBackground),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (health == null) {
            item {
                Text(
                    text = "Connect a YouTube channel on the Home tab first to run a full Channel Health & Policy Audit.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ZedTextSecondary
                )
            }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
                    border = BorderStroke(1.dp, ZedEmeraldSuccess.copy(alpha = 0.45f))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "${health.channel.title} • Overall Health: ${health.overallHealthScore}/100",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = ZedEmeraldSuccess
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = health.dropDiagnosisSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Best Upload Schedule: ${health.bestUploadSchedule}",
                            style = MaterialTheme.typography.labelLarge,
                            color = ZedCyanPrimary
                        )
                    }
                }
            }

            item {
                Text(
                    text = "YouTube Policy & Monetization Safety Checks",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(health.policyChecks) { p ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
                    border = BorderStroke(1.dp, ZedCardBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = p.status,
                                tint = if (p.status == "SAFE") ZedEmeraldSuccess else ZedAmberWarning,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${p.area} (${p.status})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = p.guidance,
                            style = MaterialTheme.typography.bodySmall,
                            color = ZedTextSecondary
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// DETAIL SCREEN: REVENUE & SPONSOR DEAL CALCULATOR
// =========================================================================

@Composable
fun RevenueAndSponsorCalculatorScreen(
    uiState: ZedCraftUiState,
    viewModel: ZedCraftViewModel
) {
    val context = LocalContext.current
    val longFormMonthlyUsd = (uiState.calcMonthlyViews / 1000.0) * uiState.calcNicheRpmUsd
    val shortsMonthlyUsd = (uiState.calcShortsViews / 1000.0) * 0.09 // Realistic $0.09 RPM for Shorts
    val totalAdSenseMonthly = longFormMonthlyUsd + shortsMonthlyUsd
    val totalAdSenseYearly = totalAdSenseMonthly * 12.0

    // Sponsor Integration Pricing (60s integrated shoutout: ~$22–$35 CPM)
    val sponsorLowUsd = (uiState.calcAvgViewsPerVideo / 1000.0) * 20.0
    val sponsorHighUsd = (uiState.calcAvgViewsPerVideo / 1000.0) * 35.0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ZedDarkBackground),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
                border = BorderStroke(1.dp, ZedEmeraldSuccess.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Estimated Creator Earnings",
                        style = MaterialTheme.typography.labelLarge,
                        color = ZedTextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = String.format(Locale.US, "$%,.0f / month", totalAdSenseMonthly),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = ZedEmeraldSuccess
                    )
                    Text(
                        text = String.format(Locale.US, "Projected Annual AdSense: $%,.0f / yr", totalAdSenseYearly),
                        style = MaterialTheme.typography.bodySmall,
                        color = ZedCyanPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = ZedDarkSurfaceElevated,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "60-Second Brand Integration Rate Card",
                                style = MaterialTheme.typography.labelMedium,
                                color = ZedAmberWarning,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = String.format(Locale.US, "$%,.0f – $%,.0f per sponsored video", sponsorLowUsd, sponsorHighUsd),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
                border = BorderStroke(1.dp, ZedCardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Monthly Long-Form Views: ${YouTubeApiClient.formatCompact(uiState.calcMonthlyViews)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Slider(
                        value = uiState.calcMonthlyViews.toFloat(),
                        onValueChange = { viewModel.updateRevenueCalculator(monthlyViews = it.toLong()) },
                        valueRange = 5_000f..5_000_000f,
                        colors = SliderDefaults.colors(thumbColor = ZedCyanPrimary, activeTrackColor = ZedCyanPrimary)
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = String.format(Locale.US, "Niche RPM (Revenue per 1,000 views): $%.2f", uiState.calcNicheRpmUsd),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Slider(
                        value = uiState.calcNicheRpmUsd.toFloat(),
                        onValueChange = { viewModel.updateRevenueCalculator(nicheRpmUsd = it.toDouble()) },
                        valueRange = 1.0f..35.0f,
                        colors = SliderDefaults.colors(thumbColor = ZedEmeraldSuccess, activeTrackColor = ZedEmeraldSuccess)
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Monthly Shorts Views: ${YouTubeApiClient.formatCompact(uiState.calcShortsViews)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Slider(
                        value = uiState.calcShortsViews.toFloat(),
                        onValueChange = { viewModel.updateRevenueCalculator(shortsViews = it.toLong()) },
                        valueRange = 0f..10_000_000f,
                        colors = SliderDefaults.colors(thumbColor = ZedPurpleAccent, activeTrackColor = ZedPurpleAccent)
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Avg Views Per Video (for Sponsor Rate): ${YouTubeApiClient.formatCompact(uiState.calcAvgViewsPerVideo)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Slider(
                        value = uiState.calcAvgViewsPerVideo.toFloat(),
                        onValueChange = { viewModel.updateRevenueCalculator(avgViewsPerVideo = it.toLong()) },
                        valueRange = 1_000f..1_000_000f,
                        colors = SliderDefaults.colors(thumbColor = ZedAmberWarning, activeTrackColor = ZedAmberWarning)
                    )
                }
            }
        }

        item {
            val pitchTemplate = """
                Subject: Partnership Idea: ${uiState.activeChannel?.title ?: "Our YouTube Channel"} x [Brand Name]
                
                Hi [Name],
                
                I run ${uiState.activeChannel?.title ?: "a YouTube channel"} (${uiState.activeChannel?.handle ?: "@creator"}), where our videos average ${YouTubeApiClient.formatCompact(uiState.calcAvgViewsPerVideo)} views per upload from viewers passionate about ${uiState.optimizeNicheInput}.
                
                We're planning an upcoming deep-dive video where [Brand Name] would be a natural fit for a 60-second hands-on integration. Our standard integration rate is ${String.format(Locale.US, "$%,.0f", (sponsorLowUsd + sponsorHighUsd) / 2)} including a pinned comment and top-of-description tracking link.
                
                Would you be open to a quick brief this week?
            """.trimIndent()

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
                            text = "Sponsor Cold-Pitch Template",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cb?.setPrimaryClip(ClipData.newPlainText("Sponsor Pitch", pitchTemplate))
                                Toast.makeText(context, "Sponsor pitch copied!", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy Pitch", tint = ZedCyanPrimary)
                        }
                    }
                    Text(
                        text = pitchTemplate,
                        style = MaterialTheme.typography.bodySmall,
                        color = ZedTextSecondary
                    )
                }
            }
        }
    }
}

// =========================================================================
// DETAIL SCREEN: PRE-PUBLISH & YPP LAUNCH CHECKLIST
// =========================================================================

@Composable
fun LaunchChecklistScreen(
    items: List<ChecklistItemEntity>,
    viewModel: ZedCraftViewModel
) {
    val completed = items.count { it.isCompleted }
    val total = items.size.coerceAtLeast(1)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ZedDarkBackground),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
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
                            text = "Checklist Progress: $completed / ${items.size}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedButton(
                            onClick = { viewModel.resetChecklistProgress() },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, ZedCardBorder)
                        ) {
                            Text("Reset All", style = MaterialTheme.typography.labelSmall, color = ZedTextSecondary)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { completed.toFloat() / total.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = ZedEmeraldSuccess,
                        trackColor = ZedDarkBackground
                    )
                }
            }
        }

        items(items, key = { it.itemKey }) { item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.toggleChecklistItem(item) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
                border = BorderStroke(
                    1.dp,
                    if (item.isCompleted) ZedEmeraldSuccess.copy(alpha = 0.45f) else ZedCardBorder
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Checkbox(
                        checked = item.isCompleted,
                        onCheckedChange = { viewModel.toggleChecklistItem(item) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = ZedEmeraldSuccess,
                            checkmarkColor = ZedDarkBackground
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = item.phase.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = ZedCyanPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = ZedTextSecondary
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// DETAIL SCREEN: SAVED IDEAS VAULT
// =========================================================================

@Composable
fun SavedVaultScreen(
    savedIdeas: List<SavedIdeaEntity>,
    viewModel: ZedCraftViewModel
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ZedDarkBackground),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (savedIdeas.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
                    border = BorderStroke(1.dp, ZedCardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Your Vault is Empty",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Bookmark optimized titles, descriptions, scripts, keywords, or ZD coaching notes to access them offline anytime.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ZedTextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(savedIdeas, key = { it.id }) { idea ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
                    border = BorderStroke(1.dp, ZedCardBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = idea.category.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = ZedCyanPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Row {
                                IconButton(
                                    onClick = {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, idea.title)
                                            putExtra(Intent.EXTRA_TEXT, "${idea.title}\n\n${idea.content}")
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Export Idea"))
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = ZedTextSecondary, modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = { viewModel.deleteSavedIdea(idea.id) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = ZedCoralError, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        Text(
                            text = idea.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = idea.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = ZedTextSecondary
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// DETAIL SCREEN: PRIVACY POLICY & YOUTUBE TERMS
// =========================================================================

@Composable
fun PrivacyAndTermsScreen() {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ZedDarkBackground),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
                border = BorderStroke(1.dp, ZedEmeraldSuccess.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "YouTube Terms & Data Protection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ZedEmeraldSuccess
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = """
                            1. By using ZEDCraft, you agree to be bound by the YouTube Terms of Service (https://www.youtube.com/t/terms) and Google Privacy Policy (https://policies.google.com/privacy).
                            
                            2. User-Confirmed Changes Only: ZEDCraft never modifies your videos automatically. Live updates to your YouTube video title, description, tags, or thumbnail happen strictly when you tap "Apply to YouTube" and confirm the update dialog.
                            
                            3. Automatic Local Undo Backup: Before applying any title, description, or tag update, ZEDCraft saves your previous video snippet locally on your device so you can restore the previous version with a single tap.
                            
                            4. Account Revocation: You can disconnect your channel inside Profile at any time, or revoke ZEDCraft's access via Google Security Settings (https://security.google.com/settings/security/permissions).
                        """.trimIndent(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/t/terms"))
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.5f))
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = ZedCyanPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("YouTube ToS", color = ZedCyanPrimary)
                        }
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://policies.google.com/privacy"))
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.5f))
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = ZedCyanPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Google Privacy", color = ZedCyanPrimary)
                        }
                    }
                }
            }
        }
    }
}
