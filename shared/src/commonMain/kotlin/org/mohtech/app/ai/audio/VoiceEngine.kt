package org.mohtech.app.ai.audio

/**
 * VoiceEngine:
 * Platform-agnostic contract for audio voice capture and on-device Arabic speech-to-text.
 * Bridges platform speech recognizers (such as Android SpeechRecognizer) with MVI ExpenseViewModel.
 */
interface VoiceEngine {
    /**
     * Verifies if on-device speech recognition hardware and service are available.
     */
    fun isAvailable(): Boolean

    /**
     * Starts listening to audio input and delivers real-time amplitude and transcribed text.
     */
    fun startListening(
        onResult: (String) -> Unit,
        onAmplitudeChanged: (Float) -> Unit,
        onError: (String) -> Unit
    )

    /**
     * Stops listening and processes pending captured audio.
     */
    fun stopListening()

    /**
     * Releases underlying audio hardware and speech recognizer resources.
     */
    fun release()
}

/**
 * Platform factory to instantiate the platform-backed VoiceEngine.
 */
expect fun createVoiceEngine(context: Any? = null): VoiceEngine?
