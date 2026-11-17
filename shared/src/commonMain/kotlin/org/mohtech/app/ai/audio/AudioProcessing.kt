package org.mohtech.app.ai.audio

import kotlin.math.sqrt

/**
 * AudioProcessor:
 * Mathematical utilities for raw PCM 16kHz Mono 16-bit audio buffers.
 * Includes RMS amplitude normalization and sliding/sequential chunking for audio > 30s.
 */
object AudioProcessor {

    /**
     * Standard audio parameters for Whisper.cpp.
     */
    const val SAMPLE_RATE_HZ = 16000
    const val MAX_CHUNK_DURATION_SECONDS = 30
    const val SAMPLES_PER_30S_CHUNK = SAMPLE_RATE_HZ * MAX_CHUNK_DURATION_SECONDS // 480,000 samples

    /**
     * Calculates normalized RMS audio amplitude in the range [0.0f, 1.0f]
     * from a 16-bit linear PCM ShortArray buffer.
     */
    fun calculateRmsAmplitude(pcm: ShortArray): Float {
        if (pcm.isEmpty()) return 0.0f

        var sumOfSquares = 0.0
        for (sample in pcm) {
            val normalized = sample.toDouble() / 32768.0
            sumOfSquares += normalized * normalized
        }
        val meanSquare = sumOfSquares / pcm.size
        val rms = sqrt(meanSquare).toFloat()
        return rms.coerceIn(0.0f, 1.0f)
    }

    /**
     * Chunks a continuous PCM audio buffer exceeding 30 seconds into sequential
     * chunks of at most [maxChunkSamples] (default 480,000 samples = 30s @ 16kHz).
     */
    fun chunkAudio(
        pcm: ShortArray,
        maxChunkSamples: Int = SAMPLES_PER_30S_CHUNK
    ): List<ShortArray> {
        require(maxChunkSamples > 0) { "maxChunkSamples must be positive: $maxChunkSamples" }
        if (pcm.isEmpty()) return emptyList()

        val chunks = mutableListOf<ShortArray>()
        var start = 0
        while (start < pcm.size) {
            val end = minOf(start + maxChunkSamples, pcm.size)
            chunks.add(pcm.copyOfRange(start, end))
            start = end
        }
        return chunks
    }
}
