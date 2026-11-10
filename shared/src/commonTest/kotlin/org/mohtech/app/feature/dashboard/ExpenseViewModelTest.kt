package org.mohtech.app.feature.dashboard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.mohtech.app.ai.nlp.LocalDialectExpenseParser
import org.mohtech.app.data.model.ExpenseCategory
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

@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var storage: FakePlatformExpenseStorage
    private lateinit var preferences: FakeAppPreferences
    private lateinit var repository: LocalExpenseRepository
    private lateinit var parser: LocalDialectExpenseParser
    private lateinit var viewModel: ExpenseViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        storage = FakePlatformExpenseStorage()
        preferences = FakeAppPreferences()
        repository = LocalExpenseRepository(storage, preferences)
        parser = LocalDialectExpenseParser()
        viewModel = ExpenseViewModel(repository, preferences, parser)
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
}
