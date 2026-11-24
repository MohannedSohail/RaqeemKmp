package org.mohtech.app.data.storage

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.mohtech.app.data.model.ExpenseItem
import java.io.File

class AndroidPlatformExpenseStorage(context: Context) : PlatformExpenseStorage {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val storageFile = File(context.filesDir, "raqeem_expenses.json")
    private val lock = Any()

    override suspend fun saveExpense(item: ExpenseItem) {
        synchronized(lock) {
            val list = loadInternal().filter { it.id != item.id }.toMutableList()
            list.add(0, item)
            saveInternal(list.distinctBy { it.id })
        }
    }

    override suspend fun saveAll(items: List<ExpenseItem>) {
        synchronized(lock) {
            val uniqueNewItems = items.distinctBy { it.id }
            val existing = loadInternal().filterNot { old -> uniqueNewItems.any { it.id == old.id } }.toMutableList()
            existing.addAll(uniqueNewItems)
            saveInternal(existing.distinctBy { it.id })
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
            saveInternal(list.distinctBy { it.id })
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
            val decoded = json.decodeFromString<List<ExpenseItem>>(content)
            val distinct = decoded.distinctBy { it.id }
            if (distinct.size < decoded.size) {
                saveInternal(distinct)
            }
            distinct
        } catch (e: Exception) {
            // Quarantine corrupted file to avoid wiping user data upon subsequent writes
            try {
                val corruptFile = File(storageFile.parentFile, "raqeem_expenses_${System.currentTimeMillis()}.corrupt")
                storageFile.renameTo(corruptFile)
            } catch (_: Throwable) {}
            emptyList()
        }
    }

    private fun saveInternal(items: List<ExpenseItem>) {
        val serialized = json.encodeToString(items.distinctBy { it.id })
        val tempFile = File(storageFile.parentFile, "raqeem_expenses.json.tmp")
        try {
            java.io.FileOutputStream(tempFile).use { fos ->
                fos.write(serialized.toByteArray(Charsets.UTF_8))
                fos.flush()
                fos.fd.sync()
            }
            if (tempFile.exists()) {
                val moved = if (storageFile.exists()) {
                    if (storageFile.delete()) {
                        tempFile.renameTo(storageFile)
                    } else {
                        false
                    }
                } else {
                    tempFile.renameTo(storageFile)
                }
                if (!moved) {
                    throw java.io.IOException("Failed to atomically rename $tempFile to $storageFile")
                }
            }
        } catch (e: Exception) {
            // Fallback direct write if atomic rename fails to ensure user data is never lost
            storageFile.writeText(serialized)
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }
}

actual fun createPlatformExpenseStorage(context: Any?): PlatformExpenseStorage {
    val ctx = (context as? Context) ?: AndroidContextHolder.context
        ?: throw IllegalStateException("Android Context must be provided to initialize PlatformExpenseStorage")
    return AndroidPlatformExpenseStorage(ctx.applicationContext)
}

