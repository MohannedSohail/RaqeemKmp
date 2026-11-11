package org.mohtech.app.data.storage

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.mohtech.app.data.model.ExpenseItem
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

class IosPlatformExpenseStorage : PlatformExpenseStorage {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val filePath: String by lazy {
        val paths = NSFileManager.defaultManager.URLsForDirectory(
            NSDocumentDirectory,
            NSUserDomainMask
        )
        val documentDir = paths.first().toString().removePrefix("file://")
        "$documentDir/raqeem_expenses.json"
    }

    override suspend fun saveExpense(item: ExpenseItem) {
        val list = loadInternal().toMutableList()
        val existingIndex = list.indexOfFirst { it.id == item.id }
        if (existingIndex >= 0) {
            list[existingIndex] = item
        } else {
            list.add(0, item)
        }
        saveInternal(list)
    }

    override suspend fun saveAll(items: List<ExpenseItem>) {
        val list = loadInternal().toMutableList()
        list.addAll(items)
        saveInternal(list)
    }

    override suspend fun getAllExpenses(): List<ExpenseItem> {
        return loadInternal()
    }

    override suspend fun deleteExpense(id: String) {
        val list = loadInternal().filter { it.id != id }
        saveInternal(list)
    }

    override suspend fun clearAll() {
        saveInternal(emptyList())
    }

    private fun loadInternal(): List<ExpenseItem> {
        return try {
            val content = NSString.stringWithContentsOfFile(
                path = filePath,
                encoding = NSUTF8StringEncoding,
                error = null
            ) ?: return emptyList()
            json.decodeFromString<List<ExpenseItem>>(content as String)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveInternal(items: List<ExpenseItem>) {
        val serialized = json.encodeToString(items)
        (serialized as NSString).writeToFile(
            path = filePath,
            atomically = true,
            encoding = NSUTF8StringEncoding,
            error = null
        )
    }
}

actual fun createPlatformExpenseStorage(context: Any?): PlatformExpenseStorage = IosPlatformExpenseStorage()

