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
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ZedBgPrimary
import com.example.ui.theme.ZedBrandKellyGreen
import com.example.ui.theme.ZedBrandMidGradient
import com.example.ui.theme.ZedBrandVoltLime
import com.example.ui.theme.ZedOnPrimaryDark
import com.example.ui.theme.ZedSurfaceCard
import com.example.ui.theme.ZedSurfaceElevated
import com.example.ui.theme.ZedTextPrimary
import com.example.ui.theme.ZedTextSecondary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

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
    val isVoltLime: Boolean,
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

    // Soft breathing #D9FF00 / #007E08 neon halo (~1.5s cycle)
    val haloPulse by infiniteTransition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_pulse"
    )

    // Sweeping light shine across the 3D squircle medallion every ~2 seconds
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

    // Brand gradient circular spinner rotation
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
            SparkleParticle(angleRad = 0.35f, baseRadiusFraction = 0.70f, speedMultiplier = 1.0f, sizeDp = 3.2f, isVoltLime = true, phaseOffset = 0.0f),
            SparkleParticle(angleRad = 1.15f, baseRadiusFraction = 0.82f, speedMultiplier = 1.3f, sizeDp = 2.6f, isVoltLime = false, phaseOffset = 1.1f),
            SparkleParticle(angleRad = 2.05f, baseRadiusFraction = 0.74f, speedMultiplier = 0.9f, sizeDp = 3.5f, isVoltLime = true, phaseOffset = 2.2f),
            SparkleParticle(angleRad = 2.85f, baseRadiusFraction = 0.86f, speedMultiplier = 1.2f, sizeDp = 2.8f, isVoltLime = false, phaseOffset = 3.0f),
            SparkleParticle(angleRad = 3.75f, baseRadiusFraction = 0.72f, speedMultiplier = 1.1f, sizeDp = 3.0f, isVoltLime = true, phaseOffset = 4.1f),
            SparkleParticle(angleRad = 4.65f, baseRadiusFraction = 0.84f, speedMultiplier = 1.4f, sizeDp = 2.5f, isVoltLime = false, phaseOffset = 5.0f),
            SparkleParticle(angleRad = 5.45f, baseRadiusFraction = 0.76f, speedMultiplier = 1.0f, sizeDp = 3.4f, isVoltLime = true, phaseOffset = 5.7f),
            SparkleParticle(angleRad = 6.05f, baseRadiusFraction = 0.88f, speedMultiplier = 1.25f, sizeDp = 2.4f, isVoltLime = false, phaseOffset = 1.8f)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ZedBgPrimary)
            .testTag("zedcraft_splash_loading_screen"),
        contentAlignment = Alignment.Center
    ) {
        // Ambient Soft Glowing Corner Orbs (inspired by Image 1's sculpted glass orbs)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val topOrbRadius = size.minDimension * 0.42f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ZedBrandKellyGreen.copy(alpha = 0.32f),
                        ZedBrandVoltLime.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.08f, size.height * 0.06f),
                    radius = topOrbRadius
                ),
                radius = topOrbRadius,
                center = Offset(size.width * 0.08f, size.height * 0.06f)
            )

            val rightOrbRadius = size.minDimension * 0.26f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ZedBrandVoltLime.copy(alpha = 0.16f),
                        ZedBrandKellyGreen.copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.94f, size.height * 0.30f),
                    radius = rightOrbRadius
                ),
                radius = rightOrbRadius,
                center = Offset(size.width * 0.94f, size.height * 0.30f)
            )

            val bottomOrbRadius = size.minDimension * 0.45f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ZedBrandKellyGreen.copy(alpha = 0.28f),
                        ZedBrandVoltLime.copy(alpha = 0.09f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.90f, size.height * 0.92f),
                    radius = bottomOrbRadius
                ),
                radius = bottomOrbRadius,
                center = Offset(size.width * 0.90f, size.height * 0.92f)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .scale(entranceScale)
                .alpha(entranceAlpha)
        ) {
            // Center Sculpted 3D Glass Squircle Medallion + #D9FF00 / #007E08 Play Emblem
            Box(
                modifier = Modifier.size(214.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxRadius = size.minDimension / 2f

                    // 1. Soft breathing #D9FF00 & #007E08 neon halo behind the squircle medallion
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                ZedBrandVoltLime.copy(alpha = 0.38f * (haloPulse / 1.16f)),
                                ZedBrandKellyGreen.copy(alpha = 0.26f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = maxRadius * haloPulse
                        ),
                        radius = maxRadius * haloPulse,
                        center = center
                    )

                    // 2. Floating #D9FF00 & #007E08 sparkle particles
                    sparkles.forEach { sp ->
                        val drift = sin(particlePhase * sp.speedMultiplier + sp.phaseOffset)
                        val r = maxRadius * sp.baseRadiusFraction + drift * 8.dp.toPx()
                        val angle = sp.angleRad + cos(particlePhase * 0.5f + sp.phaseOffset) * 0.14f
                        val px = center.x + cos(angle) * r
                        val py = center.y + sin(angle) * r
                        val twinkleAlpha = ((sin(particlePhase * 2f + sp.phaseOffset) + 1f) / 2f)
                            .coerceIn(0.24f, 0.95f)
                        val particleColor = if (sp.isVoltLime) {
                            ZedBrandVoltLime.copy(alpha = twinkleAlpha)
                        } else {
                            ZedBrandMidGradient.copy(alpha = twinkleAlpha)
                        }
                        drawCircle(
                            color = particleColor,
                            radius = sp.sizeDp.dp.toPx(),
                            center = Offset(px, py)
                        )
                    }

                    // 3. Outer Frosted Squircle Medallion (matching Image 1's sculpted squircle)
                    val squircleSize = size.minDimension * 0.64f
                    val sqLeft = center.x - squircleSize / 2f
                    val sqTop = center.y - squircleSize / 2f
                    val sqCorner = CornerRadius(squircleSize * 0.32f, squircleSize * 0.32f)

                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                ZedSurfaceElevated,
                                ZedSurfaceCard
                            ),
                            startY = sqTop,
                            endY = sqTop + squircleSize
                        ),
                        topLeft = Offset(sqLeft, sqTop),
                        size = Size(squircleSize, squircleSize),
                        cornerRadius = sqCorner
                    )

                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                ZedBrandVoltLime.copy(alpha = 0.65f),
                                ZedBrandKellyGreen.copy(alpha = 0.45f)
                            ),
                            start = Offset(sqLeft, sqTop),
                            end = Offset(sqLeft + squircleSize, sqTop + squircleSize)
                        ),
                        topLeft = Offset(sqLeft, sqTop),
                        size = Size(squircleSize, squircleSize),
                        cornerRadius = sqCorner,
                        style = Stroke(width = 1.8.dp.toPx())
                    )

                    // 4. Inner 3D Glossy #D9FF00 -> #007E08 Play Badge inside the Squircle
                    val btnWidth = squircleSize * 0.68f
                    val btnHeight = squircleSize * 0.48f
                    val btnLeft = center.x - btnWidth / 2f
                    val btnTop = center.y - btnHeight / 2f
                    val cornerRadius = CornerRadius(btnHeight * 0.32f, btnHeight * 0.32f)

                    // 3D Bottom Extrusion Lip in deep #007E08
                    drawRoundRect(
                        color = ZedBrandKellyGreen.copy(alpha = 0.85f),
                        topLeft = Offset(btnLeft, btnTop + 4.dp.toPx()),
                        size = Size(btnWidth, btnHeight),
                        cornerRadius = cornerRadius
                    )

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

                    // Main Gradient Body (#D9FF00 -> #7ED903 -> #007E08)
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                ZedBrandVoltLime,
                                ZedBrandMidGradient,
                                ZedBrandKellyGreen
                            ),
                            startY = btnTop,
                            endY = btnTop + btnHeight
                        ),
                        topLeft = Offset(btnLeft, btnTop),
                        size = Size(btnWidth, btnHeight),
                        cornerRadius = cornerRadius
                    )

                    // Sweeping diagonal light shine across the badge
                    clipPath(buttonPath) {
                        val sweepCenterX = btnLeft + btnWidth * shineProgress
                        val sweepWidth = btnWidth * 0.38f
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    ZedTextPrimary.copy(alpha = 0.45f),
                                    Color.Transparent
                                ),
                                start = Offset(sweepCenterX - sweepWidth, btnTop),
                                end = Offset(sweepCenterX + sweepWidth, btnTop + btnHeight)
                            ),
                            topLeft = Offset(btnLeft, btnTop),
                            size = Size(btnWidth, btnHeight)
                        )
                    }

                    // Center Crisp Play Triangle
                    val triWidth = btnWidth * 0.27f
                    val triHeight = btnHeight * 0.44f
                    val triLeft = center.x - triWidth * 0.36f
                    val triRight = triLeft + triWidth
                    val triTop = center.y - triHeight / 2f
                    val triBottom = center.y + triHeight / 2f

                    val playTriangle = Path().apply {
                        moveTo(triLeft, triTop)
                        lineTo(triRight, center.y)
                        lineTo(triLeft, triBottom)
                        close()
                    }
                    drawPath(
                        path = playTriangle,
                        color = ZedOnPrimaryDark
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "ZEDCraft",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = ZedTextPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "CREATE  •  OPTIMIZE  •  GROW",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = ZedBrandVoltLime
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Brand gradient circular spinner rotating smoothly underneath
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
                                ZedBrandKellyGreen.copy(alpha = 0.2f),
                                ZedBrandKellyGreen,
                                ZedBrandMidGradient,
                                ZedBrandVoltLime
                            )
                        ),
                        startAngle = 15f,
                        sweepAngle = 305f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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
                    color = ZedTextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
