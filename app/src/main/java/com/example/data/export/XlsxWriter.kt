package com.example.data.export

import java.io.ByteArrayOutputStream
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.floor

/** One cell's value, which also decides how Excel formats it. */
sealed interface XlsxCell {
    data class Text(val value: String) : XlsxCell

    /** A plain count or ratio, shown with no decimals unless it has them. */
    data class Number(val value: Double) : XlsxCell

    /** An amount, shown as `1,234.50` with negatives in red. */
    data class Money(val value: Double) : XlsxCell

    /** A fraction: 0.82 reads as `82.0%`. */
    data class Percent(val value: Double) : XlsxCell

    /** A calendar day in the device's time zone; the clock time is dropped. */
    data class Day(val millis: Long) : XlsxCell

    data class DayTime(val millis: Long) : XlsxCell

    /** A heading inside a sheet's body, such as a section break on the summary. */
    data class Label(val value: String) : XlsxCell

    data object Empty : XlsxCell
}

data class XlsxColumn(val header: String, val width: Int = 18)

data class XlsxSheet(
    val name: String,
    val columns: List<XlsxColumn>,
    val rows: List<List<XlsxCell>>,
    /** Off for sheets that read as a report rather than a table, like the summary. */
    val autoFilter: Boolean = true
)

/**
 * Writes a workbook as a real `.xlsx` file.
 *
 * An xlsx is a ZIP of SpreadsheetML parts, so building one by hand costs a few hundred lines and
 * keeps a multi-megabyte spreadsheet library (and its Android packaging problems) out of the app.
 * Strings are written inline rather than through a shared-string table: it costs a few bytes on
 * repeated text but means a sheet can be streamed out in a single pass.
 */
object XlsxWriter {

    /** Style indices, matching the order of `cellXfs` in [STYLES]. */
    private const val S_DEFAULT = 0
    private const val S_HEADER = 1
    private const val S_MONEY = 2
    private const val S_DAY = 3
    private const val S_DAY_TIME = 4
    private const val S_PERCENT = 5
    private const val S_LABEL = 6

    private const val DAY_MS = 86_400_000.0

    /** Excel's day 0 is 1899-12-30, which is 25,569 days before the Unix epoch. */
    private const val EPOCH_OFFSET_DAYS = 25_569.0

    private const val MAX_SHEET_NAME = 31

    fun write(sheets: List<XlsxSheet>): ByteArray {
        require(sheets.isNotEmpty()) { "A workbook needs at least one sheet" }
        val named = uniqueNames(sheets)
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.put("[Content_Types].xml", contentTypes(sheets.size))
            zip.put("_rels/.rels", ROOT_RELS)
            zip.put("xl/workbook.xml", workbook(named))
            zip.put("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            zip.put("xl/styles.xml", STYLES)
            sheets.forEachIndexed { index, sheet ->
                zip.put("xl/worksheets/sheet${index + 1}.xml", sheetXml(sheet, isFirst = index == 0))
            }
        }
        return out.toByteArray()
    }

    private fun ZipOutputStream.put(path: String, body: String) {
        putNextEntry(ZipEntry(path))
        write(body.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    // ------------------------------------------------------------------ parts

    private fun contentTypes(sheetCount: Int): String = buildString {
        append(XML_HEADER)
        append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
        for (i in 1..sheetCount) {
            append("<Override PartName=\"/xl/worksheets/sheet$i.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
        }
        append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
        append("</Types>")
    }

    private fun workbook(names: List<String>): String = buildString {
        append(XML_HEADER)
        append("<workbook xmlns=\"$NS_MAIN\" xmlns:r=\"$NS_REL\"><sheets>")
        names.forEachIndexed { index, name ->
            append("<sheet name=\"${esc(name)}\" sheetId=\"${index + 1}\" r:id=\"rId${index + 1}\"/>")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRels(sheetCount: Int): String = buildString {
        append(XML_HEADER)
        append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
        for (i in 1..sheetCount) {
            append("<Relationship Id=\"rId$i\" Type=\"$NS_DOC/worksheet\" Target=\"worksheets/sheet$i.xml\"/>")
        }
        append("<Relationship Id=\"rId${sheetCount + 1}\" Type=\"$NS_DOC/styles\" Target=\"styles.xml\"/>")
        append("</Relationships>")
    }

    private fun sheetXml(sheet: XlsxSheet, isFirst: Boolean): String = buildString {
        val lastColumn = columnName(sheet.columns.size - 1)
        append(XML_HEADER)
        append("<worksheet xmlns=\"$NS_MAIN\">")

        // The header row stays put while the body scrolls under it.
        append("<sheetViews><sheetView${if (isFirst) " tabSelected=\"1\"" else ""} workbookViewId=\"0\">")
        append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>")
        append("</sheetView></sheetViews>")

        append("<cols>")
        sheet.columns.forEachIndexed { index, column ->
            append("<col min=\"${index + 1}\" max=\"${index + 1}\" width=\"${column.width}\" customWidth=\"1\"/>")
        }
        append("</cols>")

        append("<sheetData>")
        append("<row r=\"1\">")
        sheet.columns.forEachIndexed { index, column ->
            append(inlineString(columnName(index) + "1", column.header, S_HEADER))
        }
        append("</row>")
        sheet.rows.forEachIndexed { rowIndex, row ->
            val rowNumber = rowIndex + 2
            append("<row r=\"$rowNumber\">")
            row.forEachIndexed { columnIndex, cell ->
                append(cellXml(columnName(columnIndex) + rowNumber, cell))
            }
            append("</row>")
        }
        append("</sheetData>")

        if (sheet.autoFilter) {
            append("<autoFilter ref=\"A1:$lastColumn${sheet.rows.size + 1}\"/>")
        }
        append("</worksheet>")
    }

    private fun cellXml(ref: String, cell: XlsxCell): String = when (cell) {
        is XlsxCell.Text -> inlineString(ref, cell.value, S_DEFAULT)
        is XlsxCell.Label -> inlineString(ref, cell.value, S_LABEL)
        is XlsxCell.Number -> number(ref, cell.value, S_DEFAULT)
        is XlsxCell.Money -> number(ref, cell.value, S_MONEY)
        is XlsxCell.Percent -> number(ref, cell.value, S_PERCENT)
        is XlsxCell.Day -> number(ref, serial(cell.millis, dayOnly = true), S_DAY)
        is XlsxCell.DayTime -> number(ref, serial(cell.millis, dayOnly = false), S_DAY_TIME)
        XlsxCell.Empty -> ""
    }

    private fun inlineString(ref: String, value: String, style: Int): String {
        if (value.isEmpty()) return ""
        return "<c r=\"$ref\" s=\"$style\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(value)}</t></is></c>"
    }

    private fun number(ref: String, value: Double, style: Int): String {
        // Excel has no way to store these, and writing them produces a file it refuses to open.
        if (value.isNaN() || value.isInfinite()) return ""
        return "<c r=\"$ref\" s=\"$style\">" + "<v>${trim(value)}</v>" + "</c>"
    }

    /** Avoids the `1.0E7` that [Double.toString] would otherwise emit, which Excel reads as text. */
    private fun trim(value: Double): String {
        val rounded = Math.round(value * 1_000_000.0) / 1_000_000.0
        return if (rounded == floor(rounded) && !rounded.isInfinite() && Math.abs(rounded) < 1e15) {
            rounded.toLong().toString()
        } else {
            java.math.BigDecimal(rounded).setScale(6, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString()
        }
    }

    // ----------------------------------------------------------------- helpers

    /**
     * Excel keeps dates as days since 1899-12-30, read in whatever time zone the reader is in.
     * Shifting by the device's offset first means a payment made late on the 3rd still lands on
     * the 3rd rather than slipping a day.
     */
    fun serial(millis: Long, dayOnly: Boolean): Double {
        val local = millis + TimeZone.getDefault().getOffset(millis)
        val days = local / DAY_MS + EPOCH_OFFSET_DAYS
        return if (dayOnly) floor(days) else days
    }

    /** 0 -> A, 25 -> Z, 26 -> AA. */
    fun columnName(index: Int): String {
        require(index >= 0) { "Column index cannot be negative" }
        var remaining = index
        val name = StringBuilder()
        while (true) {
            name.append('A' + remaining % 26)
            remaining = remaining / 26 - 1
            if (remaining < 0) break
        }
        return name.reverse().toString()
    }

    /**
     * Excel refuses `[]:*?/\` in a tab name, caps it at 31 characters, and will not open a file
     * with two tabs of the same name.
     */
    private fun uniqueNames(sheets: List<XlsxSheet>): List<String> {
        val taken = mutableSetOf<String>()
        return sheets.mapIndexed { index, sheet ->
            val cleaned = sheet.name
                .filterNot { it in "[]:*?/\\" }
                .trim()
                .take(MAX_SHEET_NAME)
                .ifBlank { "Sheet${index + 1}" }
            var candidate = cleaned
            var suffix = 2
            while (!taken.add(candidate.lowercase())) {
                val room = MAX_SHEET_NAME - (" $suffix".length)
                candidate = cleaned.take(room) + " " + suffix
                suffix++
            }
            candidate
        }
    }

    private fun esc(raw: String): String {
        val escaped = StringBuilder(raw.length + 16)
        for (ch in raw) {
            when {
                ch == '&' -> escaped.append("&amp;")
                ch == '<' -> escaped.append("&lt;")
                ch == '>' -> escaped.append("&gt;")
                ch == '"' -> escaped.append("&quot;")
                ch == '\'' -> escaped.append("&apos;")
                // XML 1.0 has no way to represent these, and Excel rejects the whole file.
                ch.code < 0x20 && ch != '\t' && ch != '\n' && ch != '\r' -> Unit
                else -> escaped.append(ch)
            }
        }
        return escaped.toString()
    }

    private const val XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
    private const val NS_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val NS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val NS_DOC = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    private val ROOT_RELS = XML_HEADER +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"$NS_DOC/officeDocument\" Target=\"xl/workbook.xml\"/>" +
        "</Relationships>"

    // The first two fills must be `none` then `gray125`; Excel hard-codes those slots.
    private val STYLES = XML_HEADER +
        "<styleSheet xmlns=\"$NS_MAIN\">" +
        "<numFmts count=\"4\">" +
        "<numFmt numFmtId=\"164\" formatCode=\"dd-mmm-yyyy\"/>" +
        "<numFmt numFmtId=\"165\" formatCode=\"dd-mmm-yyyy hh:mm\"/>" +
        "<numFmt numFmtId=\"166\" formatCode=\"#,##0.00;[Red]-#,##0.00\"/>" +
        "<numFmt numFmtId=\"167\" formatCode=\"0.0%\"/>" +
        "</numFmts>" +
        "<fonts count=\"3\">" +
        "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
        "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/><color rgb=\"FFFFFFFF\"/></font>" +
        "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
        "</fonts>" +
        "<fills count=\"3\">" +
        "<fill><patternFill patternType=\"none\"/></fill>" +
        "<fill><patternFill patternType=\"gray125\"/></fill>" +
        "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF1C1F26\"/><bgColor indexed=\"64\"/></patternFill></fill>" +
        "</fills>" +
        "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
        "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
        "<cellXfs count=\"7\">" +
        "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
        "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>" +
        "<xf numFmtId=\"166\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
        "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
        "<xf numFmtId=\"165\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
        "<xf numFmtId=\"167\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
        "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>" +
        "</cellXfs>" +
        "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>" +
        "</styleSheet>"
}
