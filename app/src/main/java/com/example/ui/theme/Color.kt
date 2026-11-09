package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * ZEDCraft Brand Color System (Extracted from Brand Palette Reference Image 2):
 * - Brand Color (1): Deep Kelly Forest Green (#007E08)
 * - Brand Color (2): Electric Volt Chartreuse Lime (#D9FF00)
 *
 * All design tokens (primary, secondary, accent, background, surface, surface-elevated,
 * border, text-primary, text-secondary, success, warning, error, and glow) are derived
 * exclusively from this palette.
 */

// Core Brand Hex Tokens from Image 2
val ZedBrandVoltLime = Color(0xFFD9FF00)       // Primary (#D9FF00)
val ZedBrandKellyGreen = Color(0xFF007E08)     // Secondary (#007E08)
val ZedBrandMidGradient = Color(0xFF7ED903)    // Midpoint of #007E08 -> #D9FF00 gradient
val ZedBrandLegibleGreen = Color(0xFF4CE856)   // Tinted bright variation of #007E08 for high-contrast text/icons on dark cards

// Dark Mode Surfaces (Default - Deep Obsidian Forest built from #007E08 & #D9FF00)
val ZedBgPrimary = Color(0xFF061107)           // background
val ZedBgSecondary = Color(0xFF0A1B0C)         // surface
val ZedSurfaceCard = Color(0xFF0F2612)         // surface-card (glassmorphism base)
val ZedSurfaceElevated = Color(0xFF16361A)     // surface-elevated
val ZedGlassBorder = Color(0xFF28592E)         // border (fine 1px specular border)

// Brand Semantic Tokens
val ZedCyan = ZedBrandVoltLime                 // primary (#D9FF00)
val ZedCyanSoft = Color(0xFFB8E600)            // primary-soft
val ZedViolet = ZedBrandLegibleGreen           // secondary legible accent for dark surfaces
val ZedVioletDeep = ZedBrandKellyGreen         // secondary deep (#007E08)
val ZedPinkAccent = Color(0xFFA8F205)          // accent highlight

// Status Tokens (harmonized with #007E08 & #D9FF00)
val ZedSuccess = Color(0xFF42E64C)             // success
val ZedWarning = Color(0xFFD9FF00)             // warning (#D9FF00 Volt Lime)
val ZedError = Color(0xFFFF5A4F)               // error (high-contrast warm coral)
val ZedBrandGlow = Color(0xFFD9FF00)           // glow

// Typography Contrast Tokens
val ZedTextPrimary = Color(0xFFF6FFEE)         // text-primary (crisp ivory-lime white)
val ZedTextSecondary = Color(0xFFB7D9BA)       // text-secondary (high-contrast sage-mint)
val ZedTextMuted = Color(0xFF77A37B)           // text-muted (muted forest sage)
val ZedOnPrimaryDark = Color(0xFF051806)       // deep forest ink for text/icons on #D9FF00 buttons

// Reusable Brand Gradient Brushes (matching Image 1 & Image 2)
val ZedBrandLinearGradient: Brush = Brush.linearGradient(
    colors = listOf(ZedBrandVoltLime, ZedBrandMidGradient, ZedBrandKellyGreen)
)

val ZedBrandReverseLinearGradient: Brush = Brush.linearGradient(
    colors = listOf(ZedBrandKellyGreen, ZedBrandMidGradient, ZedBrandVoltLime)
)

val ZedGlassCardGradient: Brush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF153419),
        Color(0xFF0D2110)
    )
)

// Semantic aliases used across all ZEDCraft screens
val ZedDarkBackground = ZedBgPrimary
val ZedGlassSurface = ZedSurfaceCard
val ZedDarkSurfaceElevated = ZedSurfaceElevated
val ZedCardBorder = ZedGlassBorder
val ZedCyanPrimary = ZedBrandVoltLime
val ZedPurpleAccent = ZedBrandLegibleGreen
val ZedEmeraldSuccess = ZedSuccess
val ZedAmberWarning = ZedWarning
val ZedCoralError = ZedError

// Light Mode Palette (built from the same #007E08 & #D9FF00 palette + Image 1 soft glass style)
val ZedLightBg = Color(0xFFF2F9F0)
val ZedLightSurface = Color(0xFFFFFFFF)
val ZedLightCard = Color(0xFFE6F4E3)
val ZedLightTextPrimary = Color(0xFF061B08)
val ZedLightTextSecondary = Color(0xFF2D5931)
val ZedLightPrimary = Color(0xFF007E08)
val ZedLightSecondary = Color(0xFF55A600)
