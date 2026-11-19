package org.mohtech.app.ai.whisper

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakeWhisperNativeBridge : WhisperNativeContextBridge {
    var loadContextCallCount = 0
    var freeContextCallCount = 0
    var lastModelPath: String? = null
    var lastNumThreads: Int = 0
    var lastLanguage: String? = null
    var lastTranslate: Boolean? = null
    var lastInitialPrompt: String? = null
    var isContextLoaded = false

    override fun isLoaded(): Boolean = isContextLoaded

    override fun loadContext(modelPath: String): Long {
        loadContextCallCount++
        lastModelPath = modelPath
        isContextLoaded = true
        return 0x12345678L
    }

    override fun freeContext(contextPtr: Long) {
        freeContextCallCount++
        isContextLoaded = false
    }

    override fun fullTranscribe(
        contextPtr: Long,
        numThreads: Int,
        pcm: FloatArray,
        language: String,
        translate: Boolean,
        initialPrompt: String
    ): String {
        lastNumThreads = numThreads
        lastLanguage = language
        lastTranslate = translate
        lastInitialPrompt = initialPrompt
        return "بنزين بخمسين شيكل"
    }
}

/**
 * Common testable implementation of LocalSpeechTranscriber backed by WhisperNativeContextBridge.
 */
class TestableSpeechTranscriber(
    private val bridge: WhisperNativeContextBridge,
    private val isModelPresent: Boolean = true
) : LocalSpeechTranscriber {

    private var activeContextPtr: Long = 0L
    private val lock = Any()

    override suspend fun transcribe(
        pcm16kMono: ShortArray,
        config: WhisperModelConfig
    ): TranscribeResult {
        if (pcm16kMono.isEmpty()) return TranscribeResult.Failure(SpeechError.EmptyResult)
        if (!isModelPresent) return TranscribeResult.Failure(SpeechError.ModelMissing)

        val ctx = synchronized(lock) {
            if (activeContextPtr == 0L) {
                activeContextPtr = bridge.loadContext(config.modelFileName)
            }
            activeContextPtr
        }

        if (ctx == 0L) return TranscribeResult.Failure(SpeechError.ModelLoadFailed)

        val floatPcm = FloatArray(pcm16kMono.size) { i -> pcm16kMono[i].toFloat() / 32768.0f }
        val text = bridge.fullTranscribe(
            contextPtr = ctx,
            numThreads = config.numThreads,
            pcm = floatPcm,
            language = config.language,
            translate = config.translate,
            initialPrompt = config.initialPrompt
        )

        return if (text.isBlank()) {
            TranscribeResult.Failure(SpeechError.EmptyResult)
        } else {
            TranscribeResult.Success(text, durationMs = 150L)
        }
    }

    override fun isModelReady(): Boolean = isModelPresent

    override fun getInstalledModels(): List<InstalledModel> = listOf(
        InstalledModel("tiny", "Tiny", "ggml-tiny-q5_1.bin", 31_000_000L, isModelPresent, true)
    )

    override fun release() {
        synchronized(lock) {
            if (activeContextPtr != 0L) {
                bridge.freeContext(activeContextPtr)
                activeContextPtr = 0L
            }
        }
    }
}

class WhisperContextLifecycleTest {

    @Test
    fun testWhisperConfigParametersPassedToNativeBridge() = runTest {
        val bridge = FakeWhisperNativeBridge()
        val transcriber = TestableSpeechTranscriber(bridge)
        val pcm = ShortArray(1600) { 100 }
        val config = WhisperModelConfig(
            modelFileName = "ggml-tiny-q5_1.bin",
            language = "ar",
            translate = false,
            initialPrompt = "بنزين، شيكل"
        )

        val result = transcriber.transcribe(pcm, config)

        assertTrue(result is TranscribeResult.Success)
        assertEquals("ar", bridge.lastLanguage)
        assertEquals(false, bridge.lastTranslate)
        assertEquals("بنزين، شيكل", bridge.lastInitialPrompt)
        assertEquals(4, bridge.lastNumThreads)
    }

    @Test
    fun testConsecutiveTranscriptionsReuseLoadedContextWithoutReloading() = runTest {
        val bridge = FakeWhisperNativeBridge()
        val transcriber = TestableSpeechTranscriber(bridge)
        val pcm = ShortArray(1600) { 100 }

        // Call 1
        val res1 = transcriber.transcribe(pcm)
        assertTrue(res1 is TranscribeResult.Success)
        assertEquals(1, bridge.loadContextCallCount, "First transcribe must load the model context")

        // Call 2
        val res2 = transcriber.transcribe(pcm)
        assertTrue(res2 is TranscribeResult.Success)
        assertEquals(1, bridge.loadContextCallCount, "Second transcribe must reuse cached context without reloading")
        assertEquals(0, bridge.freeContextCallCount, "Context should not be freed between calls")

        // Release
        transcriber.release()
        assertEquals(1, bridge.freeContextCallCount, "release() must free native context exactly once")
        assertFalse(bridge.isLoaded())

        // Multiple release calls are idempotent
        transcriber.release()
        assertEquals(1, bridge.freeContextCallCount, "subsequent release() must be idempotent")
    }

    @Test
    fun testModelMissingReturnsFailureWithoutLoadingContext() = runTest {
        val bridge = FakeWhisperNativeBridge()
        val transcriber = TestableSpeechTranscriber(bridge, isModelPresent = false)
        val pcm = ShortArray(1600) { 100 }

        val res = transcriber.transcribe(pcm)

        assertTrue(res is TranscribeResult.Failure)
        assertEquals(SpeechError.ModelMissing, res.error)
        assertEquals(0, bridge.loadContextCallCount, "Must not attempt to load context if model file is missing")
    }
}

