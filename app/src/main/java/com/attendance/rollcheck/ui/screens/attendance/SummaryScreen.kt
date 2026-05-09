package com.attendance.rollcheck.ui.screens.attendance

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.attendance.rollcheck.data.repository.AttendanceRepository
import com.attendance.rollcheck.data.work.AttendanceSaveWorker
import com.attendance.rollcheck.domain.manager.SessionManager
import com.attendance.rollcheck.domain.model.SessionAttendanceEntity
import com.attendance.rollcheck.domain.model.Student
import com.attendance.rollcheck.ui.components.cards.InfoCard
import com.attendance.rollcheck.ui.components.cards.StatCard
import com.attendance.rollcheck.ui.components.cards.StudentListCard
import com.attendance.rollcheck.ui.theme.AbsentRed
import com.attendance.rollcheck.ui.theme.AccentBlue
import com.attendance.rollcheck.ui.theme.AppBackground
import com.attendance.rollcheck.ui.theme.BorderColor
import com.attendance.rollcheck.ui.theme.CardBackground
import com.attendance.rollcheck.ui.theme.PresentGreen
import com.attendance.rollcheck.ui.theme.TextPrimary
import com.attendance.rollcheck.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    className: String = "Class",
    onUpdateAttendance: (success: Boolean) -> Unit,
    onExitToHome: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { AttendanceRepository.getInstance(context) }
    val sessionManager = remember { SessionManager.getInstance(context) }
    val workManager = remember { WorkManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var records by remember { mutableStateOf<List<SessionAttendanceEntity>>(emptyList()) }
    var localRecords by remember { mutableStateOf<List<SessionAttendanceEntity>>(emptyList()) }
    var displayClassName by remember { mutableStateOf(className) }
    var isLoading by remember { mutableStateOf(true) }
    var isStartingSave by remember { mutableStateOf(false) }
    var isExiting by remember { mutableStateOf(false) }
    var showingAbsent by remember { mutableStateOf(true) }
    var lastBackPressAt by remember { mutableLongStateOf(0L) }
    var trackedSaveWorkId by remember { mutableStateOf<String?>(null) }

    val saveWorkInfos by workManager
        .getWorkInfosForUniqueWorkLiveData(AttendanceSaveWorker.uniqueWorkName(className))
        .observeAsState(emptyList())
    val activeSaveInfo = saveWorkInfos.firstOrNull {
        it.state == WorkInfo.State.ENQUEUED ||
            it.state == WorkInfo.State.RUNNING ||
            it.state == WorkInfo.State.BLOCKED
    }
    val trackedSaveInfo = trackedSaveWorkId?.let { currentId ->
        saveWorkInfos.firstOrNull { it.id.toString() == currentId }
    }
    val isSaving = isStartingSave || activeSaveInfo != null

    suspend fun persistRoomBuffer() {
        localRecords.forEach { record ->
            if (record.status != -1) {
                    repo.markStudent(className, record.studentId, record.status)
            }
        }
    }

    LaunchedEffect(className) {
        sessionManager.setSummaryStage(className)
        val classInfo = repo.getClassById(className)
        displayClassName = classInfo?.className ?: className
        records = repo.getSessionAttendance(className)
        localRecords = records
        isLoading = false
    }

    LaunchedEffect(activeSaveInfo?.id) {
        if (trackedSaveWorkId == null && activeSaveInfo != null) {
            trackedSaveWorkId = activeSaveInfo.id.toString()
        }
    }

    LaunchedEffect(trackedSaveWorkId, trackedSaveInfo?.id, activeSaveInfo?.id) {
        if (isStartingSave && trackedSaveWorkId != null && (trackedSaveInfo != null || activeSaveInfo != null)) {
            isStartingSave = false
        }
    }

    LaunchedEffect(trackedSaveInfo?.id, trackedSaveInfo?.state) {
        val workInfo = trackedSaveInfo ?: return@LaunchedEffect
        if (!workInfo.state.isFinished) {
            isStartingSave = false
            return@LaunchedEffect
        }

        isStartingSave = false
        val success = workInfo.state == WorkInfo.State.SUCCEEDED
        trackedSaveWorkId = null
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onUpdateAttendance(success)
    }

    BackHandler(enabled = !isLoading && !isSaving && !isExiting) {
        val now = System.currentTimeMillis()
        if (now - lastBackPressAt < 1500L) {
            isExiting = true
            scope.launch {
                persistRoomBuffer()
                isExiting = false
                onExitToHome()
            }
        } else {
            lastBackPressAt = now
            Toast.makeText(context, "Press back again to go Home", Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler(enabled = isSaving) {
        Toast.makeText(context, "Wait for Excel to be written", Toast.LENGTH_SHORT).show()
    }

    val presentStudents = localRecords
        .filter { it.status == 1 }
        .map { Student(it.studentId, it.name) }
        .sortedBy { it.studentId }
    val absentStudents = localRecords
        .filter { it.status == 0 }
        .map { Student(it.studentId, it.name) }
        .sortedBy { it.studentId }

    val totalMarked = presentStudents.size + absentStudents.size
    val attendancePct = if (totalMarked > 0) (presentStudents.size.toFloat() / totalMarked * 100).toInt() else 0
    val dayDate = SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()).format(Date())

    fun markPresent(student: Student) {
        localRecords = localRecords.map { if (it.studentId == student.studentId) it.copy(status = 1) else it }
    }

    fun markAbsent(student: Student) {
        localRecords = localRecords.map { if (it.studentId == student.studentId) it.copy(status = 0) else it }
    }

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text("Attendance Summary", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentBlue)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .widthIn(max = 500.dp)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        item { Spacer(Modifier.height(8.dp)) }
                        item { InfoCard(Icons.Default.CalendarMonth, AccentBlue, "DAY & DATE", dayDate) }
                        item { Spacer(Modifier.height(8.dp)) }
                        item { InfoCard(Icons.Default.Book, AccentBlue, "CLASS", displayClassName) }
                        item { Spacer(Modifier.height(12.dp)) }
                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                StatCard("Present", presentStudents.size.toString(), PresentGreen, Modifier.weight(1f))
                                StatCard("Absent", absentStudents.size.toString(), AbsentRed, Modifier.weight(1f))
                                StatCard("Attendance", "$attendancePct%", AccentBlue, Modifier.weight(1f))
                            }
                        }
                        item { Spacer(Modifier.height(12.dp)) }
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(CardBackground)
                                    .border(1.dp, BorderColor, RoundedCornerShape(14.dp))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                SummaryTabButton("Marked Absent", Icons.Default.Cancel, showingAbsent, AbsentRed, Modifier.weight(1f)) {
                                    showingAbsent = true
                                }
                                SummaryTabButton("Marked Present", Icons.Default.CheckCircle, !showingAbsent, PresentGreen, Modifier.weight(1f)) {
                                    showingAbsent = false
                                }
                            }
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                        item {
                            if (showingAbsent) {
                                StudentListCard(
                                    title = "Marked Absent",
                                    icon = Icons.Default.Cancel,
                                    iconTint = AbsentRed,
                                    students = absentStudents,
                                    actionIcon = Icons.Default.CheckCircle,
                                    actionIconTint = PresentGreen,
                                    emptyIcon = Icons.Default.CheckCircle,
                                    emptyIconTint = PresentGreen,
                                    emptyMessage = "All students present!",
                                    emptyMessageColor = PresentGreen,
                                    onAction = { markPresent(it) }
                                )
                            } else {
                                StudentListCard(
                                    title = "Marked Present",
                                    icon = Icons.Default.CheckCircle,
                                    iconTint = PresentGreen,
                                    students = presentStudents,
                                    actionIcon = Icons.Default.Cancel,
                                    actionIconTint = AbsentRed,
                                    emptyIcon = Icons.Default.Cancel,
                                    emptyIconTint = AbsentRed,
                                    emptyMessage = "No students present!",
                                    emptyMessageColor = AbsentRed,
                                    onAction = { markAbsent(it) }
                                )
                            }
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            isStartingSave = true
                            scope.launch {
                                persistRoomBuffer()
                                trackedSaveWorkId = AttendanceSaveWorker.enqueue(context, className)
                            }
                        },
                        enabled = !isSaving && !isExiting,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Write to Excel", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(Modifier.height(16.dp))
                }

                if (isSaving) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(AppBackground.copy(alpha = 0.82f))
                            .clickable(enabled = true, indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {
                                Toast.makeText(context, "Wait for Excel to be written", Toast.LENGTH_SHORT).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = AccentBlue)
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "Wait for Excel to be written",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Please keep the app open until this finishes.",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryTabButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    activeColor: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) activeColor.copy(alpha = 0.18f) else Color.Transparent)
            .border(
                if (selected) 1.dp else 0.dp,
                if (selected) activeColor.copy(alpha = 0.5f) else Color.Transparent,
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = if (selected) activeColor else TextSecondary, modifier = Modifier.size(16.dp))
            Text(label, color = if (selected) activeColor else TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
