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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.ui.components.DataBadge
import com.example.ui.components.ErrorBanner
import com.example.ui.components.GlassCard
import com.example.ui.theme.ZedCyan
import com.example.ui.theme.ZedSuccess
import com.example.ui.theme.ZedViolet
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val TAG = "ZedCraftAuth"

// Read-only YouTube scopes only — never request write access
const val SCOPE_YOUTUBE_READONLY = "https://www.googleapis.com/auth/youtube.readonly"
const val SCOPE_YT_ANALYTICS_READONLY = "https://www.googleapis.com/auth/yt-analytics.readonly"

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        onUnauthenticated()
        return
    }

    // Separate single-option request for silent auto sign-in
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

    scope.launch {
        try {
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (e: Exception) {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
        return
    }

    // Separate single-option request for interactive sign-in
    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

    scope.launch {
        try {
            val result = credentialManager.getCredential(context as Activity, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                onAuthError("Unexpected credential type returned.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Google Sign-In failed.")
        }
    }
}

fun signOutFromZedCraft(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}

/**
 * Composable launcher for authorizing read-only YouTube Data API v3 & YouTube Analytics API OAuth 2.0 scopes.
 */
@Composable
fun rememberYouTubeReadOnlyOAuthLauncher(
    onTokenReceived: (String) -> Unit,
    onError: (String) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val intentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        try {
            val authResult = Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(activityResult.data)
            val token = authResult.accessToken
            if (!token.isNullOrBlank()) {
                onTokenReceived(token)
            } else {
                onError("YouTube read-only authorization was cancelled or did not return an access token.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "YouTube OAuth resolution failed", e)
            onError(e.localizedMessage ?: "YouTube OAuth authorization failed.")
        }
    }

    return {
        val authRequest = AuthorizationRequest.builder()
            .setRequestedScopes(
                listOf(
                    Scope(SCOPE_YOUTUBE_READONLY),
                    Scope(SCOPE_YT_ANALYTICS_READONLY)
                )
            )
            .build()

        Identity.getAuthorizationClient(context)
            .authorize(authRequest)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent != null) {
                        intentSenderLauncher.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        )
                    }
                } else {
                    val token = result.accessToken
                    if (!token.isNullOrBlank()) {
                        onTokenReceived(token)
                    } else {
                        onError("No OAuth token returned. Make sure your Google account is added as a Test User on the OAuth consent screen.")
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "AuthorizationClient.authorize failed", e)
                onError(
                    "YouTube OAuth scope check: ${e.localizedMessage ?: "Failed"}. You can also connect any channel by @handle below."
                )
            }
    }
}

@Composable
fun OnboardingAndConnectScreen(
    onGoogleSignInSuccess: () -> Unit,
    onYouTubeOAuthSuccess: (String) -> Unit,
    onConnectByHandle: (String) -> Unit,
    onUsePublicToolsWithoutConnecting: () -> Unit,
    onOpenPrivacyAndTerms: () -> Unit,
    onOpenGitHubUpdater: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var isSigningIn by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var handleInput by remember { mutableStateOf("") }

    val triggerYouTubeOAuth = rememberYouTubeReadOnlyOAuthLauncher(
        onTokenReceived = { token ->
            isSigningIn = false
            onYouTubeOAuthSuccess(token)
        },
        onError = { msg ->
            isSigningIn = false
            authError = msg
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Brand Header with GitHub Update Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.ic_zedcraft_logo),
                    contentDescription = "ZEDCraft Logo",
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "ZEDCraft",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = onOpenGitHubUpdater,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ZedCyan.copy(alpha = 0.5f)),
                modifier = Modifier.testTag("onboarding_github_update_button")
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdateAlt,
                    contentDescription = "Check for GitHub Updates",
                    tint = ZedCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Updates", style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Hero Art Banner
        Image(
            painter = painterResource(id = R.drawable.img_zedcraft_hero_1794133254737),
            contentDescription = "ZEDCraft Creator Studio",
            modifier = Modifier
                .fillMaxWidth()
                .height(175.dp)
                .clip(RoundedCornerShape(22.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(16.dp))

        DataBadge(text = "Powered by Gemini 4 Argon", isAi = true)

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Grow Smarter With Real Data & ZD AI",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Thumbnail Vision Scorer, Title & Description Optimizer, Real Keyword Research, Video SEO Audits, Channel Health Checks, and ZD—your 12-skill YouTube strategist.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(18.dp))

        if (authError != null) {
            ErrorBanner(message = authError!!)
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Primary Card: Google Sign-In + YouTube Channel Connect
        GlassCard(accentBorderColor = ZedCyan.copy(alpha = 0.6f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = ZedCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Connect Your YouTube Channel",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Uses Google Sign-In & OAuth 2.0 read-only scopes (youtube.readonly & yt-analytics.readonly). ZEDCraft never modifies or deletes your videos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = {
                    isSigningIn = true
                    authError = null
                    onGoogleSignInClicked(
                        context = context,
                        credentialManager = credentialManager,
                        onAuthSuccess = {
                            onGoogleSignInSuccess()
                            triggerYouTubeOAuth()
                        },
                        onAuthError = { msg ->
                            isSigningIn = false
                            authError = msg
                        },
                        scope = scope,
                        onAuthCancelled = {
                            isSigningIn = false
                        }
                    )
                },
                enabled = !isSigningIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("google_sign_in_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (isSigningIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Connecting with Google...")
                } else {
                    Icon(Icons.Default.AccountCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign in with Google & Connect Channel", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Quick connect by @handle or Channel ID
            OutlinedTextField(
                value = handleInput,
                onValueChange = { handleInput = it },
                label = { Text("Or connect by @handle, Channel ID, or Video Link") },
                placeholder = { Text("e.g. @mkbhd or UC...") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_handle_input"),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    if (handleInput.isNotBlank()) {
                        onConnectByHandle(handleInput)
                    } else {
                        authError = "Enter a channel @handle, Channel ID, or video URL first."
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("connect_handle_button"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ZedCyan.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Load Channel Dashboard by @Handle / Link")
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Public tools option without connecting
        GlassCard(accentBorderColor = ZedViolet.copy(alpha = 0.45f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = ZedViolet,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Use Public Tools Without Connecting",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Access Thumbnail Scorer, Title Optimizer, Tags Generator, Keyword Research, and ZD AI immediately.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onUsePublicToolsWithoutConnecting,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("public_tools_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Continue to Public Optimization Tools")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            onClick = onOpenPrivacyAndTerms,
            modifier = Modifier.testTag("privacy_terms_button")
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp), tint = ZedCyan)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "YouTube API Terms of Service & ZEDCraft Privacy Policy",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
