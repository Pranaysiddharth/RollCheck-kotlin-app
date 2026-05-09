package com.attendance.rollcheck.ui.screens.classmanagement

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.attendance.rollcheck.ui.theme.*
import com.attendance.rollcheck.utils.adaptive.responsiveHPad
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenameClassScreen(
    classId: String,
    className: String,
    onBack: () -> Unit,
    onRenamed: () -> Unit
) {
    val context = LocalContext.current
    val classManager = remember { com.attendance.rollcheck.domain.manager.ClassManager.getInstance(context) }
    val hPad  = responsiveHPad()
    val scope = rememberCoroutineScope()

    var newName  by remember { mutableStateOf(className) }
    var nameError by remember { mutableStateOf("") }
    var isSaving  by remember { mutableStateOf(false) }

    fun validate(): Boolean {
        nameError = when {
            newName.isBlank()      -> "Name cannot be empty"
            newName == className   -> "New name is same as current"
            newName.length < 3     -> "Name too short"
            !newName.matches(Regex("[A-Z0-9_]+")) -> "Only letters, numbers and _ allowed"
            else -> ""
        }
        return nameError.isEmpty()
    }

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = { Text("Rename Class", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary) },
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
                    .fillMaxWidth()
                    .widthIn(max = 500.dp)
                    .padding(padding)
                    .padding(horizontal = hPad),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(Modifier.height(4.dp))

                // Current name
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardBackground)
                        .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Text("CURRENT NAME", fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        color = TextSecondary, letterSpacing = 1.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(className, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = TextSecondary, fontFamily = FontFamily.Monospace)
                }

                // New name input
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardBackground)
                        .border(1.dp,
                            if (nameError.isNotEmpty()) AbsentRed.copy(alpha = 0.6f) else BorderColor,
                            RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Text("NEW NAME", fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        color = TextSecondary, letterSpacing = 1.sp)
                    Spacer(Modifier.height(6.dp))
                    BasicTextField(
                        value           = newName,
                        onValueChange   = { newName = it.uppercase().replace(" ", "_"); nameError = "" },
                        singleLine      = true,
                        cursorBrush     = SolidColor(AccentBlue),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            color = TextPrimary, fontFamily = FontFamily.Monospace),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (nameError.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(nameError, fontSize = 11.sp, color = AbsentRed)
                    }
                }

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick = {
                        if (!validate()) return@Button
                        isSaving = true
                        scope.launch {
                            val success = classManager.renameClass(classId, newName)
                            isSaving = false
                            if (success) {
                                onRenamed()
                            } else {
                                nameError = "Failed to rename class"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    shape    = RoundedCornerShape(14.dp),
                    enabled  = !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Rename Class", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}