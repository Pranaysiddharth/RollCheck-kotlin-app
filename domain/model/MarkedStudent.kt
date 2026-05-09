package com.attendance.rollcheck.domain.model

// Used by RollCallScreen to show the previously marked card.
data class MarkedStudent(
    val roll: Int,
    val studentId: String,
    val name: String,
    val status: Int       // 1 = present, 0 = absent
)