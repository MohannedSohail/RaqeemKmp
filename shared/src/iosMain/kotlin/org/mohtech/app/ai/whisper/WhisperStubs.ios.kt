package org.mohtech.app.ai.whisper

/**
 * iOS stub for LocalSpeechTranscriber.
 * Raqeem is 100% offline-first; on iOS devices without local Whisper model files,
 * it safely reports ModelMissing so the UI provides manual text fallback without crashing.
 */
class IosLocalSpeechTranscriber : LocalSpeechTranscriber {
    override suspend fun transcribe(
        pcm16kMono: ShortArray,
        config: WhisperModelConfig
    ): TranscribeResult {
        return TranscribeResult.Failure(SpeechError.ModelMissing)
    }

    override fun isModelReady(): Boolean = false

    override fun getInstalledModels(): List<InstalledModel> = emptyList()

    override fun release() {}
}

/**
 * iOS stub for RawAudioRecorder.
 */
class IosRawAudioRecorder : RawAudioRecorder {
    private var recording = false

    override fun startRecording(
        onAmplitudeChanged: (Float) -> Unit,
        onError: (SpeechError) -> Unit
    ) {
        recording = false
        onError(SpeechError.ModelMissing)
    }

    override fun stopRecording(): ShortArray? {
        recording = false
        return null
    }

    override fun isRecording(): Boolean = recording

    override fun release() {
        recording = false
    }
}

actual fun createLocalSpeechTranscriber(context: Any?): LocalSpeechTranscriber = IosLocalSpeechTranscriber()

actual fun createRawAudioRecorder(context: Any?): RawAudioRecorder = IosRawAudioRecorder()
