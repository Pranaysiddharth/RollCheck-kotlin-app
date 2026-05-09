package com.attendance.rollcheck.data.repository

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.pdf.PrintedPdfDocument
import android.provider.MediaStore
import android.util.Log
import com.attendance.rollcheck.data.local.db.ActiveSessionDao
import com.attendance.rollcheck.data.local.db.ClassDao
import com.attendance.rollcheck.data.local.db.ManualRecoveryBaselineDao
import com.attendance.rollcheck.data.local.db.RollCheckDatabase
import com.attendance.rollcheck.data.local.db.SessionAttendanceDao
import com.attendance.rollcheck.data.local.db.StudentDao
import com.attendance.rollcheck.data.local.excel.ExcelManager
import com.attendance.rollcheck.data.local.prefs.PreferencesManager
import com.attendance.rollcheck.domain.model.ActiveSessionEntity
import com.attendance.rollcheck.domain.model.ClassEntity
import com.attendance.rollcheck.domain.model.ClassInfo
import com.attendance.rollcheck.domain.model.ExcelValidationResult
import com.attendance.rollcheck.domain.model.ManualRecoveryBaselineEntity
import com.attendance.rollcheck.domain.model.SessionAttendanceEntity
import com.attendance.rollcheck.domain.model.SessionInfo
import com.attendance.rollcheck.domain.model.Student
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipFile
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class AttendanceRepository private constructor(private val context: Context) {
    private val db: RollCheckDatabase = RollCheckDatabase.getInstance(context)
    private val classDao: ClassDao = db.classDao()
    private val studentDao: StudentDao = db.studentDao()
    private val sessionDao: SessionAttendanceDao = db.sessionAttendanceDao()
    private val activeDao: ActiveSessionDao = db.activeSessionDao()
    private val manualBaselineDao: ManualRecoveryBaselineDao = db.manualRecoveryBaselineDao()
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val exportMutex = Mutex()
    private val classesFlow = classDao.getAllClasses()
        .map { list ->
            list.map { ClassInfo(it.id, it.className, it.studentCount, it.rollPrefix, it.filePath) }
        }
        .stateIn(repoScope, SharingStarted.Eagerly, null)

    suspend fun ensurePublicFoldersExist() = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val collection = MediaStore.Files.getContentUri("external")

                val folders = listOf("Documents/RollCheck/Attendance", "Documents/RollCheck/Exports")
                for (folderPath in folders) {
                    val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
                    val selectionArgs = arrayOf("$folderPath/")
                    val cursor = resolver.query(collection, arrayOf(MediaStore.MediaColumns.RELATIVE_PATH), selection, selectionArgs, null)
                    val exists = cursor?.use { it.count > 0 } ?: false

                    if (!exists) {
                        val contentValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.RELATIVE_PATH, folderPath)
                            put(MediaStore.MediaColumns.IS_PENDING, 1)
                        }
                        resolver.insert(collection, contentValues)?.let { uri ->
                            contentValues.clear()
                            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                            resolver.update(uri, contentValues, null, null)
                        }
                    }
                }
            } else {
                val docDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                File(docDir, "RollCheck/Attendance").mkdirs()
                File(docDir, "RollCheck/Exports").mkdirs()
            }
        } catch (e: Exception) {
            Log.e("AttendanceRepository", "ensurePublicFoldersExist failed", e)
        }
    }

    fun observeClasses(): Flow<List<ClassInfo>?> = classesFlow

    suspend fun getClassById(classId: String): ClassInfo? =
        classDao.getClassById(classId)?.let {
            ClassInfo(it.id, it.className, it.studentCount, it.rollPrefix, it.filePath)
        }

    fun validateExcelFile(uri: Uri): ExcelValidationResult =
        ExcelManager.validateFile(context, uri)

    suspend fun classNameExists(name: String): Boolean = classDao.classNameExists(name)

    suspend fun classExistsById(classId: String): Boolean = classDao.classExists(classId)

    suspend fun addClass(className: String, rollPrefix: String, uri: Uri): String? = withContext(Dispatchers.IO) {
        if (classDao.classNameExists(className)) throw Exception("Class '$className' already exists")

        val classId = UUID.randomUUID().toString()
        val filePath = ExcelManager.importFile(context, uri, className)
        if (filePath.isEmpty()) return@withContext null

        val students = ExcelManager.parseStudents(filePath, classId)
        val entity = ClassEntity(
            id = classId,
            className = className,
            filePath = filePath,
            rollPrefix = rollPrefix,
            studentCount = students.size
        )
        classDao.insertClass(entity)
        studentDao.insertStudents(students)
        copyToAttendanceFolder(filePath, "${className}_Attendance.xlsx")
        classId
    }

    suspend fun deleteClass(classId: String, className: String, filePath: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. Copy the internal master file to the public 'Exports' folder as a backup
            filePath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    copyToExportFolder(path, "${className}_Backup.xlsx")
                }
            }

            // 2. Delete the public attendance file if it exists
            deleteFromPublicFolder("${className}_Attendance.xlsx", "Documents/RollCheck/Attendance")

            // 3. Delete from Local Database
            classDao.deleteClassById(classId)
            studentDao.deleteStudentsForClass(classId)

            // 4. Delete the internal master copy
            filePath?.let {
                val file = File(it)
                if (file.exists()) {
                    val deleted = file.delete()
                    Log.d("AttendanceRepository", "Deleted internal master copy: $it, success: $deleted")
                }
            }
            true
        } catch (e: Exception) {
            Log.e("AttendanceRepository", "Delete class failed", e)
            false
        }
    }

    private fun deleteFromPublicFolder(fileName: String, relativePath: String) {
        try {
            val resolver = context.contentResolver
            val collection = MediaStore.Files.getContentUri("external")

            // Search by name only (case-insensitive) to be safe
            val selection = "LOWER(${MediaStore.MediaColumns.DISPLAY_NAME}) = LOWER(?)"
            val selectionArgs = arrayOf(fileName)

            resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.DISPLAY_NAME), selection, selectionArgs, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val foundPath = cursor.getString(pathCol)
                    val foundName = cursor.getString(nameCol)
                    // Check if path matches our target (case-insensitive)
                    if (foundPath.contains(relativePath, ignoreCase = true) || relativePath.contains(foundPath, ignoreCase = true)) {
                        val id = cursor.getLong(idCol)
                        val uri = ContentUris.withAppendedId(collection, id)
                        val deleted = resolver.delete(uri, null, null)
                        Log.d("AttendanceRepository", "Deleted public file: $foundName at $foundPath, success: ${deleted > 0}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AttendanceRepository", "deleteFromPublicFolder failed", e)
        }
    }

    suspend fun getTodaySessionCount(classId: String): Int = withContext(Dispatchers.IO) {
        val entity = classDao.getClassById(classId) ?: return@withContext 0
        ExcelManager.getTodaySessionCount(entity.filePath)
    }

    suspend fun getSessionStartMeta(classId: String): Pair<Pair<Int, Int>, Int> = withContext(Dispatchers.IO) {
        val entity = classDao.getClassById(classId) ?: return@withContext Pair(Pair(0, 0), 0)
        ExcelManager.getRollRangeAndTodaySessionCount(entity.filePath)
    }

    suspend fun getActiveStudentCount(classId: String): Int = withContext(Dispatchers.IO) {
        studentDao.getActiveStudentCount(classId)
    }

    suspend fun renameClass(classId: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val entity = classDao.getClassById(classId) ?: return@withContext false
            val oldClassName = entity.className
            val oldFile = File(entity.filePath)

            if (oldFile.exists()) {
                val newFileName = "${newName}_MasterQM.xlsx"
                val newFile = File(oldFile.parentFile, newFileName)
                if (oldFile.renameTo(newFile)) {
                    classDao.updateClassDetails(classId, newName, newFile.absolutePath)
                    renameInPublicFolder(
                        "${oldClassName}_Attendance.xlsx",
                        "${newName}_Attendance.xlsx",
                        "Documents/RollCheck/Attendance"
                    )
                    return@withContext true
                }
            }
            classDao.updateClassName(classId, newName)
            true
        } catch (e: Exception) {
            Log.e("AttendanceRepository", "Rename class failed", e)
            false
        }
    }

    private fun renameInPublicFolder(oldName: String, newName: String, relativePath: String) {
        try {
            val resolver = context.contentResolver
            val collection = MediaStore.Files.getContentUri("external")

            // Search by name only (case-insensitive)
            val selection = "LOWER(${MediaStore.MediaColumns.DISPLAY_NAME}) = LOWER(?)"
            val selectionArgs = arrayOf(oldName)

            resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.RELATIVE_PATH), selection, selectionArgs, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)

                while (cursor.moveToNext()) {
                    val foundPath = cursor.getString(pathCol)
                    if (foundPath.contains(relativePath, ignoreCase = true) || relativePath.contains(foundPath, ignoreCase = true)) {
                        val id = cursor.getLong(idCol)
                        val uri = ContentUris.withAppendedId(collection, id)
                        val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, newName) }
                        val updated = resolver.update(uri, values, null, null)
                        Log.d("AttendanceRepository", "Renamed public file: $oldName to $newName, success: ${updated > 0}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AttendanceRepository", "renameInPublicFolder failed", e)
        }
    }

    // ── Students ──────────────────────────────────────────────────────────────

    suspend fun getStudentsForClass(classId: String): List<Student> =
        withContext(Dispatchers.IO) {
            getSessionStudentsForClass(classId)?.let { return@withContext it }
            syncStudentsFromExcel(classId)
            studentDao.getActiveStudentsForClass(classId).map {
                Student(it.studentId, it.name, it.inactiveFlag, it.threshold)
            }
        }

    suspend fun getCachedStudentsForClass(classId: String): List<Student> =
        withContext(Dispatchers.IO) {
            getSessionStudentsForClass(classId)?.let { return@withContext it }
            studentDao.getActiveStudentsForClass(classId).map {
                Student(it.studentId, it.name, it.inactiveFlag, it.threshold)
            }
        }

    suspend fun getAllStudentsForClass(classId: String): List<Student> =
        withContext(Dispatchers.IO) {
            syncStudentsFromExcel(classId)
            studentDao.getStudentsForClass(classId).map {
                Student(it.studentId, it.name, it.inactiveFlag, it.threshold)
            }
        }

    suspend fun getRollRangeFromExcel(classId: String): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val entity = classDao.getClassById(classId) ?: return@withContext Pair(0, 0)
        ExcelManager.getRollRange(entity.filePath)
    }

    // ── Session lifecycle ─────────────────────────────────────────────────────

    suspend fun getActiveSession(classId: String): SessionInfo? = withContext(Dispatchers.IO) {
        activeDao.getActiveSession(classId)?.let {
            SessionInfo(
                it.classId,
                it.startRoll,
                it.endRoll,
                it.lastRoll,
                it.markedCount,
                it.continuousSessionCount,
                it.stage
            )
        }
    }

    suspend fun startSession(
        classId: String,
        startRoll: Int,
        endRoll: Int,
        continuousSessionCount: Int = 1
    ) = withContext(Dispatchers.IO) {
        db.withTransaction {
            sessionDao.clearSession(classId)
            val students = studentDao.getActiveStudentsForClass(classId)
            val records = students.map {
                SessionAttendanceEntity(studentId = it.studentId, classId = classId, name = it.name, status = -1)
            }
            sessionDao.insertAll(records)
            val active = ActiveSessionEntity(
                classId = classId,
                startRoll = startRoll,
                endRoll = endRoll,
                lastRoll = startRoll,
                markedCount = 0,
                continuousSessionCount = continuousSessionCount.coerceIn(1, 10),
                stage = PreferencesManager.SESSION_STAGE_ATTENDANCE
            )
            activeDao.saveSession(active)
            manualBaselineDao.clearForClass(classId)
        }
    }

    suspend fun updateSessionProgress(classId: String, lastRoll: Int, markedCount: Int) = withContext(Dispatchers.IO) {
        activeDao.updateProgress(classId, lastRoll, markedCount)
    }

    suspend fun markStudent(classId: String, studentId: String, status: Int) = withContext(Dispatchers.IO) {
        sessionDao.updateStatus(classId, studentId, status)
    }

    suspend fun markAll(classId: String, status: Int) = withContext(Dispatchers.IO) {
        sessionDao.markAllAs(classId, status)
    }

    suspend fun getSessionAttendance(classId: String): List<SessionAttendanceEntity> =
        withContext(Dispatchers.IO) { sessionDao.getSessionAttendance(classId) }

    suspend fun finalSave(classId: String): Boolean = withContext(Dispatchers.IO) {
        exportMutex.withLock {
            val entity = classDao.getClassById(classId) ?: return@withContext false
            val records = sessionDao.getSessionAttendance(classId)
            val map = records.filter { it.status != -1 }.associate { it.studentId to it.status }
            val activeSession = activeDao.getActiveSession(classId)
                ?: return@withContext false
            val continuousSessionCount = activeSession.continuousSessionCount
            if (continuousSessionCount !in 1..10) return@withContext false
            val success = ExcelManager.writeAttendance(
                filePath = entity.filePath,
                attendanceMap = map,
                continuousSessionCount = continuousSessionCount
            )
            if (success) {
                copyToAttendanceFolder(entity.filePath, "${entity.className}_Attendance.xlsx")
                db.withTransaction {
                    sessionDao.clearSession(classId)
                    activeDao.clearSession(classId)
                    manualBaselineDao.clearForClass(classId)
                }
            }
            success
        }
    }

    suspend fun discardSession(classId: String) = withContext(Dispatchers.IO) {
        db.withTransaction {
            sessionDao.clearSession(classId)
            activeDao.clearSession(classId)
            manualBaselineDao.clearForClass(classId)
        }
    }

    suspend fun setActiveSessionStage(classId: String, stage: String) = withContext(Dispatchers.IO) {
        activeDao.updateStage(classId, stage)
    }

    suspend fun getManualRecoveryBaseline(classId: String): Set<String>? = withContext(Dispatchers.IO) {
        val ids = manualBaselineDao.getStudentIdsForClass(classId)
        if (ids.isEmpty()) {
            null
        } else {
            ids
                .filterNot { it == MANUAL_BASELINE_SENTINEL }
                .toSet()
        }
    }

    suspend fun saveManualRecoveryBaseline(classId: String, studentIds: Set<String>) = withContext(Dispatchers.IO) {
        db.withTransaction {
            manualBaselineDao.clearForClass(classId)
            val persistedIds = buildList {
                add(MANUAL_BASELINE_SENTINEL)
                addAll(studentIds)
            }
            manualBaselineDao.insertAll(
                persistedIds.map { studentId ->
                    ManualRecoveryBaselineEntity(classId = classId, studentId = studentId)
                }
            )
        }
    }

    private suspend fun getSessionStudentsForClass(classId: String): List<Student>? {
        if (activeDao.getActiveSession(classId) == null) return null
        val activeStudentsById = studentDao.getActiveStudentsForClass(classId).associateBy { it.studentId }
        val sessionStudents = sessionDao.getSessionAttendance(classId)
            .map { record ->
                val student = activeStudentsById[record.studentId]
                Student(
                    studentId = record.studentId,
                    name = record.name,
                    inactiveFlag = student?.inactiveFlag ?: 0,
                    threshold = student?.threshold ?: 75.0
                )
            }
            .sortedBy { extractRollInt(it.studentId) }
        return if (sessionStudents.isEmpty()) {
            null
        } else {
            sessionStudents
        }
    }

    private fun extractRollInt(studentId: String): Int =
        studentId.trim().takeLastWhile { it.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE

    suspend fun copyToAttendanceFolder(sourcePath: String, destName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val relativePath = "Documents/RollCheck/Attendance/"

                deleteFromPublicFolder(destName, "Documents/RollCheck/Attendance")

                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, destName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val uri = resolver.insert(collection, contentValues) ?: return@withContext false

                try {
                    resolver.openOutputStream(uri)?.use { output ->
                        FileInputStream(sourcePath).use { input -> input.copyTo(output) }
                        output.flush()
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                    true
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    false
                }
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "RollCheck/Attendance")
                if (!dir.exists()) dir.mkdirs()
                val destFile = File(dir, destName)
                FileInputStream(sourcePath).use { input ->
                    FileOutputStream(destFile).use { output -> input.copyTo(output) }
                }
                android.media.MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), null, null)
                true
            }
        } catch (e: Exception) {
            Log.e("AttendanceRepository", "copyToAttendanceFolder failed", e)
            false
        }
    }

    suspend fun copyToExportFolder(sourcePath: String, destName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(sourcePath)
            if (!sourceFile.exists()) {
                Log.e("AttendanceRepository", "copyToExportFolder: Source file not found: $sourcePath")
                return@withContext false
            }

            var finalDestName = destName
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val relativePath = "Documents/RollCheck/Exports/"

                val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
                val selection = "LOWER(${MediaStore.MediaColumns.DISPLAY_NAME}) = LOWER(?) AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf(destName, "Documents/RollCheck/Exports%")

                resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                    if (cursor.count > 0) {
                        val nameWithoutExt = destName.substringBeforeLast(".")
                        val ext = destName.substringAfterLast(".", "")
                        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                        finalDestName = "${nameWithoutExt}_$timestamp.$ext"
                    }
                }

                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, finalDestName)
                    put(MediaStore.MediaColumns.MIME_TYPE, if (finalDestName.endsWith(".pdf")) "application/pdf" else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val uri = resolver.insert(collection, contentValues) ?: return@withContext false

                try {
                    resolver.openOutputStream(uri)?.use { output ->
                        FileInputStream(sourceFile).use { input -> input.copyTo(output) }
                        output.flush()
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                    true
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    false
                }
            } else {
                val exportDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "RollCheck/Exports")
                if (!exportDir.exists()) exportDir.mkdirs()
                var destFile = File(exportDir, destName)
                if (destFile.exists()) {
                    val nameWithoutExt = destName.substringBeforeLast(".")
                    val ext = destName.substringAfterLast(".", "")
                    val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                    destFile = File(exportDir, "${nameWithoutExt}_$timestamp.$ext")
                }
                FileInputStream(sourceFile).use { input ->
                    FileOutputStream(destFile).use { output -> input.copyTo(output) }
                }
                android.media.MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), null, null)
                true
            }
        } catch (e: Exception) {
            Log.e("AttendanceRepository", "copyToExportFolder failed", e)
            false
        }
    }

    suspend fun exportClassExcelToExportFolder(classId: String): Boolean = withContext(Dispatchers.IO) {
        exportMutex.withLock {
            val entity = classDao.getClassById(classId) ?: return@withContext false
            copyToExportFolder(entity.filePath, "${entity.className}_Attendance.xlsx")
        }
    }

    suspend fun studentNameExistsForClassExcept(
        classId: String,
        studentId: String,
        name: String
    ): Boolean = withContext(Dispatchers.IO) {
        studentDao.studentNameExistsForClassExcept(classId, studentId, name)
    }

    suspend fun updateStudentDetails(
        classId: String,
        studentId: String,
        name: String,
        inactiveFlag: Int,
        threshold: Double
    ): Boolean = withContext(Dispatchers.IO) {
        exportMutex.withLock {
            val entity = classDao.getClassById(classId) ?: return@withContext false
            val fileUpdated = ExcelManager.updateStudentDetails(
                filePath = entity.filePath,
                studentId = studentId,
                newName = name,
                inactiveFlag = inactiveFlag,
                threshold = threshold
            )
            if (!fileUpdated) return@withContext false
            studentDao.updateStudentFields(classId, studentId, name, inactiveFlag, threshold)
            copyToAttendanceFolder(entity.filePath, "${entity.className}_Attendance.xlsx")
            true
        }
    }

    // ── PRINT FRAMEWORK: MS Office-style PDF export ──────────────────────────

    /**
     * Triggers the Android system Print Dialog (blue/white preview) for the given class.
     *
     * Produces three logical sections, all on A4 LANDSCAPE pages:
     *
     *  SECTION 1 — ROSTER
     *    Student ID | Name | Inactive badge | Threshold % | Att %
     *    Paginated vertically (≈ 28 students/page) until all students are shown.
     *
     *  SECTION 2 — HOLIDAY REGISTER
     *    Two-column table: Date | Event Name.
     *    Paginated vertically if needed.
     *
     *  SECTION 3 — MONTHLY ATTENDANCE GRIDS
     *    One set of pages per calendar month (one page per ~26 students).
     *    Fixed columns: Student ID + Student Name.
     *    Then one narrow column per class-day in that month.
     *    Each date column header has two stacked rows:
     *      • Top  half (amber bg) → day abbreviation: Mon / Tue / Wed …
     *      • Bottom half (navy bg) → day-of-month only: 05 / 12 / 23 …
     *        (month + year already shown in the page banner)
     *    Data cells: P (green) / A (red) / – (grey / inactive).
     *
     * @param uiContext  Activity context (required for PrintManager).
     * @param classId    Target class.
     */
    suspend fun printAttendancePdf(uiContext: Context, classId: String): Boolean = withContext(Dispatchers.IO) {
        exportMutex.withLock {
            val entity = classDao.getClassById(classId) ?: return@withContext false
            val excelFile = File(entity.filePath)
            if (!excelFile.exists()) return@withContext false

            try {
                val reportData = parseExcelForPrint(entity.className, excelFile)
                val tempPdf = File(context.cacheDir, "${entity.id}_attendance_report.pdf")
                ClassListPrintAdapter(context, reportData).writeAllPagesTo(tempPdf)
                val exported = copyToExportFolder(tempPdf.absolutePath, "${entity.className}_Attendance_Report.pdf")
                tempPdf.delete()
                exported
            } catch (e: Exception) {
                Log.e("AttendanceRepository", "printAttendancePdf failed", e)
                false
            }
        }
    }

    // ── Data models ───────────────────────────────────────────────────────────

    /** One date column read from the sheet. */
    private data class DateCol(
        val dayName: String,   // "Mon", "Tue" … (row 0 of sheet)
        val dateLabel: String, // "05-01-2026"    (row 1 of sheet)
        val colIndex: Int      // 0-based sheet column index
    )

    /** All date columns for one calendar month. */
    private data class MonthGroup(
        val displayLabel: String, // "January 2026"
        val cols: List<DateCol>
    )

    /** Everything the renderer needs, fully evaluated — no formulas. */
    private data class ReportData(
        val className: String,
        val generatedOn: String,
        /** Each row: [studentId, name, inactive(0/1), threshold, attPct, dateVal0, dateVal1, …]
         *  All values are already evaluated strings, never formula text. */
        val rows: List<List<String>>,
        /** Date columns in sheet order (index 5, 6, 7 … in each row). */
        val dateCols: List<DateCol>,
        /** Date columns grouped by calendar month, chronological order. */
        val monthGroups: List<MonthGroup>,
        /** Holiday entries: "26-01-2026" → "Republic Day". */
        val holidays: List<Pair<String, String>>
    )

    // ── Excel parser ──────────────────────────────────────────────────────────

    private fun parseSharedStrings(zip: ZipFile): List<String> {
        val entry = zip.getEntry("xl/sharedStrings.xml") ?: return emptyList()
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }.newPullParser()
        zip.getInputStream(entry).use { input ->
            parser.setInput(input, "UTF-8")
            val strings = mutableListOf<String>()
            var current: StringBuilder? = null
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "si" -> current = StringBuilder()
                            "t" -> {
                                val holder = current
                                if (holder != null) holder.append(parser.nextText())
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "si") {
                            strings += current?.toString().orEmpty()
                            current = null
                        }
                    }
                }
                event = parser.next()
            }
            return strings
        }
    }

    private fun colRefToIndex(cellRef: String): Int {
        var result = 0
        for (ch in cellRef) {
            if (!ch.isLetter()) break
            result = result * 26 + (ch.uppercaseChar() - 'A' + 1)
        }
        return result - 1
    }

    private fun parseSheetValues(zip: ZipFile, sharedStrings: List<String>): Map<Int, Map<Int, String>> {
        val entry = zip.getEntry("xl/worksheets/sheet1.xml") ?: return emptyMap()
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }.newPullParser()
        zip.getInputStream(entry).use { input ->
            parser.setInput(input, "UTF-8")
            val rows = linkedMapOf<Int, MutableMap<Int, String>>()
            var currentRow = -1
            var currentCol = -1
            var currentType: String? = null
            var rawValue: String? = null
            var inlineValue: String? = null

            fun commitCell() {
                if (currentRow < 0 || currentCol < 0) return
                val resolved = when (currentType) {
                    "s" -> rawValue?.toIntOrNull()?.let { idx -> sharedStrings.getOrNull(idx) }.orEmpty()
                    "inlineStr" -> inlineValue.orEmpty()
                    else -> rawValue.orEmpty()
                }
                rows.getOrPut(currentRow) { linkedMapOf() }[currentCol] = resolved
            }

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "row" -> {
                                currentRow = (parser.getAttributeValue(null, "r")?.toIntOrNull() ?: 1) - 1
                            }
                            "c" -> {
                                val ref = parser.getAttributeValue(null, "r").orEmpty()
                                currentCol = colRefToIndex(ref)
                                currentType = parser.getAttributeValue(null, "t")
                                rawValue = null
                                inlineValue = null
                            }
                            "v" -> rawValue = parser.nextText()
                            "t" -> {
                                if (currentType == "inlineStr") {
                                    inlineValue = (inlineValue ?: "") + parser.nextText()
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "c") {
                            commitCell()
                            currentCol = -1
                            currentType = null
                            rawValue = null
                            inlineValue = null
                        }
                    }
                }
                event = parser.next()
            }
            return rows
        }
    }

    private fun cellText(sheetRows: Map<Int, Map<Int, String>>, rowIndex: Int, colIndex: Int): String =
        sheetRows[rowIndex]?.get(colIndex)?.trim().orEmpty()

    private fun printableSessionSpan(label: String): Int =
        Regex("""\(c(\d+)\)""").find(label)?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1

    private fun resolvePrintableAttendance(rowData: List<String>, dateCols: List<DateCol>): String {
        val cached = rowData.getOrElse(4) { "" }.trim()
        cached.toDoubleOrNull()?.let { return String.format(Locale.US, "%.2f", it) }
        if (cached.equals("N/A", ignoreCase = true)) return "N/A"
        if (rowData.getOrElse(2) { "" }.trim() == "1") return "N/A"

        var presentWeight = 0
        var totalWeight = 0
        dateCols.forEachIndexed { index, dateCol ->
            val raw = rowData.getOrElse(5 + index) { "" }.trim()
            val numeric = raw.toIntOrNull() ?: raw.toDoubleOrNull()?.toInt() ?: return@forEachIndexed
            val span = printableSessionSpan(dateCol.dateLabel)
            if (numeric > 0) {
                presentWeight += numeric
                totalWeight += numeric
            } else if (numeric == 0) {
                totalWeight += span
            }
        }

        if (totalWeight <= 0) return "0.00"
        val percentage = kotlin.math.round((presentWeight.toDouble() / totalWeight.toDouble()) * 10000.0) / 100.0
        return String.format(Locale.US, "%.2f", percentage)
    }

    private fun parseExcelForPrint(className: String, excelFile: File): ReportData {
        val rows     = mutableListOf<List<String>>()
        val dateCols = mutableListOf<DateCol>()
        val holidays = mutableListOf<Pair<String, String>>()

        ZipFile(excelFile).use { zip ->
            val sharedStrings = parseSharedStrings(zip)
            val sheetRows = parseSheetValues(zip, sharedStrings)

            var ci = 5
            while (true) {
                val label = cellText(sheetRows, 1, ci)
                if (label.isBlank()) break
                val day = cellText(sheetRows, 0, ci)
                dateCols += DateCol(dayName = day, dateLabel = label, colIndex = ci)
                ci++
            }

            var inHolidaySection = false
            val maxRowIndex = (sheetRows.keys.maxOrNull() ?: 1)
            for (ri in 2..maxRowIndex) {
                val c0 = cellText(sheetRows, ri, 0)
                val c1 = cellText(sheetRows, ri, 1)

                val isHolidayHeader = c0.equals("Holiday / Event Register", ignoreCase = true) ||
                    c0.equals("Holiday Date", ignoreCase = true) ||
                    c0.equals("Holiday / Event Date", ignoreCase = true) ||
                    (c0.contains("Holiday", ignoreCase = true) && c1.contains("Event", ignoreCase = true))

                if (isHolidayHeader) {
                    inHolidaySection = true
                    continue
                }

                if (inHolidaySection) {
                    if (c0.isBlank() && c1.isBlank()) continue
                    if (c0.contains("Holiday", ignoreCase = true) && c1.contains("Event", ignoreCase = true)) continue
                    if (c0.isNotBlank() && c1.isNotBlank()) holidays += c0 to c1
                    continue
                }

                if (c0.isBlank()) continue
                if (c0.none { it.isDigit() }) continue

                val rowData = mutableListOf<String>()
                for (baseCol in 0..4) {
                    rowData += cellText(sheetRows, ri, baseCol)
                }
                for (dc in dateCols) {
                    rowData += cellText(sheetRows, ri, dc.colIndex)
                }
                rowData[4] = resolvePrintableAttendance(rowData, dateCols)
                rows += rowData
            }
        }

        // Group date columns by "MM-yyyy" key → MonthGroup
        val grouped   = LinkedHashMap<String, MutableList<DateCol>>()
        for (dc in dateCols) {
            // Strip any multi-session suffix like "(1)", "(2)" before extracting the
            // month key — otherwise "05-01-2026(1)" produces key "01-2026(1)" which
            // creates a duplicate month group and generates extra pages.
            val cleanLabel = dc.dateLabel.substringBefore("(")
            val parts = cleanLabel.split("-")
            val key   = if (parts.size == 3) "${parts[1]}-${parts[2]}" else cleanLabel
            grouped.getOrPut(key) { mutableListOf() } += dc
        }
        val mFmt   = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault())
        val kFmt   = java.text.SimpleDateFormat("MM-yyyy",   java.util.Locale.getDefault())
        val monthGroups = grouped.map { (key, cols) ->
            val label = try { mFmt.format(kFmt.parse(key)!!) } catch (_: Exception) { key }
            MonthGroup(displayLabel = label, cols = cols)
        }

        val sdf = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
        return ReportData(
            className   = className,
            generatedOn = sdf.format(java.util.Date()),
            rows        = rows,
            dateCols    = dateCols,
            monthGroups = monthGroups,
            holidays    = holidays
        )
    }

    // ── PrintDocumentAdapter ──────────────────────────────────────────────────

    private class ClassListPrintAdapter(
        private val context: Context,
        private val data: ReportData
    ) : PrintDocumentAdapter() {

        // ── Palette ───────────────────────────────────────────────────────────
        private val C_NAVY     = Color.parseColor("#1F3864")
        private val C_NAVY_LT  = Color.parseColor("#2E4DA0")
        private val C_AMBER    = Color.parseColor("#F9C74F")
        private val C_WHITE    = Color.WHITE
        private val C_STRIPE_A = Color.parseColor("#EFF3FB")
        private val C_STRIPE_B = Color.WHITE
        private val C_INACTIVE = Color.parseColor("#F2DCDB")
        private val C_BORDER   = Color.parseColor("#BDC7E0")
        private val C_BORDER_D = Color.parseColor("#4A6FA5")
        private val C_PRESENT  = Color.parseColor("#375623")
        private val C_ABSENT   = Color.parseColor("#9C0006")
        private val C_NA       = Color.parseColor("#7F7F7F")
        private val C_FOOTER   = Color.parseColor("#595959")
        private val C_GREEN_BG = Color.parseColor("#E2EFDA")
        private val C_RED_BG   = Color.parseColor("#FCE4D6")

        // ── Typefaces ─────────────────────────────────────────────────────────
        private val T_MONO = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        private val T_SANS = Typeface.create("sans-serif", Typeface.NORMAL)
        private val T_BOLD = Typeface.create("sans-serif", Typeface.BOLD)

        // ── Fixed A4 LANDSCAPE geometry (points at 72 pt/inch) ────────────────
        //    A4 landscape = 841.89 × 595.28 pt → we use 842 × 595
        private val PW     = 842f   // page width
        private val PH     = 595f   // page height
        private val MAR    = 28f    // margin all sides
        private val UW     = PW - 2f * MAR    // 786 pt usable width
        private val UH     = PH - 2f * MAR    // 539 pt usable height

        // ── Row/section heights ───────────────────────────────────────────────
        private val TITLE_H  = 42f   // top banner
        private val FOOTER_H = 18f   // bottom footer
        private val HDR_H    = 24f   // single column-header row
        private val ROW_H    = 15f   // data row

        // Usable body height below banner and above footer
        private val BODY_H   = UH - TITLE_H - FOOTER_H   // 539 - 42 - 18 = 479

        // ── Per-section row capacities ────────────────────────────────────────
        // Roster: one header row, then data rows
        private val ROSTER_ROWS_PER_PAGE = ((BODY_H - HDR_H) / ROW_H).toInt().coerceAtLeast(1)

        // Holiday: same single-header layout
        private val HOLIDAY_ROWS_PER_PAGE = ROSTER_ROWS_PER_PAGE

        // Monthly: DOUBLE header (day-name row + date-number row), then data rows
        private val MONTH_HDR_H          = HDR_H * 2f
        private val MONTH_ROWS_PER_PAGE  = ((BODY_H - MONTH_HDR_H) / ROW_H).toInt().coerceAtLeast(1)

        // ── Roster 2-up layout ────────────────────────────────────────────────
        // Two tables rendered side-by-side on each page to save space.
        private val ROSTER_GAP = 12f                          // gap between the two panels
        private val ROSTER_TW  = (UW - ROSTER_GAP) / 2f      // ≈ 387 pt per panel

        private val R_ID     = 68f
        private val R_NAME   = 133f
        private val R_INACT  = 26f   // just a single digit now ("1" / "0")
        private val R_THRESH = 65f
        private val R_ATT    = ROSTER_TW - 68f - 133f - 26f - 65f  // ≈ 95 pt

        // ── Monthly grid fixed columns ────────────────────────────────────────
        private val M_ID   = 90f
        private val M_NAME = 120f
        // date cols share (UW - M_ID - M_NAME) = 576 pt

        // ── Always-landscape PrintAttributes ─────────────────────────────────
        private val LANDSCAPE = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape())
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

        private var totalPages = 0

        // ── Helper: how many vertical pages for each monthly group ────────────
        private fun monthVertPages() =
            ((data.rows.size + MONTH_ROWS_PER_PAGE - 1) / MONTH_ROWS_PER_PAGE).coerceAtLeast(1)

        // ── Helper: total roster pages (2 panels per page) ────────────────────
        private val ROSTER_SPPP = ROSTER_ROWS_PER_PAGE * 2   // students per full page

        private fun rosterPages() =
            if (data.rows.isEmpty()) 1
            else (data.rows.size + ROSTER_SPPP - 1) / ROSTER_SPPP

        // ── Helper: total holiday pages ───────────────────────────────────────
        private fun holidayPages() =
            if (data.holidays.isEmpty()) 0
            else (data.holidays.size + HOLIDAY_ROWS_PER_PAGE - 1) / HOLIDAY_ROWS_PER_PAGE

        // ─────────────────────────────────────────────────────────────────────
        override fun onStart() {}
        override fun onFinish() {}

        fun writeAllPagesTo(destFile: File) {
            totalPages = rosterPages() + holidayPages() + data.monthGroups.sumOf { monthVertPages() }
            val pdf = PrintedPdfDocument(context, LANDSCAPE)
            try {
                renderPages(pdf, ranges = null, cancel = null)
                FileOutputStream(destFile).use { pdf.writeTo(it) }
            } finally {
                pdf.close()
            }
        }

        override fun onLayout(
            oldAttr: PrintAttributes?,
            newAttr: PrintAttributes,
            cancel: CancellationSignal,
            cb: LayoutResultCallback,
            extras: Bundle?
        ) {
            if (cancel.isCanceled) { cb.onLayoutCancelled(); return }

            totalPages = rosterPages() +
                    holidayPages() +
                    data.monthGroups.sumOf { monthVertPages() }

            cb.onLayoutFinished(
                PrintDocumentInfo.Builder("${data.className}_Report.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(totalPages)
                    .build(),
                true
            )
        }

        override fun onWrite(
            ranges: Array<out PageRange>,
            dest: ParcelFileDescriptor,
            cancel: CancellationSignal,
            cb: WriteResultCallback
        ) {
            val pdf = PrintedPdfDocument(context, LANDSCAPE)
            try {
                renderPages(pdf, ranges, cancel)
                FileOutputStream(dest.fileDescriptor).use { pdf.writeTo(it) }
                cb.onWriteFinished(ranges)
            } catch (e: Exception) {
                Log.e("AttendanceRepository", "onWrite failed", e)
                if (cancel.isCanceled) cb.onWriteCancelled() else cb.onWriteFailed(e.message)
            } finally {
                pdf.close()
            }
        }

        private fun renderPages(
            pdf: PrintedPdfDocument,
            ranges: Array<out PageRange>?,
            cancel: CancellationSignal?
        ) {
            var pi = 0

            repeat(rosterPages()) { vp ->
                if (cancel?.isCanceled == true) throw RuntimeException("cancelled")
                if (ranges == null || inRanges(ranges, pi)) {
                    val s = vp * ROSTER_SPPP
                    val e = minOf(s + ROSTER_SPPP, data.rows.size)
                    val pg = pdf.startPage(pi)
                    drawRosterPage(pg.canvas, pi, data.rows.subList(s, e))
                    pdf.finishPage(pg)
                }
                pi++
            }

            repeat(holidayPages()) { vp ->
                if (cancel?.isCanceled == true) throw RuntimeException("cancelled")
                if (ranges == null || inRanges(ranges, pi)) {
                    val s = vp * HOLIDAY_ROWS_PER_PAGE
                    val e = minOf(s + HOLIDAY_ROWS_PER_PAGE, data.holidays.size)
                    val pg = pdf.startPage(pi)
                    drawHolidayPage(pg.canvas, pi, data.holidays.subList(s, e))
                    pdf.finishPage(pg)
                }
                pi++
            }

            val mvp = monthVertPages()
            for (mg in data.monthGroups) {
                repeat(mvp) { vp ->
                    if (cancel?.isCanceled == true) throw RuntimeException("cancelled")
                    if (ranges == null || inRanges(ranges, pi)) {
                        val s = vp * MONTH_ROWS_PER_PAGE
                        val e = minOf(s + MONTH_ROWS_PER_PAGE, data.rows.size)
                        val pg = pdf.startPage(pi)
                        drawMonthPage(pg.canvas, pi, mg, data.rows.subList(s, e), vp, mvp)
                        pdf.finishPage(pg)
                    }
                    pi++
                }
            }
        }


        // ═════════════════════════════════════════════════════════════════════
        // SECTION 1 — ROSTER
        // ═════════════════════════════════════════════════════════════════════

        private fun drawRosterPage(c: Canvas, pi: Int, rows: List<List<String>>) {
            c.save(); c.translate(MAR, MAR)
            val y = banner(c, 0f, "Attendance Roster", "Student summary with attendance percentages")

            val leftRows  = rows.take(ROSTER_ROWS_PER_PAGE)
            val rightRows = rows.drop(ROSTER_ROWS_PER_PAGE)

            // Left panel
            val yData = rosterHeader(c, y, 0f)
            rosterRows(c, yData, leftRows, 0f)

            // Right panel — only drawn if there are remaining students
            if (rightRows.isNotEmpty()) {
                rosterHeader(c, y, ROSTER_TW + ROSTER_GAP)
                rosterRows(c, yData, rightRows, ROSTER_TW + ROSTER_GAP)
            }

            footer(c, pi, "Section 1 of 3 — Roster")
            c.restore()
        }

        private fun rosterHeader(c: Canvas, y0: Float, xOff: Float): Float {
            val bg = fP(C_NAVY); val bd = sP(C_BORDER_D, 0.5f)
            c.drawRect(xOff, y0, xOff + ROSTER_TW, y0 + HDR_H, bg)
            var x = xOff
            for ((lbl, w) in listOf(
                "Student ID"      to R_ID,
                "Student Name"    to R_NAME,
                "Inactive"        to R_INACT,
                "Threshold (%)"   to R_THRESH,
                "Attendance (%)"  to R_ATT
            )) {
                c.drawRect(RectF(x, y0, x + w, y0 + HDR_H), bd)
                c.drawText(lbl, x + 3f, y0 + HDR_H / 2f + 3f, tP(C_WHITE, 6.5f, T_BOLD))
                x += w
            }
            return y0 + HDR_H
        }

        private fun rosterRows(c: Canvas, y0: Float, rows: List<List<String>>, xOff: Float) {
            val bd = sP(C_BORDER, 0.4f)
            var y = y0
            rows.forEachIndexed { idx, row ->
                val inactive = isInactive(row)
                val bg = if (inactive) C_INACTIVE else if (idx % 2 == 0) C_STRIPE_A else C_STRIPE_B
                c.drawRect(xOff, y, xOff + ROSTER_TW, y + ROW_H, fP(bg))
                val ty = y + ROW_H - 3.5f

                // Student ID (monospace navy)
                c.drawText(row.getOrElse(0) { "" }, xOff + 4f, ty,
                    tP(Color.parseColor("#1A237E"), 6.5f, T_MONO))

                // Student Name — italic + muted grey if inactive
                val nameTf  = if (inactive) Typeface.create(T_SANS, Typeface.ITALIC) else T_SANS
                val nameClr = if (inactive) Color.parseColor("#808080") else Color.DKGRAY
                c.drawText(clip(row.getOrElse(1) { "" }, 22), xOff + R_ID + 4f, ty,
                    tP(nameClr, 7f, nameTf))

                // Inactive Flag — "1" in bold red if inactive, "0" in light grey if active
                val cx1     = xOff + R_ID + R_NAME + R_INACT / 2f
                val flagClr = if (inactive) Color.parseColor("#C00000") else Color.LTGRAY
                val flagTf  = if (inactive) T_BOLD else T_SANS
                c.drawText(if (inactive) "1" else "0", cx1, ty,
                    tP(flagClr, 7f, flagTf, Paint.Align.CENTER))

                // Threshold (%) — strip trailing ".0" and do not append '%'
                val thresh     = row.getOrElse(3) { "" }.trim()
                val threshDisp = thresh.toDoubleOrNull()?.let {
                    if (it == it.toLong().toDouble()) "${it.toLong()}" else thresh
                } ?: thresh
                c.drawText(threshDisp, xOff + R_ID + R_NAME + R_INACT + R_THRESH / 2f, ty,
                    tP(Color.DKGRAY, 7f, T_SANS, Paint.Align.CENTER))

                // Attendance (%)
                val attRaw  = row.getOrElse(4) { "" }.trim()
                val attVal  = attRaw.replace("%", "").toDoubleOrNull()
                val attClr  = when {
                    attRaw.equals("N/A", ignoreCase = true) -> C_NA
                    attVal != null && attVal < 75.0          -> C_ABSENT
                    attVal != null && attVal >= 90.0         -> C_PRESENT
                    else                                     -> Color.parseColor("#7B3F00")
                }
                val attDisp = when {
                    attRaw.equals("N/A", ignoreCase = true) -> "N/A"
                    attVal != null                           -> String.format(Locale.US, "%.2f", attVal)
                    attRaw.isBlank()                         -> "0.00"
                    else                                     -> attRaw
                }
                c.drawText(attDisp, xOff + R_ID + R_NAME + R_INACT + R_THRESH + R_ATT / 2f, ty,
                    tP(attClr, 7f, T_BOLD, Paint.Align.CENTER))

                // Row separator + vertical dividers
                c.drawLine(xOff, y + ROW_H, xOff + ROSTER_TW, y + ROW_H, bd)
                var dx = xOff
                for (w in listOf(R_ID, R_NAME, R_INACT, R_THRESH, R_ATT)) {
                    dx += w; c.drawLine(dx, y, dx, y + ROW_H, bd)
                }
                y += ROW_H
            }
            c.drawRect(xOff, y0, xOff + ROSTER_TW, y, sP(C_BORDER, 1f))
        }

        // ═════════════════════════════════════════════════════════════════════
        // SECTION 2 — HOLIDAY REGISTER
        // ═════════════════════════════════════════════════════════════════════

        private fun drawHolidayPage(c: Canvas, pi: Int, entries: List<Pair<String, String>>) {
            c.save(); c.translate(MAR, MAR)
            var y = banner(c, 0f, "Holiday & Event Register", "Dates marked as non-teaching days")

            val colD = 180f; val colE = UW - colD
            val bg = fP(C_NAVY); val tx = tP(C_WHITE, 8.5f, T_BOLD); val bd = sP(C_BORDER_D, 0.5f)
            c.drawRect(0f, y, UW, y + HDR_H, bg)
            c.drawRect(RectF(0f, y, colD, y + HDR_H), bd)
            c.drawText("Holiday / Event Date", 4f, y + HDR_H / 2f + 3f, Paint(tx).also { it.textAlign = Paint.Align.LEFT })
            c.drawRect(RectF(colD, y, UW, y + HDR_H), bd)
            c.drawText("Event Name", colD + 4f, y + HDR_H / 2f + 3f, Paint(tx).also { it.textAlign = Paint.Align.LEFT })
            y += HDR_H

            entries.forEachIndexed { idx, (date, event) ->
                val rowBg = if (idx % 2 == 0) C_STRIPE_A else C_STRIPE_B
                c.drawRect(0f, y, UW, y + ROW_H, fP(rowBg))
                val ty = y + ROW_H - 4f
                c.drawText(date,  4f,         ty, tP(Color.DKGRAY, 8f, T_MONO))
                c.drawText(event, colD + 4f,  ty, tP(Color.DKGRAY, 8f, T_SANS))
                c.drawLine(0f, y + ROW_H, UW, y + ROW_H, sP(C_BORDER, 0.4f))
                c.drawLine(colD, y, colD, y + ROW_H, sP(C_BORDER, 0.4f))
                y += ROW_H
            }
            c.drawRect(0f, TITLE_H + HDR_H, UW, y, sP(C_BORDER, 1f))
            footer(c, pi, "Section 2 of 3 — Holiday Register")
            c.restore()
        }

        // ═════════════════════════════════════════════════════════════════════
        // SECTION 3 — MONTHLY GRID
        // ═════════════════════════════════════════════════════════════════════

        /**
         * Draws one page of a month's attendance grid.
         *
         * Column header — two stacked rows, each HDR_H tall:
         *   Row A (amber bg): day-of-week abbreviation → "Mon", "Tue" …
         *   Row B (navy bg):  day-of-month ONLY        → "05", "12" …
         *     (month + year already appear in the banner, no need to repeat them)
         *
         * @param rows     The slice of student rows for this vertical page.
         * @param vPageIdx 0-based vertical-page index within this month.
         * @param totalVP  Total vertical pages for this month.
         */
        private fun drawMonthPage(
            c: Canvas, pi: Int,
            mg: MonthGroup,
            rows: List<List<String>>,
            vPageIdx: Int, totalVP: Int
        ) {
            c.save(); c.translate(MAR, MAR)

            val subTitle = buildString {
                append("Month-by-day attendance grid  •  P = Present  •  A = Absent")
                if (totalVP > 1) append("  •  Students ${vPageIdx * MONTH_ROWS_PER_PAGE + 1}–${vPageIdx * MONTH_ROWS_PER_PAGE + rows.size}")
            }
            var y = banner(c, 0f, mg.displayLabel, subTitle)

            val nDays  = mg.cols.size.coerceAtLeast(1)
            val dateSz = ((UW - M_ID - M_NAME) / nDays).coerceIn(16f, 38f)
            val gridW  = M_ID + M_NAME + nDays * dateSz

            // ── Double-height column header ────────────────────────────────────
            val hHalf = HDR_H          // each sub-row is HDR_H tall
            val hFull = hHalf * 2f

            // Fixed: Student ID (full double height, navy)
            c.drawRect(0f, y, M_ID, y + hFull, fP(C_NAVY))
            c.drawRect(RectF(0f, y, M_ID, y + hFull), sP(C_BORDER_D, 0.5f))
            c.drawText("Student ID", 3f, y + hFull / 2f + 3f, tP(C_WHITE, 7.5f, T_BOLD))

            // Fixed: Student Name (full double height, navy)
            c.drawRect(M_ID, y, M_ID + M_NAME, y + hFull, fP(C_NAVY))
            c.drawRect(RectF(M_ID, y, M_ID + M_NAME, y + hFull), sP(C_BORDER_D, 0.5f))
            c.drawText("Student Name", M_ID + 3f, y + hFull / 2f + 3f, tP(C_WHITE, 7.5f, T_BOLD))

            // Date columns: amber top (day name) + navy bottom (day number)
            mg.cols.forEachIndexed { di, dc ->
                val dx = M_ID + M_NAME + di * dateSz

                // TOP HALF — amber, day abbreviation (Mon / Tue …)
                c.drawRect(dx, y, dx + dateSz, y + hHalf, fP(C_AMBER))
                c.drawRect(RectF(dx, y, dx + dateSz, y + hHalf), sP(C_BORDER_D, 0.5f))
                val dayAbbr = dc.dayName.take(3).ifBlank { "?" }
                c.drawText(dayAbbr, dx + dateSz / 2f, y + hHalf - 5f,
                    tP(C_NAVY, 6.5f, T_BOLD, Paint.Align.CENTER))

                // BOTTOM HALF — navy, day-of-month only (e.g. "05" from "05-01-2026")
                c.drawRect(dx, y + hHalf, dx + dateSz, y + hFull, fP(C_NAVY))
                c.drawRect(RectF(dx, y + hHalf, dx + dateSz, y + hFull), sP(C_BORDER_D, 0.5f))
                val dayNum = sessionDayDisplay(dc.dateLabel)
                c.drawText(dayNum, dx + dateSz / 2f, y + hFull - 5f,
                    tP(C_WHITE, 7f, T_MONO, Paint.Align.CENTER))
            }
            y += hFull

            // ── Data rows ─────────────────────────────────────────────────────
            val tableTop = y
            rows.forEachIndexed { idx, row ->
                val inactive = isInactive(row)
                val rowBg = if (inactive) C_INACTIVE else if (idx % 2 == 0) C_STRIPE_A else C_STRIPE_B
                c.drawRect(0f, y, gridW, y + ROW_H, fP(rowBg))
                val ty = y + ROW_H - 4f

                c.drawText(row.getOrElse(0) { "" }, 3f, ty,
                    tP(Color.parseColor("#1A237E"), 6.5f, T_MONO))
                val nameTf = if (inactive) Typeface.create(T_SANS, Typeface.ITALIC) else T_SANS
                c.drawText(clip(row.getOrElse(1) { "" }, 19), M_ID + 3f, ty,
                    tP(Color.DKGRAY, 6.5f, nameTf))

                c.drawLine(M_ID,        y, M_ID,        y + ROW_H, sP(C_BORDER, 0.3f))
                c.drawLine(M_ID+M_NAME, y, M_ID+M_NAME, y + ROW_H, sP(C_BORDER, 0.3f))

                mg.cols.forEachIndexed { di, dc ->
                    val globalDi = data.dateCols.indexOfFirst { it.colIndex == dc.colIndex }
                    val rawVal   = if (globalDi >= 0) row.getOrElse(5 + globalDi) { "" }.trim() else ""
                    val numVal   = rawVal.toDoubleOrNull()?.toInt()   // present count, 0, or null

                    val dx = M_ID + M_NAME + di * dateSz
                    val cx = dx + dateSz / 2f

                    // Cell background
                    val cellBg = when {
                        inactive   -> C_INACTIVE
                        numVal != null && numVal > 0 -> C_GREEN_BG
                        numVal == 0 -> C_RED_BG
                        else        -> rowBg
                    }
                    c.drawRect(dx, y, dx + dateSz, y + ROW_H, fP(cellBg))

                    val display = when {
                        numVal != null && numVal > 0 -> "P"
                        numVal == 0 -> "A"
                        else -> "–"
                    }
                    val txtClr  = when {
                        numVal != null && numVal > 0 -> C_PRESENT
                        numVal == 0 -> C_ABSENT
                        else -> C_NA
                    }
                    c.drawText(display, cx, ty, tP(txtClr, 8f, T_BOLD, Paint.Align.CENTER))
                    c.drawLine(dx, y, dx, y + ROW_H, sP(C_BORDER, 0.3f))
                }

                c.drawLine(0f, y + ROW_H, gridW, y + ROW_H, sP(C_BORDER, 0.3f))
                y += ROW_H
            }
            c.drawRect(0f, tableTop - hFull, gridW, y, sP(C_BORDER, 1f))

            footer(c, pi, "Section 3 of 3 — ${mg.displayLabel}")
            c.restore()
        }

        // ═════════════════════════════════════════════════════════════════════
        // SHARED DRAWING HELPERS
        // ═════════════════════════════════════════════════════════════════════

        /** Draws the navy banner and returns Y below it. */
        private fun banner(c: Canvas, y0: Float, title: String, subtitle: String): Float {
            c.drawRect(0f, y0, UW, y0 + TITLE_H, fP(C_NAVY_LT))
            c.drawText("RollCheck", 8f, y0 + 16f, tP(C_AMBER, 11f, T_BOLD))
            c.drawText("${data.className}  –  $title", UW / 2f, y0 + 16f,
                tP(C_WHITE, 13f, T_BOLD, Paint.Align.CENTER))
            c.drawText("$subtitle  •  ${data.generatedOn}", UW / 2f, y0 + 31f,
                tP(Color.parseColor("#B0C4DE"), 7.5f, T_SANS, Paint.Align.CENTER))
            c.drawLine(0f, y0 + TITLE_H - 1.5f, UW, y0 + TITLE_H - 1.5f, sP(C_AMBER, 2f))
            return y0 + TITLE_H
        }

        /** Draws the footer at the very bottom of the usable area. */
        private fun footer(c: Canvas, pi: Int, section: String) {
            val fy = UH - FOOTER_H
            c.drawLine(0f, fy, UW, fy, sP(C_BORDER, 0.8f))
            c.drawText("RollCheck  •  Confidential  •  $section", 0f, fy + 12f,
                tP(C_FOOTER, 7f, T_SANS))
            c.drawText("Page ${pi + 1} of $totalPages", UW, fy + 12f,
                tP(C_FOOTER, 7f, T_SANS, Paint.Align.RIGHT))
        }

        // ── Paint factories ───────────────────────────────────────────────────
        private fun fP(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; style = Paint.Style.FILL
        }
        private fun sP(color: Int, w: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; style = Paint.Style.STROKE; strokeWidth = w
        }
        private fun tP(color: Int, size: Float, tf: Typeface, align: Paint.Align = Paint.Align.LEFT) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                textSize = size
                typeface = tf
                textAlign = align
                isSubpixelText = true
                isLinearText = true
                hinting = Paint.HINTING_ON
            }

        // ── Misc helpers ──────────────────────────────────────────────────────
        private fun isInactive(row: List<String>) =
            row.getOrElse(2) { "0" }.trim().toDoubleOrNull()?.toInt() == 1

        private fun clip(text: String, max: Int) =
            if (text.length > max) text.take(max - 1) + "…" else text

        private fun sessionDayDisplay(dateLabel: String): String {
            val baseDay = dateLabel.substringBefore("-").trim()
            val suffixes = Regex("""(\([^)]*\))""")
                .findAll(dateLabel)
                .joinToString(separator = "") { it.value }
            return baseDay + suffixes
        }

        private fun inRanges(ranges: Array<out PageRange>, pi: Int) =
            ranges.any { it.start <= pi && pi <= it.end }
    }


    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun syncStudentsFromExcel(classId: String) {
        val entity = classDao.getClassById(classId) ?: return
        val students = ExcelManager.parseStudents(entity.filePath, classId)
        if (students.isEmpty()) return
        studentDao.deleteStudentsForClass(classId)
        studentDao.insertStudents(students)
    }

    fun getTemplateBytes(): ByteArray? = ExcelManager.getTemplateBytes(context)
    fun copyTemplateTo(uri: Uri): Boolean = ExcelManager.copyTemplateToUri(context, uri)
    fun downloadTemplateToDocuments(): Boolean = ExcelManager.downloadTemplateToDocuments(context)

    fun hashPin(pin: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(pin.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    suspend fun markHoliday(classId: String, label: String): Boolean = withContext(Dispatchers.IO) {
        val entity = classDao.getClassById(classId) ?: return@withContext false
        val success = ExcelManager.markHoliday(entity.filePath, label)
        if (success) copyToAttendanceFolder(entity.filePath, "${entity.className}_Attendance.xlsx")
        success
    }

    suspend fun markHolidayForAllClasses(label: String): Boolean = withContext(Dispatchers.IO) {
        val classes = classDao.getAllClassesNow()
        if (classes.isEmpty()) return@withContext false

        var successCount = 0
        classes.forEach { entity ->
            val success = ExcelManager.markHoliday(entity.filePath, label)
            if (success) {
                copyToAttendanceFolder(entity.filePath, "${entity.className}_Attendance.xlsx")
                successCount++
            }
        }
        successCount == classes.size
    }

    companion object {
        private const val MANUAL_BASELINE_SENTINEL = "__QM_MANUAL_BASELINE__"
        @Volatile private var INSTANCE: AttendanceRepository? = null
        fun getInstance(context: Context): AttendanceRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AttendanceRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}






