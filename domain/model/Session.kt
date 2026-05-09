package com.attendance.rollcheck.domain.model

data class Session(
    val className: String,
    val startRoll: Int,
    val endRoll: Int,
    val lastRoll: Int       = startRoll,
    val markedCount: Int    = 0,
    val isFinished: Boolean = false
)