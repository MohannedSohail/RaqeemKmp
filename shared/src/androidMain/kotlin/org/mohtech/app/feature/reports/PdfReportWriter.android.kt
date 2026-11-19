package org.mohtech.app.feature.reports

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.mohtech.app.data.storage.AndroidContextHolder
import java.io.ByteArrayOutputStream

actual class PdfReportWriter actual constructor(context: Any?) {

    private val appContext: Context? = (context as? Context) ?: AndroidContextHolder.context
    actual val supportsPdf: Boolean = true

    // Standard A4 dimensions in PostScript points (72 dpi)
    private val pageWidth = 595
    private val pageHeight = 842
    private val margin = 36f
    private val contentWidth = pageWidth - (2 * margin)

    actual fun generatePdf(report: ReportData): ByteArray? {
        val document = PdfDocument()
        val timeZone = TimeZone.currentSystemDefault()

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 24, 33)
            textSize = 10f
            typeface = Typeface.DEFAULT
        }

        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 196, 140) // Fintech Emerald
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
        }

        val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 10f
            typeface = Typeface.DEFAULT
        }

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        var y = margin

        // Draw Header
        fun drawHeader() {
            // Brand seal square
            fillPaint.color = Color.rgb(24, 32, 50)
            canvas.drawRoundRect(RectF(pageWidth - margin - 32f, y, pageWidth - margin, y + 32f), 8f, 8f, fillPaint)
            fillPaint.color = Color.rgb(0, 196, 140)
            fillPaint.textSize = 18f
            fillPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("ر", pageWidth - margin - 16f, y + 23f, fillPaint)

            // Title
            val titleLayout = createRtlLayout("رقيم • تقرير المصروفات", headerPaint, contentWidth.toInt() - 40)
            canvas.save()
            canvas.translate(margin, y)
            titleLayout.draw(canvas)
            canvas.restore()

            y += 20f
            val subText = "${report.periodTitle} • صادر بتاريخ: ${report.generatedDateFormatted}"
            val subLayout = createRtlLayout(subText, subtitlePaint, contentWidth.toInt() - 40)
            canvas.save()
            canvas.translate(margin, y)
            subLayout.draw(canvas)
            canvas.restore()

            y += 24f

            // Divider line
            fillPaint.color = Color.rgb(226, 232, 240)
            canvas.drawRect(margin, y, pageWidth - margin, y + 1f, fillPaint)
            y += 12f
        }

        drawHeader()

        // 1. Currency Totals Summary Box
        if (report.totalsByCurrency.isNotEmpty()) {
            fillPaint.color = Color.rgb(248, 250, 252)
            canvas.drawRoundRect(RectF(margin, y, pageWidth - margin, y + 36f), 6f, 6f, fillPaint)

            val summarySb = StringBuilder("الإجماليات: ")
            report.totalsByCurrency.forEach { (curr, sum) ->
                summarySb.append("$sum $curr   ")
            }
            summarySb.append("(${report.totalOperationsCount} عمليات)")

            val sumPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42)
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
            }
            val sumLayout = createRtlLayout(summarySb.toString(), sumPaint, contentWidth.toInt() - 16)
            canvas.save()
            canvas.translate(margin + 8f, y + 10f)
            sumLayout.draw(canvas)
            canvas.restore()

            y += 48f
        }

        // 2. Table Header
        fun drawTableHeader() {
            fillPaint.color = Color.rgb(241, 245, 249)
            canvas.drawRoundRect(RectF(margin, y, pageWidth - margin, y + 20f), 4f, 4f, fillPaint)

            val thPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(71, 85, 105)
                textSize = 9f
                typeface = Typeface.DEFAULT_BOLD
            }

            // Columns right-to-left: Date, Title, Amount, Currency, Category, Payer
            drawCellRtl(canvas, "التاريخ", thPaint, margin + 420f, y + 3f, 90)
            drawCellRtl(canvas, "البند", thPaint, margin + 280f, y + 3f, 130)
            drawCellRtl(canvas, "المبلغ", thPaint, margin + 210f, y + 3f, 60)
            drawCellRtl(canvas, "العملة", thPaint, margin + 160f, y + 3f, 40)
            drawCellRtl(canvas, "التصنيف", thPaint, margin + 85f, y + 3f, 70)
            drawCellRtl(canvas, "الدافع", thPaint, margin + 10f, y + 3f, 70)

            y += 24f
        }

        drawTableHeader()

        // 3. Table Rows
        val rowPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            textSize = 9f
            typeface = Typeface.DEFAULT
        }

        for (item in report.items) {
            // Check if page overflow
            if (y > pageHeight - margin - 40f) {
                // Draw footer on current page
                drawFooter(canvas, pageNumber)
                document.finishPage(page)

                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = margin

                drawHeader()
                drawTableHeader()
            }

            val date = Instant.fromEpochMilliseconds(item.timestamp).toLocalDateTime(timeZone).date
            val dateStr = "${date.monthNumber}/${date.dayOfMonth}"
            val amtFormatted = if (item.amount % 1.0 == 0.0) "${item.amount.toInt()}" else "${item.amount}"

            drawCellRtl(canvas, dateStr, rowPaint, margin + 420f, y + 2f, 90)
            drawCellRtl(canvas, item.title, rowPaint, margin + 280f, y + 2f, 130)
            drawCellRtl(canvas, amtFormatted, rowPaint, margin + 210f, y + 2f, 60)
            drawCellRtl(canvas, item.currency, rowPaint, margin + 160f, y + 2f, 40)
            drawCellRtl(canvas, item.category.arabicTitle, rowPaint, margin + 85f, y + 2f, 70)
            drawCellRtl(canvas, item.payer.name, rowPaint, margin + 10f, y + 2f, 70)

            // hairline bottom line
            fillPaint.color = Color.rgb(241, 245, 249)
            canvas.drawRect(margin, y + 16f, pageWidth - margin, y + 17f, fillPaint)

            y += 18f
        }

        // Draw footer on last page
        drawFooter(canvas, pageNumber)
        document.finishPage(page)

        val out = ByteArrayOutputStream()
        document.writeTo(out)
        document.close()
        return out.toByteArray()
    }

    private fun drawCellRtl(
        canvas: android.graphics.Canvas,
        text: String,
        paint: TextPaint,
        x: Float,
        y: Float,
        width: Int = 120
    ) {
        val layout = createRtlLayout(text, paint, width)
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawFooter(canvas: android.graphics.Canvas, pageNumber: Int) {
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(148, 163, 184)
            textSize = 8f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("رقيم • صفحة $pageNumber", pageWidth / 2f, pageHeight - margin + 10f, footerPaint)
    }

    private fun createRtlLayout(text: String, paint: TextPaint, width: Int): StaticLayout {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, maxOf(width, 10))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(
                text,
                paint,
                maxOf(width, 10),
                Layout.Alignment.ALIGN_NORMAL,
                1.0f,
                0.0f,
                false
            )
        }
    }
}

