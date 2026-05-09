package com.attendance.rollcheck.ui.screens.attendance

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.attendance.rollcheck.domain.model.Student
import com.attendance.rollcheck.ui.components.cards.StatCard
import com.attendance.rollcheck.ui.components.dialogs.ExitSessionDialog
import com.attendance.rollcheck.ui.theme.*
import com.attendance.rollcheck.utils.adaptive.responsiveHPad
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkPresentScreen(
    classId: String,
    onBack: () -> Unit,
    onFinish: (presentRolls: Set<String>, absentRolls: Set<String>) -> Unit,
    students: List<Student> = emptyList(),
    showDiscardOnBack: Boolean = true
) {
    val context        = LocalContext.current
    val classManager   = remember { com.attendance.rollcheck.domain.manager.ClassManager.getInstance(context) }
    val sessionManager = remember { com.attendance.rollcheck.domain.manager.SessionManager.getInstance(context) }
    val scope          = rememberCoroutineScope()
    val hPad           = responsiveHPad()

    var dbStudents        by remember { mutableStateOf<List<Student>>(students) }
    var displayClassName  by remember { mutableStateOf("") }
    var displayRollPrefix by remember { mutableStateOf("-") }
    var isLoading         by remember { mutableStateOf(true) }
    var isSaving          by remember { mutableStateOf(false) }
    var showBackDialog    by remember { mutableStateOf(false) }
    var hasSelectionChanges by remember { mutableStateOf(false) }

    BackHandler { showBackDialog = true }

    LaunchedEffect(classId) {
        val classInfo     = classManager.getClassById(classId)
        displayClassName  = classInfo?.className ?: "Class"
        displayRollPrefix = classInfo?.rollPrefix?.ifBlank { "-" } ?: "-"
        dbStudents        = sessionManager.getStudents(classId)
        isLoading         = false
    }

    // FIX 1: Expose the MutableState<Int> directly so onClick lambdas can capture
    //         a stable reference and update it without being recreated each frame.
    val presentCountState = remember(dbStudents) { mutableIntStateOf(0) }
    var presentCount by presentCountState

    val presentStates    = remember(dbStudents) { dbStudents.map { mutableStateOf(false) } }
    val studentIndexById = remember(dbStudents) {
        dbStudents.mapIndexed { index, student -> student.studentId to index }.toMap()
    }
    val lastTappedState = remember { mutableStateOf<Student?>(null) }

    fun extractRollInt(studentId: String): Int =
        studentId.trim().takeLastWhile { it.isDigit() }.toIntOrNull() ?: 0

    suspend fun persistCurrentSelections() {
        dbStudents.forEachIndexed { idx, student ->
            val isPresent = presentStates.getOrNull(idx)?.value == true
            sessionManager.markStudent(classId, student.studentId, if (isPresent) 1 else 0)
        }
        val progressRoll = lastTappedState.value?.studentId
            ?.let(::extractRollInt)
            ?: sessionManager.getActiveSession(classId)?.lastRoll
            ?: 1
        sessionManager.updateProgress(classId, progressRoll, presentCountState.value)
    }

    if (showBackDialog) {
        ExitSessionDialog(
            onDismiss = { showBackDialog = false },
            onSave    = {
                scope.launch {
                    persistCurrentSelections()
                    showBackDialog = false
                    onBack()
                }
            },
            onDiscard = {
                scope.launch {
                    sessionManager.discardSession(classId)
                    showBackDialog = false
                    onBack()
                }
            },
            showDiscard = showDiscardOnBack,
            requireDiscardConfirmation = hasSelectionChanges
        )
    }

    LaunchedEffect(dbStudents, classId) {
        presentStates.forEach { it.value = false }
        var loadedPresentCount = 0
        if (dbStudents.isNotEmpty()) {
            val sessionAttendance = sessionManager.getSessionAttendance(classId)
            sessionAttendance.forEach { record ->
                val idx = studentIndexById[record.studentId] ?: return@forEach
                val isPresent = record.status == 1
                presentStates[idx].value = isPresent
                if (isPresent) loadedPresentCount++
            }
        }
        presentCountState.value = loadedPresentCount

        val activeRoll = sessionManager.getActiveSession(classId)?.lastRoll ?: 0
        lastTappedState.value = dbStudents.firstOrNull { extractRollInt(it.studentId) == activeRoll }
            ?: lastTappedState.value
    }

    // FIX 2: absentCount should only re-derive when presentCountState.value changes.
    //         Including `presentCount` as a `remember` key would invalidate the
    //         derivedStateOf block on every tap, completely defeating its purpose.
    val absentCount by remember(dbStudents) {
        derivedStateOf { dbStudents.size - presentCountState.value }
    }

    // FIX 3: lastTapped is no longer read here. Moving it inside NameTagBox scopes
    //         that recomposition to NameTagBox alone and prevents the parent (and
    //         thus the entire grid) from recomposing on every cell tap.

    val listState = rememberLazyListState()
    val rowChunks = remember(dbStudents) {
        dbStudents.chunked(5)
    }

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Mark Present Students",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 18.sp,
                        color      = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { showBackDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentBlue)
            }
            return@Scaffold
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 500.dp)
                    .padding(padding)
                    .padding(horizontal = hPad)
            ) {
                Spacer(Modifier.height(4.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(CardBackground)
                            .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                    ) {
                        ClassMetaTable(
                            className  = displayClassName,
                            rollPrefix = displayRollPrefix
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "All absent by default",
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = AbsentRed
                    )
                }
                Spacer(Modifier.height(8.dp))

                // FIX 3 (cont.): NameTagBox now owns the lastTappedState read.
                //                 Tapping a cell recomposes only NameTagBox, not
                //                 the parent or any sibling grid items.
                NameTagBox(
                    lastTappedState  = lastTappedState,
                    presentStates    = presentStates,
                    studentIndexById = studentIndexById
                )
                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(
                        items = rowChunks,
                        key = { row -> row.firstOrNull()?.studentId ?: "empty_row" }
                    ) { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            row.forEach { student ->
                                val index = studentIndexById[student.studentId] ?: -1
                                if (index >= 0) {
                                    val state = presentStates[index]
                                    val isPresent = state.value
                                    val onClick = remember {
                                        {
                                            val nextValue = !state.value
                                            val updatedPresentCount = (presentCountState.value + if (nextValue) 1 else -1).coerceAtLeast(0)
                                            hasSelectionChanges = true
                                            state.value = nextValue
                                            presentCountState.value = updatedPresentCount
                                            lastTappedState.value = student
                                            scope.launch {
                                                sessionManager.markStudent(classId, student.studentId, if (nextValue) 1 else 0)
                                sessionManager.updateProgress(classId, extractRollInt(student.studentId), updatedPresentCount)
                                            }
                                            Unit
                                        }
                                    }
                                    Box(modifier = Modifier.weight(1f)) {
                                        RollBox(
                                            roll = student.studentId,
                                            isPresent = isPresent,
                                            onClick = onClick
                                        )
                                    }
                                }
                            }
                            repeat(5 - row.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard("Present", presentCount.toString(), PresentGreen, Modifier.weight(1f))
                    StatCard("Absent",  absentCount.toString(),  AbsentRed,    Modifier.weight(1f))
                    StatCard("Total",   dbStudents.size.toString(), AccentBlue, Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick  = {
                        isSaving = true
                        val present = mutableSetOf<String>()
                        val absent  = mutableSetOf<String>()
                        dbStudents.forEachIndexed { idx, student ->
                            if (presentStates.getOrNull(idx)?.value == true) present.add(student.studentId)
                            else absent.add(student.studentId)
                        }
                        scope.launch {
                            persistCurrentSelections()
                            isSaving = false
                            onFinish(present, absent)
                        }
                    },
                    enabled  = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    shape  = RoundedCornerShape(16.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(18.dp),
                            color       = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Summarize & Save", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// FIX 3 (impl): NameTagBox now accepts the raw MutableState objects and reads them
//               internally. Recompositions caused by tapping cells are contained here
//               and do not propagate to the parent or the LazyVerticalGrid.
@Composable
private fun NameTagBox(
    lastTappedState  : MutableState<Student?>,
    presentStates    : List<MutableState<Boolean>>,
    studentIndexById : Map<String, Int>
) {
    val student by lastTappedState

    // derivedStateOf tracks both lastTappedState.value and the relevant
    // presentStates[idx].value, recomputing only when either actually changes.
    val isPresent by remember(presentStates, studentIndexById) {
        derivedStateOf {
            student?.let { s ->
                studentIndexById[s.studentId]?.let { idx ->
                    presentStates.getOrNull(idx)?.value
                }
            } ?: false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CardBackground)
            .border(
                1.dp,
                when {
                    student == null -> BorderColor
                    isPresent       -> PresentGreen.copy(alpha = 0.4f)
                    else            -> AbsentRed.copy(alpha = 0.4f)
                },
                RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (student == null) {
            Text(
                "Tap a number to see name and change state",
                fontSize = 13.sp,
                lineHeight = 16.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            val nameScrollState = rememberScrollState()
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(BorderColor)
                            .widthIn(min = 44.dp)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            getDisplayRoll(student!!.studentId),
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color      = TextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(nameScrollState)
                    ) {
                        Text(
                            student!!.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isPresent) PresentGreen.copy(alpha = 0.15f)
                            else           AbsentRed.copy(alpha = 0.15f)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        if (isPresent) "P" else "A",
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color      = if (isPresent) PresentGreen else AbsentRed
                    )
                }
            }
        }
    }
}

@Composable
private fun RollBox(roll: String, isPresent: Boolean, onClick: () -> Unit) {
    val bgColor = if (isPresent) PresentGreen.copy(alpha = 0.15f) else AbsentRed.copy(alpha = 0.10f)
    val borderColor = if (isPresent) PresentGreen.copy(alpha = 0.45f) else AbsentRed.copy(alpha = 0.28f)
    val textColor = if (isPresent) PresentGreen else AbsentRed
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            getDisplayRoll(roll),
            fontSize   = 13.sp,
            fontWeight = FontWeight.Bold,
            color      = textColor,
            fontFamily = FontFamily.Monospace,
            textAlign  = TextAlign.Center
        )
    }
}

@Composable
private fun ClassMetaTable(
    className  : String,
    rollPrefix : String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        Column(
            modifier = Modifier
                .weight(0.32f)
                .fillMaxHeight()
        ) {
            ClassMetaLeftCell(label = "Class")
            HorizontalDivider(color = BorderColor, thickness = 1.dp)
            ClassMetaLeftCell(label = "Roll Prefix")
        }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(BorderColor.copy(alpha = 0.85f))
        )
        Column(
            modifier = Modifier
                .weight(0.68f)
                .fillMaxHeight()
        ) {
            ClassMetaRightCell(value = className,   valueColor = AccentBlue)
            HorizontalDivider(color = BorderColor, thickness = 1.dp)
            ClassMetaRightCell(value = rollPrefix,  valueColor = TextPrimary)
        }
    }
}

@Composable
private fun ClassMetaLeftCell(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize   = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color      = TextSecondary,
            textAlign  = TextAlign.Center,
            modifier   = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ClassMetaRightCell(
    value      : String,
    valueColor : Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            value,
            fontSize   = 12.sp,
            fontWeight = FontWeight.Bold,
            color      = valueColor,
            fontFamily = FontFamily.Monospace,
            maxLines   = 1,
            textAlign  = TextAlign.Center,
            modifier   = Modifier.fillMaxWidth()
        )
    }
}

private fun getDisplayRoll(rawId: String): String {
    val numeric = rawId.reversed().takeWhile { it.isDigit() }.reversed().toIntOrNull() ?: 0
    return if (numeric <= 999) numeric.toString().padStart(3, '0') else numeric.toString()
}





