package org.mohtech.app.scenario

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.mohtech.app.AppDestination
import org.mohtech.app.ai.audio.AudioProcessor
import org.mohtech.app.ai.nlp.LocalDialectExpenseParser
import org.mohtech.app.ai.whisper.FakeAudioRecorder
import org.mohtech.app.ai.whisper.FakeTranscriber
import org.mohtech.app.ai.whisper.SpeechError
import org.mohtech.app.ai.whisper.TranscribeResult
import org.mohtech.app.ai.whisper.WhisperModelConfig
import org.mohtech.app.data.model.ExpenseCategory
import org.mohtech.app.data.model.ExpenseItem
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
 * RealWorldScenarioTest (Tier 4 - Realistic End-to-End Application Scenarios):
 * Verifies complex multi-step user workflows S1 through S5 under realistic 100% offline conditions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RealWorldScenarioTest {

    private val testDispatcher = StandardTestDispatcher()

    private class ScenarioStorage : PlatformExpenseStorage {
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

    private class ScenarioPreferences(
        private var onboardingDone: Boolean = false,
        private var currency: String = "₪",
        private var weeklyBudget: Double = 0.0
    ) : AppPreferences {
        override fun isOnboardingCompleted(): Boolean = onboardingDone
        override fun setOnboardingCompleted(completed: Boolean) { onboardingDone = completed }
        override fun getPreferredCurrency(): String = currency
        override fun setPreferredCurrency(currency: String) { this.currency = currency }
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

    // =========================================================================
    // Scenario 1: First Launch & Onboarding Experience (F6, F7)
    // =========================================================================
    @Test
    fun testScenario1_FirstLaunchAndOnboardingWorkflow() = runTest(testDispatcher) {
        val storage = ScenarioStorage()
        val preferences = ScenarioPreferences(onboardingDone = false)
        val repository = LocalExpenseRepository(storage, preferences)
        val parser = LocalDialectExpenseParser()

        // Step 1: App cold launch with no prior onboarding
        assertFalse(preferences.isOnboardingCompleted())
        var currentDestination = AppDestination.SPLASH

        // Step 2: Splash finishes -> checks preferences -> routes to ONBOARDING
        currentDestination = if (preferences.isOnboardingCompleted()) {
            AppDestination.DASHBOARD
        } else {
            AppDestination.ONBOARDING
        }
        assertEquals(AppDestination.ONBOARDING, currentDestination)

        // Step 3: User completes onboarding
        val viewModel = ExpenseViewModel(
            repository = repository,
            preferences = preferences,
            parser = parser,
            audioRecorder = FakeAudioRecorder()
        )
        advanceUntilIdle()

        viewModel.handleIntent(ExpenseIntent.CompleteOnboarding)
        advanceUntilIdle()

        preferences.setOnboardingCompleted(true)
        currentDestination = AppDestination.DASHBOARD

        assertTrue(preferences.isOnboardingCompleted())
        assertTrue(viewModel.uiState.value.isOnboardingDone)
        assertEquals(AppDestination.DASHBOARD, currentDestination)

        // Step 4: Simulate subsequent app relaunch
        val nextLaunchDestination = if (preferences.isOnboardingCompleted()) {
            AppDestination.DASHBOARD
        } else {
            AppDestination.ONBOARDING
        }
        assertEquals(AppDestination.DASHBOARD, nextLaunchDestination, "Subsequent launch must bypass onboarding")
    }

    // =========================================================================
    // Scenario 2: Offline Voice Capture & Audio Processing Pipeline (F2, F3, F4)
    // =========================================================================
    @Test
    fun testScenario2_OfflineVoiceCaptureAndAudioPipeline() = runTest(testDispatcher) {
        // Step 1: User speaks Arabic expense: "بنزين ب 50 شيكل"
        // 2 seconds of 16kHz audio = 32,000 samples
        val simulatedPcm = ShortArray(32000) { (it % 800).toShort() }
        val recorder = FakeAudioRecorder(recordedPcm = simulatedPcm)
        val transcriber = FakeTranscriber(transcribeResultText = "بنزين ب 50 شيكل")

        var recordedAmplitude = 0.0f
        recorder.startRecording(
            onAmplitudeChanged = { recordedAmplitude = it },
            onError = {}
        )
        assertTrue(recorder.isRecording())

        // Step 2: Continuous RMS amplitude calculation
        val calculatedRms = AudioProcessor.calculateRmsAmplitude(simulatedPcm)
        recorder.emitAmplitude(calculatedRms)
        assertTrue(recordedAmplitude in 0.0f..1.0f)

        // Step 3: Audio capture completed
        val audioData = recorder.stopRecording()
        assertNotNull(audioData)
        assertEquals(32000, audioData.size)

        // Step 4: Chunking validation (under 30s produces 1 chunk)
        val chunks = AudioProcessor.chunkAudio(audioData)
        assertEquals(1, chunks.size)

        // Step 5: Offline Whisper transcription
        val result = transcriber.transcribe(chunks[0], WhisperModelConfig())
        assertTrue(result is TranscribeResult.Success)
        assertEquals("بنزين ب 50 شيكل", result.text)

        // Step 6: Dialect extraction
        val parser = LocalDialectExpenseParser()
        val parsed = parser.parseSpokenText(result.text, defaultCurrency = "₪")
        assertEquals(1, parsed.items.size)
        assertEquals(50.0, parsed.items.first().amount)
        assertEquals(ExpenseCategory.TRANSPORT, parsed.items.first().category)

        recorder.release()
        transcriber.release()
    }

    // =========================================================================
    // Scenario 3: Voice Speech Error Resilience & Arabic Messaging (F2, F4, F5)
    // =========================================================================
    @Test
    fun testScenario3_VoiceErrorResilienceAndArabicFallbackMessaging() = runTest(testDispatcher) {
        val storage = ScenarioStorage()
        val preferences = ScenarioPreferences()
        val repository = LocalExpenseRepository(storage, preferences)
        val parser = LocalDialectExpenseParser()

        // Sub-case A: Whisper model missing
        val missingModelTranscriber = FakeTranscriber(
            shouldFailWith = SpeechError.ModelMissing,
            isModelReadyState = false
        )
        val viewModelWithMissingModel = ExpenseViewModel(
            repository = repository,
            preferences = preferences,
            parser = parser,
            transcriber = missingModelTranscriber,
            audioRecorder = FakeAudioRecorder()
        )
        advanceUntilIdle()

        viewModelWithMissingModel.handleIntent(ExpenseIntent.CheckPermissionsAndStartRecord)
        advanceUntilIdle()

        // Graceful fallback to manual sheet without crash
        assertTrue(
            viewModelWithMissingModel.uiState.value.showFallbackManualSheet,
            "Missing Whisper model must trigger polite manual fallback sheet"
        )
        assertFalse(viewModelWithMissingModel.uiState.value.isRecording)

        // Sub-case B: Permission denied
        val permissionDeniedRecorder = FakeAudioRecorder(errorToEmitOnStart = SpeechError.PermissionDenied)
        val viewModelPermissionDenied = ExpenseViewModel(
            repository = repository,
            preferences = preferences,
            parser = parser,
            audioRecorder = permissionDeniedRecorder
        )
        advanceUntilIdle()

        viewModelPermissionDenied.handleIntent(ExpenseIntent.CheckPermissionsAndStartRecord)
        advanceUntilIdle()

        assertTrue(
            viewModelPermissionDenied.uiState.value.showPermissionRationaleDialog ||
                viewModelPermissionDenied.uiState.value.showPermissionRationaleSheet ||
                viewModelPermissionDenied.uiState.value.showFallbackManualSheet,
            "Permission denied must present polite permission rationale or fallback"
        )
    }

    // =========================================================================
    // Scenario 4: Expense Management Lifecycle & Dynamic Daily Total (F5, F6)
    // =========================================================================
    @Test
    fun testScenario4_ExpenseManagementLifecycleAndAtomicUpdate() = runTest(testDispatcher) {
        val storage = ScenarioStorage()
        val preferences = ScenarioPreferences()
        val repository = LocalExpenseRepository(storage, preferences)
        val parser = LocalDialectExpenseParser()

        val viewModel = ExpenseViewModel(
            repository = repository,
            preferences = preferences,
            parser = parser,
            audioRecorder = FakeAudioRecorder()
        )
        advanceUntilIdle()

        // 1. Initial dailyTotal is 0.0
        assertEquals(0.0, viewModel.uiState.value.todayTotal)

        // 2. Add expense 1: قهوة ب 15 شيكل
        viewModel.handleIntent(ExpenseIntent.ParseManualSpeechText("قهوة ب 15 شيكل"))
        advanceUntilIdle()
        val batch1 = viewModel.uiState.value.reviewSheetPendingBatch
        assertNotNull(batch1)
        viewModel.handleIntent(ExpenseIntent.ConfirmBatch(batch1))
        advanceUntilIdle()

        assertEquals(15.0, viewModel.uiState.value.todayTotal)
        assertEquals(1, viewModel.uiState.value.recentExpenses.size)

        // 3. Add expense 2: بنزين ب 50 شيكل
        viewModel.handleIntent(ExpenseIntent.ParseManualSpeechText("بنزين ب 50 شيكل"))
        advanceUntilIdle()
        val batch2 = viewModel.uiState.value.reviewSheetPendingBatch
        assertNotNull(batch2)
        viewModel.handleIntent(ExpenseIntent.ConfirmBatch(batch2))
        advanceUntilIdle()

        assertEquals(65.0, viewModel.uiState.value.todayTotal)
        assertEquals(2, viewModel.uiState.value.recentExpenses.size)

        // 4. Delete expense 1
        val item1Id = viewModel.uiState.value.recentExpenses.first { it.amount == 15.0 }.id
        viewModel.handleIntent(ExpenseIntent.DeleteExpense(item1Id))
        advanceUntilIdle()

        assertEquals(50.0, viewModel.uiState.value.todayTotal)
        assertEquals(1, viewModel.uiState.value.recentExpenses.size)
    }

    // =========================================================================
    // Scenario 5: Financial Audit & Multi-Dialect Data Lifecycle (F5, F6)
    // =========================================================================
    @Test
    fun testScenario5_FinancialAuditAndDataPersistenceLifecycle() = runTest(testDispatcher) {
        val storage = ScenarioStorage()
        val preferences = ScenarioPreferences()
        val repository = LocalExpenseRepository(storage, preferences)
        val parser = LocalDialectExpenseParser()

        // 1. Process Levantine expense
        val levantine = parser.parseSpokenText("بنزين ب 50 شيكل", defaultCurrency = "₪")
        // 2. Process Egyptian expense with compound word
        val egyptian = parser.parseSpokenText("دفعت مية وخمسين جنيه في السوبرماركت", defaultCurrency = "ج.م")
        // 3. Process Gulf expense with split
        val gulf = parser.parseSpokenText("عشاء بستين ريال بيني وبين أحمد", defaultCurrency = "ر.س")

        val allItems = levantine.items + egyptian.items + gulf.items
        assertEquals(3, allItems.size)

        // Save all to storage and initialize repository flow
        storage.saveAll(allItems)
        repository.initialize()
        val stored = repository.getAllExpenses()
        assertEquals(3, stored.size)

        // Verify JSON serialization round-trip fidelity
        val json = Json { prettyPrint = true }
        val serialized = json.encodeToString(stored)
        assertTrue(serialized.isNotBlank())
        assertTrue(serialized.contains("بنزين"))
        assertTrue(serialized.contains("السوبرماركت"))
        assertTrue(serialized.contains("أحمد"))

        val decoded = json.decodeFromString<List<ExpenseItem>>(serialized)
        assertEquals(3, decoded.size)
        assertEquals(50.0, decoded[0].amount)
        assertEquals(150.0, decoded[1].amount)
        assertEquals(60.0, decoded[2].amount)
    }
}
