package com.tuckercr.hark.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tuckercr.hark.R

@Composable
private fun harkColorScheme(): ColorScheme {
    val green = colorResource(R.color.hark_green)
    val greenBright = colorResource(R.color.hark_green_bright)
    val background = colorResource(R.color.hark_background)
    val textPrimary = colorResource(R.color.hark_text_primary)
    return darkColorScheme(
        primary = green,
        onPrimary = background,
        primaryContainer = colorResource(R.color.hark_green_dim),
        onPrimaryContainer = textPrimary,
        secondary = greenBright,
        onSecondary = background,
        secondaryContainer = colorResource(R.color.hark_green_container),
        onSecondaryContainer = greenBright,
        background = background,
        onBackground = textPrimary,
        surface = colorResource(R.color.hark_surface),
        onSurface = textPrimary,
        surfaceVariant = colorResource(R.color.hark_surface_variant),
        onSurfaceVariant = colorResource(R.color.hark_text_muted),
        outline = colorResource(R.color.hark_border),
        outlineVariant = colorResource(R.color.hark_border_subtle),
        error = colorResource(R.color.hark_error),
        onError = background,
    )
}

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
        colorScheme = harkColorScheme(),
        typography = HarkTypography,
        content = content,
    )
}
