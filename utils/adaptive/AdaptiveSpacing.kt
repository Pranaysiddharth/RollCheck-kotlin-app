package com.attendance.rollcheck.utils.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Returns horizontal screen padding scaled to screen width.
 * < 360dp   → 14dp  (Realme C-series, budget phones)
 * 360–399dp → 18dp  (Realme 9, Galaxy A-series, mid-range)
 * 400dp+    → 24dp  (Pixel, Galaxy S, Emulator default)
 */
@Composable
fun responsiveHPad(): Dp =
    when (LocalConfiguration.current.screenWidthDp) {
        in 0..359   -> 14.dp
        in 360..399 -> 18.dp
        else        -> 24.dp
    }

/**
 * Scales a base spacing value by BOTH screen height AND width.
 * minFraction / maxFraction clamp how aggressively it adapts.
 */
@Composable
fun adaptiveSpacing(base: Dp, minFraction: Float = 0.65f, maxFraction: Float = 1.15f): Dp {
    val config  = LocalConfiguration.current
    val hRatio  = (config.screenHeightDp.dp / 900.dp).coerceIn(minFraction, maxFraction)
    val wFactor = when {
        config.screenWidthDp < 360 -> 0.75f
        config.screenWidthDp < 390 -> 0.88f
        else                       -> 1.00f
    }
    return base * hRatio * wFactor
}

/** True when screen width >= 500dp (tablets, landscape phones). */
@Composable
fun isWideScreen(): Boolean =
    LocalConfiguration.current.screenWidthDp >= 500