package com.attendance.rollcheck.domain.manager

import com.attendance.rollcheck.domain.model.Student

/**
 * In-memory student list manager per class.
 * Will be replaced by a Room database in a future iteration.
 */
class StudentManager {

    private val studentsByClass = mutableMapOf<String, MutableList<Student>>()

    fun getStudents(classId: String): List<Student> =
        studentsByClass.getOrPut(classId) {
            // Default: generate 60 students if not yet seeded
            (1..60).map { i ->
                Student(studentId = i.toString().padStart(3, '0'), name = "Student $i")
            }.toMutableList()
        }

    fun addStudent(classId: String, student: Student) {
        studentsByClass.getOrPut(classId) { mutableListOf() }.add(student)
    }

    fun removeStudent(classId: String, roll: String) {
        studentsByClass[classId]?.removeAll { it.studentId == roll }
    }

    fun updateStudent(classId: String, updated: Student) {
        val list = studentsByClass[classId] ?: return
        val idx  = list.indexOfFirst { it.studentId == updated.studentId }
        if (idx >= 0) list[idx] = updated
    }
}