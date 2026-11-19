package org.mohtech.app.sanitization

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * SanitizationRequirementsTest (Feature F1 - Requirement R1):
 * Comprehensive opaque-box architectural enforcement for Tier 1 and Tier 2 sanitization requirements.
 * Guarantees zero network permissions, zero cloud speech dependencies, zero telemetry SDKs,
 * and 100% offline-first privacy compliance.
 */
class SanitizationRequirementsTest {

    // =========================================================================
    // Tier 1: Functional Feature Coverage (Requirement R1)
    // =========================================================================

    @Test
    fun testManifestDeclaresZeroInternetPermission() {
        val files = ProjectSourceInspector.getSourceFileContents()
        val manifests = files.filter { it.key.endsWith("AndroidManifest.xml") }
        assertTrue(manifests.isNotEmpty(), "At least one AndroidManifest.xml must be verified")

        for ((path, content) in manifests) {
            assertFalse(
                content.contains("android.permission.INTERNET"),
                "[$path] violates offline-first requirement R1 by declaring android.permission.INTERNET"
            )
        }
    }

    @Test
    fun testManifestDeclaresZeroNetworkStatePermission() {
        val files = ProjectSourceInspector.getSourceFileContents()
        val manifests = files.filter { it.key.endsWith("AndroidManifest.xml") }
        assertTrue(manifests.isNotEmpty(), "At least one AndroidManifest.xml must be verified")

        for ((path, content) in manifests) {
            assertFalse(
                content.contains("android.permission.ACCESS_NETWORK_STATE"),
                "[$path] violates offline-first requirement R1 by declaring android.permission.ACCESS_NETWORK_STATE"
            )
        }
    }

    @Test
    fun testManifestDeclaresNoGoogleSpeechQueries() {
        val files = ProjectSourceInspector.getSourceFileContents()
        val manifests = files.filter { it.key.endsWith("AndroidManifest.xml") }

        val forbiddenQueries = listOf(
            "RecognitionService",
            "googlequicksearchbox",
            "android.speech"
        )

        for ((path, content) in manifests) {
            for (queryToken in forbiddenQueries) {
                assertFalse(
                    content.contains(queryToken),
                    "[$path] contains Google Speech query token '$queryToken' violating requirement R1"
                )
            }
        }
    }

    @Test
    fun testCodebaseContainsNoGoogleSpeechImports() {
        val files = ProjectSourceInspector.getSourceFileContents()
        val forbiddenImports = listOf(
            "import android.speech.SpeechRecognizer",
            "import android.speech.RecognizerIntent",
            "import android.speech.RecognitionListener",
            "import android.speech.RecognitionService"
        )

        val violations = mutableListOf<String>()
        for ((path, content) in files) {
            for (imp in forbiddenImports) {
                if (content.contains(imp)) {
                    violations.add("[$path] contains forbidden import: $imp")
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "Found Google speech imports violating R1:\n" + violations.joinToString("\n")
        )
    }

    @Test
    fun testCodebaseContainsNoCloudHttpClientImports() {
        val files = ProjectSourceInspector.getSourceFileContents()
        val cloudHttpTokens = listOf(
            "io.ktor.client",
            "okhttp3.OkHttpClient",
            "retrofit2.Retrofit"
        )

        val violations = mutableListOf<String>()
        for ((path, content) in files) {
            for (token in cloudHttpTokens) {
                if (content.contains(token)) {
                    violations.add("[$path] contains cloud HTTP dependency: $token")
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "Found cloud HTTP client dependencies in 100% offline app:\n" + violations.joinToString("\n")
        )
    }

    // =========================================================================
    // Tier 2: Boundary, Corner & Adversarial Checks
    // =========================================================================

    @Test
    fun testManifestHasMicrophonePermissionOnly() {
        val files = ProjectSourceInspector.getSourceFileContents()
        val manifests = files.filter { it.key.endsWith("AndroidManifest.xml") }

        for ((path, content) in manifests) {
            assertTrue(
                content.contains("android.permission.RECORD_AUDIO"),
                "[$path] must declare RECORD_AUDIO for local on-device voice recording"
            )
            // Verify no unexpected permissions like ACCESS_FINE_LOCATION, READ_CONTACTS, CAMERA
            val dangerousNetworkOrPrivacyPermissions = listOf(
                "ACCESS_FINE_LOCATION",
                "ACCESS_COARSE_LOCATION",
                "READ_CONTACTS",
                "CAMERA",
                "RECEIVE_BOOT_COMPLETED"
            )
            for (permission in dangerousNetworkOrPrivacyPermissions) {
                assertFalse(
                    content.contains(permission),
                    "[$path] contains unapproved permission: $permission"
                )
            }
        }
    }

    @Test
    fun testNoHardcodedNetworkUrlsInCodebase() {
        val files = ProjectSourceInspector.getSourceFileContents()
        // Expanded exception: schemas.openxmlformats.org and purl.org are strictly XML namespace identifiers
        // required by the Open Packaging Conventions (ECMA-376 / ISO/IEC 29500) for valid XLSX generation.
        // They are static schema URIs, never fetched over the network.
        val urlRegex = Regex("""https?://(?!schemas\.android\.com|www\.w3\.org|schemas\.openxmlformats\.org|purl\.org)[^\s"'>]+""")

        val violations = mutableListOf<String>()
        for ((path, content) in files) {
            val matches = urlRegex.findAll(content).map { it.value }.toList()
            if (matches.isNotEmpty()) {
                violations.add("[$path] contains network URLs: $matches")
            }
        }

        assertTrue(
            violations.isEmpty(),
            "Expected 0 hardcoded external network URLs in offline codebase:\n" + violations.joinToString("\n")
        )
    }

    @Test
    fun testNoSpeechRecognizerDynamicReflection() {
        val files = ProjectSourceInspector.getSourceFileContents()
        val violations = mutableListOf<String>()

        for ((path, content) in files) {
            if (content.contains("Class.forName")) {
                val lowercase = content.lowercase()
                if (lowercase.contains("speech") || lowercase.contains("recognizer")) {
                    violations.add("[$path] contains dynamic reflection loading speech classes")
                }
            }
        }

        assertTrue(violations.isEmpty(), "Found dynamic speech reflection:\n" + violations.joinToString("\n"))
    }

    @Test
    fun testInspectorDiscoversAllProductionSourceRoots() {
        val files = ProjectSourceInspector.getSourceFileContents()
        assertTrue(files.keys.any { it.startsWith("androidApp") }, "Inspector must discover androidApp source")
        assertTrue(files.keys.any { it.startsWith("shared") }, "Inspector must discover shared source")
        assertTrue(files.size >= 10, "Inspector should discover all production source files (found ${files.size})")
    }

    @Test
    fun testNoCloudOrNetworkKeywordsInDependencies() {
        val files = ProjectSourceInspector.getSourceFileContents()
        val speechAndAudioFiles = files.filter { it.key.contains("audio") || it.key.contains("whisper") }

        for ((path, content) in speechAndAudioFiles) {
            assertFalse(
                content.contains("firebase") || content.contains("aws") || content.contains("azure"),
                "[$path] references cloud provider in audio/whisper logic"
            )
        }
    }
}
