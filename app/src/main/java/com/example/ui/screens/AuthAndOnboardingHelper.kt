package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.ZedAmberWarning
import com.example.ui.theme.ZedCardBorder
import com.example.ui.theme.ZedCyanPrimary
import com.example.ui.theme.ZedDarkBackground
import com.example.ui.theme.ZedEmeraldSuccess
import com.example.ui.theme.ZedGlassSurface
import com.example.ui.theme.ZedPurpleAccent
import com.example.ui.theme.ZedTextMuted
import com.example.ui.theme.ZedTextSecondary
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlin.coroutines.resume
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Google Identity Services (`AuthorizationClient`) helper for YouTube OAuth 2.0 scopes:
 * - `https://www.googleapis.com/auth/youtube.force-ssl` (Read & write videos, titles, descriptions, tags, thumbnails)
 * - `https://www.googleapis.com/auth/yt-analytics.readonly` (YouTube Analytics reports)
 *
 * Also persists the token and connected channel metadata so ZEDCraft automatically reconnects
 * and silently refreshes expired OAuth access tokens without asking the user to sign in repeatedly.
 */
object YouTubeOAuthHelper {
    const val SCOPE_YOUTUBE_FORCE_SSL = "https://www.googleapis.com/auth/youtube.force-ssl"
    const val SCOPE_YT_ANALYTICS_READONLY = "https://www.googleapis.com/auth/yt-analytics.readonly"

    private const val PREFS_NAME = "zedcraft_youtube_oauth_store"
    private const val KEY_ACCESS_TOKEN = "oauth_access_token"
    private const val KEY_SAVED_AT_MS = "oauth_saved_at_ms"
    private const val KEY_CONNECTED_CHANNEL_ID = "connected_channel_id"
    private const val TAG = "YouTubeOAuthHelper"

    fun buildAuthorizationRequest(): AuthorizationRequest {
        return AuthorizationRequest.builder()
            .setRequestedScopes(
                listOf(
                    Scope(SCOPE_YOUTUBE_FORCE_SSL),
                    Scope(SCOPE_YT_ANALYTICS_READONLY)
                )
            )
            .build()
    }

    fun saveAccessToken(context: Context, token: String, channelId: String? = null) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, token)
            putLong(KEY_SAVED_AT_MS, System.currentTimeMillis())
            if (!channelId.isNullOrBlank()) {
                putString(KEY_CONNECTED_CHANNEL_ID, channelId)
            }
            apply()
        }
    }

    fun getSavedAccessToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() }
    }

    fun getSavedChannelId(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CONNECTED_CHANNEL_ID, null)?.takeIf { it.isNotBlank() }
    }

    fun clearSavedAuth(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    /**
     * Silently refreshes the Google OAuth 2.0 access token when the user has already granted consent.
     * Returns the fresh token if available without launching any interactive UI.
     */
    suspend fun refreshAccessTokenSilently(context: Context): String? = suspendCancellableCoroutine { cont ->
        try {
            val client = Identity.getAuthorizationClient(context)
            client.authorize(buildAuthorizationRequest())
                .addOnSuccessListener { result ->
                    if (!result.hasResolution() && !result.accessToken.isNullOrBlank()) {
                        val token = result.accessToken!!
                        saveAccessToken(context, token)
                        if (cont.isActive) cont.resume(token)
                    } else {
                        if (cont.isActive) cont.resume(getSavedAccessToken(context))
                    }
                }
                .addOnFailureListener { err ->
                    Log.w(TAG, "Silent token refresh failed, using cached token if valid", err)
                    if (cont.isActive) cont.resume(getSavedAccessToken(context))
                }
        } catch (e: Exception) {
            Log.w(TAG, "Silent token refresh exception", e)
            if (cont.isActive) cont.resume(getSavedAccessToken(context))
        }
    }
}

@Composable
fun rememberYouTubeOAuthLauncher(
    onAuthorized: (accessToken: String) -> Unit,
    onError: (message: String) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val intentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        if (activityResult.resultCode == Activity.RESULT_OK && activityResult.data != null) {
            try {
                val authResult = Identity.getAuthorizationClient(context)
                    .getAuthorizationResultFromIntent(activityResult.data!!)
                val token = authResult.accessToken
                if (!token.isNullOrBlank()) {
                    YouTubeOAuthHelper.saveAccessToken(context, token)
                    onAuthorized(token)
                } else {
                    onError("Sign-in succeeded, but no access token was returned. Please try again.")
                }
            } catch (e: Exception) {
                Log.e("YouTubeOAuthLauncher", "OAuth resolution error", e)
                onError("Could not complete YouTube sign-in. Please try again.")
            }
        } else {
            onError("YouTube channel connection was canceled.")
        }
    }

    return remember(context) {
        {
            scope.launch {
                try {
                    val authRequest = YouTubeOAuthHelper.buildAuthorizationRequest()
                    val authClient = Identity.getAuthorizationClient(context)
                    authClient.authorize(authRequest)
                        .addOnSuccessListener { authResult ->
                            if (authResult.hasResolution()) {
                                val pendingIntent = authResult.pendingIntent
                                if (pendingIntent != null) {
                                    intentSenderLauncher.launch(
                                        IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                                    )
                                } else {
                                    onError("Unable to open Google sign-in prompt.")
                                }
                            } else {
                                val token = authResult.accessToken
                                if (!token.isNullOrBlank()) {
                                    YouTubeOAuthHelper.saveAccessToken(context, token)
                                    onAuthorized(token)
                                } else {
                                    onError("Could not retrieve access token. Please try again.")
                                }
                            }
                        }
                        .addOnFailureListener { err ->
                            Log.e("YouTubeOAuthLauncher", "Google Identity authorize failed", err)
                            onError("Could not connect to Google Sign-In. Please check your internet connection and try again.")
                        }
                } catch (e: Exception) {
                    Log.e("YouTubeOAuthLauncher", "Unexpected OAuth launch error", e)
                    onError("Could not start Google Sign-In. Please try again.")
                }
            }
        }
    }
}

@Composable
fun OnboardingAndConnectScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onConnectOAuth: (String) -> Unit,
    onOAuthError: (String) -> Unit,
    onConnectPublicChannel: (String) -> Unit,
    onDismissOnboarding: () -> Unit
) {
    var channelQuery by remember { mutableStateOf("") }
    var inlineValidationError by remember { mutableStateOf<String?>(null) }

    val launchOAuth = rememberYouTubeOAuthLauncher(
        onAuthorized = onConnectOAuth,
        onError = onOAuthError
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ZedDarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hero Banner Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
            border = BorderStroke(1.dp, ZedCardBorder)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_zedcraft_hero_1794133254737),
                    contentDescription = "ZEDCraft YouTube Creator Studio Banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    ZedDarkBackground.copy(alpha = 0.85f),
                                    ZedDarkBackground
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(20.dp)
                ) {
                    Text(
                        text = "ZEDCraft",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Connect your YouTube channel to score, optimize, and apply titles, descriptions, tags, and thumbnails directly to your videos.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ZedTextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Feature Pillars
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PillarBadge(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.AutoAwesome,
                title = "1-Tap Apply",
                subtitle = "Live Video Edits",
                accent = ZedCyanPrimary
            )
            PillarBadge(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Analytics,
                title = "My Videos",
                subtitle = "Audit & Score",
                accent = ZedPurpleAccent
            )
            PillarBadge(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.TrendingUp,
                title = "ZD Coach",
                subtitle = "Growth Strategy",
                accent = ZedEmeraldSuccess
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Primary Option: Connect YouTube Channel via OAuth 2.0
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
            border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(ZedCyanPrimary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Connect YouTube Channel",
                            tint = ZedCyanPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Connect Your YouTube Channel",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Load your uploaded videos, optimize metadata with ZD, and apply changes directly to YouTube.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ZedTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = launchOAuth,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("oauth_connect_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ZedCyanPrimary,
                        contentColor = ZedDarkBackground
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Connect YouTube Channel",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Secondary Option: Public Channel Lookup
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
            border = BorderStroke(1.dp, ZedCardBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(ZedPurpleAccent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = "Analyze Channel by Handle",
                            tint = ZedPurpleAccent
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Inspect Any Public Channel",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Enter a @handle, Channel ID (UC...), or video URL to audit any public channel.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ZedTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = channelQuery,
                    onValueChange = {
                        channelQuery = it
                        inlineValidationError = null
                    },
                    placeholder = {
                        Text(
                            text = "e.g., @mkbhd, @MrBeast, or video URL",
                            color = ZedTextMuted
                        )
                    },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Channel Input",
                            tint = ZedCyanPrimary
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("channel_lookup_input"),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ZedCyanPrimary,
                        unfocusedBorderColor = ZedCardBorder,
                        focusedContainerColor = ZedDarkBackground.copy(alpha = 0.55f),
                        unfocusedContainerColor = ZedDarkBackground.copy(alpha = 0.55f)
                    )
                )

                if (inlineValidationError != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = inlineValidationError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = ZedAmberWarning
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val trimmed = channelQuery.trim()
                        if (trimmed.length < 2) {
                            inlineValidationError = "Please enter a valid @handle, Channel ID, or YouTube URL."
                        } else {
                            onConnectPublicChannel(trimmed)
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("connect_public_channel_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ZedPurpleAccent,
                        contentColor = Color.White
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Loading Channel Data...")
                    } else {
                        Text(
                            text = "Audit Public Channel",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
            ) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFFB4AB),
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onDismissOnboarding,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("skip_onboarding_button"),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, ZedCardBorder)
        ) {
            Text(
                text = "Explore Optimize, Research & ZD Tools First",
                color = ZedTextSecondary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PillarBadge(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, ZedCardBorder, RoundedCornerShape(16.dp)),
        color = ZedGlassSurface
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accent,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = ZedTextMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}
