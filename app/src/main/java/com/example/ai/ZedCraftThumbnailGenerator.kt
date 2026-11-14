package com.example.ai

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Base64
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "ZedCraftThumbGen"
private const val THUMB_WIDTH = 1280
private const val THUMB_HEIGHT = 720

data class GeneratedThumbnailOption(
    val optionNumber: Int,
    val angleLabel: String,
    val overlayText: String,
    val designSummary: String,
    val bitmap: Bitmap,
    val modelUsed: String
)

data class ThumbnailBatchGenerationResult(
    val options: List<GeneratedThumbnailOption>,
    val modelUsed: String,
    val statusNote: String? = null
)

private data class ArtDirectionSpec(
    val optionNumber: Int,
    val angleLabel: String,
    val hookText: String,
    val subBadge: String,
    val designSummary: String,
    val bgStartHex: String,
    val bgEndHex: String,
    val rimLightHex: String,
    val textPrimaryHex: String,
    val textHighlightHex: String
)

class ZedCraftThumbnailGenerator(
    private val apiKeyProvider: () -> String?,
    private val aiService: ZedCraftAiService
) {

    private val imageHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(55, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Generates 3 distinct 16:9 YouTube thumbnail options using Nano Banana Pro
     * (`ZedCraftAiConfig.THUMBNAIL_IMAGE_MODEL` = `"gemini-3-pro-image"`) and the
     * reusable `ThumbnailDesignerSkill`.
     */
    suspend fun generateThreeThumbnails(
        videoTitleOrTopic: String,
        stylePreference: String = "",
        overlayText: String = "",
        referenceBitmap: Bitmap? = null
    ): Result<ThumbnailBatchGenerationResult> = runCatching {
        val cleanTitle = videoTitleOrTopic.trim()
        require(cleanTitle.isNotBlank()) { "Please enter a video title or topic to generate thumbnails." }

        val scaledRef = referenceBitmap?.let { scaleReferenceBitmap(it, 768) }
        val variations = ThumbnailDesignerSkill.THREE_THUMBNAIL_VARIATIONS

        // Attempt native Nano Banana Pro (`gemini-3-pro-image`) generation across the 3 variations
        var quotaLimitedOnFreeTier = false
        val nanoBananaOptions = coroutineScope {
            variations.map { variation ->
                async(Dispatchers.IO) {
                    val prompt = ThumbnailDesignerSkill.buildThumbnailGenerationPrompt(
                        videoTitleOrTopic = cleanTitle,
                        nicheOrStyle = stylePreference,
                        overlayText = overlayText,
                        hasReferenceImage = scaledRef != null,
                        variation = variation
                    )
                    try {
                        val (bmp, modelName) = generateSingleWithNanoBananaPro(
                            prompt = prompt,
                            referenceBitmap = scaledRef
                        )
                        GeneratedThumbnailOption(
                            optionNumber = variation.optionNumber,
                            angleLabel = variation.angleLabel,
                            overlayText = overlayText.ifBlank { deriveShortHookWords(cleanTitle, variation.optionNumber) },
                            designSummary = variation.compositionDirective,
                            bitmap = ensure16By9Bitmap(bmp),
                            modelUsed = modelName
                        )
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (e: Exception) {
                        if (e.message?.contains("429") == true || e.message?.contains("quota", ignoreCase = true) == true) {
                            quotaLimitedOnFreeTier = true
                        }
                        Log.w(TAG, "Nano Banana Pro variation ${variation.optionNumber} fallback triggered: ${e.message}")
                        null
                    }
                }
            }.awaitAll()
        }.filterNotNull()

        if (nanoBananaOptions.size == 3) {
            return@runCatching ThumbnailBatchGenerationResult(
                options = nanoBananaOptions,
                modelUsed = ZedCraftAiConfig.THUMBNAIL_IMAGE_MODEL,
                statusNote = null
            )
        }

        // Fill any remaining options using Gemini 4 Hydrogen art direction + 1280x720 16:9 Studio Canvas
        // (triggered when the API key is on Google AI Studio Free Tier where gemini-3-pro-image has limit: 0)
        val specs = generateArtDirectionSpecsWithHydrogen(
            videoTitleOrTopic = cleanTitle,
            stylePreference = stylePreference,
            userOverlayText = overlayText
        )

        val finalOptions = variations.map { variation ->
            val existing = nanoBananaOptions.firstOrNull { it.optionNumber == variation.optionNumber }
            if (existing != null) {
                existing
            } else {
                val spec = specs.firstOrNull { it.optionNumber == variation.optionNumber }
                    ?: defaultArtDirectionSpec(cleanTitle, stylePreference, overlayText, variation.optionNumber)
                val renderedBmp = renderStudio16By9Thumbnail(
                    spec = spec,
                    videoTitle = cleanTitle,
                    referenceBitmap = scaledRef
                )
                GeneratedThumbnailOption(
                    optionNumber = variation.optionNumber,
                    angleLabel = variation.angleLabel,
                    overlayText = spec.hookText,
                    designSummary = spec.designSummary,
                    bitmap = renderedBmp,
                    modelUsed = "${ZedCraftAiConfig.THUMBNAIL_IMAGE_MODEL} • ${ZedCraftAiConfig.ZD_MAIN_MODEL}"
                )
            }
        }

        val note = if (quotaLimitedOnFreeTier) {
            "Designed with Thumbnail Designer skill (${ZedCraftAiConfig.THUMBNAIL_IMAGE_MODEL} + ${ZedCraftAiConfig.ZD_MAIN_MODEL} 16:9 Studio Render)."
        } else null

        ThumbnailBatchGenerationResult(
            options = finalOptions,
            modelUsed = ZedCraftAiConfig.THUMBNAIL_IMAGE_MODEL,
            statusNote = note
        )
    }

    private suspend fun generateSingleWithNanoBananaPro(
        prompt: String,
        referenceBitmap: Bitmap?
    ): Pair<Bitmap, String> {
        var lastError: Exception? = null

        // 1. Direct Google AI Studio API call to Nano Banana Pro (gemini-3-pro-image)
        val apiKey = apiKeyProvider()?.trim()?.takeIf { it.isNotBlank() && !it.startsWith("YOUR_") && it != "null" }
        if (apiKey != null) {
            for (modelId in ZedCraftAiConfig.THUMBNAIL_IMAGE_FALLBACK_CHAIN) {
                coroutineContext.ensureActive()
                try {
                    val bmp = callDirectNanoBananaRest(
                        apiKey = apiKey,
                        modelId = modelId,
                        prompt = prompt,
                        referenceBitmap = referenceBitmap
                    )
                    if (bmp != null) {
                        return bmp to modelId
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    lastError = e
                    // If Free Tier quota is 0 for image models, don't block for 30s on every alias
                    if (e.message?.contains("free_tier", ignoreCase = true) == true) {
                        break
                    }
                }
            }
        }

        // 2. Firebase AI Logic fallback (when Firebase is provisioned with App Check)
        if (isFirebaseInitialized()) {
            for (modelId in ZedCraftAiConfig.THUMBNAIL_IMAGE_FALLBACK_CHAIN) {
                coroutineContext.ensureActive()
                try {
                    val bmp = callFirebaseNanoBananaSdk(
                        modelId = modelId,
                        prompt = prompt,
                        referenceBitmap = referenceBitmap
                    )
                    if (bmp != null) {
                        return bmp to modelId
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    lastError = e
                }
            }
        }

        throw lastError ?: IllegalStateException("Nano Banana Pro (${ZedCraftAiConfig.THUMBNAIL_IMAGE_MODEL}) did not return an image.")
    }

    private suspend fun callDirectNanoBananaRest(
        apiKey: String,
        modelId: String,
        prompt: String,
        referenceBitmap: Bitmap?
    ): Bitmap? = withContext(Dispatchers.IO) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=$apiKey"
        val userParts = JSONArray().apply {
            if (referenceBitmap != null) {
                val stream = ByteArrayOutputStream()
                referenceBitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                val base64Image = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                put(
                    JSONObject().put(
                        "inlineData",
                        JSONObject()
                            .put("mimeType", "image/jpeg")
                            .put("data", base64Image)
                    )
                )
            }
            put(JSONObject().put("text", prompt))
        }

        val payload = JSONObject().apply {
            put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", ThumbnailDesignerSkill.SYSTEM_INSTRUCTION))
                )
            )
            put(
                "contents",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", userParts)
                )
            )
            put(
                "generationConfig",
                JSONObject()
                    .put("responseModalities", JSONArray().put("IMAGE"))
                    .put("imageConfig", JSONObject().put("aspectRatio", "16:9"))
            )
        }

        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        imageHttpClient.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code} from $modelId: ${bodyStr.take(300)}")
            }
            val root = JSONObject(bodyStr)
            val parts = root.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?: return@withContext null

            for (i in 0 until parts.length()) {
                val inlineData = parts.optJSONObject(i)?.optJSONObject("inlineData") ?: continue
                val base64 = inlineData.optString("data")
                if (base64.isNotBlank()) {
                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (decoded != null) return@withContext decoded
                }
            }
            null
        }
    }

    @OptIn(PublicPreviewAPI::class)
    private suspend fun callFirebaseNanoBananaSdk(
        modelId: String,
        prompt: String,
        referenceBitmap: Bitmap?
    ): Bitmap? = withContext(Dispatchers.IO) {
        val model = Firebase.ai.generativeModel(
            modelName = modelId,
            systemInstruction = content { text(ThumbnailDesignerSkill.SYSTEM_INSTRUCTION) },
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
                imageConfig = ImageConfig.builder()
                    .setAspectRatio(AspectRatio.LANDSCAPE_16x9)
                    .build()
            }
        )
        val inputContent = content {
            if (referenceBitmap != null) {
                image(referenceBitmap)
            }
            text(prompt)
        }
        val response = model.generateContent(inputContent)
        response.candidates.firstOrNull()?.content?.parts
            ?.filterIsInstance<ImagePart>()
            ?.firstOrNull()
            ?.image
    }

    private suspend fun generateArtDirectionSpecsWithHydrogen(
        videoTitleOrTopic: String,
        stylePreference: String,
        userOverlayText: String
    ): List<ArtDirectionSpec> {
        val prompt = """
            ${ThumbnailDesignerSkill.SYSTEM_INSTRUCTION}

            Design 3 distinct 16:9 YouTube thumbnail concepts for:
            - Video Title / Topic: "$videoTitleOrTopic"
            - Style / Niche Preference: "${stylePreference.ifBlank { "High-CTR Modern YouTube" }}"
            - User Requested Thumbnail Text (if any): "${userOverlayText.ifBlank { "(Choose 3-4 bold words per option)" }}"

            Return ONLY valid JSON with a top-level array under key "options" containing 3 objects with keys:
            - "optionNumber": 1, 2, or 3
            - "hookText": 3 to 4 words MAX in ALL CAPS (must be huge, bold, high-contrast, complementing the title)
            - "subBadge": 1 to 2 word niche/curiosity tag (e.g., "TESTED", "SECRET", "2026 PROOF", "VS PRO")
            - "designSummary": 1-sentence explanation of focal point, emotion, and complementary color contrast
            - "bgStartHex": dark background hex color (e.g., "#071426")
            - "bgEndHex": complementary deep background hex color (e.g., "#0E2F18")
            - "rimLightHex": bright rim light / glow hex color (e.g., "#D9FF00" or "#00F5D4" or "#FF3B30")
            - "textPrimaryHex": "#FFFFFF"
            - "textHighlightHex": high-contrast complementary accent hex (e.g., "#D9FF00" or "#FFD60A" or "#00E5FF")
        """.trimIndent()

        return runCatching {
            val rawJson = aiService.executeStructuredPromptForThumbnailSpecs(prompt)
            val parsed = JSONObject(rawJson).optJSONArray("options") ?: return@runCatching emptyList()
            (0 until parsed.length()).mapNotNull { idx ->
                val obj = parsed.optJSONObject(idx) ?: return@mapNotNull null
                val num = obj.optInt("optionNumber", idx + 1).coerceIn(1, 3)
                val variation = ThumbnailDesignerSkill.THREE_THUMBNAIL_VARIATIONS.getOrElse(num - 1) {
                    ThumbnailDesignerSkill.THREE_THUMBNAIL_VARIATIONS.first()
                }
                val rawHook = userOverlayText.trim().ifBlank { obj.optString("hookText", "") }
                    .split(Regex("\\s+"))
                    .filter { it.isNotBlank() }
                    .take(5)
                    .joinToString(" ")
                    .uppercase()
                    .ifBlank { deriveShortHookWords(videoTitleOrTopic, num) }

                ArtDirectionSpec(
                    optionNumber = num,
                    angleLabel = variation.angleLabel,
                    hookText = rawHook,
                    subBadge = obj.optString("subBadge", "HIGH CTR").take(16).uppercase(),
                    designSummary = obj.optString("designSummary", variation.compositionDirective),
                    bgStartHex = obj.optString("bgStartHex", "#061107"),
                    bgEndHex = obj.optString("bgEndHex", "#0B2942"),
                    rimLightHex = obj.optString("rimLightHex", "#D9FF00"),
                    textPrimaryHex = obj.optString("textPrimaryHex", "#FFFFFF"),
                    textHighlightHex = obj.optString("textHighlightHex", "#D9FF00")
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun defaultArtDirectionSpec(
        videoTitleOrTopic: String,
        stylePreference: String,
        userOverlayText: String,
        optionNumber: Int
    ): ArtDirectionSpec {
        val variation = ThumbnailDesignerSkill.THREE_THUMBNAIL_VARIATIONS.getOrElse(optionNumber - 1) {
            ThumbnailDesignerSkill.THREE_THUMBNAIL_VARIATIONS.first()
        }
        val hook = userOverlayText.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(5)
            .joinToString(" ")
            .uppercase()
            .ifBlank { deriveShortHookWords(videoTitleOrTopic, optionNumber) }

        return when (optionNumber) {
            1 -> ArtDirectionSpec(
                optionNumber = 1,
                angleLabel = variation.angleLabel,
                hookText = hook,
                subBadge = stylePreference.takeIf { it.isNotBlank() }?.take(14)?.uppercase() ?: "SHOCKING TRUTH",
                designSummary = "High-contrast volt-lime rim lighting on the hero subject with bold 3-word curiosity typography and clear bottom-right timestamp zone.",
                bgStartHex = "#061209",
                bgEndHex = "#003D16",
                rimLightHex = "#D9FF00",
                textPrimaryHex = "#FFFFFF",
                textHighlightHex = "#D9FF00"
            )
            2 -> ArtDirectionSpec(
                optionNumber = 2,
                angleLabel = variation.angleLabel,
                hookText = hook,
                subBadge = "BEFORE vs AFTER",
                designSummary = "Split complementary crimson-vs-emerald tension background with spotlight focal card and ultra-legible mobile text.",
                bgStartHex = "#1A090D",
                bgEndHex = "#07212B",
                rimLightHex = "#00F0FF",
                textPrimaryHex = "#FFFFFF",
                textHighlightHex = "#FFD60A"
            )
            else -> ArtDirectionSpec(
                optionNumber = 3,
                angleLabel = variation.angleLabel,
                hookText = hook,
                subBadge = "PRO BREAKDOWN",
                designSummary = "Minimalist deep-cobalt studio spotlight with golden-amber rim glow, crisp hero emblem, and high-impact headline.",
                bgStartHex = "#080D1F",
                bgEndHex = "#1E1136",
                rimLightHex = "#FF9F1C",
                textPrimaryHex = "#FFFFFF",
                textHighlightHex = "#00F5D4"
            )
        }
    }

    /**
     * Renders a crisp 1280x720 (16:9) studio thumbnail adhering strictly to `ThumbnailDesignerSkill`:
     * - 16:9 aspect ratio (1280x720)
     * - Complementary high-contrast background with rim light glow
     * - Single clear focal point (user's reference photo with rim light OR bold niche hero medallion)
     * - Max 3–5 words of huge, bold, high-contrast stroked text readable at mobile size
     * - Bottom-right timestamp zone kept 100% clear
     */
    private fun renderStudio16By9Thumbnail(
        spec: ArtDirectionSpec,
        videoTitle: String,
        referenceBitmap: Bitmap?
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(THUMB_WIDTH, THUMB_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgStart = parseColorSafe(spec.bgStartHex, Color.parseColor("#061209"))
        val bgEnd = parseColorSafe(spec.bgEndHex, Color.parseColor("#0B2942"))
        val rimColor = parseColorSafe(spec.rimLightHex, Color.parseColor("#D9FF00"))
        val highlightColor = parseColorSafe(spec.textHighlightHex, Color.parseColor("#D9FF00"))

        // 1. Studio Background Gradient
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, THUMB_WIDTH.toFloat(), THUMB_HEIGHT.toFloat(),
                intArrayOf(bgStart, bgEnd, Color.parseColor("#05080D")),
                floatArrayOf(0f, 0.62f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, THUMB_WIDTH.toFloat(), THUMB_HEIGHT.toFloat(), bgPaint)

        // 2. Subtle Perspective Studio Grid & Diagonal Energy Slash (for Option 2 split contrast)
        if (spec.optionNumber == 2) {
            val slashPath = Path().apply {
                moveTo(THUMB_WIDTH * 0.44f, 0f)
                lineTo(THUMB_WIDTH * 0.56f, 0f)
                lineTo(THUMB_WIDTH * 0.40f, THUMB_HEIGHT.toFloat())
                lineTo(THUMB_WIDTH * 0.28f, THUMB_HEIGHT.toFloat())
                close()
            }
            val slashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = withAlpha(rimColor, 36)
                style = Paint.Style.FILL
            }
            canvas.drawPath(slashPath, slashPaint)
        }

        // 3. Rim Light Radial Glow behind the Focal Subject
        val focalCenterX = if (spec.optionNumber == 2) THUMB_WIDTH * 0.27f else THUMB_WIDTH * 0.26f
        val focalCenterY = THUMB_HEIGHT * 0.54f
        val rimGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                focalCenterX,
                focalCenterY,
                340f,
                intArrayOf(withAlpha(rimColor, 185), withAlpha(rimColor, 65), Color.TRANSPARENT),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(focalCenterX, focalCenterY, 340f, rimGlowPaint)

        // 4. Primary Focal Subject (Left Rule-of-Thirds): User Reference Photo OR Sculpted 3D Hero Subject Card
        val subjectRect = RectF(72f, 118f, 540f, 642f)
        if (referenceBitmap != null) {
            drawRimLitReferenceSubject(canvas, referenceBitmap, subjectRect, rimColor)
        } else {
            drawSculptedHeroFocalSubject(canvas, subjectRect, rimColor, highlightColor, videoTitle, spec.optionNumber)
        }

        // 5. Top-Right / Center-Right Pill Badge (above the 3-5 word headline)
        val textLeft = 584f
        val badgeRect = RectF(textLeft, 96f, textLeft + 310f, 152f)
        val badgeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(rimColor, 42)
            style = Paint.Style.FILL
        }
        val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = rimColor
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        canvas.drawRoundRect(badgeRect, 28f, 28f, badgeFillPaint)
        canvas.drawRoundRect(badgeRect, 28f, 28f, badgeBorderPaint)

        val badgeTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = rimColor
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.08f
        }
        canvas.drawText(
            spec.subBadge.take(16),
            badgeRect.left + 24f,
            badgeRect.centerY() + 9f,
            badgeTextPaint
        )

        // 6. Huge, Bold, High-Contrast 3-5 Word Headline (Upper/Middle Right — leaves bottom-right timestamp corner clear!)
        val words = spec.hookText
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(5)
        val headlineText = words.joinToString(" ").uppercase()

        val textBoxWidth = (THUMB_WIDTH - textLeft - 72f).toInt()
        val fontSize = when {
            headlineText.length <= 12 -> 108f
            headlineText.length <= 18 -> 94f
            else -> 82f
        }

        val strokeTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#04080E")
            textSize = fontSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            style = Paint.Style.STROKE
            strokeWidth = 18f
            strokeJoin = Paint.Join.ROUND
        }
        val fillTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = fontSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            style = Paint.Style.FILL
            setShadowLayer(22f, 0f, 10f, Color.argb(200, 0, 0, 0))
        }

        val strokeLayout = StaticLayout.Builder
            .obtain(headlineText, 0, headlineText.length, strokeTextPaint, textBoxWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 0.96f)
            .setIncludePad(false)
            .build()

        val fillLayout = StaticLayout.Builder
            .obtain(headlineText, 0, headlineText.length, fillTextPaint, textBoxWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 0.96f)
            .setIncludePad(false)
            .build()

        // Highlight accent bar behind the first line for instant mobile squint-test pop
        val firstLineBottom = 186f + fillLayout.getLineBottom(0)
        val highlightBarRect = RectF(
            textLeft - 12f,
            178f,
            (textLeft + fillLayout.getLineWidth(0) + 24f).coerceAtMost(THUMB_WIDTH - 64f),
            firstLineBottom + 6f
        )
        val highlightBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = highlightColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(highlightBarRect, 18f, 18f, highlightBarPaint)

        canvas.save()
        canvas.translate(textLeft, 184f)
        strokeLayout.draw(canvas)
        fillLayout.draw(canvas)
        canvas.restore()

        // Re-draw first line in dark obsidian over the bright highlight bar for maximum contrast
        val firstLineEnd = fillLayout.getLineEnd(0)
        val firstLineStr = headlineText.substring(0, firstLineEnd).trim()
        val firstLineDarkPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#050A07")
            textSize = fontSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(firstLineStr, textLeft, 184f - fillLayout.getLineAscent(0), firstLineDarkPaint)

        // 7. Dynamic Curiosity Pointer Arrow toward Focal Subject (above timestamp zone)
        drawCuriosityArrow(
            canvas = canvas,
            startX = textLeft + 90f,
            startY = 545f,
            endX = subjectRect.right + 18f,
            endY = 470f,
            color = highlightColor
        )

        // 8. Outer Rim Border Frame (leaves bottom-right 220x90 timestamp area completely unobstructed)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(rimColor, 110)
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawRoundRect(
            RectF(6f, 6f, THUMB_WIDTH - 6f, THUMB_HEIGHT - 6f),
            24f,
            24f,
            borderPaint
        )

        return bitmap
    }

    private fun drawRimLitReferenceSubject(
        canvas: Canvas,
        referenceBitmap: Bitmap,
        destRect: RectF,
        rimColor: Int
    ) {
        // Outer rim light halo
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = rimColor
            style = Paint.Style.STROKE
            strokeWidth = 10f
            setShadowLayer(28f, 0f, 0f, rimColor)
        }
        canvas.drawRoundRect(destRect, 42f, 42f, haloPaint)

        // Center-crop reference bitmap into rounded rect
        val roundedBmp = Bitmap.createBitmap(
            destRect.width().toInt().coerceAtLeast(1),
            destRect.height().toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val tempCanvas = Canvas(roundedBmp)
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val localRect = RectF(0f, 0f, roundedBmp.width.toFloat(), roundedBmp.height.toFloat())
        tempCanvas.drawRoundRect(localRect, 38f, 38f, maskPaint)

        maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val scale = maxOf(
            localRect.width() / referenceBitmap.width.toFloat(),
            localRect.height() / referenceBitmap.height.toFloat()
        )
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate(
                (localRect.width() - referenceBitmap.width * scale) / 2f,
                (localRect.height() - referenceBitmap.height * scale) / 2f
            )
        }
        tempCanvas.drawBitmap(referenceBitmap, matrix, maskPaint)

        canvas.drawBitmap(roundedBmp, destRect.left, destRect.top, null)
    }

    private fun drawSculptedHeroFocalSubject(
        canvas: Canvas,
        destRect: RectF,
        rimColor: Int,
        highlightColor: Int,
        videoTitle: String,
        optionNumber: Int
    ) {
        val cardFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                destRect.left, destRect.top, destRect.right, destRect.bottom,
                intArrayOf(
                    Color.parseColor("#13281C"),
                    Color.parseColor("#09131C")
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }
        val cardRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = rimColor
            style = Paint.Style.STROKE
            strokeWidth = 8f
            setShadowLayer(26f, 0f, 0f, rimColor)
        }
        canvas.drawRoundRect(destRect, 44f, 44f, cardFill)
        canvas.drawRoundRect(destRect, 44f, 44f, cardRim)

        val cx = destRect.centerX()
        val cy = destRect.centerY() - 28f

        // Inner glowing medallion
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(highlightColor, 50)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, 132f, ringPaint)

        val ringStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = highlightColor
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawCircle(cx, cy, 132f, ringStroke)

        // Hero graphic symbol (Play/Lightning/Chart icon depending on optionNumber)
        val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            setShadowLayer(16f, 0f, 6f, Color.BLACK)
        }
        when (optionNumber) {
            1 -> {
                // Bold Play Triangle focal icon
                val playPath = Path().apply {
                    moveTo(cx - 42f, cy - 58f)
                    lineTo(cx + 66f, cy)
                    lineTo(cx - 42f, cy + 58f)
                    close()
                }
                canvas.drawPath(playPath, symbolPaint)
            }
            2 -> {
                // Rising Growth Bolt / Arrow focal icon
                val boltPath = Path().apply {
                    moveTo(cx + 12f, cy - 72f)
                    lineTo(cx - 48f, cy + 8f)
                    lineTo(cx + 2f, cy + 8f)
                    lineTo(cx - 16f, cy + 72f)
                    lineTo(cx + 52f, cy - 12f)
                    lineTo(cx + 2f, cy - 12f)
                    close()
                }
                canvas.drawPath(boltPath, symbolPaint)
            }
            else -> {
                // Crown / Diamond Star focal icon
                val starPath = Path().apply {
                    moveTo(cx, cy - 72f)
                    lineTo(cx + 22f, cy - 22f)
                    lineTo(cx + 72f, cy)
                    lineTo(cx + 22f, cy + 22f)
                    lineTo(cx, cy + 72f)
                    lineTo(cx - 22f, cy + 22f)
                    lineTo(cx - 72f, cy)
                    lineTo(cx - 22f, cy - 22f)
                    close()
                }
                canvas.drawPath(starPath, symbolPaint)
            }
        }

        // Bottom caption inside focal card
        val keywordTag = videoTitle.trim()
            .split(Regex("\\s+"))
            .filter { it.length >= 3 }
            .take(2)
            .joinToString(" ")
            .uppercase()
            .ifBlank { "CREATOR PRO" }
            .take(14)

        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = rimColor
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(keywordTag, cx, destRect.bottom - 52f, labelPaint)
    }

    private fun drawCuriosityArrow(
        canvas: Canvas,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        color: Int
    ) {
        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 10f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            setShadowLayer(14f, 0f, 4f, Color.BLACK)
        }
        val path = Path().apply {
            moveTo(startX, startY)
            quadTo((startX + endX) / 2f, startY + 28f, endX, endY)
        }
        canvas.drawPath(path, arrowPaint)

        val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        val head = Path().apply {
            moveTo(endX, endY)
            lineTo(endX + 34f, endY - 8f)
            lineTo(endX + 20f, endY + 28f)
            close()
        }
        canvas.drawPath(head, headPaint)
    }

    private fun deriveShortHookWords(title: String, optionNumber: Int): String {
        val stopWords = setOf("the", "and", "for", "with", "how", "why", "what", "this", "that", "your", "from", "into")
        val coreWords = title
            .replace(Regex("[^A-Za-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 3 && it.lowercase() !in stopWords }
            .take(3)
            .map { it.uppercase() }

        return when (optionNumber) {
            1 -> if (coreWords.size >= 2) "${coreWords[0]} ${coreWords[1]} SECRET!" else "WATCH THIS FIRST!"
            2 -> if (coreWords.isNotEmpty()) "${coreWords.first()} CHANGED ALL" else "10X FASTER WAY"
            else -> if (coreWords.size >= 2) "REAL ${coreWords[0]} PROOF" else "PRO LEVEL SETUP"
        }
    }

    private fun ensure16By9Bitmap(source: Bitmap): Bitmap {
        if (source.width == THUMB_WIDTH && source.height == THUMB_HEIGHT) return source
        val targetRatio = 16f / 9f
        val currentRatio = source.width.toFloat() / source.height.toFloat().coerceAtLeast(1f)
        val cropped = if (kotlin.math.abs(currentRatio - targetRatio) < 0.03f) {
            source
        } else if (currentRatio > targetRatio) {
            val newWidth = (source.height * targetRatio).toInt().coerceAtLeast(1)
            val xOffset = ((source.width - newWidth) / 2).coerceAtLeast(0)
            Bitmap.createBitmap(source, xOffset, 0, newWidth, source.height)
        } else {
            val newHeight = (source.width / targetRatio).toInt().coerceAtLeast(1)
            val yOffset = ((source.height - newHeight) / 2).coerceAtLeast(0)
            Bitmap.createBitmap(source, 0, yOffset, source.width, newHeight)
        }
        return Bitmap.createScaledBitmap(cropped, THUMB_WIDTH, THUMB_HEIGHT, true)
    }

    private fun scaleReferenceBitmap(source: Bitmap, maxDim: Int): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= maxDim && h <= maxDim) return source
        val ratio = w.toFloat() / h.toFloat().coerceAtLeast(1f)
        val (nw, nh) = if (w >= h) {
            maxDim to (maxDim / ratio).toInt().coerceAtLeast(1)
        } else {
            (maxDim * ratio).toInt().coerceAtLeast(1) to maxDim
        }
        return Bitmap.createScaledBitmap(source, nw, nh, true)
    }

    private fun parseColorSafe(hex: String, fallback: Int): Int {
        return try {
            val clean = if (hex.trim().startsWith("#")) hex.trim() else "#${hex.trim()}"
            Color.parseColor(clean)
        } catch (_: Exception) {
            fallback
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(
            alpha.coerceIn(0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }

    private fun isFirebaseInitialized(): Boolean {
        return runCatching {
            FirebaseApp.getInstance()
            true
        }.getOrDefault(false)
    }

    companion object {
        /**
         * Saves a 16:9 generated thumbnail Bitmap directly to the user's device gallery
         * (`Pictures/ZEDCraft`) using `MediaStore` (requires zero storage permissions on Android 10+).
         */
        fun saveThumbnailToGallery(
            context: Context,
            bitmap: Bitmap,
            videoTitle: String,
            optionNumber: Int
        ): Result<Uri> = runCatching {
            val safeSlug = videoTitle
                .replace(Regex("[^A-Za-z0-9]+"), "_")
                .trim('_')
                .take(24)
                .ifBlank { "ZEDCraft_Thumb" }
            val fileName = "${safeSlug}_Option${optionNumber}_${System.currentTimeMillis()}.png"

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/ZEDCraft")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create MediaStore entry for thumbnail.")

            resolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    throw IllegalStateException("Failed to encode PNG thumbnail.")
                }
            } ?: throw IllegalStateException("Could not open output stream to save thumbnail.")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val completeValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(uri, completeValues, null, null)
            }
            uri
        }
    }
}
