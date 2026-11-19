package org.mohtech.app.feature.reports

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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
}

