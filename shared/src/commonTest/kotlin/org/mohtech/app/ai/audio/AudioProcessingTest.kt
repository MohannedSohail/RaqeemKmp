package org.mohtech.app.ai.audio

import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AudioProcessingTest {

    @Test
    fun testRmsSilenceYieldsZero() {
        val silence = ShortArray(16000) { 0 }
        val rms = AudioProcessor.calculateRmsAmplitude(silence)
        assertEquals(0.0f, rms, 0.0001f)
    }

    @Test
    fun testRmsFullScaleNormalizedToOne() {
        val maxWave = ShortArray(16000) { Short.MAX_VALUE }
        val rms = AudioProcessor.calculateRmsAmplitude(maxWave)
        assertTrue(rms in 0.99f..1.0f, "Full-scale PCM buffer must yield normalized RMS close to 1.0f: was $rms")
    }

    @Test
    fun testRmsNegativeFullScaleNormalizedToOne() {
        val minWave = ShortArray(16000) { Short.MIN_VALUE }
        val rms = AudioProcessor.calculateRmsAmplitude(minWave)
        assertTrue(rms in 0.99f..1.0f, "Negative full-scale PCM buffer must yield normalized RMS close to 1.0f without integer overflow: was $rms")
    }

    @Test
    fun testRmsEmptyBufferYieldsZeroSafely() {
        val empty = ShortArray(0)
        val rms = AudioProcessor.calculateRmsAmplitude(empty)
        assertEquals(0.0f, rms, "Empty buffer must safely return 0.0f without divide-by-zero")
    }

    @Test
    fun testRmsIntermediateWaveformNormalizedBetweenZeroAndOne() {
        // Generate a 440Hz sine wave PCM signal
        val sineWave = ShortArray(16000) { i ->
            val angle = 2.0 * kotlin.math.PI * 440.0 * i / 16000.0
            (sin(angle) * 16384).toInt().toShort()
        }
        val rms = AudioProcessor.calculateRmsAmplitude(sineWave)
        assertTrue(rms > 0.0f, "Non-silent audio must have positive RMS")
        assertTrue(rms < 1.0f, "Half-scale audio must have RMS strictly below 1.0f")
        assertTrue(rms in 0.0f..1.0f, "Normalized RMS must be strictly within [0.0f, 1.0f]")
    }

    @Test
    fun testAudioChunkingUnder30SecondsReturnsSingleChunk() {
        // 10 seconds @ 16kHz = 160,000 samples
        val tenSecondsAudio = ShortArray(160000) { (it % 1000).toShort() }
        val chunks = AudioProcessor.chunkAudio(tenSecondsAudio)

        assertEquals(1, chunks.size, "Audio under 30 seconds should remain in a single chunk")
        assertEquals(160000, chunks.first().size)
    }

    @Test
    fun testAudioChunkingExceeding30SecondsSplitsProperly() {
        // 35 seconds @ 16kHz = 560,000 samples (> 480,000 samples)
        val sampleCount = 560000
        val thirtyFiveSecondsAudio = ShortArray(sampleCount) { (it % 500).toShort() }

        val chunks = AudioProcessor.chunkAudio(thirtyFiveSecondsAudio)

        assertTrue(chunks.size >= 2, "Audio exceeding 30 seconds must be split into multiple chunks")
        // Maximum chunk size must not exceed 480,000 samples (30s @ 16kHz)
        for (chunk in chunks) {
            assertTrue(
                chunk.size <= AudioProcessor.SAMPLES_PER_30S_CHUNK,
                "Each chunk must not exceed 30 seconds (480,000 samples)"
            )
        }
        val totalPreservedSamples = chunks.sumOf { it.size }
        assertEquals(sampleCount, totalPreservedSamples, "All samples must be preserved across chunks without data loss")

        // Strictly verify sample-level content fidelity across concatenated chunks
        var sampleIndex = 0
        for (chunk in chunks) {
            for (sample in chunk) {
                assertEquals(
                    thirtyFiveSecondsAudio[sampleIndex],
                    sample,
                    "Sample at index $sampleIndex across chunks must match original audio without distortion"
                )
                sampleIndex++
            }
        }
        assertEquals(sampleCount, sampleIndex)
    }

    @Test
    fun testAudioChunkingExact30SecondsBoundary() {
        // Exactly 30 seconds @ 16kHz = 480,000 samples
        val exact30Seconds = ShortArray(AudioProcessor.SAMPLES_PER_30S_CHUNK) { 100 }
        val chunks = AudioProcessor.chunkAudio(exact30Seconds)

        assertEquals(1, chunks.size, "Audio of exactly 30 seconds should produce a single chunk")
        assertEquals(AudioProcessor.SAMPLES_PER_30S_CHUNK, chunks.first().size)
    }

    @Test
    fun testAudioChunkingEmptyBufferSafelyReturnsEmpty() {
        val emptyAudio = ShortArray(0)
        val chunks = AudioProcessor.chunkAudio(emptyAudio)
        assertTrue(chunks.isEmpty() || (chunks.size == 1 && chunks[0].isEmpty()), "Empty audio buffer should safely produce empty result")
    }

    @Test
    fun testAudioChunkingMultiMinuteAudioSplitsProperly() {
        // 65 seconds @ 16kHz = 1,040,000 samples
        val sampleCount = 1040000
        val longAudio = ShortArray(sampleCount) { (it % 256).toShort() }
        val chunks = AudioProcessor.chunkAudio(longAudio)

        assertEquals(3, chunks.size, "65 seconds of audio must be split into 3 chunks (30s + 30s + 5s)")
        assertEquals(AudioProcessor.SAMPLES_PER_30S_CHUNK, chunks[0].size)
        assertEquals(AudioProcessor.SAMPLES_PER_30S_CHUNK, chunks[1].size)
        assertEquals(sampleCount - 2 * AudioProcessor.SAMPLES_PER_30S_CHUNK, chunks[2].size)
        assertEquals(sampleCount, chunks.sumOf { it.size })
    }

    @Test
    fun testAudioChunkingExactMultipleOf30SecondsProducesExactChunksWithoutEmptyTrailingChunk() {
        // Exactly 60 seconds @ 16kHz = 960,000 samples (2 exact chunks of 480,000)
        val sixtySecondsAudio = ShortArray(2 * AudioProcessor.SAMPLES_PER_30S_CHUNK) { 100 }
        val chunks = AudioProcessor.chunkAudio(sixtySecondsAudio)

        assertEquals(2, chunks.size, "Audio of exactly 60 seconds must produce exactly 2 chunks without a trailing empty chunk")
        assertEquals(AudioProcessor.SAMPLES_PER_30S_CHUNK, chunks[0].size)
        assertEquals(AudioProcessor.SAMPLES_PER_30S_CHUNK, chunks[1].size)
        assertEquals(sixtySecondsAudio.size, chunks.sumOf { it.size })
    }

    @Test
    fun testAudioChunkingCustomChunkSize() {
        // 35,000 samples with custom maxChunkSamples = 16,000 (1 second chunks)
        val customAudio = ShortArray(35000) { 120 }
        val chunks = AudioProcessor.chunkAudio(customAudio, maxChunkSamples = 16000)

        assertEquals(3, chunks.size, "35,000 samples with 16k chunk size must split into 3 chunks (16k + 16k + 3k)")
        assertEquals(16000, chunks[0].size)
        assertEquals(16000, chunks[1].size)
        assertEquals(3000, chunks[2].size)
        assertEquals(35000, chunks.sumOf { it.size })
    }

    @Test
    fun testRmsSingleSampleBuffer() {
        val singleSample = ShortArray(1) { 16384 }
        val rms = AudioProcessor.calculateRmsAmplitude(singleSample)
        assertTrue(rms in 0.49f..0.51f, "Single sample at half-scale must return ~0.5f: was $rms")
    }

    @Test
    fun testRmsConstantDcSignalNormalizedCorrectly() {
        val dcSignal = ShortArray(16000) { 16384 }
        val rms = AudioProcessor.calculateRmsAmplitude(dcSignal)
        assertTrue(rms in 0.49f..0.51f, "Constant DC signal at half-scale must return ~0.5f: was $rms")
        assertFalse(rms.isNaN(), "RMS must never be NaN")
        assertFalse(rms.isInfinite(), "RMS must never be infinite")
    }

    @Test
    fun testRmsFullScaleSquareWaveNormalizedToOne() {
        // High-energy alternating full-scale square wave (+32767 / -32768)
        val squareWave = ShortArray(16000) { if (it % 2 == 0) Short.MAX_VALUE else Short.MIN_VALUE }
        val rms = AudioProcessor.calculateRmsAmplitude(squareWave)
        assertTrue(rms in 0.99f..1.0f, "Alternating full-scale square wave must yield normalized RMS close to 1.0f without overflow: was $rms")
    }

    @Test
    fun testAudioChunkingExact30SecondsPlusOneSample() {
        // Exactly 30 seconds + 1 sample @ 16kHz = 480,001 samples
        val boundaryAudio = ShortArray(AudioProcessor.SAMPLES_PER_30S_CHUNK + 1) { 50 }
        val chunks = AudioProcessor.chunkAudio(boundaryAudio)

        assertEquals(2, chunks.size, "Audio of 30 seconds + 1 sample must produce exactly 2 chunks")
        assertEquals(AudioProcessor.SAMPLES_PER_30S_CHUNK, chunks[0].size)
        assertEquals(1, chunks[1].size)
        assertEquals(boundaryAudio.size, chunks.sumOf { it.size })
    }

    @Test
    fun testAudioChunkingInvalidChunkSizeThrowsException() {
        val pcm = ShortArray(16000) { 100 }
        assertFailsWith<IllegalArgumentException>("maxChunkSamples of 0 must throw IllegalArgumentException") {
            AudioProcessor.chunkAudio(pcm, maxChunkSamples = 0)
        }
        assertFailsWith<IllegalArgumentException>("negative maxChunkSamples must throw IllegalArgumentException") {
            AudioProcessor.chunkAudio(pcm, maxChunkSamples = -100)
        }
    }

    @Test
    fun testRmsSingleSampleMinScaleAndZero() {
        val minSample = ShortArray(1) { Short.MIN_VALUE }
        val rmsMin = AudioProcessor.calculateRmsAmplitude(minSample)
        assertTrue(rmsMin in 0.99f..1.0f, "Single sample at Short.MIN_VALUE must return ~1.0f: was $rmsMin")

        val zeroSample = ShortArray(1) { 0 }
        val rmsZero = AudioProcessor.calculateRmsAmplitude(zeroSample)
        assertEquals(0.0f, rmsZero, "Single sample at 0 must return 0.0f")
    }
}
