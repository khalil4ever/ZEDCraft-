package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.ui.screens.ChannelHealthCheckScreen
import com.example.ui.screens.GitHubUpdaterScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OnboardingAndConnectScreen
import com.example.ui.screens.OptimizeScreen
import com.example.ui.screens.PrivacyAndTermsScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ResearchScreen
import com.example.ui.screens.SavedWorkDetailScreen
import com.example.ui.screens.SetupGuideScreen
import com.example.ui.screens.VideoAnalyzerAndAuditScreen
import com.example.ui.screens.ZdAssistantScreen
import com.example.ui.screens.attemptAutoSignIn
import com.example.ui.screens.rememberYouTubeReadOnlyOAuthLauncher
import com.example.ui.theme.ZEDCraftTheme
import com.example.ui.theme.ZedCyan
import com.example.ui.theme.ZedSuccess
import com.example.ui.theme.ZedViolet
import com.example.ui.viewmodel.DetailDestination
import com.example.ui.viewmodel.PrimaryTab
import com.example.ui.viewmodel.ZedCraftUiState
import com.example.ui.viewmodel.ZedCraftViewModel
import com.example.util.configureAppCheck

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.parseColor("#111827")),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.parseColor("#111827"))
        )
        setContent {
            val viewModel: ZedCraftViewModel = viewModel(factory = ZedCraftViewModel.Factory)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val cachedCount by viewModel.cachedItemsCount.collectAsStateWithLifecycle()

            ZEDCraftTheme(darkTheme = uiState.isDarkTheme) {
                ZedCraftAppShell(
                    uiState = uiState,
                    cachedItemsCount = cachedCount,
                    viewModel = viewModel
                )
            }
        }
    }
}

/**
 * Returns the top safe-area inset (status bar / display cutout) with a guaranteed minimum
 * fallback of 28.dp so the top header never overlaps the system status bar icons (time, signal, battery).
 */
@Composable
private fun rememberSafeTopInset(): Dp {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val cutoutTop = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    return maxOf(statusTop, cutoutTop, 28.dp)
}

/**
 * Returns the bottom safe-area inset (navigation gesture bar) with a guaranteed fallback.
 */
@Composable
private fun rememberSafeBottomInset(): Dp {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return maxOf(navBottom, 8.dp)
}

@Composable
fun ZedCraftAppShell(
    uiState: ZedCraftUiState,
    cachedItemsCount: Int,
    viewModel: ZedCraftViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val safeTopInset = rememberSafeTopInset()
    val safeBottomInset = rememberSafeBottomInset()

    val triggerYouTubeOAuth = rememberYouTubeReadOnlyOAuthLauncher(
        onTokenReceived = { token ->
            viewModel.onYouTubeOAuthTokenReceived(token)
        },
        onError = { msg ->
            viewModel.showToast(msg)
        }
    )

    // Attempt silent auto sign-in once on launch
    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {},
            onUnauthenticated = {},
            scope = scope
        )
    }

    // Display transient toast messages in Snackbar
    LaunchedEffect(uiState.toastMessage) {
        val msg = uiState.toastMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.showToast(null)
        }
    }

    // Onboarding & Pre-Auth Detail Screens (with full Status Bar & Navigation Bar Safe Area protection)
    if (!uiState.hasCompletedOnboarding) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = safeBottomInset)
            ) {
                // Dedicated Status Bar Safe-Area Band matching Header Surface
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(safeTopInset)
                        .background(MaterialTheme.colorScheme.surface)
                )
                Box(modifier = Modifier.weight(1f)) {
                    when (uiState.detailDestination) {
                        is DetailDestination.PrivacyAndTerms -> {
                            PrivacyAndTermsScreen(viewModel = viewModel)
                        }
                        is DetailDestination.SetupGuide -> {
                            SetupGuideScreen(uiState = uiState, viewModel = viewModel)
                        }
                        is DetailDestination.GitHubUpdater -> {
                            GitHubUpdaterScreen(uiState = uiState, viewModel = viewModel)
                        }
                        else -> {
                            OnboardingAndConnectScreen(
                                onGoogleSignInSuccess = {
                                    viewModel.showToast("Signed in with Google!")
                                },
                                onYouTubeOAuthSuccess = { accessToken ->
                                    viewModel.onYouTubeOAuthTokenReceived(accessToken)
                                },
                                onConnectByHandle = { handle ->
                                    viewModel.connectChannelByQuery(handle)
                                },
                                onUsePublicToolsWithoutConnecting = {
                                    viewModel.enterPublicToolsWithoutConnecting()
                                },
                                onOpenPrivacyAndTerms = {
                                    viewModel.openDetail(DetailDestination.PrivacyAndTerms)
                                },
                                onOpenGitHubUpdater = {
                                    viewModel.openDetail(DetailDestination.GitHubUpdater)
                                    viewModel.checkForGitHubUpdates()
                                }
                            )
                        }
                    }
                }
            }
        }
        return
    }

    // Fixed Native App Shell (Locked viewport height, fixed header below status bar & bottom bar above gesture bar)
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            ZedCraftTopHeader(
                uiState = uiState,
                safeTopInset = safeTopInset,
                onOpenProfileOrChannel = { viewModel.selectPrimaryTab(PrimaryTab.PROFILE) },
                onOpenGitHubUpdater = {
                    viewModel.openDetail(DetailDestination.GitHubUpdater)
                    viewModel.checkForGitHubUpdates()
                },
                onOpenSetup = { viewModel.openDetail(DetailDestination.SetupGuide) },
                onToggleTheme = { viewModel.toggleDarkTheme() }
            )
        },
        floatingActionButton = {
            if (uiState.currentTab != PrimaryTab.ZD && uiState.detailDestination is DetailDestination.None) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.selectPrimaryTab(PrimaryTab.ZD) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.testTag("ask_zd_fab")
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_zd_avatar_1794133253177),
                        contentDescription = "Ask ZD AI",
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ask ZD", fontWeight = FontWeight.Bold)
                }
            }
        },
        bottomBar = {
            ZedCraftBottomNavigationBar(
                currentTab = uiState.currentTab,
                safeBottomInset = safeBottomInset,
                onSelectTab = { viewModel.selectPrimaryTab(it) }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            when (val detail = uiState.detailDestination) {
                is DetailDestination.VideoAnalyzerAndAudit -> {
                    VideoAnalyzerAndAuditScreen(uiState = uiState, viewModel = viewModel)
                }
                is DetailDestination.ChannelHealthCheck -> {
                    ChannelHealthCheckScreen(uiState = uiState, viewModel = viewModel)
                }
                is DetailDestination.SetupGuide -> {
                    SetupGuideScreen(uiState = uiState, viewModel = viewModel)
                }
                is DetailDestination.PrivacyAndTerms -> {
                    PrivacyAndTermsScreen(viewModel = viewModel)
                }
                is DetailDestination.GitHubUpdater -> {
                    GitHubUpdaterScreen(uiState = uiState, viewModel = viewModel)
                }
                is DetailDestination.SavedWorkDetail -> {
                    SavedWorkDetailScreen(item = detail.item, viewModel = viewModel)
                }
                DetailDestination.None -> {
                    Crossfade(
                        targetState = uiState.currentTab,
                        animationSpec = tween(durationMillis = 180),
                        label = "tab_crossfade"
                    ) { tab ->
                        when (tab) {
                            PrimaryTab.HOME -> HomeScreen(
                                uiState = uiState,
                                viewModel = viewModel,
                                onRequestYouTubeOAuth = triggerYouTubeOAuth
                            )
                            PrimaryTab.OPTIMIZE -> OptimizeScreen(
                                uiState = uiState,
                                viewModel = viewModel
                            )
                            PrimaryTab.RESEARCH -> ResearchScreen(
                                uiState = uiState,
                                viewModel = viewModel
                            )
                            PrimaryTab.ZD -> ZdAssistantScreen(
                                uiState = uiState,
                                viewModel = viewModel
                            )
                            PrimaryTab.PROFILE -> ProfileScreen(
                                uiState = uiState,
                                cachedItemsCount = cachedItemsCount,
                                viewModel = viewModel,
                                onRequestYouTubeOAuth = triggerYouTubeOAuth
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZedCraftTopHeader(
    uiState: ZedCraftUiState,
    safeTopInset: Dp,
    onOpenProfileOrChannel: () -> Unit,
    onOpenGitHubUpdater: () -> Unit,
    onOpenSetup: () -> Unit,
    onToggleTheme: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(top = safeTopInset)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Brand Logo + ZEDCraft Title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.ic_zedcraft_logo),
                    contentDescription = "ZEDCraft Logo",
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "ZEDCraft",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Active Channel Pill + GitHub Updates + Setup & Theme Actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val activeCh = uiState.activeChannel
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (activeCh != null) ZedSuccess.copy(alpha = 0.14f)
                    else ZedViolet.copy(alpha = 0.14f),
                    border = BorderStroke(
                        1.dp,
                        if (activeCh != null) ZedSuccess.copy(alpha = 0.45f)
                        else ZedViolet.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier
                        .clickable { onOpenProfileOrChannel() }
                        .testTag("header_channel_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (activeCh != null && activeCh.thumbnailUrl.isNotBlank()) {
                            AsyncImage(
                                model = activeCh.thumbnailUrl,
                                contentDescription = activeCh.title,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = activeCh?.title ?: "Connect Channel",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (activeCh != null) ZedSuccess else ZedViolet,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onOpenGitHubUpdater,
                    modifier = Modifier.testTag("header_github_update_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdateAlt,
                        contentDescription = "Check for Updates",
                        tint = ZedCyan
                    )
                }

                IconButton(
                    onClick = onOpenSetup,
                    modifier = Modifier.testTag("header_setup_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "API & OAuth Setup Guide",
                        tint = if (uiState.isYouTubeApiKeyConfigured) ZedCyan else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onToggleTheme,
                    modifier = Modifier.testTag("header_theme_button")
                ) {
                    Icon(
                        imageVector = if (uiState.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Toggle Dark or Light Mode",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    }
}

@Composable
private fun ZedCraftBottomNavigationBar(
    currentTab: PrimaryTab,
    safeBottomInset: Dp,
    onSelectTab: (PrimaryTab) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(bottom = safeBottomInset)
    ) {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            windowInsets = WindowInsets(0, 0, 0, 0)
        ) {
            val tabs = listOf(
                PrimaryTab.HOME to Icons.Default.Dashboard,
                PrimaryTab.OPTIMIZE to Icons.Default.Tune,
                PrimaryTab.RESEARCH to Icons.Default.Search,
                PrimaryTab.ZD to Icons.Default.AutoAwesome,
                PrimaryTab.PROFILE to Icons.Default.AccountCircle
            )
            tabs.forEach { (tab, icon) ->
                val selected = currentTab == tab
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelectTab(tab) },
                    icon = {
                        Icon(
                            imageVector = icon,
                            contentDescription = tab.label
                        )
                    },
                    label = {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                )
            }
        }
    }
}
