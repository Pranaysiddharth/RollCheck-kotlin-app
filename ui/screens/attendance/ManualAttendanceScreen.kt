package com.attendance.rollcheck.ui.screens.attendance

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.attendance.rollcheck.data.repository.AttendanceRepository
import com.attendance.rollcheck.domain.manager.ClassManager
import com.attendance.rollcheck.domain.manager.SessionManager
import com.attendance.rollcheck.domain.model.AttendanceStatus
import com.attendance.rollcheck.domain.model.Student
import com.attendance.rollcheck.ui.components.dialogs.ExitSessionDialog
import com.attendance.rollcheck.ui.theme.AbsentRed
import com.attendance.rollcheck.ui.theme.AccentBlue
import com.attendance.rollcheck.ui.theme.AppBackground
import com.attendance.rollcheck.ui.theme.BorderColor
import com.attendance.rollcheck.ui.theme.CardBackground
import com.attendance.rollcheck.ui.theme.PresentGreen
import com.attendance.rollcheck.ui.theme.TextPrimary
import com.attendance.rollcheck.ui.theme.TextSecondary
import com.attendance.rollcheck.ui.theme.WarningYellow
import com.attendance.rollcheck.utils.adaptive.adaptiveSpacing
import com.attendance.rollcheck.utils.adaptive.responsiveHPad
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualAttendanceScreen(
    className: String,
    students: List<Student>,
    isLoading: Boolean = false,
    onBack: () -> Unit,
    onExitHomeAfterNoChanges: (lastRoll: Int, markedCount: Int) -> Unit,
    onBackToRecovery: () -> Unit,
    onFinish: (presentRolls: Set<String>, absentRolls: Set<String>) -> Unit,
    showDiscardOnBack: Boolean = true
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager.getInstance(context) }
    val classManager = remember { ClassManager.getInstance(context) }
    val repo = remember { AttendanceRepository.getInstance(context) }
    val scope = rememberCoroutineScope()
    val hPad = responsiveHPad()

    var showBackDialog by remember { mutableStateOf(false) }
    var displayClassName by remember { mutableStateOf("Class") }
    var displayRollPrefix by remember { mutableStateOf("-") }
    var lastMarkedLabel by remember { mutableStateOf("---") }
    var hasManualChanges by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isResettingAll by remember { mutableStateOf(false) }
    var isBaselineReady by remember { mutableStateOf(false) }
    var rollCallAnchorLastRoll by remember { mutableIntStateOf(1) }
    var manualBaselineIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    BackHandler { showBackDialog = true }

    val statusMap = remember(students) {
        mutableStateMapOf<String, AttendanceStatus>().also { map ->
            students.forEach { map[it.studentId] = AttendanceStatus.UNMARKED }
        }
    }

    fun extractRollInt(studentId: String): Int =
        studentId.trim().takeLastWhile { it.isDigit() }.toIntOrNull() ?: 0

    fun formatRoll(studentId: String): String {
        val digits = studentId.trim().takeLastWhile { it.isDigit() }
        val number = digits.toIntOrNull()
        return when {
            number != null && number in 0..999 -> String.format("%03d", number)
            digits.isNotEmpty() -> digits
            else -> studentId
        }
    }

    fun currentMarkedCount(): Int = statusMap.values.count { it != AttendanceStatus.UNMARKED }

    fun currentPendingBaselineCount(): Int =
        manualBaselineIds.count { studentId ->
            (statusMap[studentId] ?: AttendanceStatus.UNMARKED) == AttendanceStatus.UNMARKED
        }

    fun currentManualMarkedCount(): Int =
        manualBaselineIds.count { studentId ->
            (statusMap[studentId] ?: AttendanceStatus.UNMARKED) != AttendanceStatus.UNMARKED
        }

    suspend fun persistBaselineStatuses() {
        for (studentId in manualBaselineIds) {
            val status = statusMap[studentId] ?: AttendanceStatus.UNMARKED
            sessionManager.markStudent(
                className,
                studentId,
                when (status) {
                    AttendanceStatus.PRESENT -> 1
                    AttendanceStatus.ABSENT -> 0
                    AttendanceStatus.UNMARKED -> -1
                }
            )
        }
    }

    fun rememberProgress(studentId: String) {
        lastMarkedLabel = formatRoll(studentId)
        scope.launch {
            sessionManager.updateProgress(className, rollCallAnchorLastRoll, currentMarkedCount())
        }
    }

    fun applyStatus(studentId: String, status: AttendanceStatus) {
        statusMap[studentId] = status
        hasManualChanges = true
        rememberProgress(studentId)
        scope.launch {
            sessionManager.markStudent(
                className,
                studentId,
                when (status) {
                    AttendanceStatus.PRESENT -> 1
                    AttendanceStatus.ABSENT -> 0
                    AttendanceStatus.UNMARKED -> -1
                }
            )
        }
    }

    LaunchedEffect(className) {
        val classInfo = classManager.getClassById(className)
        displayClassName = classInfo?.className ?: "Class"
        displayRollPrefix = classInfo?.rollPrefix?.ifBlank { "-" } ?: "-"
    }

    LaunchedEffect(students, className) {
        if (students.isEmpty()) {
            isBaselineReady = false
            return@LaunchedEffect
        }

        isBaselineReady = false
        val sessionAttendance = sessionManager.getSessionAttendance(className)
        sessionAttendance.forEach { record ->
            statusMap[record.studentId] = when (record.status) {
                1 -> AttendanceStatus.PRESENT
                0 -> AttendanceStatus.ABSENT
                else -> AttendanceStatus.UNMARKED
            }
        }

        val activeRoll = sessionManager.getActiveSession(className)?.lastRoll ?: 0
        val savedBaseline = repo.getManualRecoveryBaseline(className)
        val resolvedBaseline = savedBaseline
            ?.filterTo(linkedSetOf()) { studentId -> extractRollInt(studentId) > activeRoll }
            ?: sessionAttendance
                .filter { it.status == -1 }
                .map { it.studentId }
                .toSet()
                .also { repo.saveManualRecoveryBaseline(className, it) }

        val currentStudentIds = students.map { it.studentId }.toSet()
        val normalizedBaseline = resolvedBaseline.intersect(currentStudentIds)
        if (normalizedBaseline != resolvedBaseline) {
            repo.saveManualRecoveryBaseline(className, normalizedBaseline)
        }
        manualBaselineIds = normalizedBaseline

        rollCallAnchorLastRoll = activeRoll.coerceAtLeast(1)
        val activeStudentId = students.firstOrNull { extractRollInt(it.studentId) == activeRoll }?.studentId
        if (activeStudentId != null) {
            lastMarkedLabel = formatRoll(activeStudentId)
        }
        isBaselineReady = true
    }

    if (showBackDialog) {
        ExitSessionDialog(
            onDismiss = { showBackDialog = false },
            onSave = {
                scope.launch {
                    persistBaselineStatuses()
                    if (currentManualMarkedCount() == 0) {
                        sessionManager.setAttendanceStage(className)
                        showBackDialog = false
                        onExitHomeAfterNoChanges(
                            rollCallAnchorLastRoll,
                            currentMarkedCount()
                        )
                        return@launch
                    }
                    if (hasManualChanges) {
                        sessionManager.updateProgress(className, rollCallAnchorLastRoll, currentMarkedCount())
                    }
                    sessionManager.setManualAttendanceStage(className)
                    showBackDialog = false
                    onBack()
                }
            },
            onDiscard = {
                scope.launch {
                    sessionManager.discardSession(className)
                    showBackDialog = false
                    onBack()
                }
            },
            showDiscard = showDiscardOnBack,
            requireDiscardConfirmation = hasManualChanges
        )
    }

    val presentCount by remember { derivedStateOf { statusMap.values.count { it == AttendanceStatus.PRESENT } } }
    val absentCount by remember { derivedStateOf { statusMap.values.count { it == AttendanceStatus.ABSENT } } }
    val pendingCount by remember {
        derivedStateOf {
            manualBaselineIds.count { id -> statusMap[id] == AttendanceStatus.UNMARKED }
        }
    }
    val baselineStudents by remember(students, pendingCount, presentCount, absentCount) {
        derivedStateOf {
            students
                .filter { manualBaselineIds.contains(it.studentId) }
                .sortedBy { extractRollInt(it.studentId) }
        }
    }
    val baselineMarkedCount by remember {
        derivedStateOf {
            manualBaselineIds.count { id ->
                (statusMap[id] ?: AttendanceStatus.UNMARKED) != AttendanceStatus.UNMARKED
            }
        }
    }
    val allListedStudentsUnmarked by remember(baselineStudents, pendingCount) {
        derivedStateOf {
            baselineStudents.isNotEmpty() &&
                baselineStudents.all { student ->
                    (statusMap[student.studentId] ?: AttendanceStatus.UNMARKED) == AttendanceStatus.UNMARKED
                }
        }
    }
    val actionsEnabled by remember(isLoading, isBaselineReady) { derivedStateOf { !isLoading && isBaselineReady } }

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Manual Attendance",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { showBackDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextSecondary)
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
                    .widthIn(max = 520.dp)
                    .padding(padding)
                    .padding(horizontal = hPad)
            ) {
                Spacer(Modifier.height(4.dp))
                ManualClassMetaTable(className = displayClassName, rollPrefix = displayRollPrefix)
                Spacer(Modifier.height(adaptiveSpacing(12.dp)))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BulkActionButton(
                        label = "first recovery",
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        color = AccentBlue,
                        modifier = Modifier.weight(1f),
                        enabled = actionsEnabled && allListedStudentsUnmarked && !isResettingAll
                    ) {
                        scope.launch {
                            persistBaselineStatuses()
                            onBackToRecovery()
                        }
                    }
                    BulkActionButton(
                        label = "Unmark All",
                        icon = Icons.Default.Delete,
                        color = WarningYellow,
                        modifier = Modifier.weight(1f),
                        enabled = actionsEnabled && manualBaselineIds.isNotEmpty() && !isResettingAll
                    ) {
                        isResettingAll = true
                        manualBaselineIds.forEach { studentId -> statusMap[studentId] = AttendanceStatus.UNMARKED }
                        hasManualChanges = true
                        lastMarkedLabel = "---"
                        scope.launch {
                            try {
                                for (studentId in manualBaselineIds) {
                                    sessionManager.markStudent(className, studentId, -1)
                                }
                                sessionManager.updateProgress(className, rollCallAnchorLastRoll, currentMarkedCount())
                            } finally {
                                isResettingAll = false
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BulkActionButton(
                        label = "Mark All Present",
                        icon = Icons.Default.DoneAll,
                        color = PresentGreen,
                        modifier = Modifier.weight(1f),
                        enabled = actionsEnabled
                    ) {
                        baselineStudents.forEach { applyStatus(it.studentId, AttendanceStatus.PRESENT) }
                        baselineStudents.lastOrNull()?.studentId?.let { rememberProgress(it) }
                    }
                    BulkActionButton(
                        label = "Mark All Absent",
                        icon = Icons.Default.RemoveCircle,
                        color = AbsentRed,
                        modifier = Modifier.weight(1f),
                        enabled = actionsEnabled
                    ) {
                        baselineStudents.forEach { applyStatus(it.studentId, AttendanceStatus.ABSENT) }
                        baselineStudents.lastOrNull()?.studentId?.let { rememberProgress(it) }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(CardBackground)
                        .border(1.dp, BorderColor, RoundedCornerShape(20.dp))
                ) {
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = AccentBlue, strokeWidth = 2.dp)
                        }
                    } else if (baselineStudents.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No remaining students from initial recovery.",
                                color = TextSecondary,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                            items(baselineStudents, key = { it.studentId }) { student ->
                                ManualStudentRow(
                                    student = student,
                                    rollLabel = formatRoll(student.studentId),
                                    currentStatus = statusMap[student.studentId] ?: AttendanceStatus.UNMARKED,
                                    onMarkPresent = { applyStatus(student.studentId, AttendanceStatus.PRESENT) },
                                    onMarkAbsent = { applyStatus(student.studentId, AttendanceStatus.ABSENT) }
                                )
                                if (student != baselineStudents.lastOrNull()) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = BorderColor
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        val currentPending = currentPendingBaselineCount()
                        if (currentPending > 0) {
                            Toast.makeText(
                                context,
                                "Mark remaining $currentPending students also to continue",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@Button
                        }
                        isSaving = true
                        scope.launch {
                            persistBaselineStatuses()
                            val present = statusMap.filterValues { it == AttendanceStatus.PRESENT }.keys.toSet()
                            val absent = statusMap.filterValues { it == AttendanceStatus.ABSENT }.keys.toSet()
                            if (hasManualChanges) {
                                sessionManager.updateProgress(className, rollCallAnchorLastRoll, currentMarkedCount())
                            }
                            isSaving = false
                            onFinish(present, absent)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !isSaving && actionsEnabled && !isResettingAll
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Summarize & Save", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ManualClassMetaTable(className: String, rollPrefix: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground)
            .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .weight(0.34f)
                .fillMaxHeight()
        ) {
            ManualMetaLabelCell("Class")
            HorizontalDivider(color = BorderColor, thickness = 1.dp)
            ManualMetaLabelCell("Roll Prefix")
        }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(BorderColor.copy(alpha = 0.9f))
        )
        Column(
            modifier = Modifier
                .weight(0.66f)
                .fillMaxHeight()
        ) {
            ManualMetaValueCell(className, AccentBlue)
            HorizontalDivider(color = BorderColor, thickness = 1.dp)
            ManualMetaValueCell(rollPrefix, TextPrimary)
        }
    }
}

@Composable
private fun ManualMetaLabelCell(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 13.sp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ManualMetaValueCell(value: String, color: Color) {
    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(modifier = Modifier.horizontalScroll(scrollState)) {
            Text(
                value,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                fontFamily = FontFamily.Monospace,
                softWrap = false,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ManualStudentRow(
    student: Student,
    rollLabel: String,
    currentStatus: AttendanceStatus,
    onMarkPresent: () -> Unit,
    onMarkAbsent: () -> Unit
) {
    val nameScrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 54.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(BorderColor)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                rollLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AppBackground.copy(alpha = 0.30f))
                .border(1.dp, BorderColor.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp)
                .clipToBounds(),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(nameScrollState),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    student.name,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    softWrap = false
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Row(
            modifier = Modifier.width(76.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusActionButton(
                label = "P",
                isActive = currentStatus == AttendanceStatus.PRESENT,
                activeColor = PresentGreen,
                onClick = onMarkPresent
            )
            StatusActionButton(
                label = "A",
                isActive = currentStatus == AttendanceStatus.ABSENT,
                activeColor = AbsentRed,
                onClick = onMarkAbsent
            )
        }
    }
}

@Composable
private fun StatusActionButton(
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    val inactiveBackground = Color.Black
    val inactiveBorder = BorderColor.copy(alpha = 0.9f)
    val inactiveText = Color.White
    val bgColor by animateColorAsState(
        if (isActive) activeColor.copy(alpha = 0.2f) else inactiveBackground,
        tween(150),
        label = "manual_bg_" + label
    )
    val borderColor by animateColorAsState(
        if (isActive) activeColor.copy(alpha = 0.7f) else inactiveBorder,
        tween(150),
        label = "manual_border_" + label
    )
    val textColor by animateColorAsState(
        if (isActive) activeColor else inactiveText,
        tween(150),
        label = "manual_text_" + label
    )
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor)
    }
}

@Composable
private fun BulkActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = if (enabled) 0.10f else 0.05f))
            .border(1.dp, color.copy(alpha = if (enabled) 0.30f else 0.15f), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        val contentColor = if (enabled) color else color.copy(alpha = 0.45f)
        Icon(icon, null, tint = contentColor, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = contentColor, textAlign = TextAlign.Center)
    }
}
