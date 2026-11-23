package org.mohtech.app.feature.reports

import kotlin.time.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.mohtech.app.data.accounting.LedgerIntegrityChecker
import org.mohtech.app.data.accounting.Money
import org.mohtech.app.data.accounting.ReceivablesLedger
import org.mohtech.app.data.model.DebtStatus
import org.mohtech.app.data.model.ExpenseItem

/**
 * Pure Kotlin CRC32 implementation compliant with ISO 3309 / IEEE 802.3.
 */
object PureCrc32 {
    private val table = IntArray(256) { i ->
        var crc = i
        for (j in 0 until 8) {
            crc = if ((crc and 1) != 0) (crc ushr 1) xor -306674912 else crc ushr 1
        }
        crc
    }

    fun calculate(data: ByteArray): Long {
        var crc = -1
        for (b in data) {
            val index = (crc xor b.toInt()) and 0xFF
            crc = (crc ushr 8) xor table[index]
        }
        return (crc.inv().toLong()) and 0xFFFFFFFFL
    }
}

/**
 * Lightweight, zero-dependency pure Kotlin ZIP builder using uncompressed STORE method.
 */
class PureZipBuilder {
    private class Entry(
        val name: String,
        val data: ByteArray,
        val crc: Long,
        val offset: Int
    )

    private val entries = mutableListOf<Entry>()
    private val buffer = mutableListOf<Byte>()

    private fun writeShort(value: Int) {
        buffer.add((value and 0xFF).toByte())
        buffer.add(((value ushr 8) and 0xFF).toByte())
    }

    private fun writeInt(value: Long) {
        buffer.add((value and 0xFFL).toByte())
        buffer.add(((value ushr 8) and 0xFFL).toByte())
        buffer.add(((value ushr 16) and 0xFFL).toByte())
        buffer.add(((value ushr 24) and 0xFFL).toByte())
    }

    fun addEntry(name: String, content: String) {
        addEntry(name, content.encodeToByteArray())
    }

    fun addEntry(name: String, data: ByteArray) {
        val nameBytes = name.encodeToByteArray()
        val crc = PureCrc32.calculate(data)
        val offset = buffer.size

        // 1. Local File Header (0x04034b50)
        writeInt(0x04034b50L)
        writeShort(20) // version needed
        writeShort(0x0800) // flags: UTF-8 filename (bit 11)
        writeShort(0) // method: STORE (no compression)
        writeShort(0) // mod time
        writeShort(0) // mod date
        writeInt(crc)
        writeInt(data.size.toLong()) // compressed size
        writeInt(data.size.toLong()) // uncompressed size
        writeShort(nameBytes.size) // filename length
        writeShort(0) // extra field length

        // Filename & Data
        for (b in nameBytes) buffer.add(b)
        for (b in data) buffer.add(b)

        entries.add(Entry(name, data, crc, offset))
    }

    fun build(): ByteArray {
        val centralDirOffset = buffer.size

        // Central Directory Headers (0x02014b50)
        for (e in entries) {
            val nameBytes = e.name.encodeToByteArray()
            writeInt(0x02014b50L)
            writeShort(20) // version made by
            writeShort(20) // version needed
            writeShort(0x0800) // flags: UTF-8
            writeShort(0) // method: STORE
            writeShort(0) // mod time
            writeShort(0) // mod date
            writeInt(e.crc)
            writeInt(e.data.size.toLong())
            writeInt(e.data.size.toLong())
            writeShort(nameBytes.size)
            writeShort(0) // extra length
            writeShort(0) // comment length
            writeShort(0) // disk number
            writeShort(0) // internal attributes
            writeInt(0) // external attributes
            writeInt(e.offset.toLong()) // relative offset of local header

            for (b in nameBytes) buffer.add(b)
        }

        val centralDirSize = buffer.size - centralDirOffset

        // End of Central Directory Record (0x06054b50)
        writeInt(0x06054b50L)
        writeShort(0) // disk number
        writeShort(0) // start disk
        writeShort(entries.size) // records on disk
        writeShort(entries.size) // total records
        writeInt(centralDirSize.toLong())
        writeInt(centralDirOffset.toLong())
        writeShort(0) // comment length

        return buffer.toByteArray()
    }
}

/**
 * XlsxWriter:
 * Generates standards-compliant Excel OpenXML (.xlsx) spreadsheets in 100% pure Kotlin.
 * Zero Apache POI or native dependencies.
 * Includes:
 * - Full Arabic RTL worksheet layout (rightToLeft="1")
 * - Real Excel date cells (serial date formatted with numFmtId="14")
 * - Auto-filter on data ranges (<autoFilter>)
 * - Multi-currency subtotal protection (never sums different currencies together)
 * - Complete Receivables Accounting Ledger workbook (Summaries, Allocations, Settlements, Aging, Audit)
 */
object XlsxWriter {

    private fun escapeXml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun buildStylesXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2">
    <font><name val="Arial"/><sz val="11"/></font>
    <font><name val="Arial"/><sz val="11"/><b val="1"/></font>
  </fonts>
  <fills count="3">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFE8ECF2"/></patternFill></fill>
  </fills>
  <borders count="1">
    <border><left/><right/><top/><bottom/><diagonal/></border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="4">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
    <xf numFmtId="2" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
    <xf numFmtId="14" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
  </cellXfs>
</styleSheet>"""
    }

    fun write(report: ReportData): ByteArray {
        val zip = PureZipBuilder()

        // 1. [Content_Types].xml
        zip.addEntry(
            "[Content_Types].xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""
        )

        // 2. _rels/.rels
        zip.addEntry(
            "_rels/.rels",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
        )

        // 3. xl/workbook.xml
        zip.addEntry(
            "xl/workbook.xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="المصروفات" sheetId="1" r:id="rId1"/>
    <sheet name="الملخص" sheetId="2" r:id="rId2"/>
  </sheets>
</workbook>"""
        )

        // 4. xl/_rels/workbook.xml.rels
        zip.addEntry(
            "xl/_rels/workbook.xml.rels",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""
        )

        // 5. xl/styles.xml
        zip.addEntry("xl/styles.xml", buildStylesXml())

        // 6. xl/worksheets/sheet1.xml (المصروفات)
        zip.addEntry("xl/worksheets/sheet1.xml", buildExpensesSheet(report))

        // 7. xl/worksheets/sheet2.xml (الملخص)
        zip.addEntry("xl/worksheets/sheet2.xml", buildSummarySheet(report))

        return zip.build()
    }

    private fun buildExpensesSheet(report: ReportData): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetViews>
    <sheetView rightToLeft="1" tabSelected="1" workbookViewId="0">
      <pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>
    </sheetView>
  </sheetViews>
  <cols>
    <col min="1" max="1" width="14" customWidth="1"/>
    <col min="2" max="2" width="28" customWidth="1"/>
    <col min="3" max="3" width="14" customWidth="1"/>
    <col min="4" max="4" width="10" customWidth="1"/>
    <col min="5" max="5" width="18" customWidth="1"/>
    <col min="6" max="6" width="16" customWidth="1"/>
    <col min="7" max="7" width="16" customWidth="1"/>
  </cols>
  <sheetData>
""")

        // Row 1: Header (styled with bold and light fill, style index 1)
        sb.append("""    <row r="1" spans="1:7">
      <c r="A1" s="1" t="inlineStr"><is><t>التاريخ</t></is></c>
      <c r="B1" s="1" t="inlineStr"><is><t>البند</t></is></c>
      <c r="C1" s="1" t="inlineStr"><is><t>المبلغ</t></is></c>
      <c r="D1" s="1" t="inlineStr"><is><t>العملة</t></is></c>
      <c r="E1" s="1" t="inlineStr"><is><t>التصنيف</t></is></c>
      <c r="F1" s="1" t="inlineStr"><is><t>الدافع</t></is></c>
      <c r="G1" s="1" t="inlineStr"><is><t>حصتي</t></is></c>
    </row>
""")

        val timeZone = TimeZone.currentSystemDefault()
        var rowIndex = 2

        for (item in report.items) {
            val date = Instant.fromEpochMilliseconds(item.timestamp).toLocalDateTime(timeZone).date
            val serialDate = date.toEpochDays() + 25569
            val title = escapeXml(item.title)
            val curr = escapeXml(item.currency.ifBlank { "₪" })
            val cat = escapeXml(item.category.arabicTitle)
            val payer = escapeXml(item.payer.name)
            val myShare = item.effectiveAmount

            sb.append("""    <row r="$rowIndex" spans="1:7">
      <c r="A$rowIndex" s="3"><v>$serialDate</v></c>
      <c r="B$rowIndex" t="inlineStr"><is><t>$title</t></is></c>
      <c r="C$rowIndex" s="2"><v>${item.amount}</v></c>
      <c r="D$rowIndex" t="inlineStr"><is><t>$curr</t></is></c>
      <c r="E$rowIndex" t="inlineStr"><is><t>$cat</t></is></c>
      <c r="F$rowIndex" t="inlineStr"><is><t>$payer</t></is></c>
      <c r="G$rowIndex" s="2"><v>$myShare</v></c>
    </row>
""")
            rowIndex++
        }

        val lastDataRow = rowIndex - 1
        if (report.items.isNotEmpty()) {
            if (report.totalsByCurrency.size <= 1) {
                // Single currency: simple SUM formula
                sb.append("""    <row r="$rowIndex" spans="1:7">
      <c r="A$rowIndex" s="1" t="inlineStr"><is><t>الإجمالي</t></is></c>
      <c r="B$rowIndex" s="1" t="inlineStr"><is><t></t></is></c>
      <c r="C$rowIndex" s="1"><f>SUM(C2:C$lastDataRow)</f></c>
      <c r="D$rowIndex" s="1" t="inlineStr"><is><t></t></is></c>
      <c r="E$rowIndex" s="1" t="inlineStr"><is><t></t></is></c>
      <c r="F$rowIndex" s="1" t="inlineStr"><is><t></t></is></c>
      <c r="G$rowIndex" s="1"><f>SUM(G2:G$lastDataRow)</f></c>
    </row>
""")
            } else {
                // Multi-currency protection: separate subtotal row per currency
                for ((curr, _) in report.totalsByCurrency) {
                    val safeCurr = escapeXml(curr)
                    sb.append("""    <row r="$rowIndex" spans="1:7">
      <c r="A$rowIndex" s="1" t="inlineStr"><is><t>إجمالي $safeCurr</t></is></c>
      <c r="B$rowIndex" s="1" t="inlineStr"><is><t></t></is></c>
      <c r="C$rowIndex" s="1"><f>SUMIF(D2:D$lastDataRow, "$safeCurr", C2:C$lastDataRow)</f></c>
      <c r="D$rowIndex" s="1" t="inlineStr"><is><t>$safeCurr</t></is></c>
      <c r="E$rowIndex" s="1" t="inlineStr"><is><t></t></is></c>
      <c r="F$rowIndex" s="1" t="inlineStr"><is><t></t></is></c>
      <c r="G$rowIndex" s="1"><f>SUMIF(D2:D$lastDataRow, "$safeCurr", G2:G$lastDataRow)</f></c>
    </row>
""")
                    rowIndex++
                }
            }
        }

        sb.append("""  </sheetData>
""")
        if (report.items.isNotEmpty()) {
            sb.append("""  <autoFilter ref="A1:G$lastDataRow"/>
""")
        }
        sb.append("""</worksheet>""")
        return sb.toString()
    }

    private fun buildSummarySheet(report: ReportData): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetViews>
    <sheetView rightToLeft="1" workbookViewId="0"/>
  </sheetViews>
  <cols>
    <col min="1" max="1" width="22" customWidth="1"/>
    <col min="2" max="2" width="16" customWidth="1"/>
    <col min="3" max="3" width="14" customWidth="1"/>
    <col min="4" max="4" width="12" customWidth="1"/>
  </cols>
  <sheetData>
    <row r="1" spans="1:4">
      <c r="A1" s="1" t="inlineStr"><is><t>تقرير</t></is></c>
      <c r="B1" t="inlineStr"><is><t>${escapeXml(report.periodTitle)}</t></is></c>
      <c r="C1" s="1" t="inlineStr"><is><t>تاريخ الإنشاء</t></is></c>
      <c r="D1" t="inlineStr"><is><t>${report.generatedDateFormatted}</t></is></c>
    </row>
    <row r="2" spans="1:4"><c r="A2"/></row>
    <row r="3" spans="1:4">
      <c r="A3" s="1" t="inlineStr"><is><t>إجمالي العملات (حصتي)</t></is></c>
      <c r="B3" s="1" t="inlineStr"><is><t>المجموع</t></is></c>
      <c r="C3" s="1" t="inlineStr"><is><t>عدد العمليات</t></is></c>
    </row>
""")
        var r = 4
        for ((currency, sum) in report.totalsByCurrency) {
            val count = report.items.count { (it.currency.ifBlank { "₪" }) == currency }
            sb.append("""    <row r="$r" spans="1:4">
      <c r="A$r" t="inlineStr"><is><t>${escapeXml(currency)}</t></is></c>
      <c r="B$r" s="2"><v>$sum</v></c>
      <c r="C$r"><v>$count</v></c>
    </row>
""")
            r++
        }

        r++
        sb.append("""    <row r="$r" spans="1:4">
      <c r="A$r" s="1" t="inlineStr"><is><t>توزيع التصنيفات (حصتي)</t></is></c>
      <c r="B$r" s="1" t="inlineStr"><is><t>المبلغ</t></is></c>
      <c r="C$r" s="1" t="inlineStr"><is><t>النسبة</t></is></c>
      <c r="D$r" s="1" t="inlineStr"><is><t>العمليات</t></is></c>
    </row>
""")
        r++

        for ((currency, summaries) in report.categoryBreakdownByCurrency) {
            for (catSum in summaries) {
                val pctStr = "${(catSum.percentage * 100).toInt()}%"
                sb.append("""    <row r="$r" spans="1:4">
      <c r="A$r" t="inlineStr"><is><t>${escapeXml(catSum.category.arabicTitle)} ($currency)</t></is></c>
      <c r="B$r" s="2"><v>${catSum.totalAmount}</v></c>
      <c r="C$r" t="inlineStr"><is><t>$pctStr</t></is></c>
      <c r="D$r"><v>${catSum.count}</v></c>
    </row>
""")
                r++
            }
        }

        sb.append("""  </sheetData>
</worksheet>""")
        return sb.toString()
    }

    /**
     * Generates a 5-sheet accountant-grade Receivables and Debts Workbook.
     */
    fun writeReceivablesWorkbook(
        expenses: List<ExpenseItem>,
        nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): ByteArray {
        val zip = PureZipBuilder()

        // 1. [Content_Types].xml
        zip.addEntry(
            "[Content_Types].xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet3.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet4.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet5.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""
        )

        // 2. _rels/.rels
        zip.addEntry(
            "_rels/.rels",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
        )

        // 3. xl/workbook.xml
        zip.addEntry(
            "xl/workbook.xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="ملخص الأطراف" sheetId="1" r:id="rId1"/>
    <sheet name="سجل التخصيصات" sheetId="2" r:id="rId2"/>
    <sheet name="سجل التسديدات" sheetId="3" r:id="rId3"/>
    <sheet name="أعمار الديون" sheetId="4" r:id="rId4"/>
    <sheet name="تقرير المطابقة" sheetId="5" r:id="rId5"/>
  </sheets>
</workbook>"""
        )

        // 4. xl/_rels/workbook.xml.rels
        zip.addEntry(
            "xl/_rels/workbook.xml.rels",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/>
  <Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet4.xml"/>
  <Relationship Id="rId5" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet5.xml"/>
  <Relationship Id="rId6" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""
        )

        // 5. xl/styles.xml
        zip.addEntry("xl/styles.xml", buildStylesXml())

        val partySummaries = ReceivablesLedger.getPartySummaries(expenses)
        val allAllocations = ReceivablesLedger.getAllocationComputations(expenses)

        // Sheet 1: ملخص الأطراف
        zip.addEntry("xl/worksheets/sheet1.xml", buildPartySummariesSheet(partySummaries))

        // Sheet 2: سجل التخصيصات
        zip.addEntry("xl/worksheets/sheet2.xml", buildDetailedAllocationsSheet(allAllocations, timeZone))

        // Sheet 3: سجل التسديدات
        zip.addEntry("xl/worksheets/sheet3.xml", buildSettlementsSheet(allAllocations, timeZone))

        // Sheet 4: أعمار الديون
        zip.addEntry("xl/worksheets/sheet4.xml", buildAgingSheet(allAllocations, nowMillis))

        // Sheet 5: تقرير المطابقة والتدقيق
        val integrity = LedgerIntegrityChecker.verify(expenses, nowMillis)
        zip.addEntry("xl/worksheets/sheet5.xml", buildAuditReportSheet(integrity, expenses.size, nowMillis, timeZone))

        return zip.build()
    }

    private fun buildPartySummariesSheet(summaries: List<org.mohtech.app.data.accounting.PartyBalanceSummary>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetViews><sheetView rightToLeft="1" tabSelected="1" workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>
  <cols>
    <col min="1" max="1" width="20" customWidth="1"/>
    <col min="2" max="2" width="10" customWidth="1"/>
    <col min="3" max="3" width="14" customWidth="1"/>
    <col min="4" max="4" width="14" customWidth="1"/>
    <col min="5" max="5" width="14" customWidth="1"/>
    <col min="6" max="6" width="14" customWidth="1"/>
    <col min="7" max="7" width="14" customWidth="1"/>
    <col min="8" max="8" width="14" customWidth="1"/>
    <col min="9" max="9" width="14" customWidth="1"/>
  </cols>
  <sheetData>
    <row r="1" spans="1:9">
      <c r="A1" s="1" t="inlineStr"><is><t>الطرف</t></is></c>
      <c r="B1" s="1" t="inlineStr"><is><t>العملة</t></is></c>
      <c r="C1" s="1" t="inlineStr"><is><t>مستحق لي</t></is></c>
      <c r="D1" s="1" t="inlineStr"><is><t>مسدد لي</t></is></c>
      <c r="E1" s="1" t="inlineStr"><is><t>متبقي لي</t></is></c>
      <c r="F1" s="1" t="inlineStr"><is><t>مطالب به مني</t></is></c>
      <c r="G1" s="1" t="inlineStr"><is><t>مسدد مني</t></is></c>
      <c r="H1" s="1" t="inlineStr"><is><t>متبقي علي</t></is></c>
      <c r="I1" s="1" t="inlineStr"><is><t>الصافي</t></is></c>
    </row>
""")
        var r = 2
        for (s in summaries) {
            sb.append("""    <row r="$r" spans="1:9">
      <c r="A$r" t="inlineStr"><is><t>${escapeXml(s.party.name)}</t></is></c>
      <c r="B$r" t="inlineStr"><is><t>${escapeXml(s.currency)}</t></is></c>
      <c r="C$r" s="2"><v>${s.totalReceivable}</v></c>
      <c r="D$r" s="2"><v>${s.totalSettledToMe}</v></c>
      <c r="E$r" s="2"><v>${s.totalRemainingToMe}</v></c>
      <c r="F$r" s="2"><v>${s.totalPayable}</v></c>
      <c r="G$r" s="2"><v>${s.totalSettledByMe}</v></c>
      <c r="H$r" s="2"><v>${s.totalRemainingByMe}</v></c>
      <c r="I$r" s="2"><v>${s.netBalance}</v></c>
    </row>
""")
            r++
        }
        val lastRow = r - 1
        sb.append("""  </sheetData>
""")
        if (summaries.isNotEmpty()) {
            sb.append("""  <autoFilter ref="A1:I$lastRow"/>
""")
        }
        sb.append("""</worksheet>""")
        return sb.toString()
    }

    private fun buildDetailedAllocationsSheet(
        allocations: List<org.mohtech.app.data.accounting.AllocationComputation>,
        timeZone: TimeZone
    ): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetViews><sheetView rightToLeft="1" workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>
  <cols>
    <col min="1" max="1" width="12" customWidth="1"/>
    <col min="2" max="2" width="14" customWidth="1"/>
    <col min="3" max="3" width="24" customWidth="1"/>
    <col min="4" max="4" width="18" customWidth="1"/>
    <col min="5" max="5" width="10" customWidth="1"/>
    <col min="6" max="6" width="14" customWidth="1"/>
    <col min="7" max="7" width="14" customWidth="1"/>
    <col min="8" max="8" width="14" customWidth="1"/>
    <col min="9" max="9" width="14" customWidth="1"/>
    <col min="10" max="10" width="24" customWidth="1"/>
  </cols>
  <sheetData>
    <row r="1" spans="1:10">
      <c r="A1" s="1" t="inlineStr"><is><t>رقم السند</t></is></c>
      <c r="B1" s="1" t="inlineStr"><is><t>التاريخ</t></is></c>
      <c r="C1" s="1" t="inlineStr"><is><t>المصروف</t></is></c>
      <c r="D1" s="1" t="inlineStr"><is><t>الطرف</t></is></c>
      <c r="E1" s="1" t="inlineStr"><is><t>العملة</t></is></c>
      <c r="F1" s="1" t="inlineStr"><is><t>المبلغ</t></is></c>
      <c r="G1" s="1" t="inlineStr"><is><t>المسدد</t></is></c>
      <c r="H1" s="1" t="inlineStr"><is><t>المتبقي</t></is></c>
      <c r="I1" s="1" t="inlineStr"><is><t>الحالة</t></is></c>
      <c r="J1" s="1" t="inlineStr"><is><t>ملاحظات</t></is></c>
    </row>
""")
        var r = 2
        for (a in allocations) {
            val date = Instant.fromEpochMilliseconds(a.expenseTimestamp).toLocalDateTime(timeZone).date
            val serialDate = date.toEpochDays() + 25569
            val statusStr = when (a.status) {
                DebtStatus.OPEN -> "قيد التحصيل"
                DebtStatus.PARTIAL -> "مسدد جزئياً"
                DebtStatus.SETTLED -> "مسدد بالكامل"
            }
            sb.append("""    <row r="$r" spans="1:10">
      <c r="A$r"><v>${a.refNo}</v></c>
      <c r="B$r" s="3"><v>$serialDate</v></c>
      <c r="C$r" t="inlineStr"><is><t>${escapeXml(a.expenseTitle)}</t></is></c>
      <c r="D$r" t="inlineStr"><is><t>${escapeXml(a.party.name)}</t></is></c>
      <c r="E$r" t="inlineStr"><is><t>${escapeXml(a.currency)}</t></is></c>
      <c r="F$r" s="2"><v>${a.totalAmount}</v></c>
      <c r="G$r" s="2"><v>${a.settledAmount}</v></c>
      <c r="H$r" s="2"><v>${a.remainingAmount}</v></c>
      <c r="I$r" t="inlineStr"><is><t>$statusStr</t></is></c>
      <c r="J$r" t="inlineStr"><is><t>${escapeXml(a.note)}</t></is></c>
    </row>
""")
            r++
        }
        val lastRow = r - 1
        sb.append("""  </sheetData>
""")
        if (allocations.isNotEmpty()) {
            sb.append("""  <autoFilter ref="A1:J$lastRow"/>
""")
        }
        sb.append("""</worksheet>""")
        return sb.toString()
    }

    private fun buildSettlementsSheet(
        allocations: List<org.mohtech.app.data.accounting.AllocationComputation>,
        timeZone: TimeZone
    ): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetViews><sheetView rightToLeft="1" workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>
  <cols>
    <col min="1" max="1" width="12" customWidth="1"/>
    <col min="2" max="2" width="14" customWidth="1"/>
    <col min="3" max="3" width="18" customWidth="1"/>
    <col min="4" max="4" width="10" customWidth="1"/>
    <col min="5" max="5" width="14" customWidth="1"/>
    <col min="6" max="6" width="14" customWidth="1"/>
    <col min="7" max="7" width="12" customWidth="1"/>
    <col min="8" max="8" width="24" customWidth="1"/>
  </cols>
  <sheetData>
    <row r="1" spans="1:8">
      <c r="A1" s="1" t="inlineStr"><is><t>رقم الإيصال</t></is></c>
      <c r="B1" s="1" t="inlineStr"><is><t>تاريخ السداد</t></is></c>
      <c r="C1" s="1" t="inlineStr"><is><t>الطرف</t></is></c>
      <c r="D1" s="1" t="inlineStr"><is><t>العملة</t></is></c>
      <c r="E1" s="1" t="inlineStr"><is><t>المبلغ</t></is></c>
      <c r="F1" s="1" t="inlineStr"><is><t>وقت التسجيل</t></is></c>
      <c r="G1" s="1" t="inlineStr"><is><t>الحالة</t></is></c>
      <c r="H1" s="1" t="inlineStr"><is><t>ملاحظات</t></is></c>
    </row>
""")
        var r = 2
        for (a in allocations) {
            for (s in a.settlements) {
                val date = Instant.fromEpochMilliseconds(s.settledAt).toLocalDateTime(timeZone).date
                val serialDate = date.toEpochDays() + 25569
                val recDate = Instant.fromEpochMilliseconds(s.recordedAt).toLocalDateTime(timeZone).date
                val recSerial = recDate.toEpochDays() + 25569
                val statusStr = if (s.voidedAt == null) "معتمد" else "ملغى (Voided)"
                sb.append("""    <row r="$r" spans="1:8">
      <c r="A$r"><v>${s.refNo}</v></c>
      <c r="B$r" s="3"><v>$serialDate</v></c>
      <c r="C$r" t="inlineStr"><is><t>${escapeXml(a.party.name)}</t></is></c>
      <c r="D$r" t="inlineStr"><is><t>${escapeXml(a.currency)}</t></is></c>
      <c r="E$r" s="2"><v>${s.amount}</v></c>
      <c r="F$r" s="3"><v>$recSerial</v></c>
      <c r="G$r" t="inlineStr"><is><t>$statusStr</t></is></c>
      <c r="H$r" t="inlineStr"><is><t>${escapeXml(s.note)}</t></is></c>
    </row>
""")
                r++
            }
        }
        val lastRow = r - 1
        sb.append("""  </sheetData>
""")
        if (r > 2) {
            sb.append("""  <autoFilter ref="A1:H$lastRow"/>
""")
        }
        sb.append("""</worksheet>""")
        return sb.toString()
    }

    private fun buildAgingSheet(
        allocations: List<org.mohtech.app.data.accounting.AllocationComputation>,
        nowMillis: Long
    ): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetViews><sheetView rightToLeft="1" workbookViewId="0"/></sheetViews>
  <cols>
    <col min="1" max="1" width="20" customWidth="1"/>
    <col min="2" max="2" width="10" customWidth="1"/>
    <col min="3" max="3" width="14" customWidth="1"/>
    <col min="4" max="4" width="14" customWidth="1"/>
    <col min="5" max="5" width="14" customWidth="1"/>
    <col min="6" max="6" width="14" customWidth="1"/>
    <col min="7" max="7" width="14" customWidth="1"/>
  </cols>
  <sheetData>
    <row r="1" spans="1:7">
      <c r="A1" s="1" t="inlineStr"><is><t>الطرف</t></is></c>
      <c r="B1" s="1" t="inlineStr"><is><t>العملة</t></is></c>
      <c r="C1" s="1" t="inlineStr"><is><t>0-7 أيام</t></is></c>
      <c r="D1" s="1" t="inlineStr"><is><t>8-30 يوماً</t></is></c>
      <c r="E1" s="1" t="inlineStr"><is><t>31-60 يوماً</t></is></c>
      <c r="F1" s="1" t="inlineStr"><is><t>أكثر من 60 يوماً</t></is></c>
      <c r="G1" s="1" t="inlineStr"><is><t>المجموع المتبقي</t></is></c>
    </row>
""")
        val openReceivables = allocations.filter { it.isOwedToMe && it.remainingAmount > 0.0 }
        val grouped = openReceivables.groupBy { Pair(it.party.name, it.currency) }

        var r = 2
        for ((key, items) in grouped) {
            val partyName = key.first
            val currency = key.second

            var b0_7 = 0.0
            var b8_30 = 0.0
            var b31_60 = 0.0
            var b60plus = 0.0

            for (item in items) {
                val ageDays = ((nowMillis - item.expenseTimestamp) / (24L * 60L * 60L * 1000L)).toInt().coerceAtLeast(0)
                when {
                    ageDays <= 7 -> b0_7 += item.remainingAmount
                    ageDays <= 30 -> b8_30 += item.remainingAmount
                    ageDays <= 60 -> b31_60 += item.remainingAmount
                    else -> b60plus += item.remainingAmount
                }
            }
            val totalRemaining = b0_7 + b8_30 + b31_60 + b60plus

            sb.append("""    <row r="$r" spans="1:7">
      <c r="A$r" t="inlineStr"><is><t>${escapeXml(partyName)}</t></is></c>
      <c r="B$r" t="inlineStr"><is><t>${escapeXml(currency)}</t></is></c>
      <c r="C$r" s="2"><v>$b0_7</v></c>
      <c r="D$r" s="2"><v>$b8_30</v></c>
      <c r="E$r" s="2"><v>$b31_60</v></c>
      <c r="F$r" s="2"><v>$b60plus</v></c>
      <c r="G$r" s="2"><v>$totalRemaining</v></c>
    </row>
""")
            r++
        }
        sb.append("""  </sheetData>
</worksheet>""")
        return sb.toString()
    }

    private fun buildAuditReportSheet(
        integrity: org.mohtech.app.data.accounting.IntegrityCheckResult,
        totalExpenses: Int,
        nowMillis: Long,
        timeZone: TimeZone
    ): String {
        val date = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(timeZone)
        val formattedDate = "${date.year}-${date.monthNumber.toString().padStart(2, '0')}-${date.dayOfMonth.toString().padStart(2, '0')} ${date.hour.toString().padStart(2, '0')}:${date.minute.toString().padStart(2, '0')}"
        val statusText = if (integrity.isValid) "سليمة ومطابقة 100%" else "يوجد تباين رياضي"

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetViews><sheetView rightToLeft="1" workbookViewId="0"/></sheetViews>
  <cols>
    <col min="1" max="1" width="28" customWidth="1"/>
    <col min="2" max="2" width="28" customWidth="1"/>
    <col min="3" max="3" width="36" customWidth="1"/>
  </cols>
  <sheetData>
    <row r="1" spans="1:3">
      <c r="A1" s="1" t="inlineStr"><is><t>تقرير المطابقة والتدقيق المالي</t></is></c>
      <c r="B1" t="inlineStr"><is><t>تاريخ التدقيق: $formattedDate</t></is></c>
    </row>
    <row r="2" spans="1:3">
      <c r="A2" s="1" t="inlineStr"><is><t>إجمالي القيود المفحوصة</t></is></c>
      <c r="B2"><v>$totalExpenses</v></c>
    </row>
    <row r="3" spans="1:3">
      <c r="A3" s="1" t="inlineStr"><is><t>حالة التدقيق والمطابقة</t></is></c>
      <c r="B3" s="1" t="inlineStr"><is><t>$statusText</t></is></c>
    </row>
    <row r="4" spans="1:3"><c r="A4"/></row>
    <row r="5" spans="1:3">
      <c r="A5" s="1" t="inlineStr"><is><t>فحص الأخطاء الرياضية</t></is></c>
      <c r="B5" t="inlineStr"><is><t>${if (integrity.errors.isEmpty()) "صفر أخطاء" else "${integrity.errors.size} خطأ"}</t></is></c>
    </row>
""")
        var r = 6
        for (err in integrity.errors) {
            sb.append("""    <row r="$r" spans="1:3">
      <c r="A$r" t="inlineStr"><is><t>مخالفة مطابقة</t></is></c>
      <c r="B$r" t="inlineStr"><is><t>${escapeXml(err)}</t></is></c>
    </row>
""")
            r++
        }
        sb.append("""  </sheetData>
</worksheet>""")
        return sb.toString()
    }
}
