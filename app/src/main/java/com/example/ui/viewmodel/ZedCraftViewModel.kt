package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.R
import com.example.ai.DescriptionOptimizationResult
import com.example.ai.TagsGenerationResult
import com.example.ai.ThumbnailAbResult
import com.example.ai.ThumbnailScoreResult
import com.example.ai.TitleOptimizationResult
import com.example.ai.VideoIdeasResult
import com.example.ai.ZdAssistantResponse
import com.example.ai.ZdSkillModule
import com.example.ai.ZdSkillPack
import com.example.ai.ZedCraftAiConfig
import com.example.ai.ZedCraftAiService
import com.example.data.local.LocalSavedWorkEntity
import com.example.data.local.YouTubeCacheDao
import com.example.data.local.ZedCraftLocalDatabase
import com.example.data.model.CompetitorChannel
import com.example.data.model.ConnectedChannel
import com.example.data.model.SavedWorkItem
import com.example.data.model.WorkType
import com.example.data.model.ZdChatMessage
import com.example.data.remote.ChannelHealthReport
import com.example.data.remote.GitHubUpdateClient
import com.example.data.remote.GitHubUpdateInfo
import com.example.data.remote.KeywordResearchResult
import com.example.data.remote.VideoSeoAuditResult
import com.example.data.remote.YouTubeApiClient
import com.example.data.remote.YtAnalyticsOverview
import com.example.data.remote.YtChannelDetail
import com.example.data.remote.YtVideoDetail
import com.example.data.repository.ZedCraftFirestoreRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class PrimaryTab(val label: String) {
    HOME("Home"),
    OPTIMIZE("Optimize"),
    RESEARCH("Research"),
    ZD("ZD"),
    PROFILE("Profile")
}

sealed interface DetailDestination {
    data object None : DetailDestination
    data class VideoAnalyzerAndAudit(val videoIdOrUrl: String = "") : DetailDestination
    data object ChannelHealthCheck : DetailDestination
    data object SetupGuide : DetailDestination
    data object PrivacyAndTerms : DetailDestination
    data object GitHubUpdater : DetailDestination
    data class SavedWorkDetail(val item: SavedWorkItem) : DetailDestination
}

data class ZedCraftUiState(
    val isDarkTheme: Boolean = true,
    val hasCompletedOnboarding: Boolean = false,
    val isPublicGuestMode: Boolean = false,
    val isSignedInWithGoogle: Boolean = false,
    val userUid: String = "",
    val userDisplayName: String = "",
    val userEmail: String = "",
    val oauthAccessToken: String? = null,
    val isYouTubeApiKeyConfigured: Boolean = false,

    // Navigation
    val currentTab: PrimaryTab = PrimaryTab.HOME,
    val optimizeSubTab: Int = 0, // 0: Thumbnail, 1: Title, 2: Description, 3: Tags
    val researchSubTab: Int = 0, // 0: Keywords, 1: Trends, 2: Competitors, 3: Idea Finder
    val zdSubTab: Int = 0,       // 0: Chat, 1: Skills Pack (12 skills)
    val detailDestination: DetailDestination = DetailDestination.None,

    // Connected Channel & Multi-Channel Switcher
    val connectedChannels: List<ConnectedChannel> = emptyList(),
    val activeChannel: YtChannelDetail? = null,
    val activeChannelVideos: List<YtVideoDetail> = emptyList(),
    val analyticsOverview: YtAnalyticsOverview? = null,
    val channelHealthReport: ChannelHealthReport? = null,
    val isChannelLoading: Boolean = false,
    val channelErrorMessage: String? = null,

    // 1. Thumbnail Scorer & A/B Compare
    val isAbCompareMode: Boolean = false,
    val thumbnailBitmapA: Bitmap? = null,
    val thumbnailBitmapB: Bitmap? = null,
    val thumbnailVideoTitle: String = "",
    val isScoringThumbnail: Boolean = false,
    val thumbnailScoreResult: ThumbnailScoreResult? = null,
    val thumbnailAbResult: ThumbnailAbResult? = null,
    val thumbnailError: String? = null,

    // 2. Title Optimizer
    val titleInput: String = "",
    val titleNicheContext: String = "",
    val isOptimizingTitle: Boolean = false,
    val titleResult: TitleOptimizationResult? = null,
    val titleError: String? = null,

    // 3. Description Optimizer
    val descTitleInput: String = "",
    val descExistingInput: String = "",
    val descTranscriptInput: String = "",
    val isOptimizingDescription: Boolean = false,
    val descriptionResult: DescriptionOptimizationResult? = null,
    val descriptionError: String? = null,

    // 4. Tags & Hashtags Generator
    val tagsTopicInput: String = "",
    val isGeneratingTags: Boolean = false,
    val tagsResult: TagsGenerationResult? = null,
    val tagsError: String? = null,

    // 5. Keyword Research
    val keywordInput: String = "",
    val isResearchingKeyword: Boolean = false,
    val keywordResult: KeywordResearchResult? = null,
    val keywordError: String? = null,

    // 6. Video Analyzer & SEO Audit
    val analyzedVideoInput: String = "",
    val isAnalyzingVideo: Boolean = false,
    val analyzedVideo: YtVideoDetail? = null,
    val videoAuditResult: VideoSeoAuditResult? = null,
    val videoAnalyzerError: String? = null,

    // 7. Trend & Idea Finder
    val selectedTrendRegion: String = "US",
    val selectedTrendCategoryId: String = "0",
    val isLoadingTrends: Boolean = false,
    val trendingVideos: List<YtVideoDetail> = emptyList(),
    val trendsError: String? = null,
    val ideaNicheInput: String = "",
    val isGeneratingIdeas: Boolean = false,
    val videoIdeasResult: VideoIdeasResult? = null,
    val ideasError: String? = null,

    // 8. Competitor Tracking
    val competitorInput: String = "",
    val isAddingCompetitor: Boolean = false,
    val trackedCompetitors: List<CompetitorChannel> = emptyList(),
    val competitorError: String? = null,

    // 9. ZD AI Assistant Chat
    val activeZdSkill: ZdSkillModule = ZdSkillPack.skills.first(),
    val zdInputText: String = "",
    val isZdThinking: Boolean = false,
    val zdChatMessages: List<ZdChatMessage> = emptyList(),
    val zdLatestResponse: ZdAssistantResponse? = null,
    val zdError: String? = null,

    // 10. Saved Work & History
    val savedWorkItems: List<SavedWorkItem> = emptyList(),
    val selectedCompareItemA: SavedWorkItem? = null,
    val selectedCompareItemB: SavedWorkItem? = null,

    // 11. Background App Updates (Hidden Repository)
    val isCheckingGitHubUpdate: Boolean = false,
    val githubUpdateInfo: GitHubUpdateInfo? = null,
    val githubUpdateError: String? = null,

    // Transient feedback snackbar/banner
    val toastMessage: String? = null
)

class ZedCraftViewModel(
    private val firestoreRepository: ZedCraftFirestoreRepository,
    private val youTubeApiClient: YouTubeApiClient,
    private val aiService: ZedCraftAiService,
    private val cacheDao: YouTubeCacheDao,
    private val gitHubUpdateClient: GitHubUpdateClient,
    private val auth: FirebaseAuth = Firebase.auth
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ZedCraftUiState(
            isYouTubeApiKeyConfigured = youTubeApiClient.isApiKeyConfigured()
        )
    )
    val uiState: StateFlow<ZedCraftUiState> = _uiState.asStateFlow()

    val cachedItemsCount: StateFlow<Int> = cacheDao.observeCacheCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private var firestoreStreamsJob: Job? = null

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        if (user != null) {
            _uiState.update {
                it.copy(
                    isSignedInWithGoogle = true,
                    userUid = user.uid,
                    userDisplayName = user.displayName ?: "ZEDCraft Creator",
                    userEmail = user.email.orEmpty(),
                    hasCompletedOnboarding = true
                )
            }
            startFirestoreUserStreams(user.uid, user.displayName ?: "ZEDCraft Creator", user.email.orEmpty())
        } else {
            firestoreStreamsJob?.cancel()
            _uiState.update {
                it.copy(
                    isSignedInWithGoogle = false,
                    userUid = "",
                    userDisplayName = "",
                    userEmail = "",
                    oauthAccessToken = null,
                    connectedChannels = emptyList(),
                    trackedCompetitors = emptyList()
                )
            }
            observeLocalSavedWorkWhenSignedOut()
        }
    }

    init {
        auth.addAuthStateListener(authStateListener)
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authStateListener)
        firestoreStreamsJob?.cancel()
    }

    private fun startFirestoreUserStreams(uid: String, displayName: String, email: String) {
        firestoreStreamsJob?.cancel()
        firestoreStreamsJob = viewModelScope.launch {
            firestoreRepository.upsertUserProfile(displayName = displayName, email = email)

            launch {
                firestoreRepository.observeConnectedChannels()
                    .catch { e -> unwrapAndIgnoreIfCancelled(e) }
                    .collect { channels ->
                        _uiState.update { it.copy(connectedChannels = channels) }
                        if (_uiState.value.activeChannel == null && channels.isNotEmpty()) {
                            val first = channels.first()
                            connectChannelByQuery(first.channelId, isFromSavedChannel = true)
                        }
                    }
            }

            launch {
                firestoreRepository.observeSavedWork()
                    .catch { e -> unwrapAndIgnoreIfCancelled(e) }
                    .collect { items ->
                        _uiState.update { it.copy(savedWorkItems = items) }
                    }
            }

            launch {
                firestoreRepository.observeCompetitors()
                    .catch { e -> unwrapAndIgnoreIfCancelled(e) }
                    .collect { competitors ->
                        _uiState.update { it.copy(trackedCompetitors = competitors) }
                    }
            }

            launch {
                firestoreRepository.observeZdChats()
                    .catch { e -> unwrapAndIgnoreIfCancelled(e) }
                    .collect { messages ->
                        _uiState.update { it.copy(zdChatMessages = messages) }
                    }
            }
        }
    }

    private fun observeLocalSavedWorkWhenSignedOut() {
        viewModelScope.launch {
            cacheDao.observeLocalSavedWork().collect { localItems ->
                if (!_uiState.value.isSignedInWithGoogle) {
                    val mapped = localItems.map { local ->
                        SavedWorkItem(
                            id = "local_${local.localId}",
                            userId = "guest",
                            workType = local.workType,
                            title = local.title,
                            subtitle = local.subtitle,
                            score = local.score,
                            payloadJson = local.payloadJson,
                            channelId = local.channelId
                        )
                    }
                    _uiState.update { it.copy(savedWorkItems = mapped) }
                }
            }
        }
    }

    private fun unwrapAndIgnoreIfCancelled(error: Throwable) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
    }

    // --- Navigation & Shell State ---
    fun selectPrimaryTab(tab: PrimaryTab) {
        _uiState.update { it.copy(currentTab = tab, detailDestination = DetailDestination.None) }
        if (tab == PrimaryTab.RESEARCH && _uiState.value.researchSubTab == 1 && _uiState.value.trendingVideos.isEmpty()) {
            loadTrendingVideos()
        }
    }

    fun selectOptimizeSubTab(index: Int) {
        _uiState.update { it.copy( currentTab = PrimaryTab.OPTIMIZE, optimizeSubTab = index.coerceIn(0, 3), detailDestination = DetailDestination.None) }
    }

    fun selectResearchSubTab(index: Int) {
        _uiState.update { it.copy(currentTab = PrimaryTab.RESEARCH, researchSubTab = index.coerceIn(0, 3), detailDestination = DetailDestination.None) }
        if (index == 1 && _uiState.value.trendingVideos.isEmpty()) {
            loadTrendingVideos()
        }
    }

    fun selectZdSubTab(index: Int) {
        _uiState.update { it.copy(currentTab = PrimaryTab.ZD, zdSubTab = index.coerceIn(0, 1), detailDestination = DetailDestination.None) }
    }

    fun openDetail(destination: DetailDestination) {
        _uiState.update { it.copy(detailDestination = destination) }
        if (destination is DetailDestination.VideoAnalyzerAndAudit && destination.videoIdOrUrl.isNotBlank()) {
            _uiState.update { it.copy(analyzedVideoInput = destination.videoIdOrUrl) }
            analyzeAndAuditVideo(destination.videoIdOrUrl)
        }
    }

    fun closeDetail() {
        _uiState.update { it.copy(detailDestination = DetailDestination.None) }
    }

    fun toggleDarkTheme() {
        _uiState.update { it.copy(isDarkTheme = !it.isDarkTheme) }
    }

    fun enterPublicToolsWithoutConnecting() {
        _uiState.update {
            it.copy(
                hasCompletedOnboarding = true,
                isPublicGuestMode = true,
                currentTab = PrimaryTab.OPTIMIZE
            )
        }
    }

    fun showToast(msg: String?) {
        _uiState.update { it.copy(toastMessage = msg) }
    }

    // --- Channel Authorization & Multi-Channel Connection ---
    fun onYouTubeOAuthTokenReceived(accessToken: String) {
        _uiState.update { it.copy(oauthAccessToken = accessToken, isChannelLoading = true, channelErrorMessage = null) }
        viewModelScope.launch {
            val result = youTubeApiClient.fetchMyAuthorizedChannels(accessToken)
            result.onSuccess { channels ->
                if (channels.isNotEmpty()) {
                    val primary = channels.first()
                    setActiveChannelAndLoadTelemetry(primary, isOAuth = true)
                    channels.forEach { ch ->
                        if (_uiState.value.isSignedInWithGoogle) {
                            firestoreRepository.saveConnectedChannel(
                                ConnectedChannel(
                                    channelId = ch.channelId,
                                    title = ch.title,
                                    handle = ch.handle,
                                    thumbnailUrl = ch.thumbnailUrl,
                                    subscriberCount = ch.subscriberCount,
                                    viewCount = ch.viewCount,
                                    videoCount = ch.videoCount,
                                    healthScore = 85,
                                    isOAuthConnected = true
                                )
                            )
                        }
                    }
                    showToast("Connected ${primary.title} via Google OAuth 2.0!")
                } else {
                    _uiState.update {
                        it.copy(
                            isChannelLoading = false,
                            channelErrorMessage = "No YouTube channel was found on this Google account. You can connect any channel by @handle or Channel ID below."
                        )
                    }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isChannelLoading = false,
                        channelErrorMessage = err.localizedMessage ?: "Failed to load authorized YouTube channel."
                    )
                }
            }
        }
    }

    fun connectChannelByQuery(queryOrHandleOrUrl: String, isFromSavedChannel: Boolean = false) {
        val clean = queryOrHandleOrUrl.trim()
        if (clean.isBlank()) {
            _uiState.update { it.copy(channelErrorMessage = "Enter a YouTube @handle, Channel ID (UC...), or video URL.") }
            return
        }
        _uiState.update { it.copy(isChannelLoading = true, channelErrorMessage = null) }
        viewModelScope.launch {
            val token = _uiState.value.oauthAccessToken
            val result = youTubeApiClient.lookupChannel(clean, token, forceRefresh = !isFromSavedChannel)
            result.onSuccess { channel ->
                setActiveChannelAndLoadTelemetry(channel, isOAuth = false)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isChannelLoading = false,
                        channelErrorMessage = err.localizedMessage ?: "Could not connect channel."
                    )
                }
            }
        }
    }

    private suspend fun setActiveChannelAndLoadTelemetry(channel: YtChannelDetail, isOAuth: Boolean) {
        val token = _uiState.value.oauthAccessToken
        val recentVideos = youTubeApiClient.fetchChannelRecentVideos(
            channel = channel,
            maxResults = 25,
            oauthAccessToken = token
        ).getOrDefault(emptyList())

        val healthReport = youTubeApiClient.buildChannelHealthReport(channel, recentVideos)
        val analyticsOverview = youTubeApiClient.fetchChannelAnalytics(
            channel = channel,
            recentVideos = recentVideos,
            oauthAccessToken = if (isOAuth) token else null,
            daysRange = 28
        )

        _uiState.update {
            it.copy(
                activeChannel = channel,
                activeChannelVideos = recentVideos,
                channelHealthReport = healthReport,
                analyticsOverview = analyticsOverview,
                isChannelLoading = false,
                channelErrorMessage = null,
                hasCompletedOnboarding = true
            )
        }

        if (_uiState.value.isSignedInWithGoogle) {
            firestoreRepository.saveConnectedChannel(
                ConnectedChannel(
                    channelId = channel.channelId,
                    title = channel.title,
                    handle = channel.handle,
                    thumbnailUrl = channel.thumbnailUrl,
                    subscriberCount = channel.subscriberCount,
                    viewCount = channel.viewCount,
                    videoCount = channel.videoCount,
                    healthScore = healthReport.overallHealthScore,
                    isOAuthConnected = isOAuth
                )
            )
            firestoreRepository.upsertUserProfile(
                displayName = _uiState.value.userDisplayName.ifBlank { channel.title },
                email = _uiState.value.userEmail,
                activeChannelId = channel.channelId,
                activeChannelTitle = channel.title
            )
        }
    }

    fun switchActiveChannel(connectedChannel: ConnectedChannel) {
        connectChannelByQuery(connectedChannel.channelId, isFromSavedChannel = false)
    }

    fun disconnectChannel(channelId: String) {
        viewModelScope.launch {
            if (_uiState.value.isSignedInWithGoogle) {
                firestoreRepository.removeConnectedChannel(channelId)
            }
            if (_uiState.value.activeChannel?.channelId == channelId) {
                _uiState.update {
                    it.copy(
                        activeChannel = null,
                        activeChannelVideos = emptyList(),
                        analyticsOverview = null,
                        channelHealthReport = null
                    )
                }
            }
            showToast("Channel disconnected.")
        }
    }

    // --- 1. Thumbnail Scorer & A/B Compare ---
    fun setAbCompareMode(enabled: Boolean) {
        _uiState.update { it.copy(isAbCompareMode = enabled, thumbnailError = null) }
    }

    fun setThumbnailVideoTitle(title: String) {
        _uiState.update { it.copy(thumbnailVideoTitle = title) }
    }

    fun loadBitmapFromUri(context: Context, uri: Uri, isSlotB: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                } ?: throw IllegalArgumentException("Unable to decode selected image.")
            }.onSuccess { bmp ->
                _uiState.update {
                    if (isSlotB) it.copy(thumbnailBitmapB = bmp, thumbnailError = null)
                    else it.copy(thumbnailBitmapA = bmp, thumbnailError = null)
                }
            }.onFailure { err ->
                _uiState.update { it.copy(thumbnailError = err.localizedMessage ?: "Failed to load image.") }
            }
        }
    }

    fun loadThumbnailFromVideoUrlOrDetail(videoUrlOrId: String, titleIfKnown: String = "", isSlotB: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isScoringThumbnail = true, thumbnailError = null) }
            runCatching {
                val vid = youTubeApiClient.extractVideoId(videoUrlOrId)
                    ?: throw IllegalArgumentException("Enter a valid YouTube video URL or 11-character video ID.")
                val urlsToTry = listOf(
                    "https://i.ytimg.com/vi/$vid/maxresdefault.jpg",
                    "https://i.ytimg.com/vi/$vid/hqdefault.jpg"
                )
                var loadedBmp: Bitmap? = null
                for (u in urlsToTry) {
                    val bmp = runCatching {
                        URL(u).openStream().use { BitmapFactory.decodeStream(it) }
                    }.getOrNull()
                    if (bmp != null) {
                        loadedBmp = bmp
                        break
                    }
                }
                loadedBmp ?: throw IllegalStateException("Could not download thumbnail for video $vid.")
            }.onSuccess { bmp ->
                _uiState.update {
                    it.copy(
                        isScoringThumbnail = false,
                        thumbnailBitmapA = if (isSlotB) it.thumbnailBitmapA else bmp,
                        thumbnailBitmapB = if (isSlotB) bmp else it.thumbnailBitmapB,
                        thumbnailVideoTitle = if (titleIfKnown.isNotBlank()) titleIfKnown else it.thumbnailVideoTitle
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isScoringThumbnail = false,
                        thumbnailError = err.localizedMessage ?: "Failed to fetch YouTube thumbnail."
                    )
                }
            }
        }
    }

    fun runThumbnailScorer() {
        val state = _uiState.value
        val bmpA = state.thumbnailBitmapA
        if (bmpA == null) {
            _uiState.update { it.copy(thumbnailError = "Select a thumbnail image from your device, connected videos, or a YouTube link first.") }
            return
        }

        if (state.isAbCompareMode) {
            val bmpB = state.thumbnailBitmapB
            if (bmpB == null) {
                _uiState.update { it.copy(thumbnailError = "Select Thumbnail B to run a head-to-head A/B comparison.") }
                return
            }
            _uiState.update { it.copy(isScoringThumbnail = true, thumbnailError = null) }
            viewModelScope.launch {
                aiService.compareThumbnailsAb(bmpA, bmpB, state.thumbnailVideoTitle)
                    .onSuccess { res ->
                        _uiState.update { it.copy(isScoringThumbnail = false, thumbnailAbResult = res) }
                        persistSavedWork(
                            workType = WorkType.AB_COMPARE,
                            title = state.thumbnailVideoTitle.ifBlank { "Thumbnail A/B Comparison" },
                            subtitle = "Winner: Thumbnail ${res.winner} (${res.scoreA} vs ${res.scoreB})",
                            score = maxOf(res.scoreA, res.scoreB),
                            payloadJson = JSONObject().apply {
                                put("winner", res.winner)
                                put("scoreA", res.scoreA)
                                put("scoreB", res.scoreB)
                                put("verdict", res.headlineVerdict)
                            }.toString()
                        )
                    }
                    .onFailure { err ->
                        _uiState.update {
                            it.copy(
                                isScoringThumbnail = false,
                                thumbnailError = err.localizedMessage ?: "Thumbnail A/B comparison failed."
                            )
                        }
                    }
            }
        } else {
            _uiState.update { it.copy(isScoringThumbnail = true, thumbnailError = null) }
            viewModelScope.launch {
                aiService.scoreThumbnailWithVision(
                    bitmap = bmpA,
                    pairedVideoTitle = state.thumbnailVideoTitle,
                    nicheContext = state.activeChannel?.title.orEmpty()
                ).onSuccess { res ->
                    _uiState.update { it.copy(isScoringThumbnail = false, thumbnailScoreResult = res) }
                    persistSavedWork(
                        workType = WorkType.THUMBNAIL_SCORE,
                        title = state.thumbnailVideoTitle.ifBlank { "Thumbnail Vision Analysis" },
                        subtitle = "AI Estimate: ${res.overallScore}/100 • Synergy: ${res.titleSynergyScore}/100",
                        score = res.overallScore,
                        payloadJson = JSONObject().apply {
                            put("overallScore", res.overallScore)
                            put("titleSynergyScore", res.titleSynergyScore)
                            put("verdict", res.titleSynergyVerdict)
                            put("fixes", res.actionableFixes.joinToString("\n• "))
                        }.toString()
                    )
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isScoringThumbnail = false,
                            thumbnailError = err.localizedMessage ?: "Thumbnail Vision scoring failed."
                        )
                    }
                }
            }
        }
    }

    // --- 2. Title Optimizer ---
    fun updateTitleInputs(title: String, context: String = _uiState.value.titleNicheContext) {
        _uiState.update { it.copy(titleInput = title, titleNicheContext = context, titleError = null) }
    }

    fun runTitleOptimizer() {
        val state = _uiState.value
        if (state.titleInput.isBlank()) {
            _uiState.update { it.copy(titleError = "Please enter a video title to score and optimize.") }
            return
        }
        _uiState.update { it.copy(isOptimizingTitle = true, titleError = null) }
        viewModelScope.launch {
            aiService.scoreAndOptimizeTitle(
                rawTitle = state.titleInput,
                videoContextOrNiche = state.titleNicheContext.ifBlank { state.activeChannel?.title.orEmpty() }
            ).onSuccess { res ->
                _uiState.update { it.copy(isOptimizingTitle = false, titleResult = res) }
                persistSavedWork(
                    workType = WorkType.TITLE_OPTIMIZER,
                    title = res.originalTitle,
                    subtitle = "Score: ${res.overallScore}/100 • Top Alternative: ${res.tenImprovedTitles.firstOrNull()?.title.orEmpty()}",
                    score = res.overallScore,
                    payloadJson = JSONObject().apply {
                        put("originalTitle", res.originalTitle)
                        put("overallScore", res.overallScore)
                        put("topTitles", res.tenImprovedTitles.joinToString("\n") { "${it.score}/100: ${it.title}" })
                    }.toString()
                )
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isOptimizingTitle = false,
                        titleError = err.localizedMessage ?: "Title optimization failed."
                    )
                }
            }
        }
    }

    // --- 3. Description & Chapters Optimizer ---
    fun updateDescriptionInputs(title: String, existingDesc: String, transcript: String) {
        _uiState.update {
            it.copy(
                descTitleInput = title,
                descExistingInput = existingDesc,
                descTranscriptInput = transcript,
                descriptionError = null
            )
        }
    }

    fun runDescriptionOptimizer() {
        val state = _uiState.value
        if (state.descTitleInput.isBlank() && state.descExistingInput.isBlank()) {
            _uiState.update { it.copy(descriptionError = "Enter a video title/topic or paste an existing description.") }
            return
        }
        _uiState.update { it.copy(isOptimizingDescription = true, descriptionError = null) }
        viewModelScope.launch {
            aiService.optimizeDescriptionAndChapters(
                videoTitleOrTopic = state.descTitleInput,
                existingDescription = state.descExistingInput,
                transcriptOrOutline = state.descTranscriptInput
            ).onSuccess { res ->
                _uiState.update { it.copy(isOptimizingDescription = false, descriptionResult = res) }
                persistSavedWork(
                    workType = WorkType.DESCRIPTION_OPTIMIZER,
                    title = state.descTitleInput.ifBlank { "Optimized Description" },
                    subtitle = res.firstTwoLinesHook.take(100),
                    score = res.overallScore,
                    payloadJson = JSONObject().apply {
                        put("hook", res.firstTwoLinesHook)
                        put("fullDescription", res.fullOptimizedDescription)
                    }.toString()
                )
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isOptimizingDescription = false,
                        descriptionError = err.localizedMessage ?: "Description optimization failed."
                    )
                }
            }
        }
    }

    // --- 4. Tags & Hashtags Generator ---
    fun updateTagsInput(topic: String) {
        _uiState.update { it.copy(tagsTopicInput = topic, tagsError = null) }
    }

    fun runTagsGenerator() {
        val topic = _uiState.value.tagsTopicInput.trim()
        if (topic.isBlank()) {
            _uiState.update { it.copy(tagsError = "Enter a video topic or title to generate tags and hashtags.") }
            return
        }
        _uiState.update { it.copy(isGeneratingTags = true, tagsError = null) }
        viewModelScope.launch {
            // Pull real tags from top-ranking videos when API key is configured
            val realRankingTags = if (youTubeApiClient.isApiKeyConfigured()) {
                youTubeApiClient.performKeywordResearch(topic, _uiState.value.oauthAccessToken)
                    .getOrNull()?.extractedTagsFromWinners.orEmpty()
            } else emptyList()

            aiService.generateTagsAndHashtags(topic, realRankingTags)
                .onSuccess { res ->
                    _uiState.update { it.copy(isGeneratingTags = false, tagsResult = res) }
                    persistSavedWork(
                        workType = WorkType.TAGS_GENERATOR,
                        title = topic,
                        subtitle = "${res.recommendedTags.size} tags (${res.totalCharacterCount}/500 chars) • ${res.threeBestHashtags.joinToString(" ")}",
                        score = 94,
                        payloadJson = JSONObject().apply {
                            put("tags", res.recommendedTags.joinToString(", "))
                            put("hashtags", res.threeBestHashtags.joinToString(" "))
                        }.toString()
                    )
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isGeneratingTags = false,
                            tagsError = err.localizedMessage ?: "Tag generation failed."
                        )
                    }
                }
        }
    }

    // --- 5. Keyword Research (Real Data Only) ---
    fun updateKeywordInput(keyword: String) {
        _uiState.update { it.copy(keywordInput = keyword, keywordError = null) }
    }

    fun runKeywordResearch(forceRefresh: Boolean = false) {
        val kw = _uiState.value.keywordInput.trim()
        if (kw.isBlank()) {
            _uiState.update { it.copy(keywordError = "Enter a keyword or search phrase to research.") }
            return
        }
        _uiState.update { it.copy(isResearchingKeyword = true, keywordError = null) }
        viewModelScope.launch {
            youTubeApiClient.performKeywordResearch(kw, _uiState.value.oauthAccessToken, forceRefresh)
                .onSuccess { res ->
                    _uiState.update { it.copy(isResearchingKeyword = false, keywordResult = res) }
                    persistSavedWork(
                        workType = WorkType.KEYWORD_RESEARCH,
                        title = res.keyword,
                        subtitle = "Opportunity: ${res.opportunityScore}/100 • Demand: ${res.demandScore}/100 • Competition: ${res.competitionScore}/100",
                        score = res.opportunityScore,
                        payloadJson = JSONObject().apply {
                            put("keyword", res.keyword)
                            put("opportunityScore", res.opportunityScore)
                            put("avgViews", res.avgViewsTopResults)
                            put("relatedKeywords", res.relatedKeywords.joinToString(", "))
                        }.toString()
                    )
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isResearchingKeyword = false,
                            keywordError = err.localizedMessage ?: "Keyword research failed."
                        )
                    }
                }
        }
    }

    // --- 6. Video Analyzer & SEO Audit ---
    fun updateAnalyzedVideoInput(input: String) {
        _uiState.update { it.copy(analyzedVideoInput = input, videoAnalyzerError = null) }
    }

    fun analyzeAndAuditVideo(videoIdOrUrl: String = _uiState.value.analyzedVideoInput) {
        val clean = videoIdOrUrl.trim()
        if (clean.isBlank()) {
            _uiState.update { it.copy(videoAnalyzerError = "Paste a YouTube video link or 11-character video ID.") }
            return
        }
        _uiState.update { it.copy(isAnalyzingVideo = true, videoAnalyzerError = null) }
        viewModelScope.launch {
            youTubeApiClient.fetchVideoById(clean, _uiState.value.oauthAccessToken)
                .onSuccess { video ->
                    val audit = youTubeApiClient.runVideoSeoAudit(video)
                    _uiState.update {
                        it.copy(
                            isAnalyzingVideo = false,
                            analyzedVideo = video,
                            videoAuditResult = audit
                        )
                    }
                    persistSavedWork(
                        workType = WorkType.VIDEO_AUDIT,
                        title = video.title,
                        subtitle = "ZEDCraft Audit Score: ${audit.zedCraftScore}/100 • ${YouTubeApiClient.formatCompact(video.viewCount)} views",
                        score = audit.zedCraftScore,
                        payloadJson = JSONObject().apply {
                            put("videoId", video.videoId)
                            put("title", video.title)
                            put("score", audit.zedCraftScore)
                            put("todos", audit.prioritizedTodoList.joinToString("\n• "))
                        }.toString()
                    )
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isAnalyzingVideo = false,
                            videoAnalyzerError = err.localizedMessage ?: "Failed to analyze video."
                        )
                    }
                }
        }
    }

    // --- 7. Trend & Idea Finder ---
    fun updateTrendFilters(region: String, categoryId: String) {
        _uiState.update {
            it.copy(
                selectedTrendRegion = region,
                selectedTrendCategoryId = categoryId,
                trendsError = null
            )
        }
        loadTrendingVideos(region, categoryId, forceRefresh = true)
    }

    fun loadTrendingVideos(
        region: String = _uiState.value.selectedTrendRegion,
        categoryId: String = _uiState.value.selectedTrendCategoryId,
        forceRefresh: Boolean = false
    ) {
        _uiState.update { it.copy(isLoadingTrends = true, trendsError = null) }
        viewModelScope.launch {
            youTubeApiClient.fetchTrendingVideos(
                regionCode = region,
                videoCategoryId = categoryId,
                oauthAccessToken = _uiState.value.oauthAccessToken,
                forceRefresh = forceRefresh
            ).onSuccess { list ->
                _uiState.update { it.copy(isLoadingTrends = false, trendingVideos = list) }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingTrends = false,
                        trendsError = err.localizedMessage ?: "Failed to load trending videos."
                    )
                }
            }
        }
    }

    fun updateIdeaNicheInput(niche: String) {
        _uiState.update { it.copy(ideaNicheInput = niche, ideasError = null) }
    }

    fun generateAiVideoIdeas() {
        val state = _uiState.value
        val niche = state.ideaNicheInput.trim().ifBlank { state.activeChannel?.title.orEmpty() }
        if (niche.isBlank()) {
            _uiState.update { it.copy(ideasError = "Enter your channel niche or topic to generate tailored video ideas.") }
            return
        }
        _uiState.update { it.copy(isGeneratingIdeas = true, ideasError = null) }
        viewModelScope.launch {
            aiService.generateChannelVideoIdeas(
                nicheOrTopic = niche,
                channel = state.activeChannel,
                topChannelVideos = state.activeChannelVideos.sortedByDescending { it.viewCount }.take(5),
                trendingVideos = state.trendingVideos.take(5)
            ).onSuccess { res ->
                _uiState.update { it.copy(isGeneratingIdeas = false, videoIdeasResult = res) }
                persistSavedWork(
                    workType = WorkType.TREND_IDEA,
                    title = "6 Video Ideas for $niche",
                    subtitle = res.ideas.firstOrNull()?.title.orEmpty(),
                    score = res.ideas.firstOrNull()?.opportunityScore ?: 90,
                    payloadJson = JSONObject().apply {
                        put("niche", niche)
                        put("ideas", res.ideas.joinToString("\n\n") { "${it.title}\nThumbnail: ${it.thumbnailConcept}" })
                    }.toString()
                )
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isGeneratingIdeas = false,
                        ideasError = err.localizedMessage ?: "Video idea generation failed."
                    )
                }
            }
        }
    }

    // --- 8. Competitor Tracking ---
    fun updateCompetitorInput(input: String) {
        _uiState.update { it.copy(competitorInput = input, competitorError = null) }
    }

    fun addCompetitorChannel() {
        val query = _uiState.value.competitorInput.trim()
        if (query.isBlank()) {
            _uiState.update { it.copy(competitorError = "Enter a public channel @handle, Channel ID, or video URL.") }
            return
        }
        _uiState.update { it.copy(isAddingCompetitor = true, competitorError = null) }
        viewModelScope.launch {
            val token = _uiState.value.oauthAccessToken
            youTubeApiClient.lookupChannel(query, token)
                .onSuccess { ch ->
                    val recent = youTubeApiClient.fetchChannelRecentVideos(ch, 10, token).getOrDefault(emptyList())
                    val recentAvg = if (recent.isNotEmpty()) recent.sumOf { it.viewCount } / recent.size else 0L
                    val comp = CompetitorChannel(
                        id = ch.channelId,
                        userId = _uiState.value.userUid,
                        channelId = ch.channelId,
                        title = ch.title,
                        handle = ch.handle,
                        thumbnailUrl = ch.thumbnailUrl,
                        subscriberCount = ch.subscriberCount,
                        viewCount = ch.viewCount,
                        videoCount = ch.videoCount,
                        recentAvgViews = recentAvg
                    )
                    if (_uiState.value.isSignedInWithGoogle) {
                        firestoreRepository.addOrUpdateCompetitor(comp)
                    } else {
                        _uiState.update { state ->
                            state.copy(
                                trackedCompetitors = (state.trackedCompetitors.filterNot { it.channelId == comp.channelId } + comp)
                                    .sortedByDescending { it.subscriberCount }
                            )
                        }
                    }
                    _uiState.update { it.copy(isAddingCompetitor = false, competitorInput = "") }
                    showToast("Added ${ch.title} to Competitor Tracker!")
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isAddingCompetitor = false,
                            competitorError = err.localizedMessage ?: "Could not find competitor channel."
                        )
                    }
                }
        }
    }

    fun removeCompetitor(channelId: String) {
        viewModelScope.launch {
            if (_uiState.value.isSignedInWithGoogle) {
                firestoreRepository.removeCompetitor(channelId)
            } else {
                _uiState.update { state ->
                    state.copy(trackedCompetitors = state.trackedCompetitors.filterNot { it.channelId == channelId })
                }
            }
        }
    }

    // --- 9. ZD AI Assistant Chat & Skill Pack ---
    fun selectZdSkill(skill: ZdSkillModule) {
        _uiState.update {
            it.copy(
                currentTab = PrimaryTab.ZD,
                zdSubTab = 0,
                activeZdSkill = skill
            )
        }
    }

    fun updateZdInput(text: String) {
        _uiState.update { it.copy(zdInputText = text, zdError = null) }
    }

    fun sendZdPrompt(promptOverride: String? = null, skillOverride: ZdSkillModule? = null) {
        val state = _uiState.value
        val prompt = (promptOverride ?: state.zdInputText).trim()
        if (prompt.isBlank()) return

        val routedSkill = skillOverride ?: ZdSkillPack.routeSkillForPrompt(prompt, state.activeZdSkill.id)
        val userMessage = ZdChatMessage(
            id = "local_u_${System.currentTimeMillis()}",
            userId = state.userUid.ifBlank { "guest" },
            sessionId = "default",
            role = "user",
            content = prompt,
            skillId = routedSkill.id,
            modelUsed = ZedCraftAiConfig.ZD_MAIN_MODEL
        )

        _uiState.update {
            it.copy(
                zdInputText = if (promptOverride == null) "" else it.zdInputText,
                isZdThinking = true,
                zdError = null,
                activeZdSkill = routedSkill,
                zdSubTab = 0,
                zdChatMessages = if (!it.isSignedInWithGoogle) it.zdChatMessages + userMessage else it.zdChatMessages
            )
        }

        viewModelScope.launch {
            if (state.isSignedInWithGoogle) {
                firestoreRepository.appendZdChatMessage(
                    sessionId = "default",
                    role = "user",
                    content = prompt,
                    skillId = routedSkill.id,
                    modelUsed = ZedCraftAiConfig.ZD_MAIN_MODEL
                )
            }

            val historyPairs = _uiState.value.zdChatMessages.takeLast(8).map { it.role to it.content }
            aiService.sendZdAssistantMessage(
                userPrompt = prompt,
                explicitSkillId = skillOverride?.id,
                connectedChannel = state.activeChannel,
                recentVideos = state.activeChannelVideos,
                analyticsOverview = state.analyticsOverview,
                healthReport = state.channelHealthReport,
                recentChatHistory = historyPairs,
                attachedThumbnail = state.thumbnailBitmapA
            ).onSuccess { response ->
                val toolJson = if (response.toolCard != null) {
                    JSONObject().apply {
                        put("toolType", response.toolCard.toolType.id)
                        put("badgeText", response.toolCard.badgeText)
                        put("title", response.toolCard.title)
                        put("score", response.toolCard.score)
                        put("bullets", response.toolCard.bulletPoints.joinToString("\n• "))
                        put("copyableText", response.toolCard.copyableText)
                    }.toString()
                } else ""

                if (_uiState.value.isSignedInWithGoogle) {
                    firestoreRepository.appendZdChatMessage(
                        sessionId = "default",
                        role = "model",
                        content = response.replyMarkdown,
                        skillId = response.routedSkill.id,
                        modelUsed = response.modelUsed,
                        toolCardJson = toolJson
                    )
                } else {
                    val modelMessage = ZdChatMessage(
                        id = "local_m_${System.currentTimeMillis()}",
                        userId = "guest",
                        sessionId = "default",
                        role = "model",
                        content = response.replyMarkdown,
                        skillId = response.routedSkill.id,
                        toolCardJson = toolJson,
                        modelUsed = response.modelUsed
                    )
                    _uiState.update { it.copy(zdChatMessages = it.zdChatMessages + modelMessage) }
                }

                _uiState.update {
                    it.copy(
                        isZdThinking = false,
                        zdLatestResponse = response,
                        activeZdSkill = response.routedSkill
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isZdThinking = false,
                        zdError = err.localizedMessage ?: "ZD encountered an error generating a response."
                    )
                }
            }
        }
    }

    fun clearZdConversation() {
        viewModelScope.launch {
            if (_uiState.value.isSignedInWithGoogle) {
                firestoreRepository.clearZdChatHistory()
            }
            _uiState.update { it.copy(zdChatMessages = emptyList(), zdLatestResponse = null) }
            showToast("ZD conversation cleared.")
        }
    }

    // --- 10. Saved Work & Data Management ---
    private suspend fun persistSavedWork(
        workType: String,
        title: String,
        subtitle: String,
        score: Int,
        payloadJson: String
    ) {
        val channelId = _uiState.value.activeChannel?.channelId.orEmpty()
        if (_uiState.value.isSignedInWithGoogle) {
            firestoreRepository.saveWorkItem(
                workType = workType,
                title = title,
                subtitle = subtitle,
                score = score,
                payloadJson = payloadJson,
                channelId = channelId
            )
        } else {
            cacheDao.insertLocalSavedWork(
                LocalSavedWorkEntity(
                    workType = workType,
                    title = title,
                    subtitle = subtitle,
                    score = score,
                    payloadJson = payloadJson,
                    channelId = channelId
                )
            )
        }
    }

    fun deleteSavedWorkItem(item: SavedWorkItem) {
        viewModelScope.launch {
            if (item.id.startsWith("local_")) {
                val localId = item.id.removePrefix("local_").toLongOrNull() ?: return@launch
                cacheDao.deleteLocalSavedWork(localId)
            } else if (_uiState.value.isSignedInWithGoogle) {
                firestoreRepository.deleteSavedWork(item.id)
            }
            showToast("Removed saved item.")
        }
    }

    fun toggleSelectForCompare(item: SavedWorkItem) {
        _uiState.update { state ->
            when {
                state.selectedCompareItemA?.id == item.id -> state.copy(selectedCompareItemA = null)
                state.selectedCompareItemB?.id == item.id -> state.copy(selectedCompareItemB = null)
                state.selectedCompareItemA == null -> state.copy(selectedCompareItemA = item)
                else -> state.copy(selectedCompareItemB = item)
            }
        }
    }

    fun clearCompareSelection() {
        _uiState.update { it.copy(selectedCompareItemA = null, selectedCompareItemB = null) }
    }

    fun clearLocalApiCache() {
        viewModelScope.launch {
            cacheDao.clearAllCache()
            showToast("Local YouTube API cache cleared.")
        }
    }

    fun deleteAllUserAndLocalData() {
        viewModelScope.launch {
            cacheDao.clearAllCache()
            cacheDao.clearLocalSavedWork()
            if (_uiState.value.isSignedInWithGoogle) {
                firestoreRepository.deleteAllUserData()
            }
            _uiState.update {
                it.copy(
                    activeChannel = null,
                    activeChannelVideos = emptyList(),
                    analyticsOverview = null,
                    channelHealthReport = null,
                    connectedChannels = emptyList(),
                    savedWorkItems = emptyList(),
                    trackedCompetitors = emptyList(),
                    zdChatMessages = emptyList(),
                    toastMessage = "All cloud and local ZEDCraft data has been permanently deleted."
                )
            }
        }
    }

    // --- 11. App Updates (Hidden Release Channel) ---
    fun checkForGitHubUpdates() {
        _uiState.update { it.copy(isCheckingGitHubUpdate = true, githubUpdateError = null) }
        viewModelScope.launch {
            gitHubUpdateClient.checkForUpdates()
                .onSuccess { info ->
                    _uiState.update {
                        it.copy(
                            isCheckingGitHubUpdate = false,
                            githubUpdateInfo = info,
                            githubUpdateError = null
                        )
                    }
                    showToast(info.statusSummary)
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isCheckingGitHubUpdate = false,
                            githubUpdateError = err.localizedMessage ?: "Failed to check for updates."
                        )
                    }
                }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val firestore = FirebaseFirestore.getInstance(databaseId)
                val firestoreRepo = ZedCraftFirestoreRepository(firestore)
                val localDb = ZedCraftLocalDatabase.getInstance(app)
                val cacheDao = localDb.cacheDao()
                val ytClient = YouTubeApiClient(cacheDao)
                val aiService = ZedCraftAiService()
                val ghClient = GitHubUpdateClient(app)
                return ZedCraftViewModel(
                    firestoreRepository = firestoreRepo,
                    youTubeApiClient = ytClient,
                    aiService = aiService,
                    cacheDao = cacheDao,
                    gitHubUpdateClient = ghClient
                ) as T
            }
        }
    }
}
