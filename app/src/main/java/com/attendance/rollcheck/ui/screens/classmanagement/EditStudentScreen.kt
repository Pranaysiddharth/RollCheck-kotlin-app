package com.attendance.rollcheck.ui.screens.classmanagement

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.attendance.rollcheck.data.repository.AttendanceRepository
import com.attendance.rollcheck.domain.model.Student
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
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditStudentScreen(
    classId: String,
    className: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { AttendanceRepository.getInstance(context) }
    val scope = rememberCoroutineScope()
    val hPad = responsiveHPad()

    var students by remember { mutableStateOf<List<Student>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<Student?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var popupError by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        students = repo.getAllStudentsForClass(classId)
        isLoading = false
    }

    fun displayRoll(studentId: String): String {
        val digits = studentId.takeLastWhile { it.isDigit() }
        val number = digits.toIntOrNull() ?: return studentId
        return "%03d".format(number)
    }

    fun formatThreshold(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else String.format("%.2f", value)

    LaunchedEffect(classId) {
        reload()
    }

    popupError?.let { message ->
        AlertDialog(
            onDismissRequest = { popupError = null },
            containerColor = CardBackground,
            title = { Text("Invalid Input", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text(message, color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { popupError = null }) {
                    Text("OK", color = AccentBlue, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = { Text("Edit Students", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary) },
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
                modifier = Modifier.fillMaxSize().widthIn(max = 500.dp).padding(padding).padding(horizontal = hPad)
            ) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        className,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    )
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(TextSecondary.copy(alpha = 0.85f))
                    )
                    Text(
                        "${students.size} students",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                }
                if (saveError != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(saveError ?: "", color = AbsentRed, fontSize = 12.sp)
                }
                Spacer(Modifier.height(10.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentBlue)
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardBackground)
                            .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                    ) {
                        LazyColumn {
                            itemsIndexed(students) { index, student ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { selected = student }
                                        .padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val roll = displayRoll(student.studentId)
                                    Box(
                                        modifier = Modifier.widthIn(min = 52.dp).clip(RoundedCornerShape(6.dp)).background(BorderColor)
                                            .padding(horizontal = 8.dp, vertical = 3.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(roll, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary, fontFamily = FontFamily.Monospace)
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        student.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (student.inactiveFlag == 1) TextSecondary else TextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Box(
                                        modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(AccentBlue.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Edit, null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                                    }
                                }
                                if (index < students.lastIndex) {
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = BorderColor)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { editing ->
        var newName by remember(editing.studentId) { mutableStateOf(editing.name) }
        var inactive by remember(editing.studentId) { mutableStateOf(editing.inactiveFlag == 1) }
        var threshold by remember(editing.studentId) { mutableStateOf(formatThreshold(editing.threshold)) }
        var saving by remember(editing.studentId) { mutableStateOf(false) }
        var inlineWarning by remember(editing.studentId) { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { if (!saving) selected = null },
            containerColor = CardBackground,
            title = { Text("Edit Student", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(BorderColor)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            displayRoll(editing.studentId),
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        editing.name,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    )
                    OutlinedTextField(
                        value = newName,
                        onValueChange = {
                            newName = it
                            inlineWarning = null
                        },
                        label = { Text("Name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = threshold,
                        onValueChange = {
                            val normalized = it.trim()
                            if (normalized.isEmpty() || normalized.matches(Regex("""\d{0,3}(\.\d{0,2})?"""))) {
                                threshold = normalized
                                inlineWarning = null
                            } else {
                                popupError = "Threshold must be a number from 0.00 to 100.00."
                            }
                        },
                        label = { Text("Attendance Threshold %") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Inactive", color = TextSecondary)
                        Button(
                            onClick = {
                                inactive = !inactive
                                inlineWarning = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (inactive) AbsentRed else PresentGreen),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(if (inactive) "Yes" else "No")
                        }
                    }
                    inlineWarning?.let {
                        Text(
                            it,
                            color = AbsentRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    inlineWarning = null
                    val normalizedName = newName.trim()
                    if (normalizedName.isBlank()) {
                        saveError = "Name cannot be empty"
                        return@TextButton
                    }

                    val thresholdValue = threshold.toDoubleOrNull()
                    if (thresholdValue == null) {
                        popupError = "Threshold must be a number from 0.00 to 100.00."
                        return@TextButton
                    }
                    if (thresholdValue < 0.0 || thresholdValue > 100.0) {
                        popupError = "Threshold must be between 0.00 and 100.00."
                        return@TextButton
                    }
                    val unchanged =
                        normalizedName == editing.name.trim() &&
                        inactive == (editing.inactiveFlag == 1) &&
                        abs(thresholdValue - editing.threshold) < 0.0001
                    if (unchanged) {
                        inlineWarning = "Nothing changed"
                        return@TextButton
                    }
                    saving = true
                    scope.launch {
                        val duplicateName = students.any {
                            it.studentId != editing.studentId && it.name.trim().equals(normalizedName, ignoreCase = true)
                        } || repo.studentNameExistsForClassExcept(classId, editing.studentId, normalizedName)
                        if (duplicateName) {
                            saving = false
                            popupError = "A student with the name '$normalizedName' already exists in this class."
                            return@launch
                        }
                        val ok = repo.updateStudentDetails(
                            classId = classId,
                            studentId = editing.studentId,
                            name = normalizedName,
                            inactiveFlag = if (inactive) 1 else 0,
                            threshold = thresholdValue
                        )
                        saving = false
                        if (ok) {
                            saveError = null
                            inlineWarning = null
                            students = students.map { student ->
                                if (student.studentId == editing.studentId) {
                                    student.copy(
                                        name = normalizedName,
                                        inactiveFlag = if (inactive) 1 else 0,
                                        threshold = thresholdValue
                                    )
                                } else {
                                    student
                                }
                            }
                            selected = null
                        } else {
                            saveError = "Unable to save student details"
                        }
                    }
                }, enabled = !saving) {
                    if (saving) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Save", color = AccentBlue, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selected = null }, enabled = !saving) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}




