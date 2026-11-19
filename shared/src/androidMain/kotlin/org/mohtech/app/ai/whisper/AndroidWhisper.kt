package org.mohtech.app.ai.whisper

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mohtech.app.ai.audio.AudioProcessor
import org.mohtech.app.data.storage.AndroidContextHolder
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * AndroidRawAudioRecorder:
 * Captures 16kHz Mono 16-bit linear PCM audio using android.media.AudioRecord.
 * Emits real-time normalized RMS amplitude for UI audio wave visualization.
 * Guarantees safe release of microphone hardware resources.
 */
class AndroidRawAudioRecorder(
    private val context: Context? = null
) : RawAudioRecorder {

    companion object {
        const val SAMPLE_RATE_HZ = 16000
        const val BUFFER_SIZE_FACTOR = 2
    }

    private val isRecordingState = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private val pcmBuffer = mutableListOf<Short>()
    private val bufferLock = Any()

    override fun startRecording(
        onAmplitudeChanged: (Float) -> Unit,
        onError: (SpeechError) -> Unit
    ) {
        val appContext = context ?: AndroidContextHolder.context
        if (appContext != null &&
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            onError(SpeechError.PermissionDenied)
            return
        }

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                onError(SpeechError.AudioCapture)
                return
            }

            val bufferSize = maxOf(minBufferSize, SAMPLE_RATE_HZ * BUFFER_SIZE_FACTOR)
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                onError(SpeechError.AudioCapture)
                return
            }

            audioRecord = record
            synchronized(bufferLock) {
                pcmBuffer.clear()
            }
            isRecordingState.set(true)
            record.startRecording()

            recordingThread = thread(name = "Raqeem-AudioRecord-Thread") {
                val readBuffer = ShortArray(1600) // 100ms at 16kHz
                try {
                    while (isRecordingState.get()) {
                        val readCount = record.read(readBuffer, 0, readBuffer.size)
                        if (readCount > 0) {
                            val chunk = if (readCount == readBuffer.size) readBuffer else readBuffer.copyOf(readCount)
                            synchronized(bufferLock) {
                                for (i in 0 until readCount) {
                                    pcmBuffer.add(readBuffer[i])
                                }
                            }
                            val rms = AudioProcessor.calculateRmsAmplitude(chunk)
                            onAmplitudeChanged(rms)
                        } else if (readCount < 0) {
                            if (isRecordingState.get()) {
                                onError(SpeechError.AudioCapture)
                            }
                            break
                        }
                    }
                } catch (_: Throwable) {
                    if (isRecordingState.get()) {
                        onError(SpeechError.AudioCapture)
                    }
                }
            }
        } catch (_: SecurityException) {
            onError(SpeechError.PermissionDenied)
        } catch (_: Throwable) {
            onError(SpeechError.AudioCapture)
        }
    }

    override fun stopRecording(): ShortArray? {
        isRecordingState.set(false)
        try {
            audioRecord?.stop()
        } catch (_: Throwable) {
        } finally {
            try {
                audioRecord?.release()
            } catch (_: Throwable) {
            }
            audioRecord = null
        }

        try {
            recordingThread?.join(500)
        } catch (_: Throwable) {
        } finally {
            recordingThread = null
        }

        return synchronized(bufferLock) {
            if (pcmBuffer.isEmpty()) null else pcmBuffer.toShortArray()
        }
    }

    override fun isRecording(): Boolean = isRecordingState.get()

    override fun release() {
        isRecordingState.set(false)
        try {
            audioRecord?.stop()
        } catch (_: Throwable) {
        } finally {
            try {
                audioRecord?.release()
            } catch (_: Throwable) {
            }
            audioRecord = null
        }
        try {
            recordingThread?.join(300)
        } catch (_: Throwable) {
        } finally {
            recordingThread = null
        }
        synchronized(bufferLock) {
            pcmBuffer.clear()
        }
    }
}

/**
 * AndroidLocalSpeechTranscriber:
 * 100% on-device Whisper STT engine with JNI bindings to whisper.cpp.
 * Runs on Dispatchers.Default and chunks audio exceeding 30s.
 */
class AndroidLocalSpeechTranscriber(
    private val context: Context? = null
) : LocalSpeechTranscriber {

    companion object {
        private var isNativeLibraryLoaded = false
        init {
            try {
                System.loadLibrary("whisper")
                isNativeLibraryLoaded = true
            } catch (_: UnsatisfiedLinkError) {
                isNativeLibraryLoaded = false
            } catch (_: Throwable) {
                isNativeLibraryLoaded = false
            }
        }
    }

    // Native JNI functions for whisper.cpp bindings
    private external fun initModel(modelPath: String): Long
    private external fun freeModel(contextPtr: Long)
    private external fun fullTranscribe(
        contextPtr: Long,
        numThreads: Int,
        pcm: FloatArray,
        prompt: String,
        language: String,
        translate: Boolean
    ): String

    private var activeContextPtr: Long = 0L
    private val contextLock = Any()

    private fun getModelFile(fileName: String): File? {
        val appContext = context ?: AndroidContextHolder.context ?: return null
        val modelsDir = File(appContext.filesDir, "models")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
        val candidate = File(modelsDir, fileName)
        if (candidate.exists() && candidate.isFile && candidate.length() > 1000) return candidate

        // Auto-extract model bundled inside APK assets (for Google Play distribution)
        val assetPaths = listOf("models/$fileName", fileName, "models/ggml-tiny.bin", "ggml-tiny.bin")
        for (assetPath in assetPaths) {
            try {
                val tempFile = File(modelsDir, "$fileName.tmp")
                appContext.assets.open(assetPath).use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (tempFile.exists() && tempFile.length() > 1000) {
                    if (candidate.exists()) candidate.delete()
                    if (tempFile.renameTo(candidate) && candidate.exists()) {
                        android.util.Log.i("AndroidWhisper", "Successfully extracted model $fileName from assets ($assetPath)")
                        return candidate
                    }
                }
            } catch (_: Throwable) {
                // Asset path not found or extraction skipped, try next
            }
        }

        val rootCandidate = File(appContext.filesDir, fileName)
        if (rootCandidate.exists() && rootCandidate.isFile && rootCandidate.length() > 1000) return rootCandidate
        val extDir = appContext.getExternalFilesDir("models")
        if (extDir != null) {
            val extCandidate = File(extDir, fileName)
            if (extCandidate.exists() && extCandidate.isFile && extCandidate.length() > 1000) return extCandidate
        }
        return null
    }

    override fun isModelReady(): Boolean {
        if (!isNativeLibraryLoaded) return false
        val appContext = context ?: AndroidContextHolder.context ?: return false
        val modelsDir = File(appContext.filesDir, "models")
        val candidateQ5 = File(modelsDir, "ggml-tiny-q5_1.bin")
        val candidateTiny = File(modelsDir, "ggml-tiny.bin")
        if ((candidateQ5.exists() && candidateQ5.length() > 1000) ||
            (candidateTiny.exists() && candidateTiny.length() > 1000)
        ) {
            return true
        }

        return try {
            val assetsList = appContext.assets.list("models") ?: emptyArray()
            assetsList.contains("ggml-tiny-q5_1.bin") || assetsList.contains("ggml-tiny.bin")
        } catch (_: Throwable) {
            false
        }
    }

    override fun getInstalledModels(): List<InstalledModel> {
        val defaultModel = "ggml-tiny-q5_1.bin"
        val appContext = context ?: AndroidContextHolder.context
        val modelFile = appContext?.let { File(File(it.filesDir, "models"), defaultModel) }
        val isInstalled = modelFile != null && modelFile.exists() && modelFile.length() > 1000
        val size = if (isInstalled && modelFile != null) modelFile.length() else 31_000_000L
        return listOf(
            InstalledModel(
                id = "tiny",
                name = "Whisper Tiny (Offline)",
                fileName = defaultModel,
                sizeBytes = size,
                isInstalled = isInstalled,
                isDefault = true
            )
        )
    }

    override suspend fun transcribe(
        pcm16kMono: ShortArray,
        config: WhisperModelConfig
    ): TranscribeResult = withContext(Dispatchers.Default) {
        if (pcm16kMono.isEmpty()) {
            return@withContext TranscribeResult.Failure(SpeechError.EmptyResult)
        }

        if (!isNativeLibraryLoaded) {
            return@withContext TranscribeResult.Failure(SpeechError.ModelMissing)
        }

        val modelFile = withContext(Dispatchers.IO) {
            getModelFile(config.modelFileName)
        }
        if (modelFile == null || !modelFile.exists()) {
            return@withContext TranscribeResult.Failure(SpeechError.ModelMissing)
        }

        try {
            val ctxPtr = synchronized(contextLock) {
                if (activeContextPtr == 0L) {
                    activeContextPtr = initModel(modelFile.absolutePath)
                }
                activeContextPtr
            }

            if (ctxPtr == 0L) {
                return@withContext TranscribeResult.Failure(SpeechError.ModelLoadFailed)
            }

            // Audio chunking for recordings > 30s
            val chunks = AudioProcessor.chunkAudio(pcm16kMono, AudioProcessor.SAMPLES_PER_30S_CHUNK)
            val startTime = System.currentTimeMillis()
            val textBuilder = StringBuilder()

            for (chunk in chunks) {
                val floatPcm = FloatArray(chunk.size) { i -> chunk[i].toFloat() / 32768.0f }
                val segmentText = fullTranscribe(
                    contextPtr = ctxPtr,
                    numThreads = config.numThreads,
                    pcm = floatPcm,
                    prompt = config.initialPrompt,
                    language = config.language,
                    translate = config.translate
                )
                if (segmentText.isNotBlank()) {
                    if (textBuilder.isNotEmpty()) textBuilder.append(" ")
                    textBuilder.append(segmentText.trim())
                }
            }

            val fullText = textBuilder.toString().trim()
            val durationMs = System.currentTimeMillis() - startTime

            if (fullText.isBlank()) {
                TranscribeResult.Failure(SpeechError.EmptyResult)
            } else {
                TranscribeResult.Success(text = fullText, durationMs = durationMs)
            }
        } catch (_: OutOfMemoryError) {
            TranscribeResult.Failure(SpeechError.OutOfMemory)
        } catch (_: Throwable) {
            TranscribeResult.Failure(SpeechError.TranscriptionFailed)
        }
    }

    override fun release() {
        synchronized(contextLock) {
            if (activeContextPtr != 0L) {
                try {
                    freeModel(activeContextPtr)
                } catch (_: Throwable) {
                }
                activeContextPtr = 0L
            }
        }
    }
}

actual fun createLocalSpeechTranscriber(context: Any?): LocalSpeechTranscriber {
    val ctx = (context as? Context) ?: AndroidContextHolder.context
    return AndroidLocalSpeechTranscriber(ctx)
}

actual fun createRawAudioRecorder(context: Any?): RawAudioRecorder {
    val ctx = (context as? Context) ?: AndroidContextHolder.context
    return AndroidRawAudioRecorder(ctx)
}
