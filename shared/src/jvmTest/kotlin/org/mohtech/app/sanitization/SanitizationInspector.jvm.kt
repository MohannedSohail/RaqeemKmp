package org.mohtech.app.sanitization

import java.io.File

actual object ProjectSourceInspector {
    actual fun getSourceFileContents(): Map<String, String> {
        var current: File? = File(".").canonicalFile
        while (current != null && !File(current, "settings.gradle.kts").exists()) {
            current = current.parentFile
        }
        val root = current ?: File(".").canonicalFile

        val result = mutableMapOf<String, String>()
        val scanDirs = listOf(
            File(root, "androidApp/src"),
            File(root, "desktopApp/src"),
            File(root, "iosApp"),
            File(root, "shared/src")
        )

        for (dir in scanDirs) {
            if (dir.exists()) {
                dir.walkTopDown()
                    .filter { file ->
                        val relPath = file.relativeTo(root).path.replace('\\', '/')
                        val segments = relPath.split('/')
                        !segments.contains("cpp")
                    }
                    .filter { it.isFile }
                    .filter { it.extension in listOf("kt", "java") || it.name == "AndroidManifest.xml" }
                    .forEach { file ->
                        val relPath = file.relativeTo(root).path.replace('\\', '/')
                        val segments = relPath.split('/')
                        val isTestSource = segments.any { segment ->
                            segment in listOf("test", "jvmTest", "commonTest", "androidTest", "iosTest", "desktopTest") ||
                                segment.endsWith("Test")
                        }
                        if (!isTestSource) {
                            result[relPath] = file.readText()
                        }
                    }
            }
        }
        return result
    }
}
