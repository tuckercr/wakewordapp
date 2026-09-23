package com.tuckercr.zamzam.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ColorScheme =
    lightColorScheme(
        primary = Color(0xFFB71C1C),
        onPrimary = Color.White,
        primaryContainer = Color(0xFF7F0000),
        onPrimaryContainer = Color.White,
        secondary = Color(0xFFF05545),
        onSecondary = Color.White,
        background = Color(0xFFE8E6D9),
        onBackground = Color(0xFF1A1A1A),
        surface = Color(0xFFE8E6D9),
        onSurface = Color(0xFF1A1A1A),
        surfaceVariant = Color(0xFFD6D3C6),
        onSurfaceVariant = Color(0xFF3A3A3A),
    )

@Composable
fun ZamZamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        content = content,
    )
}
