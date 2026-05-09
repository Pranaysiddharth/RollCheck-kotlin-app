package com.attendance.rollcheck.data.local.excel

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.attendance.rollcheck.domain.model.ExcelValidationResult
import com.attendance.rollcheck.domain.model.StudentEntity
import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellReference
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFFont
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.EmptyFileException
import org.apache.poi.openxml4j.exceptions.OLE2NotOfficeXmlFileException
import org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException
import org.apache.poi.ooxml.POIXMLException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

/**
 * Apache POI implementation for Excel operations.
 * Note: Indexing in POI is 0-based.
 */
object ExcelManager {

    private const val STUDENT_ID_COL   = 0 // 0-indexed for Apache POI
    private const val STUDENT_NAME_COL = 1
    private const val INACTIVE_COL     = 2
    private const val THRESHOLD_COL    = 3
    private const val ATTENDANCE_COL   = 4
    private const val DATE_START_COL   = 5

    private const val HEADER_ROW_1     = 0
    private const val HEADER_ROW_2     = 1
    private const val DATA_START_ROW   = 2
    private const val EXPECTED_SHEET_COUNT = 1

    private const val PRESENT_FONT_COLOR = "FF10B981"
    private const val ABSENT_FONT_COLOR = "FFEF4444"
    private const val NEUTRAL_ATTENDANCE_FONT_COLOR = "FFF8FAFC"
    private const val ATTENDANCE_THRESHOLD_WARNING_COLOR = "FFFFC107"
    private const val INACTIVE_ATTENDANCE_FONT_COLOR = "FF94A3B8"

    private val expectedPrimaryHeaders = mapOf(
        STUDENT_ID_COL to "Student ID",
        STUDENT_NAME_COL to "Student Name",
        INACTIVE_COL to "Inactive Flag",
        THRESHOLD_COL to "Threshold (%)",
        ATTENDANCE_COL to "Attendance (%)"
    )

    private val sessionDateLabelRegex = Regex("""^(\d{2}-\d{2}-\d{4})(?:\((\d+)\))?(?:\(c(\d+)\))?$""")
    private val attendanceDisplayFormatRegex = Regex(
        """0(?:\\?\.0+)?(?:;\\?-?0(?:\\?\.0+)?)?(?:;\\?-?\\?-?\\?\.\\?-+)?""",
        RegexOption.IGNORE_CASE
    )

    private data class SessionDateLabelParts(
        val baseDate: String,
        val sequenceOffset: Int?,
        val continuousSessionCount: Int
    )

    private fun parseSessionDateLabel(label: String): SessionDateLabelParts? {
        val match = sessionDateLabelRegex.matchEntire(label.trim()) ?: return null
        val baseDate = match.groupValues[1]
        val sequenceOffset = match.groupValues[2].takeIf { it.isNotBlank() }?.toIntOrNull()
        val continuousCount = match.groupValues[3].takeIf { it.isNotBlank() }?.toIntOrNull() ?: 1
        return SessionDateLabelParts(
            baseDate = baseDate,
            sequenceOffset = sequenceOffset,
            continuousSessionCount = continuousCount.coerceAtLeast(1)
        )
    }

    private fun normalizeSessionBaseDate(label: String): String =
        parseSessionDateLabel(label)?.baseDate
            ?: label.substringBefore("(").trim()

    private fun isSessionForDate(label: String, baseDate: String): Boolean =
        normalizeSessionBaseDate(label) == baseDate

    private fun sessionSpan(label: String): Int =
        parseSessionDateLabel(label)?.continuousSessionCount ?: 1

    private fun normalizeHeaderText(text: String): String =
        text.replace(Regex("\\s+"), " ").trim()

    private fun formattedCellValue(formatter: DataFormatter, cell: Cell?): String =
        normalizeHeaderText(formatter.formatCellValue(cell))

    private fun formulaCellValue(formatter: DataFormatter, evaluator: FormulaEvaluator, cell: Cell?): String =
        normalizeHeaderText(formatter.formatCellValue(cell, evaluator))

    private fun rowHasAnyTemplateData(row: Row?, formatter: DataFormatter): Boolean {
        if (row == null) return false
        val lastColumn = maxOf(ATTENDANCE_COL, row.lastCellNum.toInt() - 1)
        for (col in STUDENT_ID_COL..lastColumn) {
            val value = formattedCellValue(formatter, row.getCell(col))
            if (value.isNotBlank()) return true
        }
        return false
    }

    private fun formatValidationException(message: String?): String {
        val cleaned = message
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            .orEmpty()
        return if (cleaned.isBlank()) {
            "The selected Excel file could not be verified. Please choose a valid RollCheck .xlsx sheet."
        } else {
            "The selected Excel file could not be verified: $cleaned"
        }
    }

    private fun getNumericValue(cell: Cell?, formatter: DataFormatter, evaluator: FormulaEvaluator? = null): Double? {
        if (cell == null) return null
        return when (cell.cellType) {
            CellType.NUMERIC -> cell.numericCellValue
            CellType.FORMULA -> {
                if (evaluator != null) {
                    when (evaluator.evaluateFormulaCell(cell)) {
                        CellType.NUMERIC -> cell.numericCellValue
                        else -> formatter.formatCellValue(cell, evaluator).trim().toDoubleOrNull()
                    }
                } else {
                    cell.numericCellValue
                }
            }
            else -> formatter.formatCellValue(cell).trim().toDoubleOrNull()
        }
    }

    private fun getArgbHex(color: XSSFColor?): String? =
        color?.argbHex?.uppercase(Locale.ROOT)

    private fun fontColorHex(workbook: Workbook, cell: Cell?): String? {
        val style = cell?.cellStyle ?: return null
        val font = workbook.getFontAt(style.fontIndex)
        return if (font is XSSFFont) getArgbHex(font.xssfColor) else null
    }

    private fun isCellFontItalic(workbook: Workbook, cell: Cell?): Boolean {
        val style = cell?.cellStyle ?: return false
        val font = workbook.getFontAt(style.fontIndex)
        return font.italic
    }

    private fun conditionalFontColorHex(rule: ConditionalFormattingRule): String? {
        val fontFormatting = rule.fontFormatting as? org.apache.poi.xssf.usermodel.XSSFFontFormatting
            ?: return null
        return getArgbHex(fontFormatting.fontColor)
    }

    private fun conditionalFontIsItalic(rule: ConditionalFormattingRule): Boolean {
        val fontFormatting = rule.fontFormatting as? org.apache.poi.xssf.usermodel.XSSFFontFormatting
            ?: return false
        return fontFormatting.isItalic
    }

    private fun conditionalFontIsBold(rule: ConditionalFormattingRule): Boolean {
        val fontFormatting = rule.fontFormatting as? org.apache.poi.xssf.usermodel.XSSFFontFormatting
            ?: return false
        return fontFormatting.isBold
    }

    private fun validateExactHeaders(
        workbook: XSSFWorkbook,
        sheet: Sheet,
        formatter: DataFormatter
    ): String? {
        val headerRow = sheet.getRow(HEADER_ROW_2)
            ?: return "File doesn't match RollCheck format. Header row 2 is missing."

        for ((col, expected) in expectedPrimaryHeaders) {
            val actual = formattedCellValue(formatter, headerRow.getCell(col))
            if (actual != expected) {
                return "Header mismatch at ${CellReference.convertNumToColString(col)}2. Expected '$expected' but found '$actual'."
            }
        }

        val dateFormatter = SimpleDateFormat("EEE", Locale.ENGLISH)
        var sawAnyAttendanceHeader = false
        var blankSeen = false
        for (col in DATE_START_COL until headerRow.lastCellNum) {
            val dateLabel = formattedCellValue(formatter, headerRow.getCell(col))
            val dayLabel = formattedCellValue(formatter, sheet.getRow(HEADER_ROW_1)?.getCell(col))
            if (dateLabel.isBlank()) {
                blankSeen = true
                continue
            }
            if (blankSeen) {
                return "Attendance headers must be continuous. Found another header at ${CellReference.convertNumToColString(col)}2 after a blank column."
            }
            val parsed = parseSessionDateLabel(dateLabel)
                ?: return "Invalid attendance header at ${CellReference.convertNumToColString(col)}2. Use one of these formats: dd-MM-yyyy, dd-MM-yyyy(1), or dd-MM-yyyy(c2)."
            val expectedDay = runCatching {
                val parsedDate = SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH).parse(parsed.baseDate)
                parsedDate?.let(dateFormatter::format)
            }.getOrNull()
                ?: return "Invalid attendance date '${parsed.baseDate}' at ${CellReference.convertNumToColString(col)}2."
            if (dayLabel != expectedDay) {
                return "Day header mismatch at ${CellReference.convertNumToColString(col)}1. Expected '$expectedDay' for '$dateLabel' but found '$dayLabel'."
            }
            sawAnyAttendanceHeader = true
        }

        return null
    }

    private fun validateConditionalFontRule(
        sheet: XSSFSheet,
        targetColumn: Int,
        expectedFormula: String,
        expectedColor: String,
        requireItalic: Boolean = false,
        expectedBold: Boolean? = null,
        errorIfMissing: String,
        errorIfWrong: String
    ): String? {
        val formatting = sheet.sheetConditionalFormatting
        for (i in 0 until formatting.numConditionalFormattings) {
            val conditional = formatting.getConditionalFormattingAt(i)
            val coversTargetColumn = conditional.formattingRanges.any { range ->
                range.firstColumn == targetColumn &&
                    range.lastColumn == targetColumn &&
                    range.firstRow <= DATA_START_ROW
            }
            if (!coversTargetColumn) continue

            for (ruleIndex in 0 until conditional.numberOfRules) {
                val rule = conditional.getRule(ruleIndex)
                val actualFormula = normalizeFormula((rule.formula1 ?: "").replace("$", ""))
                if (actualFormula != expectedFormula) continue

                val fontColor = conditionalFontColorHex(rule)
                val isItalic = conditionalFontIsItalic(rule)
                val isBold = conditionalFontIsBold(rule)
                val boldMatches = expectedBold == null || isBold == expectedBold
                return if (fontColor == expectedColor && (!requireItalic || isItalic) && boldMatches) {
                    null
                } else {
                    errorIfWrong
                }
            }
        }

        return errorIfMissing
    }

    private fun validateAttendancePercentageLogic(sheet: XSSFSheet): String? {
        val expectedFormula = normalizeFormula("""AND(E3<>"N/A",E3<D3)""".replace("$", ""))
        return validateConditionalFontRule(
            sheet = sheet,
            targetColumn = ATTENDANCE_COL,
            expectedFormula = expectedFormula,
            expectedColor = ATTENDANCE_THRESHOLD_WARNING_COLOR,
            errorIfMissing = "Attendance (%) threshold conditional formatting is missing in column E. Restore the #FFC107 conditional font-color rule for values below each student's threshold.",
            errorIfWrong = "Attendance (%) threshold warning in column E must use conditional font color #FFC107 for values below each student's threshold."
        )
    }

    private fun validateInactiveConditionalLogic(sheet: XSSFSheet): String? {
        val inactiveFormula = normalizeFormula("C3=1")
        val flagRule = validateConditionalFontRule(
            sheet = sheet,
            targetColumn = INACTIVE_COL,
            expectedFormula = inactiveFormula,
            expectedColor = ABSENT_FONT_COLOR,
            expectedBold = true,
            errorIfMissing = "Inactive Flag formatting is missing in column C. Restore the red rule for value 1.",
            errorIfWrong = "Inactive Flag formatting in column C must use bold font color #EF4444 when the value is 1."
        )
        if (flagRule != null) return flagRule

        val nameRule = validateConditionalFontRule(
            sheet = sheet,
            targetColumn = STUDENT_NAME_COL,
            expectedFormula = inactiveFormula,
            expectedColor = INACTIVE_ATTENDANCE_FONT_COLOR,
            requireItalic = true,
            expectedBold = false,
            errorIfMissing = "Inactive-student formatting is missing in column B. Restore the non-bold italic #94A3B8 rule when Inactive Flag is 1.",
            errorIfWrong = "Inactive-student formatting in column B must use non-bold italic font color #94A3B8 when Inactive Flag is 1."
        )
        if (nameRule != null) return nameRule

        return validateConditionalFontRule(
            sheet = sheet,
            targetColumn = ATTENDANCE_COL,
            expectedFormula = inactiveFormula,
            expectedColor = INACTIVE_ATTENDANCE_FONT_COLOR,
            requireItalic = true,
            expectedBold = false,
            errorIfMissing = "Inactive attendance formatting is missing in column E. Restore the non-bold italic #94A3B8 N/A rule when Inactive Flag is 1.",
            errorIfWrong = "Inactive attendance formatting in column E must use non-bold italic font color #94A3B8 when Inactive Flag is 1."
        )
    }

    private fun parseDisplayedAttendance(display: String): Double? {
        val normalized = display.trim().removeSuffix("%").trim()
        return normalized.toDoubleOrNull()
    }

    private fun normalizeExcelFormatString(format: String): String =
        format
            .trim()
            .replace("\\", "")
            .replace("_", "")
            .replace("*", "")
            .replace("\"", "")

    private fun normalizeFormula(formula: String): String =
        formula.replace(Regex("\\s+"), "").uppercase(Locale.ROOT)

    private fun validateStudentRows(
        workbook: XSSFWorkbook,
        sheet: Sheet,
        formatter: DataFormatter,
        evaluator: FormulaEvaluator
    ): ExcelValidationResult {
        val rollIds = mutableSetOf<String>()
        var firstId = ""
        var firstName = ""
        var lastId = ""
        var lastName = ""
        var rowCount = 0
        var expectedPrefix: String? = null

        val headerRow = sheet.getRow(HEADER_ROW_2) ?: return ExcelValidationResult(false, "Header row 2 is missing.")
        var lastAttendanceCol = DATE_START_COL - 1
        while (formattedCellValue(formatter, headerRow.getCell(lastAttendanceCol + 1)).isNotBlank()) {
            lastAttendanceCol++
        }

        for (i in DATA_START_ROW..sheet.lastRowNum) {
            val row = sheet.getRow(i) ?: break
            val id = formattedCellValue(formatter, row.getCell(STUDENT_ID_COL))
            if (id.isEmpty()) {
                if (rowHasAnyTemplateData(row, formatter)) {
                    return ExcelValidationResult(
                        false,
                        "Student ID is missing at row ${i + 1}. Column A must contain the roll ID for every student row."
                    )
                }
                break
            }
            if (id.none { it.isDigit() }) {
                return ExcelValidationResult(
                    false,
                    "Invalid Student ID '$id' at row ${i + 1}. Column A must contain a roll ID that includes digits."
                )
            }

            val name = formattedCellValue(formatter, row.getCell(STUDENT_NAME_COL))
            if (name.isEmpty()) {
                return ExcelValidationResult(false, "Student name is blank at row ${i + 1}.")
            }

            val normalizedId = id.uppercase()
            if (!rollIds.add(normalizedId)) {
                return ExcelValidationResult(false, "Duplicate Roll ID found: $id")
            }

            val rollDigits = normalizedId.takeLastWhile { it.isDigit() }
            if (rollDigits.length != 3) {
                return ExcelValidationResult(
                    false,
                    "Invalid Student ID '$id' at row ${i + 1}. Roll IDs must end with exactly 3 digits like BT24CSE001."
                )
            }

            val currentPrefix = extractRollPrefix(normalizedId)
            if (expectedPrefix == null) {
                expectedPrefix = currentPrefix
            } else if (currentPrefix != expectedPrefix) {
                val expectedLabel = expectedPrefix!!.ifEmpty { "(no prefix)" }
                val foundLabel = currentPrefix.ifEmpty { "(no prefix)" }
                return ExcelValidationResult(
                    false,
                    "Roll prefix mismatch at row ${i + 1}. Expected $expectedLabel but found $foundLabel."
                )
            }

            val inactive = getNumericValue(row.getCell(INACTIVE_COL), formatter, evaluator)
                ?: return ExcelValidationResult(false, "Inactive Flag must be numeric at row ${i + 1}.")
            if (inactive !in listOf(0.0, 1.0)) {
                return ExcelValidationResult(false, "Inactive Flag must be 0 or 1 at row ${i + 1}.")
            }

            val threshold = getNumericValue(row.getCell(THRESHOLD_COL), formatter, evaluator)
                ?: return ExcelValidationResult(false, "Threshold must be numeric at row ${i + 1}.")
            if (threshold < 0.0 || threshold > 100.0) {
                return ExcelValidationResult(false, "Threshold must be between 0.00 and 100.00 at row ${i + 1}.")
            }

            val attendanceCell = row.getCell(ATTENDANCE_COL)
                ?: return ExcelValidationResult(false, "Attendance (%) cell is missing at row ${i + 1}.")
            val attendanceCellType = attendanceCell.cellType
            if (attendanceCellType == CellType.BLANK) {
                return ExcelValidationResult(false, "Attendance (%) cell is blank at row ${i + 1}.")
            }
            if (inactive == 1.0) {
                if (attendanceCellType == CellType.FORMULA) {
                    val formula = normalizeFormula(attendanceCell.cellFormula)
                    val expectedFormula = normalizeFormula(buildAttendanceFormula(i))
                    if (formula != expectedFormula) {
                        return ExcelValidationResult(
                            false,
                            "Attendance (%) formula mismatch at row ${i + 1}. Restore the RollCheck template formula in column E."
                        )
                    }
                } else if (attendanceCellType != CellType.STRING) {
                    return ExcelValidationResult(
                        false,
                        "Attendance (%) must be N/A for inactive students at row ${i + 1}."
                    )
                }

                val attendanceDisplay = formulaCellValue(formatter, evaluator, attendanceCell)
                if (attendanceDisplay != "N/A") {
                    return ExcelValidationResult(
                        false,
                        "Attendance (%) must display N/A for inactive students at row ${i + 1}."
                    )
                }
            } else {
                if (attendanceCellType != CellType.FORMULA) {
                    return ExcelValidationResult(
                        false,
                        "Attendance (%) must be a formula at row ${i + 1}. Restore the template formula in column E."
                    )
                }
                val formula = normalizeFormula(attendanceCell.cellFormula)
                val expectedFormula = normalizeFormula(buildAttendanceFormula(i))
                if (formula != expectedFormula) {
                    return ExcelValidationResult(
                        false,
                        "Attendance (%) formula mismatch at row ${i + 1}. Restore the RollCheck template formula in column E."
                    )
                }
            }

            for (col in DATE_START_COL..lastAttendanceCol) {
                val cell = row.getCell(col)
                val display = formulaCellValue(formatter, evaluator, cell)
                if (display.isBlank()) continue

                val numeric = display.toIntOrNull()
                    ?: return ExcelValidationResult(false, "Attendance value at ${CellReference.convertNumToColString(col)}${i + 1} must be blank or an integer from 0 to 5.")
                if (numeric !in 0..10) {
                    return ExcelValidationResult(false, "Attendance value at ${CellReference.convertNumToColString(col)}${i + 1} must be between 0 and 10.")
                }

                val allowedColors = if (numeric == 0) {
                    setOf(ABSENT_FONT_COLOR, NEUTRAL_ATTENDANCE_FONT_COLOR)
                } else {
                    setOf(PRESENT_FONT_COLOR, NEUTRAL_ATTENDANCE_FONT_COLOR)
                }
                val actualColor = fontColorHex(workbook, cell)
                if (actualColor !in allowedColors) {
                    return ExcelValidationResult(
                        false,
                        "Attendance color mismatch at ${CellReference.convertNumToColString(col)}${i + 1}. Expected ${if (numeric == 0) "red or neutral" else "green or neutral"} font."
                    )
                }
            }

            if (rowCount == 0) {
                firstId = id
                firstName = name
            }
            lastId = id
            lastName = name
            rowCount++
        }

        if (rowCount == 0) {
            return ExcelValidationResult(false, "No student data found in file.")
        }

        return ExcelValidationResult(
            isValid = true,
            studentCount = rowCount,
            firstStudentId = firstId,
            firstStudentName = firstName,
            lastStudentId = lastId,
            lastStudentName = lastName,
            inferredRollPrefix = expectedPrefix ?: ""
        )
    }

    private fun buildSessionDateLabel(
        baseDateLabel: String,
        previousSessionCount: Int,
        continuousSessionCount: Int
    ): String {
        val prefix = if (previousSessionCount <= 0) {
            baseDateLabel
        } else {
            "$baseDateLabel($previousSessionCount)"
        }
        val normalizedContinuous = continuousSessionCount.coerceAtLeast(1)
        return if (normalizedContinuous > 1) "$prefix(c$normalizedContinuous)" else prefix
    }

    private fun buildAttendanceFormula(rowIndexZeroBased: Int): String {
        val rowNumber = rowIndexZeroBased + 1
        return """IF(C$rowNumber=1,"N/A",IFERROR(ROUND(SUM(F$rowNumber:XFD$rowNumber)/SUMPRODUCT((F$rowNumber:XFD$rowNumber<>"")*IFERROR(VALUE(MID(F$2:XFD$2,FIND("(c",F$2:XFD$2)+2,2)),1))*100,2),0))"""
    }

    private fun refreshAttendanceFormula(
        attendanceCell: Cell,
        rowIndexZeroBased: Int
    ) {
        attendanceCell.cellFormula = buildAttendanceFormula(rowIndexZeroBased)
    }

    private fun rebuildFirstSessionColumnLayout(sheet: Sheet) {
        val xssfSheet = sheet as? XSSFSheet ?: return
        val worksheet = xssfSheet.ctWorksheet
        while (worksheet.sizeOfColsArray() > 0) {
            worksheet.removeCols(0)
        }
        val cols = worksheet.addNewCols()

        fun addCol(min: Int, max: Int, width: Double) {
            val col = cols.addNewCol()
            col.min = min.toLong()
            col.max = max.toLong()
            col.width = width
            col.customWidth = true
        }

        addCol(1, 1, 16.0)
        addCol(2, 2, 28.3984375)
        addCol(3, 3, 10.0)
        addCol(4, 4, 11.0)
        addCol(5, 5, 14.0)
        addCol(6, 25, 4.0)
    }

    private data class FirstSessionStyles(
        val dayHeader: CellStyle,
        val dateHeader: CellStyle,
        val blankOdd: CellStyle,
        val blankEven: CellStyle,
        val presentOdd: CellStyle,
        val presentEven: CellStyle,
        val absentOdd: CellStyle,
        val absentEven: CellStyle
    )

    private fun buildFirstSessionStyles(workbook: XSSFWorkbook, sheet: Sheet): FirstSessionStyles {
        fun seedStyleForRow(rowIndex: Int): CellStyle {
            val row = sheet.getRow(rowIndex)
            return row?.getCell(ATTENDANCE_COL)?.cellStyle
                ?: row?.getCell(THRESHOLD_COL)?.cellStyle
                ?: workbook.getCellStyleAt(0)
        }

        val oddSeed = seedStyleForRow(DATA_START_ROW)
        val evenSeed = seedStyleForRow(DATA_START_ROW + 1)

        return FirstSessionStyles(
            dayHeader = createVerticalSessionHeaderStyle(
                workbook = workbook,
                fillColorHex = "FF151A2D",
                fontColorHex = "FF4FACFE"
            ),
            dateHeader = createVerticalSessionHeaderStyle(
                workbook = workbook,
                fillColorHex = "FF1E2A45",
                fontColorHex = "FFF8FAFC"
            ),
            blankOdd = createSessionValueStyle(workbook, oddSeed, NEUTRAL_ATTENDANCE_FONT_COLOR),
            blankEven = createSessionValueStyle(workbook, evenSeed, NEUTRAL_ATTENDANCE_FONT_COLOR),
            presentOdd = createSessionValueStyle(workbook, oddSeed, PRESENT_FONT_COLOR),
            presentEven = createSessionValueStyle(workbook, evenSeed, PRESENT_FONT_COLOR),
            absentOdd = createSessionValueStyle(workbook, oddSeed, ABSENT_FONT_COLOR),
            absentEven = createSessionValueStyle(workbook, evenSeed, ABSENT_FONT_COLOR)
        )
    }

    private fun createSessionValueStyle(
        workbook: XSSFWorkbook,
        seedStyle: CellStyle,
        fontColorHex: String
    ): CellStyle {
        val style = workbook.createCellStyle() as org.apache.poi.xssf.usermodel.XSSFCellStyle
        style.cloneStyleFrom(seedStyle)
        style.alignment = HorizontalAlignment.CENTER
        style.verticalAlignment = VerticalAlignment.CENTER
        style.dataFormat = workbook.creationHelper.createDataFormat().getFormat("0")

        val seedFont = workbook.getFontAt(seedStyle.fontIndex)
        val font = workbook.createFont() as XSSFFont
        font.fontName = seedFont.fontName ?: "Arial"
        font.fontHeight = seedFont.fontHeight
        font.bold = true
        font.italic = false
        font.underline = seedFont.underline
        font.strikeout = seedFont.strikeout
        font.typeOffset = seedFont.typeOffset
        font.setColor(getXssfColor(fontColorHex))
        style.setFont(font)
        return style
    }

    private fun createVerticalSessionHeaderStyle(
        workbook: XSSFWorkbook,
        fillColorHex: String,
        fontColorHex: String
    ): CellStyle {
        val style = workbook.createCellStyle() as org.apache.poi.xssf.usermodel.XSSFCellStyle
        style.fillPattern = FillPatternType.SOLID_FOREGROUND
        style.setFillForegroundColor(getXssfColor(fillColorHex))
        style.alignment = HorizontalAlignment.CENTER
        style.verticalAlignment = VerticalAlignment.CENTER
        style.rotation = 90
        applyThinWhiteBorder(style)

        val font = workbook.createFont() as XSSFFont
        font.fontName = "Arial"
        font.fontHeightInPoints = 11
        font.bold = true
        font.italic = false
        font.setColor(getXssfColor(fontColorHex))
        style.setFont(font)
        return style
    }

    private fun applyThinWhiteBorder(style: org.apache.poi.xssf.usermodel.XSSFCellStyle) {
        val white = getXssfColor("FFFFFFFF")
        style.borderLeft = BorderStyle.THIN
        style.borderRight = BorderStyle.THIN
        style.borderTop = BorderStyle.THIN
        style.borderBottom = BorderStyle.THIN
        style.setLeftBorderColor(white)
        style.setRightBorderColor(white)
        style.setTopBorderColor(white)
        style.setBottomBorderColor(white)
    }

    /**
     * Converts an 8-char "AARRGGBB" hex string into an XSSFColor.
     * MUST use a 4-byte ARGB array — a 3-byte RGB array omits the alpha byte so
     * POI stores "RRGGBB" in the XML, which is then read back as "00RRGGBB"
     * (alpha = 0 = fully transparent), making every colour invisible.
     * Passing all 4 bytes forces the stored value to "FFRRGGBB".
     */
    private fun getXssfColor(hex: String): XSSFColor {
        val argb = ByteArray(4)
        argb[0] = Integer.decode("0x${hex.substring(0, 2)}").toByte() // Alpha
        argb[1] = Integer.decode("0x${hex.substring(2, 4)}").toByte() // R
        argb[2] = Integer.decode("0x${hex.substring(4, 6)}").toByte() // G
        argb[3] = Integer.decode("0x${hex.substring(6, 8)}").toByte() // B
        return XSSFColor(argb, null)
    }
    private fun cloneTemplateFont(targetWorkbook: XSSFWorkbook, sourceFont: Font?): XSSFFont {
        val targetFont = targetWorkbook.createFont() as XSSFFont
        if (sourceFont == null) return targetFont
        targetFont.fontName = sourceFont.fontName ?: "Arial"
        targetFont.fontHeight = sourceFont.fontHeight
        targetFont.bold = sourceFont.bold
        targetFont.italic = sourceFont.italic
        targetFont.underline = sourceFont.underline
        targetFont.strikeout = sourceFont.strikeout
        targetFont.typeOffset = sourceFont.typeOffset
        if (sourceFont is XSSFFont) {
            sourceFont.xssfColor?.let { targetFont.setColor(it) }
        } else {
            targetFont.color = sourceFont.color
        }
        return targetFont
    }

    private fun cloneTemplateStyle(
        targetWorkbook: XSSFWorkbook,
        sourceWorkbook: XSSFWorkbook,
        sourceStyle: CellStyle?
    ): CellStyle? {
        if (sourceStyle == null) return null
        val cloned = targetWorkbook.createCellStyle() as org.apache.poi.xssf.usermodel.XSSFCellStyle
        cloned.cloneStyleFrom(sourceStyle)
        runCatching {
            cloned.dataFormat = targetWorkbook.creationHelper.createDataFormat()
                .getFormat(sourceStyle.dataFormatString)
        }
        val sourceFont = sourceWorkbook.getFontAt(sourceStyle.fontIndex)
        cloned.setFont(cloneTemplateFont(targetWorkbook, sourceFont))
        return cloned
    }

    private fun templateDateStyleRowIndex(sheet: Sheet, targetRowIndex: Int): Int {
        return when {
            targetRowIndex <= sheet.lastRowNum && sheet.getRow(targetRowIndex)?.getCell(DATE_START_COL) != null -> targetRowIndex
            ((targetRowIndex - DATA_START_ROW) % 2) == 0 -> DATA_START_ROW
            else -> DATA_START_ROW + 1
        }
    }

    private fun extractRollPrefix(studentId: String): String =
        studentId.trim().uppercase().reversed().dropWhile { it.isDigit() }.reversed()

    fun validateFile(context: Context, uri: Uri): ExcelValidationResult {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val workbook = XSSFWorkbook(stream)
                if (workbook.numberOfSheets != EXPECTED_SHEET_COUNT) {
                    workbook.close()
                    return ExcelValidationResult(false, "File must contain exactly 1 sheet.")
                }
                val sheet = workbook.getSheetAt(0) as? XSSFSheet
                    ?: run {
                        workbook.close()
                        return ExcelValidationResult(false, "Invalid RollCheck sheet.")
                    }
                val formatter = DataFormatter()
                val evaluator = workbook.creationHelper.createFormulaEvaluator()

                val headerError = validateExactHeaders(workbook, sheet, formatter)
                if (headerError != null) {
                    workbook.close()
                    return ExcelValidationResult(false, headerError)
                }

                val attendanceLogicError = validateAttendancePercentageLogic(sheet)
                if (attendanceLogicError != null) {
                    workbook.close()
                    return ExcelValidationResult(false, attendanceLogicError)
                }

                val inactiveLogicError = validateInactiveConditionalLogic(sheet)
                if (inactiveLogicError != null) {
                    workbook.close()
                    return ExcelValidationResult(false, inactiveLogicError)
                }

                val result = validateStudentRows(workbook, sheet, formatter, evaluator)
                workbook.close()
                result
            } ?: ExcelValidationResult(false, "Could not read the selected file. Please choose a valid .xlsx workbook.")
        } catch (_: EmptyFileException) {
            ExcelValidationResult(false, "The selected Excel file is empty.")
        } catch (_: OLE2NotOfficeXmlFileException) {
            ExcelValidationResult(false, "This file is not a valid .xlsx workbook. Please choose the RollCheck Excel sheet in .xlsx format.")
        } catch (_: NotOfficeXmlFileException) {
            ExcelValidationResult(false, "This file is not a valid .xlsx workbook. Please choose the RollCheck Excel sheet in .xlsx format.")
        } catch (e: POIXMLException) {
            ExcelValidationResult(false, formatValidationException(e.localizedMessage))
        } catch (e: IllegalArgumentException) {
            ExcelValidationResult(false, formatValidationException(e.localizedMessage))
        } catch (e: Exception) {
            ExcelValidationResult(false, formatValidationException(e.localizedMessage))
        }
    }

    fun importFile(context: Context, uri: Uri, classId: String): String {
        val dir  = File(context.filesDir, "rollcheck").apply { mkdirs() }
        val dest = File(dir, "$classId.xlsx")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { output -> input.copyTo(output) }
        }
        return dest.absolutePath
    }

    fun parseStudents(filePath: String, classId: String): List<StudentEntity> {
        val students = mutableListOf<StudentEntity>()
        try {
            FileInputStream(File(filePath)).use { stream ->
                val workbook = XSSFWorkbook(stream)
                val sheet = workbook.getSheetAt(0)
                for (i in DATA_START_ROW..sheet.lastRowNum) {
                    val row = sheet.getRow(i) ?: break
                    val id = row.getCell(STUDENT_ID_COL)?.toString() ?: ""
                    if (id.isEmpty() || id.none { it.isDigit() }) break

                    val name = row.getCell(STUDENT_NAME_COL)?.toString() ?: ""
                    if (name.isEmpty()) continue

                    val inactive = row.getCell(INACTIVE_COL)?.numericCellValue?.toInt() ?: 0
                    val threshold = row.getCell(THRESHOLD_COL)?.numericCellValue ?: 75.0

                    students.add(StudentEntity(id, classId, name, inactive, threshold))
                }
                workbook.close()
            }
        } catch (_: Exception) {
        }
        return students
    }

    fun getRollRange(filePath: String): Pair<Int, Int> = getSessionStartMeta(filePath).first

    fun getTodaySessionCount(filePath: String): Int = getSessionStartMeta(filePath).second

    fun getRollRangeAndTodaySessionCount(filePath: String): Pair<Pair<Int, Int>, Int> =
        getSessionStartMeta(filePath)

    private fun getSessionStartMeta(filePath: String): Pair<Pair<Int, Int>, Int> {
        var min = Int.MAX_VALUE
        var max = 0
        var todayCount = 0
        val todayLabel = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        val formatter = DataFormatter()

        try {
            FileInputStream(File(filePath)).use { stream ->
                val workbook = XSSFWorkbook(stream)
                val sheet = workbook.getSheetAt(0)

                val headerRow = sheet.getRow(HEADER_ROW_2)
                if (headerRow != null) {
                    for (col in DATE_START_COL until headerRow.lastCellNum) {
                        val label = formatter.formatCellValue(headerRow.getCell(col)).trim()
                        if (isSessionForDate(label, todayLabel)) todayCount += sessionSpan(label)
                    }
                }

                for (i in DATA_START_ROW..sheet.lastRowNum) {
                    val row = sheet.getRow(i) ?: break
                    val id = formatter.formatCellValue(row.getCell(STUDENT_ID_COL)).trim()
                    if (id.isEmpty() || id.none { it.isDigit() }) break
                    val numeric = id.reversed().takeWhile { it.isDigit() }.reversed().toIntOrNull() ?: continue
                    if (numeric < min) min = numeric
                    if (numeric > max) max = numeric
                }
                workbook.close()
            }
        } catch (_: Exception) {
        }

        val range = if (max == 0) Pair(0, 0) else Pair(min, max)
        return Pair(range, todayCount)
    }

    fun updateStudentDetails(
        filePath: String,
        studentId: String,
        newName: String,
        inactiveFlag: Int,
        threshold: Double
    ): Boolean {
        val file = File(filePath)
        return try {
            val workbook = FileInputStream(file).use { XSSFWorkbook(it) }
            val sheet = workbook.getSheetAt(0)
            var updated = false

            for (i in DATA_START_ROW..sheet.lastRowNum) {
                val row = sheet.getRow(i) ?: break
                val id = row.getCell(STUDENT_ID_COL)?.toString() ?: ""
                if (id.isEmpty()) break
                if (id == studentId) {
                    // ── Col B: Student Name ──────────────────────────────────────────
                    // inactive → #94A3B8 italic, not bold
                    // active   → #F8FAFC normal, not bold
                    val nameCell = row.getCell(STUDENT_NAME_COL)
                    nameCell.setCellValue(newName)
                    val nameStyle = workbook.createCellStyle()
                    nameStyle.cloneStyleFrom(nameCell.cellStyle)
                    val nameFont = workbook.createFont() as XSSFFont
                    val seedNameFont = workbook.getFontAt(nameCell.cellStyle.fontIndex)
                    nameFont.fontName           = seedNameFont.fontName ?: "Arial"
                    nameFont.fontHeightInPoints = seedNameFont.fontHeightInPoints
                    nameFont.bold               = false
                    nameFont.italic             = (inactiveFlag == 1)
                    nameFont.setColor(getXssfColor(if (inactiveFlag == 1) "FF94A3B8" else "FFF8FAFC"))
                    nameStyle.setFont(nameFont)
                    nameCell.cellStyle = nameStyle

                    // ── Col C: Inactive Flag ─────────────────────────────────────────
                    // inactive → #EF4444 red,   bold, not italic
                    // active   → #10B981 green, bold, not italic
                    val flagCell = row.getCell(INACTIVE_COL)
                    flagCell.setCellValue(inactiveFlag.toDouble())
                    val flagStyle = workbook.createCellStyle()
                    flagStyle.cloneStyleFrom(flagCell.cellStyle)
                    val flagFont = workbook.createFont() as XSSFFont
                    val seedFlagFont = workbook.getFontAt(flagCell.cellStyle.fontIndex)
                    flagFont.fontName           = seedFlagFont.fontName ?: "Arial"
                    flagFont.fontHeightInPoints = seedFlagFont.fontHeightInPoints
                    flagFont.bold               = true
                    flagFont.italic             = false
                    flagFont.setColor(getXssfColor(if (inactiveFlag == 1) "FFEF4444" else "FF10B981"))
                    flagStyle.setFont(flagFont)
                    flagCell.cellStyle = flagStyle

                    // ── Col D: Threshold (no style change) ───────────────────────────
                    row.getCell(THRESHOLD_COL).setCellValue(threshold)

                    // ── Col E: Attendance % ──────────────────────────────────────────
                    // inactive → #94A3B8 italic, not bold  (formula shows "N/A")
                    // active   → #F8FAFC bold,   not italic
                    val attCell = row.getCell(ATTENDANCE_COL)
                    if (attCell != null) {
                        val attStyle = workbook.createCellStyle()
                        attStyle.cloneStyleFrom(attCell.cellStyle)
                        attStyle.dataFormat = workbook.creationHelper.createDataFormat().getFormat("0.00")
                        val attFont = workbook.createFont() as XSSFFont
                        val seedAttFont = workbook.getFontAt(attCell.cellStyle.fontIndex)
                        attFont.fontName           = seedAttFont.fontName ?: "Arial"
                        attFont.fontHeightInPoints = seedAttFont.fontHeightInPoints
                        attFont.bold               = (inactiveFlag == 0)
                        attFont.italic             = (inactiveFlag == 1)
                        attFont.setColor(getXssfColor(if (inactiveFlag == 1) "FF94A3B8" else "FFF8FAFC"))
                        attStyle.setFont(attFont)
                        attCell.cellStyle = attStyle
                        refreshAttendanceFormula(attCell, i)
                    }

                    updated = true
                    break
                }
            }

            if (updated) {
            (sheet as? XSSFSheet)?.columnHelper?.cleanColumns()
            workbook.setForceFormulaRecalculation(true)
                FileOutputStream(file).use { workbook.write(it) }
            }
            workbook.close()
            updated
        } catch (e: Exception) {
            Log.e("ExcelManager", "writeAttendance failed for $filePath", e)
            false
        }
    }

    fun writeAttendance(
        filePath: String,
        attendanceMap: Map<String, Int>,
        dateLabel: String = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date()),
        continuousSessionCount: Int = 1
    ): Boolean {
        val file = File(filePath)
        return try {
            val workbook = FileInputStream(file).use { XSSFWorkbook(it) }
            val sheet = workbook.getSheetAt(0)

            val headerRow1 = sheet.getRow(HEADER_ROW_1) ?: sheet.createRow(HEADER_ROW_1)
            val headerRow2 = sheet.getRow(HEADER_ROW_2) ?: sheet.createRow(HEADER_ROW_2)
            fun Row.getOrCreateCellAt(index: Int): Cell = getCell(index) ?: createCell(index)
            val firstSessionStyles by lazy { buildFirstSessionStyles(workbook, sheet) }
            fun firstSessionStyle(rowIndex: Int, status: Int?, inactive: Boolean): CellStyle {
                val evenStripe = ((rowIndex - DATA_START_ROW) % 2) == 1
                return when {
                    inactive || status == null -> if (evenStripe) firstSessionStyles.blankEven else firstSessionStyles.blankOdd
                    status > 0 -> if (evenStripe) firstSessionStyles.presentEven else firstSessionStyles.presentOdd
                    else -> if (evenStripe) firstSessionStyles.absentEven else firstSessionStyles.absentOdd
                }
            }
            fun colorizedStyle(seedStyle: CellStyle, color: XSSFColor): CellStyle {
                val newStyle = workbook.createCellStyle()
                newStyle.cloneStyleFrom(seedStyle)
                newStyle.dataFormat = workbook.creationHelper.createDataFormat().getFormat("0")
                val seedFont = workbook.getFontAt(seedStyle.fontIndex)
                val coloredFont = workbook.createFont() as XSSFFont
                coloredFont.fontName = seedFont.fontName ?: "Arial"
                coloredFont.fontHeight = seedFont.fontHeight
                coloredFont.bold = true
                coloredFont.italic = false
                coloredFont.underline = seedFont.underline
                coloredFont.strikeout = seedFont.strikeout
                coloredFont.typeOffset = seedFont.typeOffset
                coloredFont.setColor(color)
                newStyle.setFont(coloredFont)
                return newStyle
            }
            val colorizedStyleCache = mutableMapOf<String, CellStyle>()
            fun cachedColorizedStyle(seedStyle: CellStyle, color: XSSFColor): CellStyle {
                val colorKey = color.argbHex ?: "unknown"
                val key = "${seedStyle.index}|$colorKey"
                return colorizedStyleCache.getOrPut(key) { colorizedStyle(seedStyle, color) }
            }

            val formatter = DataFormatter()
            var newColIdx = DATE_START_COL
            while (formatter.formatCellValue(headerRow2.getCell(newColIdx)).trim().isNotBlank()) {
                newColIdx++
            }

            val baseDateLabel = normalizeSessionBaseDate(dateLabel)
            val parsedBaseDate = runCatching {
                SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).parse(baseDateLabel)
            }.getOrNull() ?: Date()
            val dayName = SimpleDateFormat("EEE", Locale.getDefault()).format(parsedBaseDate)
            var sameDaySessionCount = 0
            for (col in DATE_START_COL until newColIdx) {
                val existingLabel = formatter.formatCellValue(headerRow2.getCell(col)).trim()
                if (isSessionForDate(existingLabel, baseDateLabel)) {
                    sameDaySessionCount += sessionSpan(existingLabel)
                }
            }
            val finalDateLabel = buildSessionDateLabel(
                baseDateLabel = baseDateLabel,
                previousSessionCount = sameDaySessionCount,
                continuousSessionCount = continuousSessionCount
            )
            val headerCell1 = headerRow1.getOrCreateCellAt(newColIdx)
            val headerCell2 = headerRow2.getOrCreateCellAt(newColIdx)
            headerCell1.setCellValue(dayName)
            headerCell2.setCellValue(finalDateLabel)

            val previousDateCol = if (newColIdx > DATE_START_COL) newColIdx - 1 else null
            if (previousDateCol != null) {
                if (headerCell1.cellStyle.index.toInt() == 0) {
                    headerRow1.getCell(previousDateCol)?.cellStyle?.let { headerCell1.cellStyle = it }
                }
                if (headerCell2.cellStyle.index.toInt() == 0) {
                    headerRow2.getCell(previousDateCol)?.cellStyle?.let { headerCell2.cellStyle = it }
                }
                sheet.setColumnWidth(newColIdx, sheet.getColumnWidth(previousDateCol))
            } else {
                rebuildFirstSessionColumnLayout(sheet)
                headerCell1.cellStyle = firstSessionStyles.dayHeader
                headerCell2.cellStyle = firstSessionStyles.dateHeader
                sheet.setColumnWidth(newColIdx, 4 * 256)
                headerRow1.heightInPoints = 34.5f
                headerRow2.heightInPoints = 109.5f
            }

            val presentColor = getXssfColor(PRESENT_FONT_COLOR)
            val absentColor = getXssfColor(ABSENT_FONT_COLOR)

            for (i in DATA_START_ROW..sheet.lastRowNum) {
                val row = sheet.getRow(i) ?: break
                val id = row.getCell(STUDENT_ID_COL)?.toString() ?: ""
                if (id.isEmpty()) break
                if (id.none { it.isDigit() }) break

                val inactive = row.getCell(INACTIVE_COL)?.numericCellValue?.toInt() ?: 0
                val status = attendanceMap[id]
                val prevCell = if (previousDateCol != null) row.getCell(previousDateCol) else null
                val cell = row.getOrCreateCellAt(newColIdx)

                if (previousDateCol != null) {
                    if (cell.cellStyle.index.toInt() == 0 && prevCell != null) {
                        cell.cellStyle = prevCell.cellStyle
                    }
                } else {
                    cell.cellStyle = firstSessionStyle(i, status, inactive == 1)
                }

                if (inactive != 1 && status != null) {
                    val sessionValue = if (status > 0) continuousSessionCount.coerceAtLeast(1) else 0
                    cell.setCellValue(sessionValue.toDouble())

                    if (previousDateCol != null) {
                        val styleSeed = when {
                            cell.cellStyle.index.toInt() != 0 -> cell.cellStyle
                            prevCell != null -> prevCell.cellStyle
                            else -> null
                        }
                        if (styleSeed != null) {
                            val targetColor = if (sessionValue > 0) presentColor else absentColor
                            cell.cellStyle = cachedColorizedStyle(styleSeed, targetColor)
                        }
                    } else {
                        cell.cellStyle = firstSessionStyle(i, if (sessionValue > 0) 1 else 0, false)
                    }
                }

                val attendanceCell = row.getCell(ATTENDANCE_COL)
                if (attendanceCell != null) {
                    refreshAttendanceFormula(attendanceCell, i)
                }
            }

            (sheet as? XSSFSheet)?.columnHelper?.cleanColumns()
            workbook.setForceFormulaRecalculation(true)
            val tempFile = File(file.parentFile, "${file.nameWithoutExtension}_qm_write_temp.xlsx")
            FileOutputStream(tempFile).use { output ->
                workbook.write(output)
                output.fd.sync()
            }
            workbook.close()

            FileInputStream(tempFile).use { verifyInput ->
                XSSFWorkbook(verifyInput).close()
            }

            if (file.exists() && !file.delete()) {
                tempFile.delete()
                return false
            }
            val replaced = tempFile.renameTo(file).also {
                if (!it) {
                    try {
                        tempFile.copyTo(file, overwrite = true)
                        tempFile.delete()
                    } catch (copyError: Exception) {
                        Log.e("ExcelManager", "temp workbook replacement failed for $filePath", copyError)
                    }
                }
            }
            replaced || file.exists()
        } catch (e: Exception) {
            Log.e("ExcelManager", "writeAttendance failed for $filePath", e)
            false
        }
    }
    private fun getColumnLetter(col: Int): String {
        var n = col
        val letter = StringBuilder()
        while (n > 0) {
            n--
            letter.insert(0, ('A'.code + (n % 26)).toChar())
            n /= 26
        }
        return letter.toString()
    }

    fun markHoliday(
        filePath: String,
        label: String,
        dateLabel: String = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
    ): Boolean {
        val file = File(filePath)
        return try {
            val workbook = FileInputStream(file).use { XSSFWorkbook(it) }
            val sheet = workbook.getSheetAt(0)

            // 1. Find the last student row
            var lastStudentRow = DATA_START_ROW - 1
            for (i in DATA_START_ROW..sheet.lastRowNum) {
                val row = sheet.getRow(i) ?: break
                val sid = row.getCell(STUDENT_ID_COL)?.toString() ?: ""
                if (sid.isBlank() || sid.none { it.isDigit() }) break
                lastStudentRow = i
            }

            // 2. Locate the "Holiday / Event Register" section header.
            //    The template places it 6 rows below the last student row.
            //    Scan forward to find it so the code also works on files where
            //    the gap might differ slightly.
            var holidayHeaderRow = -1
            for (i in (lastStudentRow + 1)..sheet.lastRowNum) {
                val row = sheet.getRow(i) ?: continue
                val c1 = row.getCell(0)?.toString() ?: ""
                if (c1.contains("Holiday", ignoreCase = true) || c1.contains("Event", ignoreCase = true)) {
                    holidayHeaderRow = i
                    break
                }
            }

            // If not found, place it 6 rows after the last student (template gap)
            if (holidayHeaderRow == -1) {
                holidayHeaderRow = lastStudentRow + 6
            }

            // 3. Find the next empty row inside the holiday data block.
            //    Skip the section header and the column-label row below it (+2).
            val dataStartRow = holidayHeaderRow + 2
            var targetRowIdx = dataStartRow
            while (targetRowIdx <= sheet.lastRowNum) {
                val cellVal = sheet.getRow(targetRowIdx)?.getCell(0)?.toString()
                if (cellVal.isNullOrBlank()) break
                targetRowIdx++
            }

            // 4. Determine correct alternating background shade for this new entry.
            //    entryCount = number of data rows already present before this new one.
            //    Even index (0,2,4…) → #151A2D (shade A)   Odd (1,3,5…) → #1A2035 (shade B)
            //    We derive this from position rather than cloning the previous row, so the
            //    shade always alternates correctly regardless of how many entries exist.
            val entryCount = targetRowIdx - dataStartRow
            val bgHex   = if (entryCount % 2 == 0) "FF151A2D" else "FF1A2035"
            val bgColor = getXssfColor(bgHex)

            // Thin white border used on all holiday data cells — matches every
            // existing entry row (and all student rows) in the template exactly.
            val whiteThin = BorderStyle.THIN
            val whiteColor = getXssfColor("FFFFFFFF")

            // Col A (date): sky-blue font #4FACFE, center-aligned, 10pt Arial, not bold
            val dateFont = workbook.createFont() as XSSFFont
            dateFont.setColor(getXssfColor("FF4FACFE"))
            dateFont.bold               = false
            dateFont.italic             = false
            dateFont.fontHeightInPoints = 10
            dateFont.fontName           = "Arial"
            val dateStyle               = workbook.createCellStyle()
            dateStyle.setFillForegroundColor(bgColor)
            dateStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND)
            dateStyle.setFont(dateFont)
            dateStyle.alignment         = HorizontalAlignment.CENTER
            dateStyle.verticalAlignment = VerticalAlignment.CENTER
            dateStyle.borderTop         = whiteThin
            dateStyle.borderBottom      = whiteThin
            dateStyle.borderLeft        = whiteThin
            dateStyle.borderRight       = whiteThin
            dateStyle.setTopBorderColor(whiteColor)
            dateStyle.setBottomBorderColor(whiteColor)
            dateStyle.setLeftBorderColor(whiteColor)
            dateStyle.setRightBorderColor(whiteColor)

            // Col B (name): near-white font #F8FAFC, left-aligned, 10pt Arial, not bold
            val nameFont = workbook.createFont() as XSSFFont
            nameFont.setColor(getXssfColor("FFF8FAFC"))
            nameFont.bold               = false
            nameFont.italic             = false
            nameFont.fontHeightInPoints = 10
            nameFont.fontName           = "Arial"
            val nameStyle               = workbook.createCellStyle()
            nameStyle.setFillForegroundColor(bgColor)
            nameStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND)
            nameStyle.setFont(nameFont)
            nameStyle.alignment         = HorizontalAlignment.LEFT
            nameStyle.verticalAlignment = VerticalAlignment.CENTER
            nameStyle.borderTop         = whiteThin
            nameStyle.borderBottom      = whiteThin
            nameStyle.borderLeft        = whiteThin
            nameStyle.borderRight       = whiteThin
            nameStyle.setTopBorderColor(whiteColor)
            nameStyle.setBottomBorderColor(whiteColor)
            nameStyle.setLeftBorderColor(whiteColor)
            nameStyle.setRightBorderColor(whiteColor)

            val row = sheet.getRow(targetRowIdx) ?: sheet.createRow(targetRowIdx)
            // Match the template row height exactly — sheet.createRow() defaults to ~15pt
            // which makes new entries visually shorter than the 18pt template rows.
            row.heightInPoints = 18f

            val cell0 = row.createCell(0)
            cell0.setCellValue(dateLabel)
            cell0.cellStyle = dateStyle

            val cell1 = row.createCell(1)
            cell1.setCellValue(label.ifEmpty { "Holiday" })
            cell1.cellStyle = nameStyle

            // Columns C onwards in the holiday block have no fill in the template
            // so we intentionally leave them untouched.

            FileOutputStream(file).use { workbook.write(it) }
            workbook.close()
            true
        } catch (e: Exception) {
            Log.e("ExcelManager", "writeAttendance failed for $filePath", e)
            false
        }
    }

    fun copyTemplateToUri(context: Context, destUri: Uri): Boolean = try {
        context.assets.open("RollCheck_Attendance_Template.xlsx").use { input ->
            context.contentResolver.openOutputStream(destUri)?.use { output -> input.copyTo(output) }
        }
        true
    } catch (e: Exception) { false }

    fun getTemplateBytes(context: Context): ByteArray? = try {
        context.assets.open("RollCheck_Attendance_Template.xlsx").readBytes()
    } catch (e: Exception) { null }

    fun downloadTemplateToDocuments(context: Context): Boolean = try {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "RollCheck_Attendance_Template.xlsx")
                put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/RollCheck")
            }
            val destUri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
            destUri?.let { uri ->
                context.assets.open("RollCheck_Attendance_Template.xlsx").use { input ->
                    resolver.openOutputStream(uri)?.use { output -> input.copyTo(output) }
                }
                true
            } ?: false
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "RollCheck")
            if (!dir.exists()) dir.mkdirs()
            val destFile = File(dir, "RollCheck_Attendance_Template.xlsx")
            context.assets.open("RollCheck_Attendance_Template.xlsx").use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
            }
            true
        }
    } catch (e: Exception) {
        false
    }

    fun downloadTemplateToDownloads(context: Context): Boolean = downloadTemplateToDocuments(context)
}



