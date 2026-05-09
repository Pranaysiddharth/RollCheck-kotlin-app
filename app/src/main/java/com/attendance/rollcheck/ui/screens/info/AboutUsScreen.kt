package com.attendance.rollcheck.ui.screens.info

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.attendance.rollcheck.R
import com.attendance.rollcheck.ui.theme.AppBackground
import com.attendance.rollcheck.ui.theme.BorderColor
import com.attendance.rollcheck.ui.theme.CardBackground
import com.attendance.rollcheck.ui.theme.TextPrimary
import com.attendance.rollcheck.ui.theme.TextSecondary
import com.attendance.rollcheck.utils.adaptive.responsiveHPad
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutUsScreen(onBack: () -> Unit) {
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
                            "About RollCheck",
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
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(Modifier.height(16.dp))

                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "RollCheck Logo",
                    modifier = Modifier
                        .size(85.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, BorderColor, RoundedCornerShape(20.dp))
                )

                Text("RollCheck", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("Attendance made fast", fontSize = 13.sp, color = TextSecondary)

                Spacer(Modifier.height(4.dp))

                AboutCard(
                    "What is RollCheck?",
                    "RollCheck is a secure, offline-first attendance system designed for college faculty. It uses Excel as the single source of truth - no cloud, no sync, no data loss."
                )

                AboutCard(
                    "How data is stored",
                    "Attendance lives in an Excel file on your phone's internal storage. Room DB is used only as a temporary session buffer and is cleared after every successful save. Nothing is uploaded anywhere."
                )

                AboutCard(
                    "Philosophy",
                    "Fast attendance tool, not a management system. Every design decision is optimized for speed during class - tap Absent or Present, move on."
                )

                AboutCard(
                    "Tech Stack",
                    "Built with Jetpack Compose - Room DB - Apache POI - Android TTS - Material 3 design system - 100% offline"
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardBackground)
                        .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AboutRow("Version", "1.0.0 (1)")
                    HorizontalDivider(color = BorderColor)
                    AboutRow("Platform", "Android 8.0+")
                    HorizontalDivider(color = BorderColor)
                    AboutRow("Storage", "Fully Offline - No Cloud")
                    HorizontalDivider(color = BorderColor)
                    AboutRow("Data Format", "Excel (.xlsx)")
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun AboutCard(title: String, content: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground)
            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(content, fontSize = 12.sp, color = TextSecondary, lineHeight = 19.sp)
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = TextSecondary)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    }
}


