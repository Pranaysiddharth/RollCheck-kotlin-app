package com.attendance.rollcheck

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.attendance.rollcheck.data.local.prefs.PreferencesManager
import com.attendance.rollcheck.navigation.AppNavGraph
import com.attendance.rollcheck.ui.screens.auth.PinScreen
import com.attendance.rollcheck.ui.theme.AppBackground
import com.attendance.rollcheck.ui.theme.FontManager
import com.attendance.rollcheck.ui.theme.RollCheckTheme
import java.security.MessageDigest


class MainActivity : ComponentActivity() {

    companion object {
        var pinVerified by mutableStateOf(false)
            private set
        private var skipNextPauseLock: Boolean = false

        fun requestSkipNextPauseLock() {
            skipNextPauseLock = true
        }
    }

    private var securityOverlay: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        if (getSavedPinHash() != null) {
            pinVerified = false
            addSecurityOverlay()
        }


        setContent {
            val prefs = remember { PreferencesManager.getInstance(this@MainActivity) }
            val fontIndex by prefs.fontIndexFlow.collectAsState()
            val fontFamily: FontFamily = remember(fontIndex) { FontManager.fontFamilyFor(fontIndex) }

            val raw = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = raw.density,
                    fontScale = raw.fontScale.coerceAtMost(1.0f)
                )
            ) {
                RollCheckTheme(fontFamily = fontFamily) {
                    var hasSavedPin by remember { mutableStateOf(getSavedPinHash() != null) }

                    val onPinCreated: (String) -> Boolean = remember {
                        {
                            savePinHash(it)
                            hasSavedPin = true
                            pinVerified = true
                            // Automatic folder management now used
                            prefs.isFirstLaunchSetupDone = true
                            true
                        }
                    }

                    val onPinVerified = remember { { pinVerified = true } }
                    val pinVerifier: (String) -> Boolean = remember { { verifyPin(it) } }

                    Box {
                        if (hasSavedPin) {
                            AppNavGraph(
                                hasSavedPin = hasSavedPin,
                                onPinCreated = onPinCreated,
                                onPinVerified = onPinVerified,
                                pinVerifier = pinVerifier,
                                getSavedPinHash = { getSavedPinHash() },
                                verifyPin = { pin -> verifyPin(pin) },
                                savePinHash = { pin -> savePinHash(pin) }
                            )
                        }

                        if (!hasSavedPin || !pinVerified) {
                            PinScreen(
                                savedPin = if (hasSavedPin) "exists" else null,
                                onPinCreated = onPinCreated,
                                onPinVerified = onPinVerified,
                                pinVerifier = pinVerifier
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        removeSecurityOverlay()
        if (getSavedPinHash() == null) pinVerified = true
    }

    override fun onPause() {
        super.onPause()
        if (skipNextPauseLock) {
            skipNextPauseLock = false
            return
        }
        if (!isChangingConfigurations && getSavedPinHash() != null) {
            pinVerified = false
            addSecurityOverlay()
        }
    }

    private fun addSecurityOverlay() {
        if (securityOverlay != null) return
        val overlay = View(this).apply {
            setBackgroundColor(AppBackground.toArgb())
            isClickable = true
            isFocusable = true
        }
        (window.decorView as? ViewGroup)?.addView(
            overlay,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        securityOverlay = overlay
    }

    private fun removeSecurityOverlay() {
        securityOverlay?.let {
            (window.decorView as? ViewGroup)?.removeView(it)
            securityOverlay = null
        }
    }

    private fun hashPin(pin: String): String =
        MessageDigest.getInstance("SHA-256").digest(pin.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun getSavedPinHash(): String? = PreferencesManager.getInstance(this).pinHash

    private fun savePinHash(pin: String) {
        PreferencesManager.getInstance(this).pinHash = hashPin(pin)
    }

    private fun verifyPin(pin: String): Boolean = hashPin(pin) == getSavedPinHash()
}
