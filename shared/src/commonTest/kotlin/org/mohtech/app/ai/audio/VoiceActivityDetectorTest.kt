package org.mohtech.app.ai.audio

import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VoiceActivityDetectorTest {

    private val vad = VoiceActivityDetector(
        sampleRateHz = 16000,
        frameDurationMs = 25,
        minSpeechDurationMs = 300,
        paddingMs = 250,
        maxInternalSilenceMs = 1000,
        compressedSilenceMs = 500
    )

    private fun generateSineWave(durationMs: Int, amplitude: Short = 16000): ShortArray {
        val totalSamples = (16000 * durationMs) / 1000
        val pcm = ShortArray(totalSamples)
        for (i in 0 until totalSamples) {
            val angle = 2.0 * kotlin.math.PI * 440.0 * (i.toDouble() / 16000.0)
            pcm[i] = (sin(angle) * amplitude).toInt().toShort()
        }
        return pcm
    }

    private fun generateSilence(durationMs: Int, noiseAmplitude: Short = 20): ShortArray {
        val totalSamples = (16000 * durationMs) / 1000
        val pcm = ShortArray(totalSamples)
        for (i in 0 until totalSamples) {
            pcm[i] = ((i % 3 - 1) * noiseAmplitude).toShort()
        }
        return pcm
    }

    private fun concatAudio(vararg chunks: ShortArray): ShortArray {
        val total = chunks.sumOf { it.size }
        val res = ShortArray(total)
        var offset = 0
        for (c in chunks) {
            c.copyInto(res, offset)
            offset += c.size
        }
        return res
    }

    data class VadScenario(
        val name: String,
        val input: ShortArray,
        val expectSpeech: Boolean
    )

    @Test
    fun testVoiceActivityDetectorScenarios() {
        val scenarios = listOf(
            // 1. Total silence (reject)
            VadScenario(
                name = "Total silence 2s",
                input = generateSilence(2000),
                expectSpeech = false
            ),
            // 2. Short glitch (< 300ms, reject)
            VadScenario(
                name = "Short noise spike 100ms",
                input = concatAudio(generateSilence(500), generateSineWave(100), generateSilence(500)),
                expectSpeech = false
            ),
            // 3. Middle speech with padding (retain)
            VadScenario(
                name = "Middle speech 1s with silence before and after",
                input = concatAudio(generateSilence(1000), generateSineWave(1000), generateSilence(1000)),
                expectSpeech = true
            ),
            // 4. Speech starting immediately (0 delay at start)
            VadScenario(
                name = "Speech starting immediately 800ms",
                input = concatAudio(generateSineWave(800), generateSilence(600)),
                expectSpeech = true
            ),
            // 5. Speech with long internal silence gap (gap compressed)
            VadScenario(
                name = "Two words separated by 2s pause",
                input = concatAudio(
                    generateSilence(500),
                    generateSineWave(500),
                    generateSilence(2000),
                    generateSineWave(500),
                    generateSilence(500)
                ),
                expectSpeech = true
            )
        )

        for (scenario in scenarios) {
            val result = vad.process(scenario.input)
            if (scenario.expectSpeech) {
                assertTrue(result is VadResult.Speech, "Scenario '${scenario.name}' should detect speech")
                val speechResult = result as VadResult.Speech
                assertTrue(speechResult.trimmedPcm.isNotEmpty(), "Trimmed PCM must not be empty for '${scenario.name}'")
                assertTrue(
                    speechResult.trimmedPcm.size <= scenario.input.size,
                    "Output length (${speechResult.trimmedPcm.size}) must be <= input length (${scenario.input.size}) for '${scenario.name}'"
                )
            } else {
                assertEquals(
                    VadResult.NoSpeech,
                    result,
                    "Scenario '${scenario.name}' should return NoSpeech"
                )
            }
        }
    }
}

