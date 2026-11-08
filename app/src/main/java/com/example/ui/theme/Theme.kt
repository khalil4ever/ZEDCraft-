package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val ZedDarkColorScheme = darkColorScheme(
    primary = ZedCyan,
    onPrimary = Color(0xFF001F26),
    primaryContainer = Color(0xFF0B2E3B),
    onPrimaryContainer = ZedCyan,
    secondary = ZedViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF281B4D),
    onSecondaryContainer = Color(0xFFDDD6FE),
    tertiary = ZedSuccess,
    onTertiary = Color(0xFF002114),
    background = ZedBgPrimary,
    onBackground = ZedTextPrimary,
    surface = ZedBgSecondary,
    onSurface = ZedTextPrimary,
    surfaceVariant = ZedSurfaceCard,
    onSurfaceVariant = ZedTextSecondary,
    outline = ZedGlassBorder,
    error = ZedError,
    onError = Color.White
)

private val ZedLightColorScheme = lightColorScheme(
    primary = ZedLightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0369A1),
    secondary = ZedLightSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE9FE),
    onSecondaryContainer = Color(0xFF5B21B6),
    tertiary = ZedSuccess,
    onTertiary = Color.White,
    background = ZedLightBg,
    onBackground = ZedLightTextPrimary,
    surface = ZedLightSurface,
    onSurface = ZedLightTextPrimary,
    surfaceVariant = ZedLightCard,
    onSurfaceVariant = ZedLightTextSecondary,
    outline = Color(0xFFCBD5E1),
    error = ZedError,
    onError = Color.White
)

val ZedShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ZEDCraftTheme(
    darkTheme: Boolean = true, // Default to dark mode per user instructions
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ZedDarkColorScheme else ZedLightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.surface.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = colorScheme.surface.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            // Light (white) status bar icons on Dark Navy header; dark icons on Light surface
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = ZedShapes,
        content = content
    )
}
