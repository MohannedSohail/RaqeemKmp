package org.mohtech.app.ai.audio

import android.Manifest
import android.app.Activity
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
 * AndroidVoiceBridge:
 * Bridges platform Activity capabilities (foreground Activity context, system speech dialog intent,
 * and permission launchers) with the cross-platform AndroidVoiceEngine without memory leaks.
 */
object AndroidVoiceBridge {
    /**
     * Delegate to retrieve the current foreground Activity context.
     * SpeechRecognizer requires an Activity context for proper AudioRecord attribution.
     */
    var activityProvider: (() -> Activity?)? = null

    /**
     * Delegate to launch system speech recognition dialog (Branch B fallback).
     * Invoked with (onResult: (String) -> Unit, onError: (String) -> Unit).
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
 * Handles Arabic speech recognition with safe mic hardware release, Activity context attribution,
 * and dual-branch fallback to eliminate the "insufficient permissions" error on Xiaomi HyperOS/MIUI devices.
 */
class AndroidVoiceEngine(private val context: Context) : VoiceEngine {

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Dual verification: verifies both in-process RecognitionService AND speech intent activity.
     * Uses flag 0 (instead of MATCH_DEFAULT_ONLY) so OEM speech recognition services
     * on Xiaomi HyperOS/MIUI are properly detected.
     */
    override fun isAvailable(): Boolean {
        return try {
            val hasService = try {
                if (SpeechRecognizer.isRecognitionAvailable(context)) {
                    true
                } else {
                    val intent = Intent(RecognitionService.SERVICE_INTERFACE)
                    val services = context.packageManager.queryIntentServices(intent, 0)
                    services.isNotEmpty()
                }
            } catch (_: Throwable) {
                false
            }

            val hasActivity = try {
                val activityIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                val activities = context.packageManager.queryIntentActivities(activityIntent, 0)
                activities.isNotEmpty()
            } catch (_: Throwable) {
                false
            }

            hasService || hasActivity || AndroidVoiceBridge.speechIntentLauncher != null
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

            // 2. Verify Speech Recognition Capability
            if (!isAvailable()) {
                onError("SPEECH_NOT_AVAILABLE")
                return@post
            }

            // Clean up any previous session before starting a new one
            releaseHardwareInternal()

            // Helper to trigger Branch B (System Speech Dialog Fallback)
            fun launchBranchB(fallbackReason: String) {
                releaseHardwareInternal()
                val launcher = AndroidVoiceBridge.speechIntentLauncher
                if (launcher != null) {
                    launcher(
                        { text ->
                            if (text.isNotBlank()) {
                                onResult(text)
                            } else {
                                onError("NO_MATCH")
                            }
                        },
                        { errorKey ->
                            onError(errorKey)
                        }
                    )
                } else {
                    onError(fallbackReason)
                }
            }

            // Check if in-process SpeechRecognizer service is available
            val canUseInProcess = try {
                SpeechRecognizer.isRecognitionAvailable(activeContext) ||
                    activeContext.packageManager.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0).isNotEmpty()
            } catch (_: Throwable) {
                false
            }

            if (!canUseInProcess) {
                // In-process service unavailable -> directly launch Branch B
                launchBranchB("SPEECH_NOT_AVAILABLE")
                return@post
            }

            // Branch A: Native in-process SpeechRecognizer using foreground Activity context
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(activeContext).apply {
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
                            val hasAppPermission = ContextCompat.checkSelfPermission(
                                activeContext,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED

                            // If the app genuinely does NOT have permission, report PERMISSION_DENIED
                            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS && !hasAppPermission) {
                                releaseHardwareInternal()
                                onError("PERMISSION_DENIED")
                                return
                            }

                            // If no speech heard or timeout:
                            if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                                releaseHardwareInternal()
                                onError(if (error == SpeechRecognizer.ERROR_NO_MATCH) "NO_MATCH" else "SPEECH_TIMEOUT")
                                return
                            }

                            // On Xiaomi HyperOS / MIUI OEM ROMs, any other error:
                            // (ERROR_INSUFFICIENT_PERMISSIONS despite app having permission, ERROR_CLIENT,
                            //  ERROR_RECOGNIZER_BUSY, ERROR_SERVER, ERROR_AUDIO, ERROR_NETWORK)
                            // indicates the in-process IPC service failed or was restricted.
                            // Seamlessly fallback to Branch B (System Speech Dialog) instead of deflecting to keyboard.
                            launchBranchB(
                                when (error) {
                                    SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
                                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
                                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
                                    SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
                                    SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
                                    else -> "ERROR_$error"
                                }
                            )
                        }

                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val text = matches?.firstOrNull()?.trim().orEmpty()
                            releaseHardwareInternal()
                            if (text.isNotBlank()) {
                                onResult(text)
                            } else {
                                onError("NO_MATCH")
                            }
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
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                // If creating or starting SpeechRecognizer throws, fallback to Branch B
                launchBranchB("CLIENT_ERROR: ${e.message}")
            }
        }
    }

    override fun stopListening() {
        mainHandler.post {
            try {
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
    return AndroidVoiceEngine(ctx)
}
