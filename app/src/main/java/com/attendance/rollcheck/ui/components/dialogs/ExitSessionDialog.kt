package com.attendance.rollcheck.ui.components.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.attendance.rollcheck.ui.theme.AbsentRed
import com.attendance.rollcheck.ui.theme.AccentBlue
import com.attendance.rollcheck.ui.theme.CardBackground
import com.attendance.rollcheck.ui.theme.PresentGreen
import com.attendance.rollcheck.ui.theme.TextPrimary
import com.attendance.rollcheck.ui.theme.TextSecondary

@Composable
fun ExitSessionDialog(
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    showDiscard: Boolean = true,
    requireDiscardConfirmation: Boolean = true
) {
    var confirmDiscard by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {
            if (confirmDiscard) confirmDiscard = false else onDismiss()
        },
        containerColor = CardBackground,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.ExitToApp,
                    null,
                    tint = if (confirmDiscard) AbsentRed else AccentBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (confirmDiscard) "Confirm Discard" else "Exit Session",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (confirmDiscard) {
                    Text(
                        "Attendance values are valuable. Please confirm before clearing this session:",
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                    ExitBullet("Discard will remove all current attendance progress.")
                    ExitBullet("You will need to mark this session again from the start.")
                    ExitBullet("Use Cancel if you tapped Discard by mistake.")
                } else {
                    Text("Choose how you want to exit:", color = TextPrimary, fontSize = 14.sp)
                    ExitBullet("Save: Keep current progress in database.")
                    if (showDiscard) {
                        ExitBullet("Discard: Clear all session progress.")
                    }
                    ExitBullet("Continue: Return to marking attendance.")
                }
            }
        },
        confirmButton = {
            if (confirmDiscard) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { confirmDiscard = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                    TextButton(onClick = onDiscard) {
                        Text("Discard Session", color = AbsentRed, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    if (showDiscard) {
                        TextButton(onClick = {
                            if (requireDiscardConfirmation) confirmDiscard = true else onDiscard()
                        }) {
                            Text("Discard", color = AbsentRed, fontWeight = FontWeight.Bold)
                        }
                    }
                    TextButton(onClick = onSave) {
                        Text("Save", color = PresentGreen, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Continue", color = TextSecondary)
                    }
                }
            }
        }
    )
}

@Composable
private fun ExitBullet(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Spacer(
            modifier = Modifier
                .size(6.dp)
                .background(TextSecondary, CircleShape)
                .align(Alignment.CenterVertically)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
    }
}
