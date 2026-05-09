package com.attendance.rollcheck.ui.screens.classmanagement

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.attendance.rollcheck.data.local.prefs.PreferencesManager
import com.attendance.rollcheck.data.repository.AttendanceRepository
import com.attendance.rollcheck.domain.manager.ClassManager
import com.attendance.rollcheck.ui.screens.auth.PinKey
import com.attendance.rollcheck.ui.screens.auth.PinKeyEnter
import com.attendance.rollcheck.ui.theme.AbsentRed
import com.attendance.rollcheck.ui.theme.AccentBlue
import com.attendance.rollcheck.ui.theme.AppBackground
import com.attendance.rollcheck.ui.theme.BorderColor
import com.attendance.rollcheck.ui.theme.CardBackground
import com.attendance.rollcheck.ui.theme.PresentGreen
import com.attendance.rollcheck.ui.theme.TextPrimary
import com.attendance.rollcheck.ui.theme.TextSecondary
import com.attendance.rollcheck.utils.adaptive.responsiveHPad
import kotlinx.coroutines.launch

/**
 * PIN-protected class deletion screen.
 * User must enter the app PIN to confirm deletion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteClassScreen(
    classId: String,
    className: String,
    onBack: () -> Unit,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    val classManager = remember { ClassManager.getInstance(context) }
    val repo = remember { AttendanceRepository.getInstance(context) }
    val prefs = remember { PreferencesManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    val hPad = responsiveHPad()
    val haptic = LocalHapticFeedback.current
    val screenH = LocalConfiguration.current.screenHeightDp

    var pin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf(false) }

    val pinLength = 6

    fun handleKeyTap(action: () -> Unit) {
        if (deleting) return
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        action()
    }

    fun onKey(key: String) {
        errorMsg = ""
        if (key == "del") {
            if (pin.isNotEmpty()) pin = pin.dropLast(1)
            return
        }
        if (pin.length < pinLength) pin += key
    }

    fun onConfirm() {
        if (deleting) return
        if (pin.length < pinLength) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            errorMsg = "Enter all 6 digits"
            return
        }
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        deleting = true
        scope.launch {
            val enteredHash = repo.hashPin(pin)
            val success = classManager.deleteClass(classId, enteredHash, prefs.pinHash)
            deleting = false
            if (success) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onDeleted()
            } else {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                errorMsg = "Incorrect PIN"
                pin = ""
            }
        }
    }

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Delete Class",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !deleting) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 430.dp)
                    .padding(padding)
                    .padding(horizontal = hPad, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = AbsentRed.copy(alpha = 0.08f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        AbsentRed.copy(alpha = 0.35f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(AbsentRed.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = AbsentRed,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = className,
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 19.sp,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            )
                            Text(
                                text = classId,
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 14.sp,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            )
                            Text(
                                text = "This deletes the RollCheck class data and creates a backup in Export first.",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(if (screenH < 700) 18.dp else 28.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Security Check",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Enter your 6-digit PIN to continue.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(if (screenH < 700) 16.dp else 22.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        repeat(pinLength) { index ->
                            val filled = index < pin.length
                            val dotColor by animateColorAsState(
                                targetValue = if (filled) AbsentRed else Color.Transparent,
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                label = "delete_pin_dot_$index"
                            )
                            val ringColor by animateColorAsState(
                                targetValue = if (filled) AbsentRed else BorderColor,
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                label = "delete_pin_ring_$index"
                            )
                            val dotSize by animateDpAsState(
                                targetValue = if (filled) 13.dp else 11.dp,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                label = "delete_pin_size_$index"
                            )
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                                    .border(
                                        1.5.dp,
                                        ringColor,
                                        CircleShape
                                    )
                            ) {
                                if (filled) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(dotSize)
                                            .clip(CircleShape)
                                            .background(AbsentRed)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = errorMsg,
                        fontSize = 13.sp,
                        color = AbsentRed,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 18.dp)
                    )

                    Spacer(Modifier.height(if (screenH < 700) 18.dp else 24.dp))

                    val screenW = LocalConfiguration.current.screenWidthDp
                    val keyGap = if (screenW < 360) 12.dp else 16.dp
                    val availW = screenW.dp - hPad * 2
                    val keySize = ((availW - keyGap * 2) / 3).coerceIn(56.dp, 72.dp)
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
                                            handleKeyTap { onKey("del") }
                                        }
                                        "enter" -> PinKeyEnter(keySize) {
                                            handleKeyTap(::onConfirm)
                                        }
                                        else -> PinKey(key, keySize) {
                                            handleKeyTap { onKey(key) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (deleting) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(CardBackground)
                            .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        CircularProgressIndicator(color = AccentBlue, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        Text(
                            text = "Deleting class safely...",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Text(
                        text = "RollCheck will remove the class only after PIN confirmation.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }
    }
}

