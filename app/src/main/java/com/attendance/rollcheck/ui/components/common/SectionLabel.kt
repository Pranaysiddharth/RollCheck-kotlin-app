package com.attendance.rollcheck.ui.components.common

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.attendance.rollcheck.ui.theme.TextSecondary

@Composable
fun SectionLabel(text: String) {
    Text(
        text          = text.uppercase(),
        fontSize      = 11.sp,
        fontWeight    = FontWeight.Bold,
        color         = TextSecondary,
        letterSpacing = 1.sp
    )
}