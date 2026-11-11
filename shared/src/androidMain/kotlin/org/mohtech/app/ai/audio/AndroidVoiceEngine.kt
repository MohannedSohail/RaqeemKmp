package org.mohtech.app.ai.audio

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * AndroidVoiceEngine:
 * Handles offline on-device Arabic speech recognition with safe mic hardware release
 * and package availability validation to avoid crashes on Xiaomi/HyperOS/Huawei devices.
 */
class AndroidVoiceEngine(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null

    /**
     * Dual verification: verifies both system capability AND existence of an active RecognitionService.
     */
    fun isHardwareRecognitionAvailable(): Boolean {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            return false
        }
        val intent = Intent(RecognitionService.SERVICE_INTERFACE)
        val services = context.packageManager.queryIntentServices(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        return services.isNotEmpty()
    }

    fun startListening(
        onResult: (String) -> Unit,
        onAmplitudeChanged: (Float) -> Unit,
        onError: (Int) -> Unit
    ) {
        releaseHardware()

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
                        releaseHardware()
                        onError(error)
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        releaseHardware()
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
            releaseHardware()
            onError(SpeechRecognizer.ERROR_CLIENT)
        }
    }

    fun releaseHardware() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        } finally {
            speechRecognizer = null
        }
    }
}

