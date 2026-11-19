package org.mohtech.app.feature.reports

import org.mohtech.app.data.model.ExpenseCategory
import org.mohtech.app.data.model.ExpenseItem
import org.mohtech.app.data.model.Payer
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class XlsxWriterTest {

    @Test
    fun testGenerateAndVerifyValidXlsxZipArchive() {
        val expenses = listOf(
            ExpenseItem(
                id = "exp_01",
                title = "سوبرماركت & خضار <برافو>",
                amount = 125.50,
                currency = "₪",
                category = ExpenseCategory.GROCERIES,
                timestamp = 1731168000000L,
                payer = Payer("self", "أنا", true)
            ),
            ExpenseItem(
                id = "exp_02",
                title = "بنزين للسيارة",
                amount = 50.0,
                currency = "₪",
                category = ExpenseCategory.TRANSPORT,
                timestamp = 1731168100000L,
                payer = Payer("other", "محمد", false)
            )
        )

        val report = ReportBuilder.buildReport(expenses, ReportPeriod.ALL_TIME, weekStartDayIso = 6)
        val xlsxBytes = XlsxWriter.write(report)

        assertTrue(xlsxBytes.isNotEmpty(), "XLSX byte output must not be empty")

        // Unzip with standard Java ZipInputStream and verify entries
        val zipIn = ZipInputStream(ByteArrayInputStream(xlsxBytes))
        val entryMap = mutableMapOf<String, String>()

        var entry = zipIn.nextEntry
        while (entry != null) {
            val content = zipIn.readBytes().decodeToString()
            entryMap[entry.name] = content
            entry = zipIn.nextEntry
        }

        // Verify standard OpenXML parts exist
        assertTrue(entryMap.containsKey("[Content_Types].xml"), "Must contain [Content_Types].xml")
        assertTrue(entryMap.containsKey("_rels/.rels"), "Must contain _rels/.rels")
        assertTrue(entryMap.containsKey("xl/workbook.xml"), "Must contain xl/workbook.xml")
        assertTrue(entryMap.containsKey("xl/_rels/workbook.xml.rels"), "Must contain xl/_rels/workbook.xml.rels")
        assertTrue(entryMap.containsKey("xl/styles.xml"), "Must contain xl/styles.xml")
        assertTrue(entryMap.containsKey("xl/worksheets/sheet1.xml"), "Must contain xl/worksheets/sheet1.xml")
        assertTrue(entryMap.containsKey("xl/worksheets/sheet2.xml"), "Must contain xl/worksheets/sheet2.xml")

        // Verify RTL layout in sheet1
        val sheet1Xml = entryMap["xl/worksheets/sheet1.xml"]!!
        assertTrue(sheet1Xml.contains("""rightToLeft="1""""), "Worksheet must explicitly specify rightToLeft='1' for Arabic layout")
        assertTrue(sheet1Xml.contains("""state="frozen""""), "Header row must be frozen")
        assertTrue(sheet1Xml.contains("""SUM(C2:C3)"""), "Must contain SUM formula for expenses")
        assertTrue(sheet1Xml.contains("سوبرماركت &amp; خضار &lt;برافو&gt;"), "Special XML characters must be properly escaped")
        assertTrue(sheet1Xml.contains("بنزين للسيارة"), "Arabic titles must be encoded in UTF-8 without corruption")

        // Verify summary sheet
        val sheet2Xml = entryMap["xl/worksheets/sheet2.xml"]!!
        assertTrue(sheet2Xml.contains("""rightToLeft="1""""))
        assertTrue(sheet2Xml.contains("إجمالي العملات"))
        assertTrue(sheet2Xml.contains("175.5")) // 125.5 + 50.0
    }

    @Test
    fun testGenerateEmptyReportXlsxDoesNotCrash() {
        val emptyReport = ReportBuilder.buildReport(emptyList(), ReportPeriod.THIS_MONTH)
        val xlsxBytes = XlsxWriter.write(emptyReport)

        assertTrue(xlsxBytes.isNotEmpty())
        val zipIn = ZipInputStream(ByteArrayInputStream(xlsxBytes))
        val names = mutableListOf<String>()
        var entry = zipIn.nextEntry
        while (entry != null) {
            names.add(entry.name)
            entry = zipIn.nextEntry
        }
        assertTrue(names.contains("xl/worksheets/sheet1.xml"))
    }

    @Test
    fun testWriteExportFileToCacheWritesFileAndReturnsValidPath() {
        val testBytes = "test report content".encodeToByteArray()
        val fileName = "test_export_${System.currentTimeMillis()}.txt"
        val path = writeExportFileToCache(fileName, testBytes)

        val file = java.io.File(path)
        assertTrue(file.exists(), "Export file must exist on disk at $path")
        assertEquals("test report content", file.readText())
        file.delete()
    }
}

