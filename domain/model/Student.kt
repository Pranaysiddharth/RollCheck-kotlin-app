package com.attendance.rollcheck.domain.model

// Canonical Student model — used by ALL screens.
// studentId = full roll string e.g. "BT24CSE001"
data class Student(
    val studentId: String,
    val name: String,
    val inactiveFlag: Int = 0,
    val threshold: Double = 75.0
)
