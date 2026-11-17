package org.mohtech.app.sanitization

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * SanitizationTest:
 * Architectural enforcement verifying zero cloud/Google Speech dependencies, zero network permissions,
 * and zero speech reflection calls in the 100% offline-first Raqeem codebase.
 */
class SanitizationTest {

    @Test
    fun testAbsenceOfGoogleSpeechDependencies() {
        val files = ProjectSourceInspector.getSourceFileContents()
        assertTrue(files.isNotEmpty(), "Sanitization inspector must discover production source files to verify")

        val forbiddenSpeechTokens = listOf(
            "android.speech",
            "RecognizerIntent",
            "SpeechRecognizer",
            "googlequicksearchbox"
        )
        val violations = mutableListOf<String>()

        for ((path, content) in files) {
            for (token in forbiddenSpeechTokens) {
                if (content.contains(token)) {
                    violations.add("[$path] contains forbidden Google Speech token: '$token'")
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "Architecture Sanitization Failure: Expected 0 references to android.speech and RecognizerIntent, but found ${violations.size}:\n" +
                violations.joinToString("\n")
        )
    }

    @Test
    fun testAbsenceOfInternetAndNetworkPermissions() {
        val files = ProjectSourceInspector.getSourceFileContents()
        assertTrue(files.isNotEmpty(), "Sanitization inspector must discover production source files to verify")

        val manifests = files.filter { it.key.endsWith("AndroidManifest.xml") }
        assertTrue(manifests.isNotEmpty(), "Sanitization inspector must discover at least one AndroidManifest.xml to verify permissions")

        val forbiddenPermissions = listOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "\"INTERNET\"",
            "\"ACCESS_NETWORK_STATE\""
        )
        val violations = mutableListOf<String>()

        for ((path, content) in manifests) {
            for (permission in forbiddenPermissions) {
                if (content.contains(permission)) {
                    violations.add("[$path] declares forbidden network permission: '$permission'")
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "Manifest Sanitization Failure: Expected 0 network permissions in AndroidManifest.xml, but found:\n" +
                violations.joinToString("\n")
        )
    }

    @Test
    fun testAbsenceOfGoogleSpeechReflectionCalls() {
        val files = ProjectSourceInspector.getSourceFileContents()
        assertTrue(files.isNotEmpty(), "Sanitization inspector must discover production source files to verify")

        val violations = mutableListOf<String>()

        for ((path, content) in files) {
            if (content.contains("Class.forName")) {
                val lowercaseContent = content.lowercase()
                if (lowercaseContent.contains("speech") ||
                    lowercaseContent.contains("recognizer") ||
                    lowercaseContent.contains("google")
                ) {
                    violations.add("[$path] contains reflection call attempting to load Google/Speech classes: 'Class.forName'")
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "Reflection Sanitization Failure: Expected 0 reflection calls to Google Speech services, but found:\n" +
                violations.joinToString("\n")
        )
    }
}
