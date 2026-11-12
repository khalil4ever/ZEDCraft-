package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ZdChatMessageEntity
import com.example.ui.components.ZdMarkdownMessage
import com.example.ui.components.stripMarkdownToCleanText
import com.example.ui.theme.ZedAmberWarning
import com.example.ui.theme.ZedBrandKellyGreen
import com.example.ui.theme.ZedCardBorder
import com.example.ui.theme.ZedCoralError
import com.example.ui.theme.ZedCyanPrimary
import com.example.ui.theme.ZedDarkBackground
import com.example.ui.theme.ZedDarkSurfaceElevated
import com.example.ui.theme.ZedEmeraldSuccess
import com.example.ui.theme.ZedGlassSurface
import com.example.ui.theme.ZedOnPrimaryDark
import com.example.ui.theme.ZedPurpleAccent
import com.example.ui.theme.ZedTextMuted
import com.example.ui.theme.ZedTextPrimary
import com.example.ui.theme.ZedTextSecondary
import com.example.ui.viewmodel.ZedCraftUiState
import com.example.ui.viewmodel.ZedCraftViewModel

@Composable
fun ZdAssistantScreen(
    uiState: ZedCraftUiState,
    chatMessages: List<ZdChatMessageEntity>,
    viewModel: ZedCraftViewModel
) {
    ZdAssistantContent(
        uiState = uiState,
        chatMessages = chatMessages,
        onSendPrompt = { prompt -> viewModel.sendZdPrompt(prompt) },
        onStopGeneration = { viewModel.stopZdGeneration() },
        onRetryLastPrompt = { viewModel.retryLastZdPrompt() },
        onDismissError = { viewModel.dismissZdError() },
        onClearChat = { viewModel.clearZdChat() },
        onSaveToVault = { title, content ->
            viewModel.saveIdeaToVault(
                category = "ZD Coaching",
                title = title,
                content = content
            )
        }
    )
}

@Composable
fun ZdAssistantContent(
    uiState: ZedCraftUiState,
    chatMessages: List<ZdChatMessageEntity>,
    onSendPrompt: (String) -> Unit,
    onStopGeneration: () -> Unit,
    onRetryLastPrompt: () -> Unit,
    onDismissError: () -> Unit,
    onClearChat: () -> Unit,
    onSaveToVault: (title: String, content: String) -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var inputPrompt by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val quickActionPrompts = remember(uiState.activeChannel) {
        val chName = uiState.activeChannel?.title ?: "my channel"
        listOf(
            "Audit My Channel" to "Perform a 360° growth audit of $chName based on my real video stats, upload frequency, and top performers. Give me 5 prioritized next steps.",
            "Why Did Views Drop?" to "Diagnose why recent uploads on $chName may have fewer views than my channel median. Break down CTR, topic breadth, and retention fixes.",
            "10 Viral Video Ideas" to "Generate 10 high-CTR, low-competition video ideas tailored to $chName with clickable titles (<60 chars) and 3-word thumbnail text.",
            "Rewrite My Title" to "Give me 8 curiosity-driven title variations and matching thumbnail concepts for my next upload on $chName.",
            "First 30s Hook Formula" to "Write 3 high-retention opening 30-second hooks (Pattern Interrupt, Curiosity Gap, Proof-First) for my next video.",
            "Shorts to Long-Form Bridge" to "How do I convert my Shorts viewers into loyal long-form subscribers without hurting my Browse CTR?",
            "Sponsor Rate Card" to "Calculate a realistic sponsor integration rate card and cold-pitch email template for my current view velocity."
        )
    }

    LaunchedEffect(chatMessages.size, uiState.isZdThinking, uiState.streamingZdMessage, uiState.zdError) {
        val totalItems = chatMessages.size +
            (if (uiState.isZdThinking) 1 else 0) +
            (if (!uiState.zdError.isNullOrBlank()) 1 else 0)
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    val submitCurrentPrompt = {
        val toSend = inputPrompt.trim()
        if (toSend.isNotBlank() && !uiState.isZdThinking) {
            inputPrompt = ""
            keyboardController?.hide()
            onSendPrompt(toSend)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ZedDarkBackground)
    ) {
        // ZD Status Header Strip + Quick Action Pills
        Surface(
            color = ZedGlassSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            ZedCyanPrimary.copy(alpha = 0.24f),
                                            ZedBrandKellyGreen.copy(alpha = 0.35f)
                                        )
                                    )
                                )
                                .border(1.dp, ZedCyanPrimary.copy(alpha = 0.45f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "ZD Coach",
                                tint = ZedCyanPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "ZD • YouTube Strategy Coach",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = ZedTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (uiState.isZdThinking) ZedCyanPrimary else ZedEmeraldSuccess
                                        )
                                )
                            }
                            Text(
                                text = when {
                                    uiState.isZdThinking && !uiState.streamingZdMessage.isNullOrBlank() ->
                                        "Streaming channel-aware strategy..."
                                    uiState.isZdThinking ->
                                        "Analyzing channel metrics & crafting answer..."
                                    uiState.activeChannel != null ->
                                        "Ready • ${uiState.activeChannel.title} (${uiState.activeChannel.handle})"
                                    else ->
                                        "Ready • Ask about titles, CTR, hooks & growth"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (uiState.isZdThinking) ZedCyanPrimary else ZedEmeraldSuccess,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = onClearChat,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("zd_clear_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Reset ZD Conversation",
                            tint = ZedTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Quick Action Prompts Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickActionPrompts.forEach { (label, fullPrompt) ->
                        Surface(
                            modifier = Modifier
                                .alpha(if (uiState.isZdThinking) 0.55f else 1f)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(enabled = !uiState.isZdThinking) {
                                    onSendPrompt(fullPrompt)
                                },
                            color = ZedDarkSurfaceElevated,
                            border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.32f)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = ZedCyanPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ZedTextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = ZedCardBorder.copy(alpha = 0.55f))

        // Chat Messages Stream (Scrollable)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(chatMessages, key = { it.id }) { msg ->
                ZdChatBubble(
                    message = msg,
                    onCopy = {
                        val cleanText = if (msg.role == "user") {
                            msg.content
                        } else {
                            stripMarkdownToCleanText(msg.content)
                        }
                        copyMessageToClipboard(context, cleanText)
                    },
                    onSaveToVault = {
                        val cleanBody = stripMarkdownToCleanText(msg.content)
                        val cleanTitle = cleanBody.lineSequence()
                            .firstOrNull { it.isNotBlank() }
                            ?.take(60)
                            ?: "ZD Strategy Note"
                        onSaveToVault(cleanTitle, cleanBody)
                        Toast.makeText(context, "Saved to Vault", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            if (uiState.isZdThinking) {
                item(key = "zd_streaming_or_thinking_item") {
                    val streamingContent = uiState.streamingZdMessage
                    if (!streamingContent.isNullOrBlank()) {
                        ZdStreamingChatBubble(
                            streamingMarkdown = streamingContent,
                            onCopy = {
                                copyMessageToClipboard(context, stripMarkdownToCleanText(streamingContent))
                            }
                        )
                    } else {
                        ZdThinkingIndicatorCard()
                    }
                }
            }

            if (!uiState.zdError.isNullOrBlank()) {
                item(key = "zd_error_card_item") {
                    ZdErrorActionCard(
                        errorMessage = uiState.zdError,
                        isThinking = uiState.isZdThinking,
                        onRetry = onRetryLastPrompt,
                        onDismiss = onDismissError
                    )
                }
            }
        }

        // Fixed Bottom Composer Bar (automatically pushed above keyboard via AppShell imePadding)
        Surface(
            color = ZedGlassSurface,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, ZedCardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = inputPrompt,
                    onValueChange = { inputPrompt = it },
                    placeholder = {
                        Text(
                            text = if (uiState.isZdThinking) {
                                "ZD is responding... type next question"
                            } else {
                                "Ask ZD about titles, hooks, CTR, or growth..."
                            },
                            color = ZedTextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = { submitCurrentPrompt() }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("zd_prompt_input"),
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ZedTextPrimary,
                        unfocusedTextColor = ZedTextPrimary,
                        cursorColor = ZedCyanPrimary,
                        focusedBorderColor = ZedCyanPrimary,
                        unfocusedBorderColor = ZedCardBorder,
                        focusedContainerColor = ZedDarkBackground,
                        unfocusedContainerColor = ZedDarkBackground
                    )
                )
                Spacer(modifier = Modifier.width(10.dp))

                AnimatedContent(
                    targetState = uiState.isZdThinking,
                    transitionSpec = {
                        (fadeIn(tween(180)) + scaleIn(initialScale = 0.86f, animationSpec = tween(180))) togetherWith
                            (fadeOut(tween(140)) + scaleOut(targetScale = 0.86f, animationSpec = tween(140)))
                    },
                    label = "zd_composer_action_button"
                ) { isThinking ->
                    if (isThinking) {
                        IconButton(
                            onClick = onStopGeneration,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(ZedCoralError.copy(alpha = 0.2f))
                                .border(1.5.dp, ZedCoralError, CircleShape)
                                .testTag("zd_stop_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop Generation",
                                tint = ZedCoralError,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    } else {
                        val canSend = inputPrompt.isNotBlank()
                        IconButton(
                            onClick = { submitCurrentPrompt() },
                            enabled = canSend,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(if (canSend) ZedCyanPrimary else ZedDarkSurfaceElevated)
                                .border(
                                    width = 1.dp,
                                    color = if (canSend) ZedCyanPrimary else ZedCardBorder,
                                    shape = CircleShape
                                )
                                .testTag("zd_send_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send to ZD",
                                tint = if (canSend) ZedOnPrimaryDark else ZedTextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZdThinkingIndicatorCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "zd_thinking_dots")
    val dot1 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 540, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )
    val dot2 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 540, delayMillis = 160, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )
    val dot3 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 540, delayMillis = 320, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth(0.88f)
            .testTag("zd_thinking_indicator"),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp),
        colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
        border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(dot1, dot2, dot3).forEach { alphaVal ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .alpha(alphaVal)
                            .clip(CircleShape)
                            .background(ZedCyanPrimary)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "ZD is analyzing your channel & crafting strategy...",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = ZedTextSecondary
            )
        }
    }
}

@Composable
private fun ZdErrorActionCard(
    errorMessage: String,
    isThinking: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val isConfigOrNetwork = errorMessage.contains("key", ignoreCase = true) ||
        errorMessage.contains("internet", ignoreCase = true)
    val accent = if (isConfigOrNetwork) ZedCoralError else ZedAmberWarning

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("zd_friendly_error_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = ZedTextPrimary
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("zd_dismiss_error_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss error",
                        tint = ZedTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onRetry,
                    enabled = !isThinking,
                    modifier = Modifier.testTag("zd_retry_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ZedCyanPrimary,
                        contentColor = ZedOnPrimaryDark
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Retry",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ZdChatBubble(
    message: ZdChatMessageEntity,
    onCopy: () -> Unit,
    onSaveToVault: () -> Unit
) {
    val isUser = message.role == "user"
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.84f else 0.96f),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) ZedDarkSurfaceElevated else ZedGlassSurface
            ),
            border = BorderStroke(
                1.dp,
                if (isUser) ZedCyanPrimary.copy(alpha = 0.45f) else ZedCardBorder
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
                        if (!isUser) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = ZedCyanPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Text(
                            text = if (isUser) "You" else "ZD • Growth Coach",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isUser) ZedPurpleAccent else ZedCyanPrimary
                        )
                    }
                    if (!isUser) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = onCopy)
                                    .testTag("zd_copy_message_button"),
                                color = ZedDarkSurfaceElevated,
                                border = BorderStroke(1.dp, ZedCardBorder),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy Response",
                                        tint = ZedTextSecondary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Copy",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ZedTextSecondary
                                    )
                                }
                            }
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = onSaveToVault)
                                    .testTag("zd_save_message_button"),
                                color = ZedDarkSurfaceElevated,
                                border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.35f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BookmarkAdd,
                                        contentDescription = "Save Response to Vault",
                                        tint = ZedCyanPrimary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Save",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ZedCyanPrimary
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (isUser) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ZedTextPrimary
                    )
                } else {
                    ZdMarkdownMessage(
                        markdown = message.content,
                        isStreaming = false
                    )
                }
            }
        }
    }
}

@Composable
private fun ZdStreamingChatBubble(
    streamingMarkdown: String,
    onCopy: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .testTag("zd_streaming_bubble"),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = 4.dp,
                bottomEnd = 18.dp
            ),
            colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
            border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.55f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(13.dp),
                            strokeWidth = 2.dp,
                            color = ZedCyanPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ZD • Live Strategy",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = ZedCyanPrimary
                        )
                    }
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onCopy),
                        color = ZedDarkSurfaceElevated,
                        border = BorderStroke(1.dp, ZedCardBorder),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Response",
                                tint = ZedTextSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Copy",
                                style = MaterialTheme.typography.labelSmall,
                                color = ZedTextSecondary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                ZdMarkdownMessage(
                    markdown = streamingMarkdown,
                    isStreaming = true
                )
            }
        }
    }
}

private fun copyMessageToClipboard(context: Context, text: String) {
    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cb?.setPrimaryClip(ClipData.newPlainText("ZD Response", text))
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}
