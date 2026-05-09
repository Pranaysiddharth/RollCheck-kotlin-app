package com.attendance.rollcheck.ui.screens.auth

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.attendance.rollcheck.R
import com.attendance.rollcheck.ui.theme.AbsentRed
import com.attendance.rollcheck.ui.theme.AccentBlue
import com.attendance.rollcheck.ui.theme.AppBackground
import com.attendance.rollcheck.ui.theme.BorderColor
import com.attendance.rollcheck.ui.theme.CardBackground
import com.attendance.rollcheck.ui.theme.PresentGreen
import com.attendance.rollcheck.ui.theme.TextPrimary
import com.attendance.rollcheck.ui.theme.TextSecondary

@Composable
fun PinScreen(
    savedPin: String?,
    onPinCreated: (String) -> Boolean,
    onPinVerified: () -> Unit,
    pinVerifier: (String) -> Boolean,
    disallowPinReuse: ((String) -> Boolean)? = null,
    disallowPinReuseMessage: String = "New PIN must be different from the current PIN.",
    screenTitle: String? = null,
    screenSubtitle: String? = null,
) {
    val isCreateMode = savedPin == null
    val pinLength = 6

    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isConfirming by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    var shakeState by remember { mutableStateOf(0) }
    val haptic = LocalHapticFeedback.current

    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(shakeState) {
        if (shakeState > 0) {
            shakeOffset.animateTo(0f, keyframes {
                durationMillis = 400
                0f at 0
                -10f at 50
                10f at 100
                -10f at 150
                10f at 200
                -6f at 250
                6f at 300
                0f at 400
            })
        }
    }

    val currentPin = if (isConfirming) confirmPin else pin
    fun triggerShake() {
        shakeState++
    }
    fun triggerErrorFeedback() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun submit() {
        if (isCreateMode) {
            if (!isConfirming) {
                if (pin.length == pinLength) isConfirming = true
                else {
                    errorMsg = "Enter all 6 digits"
                    triggerErrorFeedback()
                    triggerShake()
                }
            } else {
                if (confirmPin.length == pinLength) {
                    if (pin != confirmPin) {
                        errorMsg = "PINs don't match. Try again."
                        triggerErrorFeedback()
                        triggerShake()
                        confirmPin = ""
                    } else if (disallowPinReuse?.invoke(confirmPin) == true) {
                        errorMsg = disallowPinReuseMessage
                        triggerErrorFeedback()
                        triggerShake()
                        pin = ""
                        confirmPin = ""
                        isConfirming = false
                    } else {
                        onPinCreated(confirmPin)
                    }
                } else {
                    errorMsg = "Enter all 6 digits"
                    triggerErrorFeedback()
                    triggerShake()
                }
            }
        } else {
            if (pin.length == pinLength) {
                if (pinVerifier(pin)) onPinVerified()
                else {
                    errorMsg = "Incorrect PIN. Try again."
                    triggerErrorFeedback()
                    triggerShake()
                    pin = ""
                }
            } else {
                errorMsg = "Enter all 6 digits"
                triggerErrorFeedback()
                triggerShake()
            }
        }
    }

    fun onKey(key: String) {
        errorMsg = ""
        if (key == "del") {
            if (isConfirming) {
                if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
            } else {
                if (pin.isNotEmpty()) pin = pin.dropLast(1)
            }
            return
        }
        if (isConfirming) {
            if (confirmPin.length < pinLength) confirmPin += key
        } else {
            if (pin.length < pinLength) pin += key
        }
    }

    val screenW = LocalConfiguration.current.screenWidthDp
    val screenH = LocalConfiguration.current.screenHeightDp
    val hPad: Dp = if (screenW < 360) 32.dp else 48.dp
    val keyGap: Dp = if (screenW < 360) 12.dp else 16.dp
    val availW = screenW.dp - hPad * 2
    val keySize: Dp = ((availW - keyGap * 2) / 3).coerceIn(56.dp, 72.dp)
    val logoSize: Dp = if (screenH < 700) 52.dp else 64.dp
    val midSpacer: Dp = if (screenH < 700) 14.dp else 24.dp
    val dotSpacer: Dp = if (screenH < 700) 10.dp else 16.dp
    val blockerInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            // Consume taps outside PIN keys so touches never reach underlying screens.
            .clickable(
                interactionSource = blockerInteraction,
                indication = null,
                onClick = {}
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 380.dp)
                .padding(horizontal = hPad),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(logoSize)
                    .clip(RoundedCornerShape(18.dp))
                    .background(AccentBlue.copy(alpha = 0.15f))
                    .border(1.dp, AccentBlue.copy(alpha = 0.35f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "RollCheck",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = screenTitle ?: when {
                    isCreateMode && !isConfirming -> "Create PIN"
                    isCreateMode && isConfirming -> "Confirm PIN"
                    else -> "Enter PIN"
                },
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = screenSubtitle ?: when {
                    isCreateMode && !isConfirming -> "Set a 6-digit PIN to secure the app"
                    isCreateMode && isConfirming -> "Re-enter your PIN to confirm"
                    else -> "Enter your PIN to continue"
                },
                fontSize = 12.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(dotSpacer))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.offset(x = shakeOffset.value.dp)
            ) {
                repeat(pinLength) { index ->
                    val filled = index < currentPin.length
                    val dotColor by animateColorAsState(if (filled) Color.White else Color.Transparent, label = "dot_$index")
                    val borderColor by animateColorAsState(if (filled) Color.White else BorderColor, label = "border_$index")
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                            .border(1.5.dp, borderColor, CircleShape)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                errorMsg,
                fontSize = 12.sp,
                color = AbsentRed,
                textAlign = TextAlign.Center,
                lineHeight = 15.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 18.dp)
                    .padding(horizontal = 4.dp),
                maxLines = 2
            )
            Spacer(Modifier.height(midSpacer))

            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("del", "0", "enter")
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(keyGap),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                keys.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(keyGap)) {
                        row.forEach { key ->
                            when (key) {
                                "del" -> PinKey(key, keySize) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onKey("del")
                                }
                                "enter" -> PinKeyEnter(keySize) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    submit()
                                }
                                else -> PinKey(key, keySize) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onKey(key)
                                }
                            }
                        }
                    }
                }
            }

            if (isConfirming) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Go Back",
                    fontSize = 12.sp,
                    color = AccentBlue,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable {
                        isConfirming = false
                        confirmPin = ""
                        pin = ""
                        errorMsg = ""
                    }
                )
            }
        }
    }
}

@Composable
fun PinKey(key: String, size: Dp = 68.dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(CardBackground)
            .border(1.dp, BorderColor, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (key == "del") {
            Icon(
                Icons.AutoMirrored.Filled.Backspace,
                "Delete",
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text(key, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
    }
}

@Composable
fun PinKeyEnter(size: Dp = 68.dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(PresentGreen)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Check, "Enter", tint = Color.White, modifier = Modifier.size(24.dp))
    }
}
