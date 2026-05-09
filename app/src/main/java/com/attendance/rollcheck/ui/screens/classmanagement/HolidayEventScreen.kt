package com.attendance.rollcheck.ui.screens.classmanagement

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.attendance.rollcheck.ui.theme.*
import com.attendance.rollcheck.utils.adaptive.responsiveHPad
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

import com.attendance.rollcheck.domain.manager.ClassManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HolidayEventScreen(
    classId: String,
    className: String,
    onBack: () -> Unit
) {
    val context   = androidx.compose.ui.platform.LocalContext.current
    val classManager = remember { ClassManager.getInstance(context) }
    val hPad    = responsiveHPad()
    val scope   = rememberCoroutineScope()
    val today   = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date()) }
    var label   by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var showEmptyLabelDialog by remember { mutableStateOf(false) }

    if (showEmptyLabelDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyLabelDialog = false },
            containerColor = CardBackground,
            title = {
                Text(
                    text = "Empty Label",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Please enter a label for the holiday or event.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showEmptyLabelDialog = false }) {
                    Text("OK", color = AccentBlue, fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = { Text("Mark Holiday / Event", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextSecondary) }
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

                // Info card
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(WarningYellow.copy(alpha = 0.08f))
                        .border(1.dp, WarningYellow.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Event, null, tint = WarningYellow, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(className, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = TextPrimary, fontFamily = FontFamily.Monospace)
                        Text("Today: $today", fontSize = 11.sp, color = TextSecondary)
                    }
                }

                // Optional label
                OutlinedTextField(
                    value         = label,
                    onValueChange = { label = it },
                    label         = { Text("Label", fontSize = 13.sp) },
                    placeholder   = { Text("e.g. Republic Day, Sports Meet", fontSize = 13.sp, color = TextSecondary) },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = AccentBlue,
                        unfocusedBorderColor = BorderColor,
                        focusedTextColor     = TextPrimary,
                        unfocusedTextColor   = TextPrimary,
                        cursorColor          = AccentBlue,
                        focusedLabelColor    = AccentBlue,
                        unfocusedLabelColor  = TextSecondary
                    )
                )

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick = {
                        if (label.isBlank()) {
                            showEmptyLabelDialog = true
                        } else {
                            isSaving = true
                            scope.launch {
                                val ok = classManager.markHoliday(classId, label)
                                isSaving = false
                                if (ok) onBack()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = WarningYellow),
                    shape    = RoundedCornerShape(14.dp),
                    enabled  = !isSaving
                ) {
                    Icon(Icons.Default.Event, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Mark as Holiday / Event", fontSize = 14.sp,
                        fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        }
    }
}