package com.attendance.rollcheck.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.attendance.rollcheck.domain.model.*

@Database(
    entities = [
        ClassEntity::class,
        StudentEntity::class,
        SessionAttendanceEntity::class,
        ActiveSessionEntity::class,
        ManualRecoveryBaselineEntity::class
    ],
    version = 7,
    exportSchema = true
)
abstract class RollCheckDatabase : RoomDatabase() {
    abstract fun classDao(): ClassDao
    abstract fun studentDao(): StudentDao
    abstract fun sessionAttendanceDao(): SessionAttendanceDao
    abstract fun activeSessionDao(): ActiveSessionDao
    abstract fun manualRecoveryBaselineDao(): ManualRecoveryBaselineDao

    companion object {
        @Volatile private var INSTANCE: RollCheckDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `students_new` (
                        `studentId` TEXT NOT NULL,
                        `classId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `inactiveFlag` INTEGER NOT NULL,
                        `threshold` INTEGER NOT NULL,
                        PRIMARY KEY(`classId`, `studentId`),
                        FOREIGN KEY(`classId`) REFERENCES `classes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `students_new` (`studentId`, `classId`, `name`, `inactiveFlag`, `threshold`)
                    SELECT `studentId`, `classId`, `name`, `inactiveFlag`, `threshold` FROM `students`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `students`")
                db.execSQL("ALTER TABLE `students_new` RENAME TO `students`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_students_classId` ON `students` (`classId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_students_studentId` ON `students` (`studentId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `session_attendance_new` (
                        `studentId` TEXT NOT NULL,
                        `classId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `status` INTEGER NOT NULL,
                        PRIMARY KEY(`classId`, `studentId`),
                        FOREIGN KEY(`classId`) REFERENCES `classes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `session_attendance_new` (`studentId`, `classId`, `name`, `status`)
                    SELECT `studentId`, `classId`, `name`, `status` FROM `session_attendance`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `session_attendance`")
                db.execSQL("ALTER TABLE `session_attendance_new` RENAME TO `session_attendance`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_attendance_classId` ON `session_attendance` (`classId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_attendance_studentId` ON `session_attendance` (`studentId`)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `manual_recovery_baseline` (
                        `classId` TEXT NOT NULL,
                        `studentId` TEXT NOT NULL,
                        PRIMARY KEY(`classId`, `studentId`),
                        FOREIGN KEY(`classId`) REFERENCES `classes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_manual_recovery_baseline_classId` ON `manual_recovery_baseline` (`classId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_manual_recovery_baseline_studentId` ON `manual_recovery_baseline` (`studentId`)"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `active_session` ADD COLUMN `stage` TEXT NOT NULL DEFAULT 'attendance'"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `active_session_new` (
                        `classId` TEXT NOT NULL,
                        `startRoll` INTEGER NOT NULL,
                        `endRoll` INTEGER NOT NULL,
                        `lastRoll` INTEGER NOT NULL,
                        `markedCount` INTEGER NOT NULL,
                        `stage` TEXT NOT NULL,
                        `startedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`classId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `active_session_new`
                        (`classId`, `startRoll`, `endRoll`, `lastRoll`, `markedCount`, `stage`, `startedAt`)
                    SELECT
                        `classId`, `startRoll`, `endRoll`, `lastRoll`, `markedCount`, `stage`, `startedAt`
                    FROM `active_session`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `active_session`")
                db.execSQL("ALTER TABLE `active_session_new` RENAME TO `active_session`")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `active_session` ADD COLUMN `continuousSessionCount` INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `students_new` (
                        `studentId` TEXT NOT NULL,
                        `classId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `inactiveFlag` INTEGER NOT NULL,
                        `threshold` REAL NOT NULL,
                        PRIMARY KEY(`classId`, `studentId`),
                        FOREIGN KEY(`classId`) REFERENCES `classes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `students_new` (`studentId`, `classId`, `name`, `inactiveFlag`, `threshold`)
                    SELECT `studentId`, `classId`, `name`, `inactiveFlag`, CAST(`threshold` AS REAL) FROM `students`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `students`")
                db.execSQL("ALTER TABLE `students_new` RENAME TO `students`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_students_classId` ON `students` (`classId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_students_studentId` ON `students` (`studentId`)")
            }
        }

        fun getInstance(context: Context): RollCheckDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RollCheckDatabase::class.java,
                    "rollcheck_db"
                )
                .addMigrations(MIGRATION_1_2)
                .addMigrations(MIGRATION_2_3)
                .addMigrations(MIGRATION_3_4)
                .addMigrations(MIGRATION_4_5)
                .addMigrations(MIGRATION_5_6)
                .addMigrations(MIGRATION_6_7)
                .build()
                .also { INSTANCE = it }
            }
    }
}
