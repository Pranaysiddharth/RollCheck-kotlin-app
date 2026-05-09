package com.attendance.rollcheck.domain.manager

import android.content.Context
import com.attendance.rollcheck.data.local.prefs.PreferencesManager
import com.attendance.rollcheck.data.repository.AttendanceRepository
import com.attendance.rollcheck.domain.model.SessionInfo
import com.attendance.rollcheck.domain.model.Student

/**
 * Domain-layer facade for session lifecycle operations.
 * Coordinates between the repository (Room DB + Excel).
 */
class SessionManager(context: Context) {

    private val repo = AttendanceRepository.getInstance(context)

    /** Returns an incomplete session for the given class if one exists. */
    suspend fun getActiveSession(classId: String): SessionInfo? = repo.getActiveSession(classId)

    /**
     * Initialises a new session:
     *  - Clears any stale Room session data
     *  - Loads active students into session_attendance table (all unmarked)
     *  - Saves ActiveSessionEntity so recovery works after app kill
     */
    suspend fun startSession(
        classId: String,
        startRoll: Int,
        endRoll: Int,
        continuousSessionCount: Int = 1
    ) = repo.startSession(classId, startRoll, endRoll, continuousSessionCount)

    /** Mark a single student present (1) or absent (0). */
    suspend fun markStudent(classId: String, studentId: String, status: Int) =
        repo.markStudent(classId, studentId, status)

    /** Bulk-mark all students in the active session. */
    suspend fun markAll(classId: String, status: Int) =
        repo.markAll(classId, status)

    /** Get session records for a class. */
    suspend fun getSessionAttendance(classId: String): List<com.attendance.rollcheck.domain.model.SessionAttendanceEntity> =
        repo.getSessionAttendance(classId)

    /** Update the roll pointer and marked count (call after each tap). */
    suspend fun updateProgress(classId: String, lastRoll: Int, markedCount: Int) =
        repo.updateSessionProgress(classId, lastRoll, markedCount)

    /**
     * Final save: writes all attendance to Excel → validates → clears Room session.
     * Called from SummaryScreen "Save Attendance" button.
     * Returns true on success.
     */
    suspend fun finalSave(classId: String): Boolean =
        repo.finalSave(classId)

    /** Load active students for a class (used by RollCallScreen & ManualAttendance). */
    suspend fun getStudents(classId: String): List<Student> =
        repo.getStudentsForClass(classId)

    /** Load cached Room students without reparsing Excel (used when startup speed matters). */
    suspend fun getCachedStudents(classId: String): List<Student> =
        repo.getCachedStudentsForClass(classId)

    /** Discards the current active session for a class without saving to Excel. */
    suspend fun discardSession(classId: String) = repo.discardSession(classId)

    suspend fun setAttendanceStage(classId: String) =
        repo.setActiveSessionStage(classId, PreferencesManager.SESSION_STAGE_ATTENDANCE)

    suspend fun setMarkPresentStage(classId: String) =
        repo.setActiveSessionStage(classId, PreferencesManager.SESSION_STAGE_MARK_PRESENT)

    suspend fun setManualAttendanceStage(classId: String) =
        repo.setActiveSessionStage(classId, PreferencesManager.SESSION_STAGE_MANUAL_ATTENDANCE)

    suspend fun setSummaryStage(classId: String) =
        repo.setActiveSessionStage(classId, PreferencesManager.SESSION_STAGE_SUMMARY)

    companion object {
        @Volatile private var INSTANCE: SessionManager? = null
        fun getInstance(context: Context): SessionManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SessionManager(context.applicationContext).also { INSTANCE = it }
            }
    }
}
