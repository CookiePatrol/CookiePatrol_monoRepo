package com.cookiepatrol.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.cookiepatrol.R

// Real font files for each weight/style avoid synthetic bold or slanted text.
private val AppFontFamily = FontFamily(
    Font(R.font.levelup_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.levelup_semibold, FontWeight.SemiBold, FontStyle.Normal),
    Font(R.font.levelup_bold, FontWeight.Bold, FontStyle.Normal),
    Font(R.font.levelup_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.levelup_semibold_italic, FontWeight.SemiBold, FontStyle.Italic),
    Font(R.font.levelup_bold_italic, FontWeight.Bold, FontStyle.Italic),
)

val CookiePatrolTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.Bold),
        displayMedium = displayMedium.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.Bold),
        displaySmall = displaySmall.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.Bold),
        headlineLarge = headlineLarge.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(fontFamily = AppFontFamily),
        bodyMedium = bodyMedium.copy(fontFamily = AppFontFamily),
        bodySmall = bodySmall.copy(fontFamily = AppFontFamily),
        labelLarge = labelLarge.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold),
        labelSmall = labelSmall.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold),
    )
}
