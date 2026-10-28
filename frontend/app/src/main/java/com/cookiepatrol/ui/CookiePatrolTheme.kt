package com.cookiepatrol.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Colors = lightColorScheme(
    primary = Color(0xFF005BC4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEAF2FF),
    onPrimaryContainer = Color(0xFF123B70),
    secondary = Color(0xFF247149),
    secondaryContainer = Color(0xFFEAF6EE),
    onSecondaryContainer = Color(0xFF1D593C),
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF1C1C1E),
    surface = Color.White,
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFE8E8ED),
    onSurfaceVariant = Color(0xFF595961),
    outline = Color(0xFF75757E),
    outlineVariant = Color(0xFFE5E5EA),
    error = Color(0xFFB42318),
    errorContainer = Color(0xFFFFEEEB),
    onErrorContainer = Color(0xFF85251C),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8EBBFF),
    onPrimary = Color(0xFF092F61),
    primaryContainer = Color(0xFF162B48),
    onPrimaryContainer = Color(0xFFD6E6FF),
    secondary = Color(0xFF8CD5A7),
    secondaryContainer = Color(0xFF173527),
    onSecondaryContainer = Color(0xFFB5EDC8),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF2F2F7),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFFB8B8C0),
    outline = Color(0xFF94949E),
    outlineVariant = Color(0xFF38383A),
    error = Color(0xFFFFB4A9),
    errorContainer = Color(0xFF44221F),
    onErrorContainer = Color(0xFFFFDAD4),
)

@Composable
fun CookiePatrolTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else Colors,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(26.dp),
            extraLarge = RoundedCornerShape(32.dp),
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
    }
}
