package com.attendance.rollcheck.navigation

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.attendance.rollcheck.data.local.prefs.PreferencesManager
import com.attendance.rollcheck.data.repository.AttendanceRepository
import com.attendance.rollcheck.domain.model.Student
import com.attendance.rollcheck.ui.screens.attendance.ManualAttendanceScreen
import com.attendance.rollcheck.ui.screens.attendance.MarkPresentScreen
import com.attendance.rollcheck.ui.screens.attendance.ResultScreen
import com.attendance.rollcheck.ui.screens.attendance.SummaryScreen
import com.attendance.rollcheck.ui.screens.auth.PinScreen
import com.attendance.rollcheck.ui.screens.classmanagement.*
import com.attendance.rollcheck.ui.screens.info.AboutUsScreen
import com.attendance.rollcheck.ui.screens.info.ExcelColorsScreen
import com.attendance.rollcheck.ui.screens.info.HowToUseScreen
import com.attendance.rollcheck.ui.screens.session.*
import com.attendance.rollcheck.ui.screens.settings.SettingsScreen
import com.attendance.rollcheck.utils.tts.TtsManager

@Composable
fun AppNavGraph(
    hasSavedPin:     Boolean,
    onPinCreated:    (String) -> Boolean,
    onPinVerified:   () -> Unit,
    pinVerifier:     (String) -> Boolean,
    getSavedPinHash: () -> String?,
    verifyPin:       (String) -> Boolean,
    savePinHash:     (String) -> Unit
) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val repo = remember(context) { AttendanceRepository.getInstance(context) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var showSettingsOverlay by rememberSaveable { mutableStateOf(false) }

    fun openRollCheckSubFolder(subFolder: String) {
        val documents = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOCUMENTS
        )
        java.io.File(documents, "RollCheck/$subFolder").mkdirs()

        val authority = "com.android.externalstorage.documents"
        val documentId = "primary:Documents/RollCheck/$subFolder"
        val documentUri = android.provider.DocumentsContract.buildDocumentUri(authority, documentId)
        val treeUri = android.provider.DocumentsContract.buildTreeDocumentUri(authority, documentId)

        val intents = listOf(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(documentUri, android.provider.DocumentsContract.Document.MIME_TYPE_DIR)
                putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, documentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(treeUri, android.provider.DocumentsContract.Document.MIME_TYPE_DIR)
                putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, treeUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, documentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )

        val opened = intents.any { intent ->
            runCatching { context.startActivity(intent) }.isSuccess
        }

        if (!opened) {
            Toast.makeText(
                context,
                "Please open Documents/RollCheck/$subFolder manually",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        enterTransition = {
            fadeIn(animationSpec = tween(100))
        },
        exitTransition = {
            fadeOut(animationSpec = tween(100))
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(100))
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(100))
        }
    ) {

        // Ã¢â€â‚¬Ã¢â€â‚¬ Home Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
        composable(
            route = Routes.HOME,
            exitTransition = { fadeOut(animationSpec = tween(100)) },
            popEnterTransition = { fadeIn(animationSpec = tween(100)) }
        ) {
            Box {
                HomeScreen(
                    onStartRollCall      = { className ->
                        scope.launch {
                            val active = repo.getActiveSession(className)
                            val homeHandle = nav.getBackStackEntry(Routes.HOME).savedStateHandle
                            homeHandle["className"] = className
                            if (active != null) {
                                homeHandle["lastRoll"] = active.lastRoll
                                homeHandle["markedCount"] = active.markedCount
                                homeHandle["summaryStage"] =
                                    active.stage == PreferencesManager.SESSION_STAGE_SUMMARY
                                homeHandle["markPresentStage"] =
                                    active.stage == PreferencesManager.SESSION_STAGE_MARK_PRESENT
                                homeHandle["manualAttendanceStage"] =
                                    active.stage == PreferencesManager.SESSION_STAGE_MANUAL_ATTENDANCE
                                nav.navigate(Routes.SESSION_RECOV) {
                                    launchSingleTop = true
                                }
                            } else {
                                homeHandle["lastRoll"] = 1
                                homeHandle["markedCount"] = 0
                                homeHandle["summaryStage"] = false
                                homeHandle["markPresentStage"] = false
                                homeHandle["manualAttendanceStage"] = false
                                nav.navigate(Routes.SESSION_START) {
                                    launchSingleTop = true
                                }
                            }
                        }
                    },
                    onNavigateToSettings = { showSettingsOverlay = true },
                    onAddClass           = { nav.navigate(Routes.ADD_CLASS) },
                    onDeleteClass        = { id, name ->
                        nav.currentBackStackEntry?.savedStateHandle?.set("classId",   id)
                        nav.currentBackStackEntry?.savedStateHandle?.set("className", name)
                        nav.navigate(Routes.DELETE_CLASS)
                    },
                    onRenameClass        = { id, name ->
                        nav.currentBackStackEntry?.savedStateHandle?.set("classId",   id)
                        nav.currentBackStackEntry?.savedStateHandle?.set("className", name)
                        nav.navigate(Routes.RENAME_CLASS)
                    },
                    onEditStudents       = { id, name ->
                        nav.currentBackStackEntry?.savedStateHandle?.set("classId",   id)
                        nav.currentBackStackEntry?.savedStateHandle?.set("className", name)
                        nav.navigate(Routes.EDIT_STUDENT)
                    },
                    onExportAttendance   = { id, name ->
                        nav.currentBackStackEntry?.savedStateHandle?.set("classId",   id)
                        nav.currentBackStackEntry?.savedStateHandle?.set("className", name)
                        nav.navigate(Routes.EXPORT_ATTENDANCE)
                    },
                    onHolidayEvent       = { id, name ->
                        nav.currentBackStackEntry?.savedStateHandle?.set("classId",   id)
                        nav.currentBackStackEntry?.savedStateHandle?.set("className", name)
                        nav.navigate(Routes.HOLIDAY_EVENT)
                    },
                    onOpenExportFolder   = {
                        openRollCheckSubFolder("Exports")
                    },
                    onChooseAttendanceFolder = {
                        openRollCheckSubFolder("Attendance")
                    },
                    onHowToUse           = { nav.navigate(Routes.HOW_TO_USE)   { launchSingleTop = true } },
                    onAboutUs            = { nav.navigate(Routes.ABOUT_US)    { launchSingleTop = true } },
                    onExcelColors        = { nav.navigate(Routes.EXCEL_COLORS) { launchSingleTop = true } }
                )

                if (showSettingsOverlay) {
                    SettingsScreen(
                        onBack = { showSettingsOverlay = false },
                        onChangePin = {
                            showSettingsOverlay = false
                            nav.navigate(Routes.VERIFY_PIN)
                        }
                    )
                }
            }
        }

        // Ã¢â€â‚¬Ã¢â€â‚¬ Add Class Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
        composable(
            route = Routes.ADD_CLASS,
            enterTransition = { fadeIn(animationSpec = tween(100)) },
            exitTransition = { fadeOut(animationSpec = tween(100)) },
            popEnterTransition = { fadeIn(animationSpec = tween(100)) },
            popExitTransition = { fadeOut(animationSpec = tween(100)) }
        ) {
            AddClassScreen(
                onBack         = { nav.popBackStack() },
                onClassCreated = { nav.popBackStack() }
            )
        }

        // Ã¢â€â‚¬Ã¢â€â‚¬ Delete Class Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
        composable(Routes.DELETE_CLASS) {
            val handle    = nav.previousBackStackEntry?.savedStateHandle
            val classId   = handle?.get<String>("classId")   ?: return@composable
            val className = handle.get<String>("className")  ?: classId
            DeleteClassScreen(
                classId   = classId,
                className = className,
                onBack    = { nav.popBackStack() },
                onDeleted = { nav.popBackStack() }
            )
        }

        // Ã¢â€â‚¬Ã¢â€â‚¬ Rename Class Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
        composable(Routes.RENAME_CLASS) {
            val handle    = nav.previousBackStackEntry?.savedStateHandle
            val classId   = handle?.get<String>("classId")   ?: return@composable
            val className = handle.get<String>("className")  ?: classId
            RenameClassScreen(
                classId   = classId,
                className = className,
                onBack    = { nav.popBackStack() },
                onRenamed = { nav.popBackStack() }
            )
        }

        // Ã¢â€â‚¬Ã¢â€â‚¬ Edit Student Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
        composable(Routes.EDIT_STUDENT) {
            val handle    = nav.previousBackStackEntry?.savedStateHandle
            val classId   = handle?.get<String>("classId")   ?: return@composable
            val className = handle.get<String>("className")  ?: classId
            EditStudentScreen(
                classId   = classId,
                className = className,
                onBack    = { nav.popBackStack() }
            )
        }

        // Ã¢â€â‚¬Ã¢â€â‚¬ Holiday / Event Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
        composable(Routes.HOLIDAY_EVENT) {
            val handle    = nav.previousBackStackEntry?.savedStateHandle
            val classId   = handle?.get<String>("classId")   ?: return@composable
            val className = handle.get<String>("className")  ?: classId
            HolidayEventScreen(
                classId   = classId,
                className = className,
                onBack    = { nav.popBackStack() }
            )
        }

        // Ã¢â€â‚¬Ã¢â€â‚¬ Export Attendance Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
        composable(Routes.EXPORT_ATTENDANCE) {
            val handle    = nav.previousBackStackEntry?.savedStateHandle
            val classId   = handle?.get<String>("classId")   ?: return@composable
            val className = handle.get<String>("className")  ?: classId
            ExportAttendanceScreen(
                classId   = classId,
                className = className,
                onBack    = { nav.popBackStack() }
            )
        }

        // Ã¢â€â‚¬Ã¢â€â‚¬ Session Start Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
        composable(Routes.SESSION_START) {
            val incomingClassId = nav.previousBackStackEntry
                ?.savedStateHandle?.get<String>("className")
                ?: nav.currentBackStackEntry?.savedStateHandle?.get<String>("className")
                ?: "Class"
            val classId = rememberSaveable(incomingClassId) { incomingClassId }
            SessionStartScreen(
                className      = classId,
                onBack         = { nav.popBackStack() },
                onStartSession = { startRoll: Int, endRoll: Int ->
                    val homeHandle = nav.getBackStackEntry(Routes.HOME).savedStateHandle
                    homeHandle["className"] = classId
                    homeHandle["startRoll"] = startRoll
                    homeHandle["endRoll"] = endRoll
                    homeHandle["fromRecovery"] = false
                    homeHandle["lastRoll"] = startRoll
                    homeHandle["markedCount"] = 0
                    homeHandle["summaryStage"] = false
                    homeHandle["markPresentStage"] = false
                    homeHandle["manualAttendanceStage"] = false
                    nav.currentBackStackEntry?.savedStateHandle?.set("className", classId)
                    nav.currentBackStackEntry?.savedStateHandle?.set("startRoll", startRoll)
                    nav.currentBackStackEntry?.savedStateHandle?.set("endRoll",   endRoll)
                    nav.currentBackStackEntry?.savedStateHandle?.set("fromRecovery", false)
                    nav.navigate(Routes.ROLL_CALL)
                },
                onMarkPresentStudents = {
                    val homeHandle = nav.getBackStackEntry(Routes.HOME).savedStateHandle
                    homeHandle["className"] = classId
                    homeHandle["fromRecovery"] = false
                    homeHandle["summaryStage"] = false
                    homeHandle["markPresentStage"] = false
                    homeHandle["manualAttendanceStage"] = false
                    nav.currentBackStackEntry?.savedStateHandle?.set("className", classId)
                    nav.currentBackStackEntry?.savedStateHandle?.set("fromRecovery", false)
                    nav.navigate(Routes.MARK_PRESENT)
                }
            )
        }

        // Ã¢â€â‚¬Ã¢â€â‚¬ Session Recovery Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
        composable(Routes.SESSION_RECOV) {
            val previousHandle = nav.previousBackStackEntry?.savedStateHandle
            val currentHandle = nav.currentBackStackEntry?.savedStateHandle
            val homeHandle = runCatching { nav.getBackStackEntry(Routes.HOME).savedStateHandle }.getOrNull()
            val seedClassId     = currentHandle?.get<String>("className")
                ?: previousHandle?.get<String>("className")
                ?: homeHandle?.get<String>("className")
                ?: "Class"
            val seedLastRoll    = currentHandle?.get<Int>("lastRoll")
                ?: previousHandle?.get<Int>("lastRoll")
                ?: homeHandle?.get<Int>("lastRoll")
                ?: 1
            val seedMarkedCount = currentHandle?.get<Int>("markedCount")
                ?: previousHandle?.get<Int>("markedCount")
                ?: homeHandle?.get<Int>("markedCount")
                ?: 0
            val seedSummaryStage = currentHandle?.get<Boolean>("summaryStage")
                ?: previousHandle?.get<Boolean>("summaryStage")
                ?: homeHandle?.get<Boolean>("summaryStage")
                ?: false
            val seedMarkPresentStage = currentHandle?.get<Boolean>("markPresentStage")
                ?: previousHandle?.get<Boolean>("markPresentStage")
                ?: homeHandle?.get<Boolean>("markPresentStage")
                ?: false
            val seedManualAttendanceStage = currentHandle?.get<Boolean>("manualAttendanceStage")
                ?: previousHandle?.get<Boolean>("manualAttendanceStage")
                ?: homeHandle?.get<Boolean>("manualAttendanceStage")
                ?: false
            var classId by rememberSaveable(seedClassId) { mutableStateOf(seedClassId) }
            var lastRoll by rememberSaveable(seedClassId) { mutableStateOf(seedLastRoll) }
            var markedCount by rememberSaveable(seedClassId) { mutableStateOf(seedMarkedCount) }
            var summaryStage by rememberSaveable(seedClassId) { mutableStateOf(seedSummaryStage) }
            var markPresentStage by rememberSaveable(seedClassId) { mutableStateOf(seedMarkPresentStage) }
            var manualAttendanceStage by rememberSaveable(seedClassId) { mutableStateOf(seedManualAttendanceStage) }

            LaunchedEffect(seedClassId) {
                if (seedClassId.isBlank() || seedClassId == "Class") {
                    if (!nav.popBackStack(Routes.HOME, inclusive = false)) {
                        nav.navigate(Routes.HOME) {
                            launchSingleTop = true
                        }
                    }
                    return@LaunchedEffect
                }

                val active = repo.getActiveSession(seedClassId)
                if (active == null) {
                    homeHandle?.set("className", seedClassId)
                    homeHandle?.set("lastRoll", 1)
                    homeHandle?.set("markedCount", 0)
                    homeHandle?.set("summaryStage", false)
                    homeHandle?.set("markPresentStage", false)
                    homeHandle?.set("manualAttendanceStage", false)
                    if (!nav.popBackStack(Routes.HOME, inclusive = false)) {
                        nav.navigate(Routes.HOME) {
                            launchSingleTop = true
                        }
                    }
                    return@LaunchedEffect
                }

                classId = active.classId
                lastRoll = active.lastRoll
                markedCount = active.markedCount
                summaryStage = active.stage == PreferencesManager.SESSION_STAGE_SUMMARY
                markPresentStage = active.stage == PreferencesManager.SESSION_STAGE_MARK_PRESENT
                manualAttendanceStage = active.stage == PreferencesManager.SESSION_STAGE_MANUAL_ATTENDANCE

                homeHandle?.set("className", active.classId)
                homeHandle?.set("lastRoll", active.lastRoll)
                homeHandle?.set("markedCount", active.markedCount)
                homeHandle?.set("summaryStage", summaryStage)
                homeHandle?.set("markPresentStage", markPresentStage)
                homeHandle?.set("manualAttendanceStage", manualAttendanceStage)
            }
            SessionRecoveryScreen(
                classId           = classId,
                lastRoll          = lastRoll,
                markedCount       = markedCount,
                isSummaryStage    = summaryStage,
                isMarkPresentStage = markPresentStage,
                isManualAttendanceStage = manualAttendanceStage,
                onBack            = { nav.popBackStack() },
                onContinueSession = {
                    if (summaryStage) {
                        nav.currentBackStackEntry?.savedStateHandle?.set("className", classId)
                        nav.navigate(Routes.SUMMARY)
                    } else if (markPresentStage) {
                        scope.launch {
                            repo.setActiveSessionStage(classId, PreferencesManager.SESSION_STAGE_MARK_PRESENT)
                            val homeHandle = nav.getBackStackEntry(Routes.HOME).savedStateHandle
                            homeHandle["className"] = classId
                            homeHandle["fromRecovery"] = true
                            nav.navigate(Routes.MARK_PRESENT) {
                                popUpTo(Routes.SESSION_RECOV) { inclusive = true }
                            }
                        }
                    } else if (manualAttendanceStage) {
                        scope.launch {
                            repo.setActiveSessionStage(classId, PreferencesManager.SESSION_STAGE_MANUAL_ATTENDANCE)
                            nav.currentBackStackEntry?.savedStateHandle?.set("className", classId)
                            nav.currentBackStackEntry?.savedStateHandle?.set("fromRecovery", true)
                            val homeHandle = nav.getBackStackEntry(Routes.HOME).savedStateHandle
                            homeHandle["className"] = classId
                            homeHandle["fromRecovery"] = true
                            nav.navigate(Routes.MANUAL_ATTEND) {
                                launchSingleTop = true
                            }
                        }
                    } else {
                        scope.launch {
                            repo.setActiveSessionStage(classId, PreferencesManager.SESSION_STAGE_ATTENDANCE)
                            val resumeStartRoll = if (markedCount > 0) lastRoll + 1 else lastRoll
                            val homeHandle = nav.getBackStackEntry(Routes.HOME).savedStateHandle
                            homeHandle["className"] = classId
                            homeHandle["startRoll"] = resumeStartRoll
                            homeHandle["endRoll"] = 999
                            homeHandle["fromRecovery"] = true
                            nav.navigate(Routes.ROLL_CALL) {
                                popUpTo(Routes.SESSION_RECOV) { inclusive = true }
                            }
                        }
                    }
                },
                onViewSummary     = {
                    nav.currentBackStackEntry?.savedStateHandle?.set("className", classId)
                    nav.navigate(Routes.SUMMARY)
                },
                onEnterManual     = {
                    scope.launch {
                        repo.setActiveSessionStage(classId, PreferencesManager.SESSION_STAGE_MANUAL_ATTENDANCE)
                        nav.currentBackStackEntry?.savedStateHandle?.set("className", classId)
                        nav.currentBackStackEntry?.savedStateHandle?.set("fromRecovery", true)
                        val homeHandle = nav.getBackStackEntry(Routes.HOME).savedStateHandle
                        homeHandle["className"] = classId
                        homeHandle["fromRecovery"] = true
                        nav.navigate(Routes.MANUAL_ATTEND) {
                            launchSingleTop = true
                        }
                    }
                },
                onDiscardSession = {
                    scope.launch {
                        repo.discardSession(classId)
                        nav.popBackStack()
                    }
                }
            )
        }

        // Ã¢â€â‚¬Ã¢â€â‚¬ Roll Call Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
        composable(Routes.ROLL_CALL) {
            val incomingHandle = nav.previousBackStackEntry?.savedStateHandle
            val currentHandle = nav.currentBackStackEntry?.savedStateHandle
            val homeHandle = runCatching { nav.getBackStackEntry(Routes.HOME).savedStateHandle }.getOrNull()
            val className = rememberSaveable(
                incomingHandle?.get<String>("className"),
                currentHandle?.get<String>("className"),
                homeHandle?.get<String>("className")
            ) {
                incomingHandle?.get<String>("className")
                    ?: currentHandle?.get<String>("className")
                    ?: homeHandle?.get<String>("className")
                    ?: "Class"
            }
            val startRoll = rememberSaveable(
                incomingHandle?.get<Int>("startRoll"),
                currentHandle?.get<Int>("startRoll"),
                homeHandle?.get<Int>("startRoll")
            ) {
                incomingHandle?.get<Int>("startRoll")
                    ?: currentHandle?.get<Int>("startRoll")
                    ?: homeHandle?.get<Int>("startRoll")
                    ?: 1
            }
            val endRoll = rememberSaveable(
                incomingHandle?.get<Int>("endRoll"),
                currentHandle?.get<Int>("endRoll"),
                homeHandle?.get<Int>("endRoll")
            ) {
                incomingHandle?.get<Int>("endRoll")
                    ?: currentHandle?.get<Int>("endRoll")
                    ?: homeHandle?.get<Int>("endRoll")
                    ?: 60
            }
            val fromRecovery = rememberSaveable(
                incomingHandle?.get<Boolean>("fromRecovery"),
                currentHandle?.get<Boolean>("fromRecovery"),
                homeHandle?.get<Boolean>("fromRecovery")
            ) {
                incomingHandle?.get<Boolean>("fromRecovery")
                    ?: currentHandle?.get<Boolean>("fromRecovery")
                    ?: homeHandle?.get<Boolean>("fromRecovery")
                    ?: false
            }
            RollCallScreen(
                className = className,
                startRoll = startRoll,
                endRoll   = endRoll,
                onExit    = {
                    nav.popBackStack(Routes.HOME, inclusive = false)
                },
                showDiscardOnBack = true,
                onFinish  = { cn ->
                    nav.currentBackStackEntry?.savedStateHandle?.set("className", cn)
                    nav.navigate(Routes.SUMMARY)
                }
            )
        }

        // Ã¢â€â‚¬Ã¢â€â‚¬ Mark Present Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
        composable(Routes.MARK_PRESENT) {
            val incomingHandle = nav.previousBackStackEntry?.savedStateHandle
            val homeHandle = runCatching { nav.getBackStackEntry(Routes.HOME).savedStateHandle }.getOrNull()
            val classId = incomingHandle?.get<String>("className")
                ?: nav.currentBackStackEntry?.savedStateHandle?.get<String>("className")
                ?: homeHandle?.get<String>("className")
                ?: "Class"
            val fromRecovery = incomingHandle?.get<Boolean>("fromRecovery")
                ?: nav.currentBackStackEntry?.savedStateHandle?.get<Boolean>("fromRecovery")
                ?: homeHandle?.get<Boolean>("fromRecovery")
                ?: false
            MarkPresentScreen(
                classId = classId,
                onBack    = {
                    if (fromRecovery) nav.popBackStack() else nav.popBackStack(Routes.HOME, inclusive = false)
                },
                onFinish  = { _: Set<String>, _: Set<String> ->
                    nav.currentBackStackEntry?.savedStateHandle?.set("className", classId)
                    nav.navigate(Routes.SUMMARY)
                },
                showDiscardOnBack = true
            )
        }

        // Ã¢â€â‚¬Ã¢â€â‚¬ Manual Attendance (kept for recovery flow only) Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
        composable(Routes.MANUAL_ATTEND) {
            val incomingHandle = nav.previousBackStackEntry?.savedStateHandle
            val homeHandle = runCatching { nav.getBackStackEntry(Routes.HOME).savedStateHandle }.getOrNull()
            val context = LocalContext.current
            val repo = remember(context) { AttendanceRepository.getInstance(context) }
            var className by remember {
                mutableStateOf(
                    incomingHandle?.get<String>("className")
                        ?: nav.currentBackStackEntry?.savedStateHandle?.get<String>("className")
                        ?: homeHandle?.get<String>("className")
                        ?: ""
                )
            }
            var students by remember(className) { mutableStateOf<List<Student>>(emptyList()) }
            var isLoadingStudents by remember(className) { mutableStateOf(true) }

            LaunchedEffect(className) {
                if (className.isBlank()) {
                    isLoadingStudents = false
                    students = emptyList()
                    return@LaunchedEffect
                }
                isLoadingStudents = true
                students = if (className == "Class") emptyList() else repo.getStudentsForClass(className)
                isLoadingStudents = false
            }

            val fromRecovery = incomingHandle?.get<Boolean>("fromRecovery")
                ?: nav.currentBackStackEntry?.savedStateHandle?.get<Boolean>("fromRecovery")
                ?: homeHandle?.get<Boolean>("fromRecovery")
                ?: false
            ManualAttendanceScreen(
                className = className,
                students = students,
                isLoading = isLoadingStudents,
                onBack    = {
                    if (!nav.popBackStack(Routes.HOME, inclusive = false)) {
                        nav.popBackStack()
                    }
                },
                onExitHomeAfterNoChanges = { lastRoll, markedCount ->
                    val homeHandle = nav.getBackStackEntry(Routes.HOME).savedStateHandle
                    homeHandle["className"] = className
                    homeHandle["lastRoll"] = lastRoll
                    homeHandle["markedCount"] = markedCount
                    homeHandle["summaryStage"] = false
                    homeHandle["markPresentStage"] = false
                    homeHandle["manualAttendanceStage"] = false
                    homeHandle["fromRecovery"] = false
                    nav.navigate(Routes.HOME) {
                        launchSingleTop = true
                        restoreState = true
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
                onBackToRecovery = {
                    scope.launch {
                        val active = repo.getActiveSession(className)
                        repo.setActiveSessionStage(className, PreferencesManager.SESSION_STAGE_ATTENDANCE)
                        val previousHandle = nav.previousBackStackEntry?.savedStateHandle
                        val homeHandle = nav.getBackStackEntry(Routes.HOME).savedStateHandle
                        homeHandle["className"] = className
                        homeHandle["lastRoll"] = active?.lastRoll ?: 1
                        homeHandle["markedCount"] = active?.markedCount ?: 0
                        homeHandle["summaryStage"] = false
                        homeHandle["markPresentStage"] = false
                        homeHandle["manualAttendanceStage"] = false
                        previousHandle?.set("className", className)
                        previousHandle?.set("lastRoll", active?.lastRoll ?: 1)
                        previousHandle?.set("markedCount", active?.markedCount ?: 0)
                        previousHandle?.set("summaryStage", false)
                        previousHandle?.set("markPresentStage", false)
                        previousHandle?.set("manualAttendanceStage", false)
                        if (nav.previousBackStackEntry?.destination?.route == Routes.SESSION_RECOV) {
                            nav.popBackStack()
                        } else {
                            nav.navigate(Routes.SESSION_RECOV) {
                                launchSingleTop = true
                                popUpTo(Routes.MANUAL_ATTEND) { inclusive = true }
                            }
                        }
                    }
                },
                showDiscardOnBack = true,
                onFinish  = { _: Set<String>, _: Set<String> ->
                    nav.currentBackStackEntry?.savedStateHandle?.set("className", className)
                    nav.navigate(Routes.SUMMARY)
                }
            )
        }

        // Ã¢â€â‚¬Ã¢â€â‚¬ Summary Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
        composable(Routes.SUMMARY) {
            val className = nav.previousBackStackEntry
                ?.savedStateHandle?.get<String>("className") ?: "Class"
            SummaryScreen(
                className = className,
                onUpdateAttendance = { success: Boolean ->
                    nav.currentBackStackEntry?.savedStateHandle?.set("className", className)
                    nav.currentBackStackEntry?.savedStateHandle?.set("success", success)
                    nav.navigate(Routes.RESULT)
                },
                onExitToHome = {
                    nav.popBackStack(Routes.HOME, inclusive = false)
                }
            )
        }

        // Ã¢â€â‚¬Ã¢â€â‚¬ Result Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
        composable(Routes.RESULT) {
            val incomingHandle = nav.previousBackStackEntry?.savedStateHandle
            val currentHandle = nav.currentBackStackEntry?.savedStateHandle
            val className = rememberSaveable(
                incomingHandle?.get<String>("className"),
                currentHandle?.get<String>("className")
            ) {
                incomingHandle?.get<String>("className")
                    ?: currentHandle?.get<String>("className")
                    ?: "Class"
            }
            val success = rememberSaveable(
                incomingHandle?.get<Boolean>("success"),
                currentHandle?.get<Boolean>("success")
            ) {
                incomingHandle?.get<Boolean>("success")
                    ?: currentHandle?.get<Boolean>("success")
                    ?: true
            }
            ResultScreen(
                success   = success,
                className = className,
                onHome    = { nav.popBackStack(Routes.HOME, inclusive = false) },
                onRetry   = { nav.popBackStack() }
            )
        }

        // Ã¢â€â‚¬Ã¢â€â‚¬ Settings Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
        composable(
            route = Routes.SETTINGS,
            enterTransition = { fadeIn(animationSpec = tween(100)) },
            exitTransition = { fadeOut(animationSpec = tween(100)) },
            popEnterTransition = { fadeIn(animationSpec = tween(100)) },
            popExitTransition = { fadeOut(animationSpec = tween(100)) }
        ) {
            SettingsScreen(
                onBack      = { nav.popBackStack() },
                onChangePin = { nav.navigate(Routes.VERIFY_PIN) }
            )
        }

        composable(Routes.VERIFY_PIN) {
            PinScreen(
                savedPin       = getSavedPinHash(),
                onPinCreated   = { false },
                onPinVerified  = {
                    nav.navigate(Routes.SET_NEW_PIN) {
                        popUpTo(Routes.VERIFY_PIN) { inclusive = true }
                    }
                },
                pinVerifier    = verifyPin,
                screenTitle    = "Confirm Current PIN",
                screenSubtitle = "Enter your current PIN to continue"
            )
        }

        composable(Routes.SET_NEW_PIN) {
            PinScreen(
                savedPin       = null,
                onPinCreated   = { pin: String ->
                    savePinHash(pin)
                    nav.navigate(Routes.SETTINGS) { popUpTo(Routes.SETTINGS) { inclusive = true } }
                    true
                },
                onPinVerified  = {},
                pinVerifier    = { false },
                disallowPinReuse = verifyPin,
                screenTitle    = "Set New PIN",
                screenSubtitle = "Choose a new 6-digit PIN"
            )
        }

        // Ã¢â€â‚¬Ã¢â€â‚¬ Info Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
        composable(
            route = Routes.HOW_TO_USE,
            enterTransition = { fadeIn(animationSpec = tween(100)) },
            exitTransition  = { fadeOut(animationSpec = tween(100)) }
        ) {
            HowToUseScreen(onBack = { nav.popBackStack() })
        }

        composable(
            route = Routes.ABOUT_US,
            enterTransition = { fadeIn(animationSpec = tween(100)) },
            exitTransition  = { fadeOut(animationSpec = tween(100)) }
        ) {
            AboutUsScreen(onBack = { nav.popBackStack() })
        }

        composable(
            route = Routes.EXCEL_COLORS,
            enterTransition = { fadeIn(animationSpec = tween(100)) },
            exitTransition  = { fadeOut(animationSpec = tween(100)) }
        ) {
            ExcelColorsScreen(onBack = { nav.popBackStack() })
        }
    }
}
