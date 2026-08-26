package com.spark.dating.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Spark palette ─────────────────────────────────────────────────────────────
// Signature: deep plum primary on near-black surface, warm coral accent.
// Avoids the generic red/pink dating-app default — more editorial, confident.

object SparkColors {
    // Plum family
    val PlumDeep    = Color(0xFF6B2D8B)  // primary dark
    val PlumMid     = Color(0xFF9B59C2)  // primary light
    val PlumLight   = Color(0xFFD4A8F0)  // tint on dark bg

    // Coral accent (the one bold move)
    val CoralFlame  = Color(0xFFFF6B6B)  // CTA, like button, match celebration
    val CoralSoft   = Color(0xFFFFB3B3)

    // Neutrals — warm-tinted, not cold grey
    val Ink         = Color(0xFF0F0A14)  // near-black surface
    val InkMid      = Color(0xFF1E1525)
    val InkSurface  = Color(0xFF2A1D36)
    val Dusk        = Color(0xFF4A3860)
    val Mist        = Color(0xFF9B8BAE)
    val Fog         = Color(0xFFD4C8E2)
    val Cream       = Color(0xFFF8F4FF)

    val White       = Color(0xFFFFFFFF)
    val Transparent = Color(0x00000000)

    // Semantic
    val Success     = Color(0xFF4CAF7C)
    val Warning     = Color(0xFFFFA726)
    val Error       = Color(0xFFE53935)
}

private val DarkColorScheme = darkColorScheme(
    primary             = SparkColors.PlumMid,
    onPrimary           = SparkColors.White,
    primaryContainer    = SparkColors.PlumDeep,
    onPrimaryContainer  = SparkColors.PlumLight,
    secondary           = SparkColors.CoralFlame,
    onSecondary         = SparkColors.White,
    secondaryContainer  = Color(0xFF8B2020),
    onSecondaryContainer= SparkColors.CoralSoft,
    tertiary            = SparkColors.Mist,
    onTertiary          = SparkColors.Ink,
    background          = SparkColors.Ink,
    onBackground        = SparkColors.Cream,
    surface             = SparkColors.InkMid,
    onSurface           = SparkColors.Fog,
    surfaceVariant      = SparkColors.InkSurface,
    onSurfaceVariant    = SparkColors.Mist,
    outline             = SparkColors.Dusk,
    error               = SparkColors.Error,
    onError             = SparkColors.White,
)

private val LightColorScheme = lightColorScheme(
    primary             = SparkColors.PlumDeep,
    onPrimary           = SparkColors.White,
    primaryContainer    = SparkColors.PlumLight,
    onPrimaryContainer  = SparkColors.PlumDeep,
    secondary           = SparkColors.CoralFlame,
    onSecondary         = SparkColors.White,
    secondaryContainer  = SparkColors.CoralSoft,
    onSecondaryContainer= Color(0xFF8B2020),
    tertiary            = SparkColors.Dusk,
    onTertiary          = SparkColors.White,
    background          = SparkColors.Cream,
    onBackground        = SparkColors.Ink,
    surface             = SparkColors.White,
    onSurface           = SparkColors.Ink,
    surfaceVariant      = SparkColors.Fog,
    onSurfaceVariant    = SparkColors.Dusk,
    outline             = SparkColors.Mist,
    error               = SparkColors.Error,
    onError             = SparkColors.White,
)

@Composable
fun SparkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SparkTypography,
        shapes = SparkShapes,
        content = content,
    )
}
