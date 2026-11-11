package org.mohtech.app.data.storage

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.mohtech.app.data.model.ExpenseItem
import java.io.File

class JvmPlatformExpenseStorage : PlatformExpenseStorage {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val storageDir = File(System.getProperty("user.home"), ".raqeem").apply {
        if (!exists()) mkdirs()
    }
    private val storageFile = File(storageDir, "expenses.json")

    private val lock = Any()

    override suspend fun saveExpense(item: ExpenseItem) {
        synchronized(lock) {
            val list = loadInternal().toMutableList()
            val existingIndex = list.indexOfFirst { it.id == item.id }
            if (existingIndex >= 0) {
                list[existingIndex] = item
            } else {
                list.add(0, item)
            }
            saveInternal(list)
        }
    }

    override suspend fun saveAll(items: List<ExpenseItem>) {
        synchronized(lock) {
            val list = loadInternal().toMutableList()
            list.addAll(items)
            saveInternal(list)
        }
    }

    override suspend fun getAllExpenses(): List<ExpenseItem> {
        synchronized(lock) {
            return loadInternal()
        }
    }

    override suspend fun deleteExpense(id: String) {
        synchronized(lock) {
            val list = loadInternal().filter { it.id != id }
            saveInternal(list)
        }
    }

    override suspend fun clearAll() {
        synchronized(lock) {
            saveInternal(emptyList())
        }
    }

    private fun loadInternal(): List<ExpenseItem> {
        if (!storageFile.exists() || storageFile.length() == 0L) return emptyList()
        return try {
            val content = storageFile.readText()
            json.decodeFromString<List<ExpenseItem>>(content)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveInternal(items: List<ExpenseItem>) {
        val serialized = json.encodeToString(items)
        storageFile.writeText(serialized)
    }
}

actual fun createPlatformExpenseStorage(context: Any?): PlatformExpenseStorage = JvmPlatformExpenseStorage()

