package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

private val SplashDarkBackground = Color(0xFF070B14)
private val YouTubeBrightRed = Color(0xFFFF0000)
private val YouTubeDeepRed = Color(0xFFB80000)
private val YouTubeNeonGlow = Color(0xFFFF2A2A)

private val SPLASH_STATUS_MESSAGES = listOf(
    "Optimizing YouTube...",
    "Fetching data...",
    "Analyzing your channel...",
    "Scoring your titles...",
    "Warming up ZD...",
    "Almost ready..."
)

private data class SparkleParticle(
    val angleRad: Float,
    val baseRadiusFraction: Float,
    val speedMultiplier: Float,
    val sizeDp: Float,
    val isRed: Boolean,
    val phaseOffset: Float
)

@Composable
fun ZedCraftSplashLoadingScreen(
    modifier: Modifier = Modifier
) {
    var entered by remember { mutableStateOf(false) }
    var messageIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        entered = true
        while (true) {
            delay(1250L)
            messageIndex = (messageIndex + 1) % SPLASH_STATUS_MESSAGES.size
        }
    }

    val entranceScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.72f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "splash_entrance_scale"
    )
    val entranceAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "splash_entrance_alpha"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "splash_infinite")

    // Soft breathing red neon halo (~1.5s cycle)
    val haloPulse by infiniteTransition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_pulse"
    )

    // Sweeping light shine across the 3D play button every ~2 seconds
    val shineProgress by infiniteTransition.animateFloat(
        initialValue = -0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shine_sweep"
    )

    // Floating sparkle animation phase
    val particlePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particle_phase"
    )

    // Rainbow circular spinner rotation
    val spinnerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinner_rotation"
    )

    val sparkles = remember {
        listOf(
            SparkleParticle(angleRad = 0.35f, baseRadiusFraction = 0.70f, speedMultiplier = 1.0f, sizeDp = 3.2f, isRed = false, phaseOffset = 0.0f),
            SparkleParticle(angleRad = 1.15f, baseRadiusFraction = 0.82f, speedMultiplier = 1.3f, sizeDp = 2.6f, isRed = true, phaseOffset = 1.1f),
            SparkleParticle(angleRad = 2.05f, baseRadiusFraction = 0.74f, speedMultiplier = 0.9f, sizeDp = 3.5f, isRed = false, phaseOffset = 2.2f),
            SparkleParticle(angleRad = 2.85f, baseRadiusFraction = 0.86f, speedMultiplier = 1.2f, sizeDp = 2.8f, isRed = true, phaseOffset = 3.0f),
            SparkleParticle(angleRad = 3.75f, baseRadiusFraction = 0.72f, speedMultiplier = 1.1f, sizeDp = 3.0f, isRed = false, phaseOffset = 4.1f),
            SparkleParticle(angleRad = 4.65f, baseRadiusFraction = 0.84f, speedMultiplier = 1.4f, sizeDp = 2.5f, isRed = true, phaseOffset = 5.0f),
            SparkleParticle(angleRad = 5.45f, baseRadiusFraction = 0.76f, speedMultiplier = 1.0f, sizeDp = 3.4f, isRed = false, phaseOffset = 5.7f),
            SparkleParticle(angleRad = 6.05f, baseRadiusFraction = 0.88f, speedMultiplier = 1.25f, sizeDp = 2.4f, isRed = true, phaseOffset = 1.8f)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SplashDarkBackground)
            .testTag("zedcraft_splash_loading_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .scale(entranceScale)
                .alpha(entranceAlpha)
        ) {
            // Center Glossy 3D Glass Red Play Button + Breathing Neon Halo + Floating Sparkles
            Box(
                modifier = Modifier.size(210.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxRadius = size.minDimension / 2f

                    // 1. Soft breathing red neon halo behind the 3D button
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                YouTubeNeonGlow.copy(alpha = 0.44f * (haloPulse / 1.16f)),
                                YouTubeBrightRed.copy(alpha = 0.18f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = maxRadius * haloPulse
                        ),
                        radius = maxRadius * haloPulse,
                        center = center
                    )

                    // 2. Tiny floating red & white sparkle particles around the icon
                    sparkles.forEach { sp ->
                        val drift = sin(particlePhase * sp.speedMultiplier + sp.phaseOffset)
                        val r = maxRadius * sp.baseRadiusFraction + drift * 8.dp.toPx()
                        val angle = sp.angleRad + cos(particlePhase * 0.5f + sp.phaseOffset) * 0.14f
                        val px = center.x + cos(angle) * r
                        val py = center.y + sin(angle) * r
                        val twinkleAlpha = ((sin(particlePhase * 2f + sp.phaseOffset) + 1f) / 2f)
                            .coerceIn(0.22f, 0.95f)
                        val particleColor = if (sp.isRed) {
                            Color(0xFFFF4D4D).copy(alpha = twinkleAlpha)
                        } else {
                            Color.White.copy(alpha = twinkleAlpha)
                        }
                        drawCircle(
                            color = particleColor,
                            radius = sp.sizeDp.dp.toPx(),
                            center = Offset(px, py)
                        )
                    }

                    // 3. 3D Glass YouTube Play Button Geometry
                    val btnWidth = size.width * 0.54f
                    val btnHeight = size.height * 0.38f
                    val btnLeft = center.x - btnWidth / 2f
                    val btnTop = center.y - btnHeight / 2f
                    val cornerRadius = CornerRadius(btnHeight * 0.30f, btnHeight * 0.30f)

                    // 3D Bottom Extrusion Shadow / Depth Lip
                    drawRoundRect(
                        color = Color(0xFF5A0000),
                        topLeft = Offset(btnLeft, btnTop + 5.dp.toPx()),
                        size = Size(btnWidth, btnHeight),
                        cornerRadius = cornerRadius
                    )

                    // Main 3D Glossy Red Body
                    val buttonPath = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = btnLeft,
                                top = btnTop,
                                right = btnLeft + btnWidth,
                                bottom = btnTop + btnHeight,
                                cornerRadius = cornerRadius
                            )
                        )
                    }

                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFFF3838),
                                YouTubeBrightRed,
                                YouTubeDeepRed
                            ),
                            startY = btnTop,
                            endY = btnTop + btnHeight
                        ),
                        topLeft = Offset(btnLeft, btnTop),
                        size = Size(btnWidth, btnHeight),
                        cornerRadius = cornerRadius
                    )

                    // Clip inside the play button for top glass highlight & sweeping light shine
                    clipPath(buttonPath) {
                        // Subtle top glass reflection highlight
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.45f),
                                    Color.White.copy(alpha = 0.08f),
                                    Color.Transparent
                                ),
                                startY = btnTop,
                                endY = btnTop + btnHeight * 0.52f
                            ),
                            topLeft = Offset(btnLeft + 2.dp.toPx(), btnTop + 1.5.dp.toPx()),
                            size = Size(btnWidth - 4.dp.toPx(), btnHeight * 0.48f),
                            cornerRadius = cornerRadius
                        )

                        // Sweeping diagonal light shine across the button every ~2s
                        val sweepCenterX = btnLeft + btnWidth * shineProgress
                        val sweepWidth = btnWidth * 0.38f
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.38f),
                                    Color.Transparent
                                ),
                                start = Offset(sweepCenterX - sweepWidth, btnTop),
                                end = Offset(sweepCenterX + sweepWidth, btnTop + btnHeight)
                            ),
                            topLeft = Offset(btnLeft, btnTop),
                            size = Size(btnWidth, btnHeight)
                        )
                    }

                    // Crisp glass specular rim border
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.65f),
                                Color(0xFFFF6B6B).copy(alpha = 0.35f),
                                Color(0xFF7A0000).copy(alpha = 0.6f)
                            ),
                            startY = btnTop,
                            endY = btnTop + btnHeight
                        ),
                        topLeft = Offset(btnLeft, btnTop),
                        size = Size(btnWidth, btnHeight),
                        cornerRadius = cornerRadius,
                        style = Stroke(width = 1.6.dp.toPx())
                    )

                    // 4. Center White Play Triangle (with subtle 3D drop shadow)
                    val triWidth = btnWidth * 0.27f
                    val triHeight = btnHeight * 0.42f
                    val triLeft = center.x - triWidth * 0.38f
                    val triRight = triLeft + triWidth
                    val triTop = center.y - triHeight / 2f
                    val triBottom = center.y + triHeight / 2f

                    val shadowTriangle = Path().apply {
                        moveTo(triLeft, triTop + 2.dp.toPx())
                        lineTo(triRight, center.y + 2.dp.toPx())
                        lineTo(triLeft, triBottom + 2.dp.toPx())
                        close()
                    }
                    drawPath(
                        path = shadowTriangle,
                        color = Color.Black.copy(alpha = 0.28f)
                    )

                    val playTriangle = Path().apply {
                        moveTo(triLeft, triTop)
                        lineTo(triRight, center.y)
                        lineTo(triLeft, triBottom)
                        close()
                    }
                    drawPath(
                        path = playTriangle,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White,
                                Color(0xFFF0F4FF)
                            ),
                            startY = triTop,
                            endY = triBottom
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Small round rainbow gradient spinner rotating smoothly underneath
            Canvas(
                modifier = Modifier
                    .size(32.dp)
                    .testTag("splash_rainbow_spinner")
            ) {
                val strokeWidth = 3.2.dp.toPx()
                rotate(degrees = spinnerRotation) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color(0xFFFF1744),
                                Color(0xFFFF9100),
                                Color(0xFFFFEA00),
                                Color(0xFF00E676),
                                Color(0xFF00E5FF),
                                Color(0xFF2979FF),
                                Color(0xFFD500F9),
                                Color(0xFFFF1744)
                            )
                        ),
                        startAngle = 15f,
                        sweepAngle = 305f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Cycling status text below the spinner with smooth fade transition
            AnimatedContent(
                targetState = SPLASH_STATUS_MESSAGES[messageIndex],
                transitionSpec = {
                    fadeIn(animationSpec = tween(320)) togetherWith fadeOut(animationSpec = tween(320))
                },
                label = "splash_status_text"
            ) { statusText ->
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFB8C7E0),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
