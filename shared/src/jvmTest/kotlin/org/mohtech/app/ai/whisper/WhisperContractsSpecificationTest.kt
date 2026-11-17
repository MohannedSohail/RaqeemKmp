package org.mohtech.app.ai.whisper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * WhisperContractsSpecificationTest (Feature F2 - Requirement R2):
 * Verifies core Whisper contracts, audio recorder interfaces, config defaults, and error hierarchies
 * across Tier 1 (functional feature) and Tier 2 (boundary/corner conditions).
 */
class WhisperContractsSpecificationTest {

    // =========================================================================
    // Tier 1: Functional Feature Coverage (Requirement R2)
    // =========================================================================

    @Test
    fun testWhisperModelConfigDefaults() {
        val config = WhisperModelConfig()
        assertEquals("ggml-tiny.bin", config.modelFileName, "Default model file must be ggml-tiny.bin")
        assertEquals("ar", config.language, "Default transcription language must be Arabic ('ar')")
        assertEquals(4, config.numThreads, "Default thread pool count should be 4 for optimal CPU performance")
        assertTrue(config.initialPrompt.contains("شيكل"), "Initial prompt must contain Arabic financial terms")
        assertTrue(config.initialPrompt.contains("بنزين"), "Initial prompt must contain Arabic expense keywords")
    }

    @Test
    fun testInstalledModelDataClassProperties() {
        val model = InstalledModel(
            id = "tiny-ar",
            name = "Whisper Tiny Arabic",
            fileName = "ggml-tiny.bin",
            sizeBytes = 75_000_000L,
            isInstalled = true,
            isDefault = true
        )

        assertEquals("tiny-ar", model.id)
        assertEquals("Whisper Tiny Arabic", model.name)
        assertEquals("ggml-tiny.bin", model.fileName)
        assertEquals(75_000_000L, model.sizeBytes)
        assertTrue(model.isInstalled)
        assertTrue(model.isDefault)

        val copy = model.copy(isDefault = false)
        assertFalse(copy.isDefault)
        assertEquals(model.id, copy.id)
    }

    @Test
    fun testTranscribeResultSuccessProperties() {
        val success = TranscribeResult.Success(text = "قهوة بخمسة شيكل", durationMs = 250L)
        assertEquals("قهوة بخمسة شيكل", success.text)
        assertEquals(250L, success.durationMs)
        assertIs<TranscribeResult>(success)
    }

    @Test
    fun testTranscribeResultFailureProperties() {
        val failure = TranscribeResult.Failure(error = SpeechError.ModelMissing)
        assertEquals(SpeechError.ModelMissing, failure.error)
        assertIs<TranscribeResult>(failure)
    }

    @Test
    fun testSpeechErrorHierarchyExhaustiveness() {
        val allErrors: List<SpeechError> = listOf(
            SpeechError.ModelMissing,
            SpeechError.ModelLoadFailed,
            SpeechError.InitializationFailed,
            SpeechError.TranscriptionFailed,
            SpeechError.PermissionDenied,
            SpeechError.AudioCapture,
            SpeechError.EmptyResult,
            SpeechError.OutOfMemory,
            SpeechError.Unknown("custom error")
        )

        assertEquals(9, allErrors.size, "SpeechError must define 9 concrete strongly-typed error cases")

        for (err in allErrors) {
            val description = when (err) {
                is SpeechError.ModelMissing -> "ModelMissing"
                is SpeechError.ModelLoadFailed -> "ModelLoadFailed"
                is SpeechError.InitializationFailed -> "InitializationFailed"
                is SpeechError.TranscriptionFailed -> "TranscriptionFailed"
                is SpeechError.PermissionDenied -> "PermissionDenied"
                is SpeechError.AudioCapture -> "AudioCapture"
                is SpeechError.EmptyResult -> "EmptyResult"
                is SpeechError.OutOfMemory -> "OutOfMemory"
                is SpeechError.Unknown -> "Unknown: ${err.message}"
            }
            assertNotNull(description)
        }
    }

    // =========================================================================
    // Tier 2: Boundary, Corner & Edge Cases
    // =========================================================================

    @Test
    fun testWhisperModelConfigCustomValues() {
        val customConfig = WhisperModelConfig(
            modelFileName = "ggml-base.bin",
            language = "ar",
            initialPrompt = "custom prompt",
            numThreads = 8
        )
        assertEquals("ggml-base.bin", customConfig.modelFileName)
        assertEquals(8, customConfig.numThreads)
        assertEquals("custom prompt", customConfig.initialPrompt)
    }

    @Test
    fun testSpeechErrorUnknownPreservesCustomMessage() {
        val errorMsg = "Native JNI error: whisper_full failed with code -3"
        val unknown = SpeechError.Unknown(errorMsg)
        assertEquals(errorMsg, unknown.message)
        assertEquals(unknown, SpeechError.Unknown(errorMsg))
    }

    @Test
    fun testTranscribeResultZeroDurationSuccess() {
        val zeroDuration = TranscribeResult.Success(text = "سوبرماركت", durationMs = 0L)
        assertEquals(0L, zeroDuration.durationMs)
        assertEquals("سوبرماركت", zeroDuration.text)
    }

    @Test
    fun testInstalledModelLargeFileSize() {
        // Multi-gigabyte model size representation (e.g. large-v3 ~1.5 GB)
        val largeSizeBytes = 1_550_000_000L
        val largeModel = InstalledModel(
            id = "large-v3",
            name = "Whisper Large v3",
            fileName = "ggml-large-v3.bin",
            sizeBytes = largeSizeBytes,
            isInstalled = false,
            isDefault = false
        )
        assertEquals(largeSizeBytes, largeModel.sizeBytes)
        assertFalse(largeModel.isInstalled)
    }

    @Test
    fun testRawAudioRecorderEmptyPcmContract() {
        val recorder = FakeAudioRecorder(recordedPcm = ShortArray(0))
        recorder.startRecording(onAmplitudeChanged = {}, onError = {})
        val result = recorder.stopRecording()
        assertNotNull(result)
        assertEquals(0, result.size)
        recorder.release()
    }
}
