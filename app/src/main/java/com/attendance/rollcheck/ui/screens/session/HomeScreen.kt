package com.attendance.rollcheck.ui.screens.session

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.attendance.rollcheck.data.local.excel.ExcelManager
import com.attendance.rollcheck.data.local.prefs.PreferencesManager
import com.attendance.rollcheck.data.repository.AttendanceRepository
import com.attendance.rollcheck.domain.model.ClassInfo
import com.attendance.rollcheck.ui.theme.*
import com.attendance.rollcheck.utils.adaptive.responsiveHPad
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStartRollCall:      (className: String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onAddClass:           () -> Unit = {},
    onDeleteClass:        (classId: String, className: String) -> Unit = { _, _ -> },
    onRenameClass:        (classId: String, className: String) -> Unit = { _, _ -> },
    onEditStudents:       (classId: String, className: String) -> Unit = { _, _ -> },
    onExportAttendance:   (classId: String, className: String) -> Unit = { _, _ -> },
    onHolidayEvent:       (classId: String, className: String) -> Unit = { _, _ -> },
    onChooseAttendanceFolder: () -> Unit = {},
    onOpenExportFolder:   () -> Unit = {},
    onHowToUse:           () -> Unit = {},
    onAboutUs:            () -> Unit = {},
    onExcelColors:        () -> Unit = {}
) {
    val context  = LocalContext.current
    val repo     = remember { AttendanceRepository.getInstance(context) }
    val prefs    = remember { PreferencesManager.getInstance(context) }
    val scope    = rememberCoroutineScope()

    // ── Issue #12: real DB data, no static list ───────────────────────────────
    LaunchedEffect(Unit) {
        repo.ensurePublicFoldersExist()
    }

    val classesState by repo.observeClasses().collectAsState(initial = null)
    val classes = classesState.orEmpty()

    val listState      = rememberLazyListState()
    var searchQuery    by remember { mutableStateOf("") }
    var searchActive   by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    var drawerOpen     by remember { mutableStateOf(false) }
    var toastMsg       by remember { mutableStateOf<String?>(null) }

    val atTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }

    val filteredClasses by remember(classes, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) classes
            else classes.filter { it.className.contains(searchQuery.trim(), ignoreCase = true) }
        }
    }

    val hPad = responsiveHPad()

    // Simple toast
    LaunchedEffect(toastMsg) {
        if (toastMsg != null) {
            kotlinx.coroutines.delay(2000)
            toastMsg = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        Scaffold(
            containerColor = AppBackground,
            topBar = {
                TopAppBar(
                    title = { Text("RollCheck", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPrimary) },
                    navigationIcon = {
                        IconButton(onClick = { drawerOpen = true }) {
                            Icon(Icons.Default.Menu, "Menu", tint = TextSecondary)
                        }
                    },
                    actions = {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, "Settings", tint = TextSecondary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                Box(modifier = Modifier.fillMaxSize().widthIn(max = 500.dp).padding(horizontal = hPad)) {
                    LazyColumn(
                        state               = listState,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding      = PaddingValues(top = 4.dp, bottom = 88.dp)
                    ) {
                        item(key = "header") {
                            Row(
                                modifier          = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SearchHeaderSlot(
                                    modifier       = Modifier.weight(1f),
                                    searchActive   = searchActive,
                                    searchQuery    = searchQuery,
                                    onQueryChange  = { searchQuery = it },
                                    focusRequester = focusRequester,
                                    totalClasses   = classes.size
                                )
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (searchActive) AccentBlue.copy(alpha = 0.15f) else CardBackground)
                                        .border(1.dp, if (searchActive) AccentBlue.copy(alpha = 0.4f) else BorderColor, RoundedCornerShape(10.dp))
                                        .clickable { searchActive = !searchActive; if (!searchActive) searchQuery = "" },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector        = if (searchActive) Icons.Default.Close else Icons.Default.Search,
                                        contentDescription = "Toggle Search",
                                        tint               = if (searchActive) AccentBlue else TextSecondary,
                                        modifier           = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        // ── Issue #12: "No Classes" empty state ───────────────
                        if (classesState != null && filteredClasses.isEmpty()) {
                            item(key = "empty") {
                                Box(modifier = Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Default.Book, null, tint = TextSecondary.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
                                        Text(
                                            if (searchQuery.isBlank()) "No classes yet.\nTap + Class to add one."
                                            else "No classes found for \"$searchQuery\"",
                                            color     = TextSecondary,
                                            fontSize  = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            items(items = filteredClasses, key = { it.id }) { classInfo ->
                                ClassCard(
                                    classInfo         = classInfo,
                                    onClick           = { onStartRollCall(classInfo.id) },
                                    onDeleteClick     = { onDeleteClass(classInfo.id, classInfo.className) },
                                    onRenameClick     = { onRenameClass(classInfo.id, classInfo.className) },
                                    onEditStudents    = { onEditStudents(classInfo.id, classInfo.className) },
                                    onExportAttendance = { onExportAttendance(classInfo.id, classInfo.className) },
                                    onHolidayEvent    = { onHolidayEvent(classInfo.id, classInfo.className) }
                                )
                            }
                        }
                    }

                    // FAB
                    AnimatedVisibility(
                        visible  = atTop,
                        enter    = slideInVertically(spring(Spring.DampingRatioMediumBouncy), { 400 }) + fadeIn(),
                        exit     = slideOutVertically(spring(stiffness = Spring.StiffnessMedium), { 400 }) + fadeOut(),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 16.dp)
                    ) {
                        FloatingActionButton(
                            onClick        = onAddClass,
                            containerColor = AccentBlue,
                            contentColor   = Color.White,
                            shape          = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier          = Modifier.padding(horizontal = 16.dp)
                            ) {
                                Icon(Icons.Default.Add, "Add Class", modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Class", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }

        // Toast
        toastMsg?.let { msg ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Box(
                    modifier = Modifier.padding(bottom = 80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardBackground)
                        .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) { Text(msg, color = TextPrimary, fontSize = 13.sp) }
            }
        }

        // Scrim
        AnimatedVisibility(visible = drawerOpen, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))
                    .pointerInput(Unit) { detectTapGestures { drawerOpen = false } }
            )
        }

        // ── Issue #1: drawer properly wired with all callbacks ────────────────
        AnimatedVisibility(
            visible = drawerOpen,
            enter   = slideInHorizontally(
                initialOffsetX = { -1000 },
                animationSpec = tween(250, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(200)),
            exit    = slideOutHorizontally(
                targetOffsetX = { -1000 },
                animationSpec = tween(200, easing = FastOutSlowInEasing)
            ) + fadeOut(tween(150))
        ) {
            HamburgerDrawerContent(
                onClose    = { drawerOpen = false },
                onHowToUse = { drawerOpen = false; onHowToUse() },
                onAboutUs  = { drawerOpen = false; onAboutUs() },
                onExcelColors = { drawerOpen = false; onExcelColors() },
                onOpenExportFolder = {
                    drawerOpen = false
                    onOpenExportFolder()
                },
                onChooseAttendanceFolder = {
                    drawerOpen = false
                    onChooseAttendanceFolder()
                },
                onDownloadTemplate = {
                    drawerOpen = false
                    scope.launch {
                        val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            repo.downloadTemplateToDocuments()
                        }
                        toastMsg = if (ok) "Template saved to Documents/RollCheck" else "Download failed"
                    }
                }
            )
        }
    }
}

// ── Hamburger Drawer ──────────────────────────────────────────────────────────
@Composable
private fun HamburgerDrawerContent(
    onClose:            () -> Unit,
    onHowToUse:         () -> Unit,
    onAboutUs:          () -> Unit,
    onExcelColors:      () -> Unit,
    onOpenExportFolder: () -> Unit,
    onChooseAttendanceFolder: () -> Unit,
    onDownloadTemplate: () -> Unit
) {
    Box(modifier = Modifier.fillMaxHeight().width(256.dp).background(CardBackground)
        .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(topEnd = 0.dp, bottomEnd = 0.dp))) {
        Column(modifier = Modifier.fillMaxSize().padding(top = 56.dp, bottom = 24.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("RollCheck", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(start = 13.dp))
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close", tint = TextSecondary) }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = BorderColor, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
            DrawerItem(Icons.Default.Download,                "Download Excel Template",  PresentGreen, onDownloadTemplate)
            HorizontalDivider(color = BorderColor, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            
            // Export Folder with 'E' overlay
            DrawerItemWithOverlay(Icons.Default.Folder, "E", "Export Folder", PresentGreen, onOpenExportFolder)
            
            // Attendance Folder with 'A' overlay
            DrawerItemWithOverlay(Icons.Default.Folder, "A", "Attendance Folder", PresentGreen, onChooseAttendanceFolder)

            DrawerItem(Icons.AutoMirrored.Filled.HelpOutline, "How to Use",               AccentBlue,   onHowToUse)
            DrawerItem(Icons.Default.Palette,                 "Excel Colors",             AccentBlue,   onExcelColors)
            DrawerItem(Icons.Default.Info,                    "About Us",                 AccentBlue,   onAboutUs)
        }
    }
}

@Composable
private fun DrawerItemWithOverlay(icon: ImageVector, overlayText: String, label: String, tint: Color, onClick: () -> Unit) {
    Row(
        modifier          = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(34.dp)) {
            // Main Folder Icon
            Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            }
            
            // Visible Letter Badge (Bottom-Right)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 3.dp, y = 3.dp)
                    .size(16.dp)
                    .background(tint, RoundedCornerShape(5.dp))
                    .border(1.5.dp, CardBackground, RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = overlayText,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 9.sp
                )
            }
        }
        Spacer(Modifier.width(18.dp)) // Increased spacer for the badge offset
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
    }
}

@Composable
private fun DrawerItem(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Row(
        modifier          = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(14.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
    }
}

// ── Search header ─────────────────────────────────────────────────────────────
@Composable
private fun SearchHeaderSlot(
    modifier: Modifier, searchActive: Boolean, searchQuery: String,
    onQueryChange: (String) -> Unit, focusRequester: FocusRequester, totalClasses: Int
) {
    Box(modifier = modifier.heightIn(min = 36.dp)) {
        AnimatedVisibility(!searchActive, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)) {
            Row(
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(CardBackground)
                    .border(1.dp, BorderColor, RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Classes", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                Spacer(Modifier.width(6.dp))
                Box(modifier = Modifier.size(width = 32.dp, height = 22.dp)
                    .clip(RoundedCornerShape(20.dp)).background(AccentBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center) {
                    Text("$totalClasses", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentBlue, textAlign = TextAlign.Center)
                }
            }
        }
        AnimatedVisibility(searchActive,
            enter = expandHorizontally(spring(Spring.DampingRatioNoBouncy), Alignment.End) + fadeIn(),
            exit  = shrinkHorizontally(spring(Spring.DampingRatioNoBouncy), Alignment.End) + fadeOut()) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp)
                    .clip(RoundedCornerShape(20.dp)).background(CardBackground)
                    .border(1.dp, AccentBlue.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, null, tint = AccentBlue, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                BasicTextField(value = searchQuery, onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    singleLine = true, cursorBrush = SolidColor(AccentBlue),
                    textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    decorationBox = { inner ->
                        if (searchQuery.isEmpty()) Text("Search class", color = TextSecondary, fontSize = 13.sp)
                        inner()
                    })
                AnimatedVisibility(searchQuery.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                    Row { Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.Close, "Clear", tint = TextSecondary,
                            modifier = Modifier.size(14.dp).clickable { onQueryChange("") }) }
                }
            }
        }
    }
}

// ── Class card ────────────────────────────────────────────────────────────────
@Composable
fun ClassCard(
    classInfo: ClassInfo,
    onClick:            () -> Unit,
    onDeleteClick:      () -> Unit = {},
    onRenameClick:      () -> Unit = {},
    onEditStudents:     () -> Unit = {},
    onExportAttendance: () -> Unit = {},
    onHolidayEvent:     () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) expanded = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val parts    = classInfo.className.split("_")
    val subject  = parts.getOrNull(0) ?: classInfo.className
    val section  = parts.drop(1).joinToString(" · ")

    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)).background(CardBackground)
            .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(AccentBlue.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Book, null, tint = AccentBlue, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(subject, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary, fontFamily = FontFamily.Monospace)
            if (section.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(section, fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
            }
        }
        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Default.MoreVert, "Options", tint = TextSecondary, modifier = Modifier.size(20.dp))
            }
            // ── Issue #3: distinct containerColor so menu is visible ──────────
            DropdownMenu(
                expanded         = expanded,
                onDismissRequest = { expanded = false },
                shape            = RoundedCornerShape(14.dp),
                containerColor   = Color(0xFF1E2D42)   // visibly different from AppBackground
            ) {
                ClassMenuItem(Icons.Default.Event,      "Mark as Holiday",    WarningYellow) { expanded = false; onHolidayEvent() }
                ClassMenuItem(Icons.Default.Edit,       "Rename Class",       AccentBlue)   { expanded = false; onRenameClick() }
                ClassMenuItem(Icons.Default.FileUpload, "Export Attendance",  PresentGreen) { expanded = false; onExportAttendance() }
                ClassMenuItem(Icons.Default.Person,     "Edit Students",      AccentBlue)   { expanded = false; onEditStudents() }
                HorizontalDivider(color = BorderColor.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 4.dp))
                ClassMenuItem(Icons.Default.Delete,     "Delete Class",       AbsentRed)    {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    expanded = false
                    onDeleteClick()
                }
            }
        }
    }
}

@Composable
private fun ClassMenuItem(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(10.dp))
                Text(label, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        },
        onClick = onClick
    )
}
