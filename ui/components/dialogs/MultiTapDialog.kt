package com.attendance.rollcheck.ui.components.dialogs


import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.attendance.rollcheck.ui.theme.AccentBlue
import com.attendance.rollcheck.ui.theme.CardBackground
import com.attendance.rollcheck.ui.theme.TextPrimary
import com.attendance.rollcheck.ui.theme.TextSecondary
import com.attendance.rollcheck.ui.theme.WarningYellow

@Composable
fun MultiTapDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = CardBackground,
        shape            = RoundedCornerShape(20.dp),
        icon = {
            Icon(Icons.Default.Warning, null, tint = WarningYellow, modifier = Modifier.size(32.dp))
        },
        title = {
            Text(
                text       = "Multiple Inputs Detected",
                color      = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center
            )
        },
        text = {
            Text(
                text      = "Both buttons were tapped simultaneously. No attendance was recorded. Please tap one button at a time.",
                color     = TextSecondary,
                textAlign = TextAlign.Center,
                fontSize  = 14.sp
            )
        },
        confirmButton = {
            Button(
                onClick  = onDismiss,
                colors   = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                shape    = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Got it", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    )
}