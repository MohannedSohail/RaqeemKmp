package org.mohtech.app.ai.whisper

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FakeTranscriberTest {

    @Test
    fun testFakeTranscriberSuccessFlow() = runTest {
        val transcriber = FakeTranscriber(transcribeResultText = "بنزين ب 50 شيكل")
        val pcm = ShortArray(16000) { 150 }

        val result = transcriber.transcribe(pcm, WhisperModelConfig())

        assertIs<TranscribeResult.Success>(result)
        assertEquals("بنزين ب 50 شيكل", result.text)
        assertEquals(1, transcriber.transcribeCount)
        assertEquals(pcm, transcriber.lastTranscribedPcm)
    }

    @Test
    fun testFakeTranscriberModelMissingError() = runTest {
        val transcriber = FakeTranscriber(
            shouldFailWith = SpeechError.ModelMissing,
            isModelReadyState = false
        )
        assertFalse(transcriber.isModelReady())

        val result = transcriber.transcribe(ShortArray(16000), WhisperModelConfig())

        assertIs<TranscribeResult.Failure>(result)
        assertEquals(SpeechError.ModelMissing, result.error)
    }

    @Test
    fun testFakeTranscriberEmptyResultError() = runTest {
        val transcriber = FakeTranscriber(transcribeResultText = "   ")

        val result = transcriber.transcribe(ShortArray(16000), WhisperModelConfig())

        assertIs<TranscribeResult.Failure>(result)
        assertEquals(SpeechError.EmptyResult, result.error)
    }

    @Test
    fun testFakeTranscriberPermissionDeniedError() = runTest {
        val transcriber = FakeTranscriber(shouldFailWith = SpeechError.PermissionDenied)

        val result = transcriber.transcribe(ShortArray(16000), WhisperModelConfig())

        assertIs<TranscribeResult.Failure>(result)
        assertEquals(SpeechError.PermissionDenied, result.error)
    }

    @Test
    fun testFakeAudioRecorderLifecycleAndAmplitude() {
        val recorder = FakeAudioRecorder()
        var receivedAmplitude = -1f
        var receivedError: SpeechError? = null

        assertFalse(recorder.isRecording())

        recorder.startRecording(
            onAmplitudeChanged = { receivedAmplitude = it },
            onError = { receivedError = it }
        )

        assertTrue(recorder.isRecording())
        assertEquals(1, recorder.startRecordingCount)

        recorder.emitAmplitude(0.65f)
        assertEquals(0.65f, receivedAmplitude)

        val captured = recorder.stopRecording()
        assertFalse(recorder.isRecording())
        assertEquals(1, recorder.stopRecordingCount)
        assertNotNull(captured)
        assertEquals(16000, captured.size)

        recorder.release()
        assertEquals(1, recorder.releaseCount)
    }

    @Test
    fun testFakeAudioRecorderPermissionDeniedOnStart() {
        val recorder = FakeAudioRecorder(errorToEmitOnStart = SpeechError.PermissionDenied)
        var receivedError: SpeechError? = null

        recorder.startRecording(
            onAmplitudeChanged = {},
            onError = { receivedError = it }
        )

        assertFalse(recorder.isRecording())
        assertEquals(SpeechError.PermissionDenied, receivedError)
    }
}
