package org.mohtech.app.data.storage

import org.mohtech.app.data.model.ExpenseItem

/**
 * PlatformExpenseStorage:
 * Contract for atomic local persistence of expense records using kotlinx.serialization.
 */
interface PlatformExpenseStorage {
    suspend fun saveExpense(item: ExpenseItem)
    suspend fun saveAll(items: List<ExpenseItem>)
    suspend fun getAllExpenses(): List<ExpenseItem>
    suspend fun deleteExpense(id: String)
    suspend fun clearAll()
}

/**
 * Platform factory to instantiate the local offline storage engine.
 */
expect fun createPlatformExpenseStorage(context: Any? = null): PlatformExpenseStorage
