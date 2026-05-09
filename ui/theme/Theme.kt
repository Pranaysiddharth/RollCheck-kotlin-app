package com.attendance.rollcheck.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.font.FontFamily

private val DarkColorScheme = darkColorScheme(
    primary      = AccentBlue,
    background   = AppBackground,
    surface      = CardBackground,
    onPrimary    = TextPrimary,
    onBackground = TextPrimary,
    onSurface    = TextPrimary
)

@Composable
fun RollCheckTheme(
    fontFamily: FontFamily = FontFamily.Default,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalAppFontFamily provides fontFamily) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            typography  = buildTypography(fontFamily),
            content     = content
        )
    }
}