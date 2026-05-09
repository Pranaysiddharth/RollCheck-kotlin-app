package com.attendance.rollcheck.ui.screens.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.attendance.rollcheck.domain.manager.ClassManager
import com.attendance.rollcheck.domain.manager.SessionManager
import com.attendance.rollcheck.data.local.prefs.PreferencesManager
import com.attendance.rollcheck.ui.theme.AccentBlue
import com.attendance.rollcheck.ui.theme.AppBackground
import com.attendance.rollcheck.ui.theme.BorderColor
import com.attendance.rollcheck.ui.theme.CardBackground
import com.attendance.rollcheck.ui.theme.TextPrimary
import com.attendance.rollcheck.ui.theme.TextSecondary
import com.attendance.rollcheck.ui.theme.WarningYellow
import com.attendance.rollcheck.utils.adaptive.responsiveHPad
import com.attendance.rollcheck.utils.tts.TtsManager
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionStartScreen(
    className: String,
    onBack: () -> Unit,
    onStartSession: (startRoll: Int, endRoll: Int) -> Unit,
    onMarkPresentStudents: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val classManager = remember { ClassManager.getInstance(context) }
    val sessionManager = remember { SessionManager.getInstance(context) }
    val prefs = remember { PreferencesManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    val hPad = responsiveHPad()

    var displayClassName by remember { mutableStateOf("") }
    var studentCount by remember { mutableIntStateOf(0) }
    var activeStudentCount by remember { mutableIntStateOf(0) }
    var rollPrefix by remember { mutableStateOf("") }
    var rollRange by remember { mutableStateOf(Pair(0, 0)) }
    var todaySessionCount by remember { mutableIntStateOf(0) }
    var isRangeLoading by remember { mutableStateOf(true) }
    var isTodayCountLoading by remember { mutableStateOf(true) }
    var showExitDialog by remember { mutableStateOf(false) }
    var continuousSessionCount by rememberSaveable { mutableIntStateOf(1) }
    var showContinuousSessionMenu by remember { mutableStateOf(false) }

    BackHandler { showExitDialog = true }

    LaunchedEffect(className) {
        coroutineScope {
            // Fast path: DB-backed values first.
            val classInfoDeferred = async { classManager.getClassById(className) }
            val activeCountDeferred = async { classManager.getActiveStudentCount(className) }

            activeStudentCount = activeCountDeferred.await()
            classInfoDeferred.await()?.let { classInfo ->
                displayClassName = classInfo.className
                studentCount = classInfo.studentCount
                rollPrefix = classInfo.rollPrefix
            }

            // Heavy Excel reads: load in background after screen is already visible.
            launch {
                isRangeLoading = true
                isTodayCountLoading = true
                val (range, todayCount) = classManager.getSessionStartMeta(className)
                rollRange = range
                todaySessionCount = todayCount
                isRangeLoading = false
                isTodayCountLoading = false
            }
        }
    }

    val dayText = remember { SimpleDateFormat("EEEE", Locale.getDefault()).format(Date()) }
    val dateText = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date()) }

    fun startSession() {
        val (start, end) = rollRange
        if (end <= 0) return
        scope.launch {
            TtsManager.preWarmRollCall(
                context = context,
                speed = prefs.ttsSpeed,
                preferredVoiceName = prefs.ttsVoiceModel,
                enabled = prefs.isTtsEnabled
            )
            TtsManager.awaitRollCallReady(
                context = context,
                timeoutMs = 1200L,
                enabled = prefs.isTtsEnabled
            )
            sessionManager.startSession(className, start, end, continuousSessionCount)
            onStartSession(start, end)
        }
    }

    fun markPresentStudents() {
        val (start, end) = rollRange
        if (end <= 0) return
        scope.launch {
            sessionManager.startSession(className, start, end, continuousSessionCount)
            sessionManager.setMarkPresentStage(className)
            onMarkPresentStudents()
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            containerColor = CardBackground,
            title = { Text("Exit session setup?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Continue keeps this screen open. Exit returns to Home.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Continue", color = AccentBlue, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    onBack()
                }) {
                    Text("Exit", color = TextSecondary)
                }
            }
        )
    }

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
            title = { Text("Start Session", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPrimary) },
                navigationIcon = { IconButton(onClick = { showExitDialog = true }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextSecondary) } },
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
                    .padding(horizontal = hPad)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardBackground)
                        .border(1.dp, BorderColor, RoundedCornerShape(14.dp))
                ) {
                    InfoTableRow(
                        Icons.Default.Book,
                        AccentBlue,
                        "CLASS",
                        if (displayClassName.isBlank()) "Loading..." else displayClassName,
                        monospace = true
                    )
                    HorizontalDivider(color = BorderColor)
                    InfoTableRow(Icons.Default.Groups, AccentBlue, "ACTIVE STUDENTS", activeStudentCount.toString())
                    if (rollPrefix.isNotEmpty()) {
                        HorizontalDivider(color = BorderColor)
                        InfoTableRow(Icons.Default.Label, AccentBlue, "ROLL PREFIX", rollPrefix, monospace = true)
                    }
                    HorizontalDivider(color = BorderColor)
                    val rangeText = when {
                        isRangeLoading -> "Loading..."
                        rollRange.first > 0 -> "${rollRange.first} - ${rollRange.second}"
                        else -> "1 - $studentCount"
                    }
                    InfoTableRow(Icons.Default.FormatListNumbered, AccentBlue, "ROLL NO RANGE", rangeText)
                    HorizontalDivider(color = BorderColor)
                    InfoTableRow(Icons.Default.Event, AccentBlue, "DAY & DATE", "$dayText, $dateText")

                    HorizontalDivider(color = BorderColor)
                    InfoTableRow(
                        icon = Icons.Default.History,
                        iconTint = AccentBlue,
                        label = "PREVIOUS SESSIONS TODAY",
                        value = if (isTodayCountLoading) "Loading..." else todaySessionCount.toString()
                    )
                    HorizontalDivider(color = BorderColor)
                    Box {
                        InfoTableDropdownRow(
                            icon = Icons.Default.FormatListNumbered,
                            iconTint = AccentBlue,
                            label = "CONTINUOUS SESSIONS",
                            value = continuousSessionCount.toString(),
                            onClick = { showContinuousSessionMenu = true }
                        )
                        DropdownMenu(
                            expanded = showContinuousSessionMenu,
                            onDismissRequest = { showContinuousSessionMenu = false },
                            containerColor = CardBackground,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, BorderColor),
                            modifier = Modifier.width(96.dp)
                        ) {
                            (1..5).forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            option.toString(),
                                            color = if (continuousSessionCount == option) AccentBlue else TextPrimary,
                                            fontWeight = if (continuousSessionCount == option) FontWeight.Bold else FontWeight.SemiBold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    },
                                    trailingIcon = {
                                        if (continuousSessionCount == option) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = AccentBlue,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    },
                                    onClick = {
                                        continuousSessionCount = option
                                        showContinuousSessionMenu = false
                                    },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                )
                                if (option < 5) {
                                    HorizontalDivider(
                                        color = BorderColor,
                                        thickness = 1.dp,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(WarningYellow.copy(alpha = 0.06f))
                        .border(1.dp, WarningYellow.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = WarningYellow, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Important", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }

                    val warnings = listOf(
                        "Attendance is automatically saved during the session",
                        "If the app closes, you can resume from where you left off",
                        "Final save will permanently update the Excel file"
                    )

                    warnings.forEach { warning ->
                        Row(modifier = Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(4.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(TextSecondary)
                            )
                            Text(warning, fontSize = 13.sp, color = TextSecondary)
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                val canStart = !isRangeLoading && rollRange.second > 0

                Button(
                    onClick = ::startSession,
                    enabled = canStart,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Start Roll Call Session", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                OutlinedButton(
                    onClick = ::markPresentStudents,
                    enabled = canStart,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = com.attendance.rollcheck.ui.theme.PresentGreen),
                    border = BorderStroke(1.dp, com.attendance.rollcheck.ui.theme.PresentGreen.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = com.attendance.rollcheck.ui.theme.PresentGreen, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Mark Present Students", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun InfoTableRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    label: String,
    value: String,
    monospace: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.sp)
            Text(
                value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default
            )
        }
    }
}

@Composable
private fun InfoTableDropdownRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.sp)
            Text(
                value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontFamily = FontFamily.Monospace
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(AccentBlue.copy(alpha = 0.10f))
                .border(1.dp, AccentBlue.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
        ) {
            IconButton(onClick = onClick) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "Choose continuous sessions",
                    tint = AccentBlue
                )
            }
        }
    }
}





