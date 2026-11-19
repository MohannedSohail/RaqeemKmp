package org.mohtech.app.feature.reports

/**
 * Platform abstraction for on-device PDF generation.
 * Full BiDi Arabic text shaping and native canvas rendering is implemented on Android via AndroidPdfReportWriter.
 * [supportsPdf] informs the UI whether PDF export is available on the current target.
 */
expect class PdfReportWriter(context: Any? = null) {
    val supportsPdf: Boolean
    fun generatePdf(report: ReportData): ByteArray?
}

