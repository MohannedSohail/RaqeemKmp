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
    private val pcmChunks = mutableListOf<ShortArray>()
    private val bufferLock = Any()

    override fun startRecording(
        onAmplitudeChanged: (Float) -> Unit,
        onError: (SpeechError) -> Unit
    ) {
        if (!isRecordingState.compareAndSet(false, true)) {
            // Already recording; ignore redundant start to prevent hardware contention
            return
        }

        val appContext = context ?: AndroidContextHolder.context
        if (appContext != null &&
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            isRecordingState.set(false)
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
                isRecordingState.set(false)
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
                isRecordingState.set(false)
                onError(SpeechError.AudioCapture)
                return
            }

            audioRecord = record
            synchronized(bufferLock) {
                pcmChunks.clear()
            }
            record.startRecording()

            recordingThread = thread(name = "Raqeem-AudioRecord-Thread") {
                val readBuffer = ShortArray(1600) // 100ms at 16kHz
                try {
                    while (isRecordingState.get()) {
                        val readCount = record.read(readBuffer, 0, readBuffer.size)
                        if (readCount > 0) {
                            val chunk = if (readCount == readBuffer.size) {
                                readBuffer.clone()
                            } else {
                                readBuffer.copyOf(readCount)
                            }
                            synchronized(bufferLock) {
                                pcmChunks.add(chunk)
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
            isRecordingState.set(false)
            try { audioRecord?.release() } catch (_: Throwable) {}
            audioRecord = null
            onError(SpeechError.PermissionDenied)
        } catch (_: Throwable) {
            isRecordingState.set(false)
            try { audioRecord?.release() } catch (_: Throwable) {}
            audioRecord = null
            onError(SpeechError.AudioCapture)
        }
    }

    override fun stopRecording(): ShortArray? {
        isRecordingState.set(false)
        try {
            audioRecord?.stop()
        } catch (_: Throwable) {
        }

        try {
            recordingThread?.join(500)
        } catch (_: Throwable) {
        } finally {
            recordingThread = null
        }

        try {
            audioRecord?.release()
        } catch (_: Throwable) {
        } finally {
            audioRecord = null
        }

        return synchronized(bufferLock) {
            if (pcmChunks.isEmpty()) {
                null
            } else {
                val totalSamples = pcmChunks.sumOf { it.size }
                val fullPcm = ShortArray(totalSamples)
                var offset = 0
                for (chunk in pcmChunks) {
                    chunk.copyInto(fullPcm, destinationOffset = offset)
                    offset += chunk.size
                }
                pcmChunks.clear()
                fullPcm
            }
        }
    }

    override fun isRecording(): Boolean = isRecordingState.get()

    override fun release() {
        isRecordingState.set(false)
        try {
            audioRecord?.stop()
        } catch (_: Throwable) {
        }
        try {
            recordingThread?.join(300)
        } catch (_: Throwable) {
        } finally {
            recordingThread = null
        }
        try {
            audioRecord?.release()
        } catch (_: Throwable) {
        } finally {
            audioRecord = null
        }
        synchronized(bufferLock) {
            pcmChunks.clear()
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
        @Volatile
        private var isNativeLibraryLoaded = false
        private var hasAttemptedLoad = false
        private val modelExtractionLock = Any()
        const val MIN_MODEL_SIZE_BYTES = 10_000_000L // GGML tiny is ~31MB

        fun ensureNativeLibraryLoaded(): Boolean {
            if (isNativeLibraryLoaded) return true
            synchronized(this) {
                if (isNativeLibraryLoaded) return true
                try {
                    System.loadLibrary("whisper")
                    isNativeLibraryLoaded = true
                } catch (_: UnsatisfiedLinkError) {
                    isNativeLibraryLoaded = false
                } catch (_: Throwable) {
                    isNativeLibraryLoaded = false
                }
                hasAttemptedLoad = true
                return isNativeLibraryLoaded
            }
        }

        init {
            ensureNativeLibraryLoaded()
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

        // Check if candidate or alternative tiny models already exist on disk
        val candidate = File(modelsDir, fileName)
        val candidatesToCheck = listOf(
            candidate,
            File(modelsDir, "ggml-tiny-q5_1.bin"),
            File(modelsDir, "ggml-tiny.bin")
        )
        for (cand in candidatesToCheck) {
            if (cand.exists() && cand.isFile && cand.length() >= MIN_MODEL_SIZE_BYTES) {
                return cand
            }
        }

        // Synchronize extraction so concurrent threads do not conflict
        synchronized(modelExtractionLock) {
            // Re-check after acquiring lock in case another thread already extracted it
            for (cand in candidatesToCheck) {
                if (cand.exists() && cand.isFile && cand.length() >= MIN_MODEL_SIZE_BYTES) {
                    return cand
                }
            }

            // Auto-extract model bundled inside APK assets (for Google Play distribution)
            val assetPaths = linkedSetOf(
                "models/$fileName",
                fileName,
                "models/ggml-tiny-q5_1.bin",
                "ggml-tiny-q5_1.bin",
                "models/ggml-tiny.bin",
                "ggml-tiny.bin"
            )
            for (assetPath in assetPaths) {
                val tempFile = File(modelsDir, "$fileName.${System.currentTimeMillis()}.${(1000..9999).random()}.tmp")
                try {
                    appContext.assets.open(assetPath).use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (tempFile.exists() && tempFile.length() >= MIN_MODEL_SIZE_BYTES) {
                        if (candidate.exists()) candidate.delete()
                        val renameSuccess = tempFile.renameTo(candidate)
                        if (!renameSuccess || !candidate.exists()) {
                            tempFile.copyTo(candidate, overwrite = true)
                        }
                        if (candidate.exists() && candidate.length() >= MIN_MODEL_SIZE_BYTES) {
                            android.util.Log.i("AndroidWhisper", "Successfully extracted model from assets ($assetPath)")
                            return candidate
                        }
                    }
                } catch (_: Throwable) {
                    // Asset path not found or extraction skipped, try next
                } finally {
                    try {
                        if (tempFile.exists()) tempFile.delete()
                    } catch (_: Throwable) {}
                }
            }

            val rootCandidate = File(appContext.filesDir, fileName)
            if (rootCandidate.exists() && rootCandidate.isFile && rootCandidate.length() >= MIN_MODEL_SIZE_BYTES) return rootCandidate
            val extDir = appContext.getExternalFilesDir("models")
            if (extDir != null) {
                val extCandidate = File(extDir, fileName)
                if (extCandidate.exists() && extCandidate.isFile && extCandidate.length() >= MIN_MODEL_SIZE_BYTES) return extCandidate
            }
            return null
        }
    }

    override fun isModelReady(): Boolean {
        if (!isNativeLibraryLoaded && !ensureNativeLibraryLoaded()) return false
        val appContext = context ?: AndroidContextHolder.context ?: return false
        val modelsDir = File(appContext.filesDir, "models")
        val candidateQ5 = File(modelsDir, "ggml-tiny-q5_1.bin")
        val candidateTiny = File(modelsDir, "ggml-tiny.bin")
        if ((candidateQ5.exists() && candidateQ5.length() >= MIN_MODEL_SIZE_BYTES) ||
            (candidateTiny.exists() && candidateTiny.length() >= MIN_MODEL_SIZE_BYTES)
        ) {
            return true
        }

        val assetCandidates = listOf(
            "models/ggml-tiny-q5_1.bin",
            "ggml-tiny-q5_1.bin",
            "models/ggml-tiny.bin",
            "ggml-tiny.bin"
        )
        for (path in assetCandidates) {
            try {
                appContext.assets.open(path).use {
                    // Pre-extract in background so first user recording encounters zero latency
                    thread(name = "Raqeem-Model-Extractor", isDaemon = true) {
                        try {
                            getModelFile("ggml-tiny-q5_1.bin")
                        } catch (_: Throwable) {}
                    }
                    return true
                }
            } catch (_: Throwable) {
            }
        }
        return false
    }

    override fun getInstalledModels(): List<InstalledModel> {
        val defaultModel = "ggml-tiny-q5_1.bin"
        val appContext = context ?: AndroidContextHolder.context
        val modelFile = appContext?.let { File(File(it.filesDir, "models"), defaultModel) }
        val isInstalled = modelFile != null && modelFile.exists() && modelFile.length() >= MIN_MODEL_SIZE_BYTES
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

        // Apply Voice Activity Detection (VAD)
        val vad = org.mohtech.app.ai.audio.VoiceActivityDetector()
        val vadResult = vad.process(pcm16kMono)
        val activePcm = when (vadResult) {
            is org.mohtech.app.ai.audio.VadResult.Speech -> vadResult.trimmedPcm
            is org.mohtech.app.ai.audio.VadResult.NoSpeech -> pcm16kMono
        }

        if (!isNativeLibraryLoaded && !ensureNativeLibraryLoaded()) {
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
            val chunks = AudioProcessor.chunkAudio(activePcm, AudioProcessor.SAMPLES_PER_30S_CHUNK)
            val startTime = System.currentTimeMillis()
            val textBuilder = StringBuilder()

            for (chunk in chunks) {
                val floatPcm = FloatArray(chunk.size) { i -> chunk[i].toFloat() / 32768.0f }
                val segmentText = synchronized(contextLock) {
                    if (activeContextPtr == 0L) {
                        ""
                    } else {
                        fullTranscribe(
                            contextPtr = activeContextPtr,
                            numThreads = config.numThreads,
                            pcm = floatPcm,
                            prompt = config.initialPrompt,
                            language = config.language,
                            translate = config.translate
                        )
                    }
                }
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
