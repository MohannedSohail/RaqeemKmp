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

    // Clean up temporary export files older than 7 days (Axis C4 requirement)
    try {
        val now = System.currentTimeMillis()
        val sevenDaysMillis = 7L * 24L * 60L * 60L * 1000L
        reportsDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("raqeem_") && (now - file.lastModified() > sevenDaysMillis)) {
                file.delete()
            }
        }
    } catch (_: Throwable) {
        // Ignore cache cleanup failures
    }

    var targetFile = File(reportsDir, fileName)
    if (targetFile.exists()) {
        val dotIndex = fileName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
        val ext = if (dotIndex > 0) fileName.substring(dotIndex) else ""
        var counter = 2
        while (targetFile.exists()) {
            targetFile = File(reportsDir, "$baseName-$counter$ext")
            counter++
        }
    }

    targetFile.writeBytes(bytes)
    return targetFile.absolutePath
}


