package com.attendance.rollcheck.ui.screens.classmanagement

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.attendance.rollcheck.MainActivity
import com.attendance.rollcheck.data.repository.AttendanceRepository
import com.attendance.rollcheck.domain.model.ExcelValidationResult
import com.attendance.rollcheck.ui.theme.AbsentRed
import com.attendance.rollcheck.ui.theme.AccentBlue
import com.attendance.rollcheck.ui.theme.AppBackground
import com.attendance.rollcheck.ui.theme.BorderColor
import com.attendance.rollcheck.ui.theme.CardBackground
import com.attendance.rollcheck.ui.theme.PresentGreen
import com.attendance.rollcheck.ui.theme.TextPrimary
import com.attendance.rollcheck.ui.theme.TextSecondary
import com.attendance.rollcheck.utils.adaptive.responsiveHPad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddClassScreen(
    onBack: () -> Unit,
    onClassCreated: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { AttendanceRepository.getInstance(context) }
    val scope = rememberCoroutineScope()
    val hPad = responsiveHPad()

    var className by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf("") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var validation by remember { mutableStateOf<ExcelValidationResult?>(null) }
    var isValidating by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf("") }
    var contentVisible by remember { mutableStateOf(false) }
    var isClosing by remember { mutableStateOf(false) }

    val fileNameFromUri = remember(selectedUri) {
        selectedUri?.let { uri ->
            try {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) it.getString(nameIndex).substringBeforeLast(".") else null
                    } else null
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    val detectedRollPrefix = validation?.inferredRollPrefix.orEmpty()
    val canCreate = className.isNotBlank() && nameError.isEmpty() && validation?.isValid == true && !isSaving

    LaunchedEffect(Unit) {
        contentVisible = true
    }

    fun closeScreen() {
        if (isClosing) return
        isClosing = true
        contentVisible = false
        scope.launch {
            kotlinx.coroutines.delay(100)
            onBack()
        }
    }

    fun onClassNameChange(raw: String) {
        className = raw.replace(" ", "_").uppercase().filter { it.isLetterOrDigit() || it == '_' }
        nameError = when {
            className.isEmpty() -> ""
            className.length < 3 -> "Name too short"
            !className.matches(Regex("[A-Z0-9_]+")) -> "Only letters, numbers and _ allowed"
            else -> ""
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        selectedUri = uri
        validation = null
        saveError = ""
        isValidating = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                repo.validateExcelFile(uri)
            }
            validation = result
            isValidating = false
        }
    }

    BackHandler { closeScreen() }

    AnimatedVisibility(
        visible = contentVisible,
        enter = slideInVertically(initialOffsetY = { it / 10 }, animationSpec = tween(100)) + fadeIn(animationSpec = tween(100)),
        exit = slideOutVertically(targetOffsetY = { it / 12 }, animationSpec = tween(100)) + fadeOut(animationSpec = tween(100))
    ) {
        Scaffold(
            containerColor = AppBackground,
            topBar = {
                TopAppBar(
                    title = { Text("Add Class", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary) },
                    navigationIcon = {
                        IconButton(onClick = { closeScreen() }) {
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
                        .padding(horizontal = hPad)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Spacer(Modifier.height(4.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(CardBackground)
                            .border(1.dp, if (nameError.isNotEmpty()) AbsentRed.copy(alpha = 0.6f) else BorderColor, RoundedCornerShape(14.dp))
                            .padding(16.dp)
                    ) {
                        Text("CLASS NAME", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.sp)
                        Spacer(Modifier.height(8.dp))
                        BasicTextField(
                            value = className,
                            onValueChange = { onClassNameChange(it) },
                            singleLine = true,
                            cursorBrush = SolidColor(AccentBlue),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary, fontFamily = FontFamily.Monospace),
                            decorationBox = { inner ->
                                if (className.isEmpty()) {
                                    Text("e.g. EDC_CSE_B_2025", fontSize = 18.sp, lineHeight = 22.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
                                }
                                inner()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        fileNameFromUri?.let { fname ->
                            val formattedName = fname.replace(" ", "_").uppercase()
                            if (className != formattedName) {
                                Spacer(Modifier.height(8.dp))
                                Surface(
                                    onClick = { onClassNameChange(formattedName) },
                                    color = Color.Transparent,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.DriveFileRenameOutline, null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "Use file name: $formattedName",
                                            fontSize = 12.sp,
                                            color = AccentBlue,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                        if (nameError.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(nameError, fontSize = 12.sp, lineHeight = 16.sp, color = AbsentRed, fontWeight = FontWeight.Medium, modifier = Modifier.fillMaxWidth())
                        }
                    }

                    Button(
                        onClick = {
                            MainActivity.requestSkipNextPauseLock()
                            filePicker.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CardBackground, contentColor = TextPrimary),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
                    ) {
                        Icon(Icons.Default.FileOpen, null, tint = AccentBlue, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (selectedUri != null) "Change Excel File" else "Select Excel File", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }

                    AnimatedVisibility(visible = selectedUri != null, enter = fadeIn(), exit = fadeOut()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(CardBackground)
                                .border(
                                    1.dp,
                                    when {
                                        isValidating -> BorderColor
                                        validation?.isValid == true -> PresentGreen.copy(alpha = 0.5f)
                                        else -> AbsentRed.copy(alpha = 0.5f)
                                    },
                                    RoundedCornerShape(14.dp)
                                )
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isValidating) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = AccentBlue, strokeWidth = 2.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text("Validating file...", fontSize = 13.sp, color = TextSecondary)
                                }
                            } else {
                                val v = validation
                                if (v != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            if (v.isValid) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                            null,
                                            tint = if (v.isValid) PresentGreen else AbsentRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            if (v.isValid) "Valid - ${v.studentCount} students detected" else v.errorMessage,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (v.isValid) PresentGreen else AbsentRed
                                        )
                                    }
                                    if (v.isValid) {
                                        HorizontalDivider(color = BorderColor)
                                        FileInfoRow("Roll Prefix", if (v.inferredRollPrefix.isNotEmpty()) v.inferredRollPrefix else "None")
                                        FileInfoRow("First", "${v.firstStudentId} - ${v.firstStudentName}")
                                        FileInfoRow("Last", "${v.lastStudentId} - ${v.lastStudentName}")
                                    }
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(CardBackground)
                            .border(1.dp, BorderColor, RoundedCornerShape(14.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Note", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        NoteRow("Only .xlsx files supported")
                        NoteRow("Every student ID in the file must use the same roll prefix")
                        NoteRow("Roll prefix is detected automatically from the Excel file")
                        NoteRow("File will be copied to internal storage")
                    }

                    if (saveError.isNotEmpty()) {
                        Text(
                            saveError,
                            fontSize = 11.sp,
                            color = AbsentRed,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = {
                            val uri = selectedUri ?: return@Button
                            isSaving = true
                            saveError = ""
                            scope.launch {
                                try {
                                    if (repo.classNameExists(className)) {
                                        isSaving = false
                                        nameError = "Class '$className' already exists"
                                        return@launch
                                    }
                                    val classId = repo.addClass(className, detectedRollPrefix, uri)
                                    isSaving = false
                                    if (classId != null) {
                                        onClassCreated()
                                    } else {
                                        saveError = "Failed to import file"
                                    }
                                } catch (e: Exception) {
                                    isSaving = false
                                    saveError = e.message ?: "An unknown error occurred"
                                }
                            }
                        },
                        enabled = canCreate,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                        }
                        Text("CREATE CLASS", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

}

@Composable
private fun FileInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Row(
            modifier = Modifier.width(96.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                label,
                fontSize = 11.sp,
                color = TextSecondary,
                lineHeight = 16.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Text(":", fontSize = 11.sp, color = TextSecondary, lineHeight = 16.sp, maxLines = 1)
        }
        Text(
            value,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = TextPrimary,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun NoteRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .padding(end = 8.dp)
                .size(4.dp)
                .clip(RoundedCornerShape(50))
                .background(TextSecondary)
        )
        Text(text, fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp)
    }
}






