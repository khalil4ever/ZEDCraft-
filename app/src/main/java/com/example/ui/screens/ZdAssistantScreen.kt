package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ai.ZdSkillModule
import com.example.ai.ZdSkillPack
import com.example.ai.ZedCraftAiConfig
import com.example.data.model.ZdChatMessage
import com.example.ui.components.DataBadge
import com.example.ui.components.ErrorBanner
import com.example.ui.components.GlassCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.SegmentedPillBar
import com.example.ui.components.scoreColor
import com.example.ui.theme.ZedCyan
import com.example.ui.theme.ZedSuccess
import com.example.ui.theme.ZedViolet
import com.example.ui.viewmodel.ZedCraftUiState
import com.example.ui.viewmodel.ZedCraftViewModel
import org.json.JSONObject

@Composable
fun ZdAssistantScreen(
    uiState: ZedCraftUiState,
    viewModel: ZedCraftViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        SegmentedPillBar(
            options = listOf(
                "Chat with ZD" to Icons.Default.ChatBubbleOutline,
                "12-Skill Pack" to Icons.Default.Extension
            ),
            selectedIndex = uiState.zdSubTab,
            onSelect = { viewModel.selectZdSubTab(it) }
        )
        Spacer(modifier = Modifier.height(10.dp))

        if (uiState.zdSubTab == 0) {
            ZdChatPane(uiState = uiState, viewModel = viewModel)
        } else {
            ZdSkillPackPane(uiState = uiState, viewModel = viewModel)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ZdChatPane(
    uiState: ZedCraftUiState,
    viewModel: ZedCraftViewModel
) {
    val clipboard = LocalClipboardManager.current

    Column(modifier = Modifier.fillMaxSize()) {
        // ZD Partner Header Card
        GlassCard(accentBorderColor = ZedCyan.copy(alpha = 0.55f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_zd_avatar_1794133253177),
                    contentDescription = "ZD YouTube Growth Partner Avatar",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, ZedCyan, CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "ZD, your YouTube growth partner",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DataBadge(
                            text = "Skill #${uiState.activeZdSkill.number}: ${uiState.activeZdSkill.badge}",
                            isAi = true
                        )
                        DataBadge(
                            text = ZedCraftAiConfig.ZD_MAIN_MODEL,
                            isAi = true,
                            colorOverride = ZedCyan
                        )
                    }
                }
                if (uiState.zdChatMessages.isNotEmpty()) {
                    IconButton(onClick = { viewModel.clearZdConversation() }) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear ZD Conversation",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Skill quick-selector row
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ZdSkillPack.skills.forEach { skill ->
                    val isSelected = skill.id == uiState.activeZdSkill.id
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.clickable { viewModel.selectZdSkill(skill) }
                    ) {
                        Text(
                            text = "${skill.number}. ${skill.badge}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Scrollable chat messages area
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (uiState.zdChatMessages.isEmpty()) {
                item {
                    GlassCard {
                        Text(
                            text = "Active Skill: ${uiState.activeZdSkill.title}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = uiState.activeZdSkill.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Tap an action prompt to start or type your own below:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        uiState.activeZdSkill.examplePrompts.forEach { prompt ->
                            OutlinedButton(
                                onClick = {
                                    viewModel.sendZdPrompt(
                                        promptOverride = prompt,
                                        skillOverride = uiState.activeZdSkill
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = ZedCyan
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = prompt,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            items(uiState.zdChatMessages, key = { it.id.ifBlank { "${it.role}_${it.content.hashCode()}" } }) { msg ->
                ZdMessageBubble(
                    message = msg,
                    onCopy = { text ->
                        clipboard.setText(AnnotatedString(text))
                        viewModel.showToast("Copied ZD output to clipboard!")
                    }
                )
            }

            if (uiState.isZdThinking) {
                item {
                    GlassCard(accentBorderColor = ZedViolet.copy(alpha = 0.6f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.5.dp,
                                color = ZedViolet
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "ZD is analyzing your channel & executing ${uiState.activeZdSkill.badge}...",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Model: ${ZedCraftAiConfig.ZD_MAIN_MODEL} (ThinkingLevel.HIGH)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            uiState.zdError?.let { err ->
                item { ErrorBanner(message = err) }
            }

            val followUps = uiState.zdLatestResponse?.followUpPrompts.orEmpty()
            if (followUps.isNotEmpty() && !uiState.isZdThinking) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Suggested Next Steps:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            followUps.forEach { followUp ->
                                AssistChip(
                                    onClick = { viewModel.sendZdPrompt(promptOverride = followUp) },
                                    label = { Text(followUp, style = MaterialTheme.typography.bodySmall) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Fixed Input Bar at the bottom of ZD Chat
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 10.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.zdInputText,
                    onValueChange = { viewModel.updateZdInput(it) },
                    placeholder = {
                        Text("Ask ZD to score titles, write hooks, diagnose views, or plan videos...")
                    },
                    maxLines = 3,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("zd_chat_input"),
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { viewModel.sendZdPrompt() },
                    enabled = !uiState.isZdThinking && uiState.zdInputText.isNotBlank(),
                    modifier = Modifier
                        .height(50.dp)
                        .testTag("zd_send_button"),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send to ZD"
                    )
                }
            }
        }
    }
}

@Composable
private fun ZdMessageBubble(
    message: ZdChatMessage,
    onCopy: (String) -> Unit
) {
    val isUser = message.role.equals("user", ignoreCase = true)
    val skill = ZdSkillPack.getById(message.skillId)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            color = if (isUser) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
            },
            border = BorderStroke(
                1.dp,
                if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth(if (isUser) 0.88f else 0.98f)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isUser) {
                            Image(
                                painter = painterResource(id = R.drawable.img_zd_avatar_1794133253177),
                                contentDescription = "ZD",
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Text(
                                text = "ZD",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = ZedCyan
                            )
                            DataBadge(text = "AI-generated • ${skill.badge}", isAi = true)
                        } else {
                            Text(
                                text = "You",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (!isUser) {
                        IconButton(
                            onClick = { onCopy(message.content) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy ZD Response",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Rich Tool Output Card when ZD executed a tool
                if (!isUser && message.toolCardJson.isNotBlank()) {
                    val toolObj = runCatching { JSONObject(message.toolCardJson) }.getOrNull()
                    if (toolObj != null) {
                        val badgeText = toolObj.optString("badgeText", "ZD Tool Output")
                        val cardTitle = toolObj.optString("title", "")
                        val score = toolObj.optInt("score", 0)
                        val bullets = toolObj.optString("bullets", "")
                        val copyable = toolObj.optString("copyableText", "")

                        if (cardTitle.isNotBlank() || copyable.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, ZedViolet.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        DataBadge(text = badgeText, isAi = true)
                                        if (score > 0) {
                                            DataBadge(
                                                text = "AI Estimate: $score/100",
                                                isAi = true,
                                                colorOverride = scoreColor(score)
                                            )
                                        }
                                    }
                                    if (cardTitle.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = cardTitle,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (bullets.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "• $bullets",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (copyable.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                                .padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = copyable,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(onClick = { onCopy(copyable) }) {
                                                Icon(
                                                    Icons.Default.ContentCopy,
                                                    contentDescription = "Copy Tool Output",
                                                    tint = ZedCyan
                                                )
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
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ZdSkillPackPane(
    uiState: ZedCraftUiState,
    viewModel: ZedCraftViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(
                title = "ZD Built-In 12-Skill Pack",
                subtitle = "Modular YouTube creator playbook skills with real channel context & tool execution.",
                badgeText = "12 Skills Active",
                isAiBadge = true
            )
        }

        items(ZdSkillPack.skills, key = { it.id }) { skill ->
            ZdSkillCard(
                skill = skill,
                isActive = uiState.activeZdSkill.id == skill.id,
                onSelectSkill = { viewModel.selectZdSkill(skill) },
                onRunPrompt = { prompt ->
                    viewModel.sendZdPrompt(promptOverride = prompt, skillOverride = skill)
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ZdSkillCard(
    skill: ZdSkillModule,
    isActive: Boolean,
    onSelectSkill: () -> Unit,
    onRunPrompt: (String) -> Unit
) {
    GlassCard(
        accentBorderColor = if (isActive) ZedCyan else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${skill.number}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = skill.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    DataBadge(text = skill.badge, isAi = true)
                }
            }

            Button(
                onClick = onSelectSkill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) ZedSuccess.copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    contentColor = if (isActive) ZedSuccess else MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (isActive) "Selected" else "Use Skill", style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = skill.summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap an example prompt to run immediately with ZD:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            skill.examplePrompts.forEach { prompt ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                    modifier = Modifier.clickable { onRunPrompt(prompt) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = ZedCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
