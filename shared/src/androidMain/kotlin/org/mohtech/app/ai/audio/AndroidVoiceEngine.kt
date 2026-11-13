package org.mohtech.app.ai.audio

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import org.mohtech.app.data.storage.AndroidContextHolder

/**
 * AndroidVoiceEngine:
 * Handles offline on-device Arabic speech recognition with safe mic hardware release
 * and package availability validation to avoid crashes on Xiaomi/HyperOS/Huawei devices.
 * Implements the cross-platform VoiceEngine interface.
 */
class AndroidVoiceEngine(private val context: Context) : VoiceEngine {

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Dual verification: verifies both system capability AND existence of an active RecognitionService.
     * Wrapped in defensive try/catch to protect against OEM ROM exceptions (DeadSystemException, SecurityException).
     */
    override fun isAvailable(): Boolean {
        return try {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                return false
            }
            val intent = Intent(RecognitionService.SERVICE_INTERFACE)
            val services = context.packageManager.queryIntentServices(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
            services.isNotEmpty()
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
            // 1. Verify Audio Recording Permission
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                onError("PERMISSION_DENIED")
                return@post
            }

            // 2. Verify Speech Recognition Service Availability
            if (!isAvailable()) {
                onError("SPEECH_NOT_AVAILABLE")
                return@post
            }

            // 3. Clean up any previous session before starting a new one
            releaseHardwareInternal()

            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {}
                        override fun onBeginningOfSpeech() {}

                        override fun onRmsChanged(rmsdB: Float) {
                            // Normalize -2dB..10dB to 0.0..1.0
                            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                            onAmplitudeChanged(normalized)
                        }

                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            onAmplitudeChanged(0f)
                        }

                        override fun onError(error: Int) {
                            releaseHardwareInternal()
                            val errorKey = when (error) {
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "PERMISSION_DENIED"
                                SpeechRecognizer.ERROR_NO_MATCH -> "NO_MATCH"
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "SPEECH_TIMEOUT"
                                SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
                                SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
                                SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
                                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
                                SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
                                else -> "ERROR_$error"
                            }
                            onError(errorKey)
                        }

                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val text = matches?.firstOrNull() ?: ""
                            releaseHardwareInternal()
                            onResult(text)
                        }

                        override fun onPartialResults(partialResults: Bundle?) {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar")
                    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("ar-SA", "ar-EG", "ar-PS", "ar-JO"))
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                releaseHardwareInternal()
                onError("CLIENT_ERROR: ${e.message}")
            }
        }
    }

    override fun stopListening() {
        mainHandler.post {
            try {
                // stopListening allows recognition to process buffered speech and invoke onResults
                speechRecognizer?.stopListening()
            } catch (_: Exception) {
            }
        }
    }

    private fun releaseHardwareInternal() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        } finally {
            speechRecognizer = null
        }
    }

    override fun release() {
        mainHandler.post {
            releaseHardwareInternal()
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
    return AndroidVoiceEngine(ctx.applicationContext)
}
