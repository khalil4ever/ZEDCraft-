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
    primary = ZedBrandVoltLime,
    onPrimary = ZedOnPrimaryDark,
    primaryContainer = Color(0xFF143818),
    onPrimaryContainer = ZedBrandVoltLime,
    secondary = ZedBrandLegibleGreen,
    onSecondary = ZedOnPrimaryDark,
    secondaryContainer = ZedBrandKellyGreen,
    onSecondaryContainer = ZedTextPrimary,
    tertiary = ZedBrandMidGradient,
    onTertiary = ZedOnPrimaryDark,
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
    primaryContainer = Color(0xFFD8F5D2),
    onPrimaryContainer = Color(0xFF004D04),
    secondary = ZedLightSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECFCCB),
    onSecondaryContainer = Color(0xFF285400),
    tertiary = ZedBrandKellyGreen,
    onTertiary = Color.White,
    background = ZedLightBg,
    onBackground = ZedLightTextPrimary,
    surface = ZedLightSurface,
    onSurface = ZedLightTextPrimary,
    surfaceVariant = ZedLightCard,
    onSurfaceVariant = ZedLightTextSecondary,
    outline = Color(0xFFB7D9B9),
    error = ZedError,
    onError = Color.White
)

val ZedShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ZEDCraftTheme(
    darkTheme: Boolean = true, // Default to dark mode built from the brand colors
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
