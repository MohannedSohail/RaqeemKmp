package org.mohtech.app.ai.audio

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.mohtech.app.ai.whisper.AndroidLocalSpeechTranscriber
import org.mohtech.app.ai.whisper.AndroidRawAudioRecorder
import org.mohtech.app.ai.whisper.SpeechError
import org.mohtech.app.ai.whisper.TranscribeResult
import org.mohtech.app.data.storage.AndroidContextHolder

/**
 * AndroidVoiceBridge:
 * Bridges platform Activity capabilities (foreground Activity context
 * and runtime mic permission launcher) with AndroidVoiceEngine without memory leaks.
 */
object AndroidVoiceBridge {
    /**
     * Delegate to retrieve the current foreground Activity context.
     */
    var activityProvider: (() -> Activity?)? = null

    /**
     * Legacy speech intent delegate slot (kept for backward compatibility, not used in offline-first mode).
     */
    var speechIntentLauncher: ((onResult: (String) -> Unit, onError: (String) -> Unit) -> Unit)? = null

    /**
     * Delegate to launch runtime RECORD_AUDIO permission request.
     * Invoked with (onGranted: () -> Unit, onDenied: () -> Unit).
     */
    var micPermissionLauncher: ((onGranted: () -> Unit, onDenied: () -> Unit) -> Unit)? = null
}

/**
 * AndroidVoiceEngine:
 * 100% offline Arabic voice capture and speech processing engine backed by
 * on-device AndroidRawAudioRecorder and local Whisper STT.
 */
class AndroidVoiceEngine(private val context: Context) : VoiceEngine {

    private var audioRecorder: AndroidRawAudioRecorder? = null
    private var transcriber: AndroidLocalSpeechTranscriber? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var engineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var activeOnResult: ((String) -> Unit)? = null
    private var activeOnError: ((String) -> Unit)? = null

    override fun isAvailable(): Boolean {
        return try {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Backwards-compatible alias for isAvailable.
     */
    fun isHardwareRecognitionAvailable(): Boolean = isAvailable()

    override fun startListening(
        onResult: (String) -> Unit,
        onAmplitudeChanged: (Float) -> Unit,
        onError: (String) -> Unit
    ) {
        mainHandler.post {
            val activeContext: Context = AndroidVoiceBridge.activityProvider?.invoke() ?: context

            // 1. Verify Audio Recording Permission
            if (ContextCompat.checkSelfPermission(
                    activeContext,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                val bridgePermission = AndroidVoiceBridge.micPermissionLauncher
                if (bridgePermission != null) {
                    bridgePermission(
                        {
                            // Permission granted: resume startListening
                            startListening(onResult, onAmplitudeChanged, onError)
                        },
                        {
                            onError("PERMISSION_DENIED")
                        }
                    )
                } else {
                    onError("PERMISSION_DENIED")
                }
                return@post
            }

            // Clean up any previous session before starting a new one
            releaseHardwareInternal()

            activeOnResult = onResult
            activeOnError = onError

            val recorder = AndroidRawAudioRecorder(activeContext)
            audioRecorder = recorder

            if (transcriber == null) {
                transcriber = AndroidLocalSpeechTranscriber(activeContext)
            }

            recorder.startRecording(
                onAmplitudeChanged = { amplitude ->
                    mainHandler.post {
                        onAmplitudeChanged(amplitude)
                    }
                },
                onError = { speechError ->
                    mainHandler.post {
                        when (speechError) {
                            SpeechError.PermissionDenied -> onError("PERMISSION_DENIED")
                            SpeechError.ModelMissing -> onError("MODEL_MISSING")
                            SpeechError.EmptyResult -> onError("NO_MATCH")
                            else -> onError("AUDIO_ERROR")
                        }
                    }
                }
            )
        }
    }

    override fun stopListening() {
        mainHandler.post {
            val recorder = audioRecorder
            val pcm = recorder?.stopRecording()
            audioRecorder = null

            val onResult = activeOnResult
            val onError = activeOnError
            activeOnResult = null
            activeOnError = null

            if (pcm == null || pcm.isEmpty()) {
                onError?.invoke("NO_MATCH")
                return@post
            }

            val localTranscriber = transcriber ?: AndroidLocalSpeechTranscriber(context).also { transcriber = it }

            engineScope.launch {
                val result = localTranscriber.transcribe(pcm)
                when (result) {
                    is TranscribeResult.Success -> {
                        if (result.text.isNotBlank()) {
                            onResult?.invoke(result.text)
                        } else {
                            onError?.invoke("NO_MATCH")
                        }
                    }
                    is TranscribeResult.Failure -> {
                        when (result.error) {
                            SpeechError.EmptyResult -> onError?.invoke("NO_MATCH")
                            SpeechError.PermissionDenied -> onError?.invoke("PERMISSION_DENIED")
                            SpeechError.ModelMissing -> onError?.invoke("MODEL_MISSING")
                            else -> onError?.invoke("TRANSCRIPTION_FAILED")
                        }
                    }
                }
            }
        }
    }

    private fun releaseHardwareInternal() {
        try {
            audioRecorder?.stopRecording()
            audioRecorder?.release()
        } catch (_: Throwable) {
        } finally {
            audioRecorder = null
        }
        activeOnResult = null
        activeOnError = null
    }

    override fun release() {
        mainHandler.post {
            releaseHardwareInternal()
            transcriber?.release()
            transcriber = null
            engineScope.cancel()
            engineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        }
    }

    fun releaseHardware() {
        release()
    }
}

/**
 * Android factory for VoiceEngine.
 */
actual fun createVoiceEngine(context: Any?): VoiceEngine? {
    val ctx = (context as? Context) ?: AndroidContextHolder.context ?: return null
    return AndroidVoiceEngine(ctx)
}
