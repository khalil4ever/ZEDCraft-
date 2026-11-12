package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.filled.RocketLaunch
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import com.example.ui.theme.ZedTextPrimary
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
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

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

    // When the app returns to the foreground:
    // 1. Silently check for a newer version in the background if online
    // 2. If returning from "Install unknown apps" settings, automatically continue installation
    DisposableEffect(lifecycleOwner, uiState.pendingInstallAfterPermission, uiState.downloadedApkFile) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onAppForegrounded(isInitialOpen = false)
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

    // Unified Glowing Auto-Update Pop-Up Modal (Prompt -> In-Card Download -> Install / Permission)
    val releaseInfo = uiState.githubReleaseInfo
    val showModal = (uiState.showAutoUpdateDialog && releaseInfo != null && releaseInfo.isUpdateAvailable) ||
        uiState.isDownloadingUpdate ||
        !uiState.updateDownloadError.isNullOrBlank() ||
        uiState.showUnknownSourcesDialog

    if (showModal) {
        ZedCraftGlowingUpdateModal(
            uiState = uiState,
            viewModel = viewModel,
            onOpenUnknownSourcesSettings = {
                val settingsIntent = viewModel.gitHubUpdateClient.buildManageUnknownSourcesIntent(context)
                context.startActivity(settingsIntent)
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
                    .imePadding()
                    .padding(bottom = if (imeVisible) 0.dp else safeBottomInset)
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
                        .imePadding()
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

                    // Fixed Bottom Navigation Bar on Compact/Medium Screens (hidden when IME keyboard is visible)
                    if (!isExpandedScreen && !imeVisible) {
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
                            .clip(RoundedCornerShape(13.dp))
                            .background(ZedDarkSurfaceElevated)
                            .border(1.dp, ZedCardBorder, RoundedCornerShape(13.dp))
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
                        color = com.example.ui.theme.ZedTextPrimary,
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
                        if (activeChannelTitle != null) ZedEmeraldSuccess.copy(alpha = 0.6f) else ZedCyanPrimary.copy(alpha = 0.55f)
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
                            color = com.example.ui.theme.ZedTextPrimary,
                            maxLines = 1
                        )
                    }
                }

                IconButton(
                    onClick = onOpenCreditsClick,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(ZedDarkSurfaceElevated)
                        .border(1.dp, ZedCardBorder, RoundedCornerShape(13.dp))
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
            contentColor = ZedTextPrimary,
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
        contentColor = ZedTextPrimary,
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

@Composable
private fun ZedCraftGlowingUpdateModal(
    uiState: ZedCraftUiState,
    viewModel: ZedCraftViewModel,
    onOpenUnknownSourcesSettings: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    val animateDismiss: (() -> Unit) -> Unit = { onFinished ->
        scope.launch {
            isVisible = false
            delay(210L)
            onFinished()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "update_modal_glow")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "border_glow_alpha"
    )
    val iconScalePulse by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "icon_scale_pulse"
    )

    val releaseInfo = uiState.githubReleaseInfo
    val versionStr = releaseInfo?.cleanVersionNumber ?: BuildConfig.VERSION_NAME

    // Clean short "What's new" lines (skip markdown headers and blank lines)
    val whatsNewLines = remember(releaseInfo?.releaseNotes) {
        releaseInfo?.releaseNotes
            ?.lines()
            ?.map { line ->
                line.trim()
                    .removePrefix("###")
                    .removePrefix("##")
                    .removePrefix("#")
                    .removePrefix("- ")
                    .removePrefix("* ")
                    .replace("**", "")
                    .trim()
            }
            ?.filter { it.isNotBlank() && !it.startsWith("ZEDCraft v", ignoreCase = true) }
            ?.take(6)
            .orEmpty()
    }

    val modalMode = when {
        uiState.showUnknownSourcesDialog -> "PERMISSION"
        !uiState.updateDownloadError.isNullOrBlank() -> "ERROR"
        uiState.isDownloadingUpdate -> "DOWNLOADING"
        else -> "PROMPT"
    }

    Dialog(
        onDismissRequest = {
            if (!uiState.isDownloadingUpdate) {
                animateDismiss {
                    if (uiState.showUnknownSourcesDialog) {
                        viewModel.setShowUnknownSourcesDialog(show = false, pendingInstall = false)
                    } else if (!uiState.updateDownloadError.isNullOrBlank()) {
                        viewModel.dismissUpdateDownloadError()
                    } else {
                        viewModel.dismissAutoUpdateDialog()
                    }
                }
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !uiState.isDownloadingUpdate,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            ZedDarkBackground.copy(alpha = 0.88f),
                            ZedDarkBackground.copy(alpha = 0.96f)
                        )
                    )
                )
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(240)) + scaleIn(initialScale = 0.88f, animationSpec = tween(260, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.92f, animationSpec = tween(200)) + slideOutVertically(targetOffsetY = { it / 6 }, animationSpec = tween(200))
            ) {
                // Outer glowing halo + dark glassmorphism card in #D9FF00 & #007E08
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 28.dp,
                            shape = RoundedCornerShape(28.dp),
                            ambientColor = ZedCyanPrimary.copy(alpha = pulseAlpha * 0.65f),
                            spotColor = com.example.ui.theme.ZedBrandKellyGreen.copy(alpha = pulseAlpha * 0.85f)
                        )
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    ZedDarkSurfaceElevated,
                                    ZedGlassSurface
                                )
                            )
                        )
                        .border(
                            width = 1.8.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    ZedCyanPrimary.copy(alpha = pulseAlpha),
                                    com.example.ui.theme.ZedBrandKellyGreen.copy(alpha = pulseAlpha),
                                    ZedCyanPrimary.copy(alpha = pulseAlpha * 0.8f)
                                )
                            ),
                            shape = RoundedCornerShape(28.dp)
                        )
                        .padding(24.dp)
                ) {
                    AnimatedContent(
                        targetState = modalMode,
                        transitionSpec = {
                            fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                        },
                        label = "update_modal_state_transition"
                    ) { mode ->
                        when (mode) {
                            "PROMPT" -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // Glowing modern 2026 rocket icon badge
                                    Box(
                                        modifier = Modifier
                                            .size((64 * iconScalePulse).dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.linearGradient(
                                                    colors = listOf(
                                                        ZedCyanPrimary.copy(alpha = 0.24f),
                                                        ZedPurpleAccent.copy(alpha = 0.28f)
                                                    )
                                                )
                                            )
                                            .border(
                                                width = 1.5.dp,
                                                brush = Brush.linearGradient(
                                                    colors = listOf(ZedCyanPrimary, ZedPurpleAccent)
                                                ),
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.RocketLaunch,
                                            contentDescription = "New Update Available",
                                            tint = ZedCyanPrimary,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Text(
                                        text = "New Update Available",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = ZedTextPrimary,
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Surface(
                                        color = ZedCyanPrimary.copy(alpha = 0.14f),
                                        shape = RoundedCornerShape(50),
                                        border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = "ZEDCraft version $versionStr is out",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = ZedCyanPrimary,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                        )
                                    }

                                    if (whatsNewLines.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Surface(
                                            color = ZedDarkBackground.copy(alpha = 0.65f),
                                            shape = RoundedCornerShape(16.dp),
                                            border = BorderStroke(1.dp, ZedCardBorder),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(max = 104.dp)
                                                    .verticalScroll(rememberScrollState())
                                                    .padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = "What's new",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = ZedEmeraldSuccess
                                                )
                                                whatsNewLines.forEach { bullet ->
                                                    Row(verticalAlignment = Alignment.Top) {
                                                        Text(
                                                            text = "• ",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = ZedCyanPrimary,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = bullet,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = ZedTextSecondary
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(20.dp))

                                    // 1. "Update Now" (primary, glowing, full width)
                                    Button(
                                        onClick = { viewModel.startInAppUpdateDownload() },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                            .shadow(
                                                elevation = 14.dp,
                                                shape = RoundedCornerShape(14.dp),
                                                ambientColor = ZedCyanPrimary.copy(alpha = 0.6f),
                                                spotColor = ZedCyanPrimary.copy(alpha = 0.7f)
                                            )
                                            .testTag("dialog_update_now_button"),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ZedCyanPrimary,
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
                                            text = "Update Now",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // 2. "Update Later" (secondary, subtle)
                                    TextButton(
                                        onClick = {
                                            animateDismiss {
                                                viewModel.dismissAutoUpdateDialog()
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .testTag("dialog_update_later_button"),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = "Update Later",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = ZedTextSecondary
                                        )
                                    }
                                }
                            }

                            "DOWNLOADING" -> {
                                val rawPct = uiState.downloadProgressPercent.coerceIn(0, 100)
                                val animatedProgress by animateFloatAsState(
                                    targetValue = rawPct / 100f,
                                    animationSpec = tween(durationMillis = 220),
                                    label = "apk_download_progress"
                                )

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(ZedCyanPrimary.copy(alpha = 0.16f))
                                            .border(1.dp, ZedCyanPrimary.copy(alpha = 0.6f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SystemUpdateAlt,
                                            contentDescription = "Downloading Update",
                                            tint = ZedCyanPrimary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Text(
                                        text = "Downloading Update...",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = ZedTextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "ZEDCraft version $versionStr",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ZedCyanPrimary
                                    )

                                    Spacer(modifier = Modifier.height(18.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "$rawPct%",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = ZedCyanPrimary
                                        )
                                        Text(
                                            text = if (uiState.totalMb > 0f) {
                                                String.format(Locale.US, "%.1f MB of %.1f MB", uiState.downloadedMb, uiState.totalMb)
                                            } else {
                                                String.format(Locale.US, "%.1f MB downloaded", uiState.downloadedMb)
                                            },
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = ZedTextSecondary
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    LinearProgressIndicator(
                                        progress = { animatedProgress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(10.dp)
                                            .clip(RoundedCornerShape(6.dp)),
                                        color = ZedCyanPrimary,
                                        trackColor = ZedDarkBackground
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Text(
                                        text = "The installer will open automatically as soon as the download completes.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ZedTextMuted,
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    OutlinedButton(
                                        onClick = {
                                            animateDismiss {
                                                viewModel.cancelInAppUpdateDownload()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, ZedCardBorder)
                                    ) {
                                        Text("Cancel", color = ZedTextSecondary)
                                    }
                                }
                            }

                            "ERROR" -> {
                                val errMsg = uiState.updateDownloadError.orEmpty()
                                val isConnectionLost = errMsg.contains("Connection lost", ignoreCase = true)

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(ZedCoralError.copy(alpha = 0.16f))
                                            .border(1.dp, ZedCoralError.copy(alpha = 0.6f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Update Error",
                                            tint = ZedCoralError,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Text(
                                        text = if (isConnectionLost) "Connection lost" else "Update Paused",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = ZedTextPrimary
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = errMsg,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ZedTextSecondary,
                                        textAlign = TextAlign.Center
                                    )

                                    if (uiState.downloadedMb > 0f) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = String.format(
                                                Locale.US,
                                                "Saved %.1f MB — tap Retry to resume where it left off.",
                                                uiState.downloadedMb
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ZedCyanPrimary,
                                            textAlign = TextAlign.Center
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(18.dp))

                                    Button(
                                        onClick = { viewModel.startInAppUpdateDownload() },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ZedCyanPrimary,
                                            contentColor = ZedDarkBackground
                                        )
                                    ) {
                                        Text("Retry", fontWeight = FontWeight.ExtraBold)
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    TextButton(
                                        onClick = {
                                            animateDismiss {
                                                viewModel.cancelInAppUpdateDownload()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Update Later", color = ZedTextSecondary)
                                    }
                                }
                            }

                            "PERMISSION" -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(ZedAmberWarning.copy(alpha = 0.16f))
                                            .border(1.dp, ZedAmberWarning.copy(alpha = 0.6f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Security,
                                            contentDescription = "Permission Required",
                                            tint = ZedAmberWarning,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Text(
                                        text = "Allow Update Installation",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = ZedTextPrimary,
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = "Your update is downloaded and ready! Tap 'Open Settings' below and turn on 'Allow from this source' so ZEDCraft can install the update automatically.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ZedTextSecondary,
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(18.dp))

                                    Button(
                                        onClick = onOpenUnknownSourcesSettings,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ZedCyanPrimary,
                                            contentColor = ZedDarkBackground
                                        )
                                    ) {
                                        Text("Open Settings", fontWeight = FontWeight.ExtraBold)
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    TextButton(
                                        onClick = {
                                            animateDismiss {
                                                viewModel.setShowUnknownSourcesDialog(show = false, pendingInstall = false)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Update Later", color = ZedTextSecondary)
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

