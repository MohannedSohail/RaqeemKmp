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

    // Ensure collision-free unique file path in cache (never overwrite an existing report)
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


