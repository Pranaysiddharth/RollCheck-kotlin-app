package com.attendance.rollcheck.ui.screens.attendance

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.attendance.rollcheck.data.repository.AttendanceRepository
import com.attendance.rollcheck.ui.theme.*
import com.attendance.rollcheck.utils.adaptive.responsiveHPad

@Composable
fun ResultScreen(
    success: Boolean,
    className: String,
    onHome: () -> Unit,
    onRetry: () -> Unit
) {
    val hPad = responsiveHPad()
    val context = LocalContext.current
    val repo = remember { AttendanceRepository.getInstance(context) }
    var displayClassName by remember { mutableStateOf("") }

    LaunchedEffect(className) {
        val classInfo = repo.getClassById(className)
        displayClassName = classInfo?.className ?: className
    }

    val resolvedClassName = if (displayClassName.isBlank()) "Loading..." else displayClassName

    BackHandler(onBack = onHome)

    // Issue #16: Box fills screen, content column uses weight to push buttons to bottom
    Box(
        modifier         = Modifier.fillMaxSize().background(AppBackground),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .widthIn(max = 420.dp)
                .padding(horizontal = hPad)
                .padding(top = 64.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon
            Box(
                modifier = Modifier.size(88.dp).clip(RoundedCornerShape(24.dp))
                    .background(if (success) PresentGreen.copy(alpha = 0.15f) else AbsentRed.copy(alpha = 0.15f))
                    .border(1.dp, if (success) PresentGreen.copy(alpha = 0.4f) else AbsentRed.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint     = if (success) PresentGreen else AbsentRed,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text       = if (success) "Attendance Saved!" else "Save Failed",
                fontSize   = 22.sp, fontWeight = FontWeight.Bold,
                color      = if (success) PresentGreen else AbsentRed
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text      = if (success)
                    "Attendance for $resolvedClassName has been saved to Excel and synced to your attendance folder."
                else
                    "Could not write to the Excel file. Please try again.",
                fontSize  = 14.sp, color = TextSecondary,
                textAlign = TextAlign.Center, lineHeight = 21.sp
            )

            Spacer(Modifier.height(16.dp))

            Box(modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(CardBackground)
                .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(resolvedClassName, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = AccentBlue, fontFamily = FontFamily.Monospace)
            }

            // Issue #16: weight pushes buttons to the bottom of the screen
            Spacer(Modifier.weight(1f))

            // Retry (failure only)
            if (!success) {
                OutlinedButton(
                    onClick  = onRetry,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(14.dp),
                    border   = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
                ) {
                    Icon(Icons.Default.Refresh, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Retry", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                }
                Spacer(Modifier.height(12.dp))
            }

            // Go to Home — pinned at the bottom
            Button(
                onClick  = onHome,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                shape    = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Home, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Go to Home", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}
