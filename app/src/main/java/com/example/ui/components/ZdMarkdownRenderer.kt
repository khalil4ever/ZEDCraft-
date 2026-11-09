package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout. IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ZedCardBorder
import com.example.ui.theme.ZedCyanPrimary
import com.example.ui.theme.ZedDarkBackground
import com.example.ui.theme.ZedDarkSurfaceElevated
import com.example.ui.theme.ZedTextMuted
import com.example.ui.theme.ZedTextSecondary

private const val URL_TAG = "URL_ANNOTATION"

/**
 * Represents a parsed GFM Markdown block for ZD Assistant messages.
 */
sealed class ZdMarkdownBlock {
    data class Heading(val level: Int, val text: String) : ZdMarkdownBlock()
    data class Paragraph(val text: String) : ZdMarkdownBlock()
    data class ListGroup(val items: List<ListItem>) : ZdMarkdownBlock()
    data class CodeBlock(val language: String, val code: String) : ZdMarkdownBlock()
    data class TableBlock(val headers: List<String>, val rows: List<List<String>>) : ZdMarkdownBlock()
    data class Blockquote(val text: String) : ZdMarkdownBlock()
    data object Divider : ZdMarkdownBlock()

    data class ListItem(
        val isOrdered: Boolean,
        val orderNumber: Int,
        val indentLevel: Int,
        val text: String
    )
}

/**
 * Safety cleanup & streaming stabilizer so stray `**` or `##` never show on screen,
 * even while a response is actively streaming token-by-token.
 */
fun sanitizeMarkdownForDisplay(raw: String, isStreaming: Boolean = false): String {
    if (raw.isBlank()) return ""

    var text = raw.replace("\r\n", "\n")

    // 1. If the entire response was wrapped in a top-level ```markdown or ``` fence, unwrap it
    val trimmed = text.trim()
    if (trimmed.startsWith("```markdown", ignoreCase = true) && trimmed.endsWith("```") && trimmed.length > 14) {
        val inner = trimmed.removePrefix("```markdown").removePrefix("```MARKDOWN").removeSuffix("```").trim()
        if (!inner.contains("```")) {
            text = inner
        }
    }

    // 2. While streaming, hide trailing incomplete heading/bold marker tokens (e.g. a lone "#" or "**" at the very end)
    if (isStreaming) {
        text = text.replace(Regex("(?m)^\\s*#{1,6}\\s*$"), "")
        text = text.replace(Regex("(\\*\\*|__|~~|`)+$"), "")
    }

    // 3. Remove empty heading lines like "## " or "###" with no title text
    text = text.replace(Regex("(?m)^\\s*#{1,6}\\s*$"), "")

    // 4. Auto-balance unclosed bold `**` per line so streaming or malformed markdown never shows raw `**`
    val lines = text.split("\n")
    var insideCodeFence = false
    val cleanedLines = lines.map { line ->
        val fenceTrim = line.trimStart()
        if (fenceTrim.startsWith("```")) {
            insideCodeFence = !insideCodeFence
            return@map line
        }
        if (insideCodeFence) return@map line

        var processed = line
        // Strip stray heading hashes that might appear mid-line after bullet markers e.g. "- ### Title" -> "- **Title**"
        processed = processed.replace(Regex("^(\\s*(?:[-*+]|\\d+\\.)\\s+)#{1,6}\\s+(.*)$")) { match ->
            "${match.groupValues[1]}**${match.groupValues[2].trim()}**"
        }

        // Count `**` occurrences on this line
        val boldMatches = Regex("\\*\\*").findAll(processed).count()
        if (boldMatches % 2 != 0) {
            // If `**` is at the end of the line (stray closing marker or just-opened streaming marker), strip it;
            // otherwise close it at the end of the line so the bold text renders cleanly during streaming.
            val lastIdx = processed.lastIndexOf("**")
            val afterLast = processed.substring(lastIdx + 2).trim()
            processed = if (afterLast.isEmpty()) {
                processed.removeRange(lastIdx, lastIdx + 2)
            } else {
                "$processed**"
            }
        }

        // Remove any empty `****` artifacts
        processed = processed.replace("****", "")
        processed
    }

    var result = cleanedLines.joinToString("\n")
    // If streaming inside an unclosed code fence, close it virtually so it renders as a code block immediately
    if (insideCodeFence) {
        result += "\n```"
    }
    return result.trim()
}

/**
 * Converts markdown to clean, readable plain text for the "Copy" button so users never get raw `**` or `###`.
 */
fun stripMarkdownToCleanText(raw: String): String {
    val sanitized = sanitizeMarkdownForDisplay(raw, isStreaming = false)
    val lines = sanitized.split("\n")
    var insideCode = false
    val out = mutableListOf<String>()

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) {
            insideCode = !insideCode
            continue
        }
        if (insideCode) {
            out.add(line)
            continue
        }
        // Skip GFM table separator rows like |---|---|
        if (trimmed.matches(Regex("^\\|?[\\s:\\-|]+\\|?$")) && trimmed.contains("-")) {
            continue
        }

        var clean = line
        // Headings: ### Title -> Title
        clean = clean.replace(Regex("^\\s*#{1,6}\\s+"), "")
        // Blockquotes: > quote -> quote
        clean = clean.replace(Regex("^\\s*>\\s?"), "")
        // Bullets: - item or * item -> • item
        clean = clean.replace(Regex("^(\\s*)[-*+]\\s+"), "$1• ")
        // Links: [label](url) -> label (url)
        clean = clean.replace(Regex("\\[([^\\]]+)]\\(([^)]+)\\)"), "$1 ($2)")
        // Bold / Italic / Strikethrough / Inline code
        clean = clean.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        clean = clean.replace(Regex("__([^_]+)__"), "$1")
        clean = clean.replace(Regex("~~([^~]+)~~"), "$1")
        clean = clean.replace(Regex("`([^`]+)`"), "$1")
        clean = clean.replace(Regex("(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)"), "$1")
        // Stray leftover markers
        clean = clean.replace("**", "").replace("##", "")

        out.add(clean)
    }

    return out.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
}

/**
 * Parses sanitized GFM markdown into structured [ZdMarkdownBlock] elements.
 */
fun parseZdMarkdownBlocks(rawMarkdown: String, isStreaming: Boolean = false): List<ZdMarkdownBlock> {
    val cleaned = sanitizeMarkdownForDisplay(rawMarkdown, isStreaming = isStreaming)
    if (cleaned.isBlank()) return emptyList()

    val lines = cleaned.split("\n")
    val blocks = mutableListOf<ZdMarkdownBlock>()
    var i = 0

    while (i < lines.size) {
        val rawLine = lines[i]
        val trimmed = rawLine.trim()

        // 1. Blank line
        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // 2. Fenced Code Block (```lang ... ```)
        if (trimmed.startsWith("```")) {
            val lang = trimmed.removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            if (i < lines.size && lines[i].trim().startsWith("```")) {
                i++
            }
            blocks.add(
                ZdMarkdownBlock.CodeBlock(
                    language = lang,
                    code = codeLines.joinToString("\n")
                )
            )
            continue
        }

        // 3. Horizontal Rule (---, ***, ___)
        if (trimmed.matches(Regex("^([-*_])\\s*\\1\\s*\\1[-*_\\s]*$"))) {
            blocks.add(ZdMarkdownBlock.Divider)
            i++
            continue
        }

        // 4. Heading (#, ##, ###, ####)
        val headingMatch = Regex("^(#{1,6})\\s+(.+)$").find(trimmed)
        if (headingMatch != null) {
            val level = headingMatch.groupValues[1].length.coerceIn(1, 4)
            val titleText = headingMatch.groupValues[2].trim().removeSuffix("#").trim()
            if (titleText.isNotEmpty()) {
                blocks.add(ZdMarkdownBlock.Heading(level = level, text = titleText))
            }
            i++
            continue
        }

        // 5. GFM Table (| Col 1 | Col 2 |)
        if (trimmed.startsWith("|") && trimmed.count { it == '|' } >= 2) {
            val tableLines = mutableListOf<String>()
            while (i < lines.size) {
                val tLine = lines[i].trim()
                if (tLine.startsWith("|") || (tLine.contains("|") && tLine.count { it == '|' } >= 2)) {
                    tableLines.add(tLine)
                    i++
                } else {
                    break
                }
            }
            if (tableLines.isNotEmpty()) {
                val parsedRows = tableLines
                    .filterNot { row ->
                        // Filter out separator line like | --- | :---: |
                        val stripped = row.replace("|", "").replace(":", "").replace("-", "").trim()
                        stripped.isEmpty() && row.contains("-")
                    }
                    .map { row ->
                        row.trim().removePrefix("|").removeSuffix("|")
                            .split("|")
                            .map { it.trim() }
                    }
                if (parsedRows.isNotEmpty()) {
                    val headers = parsedRows.first()
                    val dataRows = if (parsedRows.size > 1) parsedRows.drop(1) else emptyList()
                    blocks.add(ZdMarkdownBlock.TableBlock(headers = headers, rows = dataRows))
                    continue
                }
            }
        }

        // 6. Bullet or Numbered List (- , * , + , 1. , 2) )
        val listRegex = Regex("^(\\s*)(?:([-*+•])|(\\d+)[.)])\\s+(.+)$")
        if (listRegex.matches(rawLine)) {
            val items = mutableListOf<ZdMarkdownBlock.ListItem>()
            var autoOrderCounter = 1
            while (i < lines.size) {
                val currentLine = lines[i]
                val match = listRegex.find(currentLine)
                if (match != null) {
                    val leadingSpaces = match.groupValues[1].length
                    val indentLevel = (leadingSpaces / 2).coerceIn(0, 3)
                    val isOrdered = match.groupValues[3].isNotEmpty()
                    val explicitNumber = match.groupValues[3].toIntOrNull() ?: autoOrderCounter
                    if (isOrdered) {
                        autoOrderCounter = explicitNumber + 1
                    } else {
                        autoOrderCounter = 1
                    }
                    val itemText = match.groupValues[4].trim()
                    items.add(
                        ZdMarkdownBlock.ListItem(
                            isOrdered = isOrdered,
                            orderNumber = explicitNumber,
                            indentLevel = indentLevel,
                            text = itemText
                        )
                    )
                    i++
                } else if (currentLine.isNotBlank() &&
                    currentLine.startsWith("  ") &&
                    items.isNotEmpty() &&
                    !currentLine.trim().startsWith("#") &&
                    !currentLine.trim().startsWith("```")
                ) {
                    // Continuation line of previous list item
                    val last = items.removeAt(items.lastIndex)
                    items.add(last.copy(text = "${last.text} ${currentLine.trim()}"))
                    i++
                } else {
                    break
                }
            }
            if (items.isNotEmpty()) {
                blocks.add(ZdMarkdownBlock.ListGroup(items = items))
            }
            continue
        }

        // 7. Blockquote (> text)
        if (trimmed.startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                quoteLines.add(lines[i].trim().removePrefix(">").trim())
                i++
            }
            val quoteText = quoteLines.joinToString("\n").trim()
            if (quoteText.isNotEmpty()) {
                blocks.add(ZdMarkdownBlock.Blockquote(text = quoteText))
            }
            continue
        }

        // 8. Paragraph (accumulate consecutive regular lines; 12px spacing between paragraphs)
        val paragraphLines = mutableListOf<String>()
        while (i < lines.size) {
            val pLine = lines[i]
            val pTrim = pLine.trim()
            if (pTrim.isEmpty() ||
                pTrim.startsWith("```") ||
                pTrim.startsWith("#") ||
                pTrim.startsWith(">") ||
                (pTrim.startsWith("|") && pTrim.count { it == '|' } >= 2) ||
                pTrim.matches(Regex("^([-*_])\\s*\\1\\s*\\1[-*_\\s]*$")) ||
                listRegex.matches(pLine)
            ) {
                break
            }
            paragraphLines.add(pTrim)
            i++
        }
        val paragraphText = paragraphLines.joinToString(" ").trim()
        if (paragraphText.isNotEmpty()) {
            blocks.add(ZdMarkdownBlock.Paragraph(text = paragraphText))
        }
    }

    return blocks
}

/**
 * Renders an AI Markdown message in ZEDCraft's dark glassmorphism theme:
 * - Bold text: White, FontWeight.SemiBold
 * - Bullet & numbered lists: proper indentation, 8.dp spacing between items, cyan bullet markers
 * - Headings (#, ##, ###): clear size hierarchy with spacing above and below
 * - Paragraphs: 1.6x line height, 12.dp spacing between them
 * - Inline code: subtle dark pill background with a cyan tint
 * - Code blocks: dark background, rounded corners, horizontal scroll, Copy button
 * - Tables: bordered, with scrollable overflow on mobile
 * - Links: cyan, opens in a new browser tab
 */
@Composable
fun ZdMarkdownMessage(
    markdown: String,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val blocks = remember(markdown, isStreaming) {
        parseZdMarkdownBlocks(markdown, isStreaming = isStreaming)
    }

    val bodyFontSize = 14.5.sp
    val bodyLineHeight = 23.2.sp // 1.6x line height
    val bodyTextStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = bodyFontSize,
        lineHeight = bodyLineHeight,
        color = com.example.ui.theme.ZedTextPrimary
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp) // 12px spacing between blocks/paragraphs
    ) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is ZdMarkdownBlock.Heading -> {
                    val topSpace: Dp = if (index == 0) 0.dp else when (block.level) {
                        1 -> 8.dp
                        2 -> 6.dp
                        else -> 4.dp
                    }
                    val fontSize = when (block.level) {
                        1 -> 19.sp
                        2 -> 17.sp
                        3 -> 15.5.sp
                        else -> 14.5.sp
                    }
                    val lineHeight = when (block.level) {
                        1 -> 26.sp
                        2 -> 24.sp
                        3 -> 22.sp
                        else -> 21.sp
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = topSpace, bottom = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (block.level <= 3) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(if (block.level == 1) 18.dp else 15.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(ZedCyanPrimary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            ZdInlineRichText(
                                text = block.text,
                                baseStyle = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = fontSize,
                                    lineHeight = lineHeight,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                ),
                                onLinkClick = { url -> openExternalLink(context, url) }
                            )
                        }
                    }
                }

                is ZdMarkdownBlock.Paragraph -> {
                    ZdInlineRichText(
                        text = block.text,
                        baseStyle = bodyTextStyle,
                        onLinkClick = { url -> openExternalLink(context, url) }
                    )
                }

                is ZdMarkdownBlock.ListGroup -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp) // 8px spacing between list items
                    ) {
                        block.items.forEach { item ->
                            val indentDp = (item.indentLevel * 14).dp
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = indentDp),
                                verticalAlignment = Alignment.Top
                            ) {
                                if (item.isOrdered) {
                                    Text(
                                        text = "${item.orderNumber}.",
                                        style = bodyTextStyle.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = ZedCyanPrimary
                                        ),
                                        modifier = Modifier
                                            .widthIn(min = 22.dp)
                                            .padding(end = 6.dp)
                                    )
                                } else {
                                    Text(
                                        text = "•",
                                        style = bodyTextStyle.copy(
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ZedCyanPrimary
                                        ),
                                        modifier = Modifier
                                            .width(18.dp)
                                    )
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    ZdInlineRichText(
                                        text = item.text,
                                        baseStyle = bodyTextStyle,
                                        onLinkClick = { url -> openExternalLink(context, url) }
                                    )
                                }
                            }
                        }
                    }
                }

                is ZdMarkdownBlock.CodeBlock -> {
                    ZdCodeBlockCard(
                        language = block.language,
                        code = block.code,
                        context = context
                    )
                }

                is ZdMarkdownBlock.TableBlock -> {
                    ZdMarkdownTable(
                        headers = block.headers,
                        rows = block.rows,
                        context = context
                    )
                }

                is ZdMarkdownBlock.Blockquote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(ZedDarkSurfaceElevated.copy(alpha = 0.7f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(24.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(ZedCyanPrimary)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        ZdInlineRichText(
                            text = block.text,
                            baseStyle = bodyTextStyle.copy(
                                fontStyle = FontStyle.Italic,
                                color = Color.White
                            ),
                            onLinkClick = { url -> openExternalLink(context, url) }
                        )
                    }
                }

                ZdMarkdownBlock.Divider -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 1.dp,
                        color = ZedCardBorder
                    )
                }
            }
        }
    }
}

@Composable
private fun ZdCodeBlockCard(
    language: String,
    code: String,
    context: Context
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = com.example.ui.theme.ZedBgPrimary,
        border = BorderStroke(1.dp, ZedCardBorder)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header bar with language badge + Copy button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(com.example.ui.theme.ZedBgSecondary)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifBlank { "code" }.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = ZedCyanPrimary
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cb?.setPrimaryClip(ClipData.newPlainText("Code Block", code))
                            Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Code",
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

            HorizontalDivider(thickness = 1.dp, color = ZedCardBorder.copy(alpha = 0.6f))

            // Horizontally scrollable code body
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = code,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = com.example.ui.theme.ZedTextPrimary
                    )
                )
            }
        }
    }
}

@Composable
private fun ZdMarkdownTable(
    headers: List<String>,
    rows: List<List<String>>,
    context: Context
) {
    val colCount = maxOf(headers.size, rows.maxOfOrNull { it.size } ?: 0)
    if (colCount == 0) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = com.example.ui.theme.ZedBgPrimary,
        border = BorderStroke(1.dp, ZedCardBorder)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            Column {
                // Header Row
                Row(
                    modifier = Modifier
                        .background(ZedDarkSurfaceElevated)
                        .height(IntrinsicSize.Min)
                ) {
                    for (c in 0 until colCount) {
                        val cellText = headers.getOrElse(c) { "" }
                        Box(
                            modifier = Modifier
                                .widthIn(min = 110.dp, max = 240.dp)
                                .fillMaxHeight()
                                .border(0.5.dp, ZedCardBorder)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            ZdInlineRichText(
                                text = cellText,
                                baseStyle = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    lineHeight = 18.sp
                                ),
                                onLinkClick = { url -> openExternalLink(context, url) }
                            )
                        }
                    }
                }

                // Data Rows
                rows.forEachIndexed { rIdx, rowCells ->
                    val rowBg = if (rIdx % 2 == 0) com.example.ui.theme.ZedBgPrimary else com.example.ui.theme.ZedBgSecondary
                    Row(
                        modifier = Modifier
                            .background(rowBg)
                            .height(IntrinsicSize.Min)
                    ) {
                        for (c in 0 until colCount) {
                            val cellText = rowCells.getOrElse(c) { "" }
                            Box(
                                modifier = Modifier
                                    .widthIn(min = 110.dp, max = 240.dp)
                                    .fillMaxHeight()
                                    .border(0.5.dp, ZedCardBorder.copy(alpha = 0.65f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                ZdInlineRichText(
                                    text = cellText,
                                    baseStyle = MaterialTheme.typography.bodySmall.copy(
                                        color = com.example.ui.theme.ZedTextPrimary,
                                        lineHeight = 18.sp
                                    ),
                                    onLinkClick = { url -> openExternalLink(context, url) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZdInlineRichText(
    text: String,
    baseStyle: TextStyle,
    onLinkClick: (String) -> Unit
) {
    val annotated = remember(text) {
        buildInlineMarkdownAnnotatedString(text)
    }

    val hasLinks = remember(annotated) {
        annotated.getStringAnnotations(tag = URL_TAG, start = 0, end = annotated.length).isNotEmpty()
    }

    if (hasLinks) {
        @Suppress("DEPRECATION")
        ClickableText(
            text = annotated,
            style = baseStyle,
            onClick = { offset ->
                annotated.getStringAnnotations(tag = URL_TAG, start = offset, end = offset)
                    .firstOrNull()?.let { annotation ->
                        onLinkClick(annotation.item)
                    }
            }
        )
    } else {
        Text(
            text = annotated,
            style = baseStyle
        )
    }
}

/**
 * Parses inline markdown tokens:
 * - `` `code` `` -> dark pill background with cyan tint
 * - `[title](url)` or bare `https://...` -> cyan clickable link
 * - `**bold**` or `__bold__` -> White, FontWeight.SemiBold
 * - `~~strikethrough~~` -> LineThrough
 * - `*italic*` -> FontStyle.Italic
 * And strips any stray leftover `**` or `##` so raw markers never appear on screen.
 */
fun buildInlineMarkdownAnnotatedString(rawText: String): AnnotatedString {
    // Safety cleanup for stray orphan markers
    val cleanedInput = rawText
        .replace(Regex("^#{1,6}\\s+"), "")
        .replace("****", "")

    val tokenRegex = Regex(
        pattern = "(`[^`]+`)|(\\[([^\\]]+)]\\((https?://[^)\\s]+)\\))|(https?://[^\\s)]+)|(\\*\\*([^*]+)\\*\\*)|(__([^_]+)__)|(~~([^~]+)~~)|((?<!\\*)\\*([^*\\n]+)\\*(?!\\*))"
    )

    return buildAnnotatedString {
        var lastIndex = 0
        for (match in tokenRegex.findAll(cleanedInput)) {
            if (match.range.first > lastIndex) {
                val plainSegment = cleanedInput.substring(lastIndex, match.range.first)
                    .replace("**", "")
                append(plainSegment)
            }

            val full = match.value
            when {
                // 1. Inline code: `code` -> subtle dark pill background with a cyan tint
                match.groups[1] != null -> {
                    val codeContent = full.removeSurrounding("`")
                    withStyle(
                        SpanStyle(
                            color = ZedCyanPrimary,
                            background = com.example.ui.theme.ZedDarkSurfaceElevated,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    ) {
                        append(" $codeContent ")
                    }
                }

                // 2. Markdown Link: [label](url) -> cyan, opens in new tab
                match.groups[2] != null -> {
                    val label = match.groups[3]?.value.orEmpty()
                    val url = match.groups[4]?.value.orEmpty()
                    pushStringAnnotation(tag = URL_TAG, annotation = url)
                    withStyle(
                        SpanStyle(
                            color = ZedCyanPrimary,
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
                        append(label)
                    }
                    pop()
                }

                // 3. Bare URL: https://... -> cyan, opens in new tab
                match.groups[5] != null -> {
                    val url = match.groups[5]?.value.orEmpty()
                    pushStringAnnotation(tag = URL_TAG, annotation = url)
                    withStyle(
                        SpanStyle(
                            color = ZedCyanPrimary,
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
                        append(url)
                    }
                    pop()
                }

                // 4. Bold (**text**) -> white, font-semibold
                match.groups[6] != null -> {
                    val boldText = match.groups[7]?.value.orEmpty()
                    withStyle(
                        SpanStyle(
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    ) {
                        append(boldText)
                    }
                }

                // 5. Bold (__text__) -> white, font-semibold
                match.groups[8] != null -> {
                    val boldText = match.groups[9]?.value.orEmpty()
                    withStyle(
                        SpanStyle(
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    ) {
                        append(boldText)
                    }
                }

                // 6. Strikethrough (~~text~~)
                match.groups[10] != null -> {
                    val strikeText = match.groups[11]?.value.orEmpty()
                    withStyle(
                        SpanStyle(
                            color = ZedTextSecondary,
                            textDecoration = TextDecoration.LineThrough
                        )
                    ) {
                        append(strikeText)
                    }
                }

                // 7. Italic (*text*)
                match.groups[12] != null -> {
                    val italicText = match.groups[13]?.value.orEmpty()
                    withStyle(
                        SpanStyle(
                            color = Color.White,
                            fontStyle = FontStyle.Italic
                        )
                    ) {
                        append(italicText)
                    }
                }
            }

            lastIndex = match.range.last + 1
        }

        if (lastIndex < cleanedInput.length) {
            val remaining = cleanedInput.substring(lastIndex)
                .replace("**", "")
            append(remaining)
        }
    }
}

private fun openExternalLink(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
