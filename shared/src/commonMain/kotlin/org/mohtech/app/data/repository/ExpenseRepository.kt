package org.mohtech.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
    suspend fun updateExpense(item: ExpenseItem)
    fun exportCsv(expenses: List<ExpenseItem>): String
    fun exportJson(expenses: List<ExpenseItem>): String
}

class LocalExpenseRepository(
    private val storage: PlatformExpenseStorage,
    private val preferences: AppPreferences
) : ExpenseRepository {

    private val _expensesFlow = MutableStateFlow<List<ExpenseItem>>(emptyList())
    override val expensesFlow: Flow<List<ExpenseItem>> = _expensesFlow.map { list ->
        list.distinctBy { it.id }.filter { it.deletedAt == null }
    }

    override suspend fun initialize() {
        val loaded = storage.getAllExpenses().distinctBy { it.id }
        _expensesFlow.value = loaded.sortedByDescending { it.timestamp }
    }

    override suspend fun saveBatch(batch: ExpenseBatch) {
        val uniqueItems = batch.items.distinctBy { it.id }
        storage.saveAll(uniqueItems)
        val current = _expensesFlow.value.filterNot { existing ->
            uniqueItems.any { it.id == existing.id }
        }.toMutableList()
        current.addAll(uniqueItems)
        _expensesFlow.value = current.distinctBy { it.id }.sortedByDescending { it.timestamp }
    }

    override suspend fun saveExpense(item: ExpenseItem) {
        storage.saveExpense(item)
        val current = _expensesFlow.value.filter { it.id != item.id }.toMutableList()
        current.add(0, item)
        _expensesFlow.value = current.distinctBy { it.id }.sortedByDescending { it.timestamp }
    }

    override suspend fun deleteExpense(id: String) {
        val currentItem = _expensesFlow.value.find { it.id == id }
        if (currentItem != null && currentItem.split?.allocations?.isNotEmpty() == true) {
            val softDeleted = currentItem.copy(deletedAt = Clock.System.now().toEpochMilliseconds())
            storage.saveExpense(softDeleted)
            val current = _expensesFlow.value.filter { it.id != id }.toMutableList()
            current.add(softDeleted)
            _expensesFlow.value = current.distinctBy { it.id }.sortedByDescending { it.timestamp }
        } else {
            storage.deleteExpense(id)
            _expensesFlow.value = _expensesFlow.value.filter { it.id != id }.distinctBy { it.id }
        }
    }

    override suspend fun getAllExpenses(): List<ExpenseItem> {
        return _expensesFlow.value.distinctBy { it.id }.filter { it.deletedAt == null }
    }

    override suspend fun getExpensesByCategory(category: ExpenseCategory): List<ExpenseItem> {
        return _expensesFlow.value.distinctBy { it.id }.filter { it.deletedAt == null && it.category == category }
    }

    override suspend fun getTodayTotal(): Double {
        val nowMillis = Clock.System.now().toEpochMilliseconds()
        val startOfToday = CalendarWeekUtil.getTodayStartTimestamp(nowMillis)
        val activeCurrency = preferences.getPreferredCurrency()

        return _expensesFlow.value.filter { item ->
            item.deletedAt == null &&
            item.timestamp >= startOfToday &&
            (activeCurrency.isBlank() || item.currency == activeCurrency)
        }.sumOf { it.effectiveAmount }
    }

    override suspend fun getWeeklyTotal(): Double {
        val nowMillis = Clock.System.now().toEpochMilliseconds()
        val startOfWeek = CalendarWeekUtil.getWeekStartTimestamp(
            currentTimestamp = nowMillis,
            weekStartDayIso = preferences.getWeekStartDay()
        )
        val activeCurrency = preferences.getPreferredCurrency()

        return _expensesFlow.value.filter { item ->
            item.deletedAt == null &&
            item.timestamp >= startOfWeek &&
            (activeCurrency.isBlank() || item.currency == activeCurrency)
        }.sumOf { it.effectiveAmount }
    }

    override suspend fun updateExpense(item: ExpenseItem) {
        storage.saveExpense(item)
        val current = _expensesFlow.value.filter { it.id != item.id }.toMutableList()
        current.add(0, item)
        _expensesFlow.value = current.distinctBy { it.id }.sortedByDescending { it.timestamp }
    }

    override fun exportCsv(expenses: List<ExpenseItem>): String {
        val header = "id,title,amount,currency,category,timestamp"
        if (expenses.isEmpty()) {
            return header
        }
        val rows = expenses.map { item ->
            val id = escapeCsvField(item.id)
            val title = escapeCsvField(item.title)
            val amount = item.amount.toString()
            val currency = escapeCsvField(item.currency)
            val category = item.category.name
            val timestamp = item.timestamp.toString()
            "$id,$title,$amount,$currency,$category,$timestamp"
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    override fun exportJson(expenses: List<ExpenseItem>): String {
        return if (expenses.isEmpty()) {
            "[]"
        } else {
            Json.encodeToString(expenses)
        }
    }

    private fun escapeCsvField(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }
}
