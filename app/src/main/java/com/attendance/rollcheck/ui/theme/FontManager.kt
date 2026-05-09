package com.attendance.rollcheck.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.attendance.rollcheck.R

/**
 * App-wide font options.
 *
 * To enable JetBrains Mono:
 *   1. Place jetbrains_mono_regular.ttf + jetbrains_mono_bold.ttf in app/src/main/res/font/
 *   2. Uncomment the JetBrainsMono FontFamily lines below and replace FontFamily.Monospace.
 */
object FontManager {

    // ── Font definitions ──────────────────────────────────────────────────────

    private val JetBrainsMono = FontFamily(
        Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
        Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
        Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
    )

    val options: List<Pair<String, FontFamily>> = listOf(
        "Roboto"         to FontFamily.Default,
        "JetBrains Mono" to JetBrainsMono
    )

    fun fontFamilyFor(index: Int): FontFamily =
        options.getOrElse(index) { options[0] }.second

    fun labelFor(index: Int): String =
        options.getOrElse(index) { options[0] }.first
}

/** CompositionLocal that carries the current app-wide font family down the tree. */
val LocalAppFontFamily = compositionLocalOf<FontFamily> { FontFamily.Default }
