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
    primary = Color(0xFFF3C685),
    onPrimary = Color(0xFF332514),
    primaryContainer = Color(0xFFFFF3E2),
    onPrimaryContainer = Color(0xFF6B4B23),
    secondary = Color(0xFF514846),
    secondaryContainer = Color(0xFFF1EDEB),
    onSecondaryContainer = Color(0xFF302A28),
    background = Color(0xFFF5F5F4),
    onBackground = Color(0xFF262222),
    surface = Color.White,
    onSurface = Color(0xFF262222),
    surfaceVariant = Color(0xFFF0EEEC),
    onSurfaceVariant = Color(0xFF625957),
    outline = Color(0xFF7B706D),
    outlineVariant = Color(0xFFECE8E6),
    error = Color(0xFFAD342D),
    errorContainer = Color(0xFFFFEFED),
    onErrorContainer = Color(0xFF762721),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF3C685),
    onPrimary = Color(0xFF332514),
    primaryContainer = Color(0xFF45351F),
    onPrimaryContainer = Color(0xFFFFDFB0),
    secondary = Color(0xFFD5C9C5),
    secondaryContainer = Color(0xFF38312F),
    onSecondaryContainer = Color(0xFFF1EDEB),
    background = Color(0xFF171515),
    onBackground = Color(0xFFF7F3F1),
    surface = Color(0xFF242020),
    onSurface = Color(0xFFF7F3F1),
    surfaceVariant = Color(0xFF332D2B),
    onSurfaceVariant = Color(0xFFC6BCB8),
    outline = Color(0xFFA99A94),
    outlineVariant = Color(0xFF423937),
    error = Color(0xFFFFB4AC),
    errorContainer = Color(0xFF492826),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun CookiePatrolTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else Colors,
        typography = CookiePatrolTypography,
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
