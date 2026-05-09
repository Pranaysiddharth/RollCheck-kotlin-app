package com.attendance.rollcheck.domain.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "manual_recovery_baseline",
    primaryKeys = ["classId", "studentId"],
    foreignKeys = [
        ForeignKey(
            entity = ClassEntity::class,
            parentColumns = ["id"],
            childColumns = ["classId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("classId"), Index("studentId")]
)
data class ManualRecoveryBaselineEntity(
    val classId: String,
    val studentId: String
)

