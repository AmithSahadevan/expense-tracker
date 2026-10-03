package com.example

import com.example.data.export.XlsxCell
import com.example.data.export.XlsxColumn
import com.example.data.export.XlsxSheet
import com.example.data.export.XlsxWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.util.Calendar
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The writer emits a real xlsx by hand, so these tests open the result as a zip of XML and check
 * the parts Excel insists on. A file that fails here is one Excel refuses to open at all.
 */
class XlsxWriterTest {

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val parts = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                parts[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
            }
        }
        return parts
    }

    private fun parse(xml: String): Element {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return factory.newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
            .documentElement
    }

    /** Every `<c>` in a sheet, keyed by its reference, as (style, value) where value is text or a number. */
    private fun cells(sheetXml: String): Map<String, Pair<String, String>> {
        val root = parse(sheetXml)
        val found = linkedMapOf<String, Pair<String, String>>()
        val nodes = root.getElementsByTagName("c")
        for (i in 0 until nodes.length) {
            val cell = nodes.item(i) as Element
            val text = cell.getElementsByTagName("t").let { if (it.length > 0) it.item(0).textContent else null }
            val number = cell.getElementsByTagName("v").let { if (it.length > 0) it.item(0).textContent else null }
            found[cell.getAttribute("r")] = cell.getAttribute("s") to (text ?: number ?: "")
        }
        return found
    }

    private fun millisAt(year: Int, month: Int, day: Int, hour: Int = 0) = Calendar.getInstance().apply {
        clear()
        set(year, month, day, hour, 0, 0)
    }.timeInMillis

    private val sample = XlsxSheet(
        name = "Transactions",
        columns = listOf(XlsxColumn("Date", 14), XlsxColumn("Title", 20), XlsxColumn("Amount", 12)),
        rows = listOf(
            listOf(XlsxCell.Day(millisAt(2024, Calendar.JANUARY, 1)), XlsxCell.Text("Rent & bills"), XlsxCell.Money(-1200.5)),
            listOf(XlsxCell.Empty, XlsxCell.Text("<script>"), XlsxCell.Money(50000.0))
        )
    )

    // --------------------------------------------------------------- packaging

    @Test
    fun theWorkbookCarriesEveryPartExcelLooksFor() {
        val parts = unzip(XlsxWriter.write(listOf(sample, sample.copy(name = "Budgets"))))

        assertTrue(parts.keys.toString(), parts.containsKey("[Content_Types].xml"))
        assertTrue(parts.containsKey("_rels/.rels"))
        assertTrue(parts.containsKey("xl/workbook.xml"))
        assertTrue(parts.containsKey("xl/_rels/workbook.xml.rels"))
        assertTrue(parts.containsKey("xl/styles.xml"))
        assertTrue(parts.containsKey("xl/worksheets/sheet1.xml"))
        assertTrue(parts.containsKey("xl/worksheets/sheet2.xml"))
        assertNull("a third sheet was never asked for", parts["xl/worksheets/sheet3.xml"])
    }

    @Test
    fun everyPartIsWellFormedXml() {
        val parts = unzip(XlsxWriter.write(listOf(sample, sample.copy(name = "Budgets"))))

        parts.forEach { (name, body) ->
            assertNotNull("$name is not parseable XML", parse(body))
        }
    }

    @Test
    fun eachSheetIsDeclaredAndPointedAtItsOwnFile() {
        val parts = unzip(XlsxWriter.write(listOf(sample, sample.copy(name = "Budgets"))))

        val workbook = parts.getValue("xl/workbook.xml")
        assertTrue(workbook, workbook.contains("name=\"Transactions\" sheetId=\"1\" r:id=\"rId1\""))
        assertTrue(workbook, workbook.contains("name=\"Budgets\" sheetId=\"2\" r:id=\"rId2\""))

        val rels = parts.getValue("xl/_rels/workbook.xml.rels")
        assertTrue(rels, rels.contains("Id=\"rId1\"") && rels.contains("worksheets/sheet1.xml"))
        assertTrue(rels, rels.contains("Id=\"rId2\"") && rels.contains("worksheets/sheet2.xml"))
        assertTrue("styles must be related too", rels.contains("Id=\"rId3\"") && rels.contains("styles.xml"))
    }

    @Test
    fun sheetNamesAreTrimmedToWhatExcelAccepts() {
        val awkward = listOf(
            sample.copy(name = "Money/Flow: 2026?"),
            sample.copy(name = "A very long sheet name that runs past the limit"),
            sample.copy(name = "Budgets"),
            sample.copy(name = "budgets"),
            sample.copy(name = "   ")
        )

        val workbook = unzip(XlsxWriter.write(awkward)).getValue("xl/workbook.xml")
        val names = Regex("name=\"([^\"]*)\"").findAll(workbook).map { it.groupValues[1] }.toList()

        assertEquals(5, names.size)
        assertEquals("MoneyFlow 2026", names[0])
        assertTrue(names[1], names[1].length <= 31)
        assertEquals("Budgets", names[2])
        assertEquals("a duplicate name must be made unique", "budgets 2", names[3])
        assertTrue("a blank name needs replacing", names[4].isNotBlank())
    }

    @Test
    fun aWorkbookWithNoSheetsIsRefused() {
        val failure = runCatching { XlsxWriter.write(emptyList()) }.exceptionOrNull()

        assertTrue(failure.toString(), failure is IllegalArgumentException)
    }

    // ------------------------------------------------------------------- cells

    @Test
    fun headersAreTheFirstRowAndCarryTheHeaderStyle() {
        val sheet = unzip(XlsxWriter.write(listOf(sample))).getValue("xl/worksheets/sheet1.xml")

        val cells = cells(sheet)
        assertEquals("1" to "Date", cells["A1"])
        assertEquals("1" to "Title", cells["B1"])
        assertEquals("1" to "Amount", cells["C1"])
    }

    @Test
    fun eachKindOfCellGetsItsOwnNumberFormat() {
        val sheet = unzip(XlsxWriter.write(listOf(sample))).getValue("xl/worksheets/sheet1.xml")

        val cells = cells(sheet)
        assertEquals("date style", "3", cells.getValue("A2").first)
        assertEquals("text style", "0", cells.getValue("B2").first)
        assertEquals("money style", "2", cells.getValue("C2").first)
        assertEquals("-1200.5", cells.getValue("C2").second)
    }

    @Test
    fun textIsEscapedSoMarkupInANoteCannotBreakTheFile() {
        val sheet = unzip(XlsxWriter.write(listOf(sample))).getValue("xl/worksheets/sheet1.xml")

        assertTrue(sheet, sheet.contains("Rent &amp; bills"))
        assertTrue(sheet, sheet.contains("&lt;script&gt;"))
        // Parsing it back gives the original text, which is what Excel will show.
        assertEquals("<script>", cells(sheet).getValue("B3").second)
    }

    @Test
    fun emptyCellsAreLeftOutAltogether() {
        val sheet = unzip(XlsxWriter.write(listOf(sample))).getValue("xl/worksheets/sheet1.xml")

        assertNull("an empty cell should not be written at all", cells(sheet)["A3"])
        assertNotNull(cells(sheet)["B3"])
    }

    @Test
    fun percentagesAndCountsKeepTheirOwnFormats() {
        val sheet = XlsxSheet(
            name = "Budgets",
            columns = listOf(XlsxColumn("Used"), XlsxColumn("Count"), XlsxColumn("Section")),
            rows = listOf(listOf(XlsxCell.Percent(0.825), XlsxCell.Number(3.0), XlsxCell.Label("TOTALS")))
        )

        val cells = cells(unzip(XlsxWriter.write(listOf(sheet))).getValue("xl/worksheets/sheet1.xml"))

        assertEquals("5" to "0.825", cells["A2"])
        assertEquals("0" to "3", cells["B2"])
        assertEquals("6" to "TOTALS", cells["C2"])
    }

    @Test
    fun largeAmountsAreWrittenInFullRatherThanInExponentNotation() {
        val sheet = XlsxSheet(
            name = "Big",
            columns = listOf(XlsxColumn("Amount")),
            rows = listOf(listOf(XlsxCell.Money(1.0E7)), listOf(XlsxCell.Money(0.125)))
        )

        val cells = cells(unzip(XlsxWriter.write(listOf(sheet))).getValue("xl/worksheets/sheet1.xml"))

        assertEquals("10000000", cells.getValue("A2").second)
        assertEquals("0.125", cells.getValue("A3").second)
    }

    @Test
    fun aValueExcelCannotStoreIsSkippedInsteadOfCorruptingTheFile() {
        val sheet = XlsxSheet(
            name = "Edge",
            columns = listOf(XlsxColumn("Amount")),
            rows = listOf(listOf(XlsxCell.Money(Double.NaN)), listOf(XlsxCell.Percent(Double.POSITIVE_INFINITY)))
        )

        val cells = cells(unzip(XlsxWriter.write(listOf(sheet))).getValue("xl/worksheets/sheet1.xml"))

        assertNull(cells["A2"])
        assertNull(cells["A3"])
    }

    // -------------------------------------------------------------- date serial

    @Test
    fun datesUseExcelsOwnDayNumbering() {
        // 45292 is the serial Excel itself shows for 1 January 2024.
        assertEquals(45292.0, XlsxWriter.serial(millisAt(2024, Calendar.JANUARY, 1), dayOnly = true), 0.0)
        assertEquals(45293.0, XlsxWriter.serial(millisAt(2024, Calendar.JANUARY, 2), dayOnly = true), 0.0)
    }

    @Test
    fun theClockTimeIsDroppedForDayCellsAndKeptForTimestamps() {
        val lateEvening = millisAt(2024, Calendar.JANUARY, 1, hour = 23)

        assertEquals("a payment at 11pm still belongs to the 1st", 45292.0, XlsxWriter.serial(lateEvening, dayOnly = true), 0.0)
        assertEquals(45292.0 + 23.0 / 24.0, XlsxWriter.serial(lateEvening, dayOnly = false), 1e-9)
    }

    // ------------------------------------------------------------ column names

    @Test
    fun columnsAreLetteredTheWayASpreadsheetDoes() {
        assertEquals("A", XlsxWriter.columnName(0))
        assertEquals("Z", XlsxWriter.columnName(25))
        assertEquals("AA", XlsxWriter.columnName(26))
        assertEquals("AB", XlsxWriter.columnName(27))
        assertEquals("BA", XlsxWriter.columnName(52))
    }

    @Test
    fun theHeaderRowIsFrozenAndFilterableOnTableSheets() {
        val parts = unzip(XlsxWriter.write(listOf(sample, sample.copy(name = "Summary", autoFilter = false))))

        val table = parts.getValue("xl/worksheets/sheet1.xml")
        assertTrue(table, table.contains("state=\"frozen\""))
        assertTrue("filter must span the header and both rows", table.contains("autoFilter ref=\"A1:C3\""))

        val report = parts.getValue("xl/worksheets/sheet2.xml")
        assertTrue("a report sheet gets no filter", !report.contains("autoFilter"))
    }
}
