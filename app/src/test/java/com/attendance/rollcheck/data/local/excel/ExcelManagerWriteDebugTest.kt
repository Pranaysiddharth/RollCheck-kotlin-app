package com.attendance.rollcheck.data.local.excel

import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFFont
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class ExcelManagerWriteDebugTest {
    @Test
    fun writes_first_session_workbook_for_debugging() {
        val projectRoot = File("C:/Users/Pavan/AndroidStudioProjects/RollCheck")
        val template = File(projectRoot, "app/src/main/assets/RollCheck_Attendance_Template.xlsx")
        val outputDir = File(projectRoot, "app/build/test-artifacts/excel").apply { mkdirs() }
        val outputFile = File(outputDir, "first_session_debug.xlsx")
        Files.copy(template.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING)

        XSSFWorkbook(outputFile.inputStream()).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            for (rowIndex in 0..sheet.lastRowNum) {
                val row = sheet.getRow(rowIndex) ?: continue
                for (col in row.lastCellNum.toInt() - 1 downTo 5) {
                    row.removeCell(row.getCell(col))
                }
            }
            outputFile.outputStream().use { workbook.write(it) }
        }

        val students = ExcelManager.parseStudents(outputFile.absolutePath, "debug")
        require(students.size >= 2) { "Need at least two students in template" }
        val firstStudent = students[0].studentId
        val secondStudent = students[1].studentId

        val success = ExcelManager.writeAttendance(
            filePath = outputFile.absolutePath,
            attendanceMap = mapOf(firstStudent to 1, secondStudent to 0),
            dateLabel = "20-04-2026",
            continuousSessionCount = 1
        )

        assertTrue("writeAttendance should succeed", success)

        XSSFWorkbook(outputFile.inputStream()).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            assertTrue(workbook.numCellStyles >= 38)
            assertEquals("Mon", sheet.getRow(0).getCell(5).stringCellValue)
            assertEquals("20-04-2026", sheet.getRow(1).getCell(5).stringCellValue)
            assertEquals(1.0, sheet.getRow(2).getCell(5).numericCellValue, 0.0)
            assertEquals(0.0, sheet.getRow(3).getCell(5).numericCellValue, 0.0)
            assertEquals("N/A", sheet.getRow(14).getCell(4).stringCellValue)
            assertEquals(34.5f, sheet.getRow(0).heightInPoints)
            assertEquals(109.5f, sheet.getRow(1).heightInPoints)
            assertEquals(4 * 256, sheet.getColumnWidth(5))

            val dayHeaderStyle = sheet.getRow(0).getCell(5).cellStyle as XSSFCellStyle
            val dateHeaderStyle = sheet.getRow(1).getCell(5).cellStyle as XSSFCellStyle
            val presentStyle = sheet.getRow(2).getCell(5).cellStyle as XSSFCellStyle
            val absentStyle = sheet.getRow(3).getCell(5).cellStyle as XSSFCellStyle

            assertEquals(90.toShort(), dayHeaderStyle.rotation)
            assertEquals(90.toShort(), dateHeaderStyle.rotation)
            assertEquals("0", presentStyle.dataFormatString)
            assertEquals("0", absentStyle.dataFormatString)
            assertTrue(colorHex(dayHeaderStyle.fillForegroundColorColor)?.endsWith("151A2D") == true)
            assertTrue(colorHex(dateHeaderStyle.fillForegroundColorColor)?.endsWith("1E2A45") == true)
            assertTrue(colorHex(presentStyle.fillForegroundColorColor)?.endsWith("151A2D") == true)
            assertTrue(colorHex(absentStyle.fillForegroundColorColor)?.endsWith("1A2035") == true)

            val dayHeaderFont = workbook.getFontAt(dayHeaderStyle.fontIndex) as XSSFFont
            val dateHeaderFont = workbook.getFontAt(dateHeaderStyle.fontIndex) as XSSFFont
            val presentFont = workbook.getFontAt(presentStyle.fontIndex) as XSSFFont
            val absentFont = workbook.getFontAt(absentStyle.fontIndex) as XSSFFont

            assertTrue(dayHeaderFont.bold)
            assertTrue(dateHeaderFont.bold)
            assertTrue(presentFont.bold)
            assertTrue(absentFont.bold)
            assertTrue(colorHex(dayHeaderFont.xssfColor)?.endsWith("4FACFE") == true)
            assertTrue(colorHex(dateHeaderFont.xssfColor)?.endsWith("F8FAFC") == true)
            assertTrue(colorHex(presentFont.xssfColor)?.endsWith("10B981") == true)
            assertTrue(colorHex(absentFont.xssfColor)?.endsWith("EF4444") == true)
        }
    }

    private fun colorHex(color: XSSFColor?): String? =
        color?.argbHex ?: color?.rgb?.joinToString(separator = "") { "%02X".format(it) }
}
