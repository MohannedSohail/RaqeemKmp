package org.mohtech.app.feature.dashboard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.mohtech.app.ai.audio.VoiceEngine
import org.mohtech.app.ai.nlp.LocalDialectExpenseParser
import org.mohtech.app.data.model.ExpenseCategory
import org.mohtech.app.data.model.ExpenseItem
import org.mohtech.app.data.model.ProofType
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

class FakePlatformExpenseStorage : PlatformExpenseStorage {
    private val list = mutableListOf<ExpenseItem>()

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

class FakeAppPreferences : AppPreferences {
    private var onboardingDone = false
    private var currency = "₪"

    override fun isOnboardingCompleted(): Boolean = onboardingDone
    override fun setOnboardingCompleted(completed: Boolean) { onboardingDone = completed }
    override fun getPreferredCurrency(): String = currency
    override fun setPreferredCurrency(currency: String) { this.currency = currency }
}

class FakeVoiceEngine(
    var available: Boolean = true
) : VoiceEngine {
    var isListening = false
    var startListeningCount = 0
    var stopListeningCount = 0
    var releaseCount = 0
    var onResultCb: ((String) -> Unit)? = null
    var onAmplitudeCb: ((Float) -> Unit)? = null
    var onErrorCb: ((String) -> Unit)? = null

    override fun isAvailable(): Boolean = available

    override fun startListening(
        onResult: (String) -> Unit,
        onAmplitudeChanged: (Float) -> Unit,
        onError: (String) -> Unit
    ) {
        isListening = true
        startListeningCount++
        onResultCb = onResult
        onAmplitudeCb = onAmplitudeChanged
        onErrorCb = onError
    }

    override fun stopListening() {
        isListening = false
        stopListeningCount++
    }

    override fun release() {
        isListening = false
        releaseCount++
    }

    fun emitResult(text: String) {
        onResultCb?.invoke(text)
    }

    fun emitAmplitude(amplitude: Float) {
        onAmplitudeCb?.invoke(amplitude)
    }

    fun emitError(error: String) {
        onErrorCb?.invoke(error)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var storage: FakePlatformExpenseStorage
    private lateinit var preferences: FakeAppPreferences
    private lateinit var repository: LocalExpenseRepository
    private lateinit var parser: LocalDialectExpenseParser
    private lateinit var fakeVoiceEngine: FakeVoiceEngine
    private lateinit var viewModel: ExpenseViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        storage = FakePlatformExpenseStorage()
        preferences = FakeAppPreferences()
        repository = LocalExpenseRepository(storage, preferences)
        parser = LocalDialectExpenseParser()
        fakeVoiceEngine = FakeVoiceEngine(available = true)
        viewModel = ExpenseViewModel(repository, preferences, parser, fakeVoiceEngine)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialStateLoadsCorrectly() = runTest(testDispatcher) {
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("₪", state.activeCurrency)
        assertFalse(state.isOnboardingDone)
        assertTrue(state.recentExpenses.isEmpty())
        assertTrue(state.isHardwareSpeechAvailable)
    }

    @Test
    fun testParseManualSpeechTextOpensReviewSheet() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.handleIntent(ExpenseIntent.ParseManualSpeechText("قهوة بعشرة شيكل"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val pendingBatch = state.reviewSheetPendingBatch
        assertNotNull(pendingBatch)
        assertEquals(1, pendingBatch.items.size)
        assertEquals(10.0, pendingBatch.items.first().amount)
        assertEquals(ExpenseCategory.DINING, pendingBatch.items.first().category)
    }

    @Test
    fun testConfirmBatchSavesToRepository() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.handleIntent(ExpenseIntent.ParseManualSpeechText("بنزين ب 50 شيكل"))
        advanceUntilIdle()

        val batch = viewModel.uiState.value.reviewSheetPendingBatch
        assertNotNull(batch)

        viewModel.handleIntent(ExpenseIntent.ConfirmBatch(batch))
        advanceUntilIdle()

        val updatedState = viewModel.uiState.value
        assertNull(updatedState.reviewSheetPendingBatch)
        assertEquals(1, updatedState.recentExpenses.size)
        assertEquals(50.0, updatedState.todayTotal)
    }

    @Test
    fun testFilterByCategory() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.handleIntent(ExpenseIntent.ParseManualSpeechText("بنزين ب 50 شيكل"))
        advanceUntilIdle()
        viewModel.handleIntent(ExpenseIntent.ConfirmBatch(viewModel.uiState.value.reviewSheetPendingBatch!!))
        advanceUntilIdle()

        viewModel.handleIntent(ExpenseIntent.ParseManualSpeechText("قهوة بعشرة شيكل"))
        advanceUntilIdle()
        viewModel.handleIntent(ExpenseIntent.ConfirmBatch(viewModel.uiState.value.reviewSheetPendingBatch!!))
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.recentExpenses.size)

        // Filter by TRANSPORT
        viewModel.handleIntent(ExpenseIntent.FilterByCategory(ExpenseCategory.TRANSPORT))
        advanceUntilIdle()

        val filteredState = viewModel.uiState.value
        assertEquals(1, filteredState.filteredExpenses.size)
        assertEquals(ExpenseCategory.TRANSPORT, filteredState.filteredExpenses.first().category)

        // Clear filter
        viewModel.handleIntent(ExpenseIntent.FilterByCategory(null))
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.filteredExpenses.size)
    }

    @Test
    fun testCompleteOnboardingUpdatesPreferencesAndState() = runTest(testDispatcher) {
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isOnboardingDone)

        viewModel.handleIntent(ExpenseIntent.CompleteOnboarding)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isOnboardingDone)
        assertTrue(preferences.isOnboardingCompleted())
    }

    @Test
    fun testVoiceRecordingSuccessFlow() = runTest(testDispatcher) {
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isRecording)

        // 1. User taps record button
        viewModel.handleIntent(ExpenseIntent.CheckPermissionsAndStartRecord)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isRecording)
        assertEquals(1, fakeVoiceEngine.startListeningCount)
        assertTrue(fakeVoiceEngine.isListening)

        // 2. Audio amplitude feedback
        fakeVoiceEngine.emitAmplitude(0.75f)
        assertEquals(0.75f, viewModel.uiState.value.recordingAmplitude)

        // 3. Engine emits recognized Arabic speech
        fakeVoiceEngine.emitResult("دفعت خمسين شيكل بنزين")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRecording)
        assertEquals(0f, viewModel.uiState.value.recordingAmplitude)

        // 4. Batch parsed via DialectParser and displayed in ReviewSheet
        val batch = viewModel.uiState.value.reviewSheetPendingBatch
        assertNotNull(batch)
        assertEquals(1, batch.items.size)
        val item = batch.items.first()
        assertEquals(50.0, item.amount)
        assertEquals("₪", item.currency)
        assertEquals(ExpenseCategory.TRANSPORT, item.category)
    }

    @Test
    fun testVoiceRecordingStopFlow() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.handleIntent(ExpenseIntent.CheckPermissionsAndStartRecord)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isRecording)

        // User taps stop recording
        viewModel.handleIntent(ExpenseIntent.StopRecording)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRecording)
        assertEquals(0f, viewModel.uiState.value.recordingAmplitude)
        assertEquals(1, fakeVoiceEngine.stopListeningCount)
    }

    @Test
    fun testVoiceRecordingFallbackWhenEngineUnavailable() = runTest(testDispatcher) {
        val unavailableEngine = FakeVoiceEngine(available = false)
        val fallbackVm = ExpenseViewModel(repository, preferences, parser, unavailableEngine)
        advanceUntilIdle()

        assertFalse(fallbackVm.uiState.value.isHardwareSpeechAvailable)

        fallbackVm.handleIntent(ExpenseIntent.CheckPermissionsAndStartRecord)
        advanceUntilIdle()

        // Should not start recording; instead display Polite Fallback Sheet
        assertFalse(fallbackVm.uiState.value.isRecording)
        assertTrue(fallbackVm.uiState.value.showFallbackManualSheet)
        assertEquals(0, unavailableEngine.startListeningCount)
    }

    @Test
    fun testVoiceRecordingPermissionDeniedFallback() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.handleIntent(ExpenseIntent.CheckPermissionsAndStartRecord)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isRecording)

        // Speech engine indicates permission denied
        fakeVoiceEngine.emitError("PERMISSION_DENIED")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRecording)
        assertTrue(viewModel.uiState.value.showFallbackManualSheet)
    }

    @Test
    fun testOpenManualSheetIntentOpensFallbackSheet() = runTest(testDispatcher) {
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showFallbackManualSheet)

        viewModel.handleIntent(ExpenseIntent.OpenManualSheet)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showFallbackManualSheet)
        assertFalse(viewModel.uiState.value.isRecording)
        assertEquals(0, fakeVoiceEngine.startListeningCount)
    }

    @Test
    fun testDuplicateStartRecordingCallsAreSafelyIgnored() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.handleIntent(ExpenseIntent.CheckPermissionsAndStartRecord)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isRecording)
        assertEquals(1, fakeVoiceEngine.startListeningCount)

        // Second duplicate call while already recording
        viewModel.handleIntent(ExpenseIntent.CheckPermissionsAndStartRecord)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isRecording)
        // Should not have triggered startListening again
        assertEquals(1, fakeVoiceEngine.startListeningCount)
    }

    @Test
    fun testRedundantStopRecordingIsSafelyIgnored() = runTest(testDispatcher) {
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isRecording)

        // Tapping stop when not recording
        viewModel.handleIntent(ExpenseIntent.StopRecording)
        advanceUntilIdle()

        assertEquals(0, fakeVoiceEngine.stopListeningCount)
        assertFalse(viewModel.uiState.value.isRecording)
    }

    @Test
    fun testBlankSpokenTextEmitsToastAndDoesNotOpenReview() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.handleIntent(ExpenseIntent.CheckPermissionsAndStartRecord)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isRecording)

        // Engine returns blank string
        fakeVoiceEngine.emitResult("   ")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRecording)
        assertNull(viewModel.uiState.value.reviewSheetPendingBatch)
    }

    @Test
    fun testSpeechTimeoutErrorResetsRecordingState() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.handleIntent(ExpenseIntent.CheckPermissionsAndStartRecord)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isRecording)

        fakeVoiceEngine.emitError("SPEECH_TIMEOUT")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRecording)
        assertEquals(0f, viewModel.uiState.value.recordingAmplitude)
    }

    @Test
    fun testVoiceEngineReleasedWhenViewModelCleared() = runTest(testDispatcher) {
        assertEquals(0, fakeVoiceEngine.releaseCount)

        viewModel.onCleared()

        assertEquals(1, fakeVoiceEngine.releaseCount)
    }

    @Test
    fun testVoiceRecordingFallbackWhenVoiceEngineIsNull() = runTest(testDispatcher) {
        val nullEngineVm = ExpenseViewModel(repository, preferences, parser, null)
        advanceUntilIdle()

        assertFalse(nullEngineVm.uiState.value.isHardwareSpeechAvailable)

        nullEngineVm.handleIntent(ExpenseIntent.CheckPermissionsAndStartRecord)
        advanceUntilIdle()

        assertFalse(nullEngineVm.uiState.value.isRecording)
        assertTrue(nullEngineVm.uiState.value.showFallbackManualSheet)
    }

    @Test
    fun testDeleteExpenseRemovesItemFromRepository() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.handleIntent(ExpenseIntent.ParseManualSpeechText("قهوة بعشرة شيكل"))
        advanceUntilIdle()
        val batch = viewModel.uiState.value.reviewSheetPendingBatch!!
        viewModel.handleIntent(ExpenseIntent.ConfirmBatch(batch))
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.recentExpenses.size)
        val expenseId = viewModel.uiState.value.recentExpenses.first().id

        viewModel.handleIntent(ExpenseIntent.DeleteExpense(expenseId))
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.recentExpenses.size)
        assertEquals(0.0, viewModel.uiState.value.todayTotal)
    }

    @Test
    fun testDismissReviewSheetAndDismissFallbackSheet() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.handleIntent(ExpenseIntent.ParseManualSpeechText("قهوة بعشرة شيكل"))
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.reviewSheetPendingBatch)

        viewModel.handleIntent(ExpenseIntent.DismissReviewSheet)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.reviewSheetPendingBatch)

        viewModel.handleIntent(ExpenseIntent.OpenManualSheet)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showFallbackManualSheet)

        viewModel.handleIntent(ExpenseIntent.DismissFallbackSheet)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showFallbackManualSheet)
    }

    @Test
    fun testParseBankSmsIntent() = runTest(testDispatcher) {
        advanceUntilIdle()
        val sms = "حركات بطاقة: تم شراء بمبلغ 150.00 ILS في سوبرماركت برافو"
        viewModel.handleIntent(ExpenseIntent.ParseBankSms(sms))
        advanceUntilIdle()

        val batch = viewModel.uiState.value.reviewSheetPendingBatch
        assertNotNull(batch)
        assertEquals(1, batch.items.size)
        val item = batch.items.first()
        assertEquals(150.0, item.amount)
        assertEquals(ProofType.BANK_SMS, item.proofType)
        assertEquals("ILS", item.currency)
    }

    @Test
    fun testParseBankSmsInvalidBodyEmitsToast() = runTest(testDispatcher) {
        advanceUntilIdle()
        val invalidSms = "مرحبا بك عزيزي العميل في مصرفك المفضل"
        viewModel.handleIntent(ExpenseIntent.ParseBankSms(invalidSms))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.reviewSheetPendingBatch)
    }
}
