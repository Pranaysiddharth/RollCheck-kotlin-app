package com.attendance.rollcheck.ui.screens.info

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.attendance.rollcheck.ui.theme.AbsentRed
import com.attendance.rollcheck.ui.theme.AccentBlue
import com.attendance.rollcheck.ui.theme.AppBackground
import com.attendance.rollcheck.ui.theme.BorderColor
import com.attendance.rollcheck.ui.theme.CardBackground
import com.attendance.rollcheck.ui.theme.PresentGreen
import com.attendance.rollcheck.ui.theme.TextPrimary
import com.attendance.rollcheck.ui.theme.TextSecondary
import com.attendance.rollcheck.ui.theme.WarningYellow
import com.attendance.rollcheck.utils.adaptive.responsiveHPad
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowToUseScreen(onBack: () -> Unit) {
    val hPad = responsiveHPad()
    val scope = rememberCoroutineScope()
    var contentVisible by remember { mutableStateOf(false) }
    var isClosing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        contentVisible = true
    }

    fun closeScreen() {
        if (isClosing) return
        isClosing = true
        contentVisible = false
        scope.launch {
            delay(100)
            onBack()
        }
    }

    BackHandler { closeScreen() }

    AnimatedVisibility(
        visible = contentVisible,
        enter = slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(100)) +
            fadeIn(animationSpec = tween(100)),
        exit = slideOutHorizontally(targetOffsetX = { -it / 2 }, animationSpec = tween(100)) +
            fadeOut(animationSpec = tween(100))
    ) {
        Scaffold(
            containerColor = AppBackground,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "How to Use",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextPrimary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { closeScreen() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextSecondary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = hPad)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Spacer(Modifier.height(4.dp))

                HowToCard(
                    icon = Icons.Default.Download,
                    color = PresentGreen,
                    title = "1. Download Template",
                    steps = listOf(
                        "Open hamburger menu (=) on home screen",
                        "Tap 'Download Excel Template'",
                        "Fill Column A: Roll ID  |  B: Name  |  C: Inactive Flag (0/1)",
                        "Threshold column D: enter 75 for 75% attendance requirement"
                    )
                )

                HowToCard(
                    icon = Icons.Default.Add,
                    color = AccentBlue,
                    title = "2. Add a Class",
                    steps = listOf(
                        "Tap + Class on the home screen",
                        "Enter class name (e.g. EDC_CSE_B_2025)",
                        "Select your Excel file - it must follow RollCheck format",
                        "Tap CREATE CLASS"
                    )
                )

                HowToCard(
                    icon = Icons.Default.PlayArrow,
                    color = WarningYellow,
                    title = "3. Take Attendance",
                    steps = listOf(
                        "Standard Mode: The digit wheel shows the current roll number. Tap PRESENT or ABSENT as students respond.",
                        "Mark Present Screen (MPS): If very few students are present, use 'Mark Present Screen' in Session Start to quickly select only the attendees from a grid."
                    )
                )

                HowToCard(
                    icon = Icons.Default.Refresh,
                    color = PresentGreen,
                    title = "4. Recovery & Manual Entry",
                    steps = listOf(
                        "Session Recovery: If the app closes mid-session, your progress is saved. Tap the class to resume from the Recovery screen",
                        "Manual Attendance: Tap 'Manual Attendance' in Session Start for a full student list",
                        "Use bulk present or bulk absent to mark all remaining students at once",
                        "Tap 'Finish Session' -> Summary screen -> Save to update the Excel file"
                    )
                )

                HowToCard(
                    icon = Icons.Default.Security,
                    color = AbsentRed,
                    title = "5. Security Features",
                    steps = listOf(
                        "Set a 6-digit PIN in Settings -> Change PIN",
                        "PIN is required every time the app is opened for data privacy",
                        "Deleting a class requires your PIN"
                    )
                )

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun HowToCard(icon: ImageVector, color: Color, title: String, steps: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBackground)
            .border(1.dp, BorderColor, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
        steps.forEach { step ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color)
                )
                Spacer(Modifier.width(8.dp))
                Text(step, fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp)
            }
        }
    }
}




