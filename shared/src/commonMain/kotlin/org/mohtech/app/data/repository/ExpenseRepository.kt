package org.mohtech.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.mohtech.app.data.model.ExpenseBatch
import org.mohtech.app.data.model.ExpenseCategory
import org.mohtech.app.data.model.ExpenseItem
import org.mohtech.app.data.storage.AppPreferences
import org.mohtech.app.data.storage.PlatformExpenseStorage

interface ExpenseRepository {
    val expensesFlow: Flow<List<ExpenseItem>>
    suspend fun initialize()
    suspend fun saveBatch(batch: ExpenseBatch)
    suspend fun saveExpense(item: ExpenseItem)
    suspend fun deleteExpense(id: String)
    suspend fun getAllExpenses(): List<ExpenseItem>
    suspend fun getExpensesByCategory(category: ExpenseCategory): List<ExpenseItem>
    suspend fun getTodayTotal(): Double
    suspend fun getWeeklyTotal(): Double
}

class LocalExpenseRepository(
    private val storage: PlatformExpenseStorage,
    private val preferences: AppPreferences
) : ExpenseRepository {

    private val _expensesFlow = MutableStateFlow<List<ExpenseItem>>(emptyList())
    override val expensesFlow: Flow<List<ExpenseItem>> = _expensesFlow.asStateFlow()

    override suspend fun initialize() {
        val loaded = storage.getAllExpenses()
        _expensesFlow.value = loaded.sortedByDescending { it.timestamp }
    }

    override suspend fun saveBatch(batch: ExpenseBatch) {
        storage.saveAll(batch.items)
        val current = _expensesFlow.value.toMutableList()
        current.addAll(batch.items)
        _expensesFlow.value = current.sortedByDescending { it.timestamp }
    }

    override suspend fun saveExpense(item: ExpenseItem) {
        storage.saveExpense(item)
        val current = _expensesFlow.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.id == item.id }
        if (existingIndex >= 0) {
            current[existingIndex] = item
        } else {
            current.add(0, item)
        }
        _expensesFlow.value = current.sortedByDescending { it.timestamp }
    }

    override suspend fun deleteExpense(id: String) {
        storage.deleteExpense(id)
        _expensesFlow.value = _expensesFlow.value.filter { it.id != id }
    }

    override suspend fun getAllExpenses(): List<ExpenseItem> {
        return _expensesFlow.value
    }

    override suspend fun getExpensesByCategory(category: ExpenseCategory): List<ExpenseItem> {
        return _expensesFlow.value.filter { it.category == category }
    }

    override suspend fun getTodayTotal(): Double {
        val now = Clock.System.now()
        val today = Instant.fromEpochMilliseconds(now.toEpochMilliseconds())
            .toLocalDateTime(TimeZone.currentSystemDefault()).date

        return _expensesFlow.value.filter { item ->
            val itemDate = Instant.fromEpochMilliseconds(item.timestamp)
                .toLocalDateTime(TimeZone.currentSystemDefault()).date
            itemDate == today
        }.sumOf { it.amount }
    }

    override suspend fun getWeeklyTotal(): Double {
        val nowMillis = Clock.System.now().toEpochMilliseconds()
        val sevenDaysAgoMillis = nowMillis - (7L * 24L * 60L * 60L * 1000L)

        return _expensesFlow.value.filter { item ->
            item.timestamp in sevenDaysAgoMillis..nowMillis
        }.sumOf { it.amount }
    }
}
