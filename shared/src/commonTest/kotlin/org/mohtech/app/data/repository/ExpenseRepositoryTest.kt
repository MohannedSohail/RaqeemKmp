package org.mohtech.app.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.mohtech.app.data.model.ExpenseCategory
import org.mohtech.app.data.model.ExpenseItem
import org.mohtech.app.data.model.Payer
import org.mohtech.app.data.storage.AppPreferences
import org.mohtech.app.data.storage.PlatformExpenseStorage
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FakeRepoStorage : PlatformExpenseStorage {
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

class FakeRepoPreferences : AppPreferences {
    private var onboardingDone = false
    private var currency = "₪"
    private var weeklyBudget = 2500.0

    override fun isOnboardingCompleted(): Boolean = onboardingDone
    override fun setOnboardingCompleted(completed: Boolean) { onboardingDone = completed }
    override fun getPreferredCurrency(): String = currency
    override fun setPreferredCurrency(currency: String) { this.currency = currency }
    override fun getWeeklyBudget(): Double = weeklyBudget
    override fun setWeeklyBudget(budget: Double) { weeklyBudget = budget }
}

class ExpenseRepositoryTest {

    private lateinit var storage: FakeRepoStorage
    private lateinit var preferences: FakeRepoPreferences
    private lateinit var repository: LocalExpenseRepository

    @BeforeTest
    fun setup() {
        storage = FakeRepoStorage()
        preferences = FakeRepoPreferences()
        repository = LocalExpenseRepository(storage, preferences)
    }

    @Test
    fun testUpdateExpenseAtomically() = runTest {
        repository.initialize()
        val originalItem = ExpenseItem(
            id = "exp_001",
            title = "قهوة",
            amount = 10.0,
            currency = "₪",
            category = ExpenseCategory.DINING,
            timestamp = 1731168000000L,
            payer = Payer("user_1", "أنا", true),
            rawSpokenText = "قهوة بعشرة شيكل"
        )
        repository.saveExpense(originalItem)

        val updatedItem = originalItem.copy(
            title = "قهوة وحلويات",
            amount = 25.0,
            category = ExpenseCategory.DINING
        )
        repository.updateExpense(updatedItem)

        val expenses = repository.getAllExpenses()
        assertEquals(1, expenses.size, "Repository must atomically update without duplicating records")
        val saved = expenses.first()
        assertEquals("قهوة وحلويات", saved.title)
        assertEquals(25.0, saved.amount)

        val flowEmission = repository.expensesFlow.first()
        assertEquals(1, flowEmission.size)
        assertEquals(25.0, flowEmission.first().amount)
    }

    @Test
    fun testUpdateExpenseWhenMultipleItemsExistPreservesOrderAndCount() = runTest {
        repository.initialize()
        val item1 = ExpenseItem(id = "exp_1", title = "item 1", amount = 10.0, currency = "₪", category = ExpenseCategory.DINING, timestamp = 1000L)
        val item2 = ExpenseItem(id = "exp_2", title = "item 2", amount = 20.0, currency = "₪", category = ExpenseCategory.GROCERIES, timestamp = 2000L)
        val item3 = ExpenseItem(id = "exp_3", title = "item 3", amount = 30.0, currency = "₪", category = ExpenseCategory.TRANSPORT, timestamp = 3000L)
        repository.saveExpense(item1)
        repository.saveExpense(item2)
        repository.saveExpense(item3)

        val updatedItem2 = item2.copy(title = "item 2 updated", amount = 25.0)
        repository.updateExpense(updatedItem2)

        val all = repository.getAllExpenses()
        assertEquals(3, all.size, "Total count must remain unchanged after updating single item")
        val updated = all.first { it.id == "exp_2" }
        assertEquals("item 2 updated", updated.title)
        assertEquals(25.0, updated.amount)
    }

    @Test
    fun testExportCsvFormat() {
        val expenses = listOf(
            ExpenseItem(
                id = "exp_1",
                title = "بنزين",
                amount = 50.0,
                currency = "₪",
                category = ExpenseCategory.TRANSPORT,
                timestamp = 1731168000000L,
                payer = Payer("u1", "أنا", true),
                rawSpokenText = "بنزين بخمسين شيكل"
            ),
            ExpenseItem(
                id = "exp_2",
                title = "خبز وحليب",
                amount = 15.0,
                currency = "₪",
                category = ExpenseCategory.GROCERIES,
                timestamp = 1731168100000L,
                payer = Payer("u1", "أنا", true),
                rawSpokenText = "خبز وحليب بخمسطاشر"
            )
        )

        val csv = repository.exportCsv(expenses)

        assertTrue(csv.isNotBlank(), "CSV export must not be blank")
        assertEquals(3, csv.trim().lines().size, "CSV must contain header row and exactly 2 expense rows")
        assertTrue(csv.contains("id,title,amount,currency,category,timestamp"), "CSV must include standard column headers")
        assertTrue(csv.contains("بنزين"), "CSV must include expense title")
        assertTrue(csv.contains("50.0"), "CSV must include expense amount")
        assertTrue(csv.contains("TRANSPORT"), "CSV must include expense category")
        assertTrue(csv.contains("خبز وحليب"), "CSV must include second expense title")
    }

    @Test
    fun testExportCsvEmptyListReturnsHeader() {
        val csv = repository.exportCsv(emptyList())
        assertTrue(csv.isNotBlank(), "Empty CSV export must still include header row")
        assertTrue(csv.contains("id,title,amount,currency,category,timestamp"))
    }

    @Test
    fun testExportCsvWithCommasAndQuotesInTitleEscapesCorrectly() {
        val expenses = listOf(
            ExpenseItem(
                id = "exp_comma_quote",
                title = """قهوة "العميد", وحلويات""",
                amount = 35.0,
                currency = "₪",
                category = ExpenseCategory.DINING,
                timestamp = 1731168000000L,
                payer = Payer("u1", "أنا", true),
                rawSpokenText = "قهوة وحلويات بخمسة وثلاثين"
            )
        )
        val csv = repository.exportCsv(expenses)
        assertTrue(csv.isNotBlank())
        val lines = csv.trim().lines()
        assertEquals(2, lines.size, "CSV must contain exactly header line and 1 data line")
        // RFC 4180: Fields containing commas and double quotes must be wrapped in quotes, and internal quotes must be doubled
        assertTrue(
            lines[1].contains(""""قهوة ""العميد"", وحلويات""""),
            "Title containing commas and quotes must escape internal quotes as double-quotes and be enclosed in quotes per RFC 4180"
        )
        assertTrue(lines[1].contains("35.0"))
        assertTrue(lines[1].contains("DINING"))
    }

    @Test
    fun testExportJsonFormat() {
        val expenses = listOf(
            ExpenseItem(
                id = "exp_1",
                title = "بنزين",
                amount = 50.0,
                currency = "₪",
                category = ExpenseCategory.TRANSPORT,
                timestamp = 1731168000000L,
                payer = Payer("u1", "أنا", true),
                rawSpokenText = "بنزين بخمسين شيكل"
            )
        )

        val jsonOutput = repository.exportJson(expenses)

        assertTrue(jsonOutput.isNotBlank(), "JSON export must not be blank")
        val decoded = Json.decodeFromString<List<ExpenseItem>>(jsonOutput)
        assertEquals(1, decoded.size, "Exported JSON must decode to exactly 1 ExpenseItem")
        assertEquals("exp_1", decoded[0].id)
        assertEquals("بنزين", decoded[0].title)
        assertEquals(50.0, decoded[0].amount)
        assertEquals(ExpenseCategory.TRANSPORT, decoded[0].category)
    }

    @Test
    fun testExportJsonEmptyListReturnsEmptyJsonArray() {
        val jsonOutput = repository.exportJson(emptyList())
        assertTrue(jsonOutput.isNotBlank(), "Empty JSON export must not be blank")
        assertEquals("[]", jsonOutput.trim(), "Exporting empty list must produce empty JSON array '[]'")
    }

    @Test
    fun testWeeklyBudgetPreferences() {
        // Default weekly budget configuration check
        val defaultBudget = preferences.getWeeklyBudget()
        assertEquals(2500.0, defaultBudget)

        // Update weekly budget
        preferences.setWeeklyBudget(3500.0)
        assertEquals(3500.0, preferences.getWeeklyBudget())

        // Boundary edge case: zero budget
        preferences.setWeeklyBudget(0.0)
        assertEquals(0.0, preferences.getWeeklyBudget())
    }
}
