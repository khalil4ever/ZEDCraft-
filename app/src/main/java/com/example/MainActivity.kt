package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.ui.screens.ChannelHealthAuditScreen
import com.example.ui.screens.CreditsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LaunchChecklistScreen
import com.example.ui.screens.OnboardingAndConnectScreen
import com.example.ui.screens.OptimizeScreen
import com.example.ui.screens.PrivacyAndTermsScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ResearchScreen
import com.example.ui.screens.RevenueAndSponsorCalculatorScreen
import com.example.ui.screens.SavedVaultScreen
import com.example.ui.screens.VideoDeepAnalyzerScreen
import com.example.ui.screens.ZdAssistantScreen
import com.example.ui.screens.ZedCraftSplashLoadingScreen
import com.example.ui.theme.ZEDCraftTheme
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
import com.example.ui.viewmodel.PrimaryTab
import com.example.ui.viewmodel.ZedCraftUiState
import com.example.ui.viewmodel.ZedCraftViewModel
import com.example.util.configureAppCheck
import java.io.File
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize Firebase App Check (Debug provider on debug builds + Logcat debug token; Play Integrity on release)
        configureAppCheck(this, intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.parseColor("#070B14")),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.parseColor("#070B14"))
        )
        setContent {
            val zedViewModel: ZedCraftViewModel = viewModel()
            val uiState by zedViewModel.uiState.collectAsState()

            ZEDCraftTheme(darkTheme = uiState.isDarkTheme) {
                Crossfade(
                    targetState = uiState.isAppReadyForContent,
                    animationSpec = tween(durationMillis = 420),
                    label = "splash_to_app_transition"
                ) { isReady ->
                    if (!isReady) {
                        ZedCraftSplashLoadingScreen()
                    } else {
                        ZedCraftAppShell(
                            uiState = uiState,
                            viewModel = zedViewModel
                        )
                    }
                }
            }
        }
    }
}

/**
 * Guarantees top status bar / display cutout clearance with a minimum 28.dp fallback
 * so the top header never overlaps the system status bar icons (time, signal, battery).
 */
@Composable
private fun rememberSafeTopInset(): Dp {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val cutoutTop = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    return maxOf(statusTop, cutoutTop, 28.dp)
}

/**
 * Guarantees bottom navigation gesture bar clearance.
 */
@Composable
private fun rememberSafeBottomInset(): Dp {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return maxOf(navBottom, 8.dp)
}

@Composable
fun ZedCraftAppShell(
    uiState: ZedCraftUiState,
    viewModel: ZedCraftViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val savedIdeas by viewModel.savedIdeas.collectAsState()
    val zdMessages by viewModel.zdChatHistory.collectAsState()
    val checklistItems by viewModel.checklistItems.collectAsState()

    val configuration = LocalConfiguration.current
    val isExpandedScreen = configuration.screenWidthDp >= 720
    val safeTopInset = rememberSafeTopInset()
    val safeBottomInset = rememberSafeBottomInset()

    // Helper to trigger APK installation or request unknown app sources permission
    val triggerInstallApk: (File) -> Unit = { apkFile ->
        if (viewModel.gitHubUpdateClient.canRequestPackageInstalls(context)) {
            runCatching {
                val installIntent = viewModel.gitHubUpdateClient.buildInstallApkIntent(context, apkFile)
                context.startActivity(installIntent)
                viewModel.clearDownloadedApkState()
            }.onFailure { err ->
                Toast.makeText(
                    context,
                    "Could not open Android package installer: ${err.localizedMessage ?: "Unknown error"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            viewModel.setShowUnknownSourcesDialog(show = true, pendingInstall = true)
        }
    }

    // Automatically launch installer as soon as in-app APK download completes
    LaunchedEffect(uiState.downloadedApkFile) {
        val file = uiState.downloadedApkFile
        if (file != null && file.exists() && !uiState.showUnknownSourcesDialog) {
            triggerInstallApk(file)
        }
    }

    // When the user returns from "Allow from this source" system settings, automatically continue installation
    DisposableEffect(lifecycleOwner, uiState.pendingInstallAfterPermission, uiState.downloadedApkFile) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val apkFile = uiState.downloadedApkFile
                if (uiState.pendingInstallAfterPermission &&
                    apkFile != null &&
                    apkFile.exists() &&
                    viewModel.gitHubUpdateClient.canRequestPackageInstalls(context)
                ) {
                    viewModel.setShowUnknownSourcesDialog(show = false, pendingInstall = false)
                    triggerInstallApk(apkFile)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 1. In-App "New update available" Dialog
    val releaseInfo = uiState.githubReleaseInfo
    if (uiState.showAutoUpdateDialog && releaseInfo != null && releaseInfo.isUpdateAvailable) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissAutoUpdateDialog() },
            containerColor = ZedDarkSurfaceElevated,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdateAlt,
                        contentDescription = null,
                        tint = ZedCyanPrimary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "New update available",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "Version ${releaseInfo.latestTag} (Installed: v${BuildConfig.VERSION_NAME})",
                            style = MaterialTheme.typography.labelSmall,
                            color = ZedCyanPrimary
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "What's new in ${releaseInfo.releaseTitle}:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = ZedEmeraldSuccess
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = releaseInfo.releaseNotes,
                        style = MaterialTheme.typography.bodySmall,
                        color = ZedTextSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.startInAppUpdateDownload() },
                    modifier = Modifier.testTag("dialog_update_now_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ZedCyanPrimary,
                        contentColor = ZedDarkBackground
                    )
                ) {
                    Text("Update now", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissAutoUpdateDialog() },
                    modifier = Modifier.testTag("dialog_update_later_button")
                ) {
                    Text("Later", color = ZedTextSecondary)
                }
            }
        )
    }

    // 2. In-App APK Download Progress Dialog (with percentage, MB/total MB, Cancel & Retry)
    if (uiState.isDownloadingUpdate || !uiState.updateDownloadError.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = { /* Keep open during active download unless canceled */ },
            containerColor = ZedDarkSurfaceElevated,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (uiState.updateDownloadError != null) Icons.Default.Warning else Icons.Default.SystemUpdateAlt,
                        contentDescription = null,
                        tint = if (uiState.updateDownloadError != null) ZedCoralError else ZedCyanPrimary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (uiState.updateDownloadError != null) {
                            "Update Download Failed"
                        } else {
                            "Downloading ZEDCraft Update..."
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (uiState.updateDownloadError != null) {
                        Text(
                            text = uiState.updateDownloadError,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFB4AB)
                        )
                    } else {
                        val pct = uiState.downloadProgressPercent.coerceIn(0, 100)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "$pct%",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = ZedCyanPrimary
                            )
                            Text(
                                text = if (uiState.totalMb > 0f) {
                                    String.format(Locale.US, "%.1f MB of %.1f MB", uiState.downloadedMb, uiState.totalMb)
                                } else {
                                    String.format(Locale.US, "%.1f MB downloaded", uiState.downloadedMb)
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = ZedTextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { pct / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = ZedCyanPrimary,
                            trackColor = ZedDarkBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "The installer will open automatically as soon as the download finishes.",
                            style = MaterialTheme.typography.labelSmall,
                            color = ZedTextMuted
                        )
                    }
                }
            },
            confirmButton = {
                if (uiState.updateDownloadError != null) {
                    Button(
                        onClick = { viewModel.startInAppUpdateDownload() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ZedCyanPrimary,
                            contentColor = ZedDarkBackground
                        )
                    ) {
                        Text("Retry", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        if (uiState.isDownloadingUpdate) {
                            viewModel.cancelInAppUpdateDownload()
                        } else {
                            viewModel.dismissUpdateDownloadError()
                        }
                    },
                    border = BorderStroke(1.dp, ZedCardBorder)
                ) {
                    Text(
                        text = if (uiState.isDownloadingUpdate) "Cancel" else "Close",
                        color = ZedTextSecondary
                    )
                }
            }
        )
    }

    // 3. Friendly Dialog for "Install unknown apps" permission (`ACTION_MANAGE_UNKNOWN_APP_SOURCES`)
    if (uiState.showUnknownSourcesDialog) {
        AlertDialog(
            onDismissRequest = {
                viewModel.setShowUnknownSourcesDialog(show = false, pendingInstall = false)
            },
            containerColor = ZedDarkSurfaceElevated,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = ZedAmberWarning
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Allow App Update Installation",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            text = {
                Text(
                    text = "To install the downloaded ZEDCraft update, please tap 'Open Settings' and switch on 'Allow from this source'. Installation will continue automatically when you return.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ZedTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val settingsIntent = viewModel.gitHubUpdateClient.buildManageUnknownSourcesIntent(context)
                        context.startActivity(settingsIntent)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ZedCyanPrimary,
                        contentColor = ZedDarkBackground
                    )
                ) {
                    Text("Open Settings", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.setShowUnknownSourcesDialog(show = false, pendingInstall = false)
                    }
                ) {
                    Text("Cancel", color = ZedTextSecondary)
                }
            }
        )
    }

    BackHandler(
        enabled = uiState.showOnboardingSheet ||
            uiState.activeDetailScreen != null ||
            uiState.selectedTab != PrimaryTab.Home
    ) {
        if (uiState.showOnboardingSheet) {
            viewModel.setShowOnboardingSheet(false)
        } else {
            viewModel.navigateBackFromDetail()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (uiState.showOnboardingSheet) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ZedDarkBackground)
                    .padding(bottom = safeBottomInset)
            ) {
                FixedAppHeader(
                    title = "Connect YouTube Channel",
                    subtitle = "Sign in with Google or inspect a public channel",
                    activeChannelTitle = uiState.activeChannel?.title,
                    activeChannelThumb = uiState.activeChannel?.thumbnailUrl,
                    showBackButton = true,
                    safeTopInset = safeTopInset,
                    onBackClick = { viewModel.setShowOnboardingSheet(false) },
                    onSwitchChannelClick = { },
                    onOpenCreditsClick = {
                        viewModel.setShowOnboardingSheet(false)
                        viewModel.openDetailScreen(DetailDestination.Credits)
                    }
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    OnboardingAndConnectScreen(
                        isLoading = uiState.isLoadingChannel,
                        errorMessage = uiState.channelError,
                        onConnectOAuth = { token -> viewModel.connectWithOAuthToken(token) },
                        onOAuthError = { msg -> viewModel.setOAuthError(msg) },
                        onConnectPublicChannel = { q -> viewModel.connectChannelByInput(q) },
                        onDismissOnboarding = { viewModel.setShowOnboardingSheet(false) }
                    )
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                if (isExpandedScreen) {
                    ZedCraftNavigationRail(
                        selectedTab = uiState.selectedTab,
                        safeTopInset = safeTopInset,
                        safeBottomInset = safeBottomInset,
                        onSelectTab = { viewModel.selectTab(it) }
                    )
                }

                // Fixed Full-Screen App Shell Column:
                // 1) Fixed Top Header below the status bar
                // 2) Scrollable Content Area in the middle
                // 3) Fixed Bottom Tab Bar above the navigation bar
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    val headerTitle = uiState.activeDetailScreen?.title ?: when (uiState.selectedTab) {
                        PrimaryTab.Home -> "ZEDCraft"
                        PrimaryTab.Optimize -> "Optimize & Apply to YouTube"
                        PrimaryTab.Research -> "Keyword, Trend & Competitor Radar"
                        PrimaryTab.Zd -> "ZD • Growth & Strategy Coach"
                        PrimaryTab.Profile -> "Channel Profile & Creator Tools"
                    }

                    val headerSubtitle = when {
                        uiState.activeDetailScreen == DetailDestination.Credits -> "About ZEDCraft & Creator"
                        uiState.activeDetailScreen != null -> "Tap back to return"
                        uiState.activeChannel != null -> "${uiState.activeChannel.title} (${uiState.activeChannel.handle})"
                        else -> "Connect your channel to optimize live videos"
                    }

                    FixedAppHeader(
                        title = headerTitle,
                        subtitle = headerSubtitle,
                        activeChannelTitle = uiState.activeChannel?.title,
                        activeChannelThumb = uiState.activeChannel?.thumbnailUrl,
                        showBackButton = uiState.activeDetailScreen != null,
                        safeTopInset = safeTopInset,
                        onBackClick = { viewModel.navigateBackFromDetail() },
                        onSwitchChannelClick = { viewModel.setShowOnboardingSheet(true) },
                        onOpenCreditsClick = { viewModel.openDetailScreen(DetailDestination.Credits) }
                    )

                    // Center Screen Content Area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        AnimatedContent(
                            targetState = Pair(uiState.selectedTab, uiState.activeDetailScreen),
                            transitionSpec = {
                                fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(150))
                            },
                            label = "zedcraft_screen_transition"
                        ) { (tab, detail) ->
                            if (detail != null) {
                                when (detail) {
                                    DetailDestination.VideoAnalyzer -> VideoDeepAnalyzerScreen(
                                        uiState = uiState,
                                        viewModel = viewModel
                                    )
                                    DetailDestination.ChannelHealthAudit -> ChannelHealthAuditScreen(
                                        uiState = uiState
                                    )
                                    DetailDestination.RevenueAndSponsorCalculator -> RevenueAndSponsorCalculatorScreen(
                                        uiState = uiState,
                                        viewModel = viewModel
                                    )
                                    DetailDestination.LaunchChecklist -> LaunchChecklistScreen(
                                        items = checklistItems,
                                        viewModel = viewModel
                                    )
                                    DetailDestination.SavedVault -> SavedVaultScreen(
                                        savedIdeas = savedIdeas,
                                        viewModel = viewModel
                                    )
                                    DetailDestination.Credits -> CreditsScreen(
                                        uiState = uiState,
                                        viewModel = viewModel
                                    )
                                    DetailDestination.PrivacyAndTerms -> PrivacyAndTermsScreen()
                                }
                            } else {
                                when (tab) {
                                    PrimaryTab.Home -> HomeScreen(
                                        uiState = uiState,
                                        viewModel = viewModel
                                    )
                                    PrimaryTab.Optimize -> OptimizeScreen(
                                        uiState = uiState,
                                        viewModel = viewModel
                                    )
                                    PrimaryTab.Research -> ResearchScreen(
                                        uiState = uiState,
                                        viewModel = viewModel
                                    )
                                    PrimaryTab.Zd -> ZdAssistantScreen(
                                        uiState = uiState,
                                        chatMessages = zdMessages,
                                        viewModel = viewModel
                                    )
                                    PrimaryTab.Profile -> ProfileScreen(
                                        uiState = uiState,
                                        savedIdeasCount = savedIdeas.size,
                                        checklistCompletedCount = checklistItems.count { item -> item.isCompleted },
                                        checklistTotalCount = checklistItems.size,
                                        viewModel = viewModel
                                    )
                                }
                            }
                        }
                    }

                    // Fixed Bottom Navigation Bar on Compact/Medium Screens
                    if (!isExpandedScreen) {
                        ZedCraftBottomNavigationBar(
                            selectedTab = uiState.selectedTab,
                            safeBottomInset = safeBottomInset,
                            onSelectTab = { viewModel.selectTab(it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FixedAppHeader(
    title: String,
    subtitle: String,
    activeChannelTitle: String?,
    activeChannelThumb: String?,
    showBackButton: Boolean,
    safeTopInset: Dp,
    onBackClick: () -> Unit,
    onSwitchChannelClick: () -> Unit,
    onOpenCreditsClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ZedGlassSurface)
            .padding(top = safeTopInset)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showBackButton) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(ZedDarkSurfaceElevated)
                            .testTag("header_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = ZedCyanPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.ic_zedcraft_logo),
                        contentDescription = "ZEDCraft Logo",
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onSwitchChannelClick() }
                        .testTag("header_channel_switch_pill"),
                    shape = RoundedCornerShape(20.dp),
                    color = ZedDarkSurfaceElevated,
                    border = BorderStroke(
                        1.dp,
                        if (activeChannelTitle != null) ZedEmeraldSuccess.copy(alpha = 0.55f) else ZedCyanPrimary.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!activeChannelThumb.isNullOrBlank()) {
                            AsyncImage(
                                model = activeChannelThumb,
                                contentDescription = activeChannelTitle,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (activeChannelTitle != null) ZedEmeraldSuccess else ZedCyanPrimary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = activeChannelTitle?.take(12) ?: "Connect",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1
                        )
                    }
                }

                IconButton(
                    onClick = onOpenCreditsClick,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(ZedDarkSurfaceElevated)
                        .testTag("header_credits_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Credits",
                        tint = ZedCyanPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        HorizontalDivider(color = ZedCardBorder.copy(alpha = 0.5f))
    }
}

@Composable
private fun ZedCraftBottomNavigationBar(
    selectedTab: PrimaryTab,
    safeBottomInset: Dp,
    onSelectTab: (PrimaryTab) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ZedGlassSurface)
            .padding(bottom = safeBottomInset)
    ) {
        HorizontalDivider(color = ZedCardBorder.copy(alpha = 0.5f))
        NavigationBar(
            containerColor = ZedGlassSurface,
            contentColor = Color.White,
            tonalElevation = 8.dp,
            windowInsets = WindowInsets(0, 0, 0, 0)
        ) {
            PrimaryTab.entries.forEach { tab ->
                val isSelected = selectedTab == tab
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onSelectTab(tab) },
                    icon = {
                        Icon(
                            imageVector = tab.toIcon(),
                            contentDescription = tab.label
                        )
                    },
                    label = {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ZedDarkBackground,
                        selectedTextColor = ZedCyanPrimary,
                        indicatorColor = ZedCyanPrimary,
                        unselectedIconColor = ZedTextSecondary,
                        unselectedTextColor = ZedTextSecondary
                    ),
                    modifier = Modifier.testTag("bottom_tab_${tab.name.lowercase()}")
                )
            }
        }
    }
}

@Composable
private fun ZedCraftNavigationRail(
    selectedTab: PrimaryTab,
    safeTopInset: Dp,
    safeBottomInset: Dp,
    onSelectTab: (PrimaryTab) -> Unit
) {
    NavigationRail(
        containerColor = ZedGlassSurface,
        contentColor = Color.White,
        windowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier
            .fillMaxHeight()
            .background(ZedGlassSurface)
            .padding(top = safeTopInset, bottom = safeBottomInset)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        PrimaryTab.entries.forEach { tab ->
            val isSelected = selectedTab == tab
            NavigationRailItem(
                selected = isSelected,
                onClick = { onSelectTab(tab) },
                icon = {
                    Icon(
                        imageVector = tab.toIcon(),
                        contentDescription = tab.label
                    )
                },
                label = {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = ZedDarkBackground,
                    selectedTextColor = ZedCyanPrimary,
                    indicatorColor = ZedCyanPrimary,
                    unselectedIconColor = ZedTextSecondary,
                    unselectedTextColor = ZedTextSecondary
                ),
                modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}")
            )
        }
    }
}

private fun PrimaryTab.toIcon(): ImageVector = when (this) {
    PrimaryTab.Home -> Icons.Default.Home
    PrimaryTab.Optimize -> Icons.Default.Tune
    PrimaryTab.Research -> Icons.Default.Explore
    PrimaryTab.Zd -> Icons.Default.AutoAwesome
    PrimaryTab.Profile -> Icons.Default.Person
}
