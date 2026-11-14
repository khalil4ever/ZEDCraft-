package com.example.ai

/**
 * Reusable "Thumbnail Designer" skill for ZEDCraft.
 *
 * Saved as its own standalone file so the prompt and design rules can be edited
 * or extended in one place at any time. Also mirrored in
 * `app/src/main/assets/thumbnail_designer_skill.md`.
 */
object ThumbnailDesignerSkill {

    const val SKILL_ID = "thumbnail_designer"
    const val SKILL_NAME = "Thumbnail Designer"

    /**
     * Exact system instruction for Nano Banana Pro (`gemini-3-pro-image`) and
     * ZEDCraft's YouTube Thumbnail Generation pipeline.
     */
    val SYSTEM_INSTRUCTION: String = """
You are a professional YouTube thumbnail graphic designer with years of experience making high-CTR thumbnails for top creators. Every thumbnail you make must be clickable, high quality, and look professionally designed, never obviously AI-generated.

Design rules:
- One clear focal point (usually a face with a strong, readable emotion, or one bold subject).
- Maximum 3 to 5 words of text. The text must be huge, bold, high contrast, and readable on a phone screen at small size.
- Simple, uncluttered composition with strong contrast between subject and background.
- Bold, limited color palette, using complementary colors so the subject pops.
- Professional lighting with a subtle rim light or glow to separate the subject from the background.
- Clean edges and realistic detail. Avoid warped hands, garbled text, extra fingers, plastic skin, oversized glossy gradients, and generic "AI art" backgrounds.
- Curiosity or emotion that makes someone want to click, without misleading clickbait.
- Layout that leaves the bottom-right corner clear, where the YouTube timestamp appears.
- Match the style to the video's niche (gaming, tech, vlog, education, business, etc.).
    """.trimIndent()

    /**
     * Three distinct high-CTR creative angles so every request generates 3 meaningfully
     * different thumbnail options for the creator to compare and score.
     */
    data class ThumbnailCreativeVariation(
        val optionNumber: Int,
        val angleLabel: String,
        val compositionDirective: String
    )

    val THREE_THUMBNAIL_VARIATIONS: List<ThumbnailCreativeVariation> = listOf(
        ThumbnailCreativeVariation(
            optionNumber = 1,
            angleLabel = "Option 1 • Expressive Close-Up & Curiosity Hook",
            compositionDirective = "Rule-of-thirds close-up on an expressive human face or hero subject on the left with dramatic cyan/lime rim lighting, high-contrast dark background, and huge 3-to-5-word bold impact text in the upper-right. Keep the bottom-right timestamp zone completely clear."
        ),
        ThumbnailCreativeVariation(
            optionNumber = 2,
            angleLabel = "Option 2 • Bold Split Contrast / Before vs After",
            compositionDirective = "High-contrast split or transformation composition with complementary color contrast separating the problem state from the breakthrough result, a single crisp hero focal subject in the center-left, and huge bold 3-to-5-word text at the top-left. Keep the bottom-right timestamp zone completely clear."
        ),
        ThumbnailCreativeVariation(
            optionNumber = 3,
            angleLabel = "Option 3 • Hero Object & Minimalist Studio Spotlight",
            compositionDirective = "Ultra-clean studio spotlight on one iconic hero object or dramatic visual proof with a glowing rim light, deep uncluttered background, and massive 3-to-5-word high-contrast typography. Keep the bottom-right timestamp zone completely clear."
        )
    )

    /**
     * Builds the full image-generation prompt for a specific variation option (1..3),
     * combining the Thumbnail Designer skill rules with the user's video title/topic,
     * optional style preference, optional 3–5 word text, and optional reference image note.
     */
    fun buildThumbnailGenerationPrompt(
        videoTitleOrTopic: String,
        nicheOrStyle: String,
        overlayText: String,
        hasReferenceImage: Boolean,
        variation: ThumbnailCreativeVariation
    ): String {
        val cleanTitle = videoTitleOrTopic.trim()
        val effectiveStyle = nicheOrStyle.trim().ifBlank { "Modern High-CTR YouTube Creator Studio" }
        val cleanOverlay = overlayText.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(5)
            .joinToString(" ")

        return buildString {
            appendLine(SYSTEM_INSTRUCTION)
            appendLine()
            appendLine("THUMBNAIL BRIEF (${variation.angleLabel}):")
            appendLine("- Aspect Ratio: 16:9 widescreen YouTube thumbnail (1280x720).")
            appendLine("- Video Title / Topic: \"$cleanTitle\"")
            appendLine("- Niche / Visual Style Preference: $effectiveStyle")
            if (cleanOverlay.isNotBlank()) {
                appendLine("- Exact Thumbnail Text (MAX 3-5 WORDS, huge bold high-contrast typography): \"$cleanOverlay\"")
            } else {
                appendLine("- Thumbnail Text: Choose a punchy 3 to 4 word hook that complements (does not repeat) the title \"$cleanTitle\" in huge, bold, high-contrast letters.")
            }
            if (hasReferenceImage) {
                appendLine("- Reference Image Provided: Incorporate the person, face, or brand subject from the attached reference image as the primary focal point with a crisp rim light separating them from the background.")
            }
            appendLine("- Variation Composition Directive: ${variation.compositionDirective}")
            appendLine("- Mandatory Safety Zone: Leave the bottom-right corner clear of any text or critical faces so the YouTube duration timestamp badge never covers anything important.")
        }.trim()
    }
}
