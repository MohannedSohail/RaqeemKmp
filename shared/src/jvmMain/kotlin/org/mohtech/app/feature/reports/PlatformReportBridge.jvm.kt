package org.mohtech.app.feature.reports

import java.io.File

actual object PlatformReportBridge {
    actual fun shareFile(filePath: String, mimeType: String) {
        // No-op on JVM desktop
    }

    actual fun saveFile(sourcePath: String, defaultFileName: String, mimeType: String) {
        // No-op on JVM desktop
    }
}

actual fun writeExportFileToCache(fileName: String, bytes: ByteArray): String {
    val reportsDir = File(System.getProperty("java.io.tmpdir") ?: ".", "raqeem_reports").apply {
        if (!exists()) mkdirs()
    }
    val file = File(reportsDir, fileName)
    file.writeBytes(bytes)
    return file.absolutePath
}


