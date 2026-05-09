package com.attendance.rollcheck.ui.components.buttons

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.attendance.rollcheck.ui.theme.*

/**
 * Font selector button used in Settings screen.
 * Shows "Aa" preview in the selected font family.
 */
@Composable
fun FontOptionButton(
    label: String,
    fontFamily: FontFamily,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) AccentBlue.copy(alpha = 0.15f) else AppBackground)
            .border(1.5.dp, if (selected) AccentBlue else BorderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text       = "Aa",
                fontSize   = 20.sp,
                fontWeight = FontWeight.Bold,
                color      = if (selected) AccentBlue else TextSecondary,
                fontFamily = fontFamily
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text       = label,
                fontSize   = 11.sp,
                fontWeight = FontWeight.Medium,
                color      = if (selected) AccentBlue else TextSecondary,
                fontFamily = fontFamily
            )
        }
    }
}