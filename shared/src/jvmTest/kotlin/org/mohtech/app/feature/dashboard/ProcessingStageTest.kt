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
import org.mohtech.app.data.model.ExpenseItem
import org.mohtech.app.data.repository.LocalExpenseRepository
import org.mohtech.app.data.storage.AppPreferences
import org.mohtech.app.data.storage.PlatformExpenseStorage
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProcessingStageTest {

    private val testDispatcher = StandardTestDispatcher()

    private class InMemoryStorage : PlatformExpenseStorage {
        val list = mutableListOf<ExpenseItem>()
        override suspend fun saveExpense(item: ExpenseItem) { list.add(item) }
        override suspend fun saveAll(items: List<ExpenseItem>) { list.addAll(items) }
        override suspend fun getAllExpenses(): List<ExpenseItem> = list.toList()
        override suspend fun deleteExpense(id: String) { list.removeAll { it.id == id } }
        override suspend fun clearAll() { list.clear() }
    }

    private class InMemoryPreferences(
        private var onboardingCompleted: Boolean = true,
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
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testSuccessfulStopRecordingTransitionsThroughStagesToReady() = runTest(testDispatcher) {
        val repository = LocalExpenseRepository(InMemoryStorage(), InMemoryPreferences())
        val preferences = InMemoryPreferences()
        val parser = LocalDialectExpenseParser()
        val transcriber = FakeTranscriber(transcribeResultText = "بنزين بـ 50 شيكل")
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
        assertEquals(ProcessingStage.IDLE, viewModel.uiState.value.processingStage)

        viewModel.handleIntent(ExpenseIntent.StopRecording)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isRecording)
        assertEquals(ProcessingStage.READY, state.processingStage)
        assertNotNull(state.reviewSheetPendingBatch)
        assertEquals(50.0, state.reviewSheetPendingBatch?.items?.first()?.amount)

        // Finish processing overlay ready state
        viewModel.handleIntent(ExpenseIntent.FinishProcessingReady)
        advanceUntilIdle()
        assertEquals(ProcessingStage.IDLE, viewModel.uiState.value.processingStage)
        assertNotNull(viewModel.uiState.value.reviewSheetPendingBatch)

        // Confirm batch
        val batch = viewModel.uiState.value.reviewSheetPendingBatch!!
        viewModel.handleIntent(ExpenseIntent.ConfirmBatch(batch))
        advanceUntilIdle()
        assertEquals(ProcessingStage.IDLE, viewModel.uiState.value.processingStage)
        assertNull(viewModel.uiState.value.reviewSheetPendingBatch)
        assertFalse(viewModel.uiState.value.isProcessing)
    }

    @Test
    fun testCancelProcessingReturnsToIdleWithoutReviewSheet() = runTest(testDispatcher) {
        val repository = LocalExpenseRepository(InMemoryStorage(), InMemoryPreferences())
        val preferences = InMemoryPreferences()
        val parser = LocalDialectExpenseParser()
        val transcriber = FakeTranscriber(transcribeResultText = "بنزين بـ 50 شيكل")
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

        viewModel.handleIntent(ExpenseIntent.StopRecording)
        // Immediately cancel before completion
        viewModel.handleIntent(ExpenseIntent.CancelProcessing)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ProcessingStage.IDLE, state.processingStage)
        assertNull(state.reviewSheetPendingBatch)
    }

    @Test
    fun testTranscriptionErrorResetsStageToIdleAndShowsFallback() = runTest(testDispatcher) {
        val repository = LocalExpenseRepository(InMemoryStorage(), InMemoryPreferences())
        val preferences = InMemoryPreferences()
        val parser = LocalDialectExpenseParser()
        val transcriber = FakeTranscriber(shouldFailWith = SpeechError.TranscriptionFailed)
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

        viewModel.handleIntent(ExpenseIntent.StopRecording)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ProcessingStage.IDLE, state.processingStage)
        assertNull(state.reviewSheetPendingBatch)
        assertNotNull(state.userErrorMessage)
    }

    @Test
    fun testDismissReviewSheetResetsReadyStageToIdle() = runTest(testDispatcher) {
        val repository = LocalExpenseRepository(InMemoryStorage(), InMemoryPreferences())
        val preferences = InMemoryPreferences()
        val parser = LocalDialectExpenseParser()
        val transcriber = FakeTranscriber(transcribeResultText = "قهوة بعشرة شيكل")
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
        viewModel.handleIntent(ExpenseIntent.StopRecording)
        advanceUntilIdle()

        assertEquals(ProcessingStage.READY, viewModel.uiState.value.processingStage)

        viewModel.handleIntent(ExpenseIntent.DismissReviewSheet)
        advanceUntilIdle()

        assertEquals(ProcessingStage.IDLE, viewModel.uiState.value.processingStage)
        assertNull(viewModel.uiState.value.reviewSheetPendingBatch)
    }

    @Test
    fun testWeeklyBudgetUpdatesInUiStateAndPersists() = runTest(testDispatcher) {
        val storage = InMemoryStorage()
        val preferences = InMemoryPreferences(weeklyBudget = 2000.0)
        val repository = LocalExpenseRepository(storage, preferences)
        val viewModel = ExpenseViewModel(repository, preferences)
        advanceUntilIdle()

        assertEquals(2000.0, viewModel.uiState.value.weeklyBudget)

        viewModel.handleIntent(ExpenseIntent.UpdateWeeklyBudget(3500.0))
        advanceUntilIdle()

        assertEquals(3500.0, viewModel.uiState.value.weeklyBudget)
        assertEquals(3500.0, preferences.getWeeklyBudget())
    }
}

