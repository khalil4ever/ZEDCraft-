package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.ai.ThumbnailVisionResponse
import com.example.ai.TitleOptimizerResponse
import com.example.ai.TitleSuggestionItem
import com.example.ai.ZedCraftAiService
import com.example.data.local.ChecklistItemEntity
import com.example.data.local.SavedIdeaEntity
import com.example.data.local.VideoRevisionBackupEntity
import com.example.data.local.YouTubeCacheDatabase
import com.example.data.local.ZdChatMessageEntity
import com.example.data.remote.ApkDownloadProgress
import com.example.data.remote.ChannelHealthReport
import com.example.data.remote.CompetitorComparison
import com.example.data.remote.GitHubReleaseInfo
import com.example.data.remote.GitHubUpdateClient
import com.example.data.remote.KeywordResearchResult
import com.example.data.remote.VideoDeepAudit
import com.example.data.remote.YouTubeApiClient
import com.example.data.remote.YouTubeApiException
import com.example.data.remote.YouTubeWriteOutcome
import com.example.data.remote.YtAnalyticsSummary
import com.example.data.remote.YtChannelDetail
import com.example.data.remote.YtCommentItem
import com.example.data.remote.YtVideoDetail
import com.example.ui.screens.YouTubeOAuthHelper
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class PrimaryTab(val label: String) {
    Home("Home"),
    Optimize("Optimize"),
    Research("Research"),
    Zd("ZD"),
    Profile("Profile")
}

enum class OptimizeSubTab(val label: String) {
    Title("Title"),
    Description("Description"),
    Tags("Tags"),
    Thumbnail("Thumbnail"),
    ScriptAndHook("Script & Hook"),
    ShortsAndRemix("Shorts & Remix")
}

enum class ResearchSubTab(val label: String) {
    Keywords("Keywords"),
    TrendsAndIdeas("Trends & Ideas"),
    Competitors("Competitors"),
    Comments("Comments")
}

enum class DetailDestination(val title: String) {
    VideoAnalyzer("Video Deep Analyzer"),
    ChannelHealthAudit("Channel Health & Policy Audit"),
    RevenueAndSponsorCalculator("Revenue & Sponsor Deal Calculator"),
    LaunchChecklist("Pre-Publish & Growth Checklists"),
    SavedVault("Saved Ideas & Templates Vault"),
    Credits("Credits"),
    PrivacyAndTerms("Privacy Policy & Terms")
}

data class ZedCraftUiState(
    val isAppReadyForContent: Boolean = false,
    val selectedTab: PrimaryTab = PrimaryTab.Home,
    val selectedOptimizeSubTab: OptimizeSubTab = OptimizeSubTab.Title,
    val selectedResearchSubTab: ResearchSubTab = ResearchSubTab.Keywords,
    val activeDetailScreen: DetailDestination? = null,
    val isDarkTheme: Boolean = true,
    val showOnboardingSheet: Boolean = false,

    // Connected Channel & Analytics State
    val isApiKeyConfigured: Boolean = false,
    val oauthAccessToken: String? = null,
    val activeChannel: YtChannelDetail? = null,
    val activeChannelVideos: List<YtVideoDetail> = emptyList(),
    val channelHealthReport: ChannelHealthReport? = null,
    val analyticsSummary: YtAnalyticsSummary? = null,
    val isLoadingChannel: Boolean = false,
    val channelError: String? = null,

    // Selected Video from "My Videos" for Direct Optimization & Live YouTube Updates
    val selectedTargetVideo: YtVideoDetail? = null,
    val customTitleDraft: String = "",
    val customTitleScoreResult: TitleSuggestionItem? = null,
    val isScoringCustomTitle: Boolean = false,
    val isApplyingToYouTube: Boolean = false,
    val applySuccessOutcome: YouTubeWriteOutcome? = null,
    val applyError: String? = null,
    val showPhoneVerifyDialog: Boolean = false,

    // Video Deep Analyzer State
    val inspectedVideoAudit: VideoDeepAudit? = null,
    val inspectedVideoComments: List<YtCommentItem> = emptyList(),
    val isLoadingVideoAudit: Boolean = false,
    val videoAuditError: String? = null,
    val videoAiCoachingSummary: String? = null,
    val isGeneratingVideoCoaching: Boolean = false,

    // Optimize Tab State
    val optimizeTopicInput: String = "",
    val optimizeNicheInput: String = "Tech & Creator Economy",
    val optimizeTitleInput: String = "",
    val optimizeDescInput: String = "",
    val optimizeTagsInput: String = "",
    val titleOptimizerResult: TitleOptimizerResponse? = null,
    val selectedThumbnailBitmap: Bitmap? = null,
    val secondThumbnailBitmap: Bitmap? = null,
    val thumbnailVisionResult: ThumbnailVisionResponse? = null,
    val scriptOutputMarkdown: String? = null,
    val shortsPlanMarkdown: String? = null,
    val isOptimizing: Boolean = false,
    val optimizeError: String? = null,

    // Research Tab State
    val keywordSeedInput: String = "",
    val keywordResearchResult: KeywordResearchResult? = null,
    val isResearchingKeywords: Boolean = false,
    val researchError: String? = null,
    val competitorInputA: String = "",
    val competitorInputB: String = "",
    val competitorComparison: CompetitorComparison? = null,
    val isComparingCompetitors: Boolean = false,
    val trendIdeasMarkdown: String? = null,
    val isGeneratingTrendIdeas: Boolean = false,
    val commentVideoUrlInput: String = "",
    val commentList: List<YtCommentItem> = emptyList(),
    val commentSentimentMarkdown: String? = null,
    val isAnalyzingComments: Boolean = false,

    // ZD Assistant State
    val isZdThinking: Boolean = false,
    val streamingZdMessage: String? = null,
    val zdError: String? = null,
    val lastFailedZdPrompt: String? = null,

    // Revenue Calculator State
    val calcMonthlyViews: Long = 150_000L,
    val calcNicheRpmUsd: Double = 6.50,
    val calcShortsViews: Long = 250_000L,
    val calcAvgViewsPerVideo: Long = 25_000L,

    // GitHub Auto-Update State
    val githubRepoPath: String = GitHubUpdateClient.DEFAULT_GITHUB_REPO,
    val isCheckingUpdate: Boolean = false,
    val githubReleaseInfo: GitHubReleaseInfo? = null,
    val updateCheckError: String? = null,
    val showAutoUpdateDialog: Boolean = false,
    val isDownloadingUpdate: Boolean = false,
    val downloadProgressPercent: Int = 0,
    val downloadedMb: Float = 0f,
    val totalMb: Float = 0f,
    val updateDownloadError: String? = null,
    val downloadedApkFile: File? = null,
    val showUnknownSourcesDialog: Boolean = false,
    val pendingInstallAfterPermission: Boolean = false
)

class ZedCraftViewModel(application: Application) : AndroidViewModel(application) {
    private val db = YouTubeCacheDatabase.getInstance(application)
    private val cacheDao = db.dao()
    val youTubeApi = YouTubeApiClient(cacheDao)
    val aiService = ZedCraftAiService()
    val gitHubUpdateClient = GitHubUpdateClient(application.applicationContext)

    private var activeDownloadJob: Job? = null
    private var networkCallbackRegistered = false

    private val _uiState = MutableStateFlow(
        ZedCraftUiState(
            isApiKeyConfigured = youTubeApi.isApiKeyConfigured()
        )
    )
    val uiState: StateFlow<ZedCraftUiState> = _uiState.asStateFlow()

    val savedIdeas: StateFlow<List<SavedIdeaEntity>> = cacheDao.observeSavedIdeas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val zdChatHistory: StateFlow<List<ZdChatMessageEntity>> = cacheDao.observeZdChatMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val checklistItems: StateFlow<List<ChecklistItemEntity>> = cacheDao.observeChecklistItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val videoRevisionBackups: StateFlow<List<VideoRevisionBackupEntity>> = cacheDao.observeVideoRevisionBackups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        initializeAppAndSplash()
        registerNetworkReconnectUpdateCheck()
    }

    private fun initializeAppAndSplash() {
        viewModelScope.launch {
            val startTimeMs = System.currentTimeMillis()

            // Clean up any old downloaded APKs after update
            runCatching { gitHubUpdateClient.cleanupOldApks() }

            // Seed default checklist & initial ZD welcome message
            runCatching { seedDefaultChecklistIfEmpty() }
            runCatching { seedWelcomeZdMessageIfEmpty() }

            // Attempt automatic OAuth session restore (capped at 1.8s during splash so it never blocks)
            val appContext = getApplication<Application>().applicationContext
            val savedToken = YouTubeOAuthHelper.getSavedAccessToken(appContext)
            if (!savedToken.isNullOrBlank()) {
                val refreshedToken = withTimeoutOrNull(1800L) {
                    YouTubeOAuthHelper.refreshAccessTokenSilently(appContext)
                } ?: savedToken
                _uiState.update { it.copy(oauthAccessToken = refreshedToken) }
                // Kick off channel load in background
                connectWithOAuthToken(refreshedToken)
            }

            // Trigger silent GitHub auto-update check on launch
            checkForGitHubUpdates(isSilent = true)

            // Ensure the custom 3D splash screen stays visible for at least 2.3s for a smooth entrance
            val elapsedMs = System.currentTimeMillis() - startTimeMs
            val remainingMs = (2350L - elapsedMs).coerceAtLeast(0L)
            if (remainingMs > 0L) {
                delay(remainingMs)
            }

            _uiState.update { it.copy(isAppReadyForContent = true) }
        }
    }

    private fun registerNetworkReconnectUpdateCheck() {
        val appContext = getApplication<Application>().applicationContext
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return
        runCatching {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(
                request,
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        super.onAvailable(network)
                        if (gitHubUpdateClient.shouldRunSilentCheck()) {
                            checkForGitHubUpdates(isSilent = true)
                        }
                    }
                }
            )
            networkCallbackRegistered = true
        }
    }

    fun selectTab(tab: PrimaryTab) {
        _uiState.update { it.copy(selectedTab = tab, activeDetailScreen = null) }
    }

    fun selectOptimizeSubTab(subTab: OptimizeSubTab) {
        _uiState.update {
            it.copy(
                selectedOptimizeSubTab = subTab,
                applySuccessOutcome = null,
                applyError = null
            )
        }
    }

    fun selectResearchSubTab(subTab: ResearchSubTab) {
        _uiState.update { it.copy(selectedResearchSubTab = subTab) }
    }

    fun openDetailScreen(destination: DetailDestination) {
        _uiState.update { it.copy(activeDetailScreen = destination) }
    }

    fun navigateBackFromDetail(): Boolean {
        if (_uiState.value.activeDetailScreen != null) {
            _uiState.update { it.copy(activeDetailScreen = null) }
            return true
        }
        if (_uiState.value.selectedTab != PrimaryTab.Home) {
            _uiState.update { it.copy(selectedTab = PrimaryTab.Home) }
            return true
        }
        return false
    }

    fun toggleTheme(isDark: Boolean) {
        _uiState.update { it.copy(isDarkTheme = isDark) }
    }

    fun setShowOnboardingSheet(show: Boolean) {
        _uiState.update { it.copy(showOnboardingSheet = show) }
    }

    fun refreshApiKeyStatus() {
        _uiState.update { it.copy(isApiKeyConfigured = youTubeApi.isApiKeyConfigured()) }
    }

    /**
     * Connects the user's YouTube channel using a real OAuth 2.0 Bearer access token
     * from Google Identity Services (`AuthorizationClient`).
     */
    fun connectWithOAuthToken(accessToken: String) {
        val appContext = getApplication<Application>().applicationContext
        YouTubeOAuthHelper.saveAccessToken(appContext, accessToken)

        _uiState.update {
            it.copy(
                oauthAccessToken = accessToken,
                isLoadingChannel = true,
                channelError = null
            )
        }
        viewModelScope.launch {
            val myChannelsResult = youTubeApi.fetchMyAuthorizedChannels(accessToken)
            myChannelsResult.onSuccess { channels ->
                val primary = channels.firstOrNull()
                if (primary != null) {
                    YouTubeOAuthHelper.saveAccessToken(appContext, accessToken, primary.channelId)
                    loadChannelDashboardData(primary, accessToken)
                } else {
                    _uiState.update {
                        it.copy(
                            isLoadingChannel = false,
                            channelError = "No YouTube channel was found on the selected Google account."
                        )
                    }
                }
            }.onFailure { err ->
                Log.w(TAG, "OAuth channel fetch failed", err)
                _uiState.update {
                    it.copy(
                        isLoadingChannel = false,
                        channelError = err.localizedMessage ?: "Failed to load your YouTube channel."
                    )
                }
            }
        }
    }

    /**
     * Connects any public YouTube channel by @handle, Channel ID (`UC...`), video URL, or name.
     */
    fun connectChannelByInput(query: String, forceRefresh: Boolean = false) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(channelError = "Please enter a @handle, Channel ID, or YouTube video URL.") }
            return
        }
        _uiState.update {
            it.copy(
                isLoadingChannel = true,
                channelError = null,
                isApiKeyConfigured = youTubeApi.isApiKeyConfigured()
            )
        }
        viewModelScope.launch {
            val token = _uiState.value.oauthAccessToken
            val result = youTubeApi.lookupChannel(trimmed, token, forceRefresh)
            result.onSuccess { channel ->
                loadChannelDashboardData(channel, token, forceRefresh)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingChannel = false,
                        channelError = err.localizedMessage ?: "Could not find channel '$trimmed'."
                    )
                }
            }
        }
    }

    private suspend fun loadChannelDashboardData(
        channel: YtChannelDetail,
        oauthToken: String?,
        forceRefresh: Boolean = false
    ) {
        val videosResult = youTubeApi.fetchChannelRecentVideos(
            uploadsPlaylistId = channel.uploadsPlaylistId,
            maxResults = 30,
            oauthAccessToken = oauthToken,
            forceRefresh = forceRefresh
        )
        val videos = videosResult.getOrDefault(emptyList())
        val healthReport = youTubeApi.buildChannelHealthReport(channel, videos)

        val analytics = if (!oauthToken.isNullOrBlank()) {
            youTubeApi.fetchOwnerAnalyticsReport(oauthToken).getOrNull()
        } else {
            null
        }

        val currentTargetId = _uiState.value.selectedTargetVideo?.videoId
        val selectedVideo = videos.firstOrNull { it.videoId == currentTargetId } ?: videos.firstOrNull()

        _uiState.update { state ->
            state.copy(
                activeChannel = channel,
                activeChannelVideos = videos,
                selectedTargetVideo = selectedVideo,
                optimizeTopicInput = if (state.optimizeTopicInput.isBlank() && selectedVideo != null) {
                    selectedVideo.title
                } else {
                    state.optimizeTopicInput
                },
                optimizeTitleInput = if (state.optimizeTitleInput.isBlank() && selectedVideo != null) {
                    selectedVideo.title
                } else {
                    state.optimizeTitleInput
                },
                optimizeDescInput = if (state.optimizeDescInput.isBlank() && selectedVideo != null) {
                    selectedVideo.description
                } else {
                    state.optimizeDescInput
                },
                optimizeTagsInput = if (state.optimizeTagsInput.isBlank() && selectedVideo != null) {
                    selectedVideo.tags.joinToString(", ")
                } else {
                    state.optimizeTagsInput
                },
                channelHealthReport = healthReport,
                analyticsSummary = analytics,
                isLoadingChannel = false,
                channelError = null,
                showOnboardingSheet = false
            )
        }
    }

    fun refreshActiveChannel() {
        val current = _uiState.value.activeChannel ?: return
        val token = _uiState.value.oauthAccessToken
        _uiState.update { it.copy(isLoadingChannel = true, channelError = null) }
        viewModelScope.launch {
            if (!token.isNullOrBlank() && current.isOAuthMine) {
                val freshToken = YouTubeOAuthHelper.refreshAccessTokenSilently(getApplication()) ?: token
                _uiState.update { it.copy(oauthAccessToken = freshToken) }
                loadChannelDashboardData(current, freshToken, forceRefresh = true)
            } else {
                connectChannelByInput(current.channelId, forceRefresh = true)
            }
        }
    }

    fun disconnectChannel() {
        YouTubeOAuthHelper.clearSavedAuth(getApplication())
        _uiState.update {
            it.copy(
                oauthAccessToken = null,
                activeChannel = null,
                activeChannelVideos = emptyList(),
                selectedTargetVideo = null,
                channelHealthReport = null,
                analyticsSummary = null,
                channelError = null,
                applySuccessOutcome = null,
                applyError = null
            )
        }
    }

    fun setOAuthError(message: String) {
        _uiState.update { it.copy(channelError = message, isLoadingChannel = false) }
    }

    /**
     * Selects one of the user's uploaded videos from "My Videos" to optimize and apply changes to.
     */
    fun selectTargetVideoForOptimize(
        video: YtVideoDetail,
        subTab: OptimizeSubTab = _uiState.value.selectedOptimizeSubTab,
        navigateToOptimize: Boolean = false,
        autoRunOptimizer: Boolean = false
    ) {
        _uiState.update {
            it.copy(
                selectedTargetVideo = video,
                selectedTab = if (navigateToOptimize) PrimaryTab.Optimize else it.selectedTab,
                selectedOptimizeSubTab = subTab,
                activeDetailScreen = if (navigateToOptimize) null else it.activeDetailScreen,
                optimizeTopicInput = video.title,
                optimizeTitleInput = video.title,
                optimizeDescInput = video.description,
                optimizeTagsInput = video.tags.joinToString(", "),
                customTitleDraft = "",
                customTitleScoreResult = null,
                applySuccessOutcome = null,
                applyError = null
            )
        }
        if (autoRunOptimizer) {
            runTitleDescTagsOptimizer()
        }
    }

    fun updateCustomTitleDraft(value: String) {
        _uiState.update {
            it.copy(
                customTitleDraft = value,
                customTitleScoreResult = null
            )
        }
    }

    /**
     * Scores a custom user-typed title out of 100 (clarity, curiosity, keywords, length, click appeal).
     */
    fun scoreCustomTitleDraft() {
        val draft = _uiState.value.customTitleDraft.trim()
        if (draft.length < 4) {
            _uiState.update { it.copy(optimizeError = "Enter a custom title of at least 4 characters to score it.") }
            return
        }
        _uiState.update { it.copy(isScoringCustomTitle = true, optimizeError = null) }
        viewModelScope.launch {
            val channelCtx = _uiState.value.activeChannel?.let {
                "Channel: ${it.title} (${it.handle}), Subscribers: ${it.subscriberCount}"
            } ?: "Niche: ${_uiState.value.optimizeNicheInput}"
            val result = aiService.scoreSingleCustomTitle(
                customTitle = draft,
                niche = _uiState.value.optimizeNicheInput,
                channelContext = channelCtx
            )
            result.onSuccess { scored ->
                _uiState.update {
                    it.copy(
                        isScoringCustomTitle = false,
                        customTitleScoreResult = scored
                    )
                }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        isScoringCustomTitle = false,
                        optimizeError = ZedCraftAiService.FRIENDLY_ZD_ERROR
                    )
                }
            }
        }
    }

    fun clearApplyMessages() {
        _uiState.update {
            it.copy(
                applySuccessOutcome = null,
                applyError = null,
                showPhoneVerifyDialog = false
            )
        }
    }

    private suspend fun getValidOAuthTokenForWrite(): String? {
        val current = _uiState.value.oauthAccessToken
        val refreshed = YouTubeOAuthHelper.refreshAccessTokenSilently(getApplication())
        val finalToken = refreshed ?: current
        if (!finalToken.isNullOrBlank()) {
            _uiState.update { it.copy(oauthAccessToken = finalToken) }
        }
        return finalToken
    }

    /**
     * Safely applies a new Title to the currently selected YouTube video (`videos.update`).
     * Only changes `title` while preserving `description`, `tags`, `categoryId`, and `defaultLanguage`.
     */
    fun applyTitleToYouTube(videoId: String, newTitle: String) {
        val trimmedTitle = newTitle.trim()
        if (trimmedTitle.isBlank()) {
            _uiState.update { it.copy(applyError = "Title cannot be empty.") }
            return
        }
        _uiState.update {
            it.copy(
                isApplyingToYouTube = true,
                applyError = null,
                applySuccessOutcome = null
            )
        }
        viewModelScope.launch {
            val token = getValidOAuthTokenForWrite()
            if (token.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        isApplyingToYouTube = false,
                        applyError = "Please connect your YouTube channel first to apply changes to your live videos."
                    )
                }
                return@launch
            }

            val result = youTubeApi.updateVideoSnippetSafely(
                videoId = videoId,
                oauthAccessToken = token,
                newTitle = trimmedTitle
            )
            result.onSuccess { outcome ->
                applyLocalVideoMetadataUpdate(outcome)
            }.onFailure { err ->
                Log.w(TAG, "applyTitleToYouTube failed", err)
                _uiState.update {
                    it.copy(
                        isApplyingToYouTube = false,
                        applyError = err.localizedMessage ?: "Could not update video title on YouTube."
                    )
                }
            }
        }
    }

    /**
     * Safely applies an optimized Description (and optionally Tags) to the selected YouTube video.
     */
    fun applyDescriptionToYouTube(videoId: String, newDescription: String, newTags: List<String>? = null) {
        if (newDescription.isBlank()) {
            _uiState.update { it.copy(applyError = "Description cannot be empty.") }
            return
        }
        _uiState.update {
            it.copy(
                isApplyingToYouTube = true,
                applyError = null,
                applySuccessOutcome = null
            )
        }
        viewModelScope.launch {
            val token = getValidOAuthTokenForWrite()
            if (token.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        isApplyingToYouTube = false,
                        applyError = "Please connect your YouTube channel first to apply changes to your live videos."
                    )
                }
                return@launch
            }

            val result = youTubeApi.updateVideoSnippetSafely(
                videoId = videoId,
                oauthAccessToken = token,
                newDescription = newDescription,
                newTags = newTags
            )
            result.onSuccess { outcome ->
                applyLocalVideoMetadataUpdate(outcome)
            }.onFailure { err ->
                Log.w(TAG, "applyDescriptionToYouTube failed", err)
                _uiState.update {
                    it.copy(
                        isApplyingToYouTube = false,
                        applyError = err.localizedMessage ?: "Could not update video description on YouTube."
                    )
                }
            }
        }
    }

    /**
     * Safely applies an optimized Tag list to the selected YouTube video.
     */
    fun applyTagsToYouTube(videoId: String, newTags: List<String>) {
        if (newTags.isEmpty()) {
            _uiState.update { it.copy(applyError = "Please add at least one tag before applying.") }
            return
        }
        _uiState.update {
            it.copy(
                isApplyingToYouTube = true,
                applyError = null,
                applySuccessOutcome = null
            )
        }
        viewModelScope.launch {
            val token = getValidOAuthTokenForWrite()
            if (token.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        isApplyingToYouTube = false,
                        applyError = "Please connect your YouTube channel first to apply changes to your live videos."
                    )
                }
                return@launch
            }

            val result = youTubeApi.updateVideoSnippetSafely(
                videoId = videoId,
                oauthAccessToken = token,
                newTags = newTags
            )
            result.onSuccess { outcome ->
                applyLocalVideoMetadataUpdate(outcome)
            }.onFailure { err ->
                Log.w(TAG, "applyTagsToYouTube failed", err)
                _uiState.update {
                    it.copy(
                        isApplyingToYouTube = false,
                        applyError = err.localizedMessage ?: "Could not update video tags on YouTube."
                    )
                }
            }
        }
    }

    /**
     * Uploads the selected thumbnail Bitmap to YouTube using `thumbnails.set`.
     * If YouTube rejects it because the channel isn't phone-verified, displays a friendly verification prompt.
     */
    fun applyThumbnailToYouTube(videoId: String) {
        val bitmap = _uiState.value.selectedThumbnailBitmap
        if (bitmap == null) {
            _uiState.update { it.copy(applyError = "Please pick a thumbnail image first.") }
            return
        }
        _uiState.update {
            it.copy(
                isApplyingToYouTube = true,
                applyError = null,
                applySuccessOutcome = null,
                showPhoneVerifyDialog = false
            )
        }
        viewModelScope.launch {
            val token = getValidOAuthTokenForWrite()
            if (token.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        isApplyingToYouTube = false,
                        applyError = "Please connect your YouTube channel first to upload custom thumbnails."
                    )
                }
                return@launch
            }

            val result = youTubeApi.uploadCustomThumbnail(
                videoId = videoId,
                bitmap = bitmap,
                oauthAccessToken = token
            )
            result.onSuccess { outcome ->
                _uiState.update {
                    it.copy(
                        isApplyingToYouTube = false,
                        applySuccessOutcome = outcome,
                        applyError = null
                    )
                }
            }.onFailure { err ->
                Log.w(TAG, "applyThumbnailToYouTube failed", err)
                val isUnverified = err is YouTubeApiException.UnverifiedChannelThumbnailException
                _uiState.update {
                    it.copy(
                        isApplyingToYouTube = false,
                        showPhoneVerifyDialog = isUnverified,
                        applyError = err.localizedMessage
                            ?: "Could not upload custom thumbnail to YouTube."
                    )
                }
            }
        }
    }

    /**
     * Restores the previous title, description, and tags saved locally before the last YouTube write.
     */
    fun undoLastVideoChange(videoId: String) {
        _uiState.update {
            it.copy(
                isApplyingToYouTube = true,
                applyError = null,
                applySuccessOutcome = null
            )
        }
        viewModelScope.launch {
            val token = getValidOAuthTokenForWrite()
            if (token.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        isApplyingToYouTube = false,
                        applyError = "Please connect your YouTube channel to restore the previous version."
                    )
                }
                return@launch
            }

            val result = youTubeApi.restoreVideoSnippetFromBackup(videoId, token)
            result.onSuccess { outcome ->
                applyLocalVideoMetadataUpdate(outcome)
            }.onFailure { err ->
                Log.w(TAG, "undoLastVideoChange failed", err)
                _uiState.update {
                    it.copy(
                        isApplyingToYouTube = false,
                        applyError = err.localizedMessage ?: "Failed to restore previous video metadata."
                    )
                }
            }
        }
    }

    private fun applyLocalVideoMetadataUpdate(outcome: YouTubeWriteOutcome) {
        _uiState.update { state ->
            val updatedList = state.activeChannelVideos.map { v ->
                if (v.videoId == outcome.videoId) {
                    v.copy(
                        title = outcome.updatedTitle.ifBlank { v.title },
                        description = outcome.updatedDescription,
                        tags = outcome.updatedTags
                    )
                } else {
                    v
                }
            }
            val updatedTarget = updatedList.firstOrNull { it.videoId == outcome.videoId }
                ?: state.selectedTargetVideo
            state.copy(
                isApplyingToYouTube = false,
                activeChannelVideos = updatedList,
                selectedTargetVideo = updatedTarget,
                optimizeTitleInput = updatedTarget?.title ?: state.optimizeTitleInput,
                optimizeDescInput = updatedTarget?.description ?: state.optimizeDescInput,
                optimizeTagsInput = updatedTarget?.tags?.joinToString(", ") ?: state.optimizeTagsInput,
                applySuccessOutcome = outcome,
                applyError = null
            )
        }
    }

    /**
     * Inspects any YouTube video by URL or ID and opens the Video Deep Analyzer screen.
     */
    fun analyzeVideoDeeply(videoUrlOrId: String) {
        val videoId = youTubeApi.extractVideoId(videoUrlOrId) ?: videoUrlOrId.trim()
        if (videoId.isBlank()) {
            _uiState.update { it.copy(videoAuditError = "Please enter a valid YouTube Video URL or 11-character Video ID.") }
            return
        }
        _uiState.update {
            it.copy(
                activeDetailScreen = DetailDestination.VideoAnalyzer,
                isLoadingVideoAudit = true,
                videoAuditError = null,
                videoAiCoachingSummary = null
            )
        }
        viewModelScope.launch {
            val token = _uiState.value.oauthAccessToken
            val videoResult = youTubeApi.fetchVideoById(videoId, token, forceRefresh = false)
            videoResult.onSuccess { video ->
                val medianViews = _uiState.value.activeChannelVideos
                    .map { it.viewCount }
                    .sorted()
                    .let { sorted -> if (sorted.isNotEmpty()) sorted[sorted.size / 2] else 0L }

                val audit = youTubeApi.auditVideoMetadata(video, medianViews)
                val comments = youTubeApi.fetchVideoComments(video.videoId, maxResults = 35, oauthAccessToken = token)
                    .getOrDefault(emptyList())

                _uiState.update {
                    it.copy(
                        inspectedVideoAudit = audit,
                        inspectedVideoComments = comments,
                        isLoadingVideoAudit = false,
                        videoAuditError = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingVideoAudit = false,
                        videoAuditError = err.localizedMessage ?: "Failed to analyze video."
                    )
                }
            }
        }
    }

    fun generateAiCoachingForInspectedVideo() {
        val audit = _uiState.value.inspectedVideoAudit ?: return
        val v = audit.video
        _uiState.update { it.copy(isGeneratingVideoCoaching = true) }
        viewModelScope.launch {
            val prompt = """
                Perform a diagnostic review of this real YouTube video:
                - Title: "${v.title}" (${v.title.length} chars)
                - Views: ${v.viewCount}, Likes: ${v.likeCount}, Comments: ${v.commentCount}
                - Engagement Rate: ${String.format("%.2f", v.engagementRatePercent)}%
                - Duration: ${YouTubeApiClient.formatDuration(v.durationSeconds)} (Short=${v.isShort})
                - Has Chapters: ${v.hasChapters} (${v.chapterCount} timestamps)
                - Tags (${v.tags.size}): ${v.tags.take(15).joinToString(", ")}
                - First 200 chars of Description: "${v.description.take(200)}"
                - Overall SEO Score: ${audit.overallSeoScore}/100

                Provide:
                1. **Why It Performed This Way**: Specific packaging, hook, and topic analysis.
                2. **3 High-CTR Replacement Titles**: Under 60 chars with curiosity gap.
                3. **Thumbnail Repackaging Concept**: Exact visual layout + 3-word overlay text.
                4. **Retention & Chapter Fix**: Concrete adjustment to lift average percentage viewed.
            """.trimIndent()

            val channelContext = buildActiveChannelContextString()
            val response = aiService.askZdAssistant(
                userPrompt = prompt,
                channelContext = channelContext,
                chatHistory = emptyList()
            )
            response.onSuccess { text ->
                _uiState.update {
                    it.copy(
                        videoAiCoachingSummary = text,
                        isGeneratingVideoCoaching = false
                    )
                }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        videoAiCoachingSummary = ZedCraftAiService.FRIENDLY_ZD_ERROR,
                        isGeneratingVideoCoaching = false
                    )
                }
            }
        }
    }

    // --- Optimize Tab Actions ---
    fun updateOptimizeInputs(
        topic: String = _uiState.value.optimizeTopicInput,
        niche: String = _uiState.value.optimizeNicheInput,
        title: String = _uiState.value.optimizeTitleInput,
        description: String = _uiState.value.optimizeDescInput,
        tags: String = _uiState.value.optimizeTagsInput
    ) {
        _uiState.update {
            it.copy(
                optimizeTopicInput = topic,
                optimizeNicheInput = niche,
                optimizeTitleInput = title,
                optimizeDescInput = description,
                optimizeTagsInput = tags,
                optimizeError = null
            )
        }
    }

    fun runTitleDescTagsOptimizer() {
        val state = _uiState.value
        val targetVideo = state.selectedTargetVideo
        val effectiveTopic = state.optimizeTopicInput.ifBlank {
            state.optimizeTitleInput.ifBlank { targetVideo?.title.orEmpty() }
        }
        val effectiveTitle = state.optimizeTitleInput.ifBlank { targetVideo?.title.orEmpty() }
        val effectiveDesc = state.optimizeDescInput.ifBlank { targetVideo?.description.orEmpty() }
        val effectiveTags = state.optimizeTagsInput.ifBlank { targetVideo?.tags?.joinToString(", ").orEmpty() }

        if (effectiveTopic.isBlank() && effectiveTitle.isBlank()) {
            _uiState.update { it.copy(optimizeError = "Pick one of your videos above or enter a video topic/title first.") }
            return
        }
        _uiState.update {
            it.copy(
                isOptimizing = true,
                optimizeError = null,
                optimizeTopicInput = effectiveTopic,
                optimizeTitleInput = effectiveTitle,
                optimizeDescInput = effectiveDesc,
                optimizeTagsInput = effectiveTags
            )
        }
        viewModelScope.launch {
            val result = aiService.generateOptimizedTitlesAndMetadata(
                topicOrDraftTitle = effectiveTitle.ifBlank { effectiveTopic },
                niche = state.optimizeNicheInput,
                currentDescription = effectiveDesc,
                currentTags = effectiveTags
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() },
                channelContext = buildActiveChannelContextString()
            )
            result.onSuccess { res ->
                _uiState.update {
                    it.copy(
                        titleOptimizerResult = res,
                        isOptimizing = false,
                        optimizeError = null
                    )
                }
            }.onFailure { err ->
                Log.w(TAG, "runTitleDescTagsOptimizer failed", err)
                _uiState.update {
                    it.copy(
                        isOptimizing = false,
                        optimizeError = ZedCraftAiService.FRIENDLY_ZD_ERROR
                    )
                }
            }
        }
    }

    fun setSelectedThumbnails(primary: Bitmap?, secondary: Bitmap? = _uiState.value.secondThumbnailBitmap) {
        _uiState.update {
            it.copy(
                selectedThumbnailBitmap = primary,
                secondThumbnailBitmap = secondary,
                optimizeError = null
            )
        }
    }

    fun runThumbnailVisionCritique() {
        val state = _uiState.value
        val primary = state.selectedThumbnailBitmap
        if (primary == null) {
            _uiState.update { it.copy(optimizeError = "Please select a thumbnail image from your device first.") }
            return
        }
        _uiState.update { it.copy(isOptimizing = true, optimizeError = null) }
        viewModelScope.launch {
            val titleForThumb = state.optimizeTitleInput.ifBlank {
                state.selectedTargetVideo?.title ?: state.optimizeTopicInput.ifBlank { "YouTube Video" }
            }
            val result = aiService.critiqueThumbnailWithVision(
                primaryBitmap = primary,
                videoTitle = titleForThumb,
                comparisonBitmap = state.secondThumbnailBitmap
            )
            result.onSuccess { visionRes ->
                _uiState.update {
                    it.copy(
                        thumbnailVisionResult = visionRes,
                        isOptimizing = false,
                        optimizeError = null
                    )
                }
            }.onFailure { err ->
                Log.w(TAG, "runThumbnailVisionCritique failed", err)
                _uiState.update {
                    it.copy(
                        isOptimizing = false,
                        optimizeError = ZedCraftAiService.FRIENDLY_ZD_ERROR
                    )
                }
            }
        }
    }

    fun runScriptAndHookGenerator(durationMinutes: Int, tone: String) {
        val state = _uiState.value
        val topic = state.optimizeTopicInput.ifBlank { state.optimizeTitleInput }
        if (topic.isBlank()) {
            _uiState.update { it.copy(optimizeError = "Enter a video topic or title to generate hooks and script outline.") }
            return
        }
        _uiState.update { it.copy(isOptimizing = true, optimizeError = null) }
        viewModelScope.launch {
            val res = aiService.generateScriptAndHooks(
                topic = topic,
                targetDurationMinutes = durationMinutes,
                tone = tone,
                channelContext = buildActiveChannelContextString()
            )
            res.onSuccess { md ->
                _uiState.update { it.copy(scriptOutputMarkdown = md, isOptimizing = false) }
            }.onFailure { err ->
                Log.w(TAG, "runScriptAndHookGenerator failed", err)
                _uiState.update {
                    it.copy(
                        isOptimizing = false,
                        optimizeError = ZedCraftAiService.FRIENDLY_ZD_ERROR
                    )
                }
            }
        }
    }

    fun runShortsRepurposeGenerator(transcriptOrSummary: String) {
        val state = _uiState.value
        val title = state.optimizeTitleInput.ifBlank { state.optimizeTopicInput }
        if (title.isBlank() && transcriptOrSummary.isBlank()) {
            _uiState.update { it.copy(optimizeError = "Enter a video title or paste your script/summary to plan Shorts clips.") }
            return
        }
        _uiState.update { it.copy(isOptimizing = true, optimizeError = null) }
        viewModelScope.launch {
            val res = aiService.generateShortsRepurposePlan(
                videoTitle = title.ifBlank { "Creator Video" },
                videoDescriptionOrTranscript = transcriptOrSummary.ifBlank { state.optimizeDescInput }
            )
            res.onSuccess { md ->
                _uiState.update { it.copy(shortsPlanMarkdown = md, isOptimizing = false) }
            }.onFailure { err ->
                Log.w(TAG, "runShortsRepurposeGenerator failed", err)
                _uiState.update {
                    it.copy(
                        isOptimizing = false,
                        optimizeError = ZedCraftAiService.FRIENDLY_ZD_ERROR
                    )
                }
            }
        }
    }

    // --- Research Tab Actions ---
    fun updateKeywordSeedInput(seed: String) {
        _uiState.update { it.copy(keywordSeedInput = seed, researchError = null) }
    }

    fun runKeywordResearch() {
        val seed = _uiState.value.keywordSeedInput.trim()
        if (seed.length < 2) {
            _uiState.update { it.copy(researchError = "Enter at least 2 characters to research YouTube keywords.") }
            return
        }
        _uiState.update { it.copy(isResearchingKeywords = true, researchError = null) }
        viewModelScope.launch {
            val res = youTubeApi.researchKeyword(seed, _uiState.value.oauthAccessToken)
            res.onSuccess { kwResult ->
                _uiState.update {
                    it.copy(
                        keywordResearchResult = kwResult,
                        isResearchingKeywords = false,
                        researchError = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isResearchingKeywords = false,
                        researchError = err.localizedMessage ?: "Keyword research failed."
                    )
                }
            }
        }
    }

    fun generateViralTopicIdeasForNiche(nicheQuery: String) {
        val query = nicheQuery.trim().ifBlank {
            _uiState.value.activeChannel?.title ?: _uiState.value.optimizeNicheInput
        }
        _uiState.update { it.copy(isGeneratingTrendIdeas = true, researchError = null) }
        viewModelScope.launch {
            val prompt = """
                Generate 10 high-demand, low-saturation YouTube video ideas for the niche: "$query".
                For each idea include:
                - **Clickable Title** (<60 chars)
                - **Thumbnail Visual Concept** (3-word text overlay + visual focal point)
                - **Target Search Keyword & Buyer/Viewer Intent**
                - **Opening 15-Second Hook**
                - **Format Recommendation** (Long-form vs Shorts remix angle)
            """.trimIndent()

            val res = aiService.askZdAssistant(
                userPrompt = prompt,
                channelContext = buildActiveChannelContextString(),
                chatHistory = emptyList()
            )
            res.onSuccess { md ->
                _uiState.update { it.copy(trendIdeasMarkdown = md, isGeneratingTrendIdeas = false) }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        isGeneratingTrendIdeas = false,
                        researchError = ZedCraftAiService.FRIENDLY_ZD_ERROR
                    )
                }
            }
        }
    }

    fun updateCompetitorInputs(channelA: String, channelB: String) {
        _uiState.update {
            it.copy(
                competitorInputA = channelA,
                competitorInputB = channelB,
                researchError = null
            )
        }
    }

    fun runCompetitorComparison() {
        val state = _uiState.value
        val inputA = state.competitorInputA.trim().ifBlank { state.activeChannel?.handle.orEmpty() }
        val inputB = state.competitorInputB.trim()
        if (inputA.isBlank() || inputB.isBlank()) {
            _uiState.update { it.copy(researchError = "Enter two YouTube @handles or Channel IDs to benchmark side-by-side.") }
            return
        }
        _uiState.update { it.copy(isComparingCompetitors = true, researchError = null) }
        viewModelScope.launch {
            val res = youTubeApi.compareChannels(inputA, inputB, state.oauthAccessToken)
            res.onSuccess { comp ->
                _uiState.update {
                    it.copy(
                        competitorComparison = comp,
                        isComparingCompetitors = false,
                        researchError = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isComparingCompetitors = false,
                        researchError = err.localizedMessage ?: "Competitor comparison failed."
                    )
                }
            }
        }
    }

    fun updateCommentVideoUrlInput(url: String) {
        _uiState.update { it.copy(commentVideoUrlInput = url, researchError = null) }
    }

    fun runCommentSentimentExplorer() {
        val rawInput = _uiState.value.commentVideoUrlInput.trim()
            .ifBlank { _uiState.value.selectedTargetVideo?.videoId ?: _uiState.value.activeChannelVideos.firstOrNull()?.videoId.orEmpty() }
        val videoId = youTubeApi.extractVideoId(rawInput) ?: rawInput
        if (videoId.isBlank()) {
            _uiState.update { it.copy(researchError = "Paste a YouTube video URL or connect a channel first.") }
            return
        }
        _uiState.update { it.copy(isAnalyzingComments = true, researchError = null) }
        viewModelScope.launch {
            val token = _uiState.value.oauthAccessToken
            val videoDetail = youTubeApi.fetchVideoById(videoId, token).getOrNull()
            val commentsRes = youTubeApi.fetchVideoComments(videoId, maxResults = 45, oauthAccessToken = token)
            commentsRes.onSuccess { comments ->
                _uiState.update { it.copy(commentList = comments) }
                if (comments.isNotEmpty()) {
                    val aiRes = aiService.analyzeCommentsAndAudienceSentiment(
                        videoTitle = videoDetail?.title ?: "Video ($videoId)",
                        comments = comments
                    )
                    aiRes.onSuccess { md ->
                        _uiState.update {
                            it.copy(
                                commentSentimentMarkdown = md,
                                isAnalyzingComments = false
                            )
                        }
                    }.onFailure {
                        _uiState.update {
                            it.copy(
                                isAnalyzingComments = false,
                                researchError = ZedCraftAiService.FRIENDLY_ZD_ERROR
                            )
                        }
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isAnalyzingComments = false,
                            commentSentimentMarkdown = "No public comments found on this video."
                        )
                    }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isAnalyzingComments = false,
                        researchError = err.localizedMessage ?: "Could not fetch comments."
                    )
                }
            }
        }
    }

    // --- ZD AI Coach Actions ---
    fun sendZdPrompt(promptText: String) {
        val trimmed = promptText.trim()
        if (trimmed.isBlank() || _uiState.value.isZdThinking) return

        _uiState.update {
            it.copy(
                isZdThinking = true,
                streamingZdMessage = null,
                zdError = null,
                lastFailedZdPrompt = null
            )
        }

        viewModelScope.launch {
            try {
                cacheDao.insertZdChatMessage(
                    ZdChatMessageEntity(
                        role = "user",
                        content = trimmed,
                        createdAtMs = System.currentTimeMillis()
                    )
                )

                val historyPairs = zdChatHistory.value.map { it.role to it.content }
                val res = aiService.askZdAssistant(
                    userPrompt = trimmed,
                    channelContext = buildActiveChannelContextString(),
                    chatHistory = historyPairs,
                    onStreamingUpdate = { partial ->
                        _uiState.update { state ->
                            state.copy(streamingZdMessage = partial)
                        }
                    }
                )

                res.onSuccess { reply ->
                    cacheDao.insertZdChatMessage(
                        ZdChatMessageEntity(
                            role = "assistant",
                            content = reply,
                            createdAtMs = System.currentTimeMillis()
                        )
                    )
                    _uiState.update {
                        it.copy(
                            isZdThinking = false,
                            streamingZdMessage = null,
                            zdError = null,
                            lastFailedZdPrompt = null
                        )
                    }
                }.onFailure { err ->
                    Log.e(TAG, "ZD AI assistant failed after retry and fallback", err)
                    _uiState.update {
                        it.copy(
                            isZdThinking = false,
                            streamingZdMessage = null,
                            zdError = ZedCraftAiService.FRIENDLY_ZD_ERROR,
                            lastFailedZdPrompt = trimmed
                        )
                    }
                }
            } catch (e: CancellationException) {
                _uiState.update { it.copy(isZdThinking = false, streamingZdMessage = null) }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected exception in sendZdPrompt", e)
                _uiState.update {
                    it.copy(
                        isZdThinking = false,
                        streamingZdMessage = null,
                        zdError = ZedCraftAiService.FRIENDLY_ZD_ERROR,
                        lastFailedZdPrompt = trimmed
                    )
                }
            }
        }
    }

    /**
     * Retries the last failed ZD prompt without duplicating the user bubble in the chat history.
     */
    fun retryLastZdPrompt() {
        val lastPrompt = _uiState.value.lastFailedZdPrompt
            ?: zdChatHistory.value.lastOrNull { it.role == "user" }?.content
            ?: return
        if (_uiState.value.isZdThinking) return

        _uiState.update {
            it.copy(
                isZdThinking = true,
                streamingZdMessage = null,
                zdError = null
            )
        }

        viewModelScope.launch {
            try {
                val historyPairs = zdChatHistory.value.dropLast(1).map { it.role to it.content }
                val res = aiService.askZdAssistant(
                    userPrompt = lastPrompt,
                    channelContext = buildActiveChannelContextString(),
                    chatHistory = historyPairs,
                    onStreamingUpdate = { partial ->
                        _uiState.update { state ->
                            state.copy(streamingZdMessage = partial)
                        }
                    }
                )

                res.onSuccess { reply ->
                    cacheDao.insertZdChatMessage(
                        ZdChatMessageEntity(
                            role = "assistant",
                            content = reply,
                            createdAtMs = System.currentTimeMillis()
                        )
                    )
                    _uiState.update {
                        it.copy(
                            isZdThinking = false,
                            streamingZdMessage = null,
                            zdError = null,
                            lastFailedZdPrompt = null
                        )
                    }
                }.onFailure { err ->
                    Log.e(TAG, "ZD retry failed", err)
                    _uiState.update {
                        it.copy(
                            isZdThinking = false,
                            streamingZdMessage = null,
                            zdError = ZedCraftAiService.FRIENDLY_ZD_ERROR,
                            lastFailedZdPrompt = lastPrompt
                        )
                    }
                }
            } catch (e: CancellationException) {
                _uiState.update { it.copy(isZdThinking = false, streamingZdMessage = null) }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected exception in retryLastZdPrompt", e)
                _uiState.update {
                    it.copy(
                        isZdThinking = false,
                        streamingZdMessage = null,
                        zdError = ZedCraftAiService.FRIENDLY_ZD_ERROR,
                        lastFailedZdPrompt = lastPrompt
                    )
                }
            }
        }
    }

    fun clearZdChat() {
        viewModelScope.launch {
            cacheDao.clearZdChatMessages()
            _uiState.update {
                it.copy(
                    isZdThinking = false,
                    streamingZdMessage = null,
                    zdError = null,
                    lastFailedZdPrompt = null
                )
            }
            seedWelcomeZdMessageIfEmpty()
        }
    }

    // --- Saved Ideas Vault Actions ---
    fun saveIdeaToVault(category: String, title: String, content: String, tags: String = "") {
        viewModelScope.launch {
            cacheDao.insertSavedIdea(
                SavedIdeaEntity(
                    category = category,
                    title = title.take(120),
                    content = content,
                    tags = tags,
                    createdAtMs = System.currentTimeMillis()
                )
            )
        }
    }

    fun deleteSavedIdea(id: Long) {
        viewModelScope.launch {
            cacheDao.deleteSavedIdea(id)
        }
    }

    // --- Checklist Actions ---
    fun toggleChecklistItem(item: ChecklistItemEntity) {
        viewModelScope.launch {
            cacheDao.upsertChecklistItem(item.copy(isCompleted = !item.isCompleted))
        }
    }

    fun resetChecklistProgress() {
        viewModelScope.launch {
            checklistItems.value.forEach { item ->
                if (item.isCompleted) {
                    cacheDao.upsertChecklistItem(item.copy(isCompleted = false))
                }
            }
        }
    }

    // --- Revenue Calculator Actions ---
    fun updateRevenueCalculator(
        monthlyViews: Long = _uiState.value.calcMonthlyViews,
        nicheRpmUsd: Double = _uiState.value.calcNicheRpmUsd,
        shortsViews: Long = _uiState.value.calcShortsViews,
        avgViewsPerVideo: Long = _uiState.value.calcAvgViewsPerVideo
    ) {
        _uiState.update {
            it.copy(
                calcMonthlyViews = monthlyViews.coerceAtLeast(0L),
                calcNicheRpmUsd = nicheRpmUsd.coerceIn(0.5, 50.0),
                calcShortsViews = shortsViews.coerceAtLeast(0L),
                calcAvgViewsPerVideo = avgViewsPerVideo.coerceAtLeast(0L)
            )
        }
    }

    fun clearRoomCache() {
        viewModelScope.launch {
            cacheDao.clearAllApiCache()
        }
    }

    // --- GitHub In-App Auto-Update Actions ---
    fun checkForGitHubUpdates(isSilent: Boolean = false) {
        if (_uiState.value.isCheckingUpdate) return
        if (isSilent && !gitHubUpdateClient.shouldRunSilentCheck()) return

        _uiState.update {
            it.copy(
                isCheckingUpdate = !isSilent,
                updateCheckError = if (isSilent) it.updateCheckError else null
            )
        }

        viewModelScope.launch {
            val res = gitHubUpdateClient.checkForUpdates(
                repoInput = GitHubUpdateClient.DEFAULT_GITHUB_REPO,
                isSilent = isSilent
            )
            res.onSuccess { info ->
                _uiState.update {
                    it.copy(
                        isCheckingUpdate = false,
                        githubReleaseInfo = info,
                        updateCheckError = null,
                        showAutoUpdateDialog = if (info.isUpdateAvailable) true else it.showAutoUpdateDialog
                    )
                }
            }.onFailure { err ->
                // If silent check fails or there is no internet, stay completely silent and never crash
                if (isSilent) {
                    _uiState.update { it.copy(isCheckingUpdate = false) }
                } else {
                    _uiState.update {
                        it.copy(
                            isCheckingUpdate = false,
                            updateCheckError = "Could not check for updates right now. Please check your internet connection."
                        )
                    }
                }
            }
        }
    }

    fun dismissAutoUpdateDialog() {
        _uiState.update { it.copy(showAutoUpdateDialog = false) }
    }

    /**
     * Downloads the release `.apk` inside the app (never opening GitHub or the browser),
     * updating progress percentage, downloaded MB, and total MB in real time.
     */
    fun startInAppUpdateDownload() {
        val release = _uiState.value.githubReleaseInfo ?: return
        val apkUrl = release.apkDownloadUrl
        if (apkUrl.isNullOrBlank()) {
            _uiState.update {
                it.copy(
                    showAutoUpdateDialog = false,
                    updateDownloadError = "Release ${release.latestTag} does not have an .apk asset attached yet."
                )
            }
            return
        }

        activeDownloadJob?.cancel()
        _uiState.update {
            it.copy(
                showAutoUpdateDialog = false,
                isDownloadingUpdate = true,
                downloadProgressPercent = 0,
                downloadedMb = 0f,
                totalMb = if (release.apkSizeBytes > 0) release.apkSizeBytes.toFloat() / (1024f * 1024f) else 0f,
                updateDownloadError = null,
                downloadedApkFile = null
            )
        }

        activeDownloadJob = viewModelScope.launch {
            val result = gitHubUpdateClient.downloadUpdateApk(
                apkUrl = apkUrl,
                versionTag = release.latestTag,
                onProgress = { progress: ApkDownloadProgress ->
                    _uiState.update { state ->
                        state.copy(
                            downloadProgressPercent = progress.percent.coerceAtLeast(0),
                            downloadedMb = progress.downloadedMb,
                            totalMb = progress.totalMb
                        )
                    }
                }
            )

            result.onSuccess { apkFile ->
                _uiState.update {
                    it.copy(
                        isDownloadingUpdate = false,
                        downloadProgressPercent = 100,
                        downloadedApkFile = apkFile,
                        updateDownloadError = null
                    )
                }
            }.onFailure { err ->
                if (err is CancellationException) {
                    _uiState.update {
                        it.copy(
                            isDownloadingUpdate = false,
                            updateDownloadError = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isDownloadingUpdate = false,
                            updateDownloadError = err.localizedMessage ?: "Download failed. Please tap Retry."
                        )
                    }
                }
            }
        }
    }

    fun cancelInAppUpdateDownload() {
        activeDownloadJob?.cancel()
        activeDownloadJob = null
        _uiState.update {
            it.copy(
                isDownloadingUpdate = false,
                updateDownloadError = null
            )
        }
    }

    fun dismissUpdateDownloadError() {
        _uiState.update { it.copy(updateDownloadError = null) }
    }

    fun setShowUnknownSourcesDialog(show: Boolean, pendingInstall: Boolean = false) {
        _uiState.update {
            it.copy(
                showUnknownSourcesDialog = show,
                pendingInstallAfterPermission = pendingInstall
            )
        }
    }

    fun clearDownloadedApkState() {
        _uiState.update {
            it.copy(
                downloadedApkFile = null,
                pendingInstallAfterPermission = false,
                showUnknownSourcesDialog = false
            )
        }
    }

    private fun buildActiveChannelContextString(): String {
        val state = _uiState.value
        val ch = state.activeChannel ?: return "No YouTube channel connected yet. General creator coaching mode."
        val topTitles = state.activeChannelVideos
            .sortedByDescending { it.viewCount }
            .take(5)
            .joinToString(" | ") { "\"${it.title}\" (${YouTubeApiClient.formatCompact(it.viewCount)} views)" }
        val health = state.channelHealthReport
        return buildString {
            appendLine("Channel: ${ch.title} (${ch.handle}) [ID: ${ch.channelId}]")
            appendLine("Subscribers: ${YouTubeApiClient.formatCompact(ch.subscriberCount)} | Total Views: ${YouTubeApiClient.formatCompact(ch.viewCount)} | Videos: ${ch.videoCount}")
            if (health != null) {
                appendLine("Channel Health Score: ${health.overallHealthScore}/100 | Uploads/Mo: ${String.format("%.1f", health.uploadsPerMonth)} | Shorts Ratio: ${health.shortsRatioPercent}%")
                appendLine("Best Upload Windows: ${health.bestUploadSchedule}")
                appendLine("Diagnosis: ${health.dropDiagnosisSummary}")
            }
            if (topTitles.isNotBlank()) {
                appendLine("Top Performing Videos: $topTitles")
            }
        }
    }

    private suspend fun seedWelcomeZdMessageIfEmpty() {
        val current = cacheDao.observeZdChatMessages()
        // Always ensure there's a helpful initial message if the table is empty
        val list = zdChatHistory.value
        if (list.isEmpty()) {
            cacheDao.insertZdChatMessage(
                ZdChatMessageEntity(
                    role = "assistant",
                    content = """
                        Welcome to **ZD** — your YouTube Growth & Strategy Coach!
                        
                        Ask me anything or pick a quick action above:
                        - **Score & Rewrite Titles** and apply them directly to your YouTube videos
                        - **Diagnose View Drops** using your real upload history & channel median
                        - **Write High-Retention Hooks** for the first 30 seconds
                        - **Audit Thumbnail Concepts** for mobile clarity and click appeal
                    """.trimIndent(),
                    createdAtMs = System.currentTimeMillis()
                )
            )
        }
    }

    private suspend fun seedDefaultChecklistIfEmpty() {
        val defaults = listOf(
            ChecklistItemEntity(
                itemKey = "pre_1_title_curiosity",
                phase = "Pre-Publish",
                title = "Title under 60 characters with clear curiosity gap",
                description = "Mobile Home & Browse feeds truncate titles around 58–62 chars. Front-load the hook.",
                isCompleted = false
            ),
            ChecklistItemEntity(
                itemKey = "pre_2_thumb_mobile_squint",
                phase = "Pre-Publish",
                title = "Thumbnail passes the 120x68px Mobile Squint Test",
                description = "Max 3 focal elements, 1–4 words of bold high-contrast text, and no bottom-right timestamp clash.",
                isCompleted = false
            ),
            ChecklistItemEntity(
                itemKey = "pre_3_hook_alignment",
                phase = "Pre-Publish",
                title = "First 15 seconds immediately validate the title & thumbnail",
                description = "Eliminate channel intros or logos; deliver the visual/verbal payoff immediately.",
                isCompleted = false
            ),
            ChecklistItemEntity(
                itemKey = "pre_4_desc_above_fold",
                phase = "Pre-Publish",
                title = "First 2 description lines contain primary keyword + compelling hook",
                description = "These 150 characters appear in YouTube Search snippets and above 'Show More'.",
                isCompleted = false
            ),
            ChecklistItemEntity(
                itemKey = "pre_5_chapters_0000",
                phase = "Pre-Publish",
                title = "00:00 chapters added (at least 3 timestamps >=10s apart)",
                description = "Unlocks Google Search key moments and improves viewer scrubbing retention.",
                isCompleted = false
            ),
            ChecklistItemEntity(
                itemKey = "pre_6_end_screen_binge",
                phase = "Pre-Publish",
                title = "End screen links to a single high-converting follow-up video",
                description = "Verbal bridge into one specific next video increases session watch time.",
                isCompleted = false
            ),
            ChecklistItemEntity(
                itemKey = "post_1_pinned_comment",
                phase = "First 24 Hours",
                title = "Pin an open-ended question or resource link in comments",
                description = "Sparks early comment velocity and boosts community engagement signals.",
                isCompleted = false
            ),
            ChecklistItemEntity(
                itemKey = "post_2_ctr_check_3hr",
                phase = "First 24 Hours",
                title = "Monitor Browse CTR after 2–3 hours; swap to backup title/thumbnail if below channel average",
                description = "Use ZEDCraft Optimize to apply an alternative title or thumbnail directly to YouTube.",
                isCompleted = false
            ),
            ChecklistItemEntity(
                itemKey = "mono_1_ypp_500_tier",
                phase = "Monetization (YPP)",
                title = "YPP Early Access Tier: 500 Subscribers + 3 Public Uploads (90d) + 3,000 Watch Hours (or 3M Shorts Views)",
                description = "Unlocks fan funding (Super Thanks, Super Chat, Channel Memberships) & Shopping.",
                isCompleted = false
            ),
            ChecklistItemEntity(
                itemKey = "mono_2_ypp_1000_tier",
                phase = "Monetization (YPP)",
                title = "Full Ad Revenue Tier: 1,000 Subscribers + 4,000 Valid Public Watch Hours (12m) or 10M Shorts Views (90d)",
                description = "Unlocks Long-Form Watch Page Ad Revenue and Shorts Feed Ad Revenue sharing.",
                isCompleted = false
            )
        )
        cacheDao.seedChecklistIfEmpty(defaults)
    }

    companion object {
        private const val TAG = "ZedCraftViewModel"
    }
}
