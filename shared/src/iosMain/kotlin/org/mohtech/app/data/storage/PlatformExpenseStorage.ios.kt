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
        val list = loadInternal().filter { it.id != item.id }.toMutableList()
        list.add(0, item)
        saveInternal(list.distinctBy { it.id })
    }

    override suspend fun saveAll(items: List<ExpenseItem>) {
        val uniqueNewItems = items.distinctBy { it.id }
        val existing = loadInternal().filterNot { old -> uniqueNewItems.any { it.id == old.id } }.toMutableList()
        existing.addAll(uniqueNewItems)
        saveInternal(existing.distinctBy { it.id })
    }

    override suspend fun getAllExpenses(): List<ExpenseItem> {
        return loadInternal().distinctBy { it.id }
    }

    override suspend fun deleteExpense(id: String) {
        val list = loadInternal().filter { it.id != id }
        saveInternal(list.distinctBy { it.id })
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
            val decoded = json.decodeFromString<List<ExpenseItem>>(content as String)
            val distinct = decoded.distinctBy { it.id }
            if (distinct.size < decoded.size) {
                saveInternal(distinct)
            }
            distinct
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveInternal(items: List<ExpenseItem>) {
        val serialized = json.encodeToString(items.distinctBy { it.id })
        (serialized as NSString).writeToFile(
            path = filePath,
            atomically = true,
            encoding = NSUTF8StringEncoding,
            error = null
        )
    }
}

actual fun createPlatformExpenseStorage(context: Any?): PlatformExpenseStorage = IosPlatformExpenseStorage()

