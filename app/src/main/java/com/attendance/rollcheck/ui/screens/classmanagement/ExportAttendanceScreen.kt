package com.attendance.rollcheck.ui.screens.classmanagement

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.attendance.rollcheck.data.work.PdfExportWorker
import com.attendance.rollcheck.domain.manager.ClassManager
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportAttendanceScreen(
    classId: String,
    className: String,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val classManager = remember { ClassManager.getInstance(context) }
    val workManager = remember { WorkManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    val hPad = responsiveHPad()

    var busyExcel by remember { mutableStateOf(false) }
    var startingPdf by remember { mutableStateOf(false) }
    var trackedPdfWorkId by remember { mutableStateOf<String?>(null) }
    var exportMsg by remember { mutableStateOf("") }
    var exportOk by remember { mutableStateOf<Boolean?>(null) }
    val pdfWorkInfos by workManager
        .getWorkInfosForUniqueWorkLiveData(PdfExportWorker.uniqueWorkName(classId))
        .observeAsState(emptyList())
    val activePdfWork = pdfWorkInfos.firstOrNull {
        it.state == WorkInfo.State.ENQUEUED ||
            it.state == WorkInfo.State.RUNNING ||
            it.state == WorkInfo.State.BLOCKED
    }
    val trackedPdfWork = trackedPdfWorkId?.let { currentId ->
        pdfWorkInfos.firstOrNull { it.id.toString() == currentId }
    }
    val busyPdf = startingPdf || activePdfWork != null
    val isBusy = busyExcel || busyPdf

    LaunchedEffect(activePdfWork?.id) {
        if (trackedPdfWorkId == null && activePdfWork != null) {
            trackedPdfWorkId = activePdfWork?.id?.toString()
        }
    }

    LaunchedEffect(trackedPdfWork?.id, trackedPdfWork?.state) {
        val workInfo = trackedPdfWork ?: return@LaunchedEffect
        if (!workInfo.state.isFinished) return@LaunchedEffect

        startingPdf = false
        trackedPdfWorkId = null
        val ok = workInfo.state == WorkInfo.State.SUCCEEDED
        val failureReason = workInfo.outputData.getString(PdfExportWorker.KEY_ERROR)?.takeIf { it.isNotBlank() }
        exportOk = ok
        exportMsg = if (ok) "PDF exported successfully!" else (failureReason ?: "PDF export failed")
    }

    fun blockBackWhileBusy() {
        Toast.makeText(context, "Please wait until export completes", Toast.LENGTH_SHORT).show()
    }

    BackHandler(enabled = isBusy) {
        blockBackWhileBusy()
    }

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = { Text("Export Attendance", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isBusy) blockBackWhileBusy() else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 500.dp).padding(padding).padding(horizontal = hPad),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                
                // Class Info Card
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardBackground)
                        .border(1.dp, BorderColor, RoundedCornerShape(16.dp)).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(AccentBlue.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.FolderOpen, null, tint = AccentBlue, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("TARGET CLASS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.sp)
                        Text(className, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary, fontFamily = FontFamily.Monospace)
                    }
                }

                Text("Choose Export Format", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.padding(start = 4.dp))

                // Excel Export Button
                ExportButton(
                    title = "Excel Worksheet",
                    subtitle = "Recommended for data processing",
                    icon = Icons.Default.TableChart,
                    color = PresentGreen,
                    isBusy = busyExcel,
                    enabled = !isBusy,
                    onClick = {
                        busyExcel = true
                        scope.launch {
                            val ok = classManager.exportExcel(classId)
                            busyExcel = false
                            exportOk = ok
                            exportMsg = if (ok) "Excel exported successfully!" else "Excel export failed"
                        }
                    }
                )

                // PDF Export Button
                ExportButton(
                    title = "Professional PDF Report",
                    subtitle = "Stored directly in export folder",
                    icon = Icons.Default.PictureAsPdf,
                    color = AccentBlue,
                    isBusy = busyPdf,
                    enabled = !isBusy,
                    onClick = {
                        startingPdf = true
                        exportOk = null
                        exportMsg = ""
                        scope.launch {
                            try {
                                trackedPdfWorkId = PdfExportWorker.enqueue(context, classId)
                            } catch (e: Exception) {
                                exportOk = false
                                exportMsg = e.message ?: "Could not start PDF export"
                            } finally {
                                startingPdf = false
                            }
                        }
                    }
                )

                Spacer(Modifier.height(8.dp))
                
                // Status Message
                exportOk?.let { ok ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background((if (ok) PresentGreen else AbsentRed).copy(alpha = 0.1f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            if (ok) Icons.Default.CheckCircle else Icons.Default.Error,
                            null,
                            tint = if (ok) PresentGreen else AbsentRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(exportMsg, fontSize = 13.sp, color = if (ok) PresentGreen else AbsentRed, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.weight(1f))

                Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Info, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "All exported files are stored in your device's\n'RollCheck/Exports' folder.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ExportButton(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    isBusy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(80.dp),
        colors = ButtonDefaults.buttonColors(containerColor = CardBackground, disabledContainerColor = CardBackground),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (isBusy) color.copy(alpha = 0.5f) else BorderColor),
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = color, strokeWidth = 3.dp)
                } else {
                    Icon(icon, null, tint = color, modifier = Modifier.size(26.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(subtitle, fontSize = 12.sp, color = TextSecondary)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = BorderColor, modifier = Modifier.size(18.dp))
        }
    }
}

