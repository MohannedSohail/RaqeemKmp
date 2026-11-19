package org.mohtech.app.feature.reports

import org.mohtech.app.data.storage.AndroidContextHolder
import java.io.File

actual object PlatformReportBridge {
    actual fun shareFile(filePath: String, mimeType: String) {
        AndroidReportBridge.shareFileDelegate?.invoke(filePath, mimeType)
    }

    actual fun saveFile(sourcePath: String, defaultFileName: String, mimeType: String) {
        AndroidReportBridge.saveDocumentLauncher?.invoke(sourcePath, defaultFileName, mimeType)
    }
}

actual fun writeExportFileToCache(fileName: String, bytes: ByteArray): String {
    val appContext = AndroidContextHolder.context
    val reportsDir = if (appContext != null) {
        File(appContext.cacheDir, "reports").apply { if (!exists()) mkdirs() }
    } else {
        File(System.getProperty("java.io.tmpdir") ?: ".", "reports").apply { if (!exists()) mkdirs() }
    }

    // Clean up temporary export files older than 24 hours
    try {
        val now = System.currentTimeMillis()
        reportsDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("raqeem_report_") && (now - file.lastModified() > 86_400_000L)) {
                file.delete()
            }
        }
    } catch (_: Throwable) {
        // Ignore cache cleanup failures
    }

    val reportFile = File(reportsDir, fileName)
    reportFile.writeBytes(bytes)
    return reportFile.absolutePath
}


