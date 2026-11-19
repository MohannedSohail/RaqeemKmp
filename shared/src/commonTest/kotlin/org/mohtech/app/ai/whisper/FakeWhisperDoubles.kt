package org.mohtech.app.ai.whisper

/**
 * FakeTranscriber:
 * Deterministic test double implementing [LocalSpeechTranscriber] for isolated unit testing.
 * Simulates on-device Whisper transcription without external model files or native binaries.
 */
class FakeTranscriber(
    var shouldFailWith: SpeechError? = null,
    var transcribeResultText: String = "قهوة بعشرة شيكل",
    var isModelReadyState: Boolean = true
) : LocalSpeechTranscriber {

    var transcribeCount: Int = 0
    var releaseCount: Int = 0
    var lastTranscribedPcm: ShortArray? = null
    var lastConfig: WhisperModelConfig? = null

    var availableModels: List<InstalledModel> = listOf(
        InstalledModel(
            id = "tiny",
            name = "Whisper Tiny (Offline)",
            fileName = "ggml-tiny-q5_1.bin",
            sizeBytes = 31_000_000L,
            isInstalled = true,
            isDefault = true
        )
    )

    override suspend fun transcribe(
        pcm16kMono: ShortArray,
        config: WhisperModelConfig
    ): TranscribeResult {
        transcribeCount++
        lastTranscribedPcm = pcm16kMono
        lastConfig = config

        val failure = shouldFailWith
        if (failure != null) {
            return TranscribeResult.Failure(failure)
        }

        if (transcribeResultText.isBlank()) {
            return TranscribeResult.Failure(SpeechError.EmptyResult)
        }

        return TranscribeResult.Success(transcribeResultText, durationMs = 120L)
    }

    override fun isModelReady(): Boolean = isModelReadyState

    override fun getInstalledModels(): List<InstalledModel> = availableModels

    override fun release() {
        releaseCount++
    }
}

/**
 * FakeAudioRecorder:
 * Deterministic test double implementing [RawAudioRecorder] for isolated unit testing.
 * Simulates PCM 16kHz mono audio recording lifecycle and amplitude events without hardware mic contention.
 */
class FakeAudioRecorder(
    var recordedPcm: ShortArray? = ShortArray(16000) { 100 },
    var errorToEmitOnStart: SpeechError? = null
) : RawAudioRecorder {

    var isRecordingState: Boolean = false
    var startRecordingCount: Int = 0
    var stopRecordingCount: Int = 0
    var releaseCount: Int = 0

    private var amplitudeCallback: ((Float) -> Unit)? = null
    private var errorCallback: ((SpeechError) -> Unit)? = null

    override fun startRecording(
        onAmplitudeChanged: (Float) -> Unit,
        onError: (SpeechError) -> Unit
    ) {
        startRecordingCount++
        amplitudeCallback = onAmplitudeChanged
        errorCallback = onError

        val err = errorToEmitOnStart
        if (err != null) {
            onError(err)
        } else {
            isRecordingState = true
        }
    }

    override fun stopRecording(): ShortArray? {
        stopRecordingCount++
        isRecordingState = false
        return recordedPcm
    }

    override fun isRecording(): Boolean = isRecordingState

    override fun release() {
        releaseCount++
        isRecordingState = false
    }

    fun emitAmplitude(amplitude: Float) {
        amplitudeCallback?.invoke(amplitude)
    }

    fun emitError(error: SpeechError) {
        errorCallback?.invoke(error)
    }
}
