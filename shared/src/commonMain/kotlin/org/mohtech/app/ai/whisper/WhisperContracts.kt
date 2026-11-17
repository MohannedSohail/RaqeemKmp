package org.mohtech.app.ai.whisper

/**
 * Metadata for a locally installed Whisper model file.
 */
data class InstalledModel(
    val id: String,
    val name: String,
    val fileName: String,
    val sizeBytes: Long,
    val isInstalled: Boolean,
    val isDefault: Boolean
)

/**
 * Configuration for an on-device Whisper transcription session.
 */
data class WhisperModelConfig(
    val modelFileName: String = "ggml-tiny.bin",
    val language: String = "ar",
    val initialPrompt: String = "شيكل، ريال، جنيه، بنزين، قهوة، سوبرماركت، مطعم، دفعت، حاسبت، اشتريت",
    val numThreads: Int = 4
)

/**
 * Strongly typed speech recognition errors.
 */
sealed interface SpeechError {
    data object ModelMissing : SpeechError
    data object ModelLoadFailed : SpeechError
    data object InitializationFailed : SpeechError
    data object TranscriptionFailed : SpeechError
    data object PermissionDenied : SpeechError
    data object AudioCapture : SpeechError
    data object EmptyResult : SpeechError
    data object OutOfMemory : SpeechError
    data class Unknown(val message: String) : SpeechError
}

/**
 * Result of a Whisper transcription operation.
 */
sealed interface TranscribeResult {
    data class Success(val text: String, val durationMs: Long) : TranscribeResult
    data class Failure(val error: SpeechError) : TranscribeResult
}

/**
 * Common contract for 100% on-device Whisper speech-to-text engine.
 */
interface LocalSpeechTranscriber {
    suspend fun transcribe(pcm16kMono: ShortArray, config: WhisperModelConfig = WhisperModelConfig()): TranscribeResult
    fun isModelReady(): Boolean
    fun getInstalledModels(): List<InstalledModel>
    fun release()
}

/**
 * Raw PCM audio recorder capturing 16kHz Mono 16-bit audio samples.
 */
interface RawAudioRecorder {
    fun startRecording(onAmplitudeChanged: (Float) -> Unit, onError: (SpeechError) -> Unit)
    fun stopRecording(): ShortArray?
    fun isRecording(): Boolean
    fun release()
}

/**
 * Platform factories for on-device Whisper audio recording and transcription.
 */
expect fun createLocalSpeechTranscriber(context: Any? = null): LocalSpeechTranscriber
expect fun createRawAudioRecorder(context: Any? = null): RawAudioRecorder
