package com.example

import com.example.ai.ThumbnailDesignerSkill
import com.example.ai.ZdAiException
import com.example.ai.ZedCraftAiConfig
import com.example.ai.ZedCraftAiService
import com.example.ui.components.ZdMarkdownBlock
import com.example.ui.components.parseZdMarkdownBlocks
import com.example.ui.components.sanitizeMarkdownForDisplay
import com.example.ui.components.stripMarkdownToCleanText
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.TimeoutCancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ZdAiAndMarkdownTest {

    @Test
    fun `ai config routes text to gemini-4-hydrogen and thumbnails to gemini-3-pro-image`() {
        assertEquals("gemini-4-hydrogen", ZedCraftAiConfig.ZD_MAIN_MODEL)
        assertEquals(ZedCraftAiConfig.ZD_MAIN_MODEL, ZedCraftAiConfig.FAST_MODEL)
        assertEquals("gemini-4-hydrogen", ZedCraftAiConfig.DIRECT_MODEL_CHAIN.first())
        assertEquals("gemini-3-pro-image", ZedCraftAiConfig.THUMBNAIL_IMAGE_MODEL)
        assertTrue(ZedCraftAiConfig.THUMBNAIL_IMAGE_FALLBACK_CHAIN.contains("gemini-3-pro-image"))
        assertEquals("LOW", ZedCraftAiConfig.HYDROGEN_DEFAULT_THINKING_LEVEL)
        assertEquals(1.0, ZedCraftAiConfig.DEFAULT_TEMPERATURE, 0.001)
    }

    @Test
    fun `thumbnail designer skill preserves exact system instruction and builds 3 distinct 16x9 prompts`() {
        val sys = ThumbnailDesignerSkill.SYSTEM_INSTRUCTION
        assertTrue(sys.contains("You are a professional YouTube thumbnail graphic designer"))
        assertTrue(sys.contains("Maximum 3 to 5 words of text"))
        assertTrue(sys.contains("bottom-right corner clear, where the YouTube timestamp appears"))
        assertEquals(3, ThumbnailDesignerSkill.THREE_THUMBNAIL_VARIATIONS.size)

        val prompt = ThumbnailDesignerSkill.buildThumbnailGenerationPrompt(
            videoTitleOrTopic = "I Built an AI Phone in 24 Hours",
            nicheOrStyle = "Tech & Gadgets",
            overlayText = "IT ACTUALLY WORKS!",
            hasReferenceImage = true,
            variation = ThumbnailDesignerSkill.THREE_THUMBNAIL_VARIATIONS[0]
        )
        assertTrue(prompt.contains("16:9"))
        assertTrue(prompt.contains("I Built an AI Phone in 24 Hours"))
        assertTrue(prompt.contains("IT ACTUALLY WORKS!"))
        assertTrue(prompt.contains("Reference Image Provided"))
    }

    @Test
    fun `ai service validates configured api key and rejects placeholder keys`() {
        val missingKeyService = ZedCraftAiService { "" }
        assertFalse(missingKeyService.hasValidDirectApiKey())

        val placeholderKeyService = ZedCraftAiService { "YOUR_GEMINI_API_KEY" }
        assertFalse(placeholderKeyService.hasValidDirectApiKey())

        val validKeyService = ZedCraftAiService { "AIzaSyExampleValidKeyForTesting123" }
        assertTrue(validKeyService.hasValidDirectApiKey())
    }

    @Test
    fun `extractFriendlyError surfaces actionable messages for network timeout and config errors`() {
        val keyErr = ZdAiException("Gemini API key was rejected (HTTP 403). Verify GEMINI_API_KEY.", isConfigError = true)
        assertEquals(
            "Gemini API key was rejected (HTTP 403). Verify GEMINI_API_KEY.",
            ZedCraftAiService.extractFriendlyError(keyErr)
        )

        val offlineMsg = ZedCraftAiService.extractFriendlyError(UnknownHostException("generativelanguage.googleapis.com"))
        assertTrue(offlineMsg.contains("internet", ignoreCase = true))

        val timeoutMsg = ZedCraftAiService.extractFriendlyError(SocketTimeoutException("Read timed out"))
        assertTrue(timeoutMsg.contains("timed out", ignoreCase = true))

        val unknownMsg = ZedCraftAiService.extractFriendlyError(IllegalStateException("NullPointerException at foo"))
        assertEquals(ZedCraftAiService.FRIENDLY_ZD_ERROR, unknownMsg)
    }

    @Test
    fun `markdown sanitizer removes stray markers during streaming and copy cleanup is clean`() {
        val streamingRaw = "### Hook Ideas\n- **Pattern Interrupt\n## "
        val sanitizedStreaming = sanitizeMarkdownForDisplay(streamingRaw, isStreaming = true)
        assertFalse(sanitizedStreaming.endsWith("##"))
        assertTrue(sanitizedStreaming.contains("**Pattern Interrupt**"))

        val cleanCopy = stripMarkdownToCleanText("### Strategy\n- **CTR Boost**: Use `3 words` on [YouTube](https://youtube.com)")
        assertFalse(cleanCopy.contains("###"))
        assertFalse(cleanCopy.contains("**"))
        assertFalse(cleanCopy.contains("`"))
        assertTrue(cleanCopy.contains("Strategy"))
        assertTrue(cleanCopy.contains("CTR Boost: Use 3 words on YouTube"))
    }

    @Test
    fun `markdown block parser parses headings lists code blocks and tables`() {
        val md = """
            ### Title Blueprint
            
            - **Option 1**: Hook curiosity
            - **Option 2**: Show proof
            
            ```kotlin
            val score = 95
            ```
            
            | Metric | Target |
            | --- | --- |
            | CTR | 8% |
        """.trimIndent()

        val blocks = parseZdMarkdownBlocks(md, isStreaming = false)
        assertTrue(blocks.any { it is ZdMarkdownBlock.Heading && it.level == 3 && it.text == "Title Blueprint" })
        assertTrue(blocks.any { it is ZdMarkdownBlock.ListGroup && it.items.size == 2 })
        assertTrue(blocks.any { it is ZdMarkdownBlock.CodeBlock && it.language == "kotlin" && it.code.contains("val score = 95") })
        assertTrue(blocks.any { it is ZdMarkdownBlock.TableBlock && it.headers == listOf("Metric", "Target") })
    }
}
