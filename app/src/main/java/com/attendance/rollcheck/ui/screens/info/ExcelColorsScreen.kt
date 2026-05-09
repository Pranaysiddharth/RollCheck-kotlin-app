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
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.attendance.rollcheck.ui.theme.AccentBlue
import com.attendance.rollcheck.ui.theme.AppBackground
import com.attendance.rollcheck.ui.theme.BorderColor
import com.attendance.rollcheck.ui.theme.CardBackground
import com.attendance.rollcheck.ui.theme.TextPrimary
import com.attendance.rollcheck.ui.theme.TextSecondary
import com.attendance.rollcheck.utils.adaptive.responsiveHPad
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class ExcelColorInfo(
    val label: String,
    val hex: String,
    val usage: String
)

private data class ExcelColorSection(
    val title: String,
    val icon: ImageVector,
    val accent: Color,
    val description: String,
    val colors: List<ExcelColorInfo>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcelColorsScreen(onBack: () -> Unit) {
    val hPad = responsiveHPad()
    val scope = rememberCoroutineScope()
    var contentVisible by remember { mutableStateOf(false) }
    var isClosing by remember { mutableStateOf(false) }

    val sections = remember {
        listOf(
            ExcelColorSection(
                title = "Attendance Status",
                icon = Icons.Default.TableChart,
                accent = Color(0xFF10B981),
                description = "Colors directly used to show attendance meaning inside Excel cells and rules.",
                colors = listOf(
                    ExcelColorInfo("#10B981", "#10B981", "Present values 1 to 5 in attendance columns"),
                    ExcelColorInfo("#EF4444", "#EF4444", "Absent value 0 in attendance columns"),
                    ExcelColorInfo("#FFC107", "#FFC107", "Attendance percentage below threshold warning"),
                    ExcelColorInfo("#94A3B8", "#94A3B8", "Inactive student text and Attendance (%) shown as italic N/A"),
                    ExcelColorInfo("#F8FAFC", "#F8FAFC", "Neutral attendance text and default readable sheet text")
                )
            ),
            ExcelColorSection(
                title = "Sheet Surfaces",
                icon = Icons.Default.BorderColor,
                accent = AccentBlue,
                description = "Dark table colors that build the Excel layout, alternating rows, and overall look.",
                colors = listOf(
                    ExcelColorInfo("#151A2D", "#151A2D", "Primary dark row background and core attendance grid surface"),
                    ExcelColorInfo("#1A2035", "#1A2035", "Alternate dark row background for zebra striping"),
                    ExcelColorInfo("#1E2A45", "#1E2A45", "Date header background strip"),
                    ExcelColorInfo("#0B0F19", "#0B0F19", "Deep dark title or contrasting text blocks")
                )
            ),
            ExcelColorSection(
                title = "Labels And Headers",
                icon = Icons.Default.Palette,
                accent = Color(0xFF4FACFE),
                description = "Accent colors used for labels, date headings, and identity styling in the workbook.",
                colors = listOf(
                    ExcelColorInfo("#4FACFE", "#4FACFE", "Date label text, highlight accents, and informational emphasis"),
                    ExcelColorInfo("#2A6496", "#2A6496", "Secondary header band tone used in template styling")
                )
            )
        )
    }

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
                            "Excel Colors",
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

                InfoBanner()

                sections.forEach { section ->
                    ExcelColorCard(section)
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun InfoBanner() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBackground)
            .border(1.dp, BorderColor, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "This screen lists the real Excel template colors in `#C00000` style.",
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
        Text(
            "Use it as a visual reference for what each workbook color means.",
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun ExcelColorCard(section: ExcelColorSection) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBackground)
            .border(1.dp, BorderColor, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(section.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(section.icon, null, tint = section.accent, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(section.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }

        Text(
            section.description,
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )

        section.colors.forEach { colorInfo ->
            ExcelColorRow(colorInfo)
        }
    }
}

@Composable
private fun ExcelColorRow(colorInfo: ExcelColorInfo) {
    val swatchColor = remember(colorInfo.hex) {
        runCatching { Color(android.graphics.Color.parseColor(colorInfo.hex)) }
            .getOrElse { Color.Transparent }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppBackground.copy(alpha = 0.55f))
            .border(1.dp, BorderColor.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(swatchColor)
                .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                colorInfo.hex,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                colorInfo.usage,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}