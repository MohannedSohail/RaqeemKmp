package org.mohtech.app.feature.reports

/**
 * AndroidReportBridge:
 * Bridges report delivery actions (FileProvider share & Storage Access Framework save)
 * with MainActivity without memory leaks or static Context references.
 */
object AndroidReportBridge {
    var shareFileDelegate: ((filePath: String, mimeType: String) -> Unit)? = null
    var saveDocumentLauncher: ((sourcePath: String, defaultFileName: String, mimeType: String) -> Unit)? = null
}

