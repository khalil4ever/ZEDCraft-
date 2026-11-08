package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.ZdChatMessageEntity
import com.example.ui.theme.ZedAmberWarning
import com.example.ui.theme.ZedCardBorder
import com.example.ui.theme.ZedCyanPrimary
import com.example.ui.theme.ZedDarkBackground
import com.example.ui.theme.ZedDarkSurfaceElevated
import com.example.ui.theme.ZedEmeraldSuccess
import com.example.ui.theme.ZedGlassSurface
import com.example.ui.theme.ZedPurpleAccent
import com.example.ui.theme.ZedTextMuted
import com.example.ui.theme.ZedTextSecondary
import com.example.ui.viewmodel.ZedCraftUiState
import com.example.ui.viewmodel.ZedCraftViewModel

@Composable
fun ZdAssistantScreen(
    uiState: ZedCraftUiState,
    chatMessages: List<ZdChatMessageEntity>,
    viewModel: ZedCraftViewModel
) {
    val context = LocalContext.current
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

    LaunchedEffect(chatMessages.size, uiState.isZdThinking, uiState.zdError) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ZedDarkBackground)
    ) {
        // ZD Header Strip + Quick Action Pills
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(ZedCyanPrimary.copy(alpha = 0.16f)),
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
                        Column {
                            Text(
                                text = "ZD • YouTube Growth & Strategy Coach",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (uiState.activeChannel != null) {
                                    "Context: ${uiState.activeChannel.title} (${uiState.activeChannel.handle})"
                                } else {
                                    "Ask anything about titles, CTR, retention & growth"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = ZedEmeraldSuccess
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.clearZdChat() },
                        modifier = Modifier.testTag("zd_clear_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Reset ZD Conversation",
                            tint = ZedTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

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
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !uiState.isZdThinking) {
                                    viewModel.sendZdPrompt(fullPrompt)
                                },
                            color = ZedDarkSurfaceElevated,
                            border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
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
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // Chat Messages Stream (Scrollable)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(chatMessages, key = { it.id }) { msg ->
                ZdChatBubble(
                    message = msg,
                    onCopy = {
                        copyMessageToClipboard(context, msg.content)
                    },
                    onSaveToVault = {
                        viewModel.saveIdeaToVault(
                            category = "ZD Coaching",
                            title = msg.content.lineSequence().firstOrNull()?.take(60) ?: "ZD Strategy Note",
                            content = msg.content
                        )
                        Toast.makeText(context, "Saved to Vault", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            if (uiState.isZdThinking) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(0.85f),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
                        border = BorderStroke(1.dp, ZedCyanPrimary.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.5.dp,
                                color = ZedCyanPrimary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "ZD is analyzing your channel & crafting strategy...",
                                style = MaterialTheme.typography.bodySmall,
                                color = ZedTextSecondary
                            )
                        }
                    }
                }
            }

            if (!uiState.zdError.isNullOrBlank()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("zd_friendly_error_card"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = ZedGlassSurface),
                        border = BorderStroke(1.dp, ZedAmberWarning.copy(alpha = 0.55f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
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
                                        .background(ZedAmberWarning.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WifiOff,
                                        contentDescription = null,
                                        tint = ZedAmberWarning,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "ZD is having trouble connecting. Please try again.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Button(
                                onClick = { viewModel.retryLastZdPrompt() },
                                enabled = !uiState.isZdThinking,
                                modifier = Modifier.testTag("zd_retry_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ZedCyanPrimary,
                                    contentColor = ZedDarkBackground
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
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
        }

        // Fixed Bottom Composer Bar
        Surface(
            color = ZedGlassSurface,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, ZedCardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputPrompt,
                    onValueChange = { inputPrompt = it },
                    placeholder = {
                        Text(
                            text = "Ask ZD about titles, hooks, CTR, or growth...",
                            color = ZedTextMuted
                        )
                    },
                    maxLines = 4,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("zd_prompt_input"),
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ZedCyanPrimary,
                        unfocusedBorderColor = ZedCardBorder,
                        focusedContainerColor = ZedDarkBackground,
                        unfocusedContainerColor = ZedDarkBackground
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val toSend = inputPrompt.trim()
                        if (toSend.isNotBlank() && !uiState.isZdThinking) {
                            inputPrompt = ""
                            viewModel.sendZdPrompt(toSend)
                        }
                    },
                    enabled = inputPrompt.isNotBlank() && !uiState.isZdThinking,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (inputPrompt.isNotBlank() && !uiState.isZdThinking) {
                                ZedCyanPrimary
                            } else {
                                ZedCardBorder
                            }
                        )
                        .testTag("zd_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send to ZD",
                        tint = if (inputPrompt.isNotBlank() && !uiState.isZdThinking) {
                            ZedDarkBackground
                        } else {
                            ZedTextMuted
                        }
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
            modifier = Modifier.fillMaxWidth(if (isUser) 0.85f else 0.96f),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) ZedPurpleAccent.copy(alpha = 0.28f) else ZedGlassSurface
            ),
            border = BorderStroke(
                1.dp,
                if (isUser) ZedPurpleAccent.copy(alpha = 0.55f) else ZedCardBorder
            )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) "You" else "ZD • Growth Coach",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isUser) ZedPurpleAccent else ZedCyanPrimary
                    )
                    if (!isUser) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = onCopy,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Response",
                                    tint = ZedTextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            IconButton(
                                onClick = onSaveToVault,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BookmarkAdd,
                                    contentDescription = "Save Response to Vault",
                                    tint = ZedCyanPrimary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
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
