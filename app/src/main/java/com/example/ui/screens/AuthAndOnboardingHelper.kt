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
import com.example.ui.theme.ZedCoralError
import com.example.ui.theme.ZedCyanPrimary
import com.example.ui.theme.ZedDarkBackground
import com.example.ui.theme.ZedDarkSurfaceElevated
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ZedDarkBackground)
    ) {
        // Ambient Soft Glowing Corner Orbs (matching Image 1's visual atmosphere in #007E08 & #D9FF00)
        Box(
            modifier = Modifier
                .size(220.dp)
                .align(Alignment.TopStart)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            com.example.ui.theme.ZedBrandKellyGreen.copy(alpha = 0.28f),
                            ZedCyanPrimary.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(240.dp)
                .align(Alignment.BottomEnd)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            com.example.ui.theme.ZedBrandKellyGreen.copy(alpha = 0.26f),
                            ZedCyanPrimary.copy(alpha = 0.09f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Sculpted 3D Glass Squircle Emblem Medallion (matching Image 1's center hero squircle)
            Box(
                modifier = Modifier
                    .size(116.dp)
                    .clip(RoundedCornerShape(32.dp))
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
                                ZedCyanPrimary.copy(alpha = 0.7f),
                                com.example.ui.theme.ZedBrandKellyGreen.copy(alpha = 0.55f)
                            )
                        ),
                        shape = RoundedCornerShape(32.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(66.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    ZedCyanPrimary,
                                    com.example.ui.theme.ZedBrandMidGradient,
                                    com.example.ui.theme.ZedBrandKellyGreen
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "ZEDCraft Emblem",
                        tint = com.example.ui.theme.ZedOnPrimaryDark,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Welcome to ZEDCraft",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = com.example.ui.theme.ZedTextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Connect your channel to score, optimize & grow",
                style = MaterialTheme.typography.bodyMedium,
                color = ZedTextSecondary
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Primary Option: Connect YouTube Channel via OAuth 2.0 (Image 1 capsule button + glass card)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
                border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(ZedCyanPrimary.copy(alpha = 0.16f))
                                .border(1.dp, ZedCyanPrimary.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
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
                                fontWeight = FontWeight.Bold,
                                color = com.example.ui.theme.ZedTextPrimary
                            )
                            Text(
                                text = "Load your uploaded videos, get Smart Suggestions, and apply changes live to YouTube.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ZedTextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Full-Width Pill Button with Circular Trailing Badge (matching Image 1's Login button)
                    Button(
                        onClick = launchOAuth,
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("oauth_connect_button"),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ZedCyanPrimary,
                            contentColor = com.example.ui.theme.ZedOnPrimaryDark
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Spacer(modifier = Modifier.size(34.dp))
                            Text(
                                text = "Connect YouTube Channel",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(com.example.ui.theme.ZedBrandKellyGreen.copy(alpha = 0.22f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = com.example.ui.theme.ZedOnPrimaryDark,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tracked Section Divider (matching Image 1's "OR CONTINUE WITH" divider)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(ZedCardBorder)
                )
                Text(
                    text = "  OR INSPECT PUBLIC CHANNEL  ",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = ZedTextMuted
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(ZedCardBorder)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Secondary Option: Public Channel Lookup (Image 1 frosted input field style)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
                border = BorderStroke(1.dp, ZedCardBorder)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    OutlinedTextField(
                        value = channelQuery,
                        onValueChange = {
                            channelQuery = it
                            inlineValidationError = null
                        },
                        placeholder = {
                            Text(
                                text = "@handle, Channel ID (UC...), or video URL",
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
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ZedCyanPrimary,
                            unfocusedBorderColor = ZedCardBorder,
                            focusedContainerColor = ZedDarkBackground.copy(alpha = 0.65f),
                            unfocusedContainerColor = ZedDarkBackground.copy(alpha = 0.65f)
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
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = com.example.ui.theme.ZedBrandKellyGreen,
                            contentColor = com.example.ui.theme.ZedTextPrimary
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = com.example.ui.theme.ZedTextPrimary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Loading Channel Data...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
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
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = ZedCoralError,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 3 Squircle Feature Tiles (matching Image 1's 3 bottom squircle tiles)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
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

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedButton(
                onClick = onDismissOnboarding,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("skip_onboarding_button"),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, ZedCardBorder)
            ) {
                Text(
                    text = "Explore Optimize, Research & ZD Tools First",
                    color = ZedTextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "SCORE  •  OPTIMIZE  •  GROW TOGETHER",
                style = MaterialTheme.typography.labelSmall,
                color = ZedTextMuted,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
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
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, ZedCardBorder, RoundedCornerShape(20.dp)),
        color = ZedDarkSurfaceElevated
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
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
