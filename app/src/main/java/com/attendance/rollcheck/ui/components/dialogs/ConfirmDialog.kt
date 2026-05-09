package com.attendance.rollcheck.ui.components.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.attendance.rollcheck.ui.theme.AbsentRed
import com.attendance.rollcheck.ui.theme.AccentBlue
import com.attendance.rollcheck.ui.theme.BorderColor
import com.attendance.rollcheck.ui.theme.CardBackground
import com.attendance.rollcheck.ui.theme.TextPrimary
import com.attendance.rollcheck.ui.theme.TextSecondary

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String   = "Confirm",
    dismissLabel: String   = "Cancel",
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = CardBackground,
        shape            = RoundedCornerShape(20.dp),
        title = {
            Text(
                text       = title,
                color      = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center
            )
        },
        text = {
            Text(
                text      = message,
                color     = TextSecondary,
                textAlign = TextAlign.Center,
                fontSize  = 14.sp
            )
        },
        confirmButton = {
            Button(
                onClick  = onConfirm,
                colors   = ButtonDefaults.buttonColors(
                    containerColor = if (isDestructive) AbsentRed else AccentBlue
                ),
                shape    = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(confirmLabel, fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick  = onDismiss,
                shape    = RoundedCornerShape(12.dp),
                border   = BorderStroke(1.dp, BorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(dismissLabel, fontWeight = FontWeight.SemiBold, color = TextSecondary)
            }
        }
    )
}