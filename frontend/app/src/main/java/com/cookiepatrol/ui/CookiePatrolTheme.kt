package com.cookiepatrol.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = lightColorScheme(
    primary = Color(0xFF185C45),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2E7),
    onPrimaryContainer = Color(0xFF123B2D),
    background = Color(0xFFF7F9F6),
    onBackground = Color(0xFF192820),
    surface = Color(0xFFF7F9F6),
    onSurface = Color(0xFF192820),
    surfaceVariant = Color(0xFFE9EEE8),
    onSurfaceVariant = Color(0xFF384B40),
    outline = Color(0xFF65776B),
    error = Color(0xFFAC2424),
    errorContainer = Color(0xFFFFE8E3),
    onErrorContainer = Color(0xFF641919),
)

@Composable
fun CookiePatrolTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors) {
        Surface(color = MaterialTheme.colorScheme.background, content = content)
    }
}
