package com.attendance.rollcheck.ui.screens.session

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.attendance.rollcheck.data.local.prefs.PreferencesManager
import com.attendance.rollcheck.data.repository.AttendanceRepository
import com.attendance.rollcheck.ui.theme.AbsentRed
import com.attendance.rollcheck.ui.theme.AccentBlue
import com.attendance.rollcheck.ui.theme.AppBackground
import com.attendance.rollcheck.ui.theme.BorderColor
import com.attendance.rollcheck.ui.theme.CardBackground
import com.attendance.rollcheck.ui.theme.PresentGreen
import com.attendance.rollcheck.ui.theme.TextPrimary
import com.attendance.rollcheck.ui.theme.TextSecondary
import com.attendance.rollcheck.utils.adaptive.adaptiveSpacing
import com.attendance.rollcheck.utils.adaptive.responsiveHPad
import com.attendance.rollcheck.utils.tts.TtsManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionRecoveryScreen(
    classId: String,
    lastRoll: Int,
    markedCount: Int,
    isSummaryStage: Boolean,
    isMarkPresentStage: Boolean,
    isManualAttendanceStage: Boolean,
    onBack: () -> Unit,
    onContinueSession: () -> Unit,
    onViewSummary: () -> Unit,
    onEnterManual: () -> Unit,
    onDiscardSession: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { AttendanceRepository.getInstance(context) }
    val prefs = remember { PreferencesManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    var displayClassName by remember { mutableStateOf(classId) }
    var currentSessionNumber by remember { mutableIntStateOf(1) }
    var visible by remember { mutableStateOf(true) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    val hPad = responsiveHPad()

    LaunchedEffect(classId) {
        val classInfo = repo.getClassById(classId)
        displayClassName = classInfo?.className ?: "Class"
    }

    LaunchedEffect(classId) {
        currentSessionNumber = (repo.getTodaySessionCount(classId) + 1).coerceAtLeast(1)
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            containerColor = CardBackground,
            title = {
                Text(
                    "Discard Session?",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Attendance values are valuable. Discarding now will clear all progress and you will need to do the session again.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onDiscardSession()
                }) {
                    Text(
                        "Discard Session",
                        color = AbsentRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = { Text("Resume Session", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 500.dp)
                    .padding(padding)
                    .padding(horizontal = hPad),
                verticalArrangement = Arrangement.spacedBy(adaptiveSpacing(16.dp))
            ) {
                Spacer(Modifier.height(4.dp))

                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(220)) + slideInVertically(
                        animationSpec = tween(220),
                        initialOffsetY = { -20 }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(AccentBlue.copy(alpha = 0.08f))
                            .border(1.dp, AccentBlue.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(AccentBlue.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.History, null, tint = AccentBlue, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text("Previous unfinished session is detected", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AccentBlue)
                    }
                }

                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(260, delayMillis = 40)) + slideInVertically(
                        animationSpec = tween(260, delayMillis = 40),
                        initialOffsetY = { -20 }
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(CardBackground)
                            .border(1.dp, BorderColor, RoundedCornerShape(20.dp))
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(AccentBlue.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Book, null, tint = AccentBlue, modifier = Modifier.size(22.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("CLASS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.5.sp)
                                Spacer(Modifier.height(2.dp))
                                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                    Text(
                                        displayClassName,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        fontFamily = FontFamily.Monospace,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                        Text("SESSION STATUS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.5.sp)
                        RecoveryStatusBar(
                            sessionNumber = currentSessionNumber.toString(),
                            showLastMarked = !isMarkPresentStage && !isManualAttendanceStage,
                            lastMarked = if (markedCount > 0) String.format("%03d", lastRoll) else "-",
                            marked = markedCount.toString()
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(300, delayMillis = 120)) + slideInVertically(
                        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
                        initialOffsetY = { 40 }
                    )
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                if (!isSummaryStage && !isMarkPresentStage && !isManualAttendanceStage) {
                                    TtsManager.preWarmRollCall(
                                        context = context,
                                        speed = prefs.ttsSpeed,
                                        preferredVoiceName = prefs.ttsVoiceModel,
                                        enabled = prefs.isTtsEnabled
                                    )
                                }
                                if (isSummaryStage || isMarkPresentStage || isManualAttendanceStage) {
                                    onContinueSession()
                                } else {
                                    scope.launch {
                                        TtsManager.awaitRollCallReady(
                                            context = context,
                                            timeoutMs = 1200L,
                                            enabled = prefs.isTtsEnabled
                                        )
                                        onContinueSession()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when {
                                    isSummaryStage -> "Continue To Summary"
                                    isMarkPresentStage -> "Continue via MPS"
                                    isManualAttendanceStage -> "Enter Manual Mode"
                                    else -> "Continue Roll Call Session"
                                },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        if (!isSummaryStage && !isMarkPresentStage && !isManualAttendanceStage) {
                            OutlinedButton(
                                onClick = onEnterManual,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                                border = BorderStroke(1.dp, BorderColor),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Enter Manual Mode", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                            }
                        }
                        TextButton(
                            onClick = {
                                showDiscardDialog = true
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = AbsentRed)
                        ) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Discard Session", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RecoveryStatusBar(
    sessionNumber: String,
    showLastMarked: Boolean,
    lastMarked: String,
    marked: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(16.dp))
            .background(AccentBlue.copy(alpha = 0.04f))
            .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
    ) {
        if (showLastMarked) {
            RecoveryStatusCell("Session Number", sessionNumber, AccentBlue, Modifier.weight(1f))
            RecoverySeparator()
            RecoveryStatusCell("Last Marked", lastMarked, AccentBlue, Modifier.weight(1f))
            RecoverySeparator()
            RecoveryStatusCell("Marked", marked, PresentGreen, Modifier.weight(1f))
        } else {
            RecoveryStatusCell("Session Number", sessionNumber, AccentBlue, Modifier.weight(1f))
            RecoverySeparator()
            RecoveryStatusCell("Marked", marked, PresentGreen, Modifier.weight(1f))
        }
    }
}

@Composable
private fun RecoverySeparator() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .fillMaxSize()
            .background(BorderColor.copy(alpha = 0.9f))
    )
}

@Composable
private fun RecoveryStatusCell(label: String, value: String, valueColor: Color, modifier: Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 11.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            value,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            fontFamily = FontFamily.Monospace
        )
    }
}


