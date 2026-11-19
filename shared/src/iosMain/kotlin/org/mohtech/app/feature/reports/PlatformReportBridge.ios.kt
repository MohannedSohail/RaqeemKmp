package org.mohtech.app.feature.reports

import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSString
import platform.Foundation.stringByAppendingPathComponent

actual object PlatformReportBridge {
    actual fun shareFile(filePath: String, mimeType: String) {
        // No-op on iOS
    }

    actual fun saveFile(sourcePath: String, defaultFileName: String, mimeType: String) {
        // No-op on iOS
    }
}

actual fun writeExportFileToCache(fileName: String, bytes: ByteArray): String {
    val tempDir = NSTemporaryDirectory()
    val fullPath = (tempDir as NSString).stringByAppendingPathComponent(fileName)
    return fullPath
}


