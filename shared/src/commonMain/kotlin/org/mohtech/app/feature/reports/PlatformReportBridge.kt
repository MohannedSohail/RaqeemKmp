package org.mohtech.app.feature.reports

/**
 * Platform bridge for sharing and saving exported reports.
 */
expect object PlatformReportBridge {
    fun shareFile(filePath: String, mimeType: String)
    fun saveFile(sourcePath: String, defaultFileName: String, mimeType: String)
}

/**
 * Writes the exported report bytes to the application cache directory under "reports/".
 * Cleans up temporary report exports older than 24 hours.
 * Returns the absolute path of the generated report file.
 */
expect fun writeExportFileToCache(fileName: String, bytes: ByteArray): String


