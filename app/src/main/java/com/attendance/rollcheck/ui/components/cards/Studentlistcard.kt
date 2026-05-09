package com.attendance.rollcheck.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.attendance.rollcheck.domain.model.Student
import com.attendance.rollcheck.ui.theme.BorderColor
import com.attendance.rollcheck.ui.theme.CardBackground
import com.attendance.rollcheck.ui.theme.TextPrimary
import com.attendance.rollcheck.ui.theme.TextSecondary

private val ROW_HEIGHT       = 52.dp
private val MAX_VISIBLE_ROWS = 4

@Composable
fun StudentListCard(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    students: List<Student>,
    actionIcon: ImageVector,
    actionIconTint: Color,
    emptyIcon: ImageVector?,
    emptyIconTint: Color,
    emptyMessage: String,
    emptyMessageColor: Color,
    onAction: (Student) -> Unit
) {
    val minListHeight = ROW_HEIGHT * 2
    val maxListHeight = ROW_HEIGHT * MAX_VISIBLE_ROWS

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground)
            .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    tint               = iconTint,
                    modifier           = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text       = title,
                fontSize   = 16.sp,
                fontWeight = FontWeight.Bold,
                color      = TextPrimary
            )
        }
        HorizontalDivider(color = BorderColor)
        if (students.isEmpty()) {
            Box(
                modifier         = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minListHeight, max = maxListHeight),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (emptyIcon != null) {
                        Icon(
                            imageVector        = emptyIcon,
                            contentDescription = null,
                            tint               = emptyIconTint,
                            modifier           = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(
                        text       = emptyMessage,
                        color      = emptyMessageColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 15.sp
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.heightIn(min = minListHeight, max = maxListHeight)) {
                itemsIndexed(
                    items = students,
                    key = { _, student -> student.studentId }
                ) { index, student ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = ROW_HEIGHT)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(min = 44.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(BorderColor)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val digits = student.studentId.reversed().takeWhile { it.isDigit() }.reversed()
                            val number = digits.toIntOrNull()
                            val displayRoll = when {
                                number != null && number in 0..999 -> String.format("%03d", number)
                                digits.isNotEmpty() -> digits
                                else -> student.studentId
                            }
                            Text(
                                text       = displayRoll,
                                fontSize   = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color      = TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                maxLines   = 1,
                                softWrap   = false
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text       = student.name,
                            color      = TextPrimary,
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier   = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(actionIconTint.copy(alpha = 0.08f))
                                .border(0.5.dp, actionIconTint.copy(alpha = 0.25f), RoundedCornerShape(7.dp))
                                .clickable { onAction(student) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector        = actionIcon,
                                contentDescription = null,
                                tint               = actionIconTint,
                                modifier           = Modifier.size(16.dp)
                            )
                        }
                    }
                    if (index < students.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color    = BorderColor
                        )
                    }
                }
            }
        }
    }
}
