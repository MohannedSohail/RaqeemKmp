// VoicePipelineFallbackTest: Guarantees fallback manual sheet on speech/recording failures
package org.mohtech.app.feature.dashboard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.mohtech.app.ai.nlp.LocalDialectExpenseParser
import org.mohtech.app.ai.whisper.FakeAudioRecorder
import org.mohtech.app.ai.whisper.FakeTranscriber
import org.mohtech.app.ai.whisper.SpeechError
import org.mohtech.app.data.repository.LocalExpenseRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class VoicePipelineFallbackTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var storage: FakePlatformExpenseStorage
    private lateinit var preferences: FakeAppPreferences
    private lateinit var repository: LocalExpenseRepository
    private lateinit var parser: LocalDialectExpenseParser

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        storage = FakePlatformExpenseStorage()
        preferences = FakeAppPreferences()
        repository = LocalExpenseRepository(storage, preferences)
        parser = LocalDialectExpenseParser()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testEmptyResultGuaranteesFallbackSheetOpen() = runTest(testDispatcher) {
        val fakeTranscriber = FakeTranscriber(shouldFailWith = SpeechError.EmptyResult)
        val fakeRecorder = FakeAudioRecorder()
        val viewModel = ExpenseViewModel(
            repository = repository,
            preferences = preferences,
            parser = parser,
            transcriber = fakeTranscriber,
            audioRecorder = fakeRecorder
        )
        advanceUntilIdle()

        viewModel.handleIntent(ExpenseIntent.CheckPermissionsAndStartRecord)
        advanceUntilIdle()
        viewModel.handleIntent(ExpenseIntent.StopRecording)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isRecording)
        assertNull(state.reviewSheetPendingBatch)
        assertTrue(state.showFallbackManualSheet, "showFallbackManualSheet must be true on EmptyResult")
    }

    @Test
    fun testModelMissingGuaranteesFallbackSheetOpen() = runTest(testDispatcher) {
        val fakeTranscriber = FakeTranscriber(
            shouldFailWith = SpeechError.ModelMissing,
            isModelReadyState = false
        )
        val fakeRecorder = FakeAudioRecorder()
        val viewModel = ExpenseViewModel(
            repository = repository,
            preferences = preferences,
            parser = parser,
            transcriber = fakeTranscriber,
            audioRecorder = fakeRecorder
        )
        advanceUntilIdle()

        viewModel.handleIntent(ExpenseIntent.CheckPermissionsAndStartRecord)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isRecording)
        assertNull(state.reviewSheetPendingBatch)
        assertTrue(state.showFallbackManualSheet, "showFallbackManualSheet must be true on ModelMissing")
    }

    @Test
    fun testTranscriptionFailedGuaranteesFallbackSheetOpen() = runTest(testDispatcher) {
        val fakeTranscriber = FakeTranscriber(shouldFailWith = SpeechError.TranscriptionFailed)
        val fakeRecorder = FakeAudioRecorder()
        val viewModel = ExpenseViewModel(
            repository = repository,
            preferences = preferences,
            parser = parser,
            transcriber = fakeTranscriber,
            audioRecorder = fakeRecorder
        )
        advanceUntilIdle()

        viewModel.handleIntent(ExpenseIntent.CheckPermissionsAndStartRecord)
        advanceUntilIdle()
        viewModel.handleIntent(ExpenseIntent.StopRecording)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isRecording)
        assertNull(state.reviewSheetPendingBatch)
        assertTrue(state.showFallbackManualSheet, "showFallbackManualSheet must be true on TranscriptionFailed")
    }

    @Test
    fun testAudioCaptureErrorGuaranteesFallbackSheetOpen() = runTest(testDispatcher) {
        val fakeTranscriber = FakeTranscriber()
        val fakeRecorder = FakeAudioRecorder(errorToEmitOnStart = SpeechError.AudioCapture)
        val viewModel = ExpenseViewModel(
            repository = repository,
            preferences = preferences,
            parser = parser,
            transcriber = fakeTranscriber,
            audioRecorder = fakeRecorder
        )
        advanceUntilIdle()

        viewModel.handleIntent(ExpenseIntent.CheckPermissionsAndStartRecord)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isRecording)
        assertNull(state.reviewSheetPendingBatch)
        assertTrue(state.showFallbackManualSheet, "showFallbackManualSheet must be true on AudioCapture error")
    }

    @Test
    fun testOutOfMemoryErrorGuaranteesFallbackSheetOpen() = runTest(testDispatcher) {
        val fakeTranscriber = FakeTranscriber(shouldFailWith = SpeechError.OutOfMemory)
        val fakeRecorder = FakeAudioRecorder()
        val viewModel = ExpenseViewModel(
            repository = repository,
            preferences = preferences,
            parser = parser,
            transcriber = fakeTranscriber,
            audioRecorder = fakeRecorder
        )
        advanceUntilIdle()

        viewModel.handleIntent(ExpenseIntent.CheckPermissionsAndStartRecord)
        advanceUntilIdle()
        viewModel.handleIntent(ExpenseIntent.StopRecording)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isRecording)
        assertNull(state.reviewSheetPendingBatch)
        assertTrue(state.showFallbackManualSheet, "showFallbackManualSheet must be true on OutOfMemory")
    }

    @Test
    fun testUnknownErrorGuaranteesFallbackSheetOpen() = runTest(testDispatcher) {
        val fakeTranscriber = FakeTranscriber(shouldFailWith = SpeechError.Unknown("unexpected native crash"))
        val fakeRecorder = FakeAudioRecorder()
        val viewModel = ExpenseViewModel(
            repository = repository,
            preferences = preferences,
            parser = parser,
            transcriber = fakeTranscriber,
            audioRecorder = fakeRecorder
        )
        advanceUntilIdle()

        viewModel.handleIntent(ExpenseIntent.CheckPermissionsAndStartRecord)
        advanceUntilIdle()
        viewModel.handleIntent(ExpenseIntent.StopRecording)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isRecording)
        assertNull(state.reviewSheetPendingBatch)
        assertTrue(state.showFallbackManualSheet, "showFallbackManualSheet must be true on Unknown error")
    }

    @Test
    fun testVadSilenceGuaranteesFallbackSheetOpen() = runTest(testDispatcher) {
        // Silent PCM buffer (all zeros) will trigger VadResult.NoSpeech
        val silentPcm = ShortArray(16000 * 2) { 0 }
        val fakeRecorder = FakeAudioRecorder(recordedPcm = silentPcm)
        val fakeTranscriber = FakeTranscriber()
        val viewModel = ExpenseViewModel(
            repository = repository,
            preferences = preferences,
            parser = parser,
            transcriber = fakeTranscriber,
            audioRecorder = fakeRecorder
        )
        advanceUntilIdle()

        viewModel.handleIntent(ExpenseIntent.CheckPermissionsAndStartRecord)
        advanceUntilIdle()
        viewModel.handleIntent(ExpenseIntent.StopRecording)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isRecording)
        assertNull(state.reviewSheetPendingBatch)
        assertTrue(state.showFallbackManualSheet, "showFallbackManualSheet must be true on silence (VAD NoSpeech)")
    }

    @Test
    fun testDynamicTimeoutTriggersFallbackSheetWithCalmExplanation() = runTest(testDispatcher) {
        // Transcriber delays 20 seconds, exceeding maximum dynamic timeout (15s)
        val fakeTranscriber = FakeTranscriber(delayMs = 20_000L)
        val fakeRecorder = FakeAudioRecorder()
        val viewModel = ExpenseViewModel(
            repository = repository,
            preferences = preferences,
            parser = parser,
            transcriber = fakeTranscriber,
            audioRecorder = fakeRecorder
        )
        advanceUntilIdle()

        viewModel.handleIntent(ExpenseIntent.CheckPermissionsAndStartRecord)
        advanceUntilIdle()
        viewModel.handleIntent(ExpenseIntent.StopRecording)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isRecording)
        assertNull(state.reviewSheetPendingBatch)
        assertTrue(state.showFallbackManualSheet, "Dynamic timeout must display fallback manual sheet")
        assertNotNull(state.userErrorMessage, "Calm explanation must be provided on timeout")
    }

    @Test
    fun testPermissionDeniedGuaranteesFallbackSheetAndRationale() = runTest(testDispatcher) {
        val fakeTranscriber = FakeTranscriber()
        val fakeRecorder = FakeAudioRecorder(errorToEmitOnStart = SpeechError.PermissionDenied)
        val viewModel = ExpenseViewModel(
            repository = repository,
            preferences = preferences,
            parser = parser,
            transcriber = fakeTranscriber,
            audioRecorder = fakeRecorder
        )
        advanceUntilIdle()

        viewModel.handleIntent(ExpenseIntent.CheckPermissionsAndStartRecord)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isRecording)
        assertTrue(state.showFallbackManualSheet, "showFallbackManualSheet must be true on PermissionDenied")
        assertTrue(state.showPermissionRationaleDialog, "showPermissionRationaleDialog must be true")
        assertTrue(state.showPermissionRationaleSheet, "showPermissionRationaleSheet must be true")
    }

    @Test
    fun testValidTranscriptionGuaranteesReviewSheetWithoutFallback() = runTest(testDispatcher) {
        val fakeTranscriber = FakeTranscriber(transcribeResultText = "دفعت خمسين شيكل بنزين")
        val fakeRecorder = FakeAudioRecorder()
        val viewModel = ExpenseViewModel(
            repository = repository,
            preferences = preferences,
            parser = parser,
            transcriber = fakeTranscriber,
            audioRecorder = fakeRecorder
        )
        advanceUntilIdle()

        viewModel.handleIntent(ExpenseIntent.CheckPermissionsAndStartRecord)
        advanceUntilIdle()
        viewModel.handleIntent(ExpenseIntent.StopRecording)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isRecording)
        assertNotNull(state.reviewSheetPendingBatch, "reviewSheetPendingBatch must be present on valid speech")
        assertFalse(state.showFallbackManualSheet, "showFallbackManualSheet must be false when review sheet is active")
    }

    @Test
    fun testAudioRecorderAndTranscriberSafeResourceRelease() = runTest(testDispatcher) {
        val fakeTranscriber = FakeTranscriber()
        val fakeRecorder = FakeAudioRecorder()
        val viewModel = ExpenseViewModel(
            repository = repository,
            preferences = preferences,
            parser = parser,
            transcriber = fakeTranscriber,
            audioRecorder = fakeRecorder
        )
        advanceUntilIdle()

        assertEquals(0, fakeRecorder.releaseCount)
        assertEquals(0, fakeTranscriber.releaseCount)

        viewModel.onCleared()

        assertEquals(1, fakeRecorder.releaseCount, "AudioRecorder must be released safely in onCleared")
        assertEquals(1, fakeTranscriber.releaseCount, "Transcriber must be released safely in onCleared")
    }

    @Test
    fun testModelLoadFailedGuaranteesFallbackSheetOpen() = runTest(testDispatcher) {
        val fakeTranscriber = FakeTranscriber(shouldFailWith = SpeechError.ModelLoadFailed)
        val fakeRecorder = FakeAudioRecorder()
        val viewModel = ExpenseViewModel(
            repository = repository,
            preferences = preferences,
            parser = parser,
            transcriber = fakeTranscriber,
            audioRecorder = fakeRecorder
        )
        advanceUntilIdle()

        viewModel.handleIntent(ExpenseIntent.CheckPermissionsAndStartRecord)
        advanceUntilIdle()
        viewModel.handleIntent(ExpenseIntent.StopRecording)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isRecording)
        assertNull(state.reviewSheetPendingBatch)
        assertTrue(state.showFallbackManualSheet, "showFallbackManualSheet must be true on ModelLoadFailed")
    }

    @Test
    fun testInitializationFailedGuaranteesFallbackSheetOpen() = runTest(testDispatcher) {
        val fakeTranscriber = FakeTranscriber(shouldFailWith = SpeechError.InitializationFailed)
        val fakeRecorder = FakeAudioRecorder()
        val viewModel = ExpenseViewModel(
            repository = repository,
            preferences = preferences,
            parser = parser,
            transcriber = fakeTranscriber,
            audioRecorder = fakeRecorder
        )
        advanceUntilIdle()

        viewModel.handleIntent(ExpenseIntent.CheckPermissionsAndStartRecord)
        advanceUntilIdle()
        viewModel.handleIntent(ExpenseIntent.StopRecording)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isRecording)
        assertNull(state.reviewSheetPendingBatch)
        assertTrue(state.showFallbackManualSheet, "showFallbackManualSheet must be true on InitializationFailed")
    }

    @Test
    fun testAudioRecorderStoppedAndReleasedWhenClearedWhileRecording() = runTest(testDispatcher) {
        val fakeTranscriber = FakeTranscriber()
        val fakeRecorder = FakeAudioRecorder()
        val viewModel = ExpenseViewModel(
            repository = repository,
            preferences = preferences,
            parser = parser,
            transcriber = fakeTranscriber,
            audioRecorder = fakeRecorder
        )
        advanceUntilIdle()

        viewModel.handleIntent(ExpenseIntent.CheckPermissionsAndStartRecord)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isRecording)

        viewModel.onCleared()

        assertEquals(1, fakeRecorder.stopRecordingCount, "stopRecording must be called if recording was active")
        assertEquals(1, fakeRecorder.releaseCount, "release must be called in finally on onCleared")
        assertEquals(1, fakeTranscriber.releaseCount, "Transcriber release must be called on onCleared")
    }

    @Test
    fun testVoiceEngineErrorGuaranteesFallbackSheetOpenAndStopsRecording() = runTest(testDispatcher) {
        val fakeVoiceEngine = FakeVoiceEngine(available = true)
        val viewModel = ExpenseViewModel(
            repository = repository,
            preferences = preferences,
            parser = parser,
            voiceEngine = fakeVoiceEngine
        )
        advanceUntilIdle()

        viewModel.handleIntent(ExpenseIntent.CheckPermissionsAndStartRecord)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isRecording)

        fakeVoiceEngine.emitError("AUDIO_ERROR")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isRecording, "Recording must stop on voice engine error")
        assertNull(state.reviewSheetPendingBatch)
        assertTrue(state.showFallbackManualSheet, "Fallback sheet must show on voice engine error")
    }

    @Test
    fun testVoiceEngineNoMatchGuaranteesFallbackSheetOpenAndStopsRecording() = runTest(testDispatcher) {
        val fakeVoiceEngine = FakeVoiceEngine(available = true)
        val viewModel = ExpenseViewModel(
            repository = repository,
            preferences = preferences,
            parser = parser,
            voiceEngine = fakeVoiceEngine
        )
        advanceUntilIdle()

        viewModel.handleIntent(ExpenseIntent.CheckPermissionsAndStartRecord)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isRecording)

        fakeVoiceEngine.emitError("NO_MATCH")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isRecording, "Recording must stop on NO_MATCH")
        assertNull(state.reviewSheetPendingBatch)
        assertTrue(state.showFallbackManualSheet, "Fallback sheet must show on NO_MATCH")
    }

    @Test
    fun testVoiceEngineBlankResultGuaranteesFallbackSheetOpenAndStopsRecording() = runTest(testDispatcher) {
        val fakeVoiceEngine = FakeVoiceEngine(available = true)
        val viewModel = ExpenseViewModel(
            repository = repository,
            preferences = preferences,
            parser = parser,
            voiceEngine = fakeVoiceEngine
        )
        advanceUntilIdle()

        viewModel.handleIntent(ExpenseIntent.CheckPermissionsAndStartRecord)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isRecording)

        fakeVoiceEngine.emitResult("   ")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isRecording, "Recording must stop on blank transcribed text")
        assertNull(state.reviewSheetPendingBatch)
        assertTrue(state.showFallbackManualSheet, "Fallback sheet must show on blank transcribed text")
    }
}
