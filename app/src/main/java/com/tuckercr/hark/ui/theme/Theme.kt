package com.tuckercr.hark.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Green = Color(0xFF1BBC68)
private val GreenBright = Color(0xFF18E699)
private val GreenDim = Color(0xFF148526)
private val Background = Color(0xFF0D1117)
private val Surface = Color(0xFF161B22)
private val SurfaceVariant = Color(0xFF1C2128)
private val TextPrimary = Color(0xFFE6EDF3)
private val TextMuted = Color(0xFF8B949E)
private val Border = Color(0xFF30363D)
private val ErrorRed = Color(0xFFF85149)

private val ColorScheme =
    darkColorScheme(
        primary = Green,
        onPrimary = Background,
        primaryContainer = GreenDim,
        onPrimaryContainer = TextPrimary,
        secondary = GreenBright,
        onSecondary = Background,
        secondaryContainer = Color(0xFF0D3320),
        onSecondaryContainer = GreenBright,
        background = Background,
        onBackground = TextPrimary,
        surface = Surface,
        onSurface = TextPrimary,
        surfaceVariant = SurfaceVariant,
        onSurfaceVariant = TextMuted,
        outline = Border,
        outlineVariant = Color(0xFF21262D),
        error = ErrorRed,
        onError = Background,
    )

private val HarkTypography =
    Typography(
        displayLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 57.sp),
        displayMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 45.sp),
        displaySmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 36.sp),
        headlineLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 32.sp),
        headlineMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
        headlineSmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
        titleLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
        titleMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 16.sp),
        titleSmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 14.sp),
        bodyLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp),
        bodyMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
        bodySmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
        labelLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 14.sp),
        labelMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 12.sp),
        labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 11.sp),
    )

@Composable
fun HarkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        typography = HarkTypography,
        content = content,
    )
}
