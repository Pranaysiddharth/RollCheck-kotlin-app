package com.attendance.rollcheck.domain.model

data class Class(
    val id: String,
    val displayName: String,
    val studentCount: Int,
    val rollPrefix: String
)