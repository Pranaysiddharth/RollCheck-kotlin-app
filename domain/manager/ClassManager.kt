package com.attendance.rollcheck.domain.manager

import android.content.Context
import com.attendance.rollcheck.data.repository.AttendanceRepository
import com.attendance.rollcheck.domain.model.ClassInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Domain-layer facade over AttendanceRepository for class operations.
 * Screens call this — never the repository directly.
 */
class ClassManager(context: Context) {

    private val repo = AttendanceRepository.getInstance(context)

    /** Live stream of all classes from Room DB. */
    fun observeClasses(): Flow<List<ClassInfo>> = repo.observeClasses().map { it.orEmpty() }

    /** Check if a class name already exists. */
    suspend fun classNameExists(name: String): Boolean = repo.classNameExists(name)

    /** Check if a class ID already exists. */
    suspend fun classExistsById(id: String): Boolean = repo.classExistsById(id)

    /** Get class info by ID. */
    suspend fun getClassById(id: String): ClassInfo? = repo.getClassById(id)

    /**
     * Full add-class flow: validate → copy xlsx → parse students → insert to DB.
     * Returns null on success, error message on failure.
     */
    suspend fun addClass(
        className: String,
        rollPrefix: String,
        fileUri: android.net.Uri
    ): String? = repo.addClass(className, rollPrefix, fileUri)

    /** Rename an existing class. */
    suspend fun renameClass(classId: String, newName: String): Boolean =
        repo.renameClass(classId, newName)

    /**
     * PIN-protected class deletion.
     * Returns true if PIN matched and class was deleted.
     */
    suspend fun deleteClass(
        classId: String,
        enteredPinHash: String,
        savedPinHash: String?
    ): Boolean {
        if (enteredPinHash != savedPinHash) return false
        val classInfo = repo.getClassById(classId) ?: return false
        return repo.deleteClass(classId, classInfo.className, classInfo.filePath)
    }

    /** Exports the class's master Excel file to the public 'RollCheck/Exports' folder. */
    suspend fun exportExcel(classId: String): Boolean =
        repo.exportClassExcelToExportFolder(classId)

    /** Exports a professional PDF report directly into the RollCheck export folder. */
    suspend fun printPdf(uiContext: Context, classId: String): Boolean =
        repo.printAttendancePdf(uiContext, classId)

    /** Gets the last roll number used in the Excel file for a class. */
    suspend fun getRollRange(classId: String): Pair<Int, Int> =
        repo.getRollRangeFromExcel(classId)

    /** Gets the number of sessions recorded today for a class. */
    suspend fun getTodaySessionCount(classId: String): Int =
        repo.getTodaySessionCount(classId)

    /** Single-read meta for session start: roll range + today's session count. */
    suspend fun getSessionStartMeta(classId: String): Pair<Pair<Int, Int>, Int> =
        repo.getSessionStartMeta(classId)

    /** Gets the number of active students for a class. */
    suspend fun getActiveStudentCount(classId: String): Int =
        repo.getActiveStudentCount(classId)

    /** Marks a specific day as a holiday/event in the Excel sheet. */
    suspend fun markHoliday(classId: String, label: String): Boolean =
        repo.markHoliday(classId, label)


    /** Marks today as a holiday/event in every class workbook. */
    suspend fun markHolidayForAllClasses(label: String): Boolean =
        repo.markHolidayForAllClasses(label)

    companion object {
        @Volatile private var INSTANCE: ClassManager? = null
        fun getInstance(context: Context): ClassManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ClassManager(context.applicationContext).also { INSTANCE = it }
            }
    }
}
