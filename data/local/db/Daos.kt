package com.attendance.rollcheck.data.local.db

import androidx.room.*
import com.attendance.rollcheck.domain.model.*
import kotlinx.coroutines.flow.Flow

// ─────────────────────────────────────────────────────────────────────────────
// CLASS DAO
// ─────────────────────────────────────────────────────────────────────────────

@Dao
interface ClassDao {
    @Query("SELECT * FROM classes ORDER BY createdAt DESC")
    fun getAllClasses(): Flow<List<ClassEntity>>

    @Query("SELECT * FROM classes ORDER BY createdAt DESC")
    suspend fun getAllClassesNow(): List<ClassEntity>

    @Query("SELECT * FROM classes WHERE id = :id")
    suspend fun getClassById(id: String): ClassEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM classes WHERE id = :id)")
    suspend fun classExists(id: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM classes WHERE className = :className)")
    suspend fun classNameExists(className: String): Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertClass(classEntity: ClassEntity)

    @Delete
    suspend fun deleteClass(classEntity: ClassEntity)

    @Query("DELETE FROM classes WHERE id = :id")
    suspend fun deleteClassById(id: String)

    @Query("UPDATE classes SET className = :newName WHERE id = :id")
    suspend fun updateClassName(id: String, newName: String)

    @Query("UPDATE classes SET className = :newName, filePath = :newPath WHERE id = :id")
    suspend fun updateClassDetails(id: String, newName: String, newPath: String)
}

// ─────────────────────────────────────────────────────────────────────────────
// STUDENT DAO
// ─────────────────────────────────────────────────────────────────────────────

@Dao
interface StudentDao {
    @Query("SELECT * FROM students WHERE classId = :classId ORDER BY studentId ASC")
    suspend fun getStudentsForClass(classId: String): List<StudentEntity>

    @Query("SELECT * FROM students WHERE classId = :classId AND inactiveFlag = 0 ORDER BY studentId ASC")
    suspend fun getActiveStudentsForClass(classId: String): List<StudentEntity>

    @Query("SELECT COUNT(*) FROM students WHERE classId = :classId AND inactiveFlag = 0")
    suspend fun getActiveStudentCount(classId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudents(students: List<StudentEntity>)

    @Query(
        "UPDATE students SET name = :name, inactiveFlag = :inactiveFlag, threshold = :threshold " +
            "WHERE studentId = :studentId AND classId = :classId"
    )
    suspend fun updateStudentFields(
        classId: String,
        studentId: String,
        name: String,
        inactiveFlag: Int,
        threshold: Double
    )

    @Query("DELETE FROM students WHERE classId = :classId")
    suspend fun deleteStudentsForClass(classId: String)
}

// ─────────────────────────────────────────────────────────────────────────────
// SESSION ATTENDANCE DAO
// ─────────────────────────────────────────────────────────────────────────────

@Dao
interface SessionAttendanceDao {
    @Query("SELECT * FROM session_attendance WHERE classId = :classId ORDER BY studentId ASC")
    suspend fun getSessionAttendance(classId: String): List<SessionAttendanceEntity>

    @Query("SELECT * FROM session_attendance WHERE classId = :classId AND status = -1 ORDER BY studentId ASC")
    suspend fun getUnmarkedStudents(classId: String): List<SessionAttendanceEntity>

    @Query("SELECT COUNT(*) FROM session_attendance WHERE classId = :classId AND status = 1")
    suspend fun getPresentCount(classId: String): Int

    @Query("SELECT COUNT(*) FROM session_attendance WHERE classId = :classId AND status = 0")
    suspend fun getAbsentCount(classId: String): Int

    @Query("SELECT classId FROM session_attendance WHERE studentId = :studentId LIMIT 1")
    suspend fun findClassIdForStudent(studentId: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<SessionAttendanceEntity>)

    @Query("UPDATE session_attendance SET status = :status WHERE classId = :classId AND studentId = :studentId")
    suspend fun updateStatus(classId: String, studentId: String, status: Int)

    @Query("UPDATE session_attendance SET status = :status WHERE classId = :classId")
    suspend fun markAllAs(classId: String, status: Int)

    @Query("DELETE FROM session_attendance WHERE classId = :classId")
    suspend fun clearSession(classId: String)

    @Query("DELETE FROM session_attendance")
    suspend fun clearAll()
}

// ─────────────────────────────────────────────────────────────────────────────
// ACTIVE SESSION DAO
// ─────────────────────────────────────────────────────────────────────────────

@Dao
interface ActiveSessionDao {
    @Query("SELECT * FROM active_session WHERE classId = :classId")
    suspend fun getActiveSession(classId: String): ActiveSessionEntity?

    @Query("SELECT * FROM active_session WHERE classId = :classId")
    fun observeActiveSession(classId: String): Flow<ActiveSessionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSession(session: ActiveSessionEntity)

    @Query("UPDATE active_session SET lastRoll = :lastRoll, markedCount = :markedCount WHERE classId = :classId")
    suspend fun updateProgress(classId: String, lastRoll: Int, markedCount: Int)

    @Query("UPDATE active_session SET stage = :stage WHERE classId = :classId")
    suspend fun updateStage(classId: String, stage: String)

    @Query("DELETE FROM active_session WHERE classId = :classId")
    suspend fun clearSession(classId: String)
}

@Dao
interface ManualRecoveryBaselineDao {
    @Query("SELECT studentId FROM manual_recovery_baseline WHERE classId = :classId ORDER BY studentId ASC")
    suspend fun getStudentIdsForClass(classId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<ManualRecoveryBaselineEntity>)

    @Query("DELETE FROM manual_recovery_baseline WHERE classId = :classId")
    suspend fun clearForClass(classId: String)

    @Query("DELETE FROM manual_recovery_baseline")
    suspend fun clearAll()
}
