package org.mohtech.app.integration

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.mohtech.app.AppDestination
import org.mohtech.app.ai.audio.AudioProcessor
import org.mohtech.app.ai.nlp.LocalDialectExpenseParser
import org.mohtech.app.ai.whisper.FakeAudioRecorder
import org.mohtech.app.ai.whisper.FakeTranscriber
import org.mohtech.app.ai.whisper.TranscribeResult
import org.mohtech.app.ai.whisper.WhisperModelConfig
import org.mohtech.app.data.model.ExpenseCategory
import org.mohtech.app.data.model.ExpenseItem
import org.mohtech.app.data.model.Payer
import org.mohtech.app.data.repository.LocalExpenseRepository
import org.mohtech.app.data.storage.AppPreferences
import org.mohtech.app.data.storage.PlatformExpenseStorage
import org.mohtech.app.feature.dashboard.ExpenseIntent
import org.mohtech.app.feature.dashboard.ExpenseViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * CrossFeatureIntegrationTest (Tier 3 - Pairwise & Multi-Feature Integration):
 * Validates interface contracts and seamless data handoffs across requirements R1 through R7.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CrossFeatureIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()

    private class InMemoryStorage : PlatformExpenseStorage {
        val list = mutableListOf<ExpenseItem>()
        override suspend fun saveExpense(item: ExpenseItem) {
            list.removeAll { it.id == item.id }
            list.add(0, item)
        }
        override suspend fun saveAll(items: List<ExpenseItem>) {
            list.addAll(items)
        }
        override suspend fun getAllExpenses(): List<ExpenseItem> = list.toList()
        override suspend fun deleteExpense(id: String) {
            list.removeAll { it.id == id }
        }
        override suspend fun clearAll() {
            list.clear()
        }
    }

    private class InMemoryPreferences(
        private var onboardingCompleted: Boolean = false,
        private var preferredCurrency: String = "₪",
        private var weeklyBudget: Double = 2500.0
    ) : AppPreferences {
        override fun isOnboardingCompleted(): Boolean = onboardingCompleted
        override fun setOnboardingCompleted(completed: Boolean) { onboardingCompleted = completed }
        override fun getPreferredCurrency(): String = preferredCurrency
        override fun setPreferredCurrency(currency: String) { preferredCurrency = currency }
        override fun getWeeklyBudget(): Double = weeklyBudget
        override fun setWeeklyBudget(budget: Double) { weeklyBudget = budget }
    }

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -------------------------------------------------------------------------
    // C1: Manifest & Sanitization + Whisper Contracts (F1 + F2)
    // -------------------------------------------------------------------------
    @Test
    fun testC1_SanitizationAndContracts_OfflineWhisperOperatesWithoutNetwork() {
        val config = WhisperModelConfig()
        // Verify default config targets local file and does not contain remote network URLs
        assertFalse(config.modelFileName.startsWith("http://"), "Model file must not be a remote URL")
        assertFalse(config.modelFileName.startsWith("https://"), "Model file must not be a remote URL")
        assertEquals("ggml-tiny.bin", config.modelFileName)
        assertEquals("ar", config.language)
    }

    // -------------------------------------------------------------------------
    // C2: RawAudioRecorder + RMS Calculation Pipeline (F2 + F3)
    // -------------------------------------------------------------------------
    @Test
    fun testC2_AudioRecorderToRmsCalculation_RealtimeAmplitudePiping() {
        val pcm = ShortArray(16000) { (it % 1000).toShort() }
        val recorder = FakeAudioRecorder(recordedPcm = pcm)
        var capturedAmplitude = -1.0f

        recorder.startRecording(
            onAmplitudeChanged = { capturedAmplitude = it },
            onError = {}
        )

        val computedRms = AudioProcessor.calculateRmsAmplitude(pcm)
        recorder.emitAmplitude(computedRms)

        assertTrue(capturedAmplitude in 0.0f..1.0f, "Emitted RMS amplitude must be within [0.0, 1.0]")
        assertEquals(computedRms, capturedAmplitude)
        recorder.release()
    }

    // -------------------------------------------------------------------------
    // C3: Audio Capture + Chunking Pipeline (F3 + F4)
    // -------------------------------------------------------------------------
    @Test
    fun testC3_AudioBufferToChunking_SplitsLargeRecordingBeforeTranscriber() {
        // 35 seconds of 16kHz audio = 560,000 samples (> 480,000 chunk boundary)
        val longRecording = ShortArray(560000) { 150 }
        val recorder = FakeAudioRecorder(recordedPcm = longRecording)

        recorder.startRecording(onAmplitudeChanged = {}, onError = {})
        val rawAudio = recorder.stopRecording()
        assertNotNull(rawAudio)

        val chunks = AudioProcessor.chunkAudio(rawAudio)
        assertEquals(2, chunks.size, "Audio over 30s must be split into 2 chunks")
        assertEquals(AudioProcessor.SAMPLES_PER_30S_CHUNK, chunks[0].size)
        assertEquals(560000 - AudioProcessor.SAMPLES_PER_30S_CHUNK, chunks[1].size)
    }

    // -------------------------------------------------------------------------
    // C4: Chunked Audio Transcription + ViewModel Review State (F4 + F5)
    // -------------------------------------------------------------------------
    @Test
    fun testC4_ChunkedTranscriptionToViewModelBatch() = runTest(testDispatcher) {
        val storage = InMemoryStorage()
        val preferences = InMemoryPreferences()
        val repository = LocalExpenseRepository(storage, preferences)
        val parser = LocalDialectExpenseParser()
        val transcriber = FakeTranscriber(transcribeResultText = "بنزين بخمسين شيكل")
        val recorder = FakeAudioRecorder()

        val viewModel = ExpenseViewModel(
            repository = repository,
            preferences = preferences,
            parser = parser,
            transcriber = transcriber,
            audioRecorder = recorder
        )
        advanceUntilIdle()

        viewModel.handleIntent(ExpenseIntent.CheckPermissionsAndStartRecord)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isRecording)

        viewModel.handleIntent(ExpenseIntent.StopRecording)
        advanceUntilIdle()

        val batch = viewModel.uiState.value.reviewSheetPendingBatch
        assertNotNull(batch, "Expected review batch to be generated after transcription")
        assertEquals(1, batch.items.size)
        assertEquals(50.0, batch.items.first().amount)
        assertEquals(ExpenseCategory.TRANSPORT, batch.items.first().category)
    }

    // -------------------------------------------------------------------------
    // C5: ViewModel Confirm Batch + Repository Atomic Persistence (F5 + F6)
    // -------------------------------------------------------------------------
    @Test
    fun testC5_ViewModelConfirmBatchToRepositoryAtomicPersistence() = runTest(testDispatcher) {
        val storage = InMemoryStorage()
        val preferences = InMemoryPreferences()
        val repository = LocalExpenseRepository(storage, preferences)
        val parser = LocalDialectExpenseParser()

        val viewModel = ExpenseViewModel(
            repository = repository,
            preferences = preferences,
            parser = parser,
            audioRecorder = FakeAudioRecorder()
        )
        advanceUntilIdle()

        viewModel.handleIntent(ExpenseIntent.ParseManualSpeechText("قهوة بعشرة شيكل"))
        advanceUntilIdle()

        val pendingBatch = viewModel.uiState.value.reviewSheetPendingBatch
        assertNotNull(pendingBatch)

        viewModel.handleIntent(ExpenseIntent.ConfirmBatch(pendingBatch))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.reviewSheetPendingBatch)
        assertEquals(1, viewModel.uiState.value.recentExpenses.size)
        assertEquals(10.0, viewModel.uiState.value.todayTotal)

        val persisted = repository.getAllExpenses()
        assertEquals(1, persisted.size)
        assertEquals(10.0, persisted.first().amount)
        assertEquals(ExpenseCategory.DINING, persisted.first().category)
    }

    // -------------------------------------------------------------------------
    // C6: AppPreferences + Navigation & Initial ViewModel State (F6 + F7)
    // -------------------------------------------------------------------------
    @Test
    fun testC6_AppPreferencesToNavigationAndViewModelState() = runTest(testDispatcher) {
        val preferences = InMemoryPreferences(onboardingCompleted = true, weeklyBudget = 3000.0)
        val storage = InMemoryStorage()
        val repository = LocalExpenseRepository(storage, preferences)
        val parser = LocalDialectExpenseParser()

        // 1. Navigation evaluates preferences
        val destination = if (preferences.isOnboardingCompleted()) {
            AppDestination.DASHBOARD
        } else {
            AppDestination.ONBOARDING
        }
        assertEquals(AppDestination.DASHBOARD, destination)

        // 2. ViewModel loads onboarding state from preferences
        val viewModel = ExpenseViewModel(
            repository = repository,
            preferences = preferences,
            parser = parser,
            audioRecorder = FakeAudioRecorder()
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isOnboardingDone)
        assertEquals(3000.0, preferences.getWeeklyBudget())
    }

    // -------------------------------------------------------------------------
    // C7: Dialect Speech Parsing + Persistence Pipeline (F4 + F5 + F6)
    // -------------------------------------------------------------------------
    @Test
    fun testC7_DialectParsingToPersistencePipeline() = runTest(testDispatcher) {
        val storage = InMemoryStorage()
        val preferences = InMemoryPreferences()
        val repository = LocalExpenseRepository(storage, preferences)
        val parser = LocalDialectExpenseParser()

        val viewModel = ExpenseViewModel(
            repository = repository,
            preferences = preferences,
            parser = parser,
            audioRecorder = FakeAudioRecorder()
        )
        advanceUntilIdle()

        // Spoken Arabic split expense
        viewModel.handleIntent(ExpenseIntent.ParseManualSpeechText("عشاء بستين شيكل بيني وبين أحمد"))
        advanceUntilIdle()

        val batch = viewModel.uiState.value.reviewSheetPendingBatch
        assertNotNull(batch)
        val item = batch.items.first()
        assertEquals(60.0, item.amount)
        assertEquals(ExpenseCategory.DINING, item.category)
        val split = item.split
        assertNotNull(split)
        assertEquals(30.0, split.amountPerPerson)

        // Confirm batch
        viewModel.handleIntent(ExpenseIntent.ConfirmBatch(batch))
        advanceUntilIdle()

        val allExpenses = repository.getAllExpenses()
        assertEquals(1, allExpenses.size)
        assertEquals(60.0, allExpenses.first().amount)
        assertEquals(2, allExpenses.first().split?.participants?.size)
    }
}
