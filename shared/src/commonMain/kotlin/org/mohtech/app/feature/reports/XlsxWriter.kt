package org.mohtech.app.feature.reports

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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
 * - Formatted header rows with freeze panes
 * - Dynamic formulas (=SUM)
 * - Two structured worksheets: "المصروفات" (Expenses) and "الملخص" (Summary)
 */
object XlsxWriter {

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
        zip.addEntry(
            "xl/styles.xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
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
  <cellXfs count="3">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
    <xf numFmtId="2" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
  </cellXfs>
</styleSheet>"""
        )

        // 6. xl/worksheets/sheet1.xml (المصروفات)
        zip.addEntry("xl/worksheets/sheet1.xml", buildExpensesSheet(report))

        // 7. xl/worksheets/sheet2.xml (الملخص)
        zip.addEntry("xl/worksheets/sheet2.xml", buildSummarySheet(report))

        return zip.build()
    }

    private fun escapeXml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
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
      <c r="G1" s="1" t="inlineStr"><is><t>الحصة</t></is></c>
    </row>
""")

        val timeZone = TimeZone.currentSystemDefault()
        var rowIndex = 2

        for (item in report.items) {
            val date = Instant.fromEpochMilliseconds(item.timestamp).toLocalDateTime(timeZone).date
            val dateStr = "${date.year}-${date.monthNumber.toString().padStart(2, '0')}-${date.dayOfMonth.toString().padStart(2, '0')}"
            val title = escapeXml(item.title)
            val curr = escapeXml(item.currency.ifBlank { "₪" })
            val cat = escapeXml(item.category.arabicTitle)
            val payer = escapeXml(item.payer.name)
            val share = if (item.split != null) "${item.split.amountPerPerson}" else "${item.amount}"

            sb.append("""    <row r="$rowIndex" spans="1:7">
      <c r="A$rowIndex" t="inlineStr"><is><t>$dateStr</t></is></c>
      <c r="B$rowIndex" t="inlineStr"><is><t>$title</t></is></c>
      <c r="C$rowIndex" s="2"><v>${item.amount}</v></c>
      <c r="D$rowIndex" t="inlineStr"><is><t>$curr</t></is></c>
      <c r="E$rowIndex" t="inlineStr"><is><t>$cat</t></is></c>
      <c r="F$rowIndex" t="inlineStr"><is><t>$payer</t></is></c>
      <c r="G$rowIndex" s="2"><v>$share</v></c>
    </row>
""")
            rowIndex++
        }

        // Summary row with =SUM if items exist
        if (report.items.isNotEmpty()) {
            val lastDataRow = rowIndex - 1
            sb.append("""    <row r="$rowIndex" spans="1:7">
      <c r="A$rowIndex" s="1" t="inlineStr"><is><t>الإجمالي</t></is></c>
      <c r="B$rowIndex" s="1" t="inlineStr"><is><t></t></is></c>
      <c r="C$rowIndex" s="1"><f>SUM(C2:C$lastDataRow)</f></c>
      <c r="D$rowIndex" s="1" t="inlineStr"><is><t></t></is></c>
      <c r="E$rowIndex" s="1" t="inlineStr"><is><t></t></is></c>
      <c r="F$rowIndex" s="1" t="inlineStr"><is><t></t></is></c>
      <c r="G$rowIndex" s="1" t="inlineStr"><is><t></t></is></c>
    </row>
""")
        }

        sb.append("""  </sheetData>
</worksheet>""")
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
    <col min="3" max="3" width="12" customWidth="1"/>
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
      <c r="A3" s="1" t="inlineStr"><is><t>إجمالي العملات</t></is></c>
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
      <c r="A$r" s="1" t="inlineStr"><is><t>توزيع التصنيفات</t></is></c>
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
     * Generates comprehensive 5-sheet Accounting Receivables Workbook:
     * 1. الملخص (Summary per currency & party, with LedgerIntegrityChecker audit footer)
     * 2. المستحقات (Detailed allocations with ref_no, date, day name, party, amount, settled, remaining, status, last settled)
     * 3. التسديدات (Settlements log with ref_no, date, day name, recordedAt, party, amount, expense title, voided info)
     * 4. كشوف الحساب (Statement of account with Person column and running balance)
     * 5. الأعمار (Aging: 0-7, 8-30, 31-60, 60+ days)
     */
    fun writeReceivablesWorkbook(
        expenses: List<ExpenseItem>,
        nowMillis: Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
    ): ByteArray {
        val zip = PureZipBuilder()
        val timeZone = TimeZone.currentSystemDefault()

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
    <sheet name="الملخص" sheetId="1" r:id="rId1"/>
    <sheet name="المستحقات" sheetId="2" r:id="rId2"/>
    <sheet name="التسديدات" sheetId="3" r:id="rId3"/>
    <sheet name="كشوف الحساب" sheetId="4" r:id="rId4"/>
    <sheet name="الأعمار" sheetId="5" r:id="rId5"/>
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
        zip.addEntry(
            "xl/styles.xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="3">
    <font><name val="Cairo"/><sz val="11"/><color rgb="FF0E131F"/></font>
    <font><b/><name val="Cairo"/><sz val="11"/><color rgb="FF0E131F"/></font>
    <font><name val="Cairo"/><sz val="10"/><color rgb="FF94A3B8"/></font>
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
    <xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/>
  </cellXfs>
</styleSheet>"""
        )

        // 6. Worksheets
        zip.addEntry("xl/worksheets/sheet1.xml", buildReceivablesSummarySheet(expenses, nowMillis, timeZone))
        zip.addEntry("xl/worksheets/sheet2.xml", buildReceivablesDetailedSheet(expenses, timeZone))
        zip.addEntry("xl/worksheets/sheet3.xml", buildSettlementsSheet(expenses, timeZone))
        zip.addEntry("xl/worksheets/sheet4.xml", buildStatementsSheet(expenses, timeZone))
        zip.addEntry("xl/worksheets/sheet5.xml", buildAgingSheet(expenses, nowMillis))

        return zip.build()
    }

    private fun buildReceivablesSummarySheet(
        expenses: List<ExpenseItem>,
        nowMillis: Long,
        timeZone: TimeZone
    ): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetViews>
    <sheetView rightToLeft="1" tabSelected="1" workbookViewId="0">
      <pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>
    </sheetView>
  </sheetViews>
  <cols>
    <col min="1" max="1" width="22" customWidth="1"/>
    <col min="2" max="2" width="12" customWidth="1"/>
    <col min="3" max="3" width="16" customWidth="1"/>
    <col min="4" max="4" width="16" customWidth="1"/>
    <col min="5" max="5" width="16" customWidth="1"/>
    <col min="6" max="6" width="16" customWidth="1"/>
  </cols>
  <sheetData>
    <row r="1" spans="1:6">
      <c r="A1" s="1" t="inlineStr"><is><t>الطرف</t></is></c>
      <c r="B1" s="1" t="inlineStr"><is><t>العملة</t></is></c>
      <c r="C1" s="1" t="inlineStr"><is><t>المستحق لي</t></is></c>
      <c r="D1" s="1" t="inlineStr"><is><t>المسدد لي</t></is></c>
      <c r="E1" s="1" t="inlineStr"><is><t>المتبقي لي</t></is></c>
      <c r="F1" s="1" t="inlineStr"><is><t>الرصيد الصافي</t></is></c>
    </row>
""")

        val summaries = org.mohtech.app.data.accounting.ReceivablesLedger.getPartySummaries(expenses)
        var rowIdx = 2
        for (sum in summaries) {
            val partyName = escapeXml(sum.party.name)
            val curr = escapeXml(sum.currency)
            sb.append("""    <row r="$rowIdx" spans="1:6">
      <c r="A$rowIdx" t="inlineStr"><is><t>$partyName</t></is></c>
      <c r="B$rowIdx" t="inlineStr"><is><t>$curr</t></is></c>
      <c r="C$rowIdx" s="2"><v>${sum.totalReceivable}</v></c>
      <c r="D$rowIdx" s="2"><v>${sum.totalSettledToMe}</v></c>
      <c r="E$rowIdx" s="2"><v>${sum.totalRemainingToMe}</v></c>
      <c r="F$rowIdx" s="2"><v>${sum.netBalance}</v></c>
    </row>
""")
            rowIdx++
        }

        // Integrity audit footer
        rowIdx++
        val audit = org.mohtech.app.data.accounting.LedgerIntegrityChecker.verify(expenses, nowMillis)
        val auditText = if (audit.isValid) "تمت المطابقة المحاسبية بنجاح (100% تطابق)" else "تنبيه: يوجد فروقات في المطابقة"
        sb.append("""    <row r="$rowIdx" spans="1:6">
      <c r="A$rowIdx" s="1" t="inlineStr"><is><t>فحص المطابقة</t></is></c>
      <c r="B$rowIdx" s="1" t="inlineStr"><is><t>$auditText</t></is></c>
    </row>
""")

        sb.append("""  </sheetData>
</worksheet>""")
        return sb.toString()
    }

    private fun buildReceivablesDetailedSheet(
        expenses: List<ExpenseItem>,
        timeZone: TimeZone
    ): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetViews>
    <sheetView rightToLeft="1" workbookViewId="0">
      <pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>
    </sheetView>
  </sheetViews>
  <cols>
    <col min="1" max="1" width="14" customWidth="1"/>
    <col min="2" max="2" width="22" customWidth="1"/>
    <col min="3" max="3" width="24" customWidth="1"/>
    <col min="4" max="4" width="18" customWidth="1"/>
    <col min="5" max="5" width="14" customWidth="1"/>
    <col min="6" max="6" width="14" customWidth="1"/>
    <col min="7" max="7" width="14" customWidth="1"/>
    <col min="8" max="8" width="16" customWidth="1"/>
    <col min="9" max="9" width="22" customWidth="1"/>
    <col min="10" max="10" width="24" customWidth="1"/>
  </cols>
  <sheetData>
    <row r="1" spans="1:10">
      <c r="A1" s="1" t="inlineStr"><is><t>الرقم المرجعي</t></is></c>
      <c r="B1" s="1" t="inlineStr"><is><t>التاريخ واليوم</t></is></c>
      <c r="C1" s="1" t="inlineStr"><is><t>البند</t></is></c>
      <c r="D1" s="1" t="inlineStr"><is><t>الطرف</t></is></c>
      <c r="E1" s="1" t="inlineStr"><is><t>المستحق</t></is></c>
      <c r="F1" s="1" t="inlineStr"><is><t>المسدد</t></is></c>
      <c r="G1" s="1" t="inlineStr"><is><t>المتبقي</t></is></c>
      <c r="H1" s="1" t="inlineStr"><is><t>الحالة</t></is></c>
      <c r="I1" s="1" t="inlineStr"><is><t>آخر سداد</t></is></c>
      <c r="J1" s="1" t="inlineStr"><is><t>ملاحظات</t></is></c>
    </row>
""")

        val computations = org.mohtech.app.data.accounting.ReceivablesLedger.getAllocationComputations(expenses)
        var rowIdx = 2
        for (c in computations) {
            val refStr = "#${c.refNo.toString().padStart(6, '0')}"
            val dateWithDay = AccountingReportUtil.formatDateWithArabicDay(c.expenseTimestamp, timeZone)
            val title = escapeXml(c.expenseTitle)
            val party = escapeXml(c.party.name)
            val statusText = when (c.status) {
                org.mohtech.app.data.model.DebtStatus.OPEN -> "قيد التحصيل"
                org.mohtech.app.data.model.DebtStatus.PARTIAL -> "مسدد جزئياً"
                org.mohtech.app.data.model.DebtStatus.SETTLED -> "مسدد بالكامل"
            }
            val lastSettleStr = if (c.lastSettledAt != null) {
                AccountingReportUtil.formatDateWithArabicDay(c.lastSettledAt, timeZone)
            } else "-"
            val note = escapeXml(c.note)

            sb.append("""    <row r="$rowIdx" spans="1:10">
      <c r="A$rowIdx" t="inlineStr"><is><t>$refStr</t></is></c>
      <c r="B$rowIdx" t="inlineStr"><is><t>$dateWithDay</t></is></c>
      <c r="C$rowIdx" t="inlineStr"><is><t>$title</t></is></c>
      <c r="D$rowIdx" t="inlineStr"><is><t>$party</t></is></c>
      <c r="E$rowIdx" s="2"><v>${c.totalAmount}</v></c>
      <c r="F$rowIdx" s="2"><v>${c.settledAmount}</v></c>
      <c r="G$rowIdx" s="2"><v>${c.remainingAmount}</v></c>
      <c r="H$rowIdx" t="inlineStr"><is><t>$statusText</t></is></c>
      <c r="I$rowIdx" t="inlineStr"><is><t>$lastSettleStr</t></is></c>
      <c r="J$rowIdx" t="inlineStr"><is><t>$note</t></is></c>
    </row>
""")
            rowIdx++
        }

        // Summary row
        if (computations.isNotEmpty()) {
            val lastData = rowIdx - 1
            sb.append("""    <row r="$rowIdx" spans="1:10">
      <c r="A$rowIdx" s="1" t="inlineStr"><is><t>المجموع</t></is></c>
      <c r="B$rowIdx" s="1"/><c r="C$rowIdx" s="1"/><c r="D$rowIdx" s="1"/>
      <c r="E$rowIdx" s="1"><f>SUM(E2:E$lastData)</f></c>
      <c r="F$rowIdx" s="1"><f>SUM(F2:F$lastData)</f></c>
      <c r="G$rowIdx" s="1"><f>SUM(G2:G$lastData)</f></c>
      <c r="H$rowIdx" s="1"/><c r="I$rowIdx" s="1"/><c r="J$rowIdx" s="1"/>
    </row>
""")
        }

        sb.append("""  </sheetData>
  <autoFilter ref="A1:J${if (computations.isEmpty()) 1 else rowIdx - 1}"/>
</worksheet>""")
        return sb.toString()
    }

    private fun buildSettlementsSheet(
        expenses: List<ExpenseItem>,
        timeZone: TimeZone
    ): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetViews>
    <sheetView rightToLeft="1" workbookViewId="0">
      <pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>
    </sheetView>
  </sheetViews>
  <cols>
    <col min="1" max="1" width="14" customWidth="1"/>
    <col min="2" max="2" width="22" customWidth="1"/>
    <col min="3" max="3" width="20" customWidth="1"/>
    <col min="4" max="4" width="18" customWidth="1"/>
    <col min="5" max="5" width="14" customWidth="1"/>
    <col min="6" max="6" width="24" customWidth="1"/>
    <col min="7" max="7" width="16" customWidth="1"/>
    <col min="8" max="8" width="24" customWidth="1"/>
  </cols>
  <sheetData>
    <row r="1" spans="1:8">
      <c r="A1" s="1" t="inlineStr"><is><t>الرقم المرجعي</t></is></c>
      <c r="B1" s="1" t="inlineStr"><is><t>تاريخ السداد واليوم</t></is></c>
      <c r="C1" s="1" t="inlineStr"><is><t>وقت التسجيل</t></is></c>
      <c r="D1" s="1" t="inlineStr"><is><t>الطرف</t></is></c>
      <c r="E1" s="1" t="inlineStr"><is><t>المبلغ</t></is></c>
      <c r="F1" s="1" t="inlineStr"><is><t>المصروف المرتبط</t></is></c>
      <c r="G1" s="1" t="inlineStr"><is><t>الحالة</t></is></c>
      <c r="H1" s="1" t="inlineStr"><is><t>ملاحظات</t></is></c>
    </row>
""")

        var rowIdx = 2
        var activeSettlementsCount = 0
        for (expense in expenses) {
            val split = expense.split ?: continue
            for (alloc in split.allocations) {
                for (s in alloc.settlements) {
                    val refStr = "#${s.refNo.toString().padStart(6, '0')}"
                    val dateWithDay = AccountingReportUtil.formatDateWithArabicDay(s.settledAt, timeZone)
                    val recStr = AccountingReportUtil.formatDateWithArabicDay(s.recordedAt, timeZone)
                    val party = escapeXml(alloc.party.name)
                    val expTitle = escapeXml(expense.title)
                    val statusText = if (s.voidedAt != null) {
                        val voidDate = AccountingReportUtil.formatDateWithArabicDay(s.voidedAt, timeZone)
                        "ملغى في $voidDate"
                    } else {
                        activeSettlementsCount++
                        "ساري"
                    }
                    val note = escapeXml(s.note)

                    sb.append("""    <row r="$rowIdx" spans="1:8">
      <c r="A$rowIdx" t="inlineStr"><is><t>$refStr</t></is></c>
      <c r="B$rowIdx" t="inlineStr"><is><t>$dateWithDay</t></is></c>
      <c r="C$rowIdx" t="inlineStr"><is><t>$recStr</t></is></c>
      <c r="D$rowIdx" t="inlineStr"><is><t>$party</t></is></c>
      <c r="E$rowIdx" s="2"><v>${s.amount}</v></c>
      <c r="F$rowIdx" t="inlineStr"><is><t>$expTitle</t></is></c>
      <c r="G$rowIdx" t="inlineStr"><is><t>$statusText</t></is></c>
      <c r="H$rowIdx" t="inlineStr"><is><t>$note</t></is></c>
    </row>
""")
                    rowIdx++
                }
            }
        }

        sb.append("""  </sheetData>
  <autoFilter ref="A1:H${if (rowIdx == 2) 1 else rowIdx - 1}"/>
</worksheet>""")
        return sb.toString()
    }

    private fun buildStatementsSheet(
        expenses: List<ExpenseItem>,
        timeZone: TimeZone
    ): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetViews>
    <sheetView rightToLeft="1" workbookViewId="0">
      <pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>
    </sheetView>
  </sheetViews>
  <cols>
    <col min="1" max="1" width="18" customWidth="1"/>
    <col min="2" max="2" width="14" customWidth="1"/>
    <col min="3" max="3" width="22" customWidth="1"/>
    <col min="4" max="4" width="26" customWidth="1"/>
    <col min="5" max="5" width="14" customWidth="1"/>
    <col min="6" max="6" width="14" customWidth="1"/>
    <col min="7" max="7" width="16" customWidth="1"/>
    <col min="8" max="8" width="16" customWidth="1"/>
    <col min="9" max="9" width="24" customWidth="1"/>
  </cols>
  <sheetData>
    <row r="1" spans="1:9">
      <c r="A1" s="1" t="inlineStr"><is><t>الطرف</t></is></c>
      <c r="B1" s="1" t="inlineStr"><is><t>الرقم المرجعي</t></is></c>
      <c r="C1" s="1" t="inlineStr"><is><t>التاريخ واليوم</t></is></c>
      <c r="D1" s="1" t="inlineStr"><is><t>البيان</t></is></c>
      <c r="E1" s="1" t="inlineStr"><is><t>مدين</t></is></c>
      <c r="F1" s="1" t="inlineStr"><is><t>دائن</t></is></c>
      <c r="G1" s="1" t="inlineStr"><is><t>الرصيد الجاري</t></is></c>
      <c r="H1" s="1" t="inlineStr"><is><t>الحالة</t></is></c>
      <c r="I1" s="1" t="inlineStr"><is><t>ملاحظات</t></is></c>
    </row>
""")

        val summaries = org.mohtech.app.data.accounting.ReceivablesLedger.getPartySummaries(expenses)
        var rowIdx = 2

        for (sum in summaries) {
            val statement = AccountingReportUtil.buildStatementOfAccount(
                partyName = sum.party.name,
                currency = sum.currency,
                expenses = expenses,
                timeZone = timeZone
            )

            for (m in statement.movements) {
                val party = escapeXml(statement.partyName)
                val refStr = "#${m.refNo.toString().padStart(6, '0')}"
                val dateWithDay = "${m.dayName} ${m.dateFormatted}"
                val desc = escapeXml(m.description)
                val debitVal = if (m.type == StatementMovementType.DEBIT) "${m.amount}" else ""
                val creditVal = if (m.type == StatementMovementType.CREDIT || m.type == StatementMovementType.VOIDED_CREDIT) "${m.amount}" else ""
                val balanceVal = "${m.runningBalance}"
                val statusText = if (m.isVoided) "ملغى (خارج الرصيد)" else "ساري"
                val note = escapeXml(m.note)

                sb.append("""    <row r="$rowIdx" spans="1:9">
      <c r="A$rowIdx" t="inlineStr"><is><t>$party</t></is></c>
      <c r="B$rowIdx" t="inlineStr"><is><t>$refStr</t></is></c>
      <c r="C$rowIdx" t="inlineStr"><is><t>$dateWithDay</t></is></c>
      <c r="D$rowIdx" t="inlineStr"><is><t>$desc</t></is></c>
      <c r="E$rowIdx" s="2">${if (debitVal.isNotBlank()) "<v>$debitVal</v>" else ""}</c>
      <c r="F$rowIdx" s="2">${if (creditVal.isNotBlank()) "<v>$creditVal</v>" else ""}</c>
      <c r="G$rowIdx" s="2"><v>$balanceVal</v></c>
      <c r="H$rowIdx" t="inlineStr"><is><t>$statusText</t></is></c>
      <c r="I$rowIdx" t="inlineStr"><is><t>$note</t></is></c>
    </row>
""")
                rowIdx++
            }
        }

        sb.append("""  </sheetData>
  <autoFilter ref="A1:I${if (rowIdx == 2) 1 else rowIdx - 1}"/>
</worksheet>""")
        return sb.toString()
    }

    private fun buildAgingSheet(
        expenses: List<ExpenseItem>,
        nowMillis: Long
    ): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetViews>
    <sheetView rightToLeft="1" workbookViewId="0">
      <pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>
    </sheetView>
  </sheetViews>
  <cols>
    <col min="1" max="1" width="22" customWidth="1"/>
    <col min="2" max="2" width="12" customWidth="1"/>
    <col min="3" max="3" width="16" customWidth="1"/>
    <col min="4" max="4" width="16" customWidth="1"/>
    <col min="5" max="5" width="16" customWidth="1"/>
    <col min="6" max="6" width="16" customWidth="1"/>
    <col min="7" max="7" width="16" customWidth="1"/>
  </cols>
  <sheetData>
    <row r="1" spans="1:7">
      <c r="A1" s="1" t="inlineStr"><is><t>الطرف</t></is></c>
      <c r="B1" s="1" t="inlineStr"><is><t>العملة</t></is></c>
      <c r="C1" s="1" t="inlineStr"><is><t>0 إلى 7 أيام</t></is></c>
      <c r="D1" s="1" t="inlineStr"><is><t>8 إلى 30 يوماً</t></is></c>
      <c r="E1" s="1" t="inlineStr"><is><t>31 إلى 60 يوماً</t></is></c>
      <c r="F1" s="1" t="inlineStr"><is><t>أكثر من 60 يوماً</t></is></c>
      <c r="G1" s="1" t="inlineStr"><is><t>إجمالي المتبقي</t></is></c>
    </row>
""")

        val computations = org.mohtech.app.data.accounting.ReceivablesLedger.getAllocationComputations(expenses)
        val openComputations = computations.filter { it.remainingAmount > 0.0 && it.isOwedToMe }
        val grouped = openComputations.groupBy { Pair(it.party.name, it.currency) }

        var rowIdx = 2
        for ((key, items) in grouped) {
            val partyName = escapeXml(key.first)
            val curr = escapeXml(key.second)

            var b0to7 = 0.0
            var b8to30 = 0.0
            var b31to60 = 0.0
            var bOver60 = 0.0

            for (it in items) {
                when (AccountingReportUtil.classifyAging(it.expenseTimestamp, nowMillis)) {
                    AgingBracket.DAYS_0_TO_7 -> b0to7 += it.remainingAmount
                    AgingBracket.DAYS_8_TO_30 -> b8to30 += it.remainingAmount
                    AgingBracket.DAYS_31_TO_60 -> b31to60 += it.remainingAmount
                    AgingBracket.DAYS_OVER_60 -> bOver60 += it.remainingAmount
                }
            }

            val totalRem = b0to7 + b8to30 + b31to60 + bOver60

            sb.append("""    <row r="$rowIdx" spans="1:7">
      <c r="A$rowIdx" t="inlineStr"><is><t>$partyName</t></is></c>
      <c r="B$rowIdx" t="inlineStr"><is><t>$curr</t></is></c>
      <c r="C$rowIdx" s="2"><v>$b0to7</v></c>
      <c r="D$rowIdx" s="2"><v>$b8to30</v></c>
      <c r="E$rowIdx" s="2"><v>$b31to60</v></c>
      <c r="F$rowIdx" s="2"><v>$bOver60</v></c>
      <c r="G$rowIdx" s="2"><v>$totalRem</v></c>
    </row>
""")
            rowIdx++
        }

        // Summary row
        if (grouped.isNotEmpty()) {
            val lastData = rowIdx - 1
            sb.append("""    <row r="$rowIdx" spans="1:7">
      <c r="A$rowIdx" s="1" t="inlineStr"><is><t>المجموع</t></is></c>
      <c r="B$rowIdx" s="1"/>
      <c r="C$rowIdx" s="1"><f>SUM(C2:C$lastData)</f></c>
      <c r="D$rowIdx" s="1"><f>SUM(D2:D$lastData)</f></c>
      <c r="E$rowIdx" s="1"><f>SUM(E2:E$lastData)</f></c>
      <c r="F$rowIdx" s="1"><f>SUM(F2:F$lastData)</f></c>
      <c r="G$rowIdx" s="1"><f>SUM(G2:G$lastData)</f></c>
    </row>
""")
        }

        sb.append("""  </sheetData>
  <autoFilter ref="A1:G${if (grouped.isEmpty()) 1 else rowIdx - 1}"/>
</worksheet>""")
        return sb.toString()
    }
}

