package com.attendance.rollcheck.ui.components.buttons

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.attendance.rollcheck.ui.theme.AbsentRed
import com.attendance.rollcheck.ui.theme.PresentGreen
import com.attendance.rollcheck.utils.extensions.multiTouchGuard

@Composable
fun AttendanceButtons(
    onAbsent: () -> Unit,
    onPresent: () -> Unit,
    onMultiTouch: () -> Unit
) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .multiTouchGuard(onMultiTouch),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(
            onClick   = onAbsent,
            modifier  = Modifier.weight(1f).heightIn(min = 56.dp),
            colors    = ButtonDefaults.buttonColors(
                containerColor = AbsentRed.copy(alpha = 0.15f),
                contentColor   = AbsentRed
            ),
            border    = BorderStroke(1.dp, AbsentRed.copy(alpha = 0.5f)),
            shape     = RoundedCornerShape(16.dp),
            elevation = null
        ) {
            Text("Absent", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Button(
            onClick   = onPresent,
            modifier  = Modifier.weight(1f).heightIn(min = 56.dp),
            colors    = ButtonDefaults.buttonColors(
                containerColor = PresentGreen.copy(alpha = 0.15f),
                contentColor   = PresentGreen
            ),
            border    = BorderStroke(1.dp, PresentGreen.copy(alpha = 0.5f)),
            shape     = RoundedCornerShape(16.dp),
            elevation = null
        ) {
            Text("Present", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}