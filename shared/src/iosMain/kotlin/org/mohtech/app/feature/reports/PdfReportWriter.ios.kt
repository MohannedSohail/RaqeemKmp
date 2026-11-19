package org.mohtech.app.feature.reports

actual class PdfReportWriter actual constructor(context: Any?) {
    actual val supportsPdf: Boolean = false
    actual fun generatePdf(report: ReportData): ByteArray? = null
}

