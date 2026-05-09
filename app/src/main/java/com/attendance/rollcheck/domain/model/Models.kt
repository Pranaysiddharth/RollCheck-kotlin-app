package com.attendance.rollcheck.domain.model

// ─────────────────────────────────────────────────────────────────────────────
// ROOM ENTITIES  (persisted — used by Room DB only)
// ─────────────────────────────────────────────────────────────────────────────
// NOTE: Student, MarkedStudent, and AttendanceStatus are declared in their own
// standalone files (Student.kt, MarkedStudent.kt, AttendanceEntry.kt).
// This file only declares the Room entities and additional domain types that
// did NOT exist before. Do NOT redeclare anything already in those files.

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One class registered in RollCheck. file_path = internal .xlsx copy. */
@Entity(tableName = "classes")
data class ClassEntity(
    @PrimaryKey val id: String,
    val className: String,
    val filePath: String,
    val rollPrefix: String,
    val studentCount: Int,
    val createdAt: Long = System.currentTimeMillis()
)

/** One student row, parsed from Excel and cached in Room. */
@Entity(
    tableName = "students",
    primaryKeys = ["classId", "studentId"],
    foreignKeys = [ForeignKey(
        entity        = ClassEntity::class,
        parentColumns = ["id"],
        childColumns  = ["classId"],
        onDelete      = ForeignKey.CASCADE
    )],
    indices = [Index("classId"), Index("studentId")]
)
data class StudentEntity(
    val studentId: String,
    val classId: String,
    val name: String,
    val inactiveFlag: Int = 0,
    val threshold: Double = 75.0
)

/**
 * Session attendance buffer — stored in Room, cleared only after a
 * successful final Excel write.
 * status: -1 = unmarked, 0 = absent, 1 = present
 */
@Entity(
    tableName = "session_attendance",
    primaryKeys = ["classId", "studentId"],
    foreignKeys = [ForeignKey(
        entity = ClassEntity::class,
        parentColumns = ["id"],
        childColumns = ["classId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices   = [Index("classId"), Index("studentId")]
)
data class SessionAttendanceEntity(
    val studentId: String,
    val classId: String,
    val name: String,
    val status: Int = -1
)

/**
 * Tracks an active roll-call session for a single class so recovery works
 * after app kill. One row may exist per class.
 */
@Entity(tableName = "active_session")
data class ActiveSessionEntity(
    @PrimaryKey val classId: String,
    val startRoll: Int,
    val endRoll: Int,
    val lastRoll: Int,
    val markedCount: Int = 0,
    val continuousSessionCount: Int = 1,
    val stage: String = "attendance",
    val startedAt: Long  = System.currentTimeMillis()
)

// ─────────────────────────────────────────────────────────────────────────────
// LIGHTWEIGHT UI / DOMAIN VALUE OBJECTS  (not persisted)
// ─────────────────────────────────────────────────────────────────────────────

data class ClassInfo(
    val id: String,
    val className: String,
    val studentCount: Int,
    val rollPrefix: String,
    val filePath: String
)

data class SessionInfo(
    val classId: String,
    val startRoll: Int,
    val endRoll: Int,
    val lastRoll: Int,
    val markedCount: Int,
    val continuousSessionCount: Int,
    val stage: String
)

data class ExcelValidationResult(
    val isValid: Boolean,
    val errorMessage: String       = "",
    val warningMessage: String     = "",
    val studentCount: Int          = 0,
    val firstStudentId: String     = "",
    val firstStudentName: String   = "",
    val lastStudentId: String      = "",
    val lastStudentName: String    = "",
    val inferredRollPrefix: String = ""
)
