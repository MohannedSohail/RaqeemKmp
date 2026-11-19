package org.mohtech.app.ai.audio

import kotlin.math.sqrt

/**
 * Result of Voice Activity Detection analysis on a 16kHz PCM buffer.
 */
sealed interface VadResult {
    /**
     * Speech was detected. [trimmedPcm] contains the speech with pre/post padding and compressed gaps.
     * [speechDurationMs] represents the cumulative estimated speech duration.
     */
    data class Speech(val trimmedPcm: ShortArray, val speechDurationMs: Long) : VadResult

    /**
     * No sufficient speech activity was detected (pure silence, ambient noise, or speech < minimum duration).
     */
    data object NoSpeech : VadResult
}

/**
 * VoiceActivityDetector:
 * 100% Offline pure Kotlin VAD engine for 16kHz Mono 16-bit PCM audio.
 * Uses adaptive moving noise floor energy threshold, hangover & pre-padding (~250ms),
 * initial/trailing silence trimming, and long interior pause compression (> 1s reduced to 500ms).
 * Rejects recordings without at least [minSpeechDurationMs] (default 300ms) of voiced frames.
 */
class VoiceActivityDetector(
    val sampleRateHz: Int = 16000,
    val frameDurationMs: Int = 25, // 20-30ms frame
    val minSpeechDurationMs: Int = 300, // minimum speech required
    val paddingMs: Int = 250, // hangover and pre-speech padding
    val maxInternalSilenceMs: Int = 1000, // pause ceiling
    val compressedSilenceMs: Int = 500 // compress pause to
) {
    private val frameSizeSamples: Int = (sampleRateHz * frameDurationMs) / 1000 // 400 samples
    private val paddingFrames: Int = (paddingMs + frameDurationMs - 1) / frameDurationMs // 10 frames
    private val minSpeechFrames: Int = (minSpeechDurationMs + frameDurationMs - 1) / frameDurationMs // 12 frames
    private val maxSilenceFrames: Int = (maxInternalSilenceMs + frameDurationMs - 1) / frameDurationMs // 40 frames
    private val targetSilenceFrames: Int = (compressedSilenceMs + frameDurationMs - 1) / frameDurationMs // 20 frames

    /**
     * Processes raw 16kHz linear PCM audio and returns trimmed audio or [VadResult.NoSpeech].
     * Guarantees output PCM size <= input PCM size.
     */
    fun process(pcm: ShortArray): VadResult {
        if (pcm.isEmpty() || pcm.size < frameSizeSamples) {
            return VadResult.NoSpeech
        }

        val totalFrames = pcm.size / frameSizeSamples
        val frameEnergies = FloatArray(totalFrames)

        // 1. Calculate RMS energy for each frame
        var minEnergy = Float.MAX_VALUE
        var avgEnergy = 0.0f
        for (f in 0 until totalFrames) {
            val offset = f * frameSizeSamples
            var sumOfSquares = 0.0
            for (i in 0 until frameSizeSamples) {
                val s = pcm[offset + i].toDouble() / 32768.0
                sumOfSquares += s * s
            }
            val rms = sqrt(sumOfSquares / frameSizeSamples).toFloat()
            frameEnergies[f] = rms
            if (rms < minEnergy) minEnergy = rms
            avgEnergy += rms
        }
        avgEnergy /= totalFrames

        // 2. Adaptive threshold: adaptive noise floor + margin
        // Minimum floor of 0.015f to reject absolute low-energy background hiss
        val noiseFloor = maxOf(minEnergy, 0.008f)
        val speechThreshold = maxOf(noiseFloor * 2.5f, 0.020f)

        val isSpeech = BooleanArray(totalFrames) { f ->
            frameEnergies[f] >= speechThreshold
        }

        // Count total speech frames
        var totalSpeechFrames = 0
        for (s in isSpeech) {
            if (s) totalSpeechFrames++
        }

        if (totalSpeechFrames < minSpeechFrames) {
            return VadResult.NoSpeech
        }

        // 3. Mark frames with hangover and pre-speech padding
        val keepFrame = BooleanArray(totalFrames)
        for (f in 0 until totalFrames) {
            if (isSpeech[f]) {
                val start = maxOf(0, f - paddingFrames)
                val end = minOf(totalFrames - 1, f + paddingFrames)
                for (k in start..end) {
                    keepFrame[k] = true
                }
            }
        }

        // Find first and last kept frame (trimming start and end silence)
        var firstKept = -1
        var lastKept = -1
        for (f in 0 until totalFrames) {
            if (keepFrame[f]) {
                if (firstKept == -1) firstKept = f
                lastKept = f
            }
        }

        if (firstKept == -1 || lastKept == -1 || (lastKept - firstKept + 1) < minSpeechFrames) {
            return VadResult.NoSpeech
        }

        // 4. Compress long interior silence down to targetSilenceFrames
        val outputFrames = mutableListOf<Int>()
        var currentSilenceStreak = 0
        for (f in firstKept..lastKept) {
            val speech = isSpeech[f]
            if (!speech) {
                currentSilenceStreak++
                if (currentSilenceStreak <= targetSilenceFrames) {
                    outputFrames.add(f)
                }
                // If silence exceeds targetSilenceFrames, drop subsequent frames to compress long pause
            } else {
                currentSilenceStreak = 0
                outputFrames.add(f)
            }
        }

        if (outputFrames.isEmpty()) {
            return VadResult.NoSpeech
        }

        // Construct cleaned ShortArray
        val resultPcm = ShortArray(outputFrames.size * frameSizeSamples)
        for (i in outputFrames.indices) {
            val frameIndex = outputFrames[i]
            val srcOffset = frameIndex * frameSizeSamples
            val dstOffset = i * frameSizeSamples
            pcm.copyInto(
                destination = resultPcm,
                destinationOffset = dstOffset,
                startIndex = srcOffset,
                endIndex = srcOffset + frameSizeSamples
            )
        }

        val speechDurationMs = (totalSpeechFrames.toLong() * frameDurationMs)
        return VadResult.Speech(resultPcm, speechDurationMs)
    }
}

