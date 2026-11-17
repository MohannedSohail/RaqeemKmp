package org.mohtech.app.sanitization

/**
 * Common expect declaration for inspecting project source and manifest files.
 */
expect object ProjectSourceInspector {
    fun getSourceFileContents(): Map<String, String>
}
