package org.mohtech.app.feature.dashboard

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.mohtech.app.ai.nlp.LocalDialectExpenseParser
import org.mohtech.app.data.accounting.ExpenseSplitSplitter
import org.mohtech.app.data.accounting.LedgerIntegrityChecker
import org.mohtech.app.data.accounting.ReceivablesLedger
import org.mohtech.app.data.accounting.SplitCalculator
import org.mohtech.app.data.model.Allocation
import org.mohtech.app.data.model.DebtStatus
import org.mohtech.app.data.model.ExpenseBatch
import org.mohtech.app.data.model.ExpenseCategory
import org.mohtech.app.data.model.ExpenseItem
import org.mohtech.app.data.model.ExpenseRecordType
import org.mohtech.app.data.model.ExpenseSplit
import org.mohtech.app.data.model.Payer
import org.mohtech.app.data.model.ProofType
import org.mohtech.app.data.repository.LocalExpenseRepository
import org.mohtech.app.data.storage.AppPreferences
import org.mohtech.app.data.storage.PlatformExpenseStorage

private class Phase3FakeStorage : PlatformExpenseStorage {
    private val list = mutableListOf<ExpenseItem>()

    override suspend fun saveExpense(item: ExpenseItem) {
        list.removeAll { it.id == item.id }
        list.add(0, item)
    }

    override suspend fun saveAll(items: List<ExpenseItem>) {
        val ids = items.map { it.id }.toSet()
        list.removeAll { it.id in ids }
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

private class Phase3FakePreferences : AppPreferences {
    private var onboardingDone = true
    private var currency = "₪"
    private var weeklyBudget = 500.0
    private var weekStartDay = 6

    override fun isOnboardingCompleted(): Boolean = onboardingDone
    override fun setOnboardingCompleted(completed: Boolean) { onboardingDone = completed }
    override fun getPreferredCurrency(): String = currency
    override fun setPreferredCurrency(currency: String) { this.currency = currency }
    override fun getWeeklyBudget(): Double = weeklyBudget
    override fun setWeeklyBudget(budget: Double) { weeklyBudget = budget }
    override fun getWeekStartDay(): Int = weekStartDay
    override fun setWeekStartDay(day: Int) { weekStartDay = day }
}

@OptIn(ExperimentalCoroutinesApi::class)
class Phase3M1Test {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var storage: Phase3FakeStorage
    private lateinit var preferences: Phase3FakePreferences
    private lateinit var repository: LocalExpenseRepository
    private lateinit var parser: LocalDialectExpenseParser
    private lateinit var viewModel: ExpenseViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        storage = Phase3FakeStorage()
        preferences = Phase3FakePreferences()
        repository = LocalExpenseRepository(storage, preferences)
        parser = LocalDialectExpenseParser()
        viewModel = ExpenseViewModel(repository, preferences, parser)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildSharedBillBatch(
        now: Long = Clock.System.now().toEpochMilliseconds(),
        totalAmount: Double = 100.0,
        myShare: Double? = 30.0,
        allocations: List<Pair<String, Double>> = listOf("البيت" to 40.0, "أخيك" to 30.0),
        receiptUri: String? = null
    ): ExpenseBatch {
        val selfPayer = Payer(id = "self", name = "أنا", isCurrentUser = true)
        val allocList = allocations.mapIndexed { idx, (name, amt) ->
            Allocation(
                id = "alloc_p3_${now}_$idx",
                party = Payer(id = name, name = name, isCurrentUser = false),
                amount = amt,
                note = "حصة $name"
            )
        }
        val split = ExpenseSplit(
            totalAmount = totalAmount,
            payer = selfPayer,
            participants = listOf(selfPayer) + allocList.map { it.party },
            amountPerPerson = totalAmount / (allocList.size + 1),
            note = "فاتورة مشتركة",
            allocations = allocList
        )
        val item = ExpenseItem(
            id = "exp_shared_${now}",
            title = "سوبرماركت ومستلزمات",
            amount = totalAmount,
            currency = "₪",
            category = ExpenseCategory.GROCERIES,
            timestamp = now,
            payer = selfPayer,
            split = split,
            proofType = if (receiptUri != null) ProofType.RECEIPT_PHOTO else ProofType.NONE,
            rawSpokenText = "سوبرماركت ب 100 شيكل",
            myShare = myShare,
            receiptPhotoPath = receiptUri
        )
        return ExpenseBatch(
            batchId = "batch_shared_$now",
            items = listOf(item),
            totalBatchAmount = totalAmount,
            createdAt = now
        )
    }

    // =========================================================================
    // R1: Editable Voice-Parsed Text
    // =========================================================================

    @Test
    fun testR1_updatePendingRawTextUpdatesTextWithoutReparsing() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.handleIntent(ExpenseIntent.ParseManualSpeechText("قهوة بعشرة شيكل"))
        advanceUntilIdle()

        val before = viewModel.uiState.value.reviewSheetPendingBatch
        assertNotNull(before)
        val beforeItem = before.items.first()
        assertEquals(10.0, beforeItem.amount)
        assertEquals(ExpenseCategory.DINING, beforeItem.category)

        // User edits text to contain a completely different amount & category — must NOT auto-reparse
        val editedText = "بنزين ب 500 شيكل بيني وبين أحمد"
        viewModel.handleIntent(ExpenseIntent.UpdatePendingRawText(editedText))
        advanceUntilIdle()

        val after = viewModel.uiState.value.reviewSheetPendingBatch
        assertNotNull(after)
        val afterItem = after.items.first()
        assertEquals(editedText, afterItem.rawSpokenText)
        assertEquals(beforeItem.amount, afterItem.amount)
        assertEquals(beforeItem.title, afterItem.title)
        assertEquals(beforeItem.category, afterItem.category)
        assertEquals(beforeItem.split, afterItem.split)
    }

    @Test
    fun testR1_updatePendingRawTextAllowsBlankWithoutDiscardingBatch() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.handleIntent(ExpenseIntent.ParseManualSpeechText("قهوة بعشرة شيكل"))
        advanceUntilIdle()

        viewModel.handleIntent(ExpenseIntent.UpdatePendingRawText(""))
        advanceUntilIdle()

        val pending = viewModel.uiState.value.reviewSheetPendingBatch
        assertNotNull(pending)
        assertEquals("", pending.items.first().rawSpokenText)
        assertEquals(10.0, pending.items.first().amount)
    }

    @Test
    fun testR1_updatePendingRawTextWhenNoBatchIsSafeNoOp() = runTest(testDispatcher) {
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.reviewSheetPendingBatch)

        viewModel.handleIntent(ExpenseIntent.UpdatePendingRawText("أي نص"))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.reviewSheetPendingBatch)
    }

    @Test
    fun testR1_reparsePendingTextUpdatesParsedFieldsAndPreservesReceiptUri() = runTest(testDispatcher) {
        advanceUntilIdle()
        val receiptUri = "file:///data/user/0/org.mohtech.app/files/receipts/rcpt_01.jpg"
        val initialBatch = buildSharedBillBatch(receiptUri = receiptUri)
        viewModel.handleIntent(ExpenseIntent.EditExpense(initialBatch.items.first()))
        advanceUntilIdle()

        val editedText = "بنزين ب 80 شيكل بيني وبين خالد"
        viewModel.handleIntent(ExpenseIntent.UpdatePendingRawText(editedText))
        advanceUntilIdle()
        viewModel.handleIntent(ExpenseIntent.ReparsePendingText(editedText))
        advanceUntilIdle()

        val reparsed = viewModel.uiState.value.reviewSheetPendingBatch
        assertNotNull(reparsed)
        assertEquals(initialBatch.items.first().let { "batch_edit_${it.id}" }, reparsed.batchId)
        assertEquals(1, reparsed.items.size)
        val item = reparsed.items.first()
        assertEquals(editedText, item.rawSpokenText)
        assertEquals(80.0, item.amount)
        assertEquals(ExpenseCategory.TRANSPORT, item.category)
        assertNotNull(item.split)
        assertEquals(receiptUri, item.receiptPhotoPath)
    }

    @Test
    fun testR1_reparsePendingTextMultiItemPreservesBillReceiptUri() = runTest(testDispatcher) {
        advanceUntilIdle()
        val receiptUri = "file:///sandbox/receipts/bill_multi.jpg"
        val now = Clock.System.now().toEpochMilliseconds()
        val draftItem = ExpenseItem(
            id = "exp_draft_multi",
            title = "مسودة",
            amount = 20.0,
            currency = "₪",
            category = ExpenseCategory.GENERAL,
            timestamp = now,
            receiptPhotoPath = receiptUri,
            rawSpokenText = "مسودة"
        )
        viewModel.handleIntent(ExpenseIntent.EditExpense(draftItem))
        advanceUntilIdle()

        viewModel.handleIntent(ExpenseIntent.ReparsePendingText("دفعت 30 بنزين و15 قهوة"))
        advanceUntilIdle()

        val reparsed = viewModel.uiState.value.reviewSheetPendingBatch
        assertNotNull(reparsed)
        assertTrue(reparsed.items.size >= 2, "Expected multi-item parse to produce >= 2 items")
        assertTrue(reparsed.items.all { it.receiptPhotoPath == receiptUri })
    }

    // =========================================================================
    // R3: Programmatic Record Splitting & External Party Granularity
    // =========================================================================

    @Test
    fun testR3_confirmSplitBatchCreatesOnePersonalAndNReceivableRecords() = runTest(testDispatcher) {
        advanceUntilIdle()
        val batch = buildSharedBillBatch(
            totalAmount = 100.0,
            myShare = 30.0,
            allocations = listOf("البيت" to 40.0, "أخيك" to 30.0)
        )

        viewModel.handleIntent(ExpenseIntent.ConfirmSplitBatch(batch))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.reviewSheetPendingBatch)

        // Personal expense list contains ONLY the 1 PERSONAL record (30 ILS)
        assertEquals(1, state.recentExpenses.size)
        val personal = state.recentExpenses.first()
        assertEquals(ExpenseRecordType.PERSONAL, personal.recordType)
        assertEquals(30.0, personal.amount)
        assertEquals(30.0, personal.effectiveAmount)
        assertNull(personal.split)
        assertNotNull(personal.splitGroupId)

        // Receivables list contains ONLY the 2 independent RECEIVABLE records (40 and 30 ILS)
        assertEquals(2, state.receivableExpenses.size)
        assertTrue(state.receivableExpenses.all { it.recordType == ExpenseRecordType.RECEIVABLE })
        assertTrue(state.receivableExpenses.all { it.splitGroupId == personal.splitGroupId })
        assertTrue(state.receivableExpenses.all { it.effectiveAmount == 0.0 })
        assertEquals(setOf(40.0, 30.0), state.receivableExpenses.map { it.amount }.toSet())
        assertEquals(2, state.receivableExpenses.map { it.id }.toSet().size)

        // Totals & reports reflect ONLY personal spend + external receivables cleanly
        assertEquals(30.0, state.todayTotal)
        assertEquals(30.0, state.weeklyTotal)
        assertEquals(70.0, state.receivablesTotalRemaining)
        val report = state.reportData
        assertNotNull(report)
        assertEquals(1, report.totalOperationsCount)

        // All stored records pass LedgerIntegrityChecker
        val allStored = repository.getAllExpenses()
        assertEquals(3, allStored.size)
        val audit = LedgerIntegrityChecker.verify(allStored)
        assertTrue(audit.isValid, "Integrity errors: ${audit.errors}")
    }

    @Test
    fun testR3_independentSettlementAndVoidPerParty() = runTest(testDispatcher) {
        advanceUntilIdle()
        val now = Clock.System.now().toEpochMilliseconds()
        val batch = buildSharedBillBatch(
            now = now,
            totalAmount = 100.0,
            myShare = 30.0,
            allocations = listOf("البيت" to 40.0, "أخيك" to 30.0)
        )

        viewModel.handleIntent(ExpenseIntent.ConfirmSplitBatch(batch))
        advanceUntilIdle()

        val baytRecord = viewModel.uiState.value.receivableExpenses.first {
            it.split?.allocations?.firstOrNull()?.party?.name == "البيت"
        }
        val baytAllocId = baytRecord.split!!.allocations.first().id

        // Settle "البيت" (40 ILS) completely
        viewModel.handleIntent(
            ExpenseIntent.RecordSettlement(
                expenseId = baytRecord.id,
                allocationId = baytAllocId,
                amount = 40.0,
                settledAt = now
            )
        )
        advanceUntilIdle()

        val afterSettle = viewModel.uiState.value
        assertEquals(30.0, afterSettle.todayTotal)
        assertEquals(30.0, afterSettle.receivablesTotalRemaining)

        val compsAfterSettle = ReceivablesLedger.getAllocationComputations(afterSettle.receivableExpenses)
        val baytComp = compsAfterSettle.first { it.party.name == "البيت" }
        val brotherComp = compsAfterSettle.first { it.party.name == "أخيك" }
        assertEquals(DebtStatus.SETTLED, baytComp.status)
        assertEquals(DebtStatus.OPEN, brotherComp.status)
        assertEquals(30.0, brotherComp.remainingAmount)

        // Void "البيت" settlement -> restores 70.0 receivables remaining
        val settlementId = baytComp.settlements.first().id
        viewModel.handleIntent(ExpenseIntent.VoidSettlement(baytRecord.id, settlementId))
        advanceUntilIdle()

        assertEquals(70.0, viewModel.uiState.value.receivablesTotalRemaining)

        // Soft-delete one receivable record leaves the other receivable and personal record intact
        viewModel.handleIntent(ExpenseIntent.DeleteExpense(baytRecord.id))
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.receivableExpenses.size)
        assertEquals(30.0, viewModel.uiState.value.receivablesTotalRemaining)
        assertEquals(1, viewModel.uiState.value.recentExpenses.size)
        assertTrue(storage.getAllExpenses().first { it.id == baytRecord.id }.deletedAt != null)
    }

    @Test
    fun testR3_zeroPersonalShareSpawnsOnlyReceivableRecords() = runTest(testDispatcher) {
        advanceUntilIdle()
        val batch = buildSharedBillBatch(
            totalAmount = 100.0,
            myShare = 0.0,
            allocations = listOf("أحمد" to 60.0, "سالم" to 40.0)
        )

        viewModel.handleIntent(ExpenseIntent.ConfirmSplitBatch(batch))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.recentExpenses.isEmpty())
        assertEquals(2, state.receivableExpenses.size)
        assertEquals(0.0, state.todayTotal)
        assertEquals(100.0, state.receivablesTotalRemaining)
    }

    @Test
    fun testR3_nonSplitItemPayableAndLegacyConfirmBatchRemainIntact() = runTest(testDispatcher) {
        advanceUntilIdle()
        val now = Clock.System.now().toEpochMilliseconds()

        // 1. Non-split personal item via ConfirmSplitBatch -> 1 PERSONAL record
        val plainItem = ExpenseItem(
            id = "exp_plain_$now",
            title = "قهوة",
            amount = 15.0,
            currency = "₪",
            category = ExpenseCategory.DINING,
            timestamp = now
        )
        viewModel.handleIntent(
            ExpenseIntent.ConfirmSplitBatch(
                ExpenseBatch("batch_plain_$now", listOf(plainItem), 15.0, now)
            )
        )
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.recentExpenses.size)
        assertEquals(ExpenseRecordType.PERSONAL, viewModel.uiState.value.recentExpenses.first().recordType)

        // 2. Payable item (!payer.isCurrentUser && split != null) -> 1 PAYABLE record
        val ahmad = Payer("ahmad", "أحمد", isCurrentUser = false)
        val self = Payer("self", "أنا", isCurrentUser = true)
        val payableItem = ExpenseItem(
            id = "exp_payable_$now",
            title = "غداء",
            amount = 60.0,
            currency = "₪",
            category = ExpenseCategory.DINING,
            timestamp = now,
            payer = ahmad,
            split = ExpenseSplit(
                totalAmount = 60.0,
                payer = ahmad,
                participants = listOf(self, ahmad),
                amountPerPerson = 30.0
            ),
            myShare = 30.0
        )
        val splitPayable = ExpenseSplitSplitter.splitItem(payableItem, now)
        assertEquals(1, splitPayable.size)
        assertEquals(ExpenseRecordType.PAYABLE, splitPayable.first().recordType)

        // 3. Legacy ConfirmBatch preserves single item with split != null
        viewModel.handleIntent(ExpenseIntent.ParseManualSpeechText("عشاء بستين شيكل بيني وبين أحمد"))
        advanceUntilIdle()
        val legacyBatch = viewModel.uiState.value.reviewSheetPendingBatch!!
        viewModel.handleIntent(ExpenseIntent.ConfirmBatch(legacyBatch))
        advanceUntilIdle()
        val legacySaved = viewModel.uiState.value.recentExpenses.first { it.id == legacyBatch.items.first().id }
        assertNotNull(legacySaved.split)
        assertEquals(60.0, legacySaved.amount)
    }

    @Test
    fun testR3_filterSearchAndReceivablesExcelExcludeAndIncludeCorrectly() = runTest(testDispatcher) {
        advanceUntilIdle()
        val batch = buildSharedBillBatch(
            totalAmount = 100.0,
            myShare = 30.0,
            allocations = listOf("البيت" to 40.0, "أخيك" to 30.0)
        )
        viewModel.handleIntent(ExpenseIntent.ConfirmSplitBatch(batch))
        advanceUntilIdle()

        viewModel.handleIntent(ExpenseIntent.FilterByCategory(ExpenseCategory.GROCERIES))
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.filteredExpenses.size)
        assertEquals(ExpenseRecordType.PERSONAL, viewModel.uiState.value.filteredExpenses.first().recordType)

        viewModel.handleIntent(ExpenseIntent.SearchExpenses("سوبرماركت"))
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.filteredExpenses.size)
        assertTrue(viewModel.uiState.value.filteredExpenses.none { it.recordType == ExpenseRecordType.RECEIVABLE })

        viewModel.handleIntent(ExpenseIntent.ExportReceivablesExcel)
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertFalse(state.isExportingReport)
        assertNull(state.exportErrorMessage)
        assertNotNull(state.exportSuccessPath)
        assertTrue(state.exportSuccessSize > 0L)
    }

    @Test
    fun testR3_legacyJsonWithoutRecordTypeDeserializesSafely() {
        val json = Json { ignoreUnknownKeys = true }
        val legacyJson = """
            {
                "id": "exp_legacy_phase2",
                "title": "عشاء قديم",
                "amount": 60.0,
                "currency": "₪",
                "category": "DINING",
                "timestamp": 1731168000000,
                "myShare": 30.0
            }
        """.trimIndent()

        val decoded = json.decodeFromString<ExpenseItem>(legacyJson)
        assertEquals(ExpenseRecordType.PERSONAL, decoded.recordType)
        assertNull(decoded.splitGroupId)
        assertEquals(30.0, decoded.effectiveAmount)
    }

    // =========================================================================
    // R2: Single-File Receipt URI Linking Without Duplication
    // =========================================================================

    @Test
    fun testR2_singleReceiptUriSharedAcrossAllSplitRecordsWithoutDuplication() = runTest(testDispatcher) {
        advanceUntilIdle()
        val singleUri = "file:///data/user/0/org.mohtech.app/files/receipts/receipt_unique_99.jpg"
        val batch = buildSharedBillBatch(
            totalAmount = 100.0,
            myShare = 30.0,
            allocations = listOf("البيت" to 40.0, "أخيك" to 30.0),
            receiptUri = singleUri
        )

        viewModel.handleIntent(ExpenseIntent.ConfirmSplitBatch(batch))
        advanceUntilIdle()

        val allStored = repository.getAllExpenses()
        assertEquals(3, allStored.size)
        assertTrue(allStored.all { it.receiptPhotoPath == singleUri })
        assertNotEquals(allStored[0].id, allStored[1].id)
        assertNotEquals(allStored[1].id, allStored[2].id)

        // Serialized DB records reference only the URI string — no binary blobs
        val serialized = Json.encodeToString(allStored)
        assertTrue(serialized.contains(singleUri))
        assertFalse(serialized.contains("data:image/"))
        assertFalse(serialized.contains("base64"))
    }

    // =========================================================================
    // R4: Auto-Balancing Split Calculation & Minor-Unit Clamping
    // =========================================================================

    @Test
    fun testR4_computeAutoBalancedSplitCalculatesPersonalShareAndRatios() {
        val summary = SplitCalculator.computeAutoBalancedSplit(
            totalAmount = 100.0,
            currency = "₪",
            partyShares = listOf(40.0, 30.0)
        )

        assertEquals(100.0, summary.totalAmount)
        assertEquals(30.0, summary.personalShare)
        assertEquals(listOf(40.0, 30.0), summary.partyShares)
        assertEquals(0.3f, summary.personalRatio, 0.0001f)
        assertEquals(0.4f, summary.partyRatios[0], 0.0001f)
        assertEquals(0.3f, summary.partyRatios[1], 0.0001f)
    }

    @Test
    fun testR4_clampPartySharePreventsExceedingTotal() {
        val clamped = SplitCalculator.clampPartyShare(
            totalAmount = 100.0,
            currency = "₪",
            otherPartyShares = listOf(40.0),
            requestedAmount = 75.0
        )
        assertEquals(60.0, clamped.clampedAmount)
        assertTrue(clamped.wasClamped)
        assertEquals(60.0, clamped.maxAllowed)

        val valid = SplitCalculator.clampPartyShare(
            totalAmount = 100.0,
            currency = "₪",
            otherPartyShares = listOf(40.0),
            requestedAmount = 30.0
        )
        assertEquals(30.0, valid.clampedAmount)
        assertFalse(valid.wasClamped)
        assertEquals(60.0, valid.maxAllowed)

        val negative = SplitCalculator.clampPartyShare(
            totalAmount = 100.0,
            currency = "₪",
            otherPartyShares = listOf(40.0),
            requestedAmount = -12.0
        )
        assertEquals(0.0, negative.clampedAmount)
        assertTrue(negative.wasClamped)

        val nonFinite = SplitCalculator.clampPartyShare(
            totalAmount = 100.0,
            currency = "₪",
            otherPartyShares = listOf(40.0),
            requestedAmount = Double.NaN
        )
        assertEquals(0.0, nonFinite.clampedAmount)
        assertTrue(nonFinite.wasClamped)
    }

    @Test
    fun testR4_shrinkingBillTotalClampsPartySharesSequentially() {
        val clamped = SplitCalculator.clampSharesToTotal(
            totalAmount = 50.0,
            currency = "₪",
            partyShares = listOf(40.0, 30.0)
        )
        assertEquals(listOf(40.0, 10.0), clamped)

        val summary = SplitCalculator.computeAutoBalancedSplit(
            totalAmount = 50.0,
            currency = "₪",
            partyShares = listOf(40.0, 30.0)
        )
        assertEquals(0.0, summary.personalShare)
        assertEquals(listOf(40.0, 10.0), summary.partyShares)

        val zeroTotal = SplitCalculator.clampSharesToTotal(
            totalAmount = 0.0,
            currency = "₪",
            partyShares = listOf(40.0, 30.0)
        )
        assertEquals(listOf(0.0, 0.0), zeroTotal)
    }

    @Test
    fun testR4_jodThreeDecimalPrecisionAutoBalanceAndClamp() {
        val summary = SplitCalculator.computeAutoBalancedSplit(
            totalAmount = 10.000,
            currency = "د.أ",
            partyShares = listOf(3.333, 3.333)
        )
        assertEquals(3.334, summary.personalShare)

        val clamped = SplitCalculator.clampPartyShare(
            totalAmount = 10.000,
            currency = "د.أ",
            otherPartyShares = listOf(3.333, 3.333),
            requestedAmount = 5.0
        )
        assertEquals(3.334, clamped.clampedAmount)
        assertTrue(clamped.wasClamped)
        assertEquals(3.334, clamped.maxAllowed)

        val shrunk = SplitCalculator.clampSharesToTotal(
            totalAmount = 5.000,
            currency = "د.أ",
            partyShares = listOf(3.333, 3.333)
        )
        assertEquals(listOf(3.333, 1.667), shrunk)
    }

    // =========================================================================
    // Iteration 2 Regression Tests (Challenger Edge-Case Fixes)
    // =========================================================================

    @Test
    fun testR3_mergePartiesUnifiesDistinctPartyIdsIntoSingleSummary() = runTest(testDispatcher) {
        advanceUntilIdle()
        val now = Clock.System.now().toEpochMilliseconds()
        val selfPayer = Payer(id = "self", name = "أنا", isCurrentUser = true)
        val partyA = Payer(id = "pA", name = "أحمد", isCurrentUser = false)
        val partyB = Payer(id = "pB", name = "سالم", isCurrentUser = false)
        val allocA = Allocation(id = "alloc_pa", party = partyA, amount = 40.0)
        val allocB = Allocation(id = "alloc_pb", party = partyB, amount = 30.0)
        val item = ExpenseItem(
            id = "exp_merge_$now",
            title = "عشاء مشترك",
            amount = 100.0,
            currency = "₪",
            category = ExpenseCategory.DINING,
            timestamp = now,
            payer = selfPayer,
            split = ExpenseSplit(
                totalAmount = 100.0,
                payer = selfPayer,
                participants = listOf(selfPayer, partyA, partyB),
                amountPerPerson = 33.33,
                allocations = listOf(allocA, allocB)
            ),
            myShare = 30.0
        )

        viewModel.handleIntent(
            ExpenseIntent.ConfirmSplitBatch(ExpenseBatch("batch_merge_$now", listOf(item), 100.0, now))
        )
        advanceUntilIdle()

        val beforeSummaries = ReceivablesLedger.getPartySummaries(viewModel.uiState.value.receivableExpenses)
        assertEquals(2, beforeSummaries.size)

        // Merge "أحمد" (id="pA") into existing "سالم" (id="pB")
        viewModel.handleIntent(ExpenseIntent.MergeParties(sourceName = "أحمد", targetName = "سالم"))
        advanceUntilIdle()

        val afterExpenses = viewModel.uiState.value.receivableExpenses
        val afterSummaries = ReceivablesLedger.getPartySummaries(afterExpenses)
        assertEquals(1, afterSummaries.size, "Merged parties with distinct IDs must unify into 1 summary")
        val unified = afterSummaries.first()
        assertEquals("pB", unified.party.id)
        assertEquals("سالم", unified.party.name)
        assertEquals(70.0, unified.totalReceivable)
        assertEquals(70.0, unified.totalRemainingToMe)
        assertTrue(
            afterExpenses.all { rec ->
                val recSplit = rec.split
                recSplit != null &&
                    recSplit.allocations.all { it.party.id == "pB" && it.party.name == "سالم" } &&
                    recSplit.participants.all { it.isCurrentUser || (it.id == "pB" && it.name == "سالم") }
            }
        )
    }

    @Test
    fun testR4_clampPartySharePreventsLongOverflowWithHugeShares() {
        val result = SplitCalculator.clampPartyShare(
            totalAmount = 100.0,
            currency = "₪",
            otherPartyShares = listOf(1e18, 1e18),
            requestedAmount = 50.0
        )
        assertEquals(0.0, result.maxAllowed)
        assertEquals(0.0, result.clampedAmount)
        assertTrue(result.wasClamped)
        assertTrue(result.maxAllowed <= 100.0)

        val jodResult = SplitCalculator.clampPartyShare(
            totalAmount = 10.000,
            currency = "د.أ",
            otherPartyShares = listOf(Double.MAX_VALUE, 1e18),
            requestedAmount = 5.0
        )
        assertEquals(0.0, jodResult.maxAllowed)
        assertEquals(0.0, jodResult.clampedAmount)
        assertTrue(jodResult.wasClamped)
    }

    @Test
    fun testR3_editingReceivableAndConfirmSplitBatchUpdatesInPlaceWithoutDuplication() = runTest(testDispatcher) {
        advanceUntilIdle()
        val now = Clock.System.now().toEpochMilliseconds()
        val batch = buildSharedBillBatch(
            now = now,
            totalAmount = 100.0,
            myShare = 30.0,
            allocations = listOf("البيت" to 40.0, "أخيك" to 30.0)
        )
        viewModel.handleIntent(ExpenseIntent.ConfirmSplitBatch(batch))
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.receivableExpenses.size)

        val baytRec = viewModel.uiState.value.receivableExpenses.first {
            it.split?.allocations?.firstOrNull()?.party?.name == "البيت"
        }

        // Edit the existing RECEIVABLE record (40 -> 45) and confirm via ConfirmSplitBatch
        viewModel.handleIntent(ExpenseIntent.EditExpense(baytRec))
        advanceUntilIdle()
        val pending = viewModel.uiState.value.reviewSheetPendingBatch!!
        val origAlloc = pending.items.first().split!!.allocations.first()
        val editedItem = pending.items.first().copy(
            title = "سوبرماركت معدل",
            amount = 45.0,
            myShare = 0.0,
            split = pending.items.first().split!!.copy(
                totalAmount = 45.0,
                amountPerPerson = 45.0,
                allocations = listOf(origAlloc.copy(amount = 45.0))
            )
        )
        viewModel.handleIntent(
            ExpenseIntent.ConfirmSplitBatch(pending.copy(items = listOf(editedItem), totalBatchAmount = 45.0))
        )
        advanceUntilIdle()

        val stateAfterEdit = viewModel.uiState.value
        assertEquals(2, stateAfterEdit.receivableExpenses.size, "Editing a RECEIVABLE must not duplicate debt record")
        val updatedBayt = stateAfterEdit.receivableExpenses.first { it.id == baytRec.id }
        assertEquals(45.0, updatedBayt.amount)
        assertEquals("سوبرماركت معدل", updatedBayt.title)
        assertEquals(75.0, stateAfterEdit.receivablesTotalRemaining)
        assertTrue(LedgerIntegrityChecker.verify(repository.getAllExpenses()).isValid)

        // Also re-split existing RECEIVABLE into personal (15) + receivable (30) -> no stale 45 RECEIVABLE left
        viewModel.handleIntent(ExpenseIntent.EditExpense(updatedBayt))
        advanceUntilIdle()
        val pendingResplit = viewModel.uiState.value.reviewSheetPendingBatch!!
        val resplitItem = pendingResplit.items.first().copy(
            amount = 45.0,
            myShare = 15.0,
            split = pendingResplit.items.first().split!!.copy(
                totalAmount = 45.0,
                allocations = listOf(origAlloc.copy(amount = 30.0))
            )
        )
        viewModel.handleIntent(
            ExpenseIntent.ConfirmSplitBatch(pendingResplit.copy(items = listOf(resplitItem), totalBatchAmount = 45.0))
        )
        advanceUntilIdle()

        val stateAfterResplit = viewModel.uiState.value
        assertEquals(2, stateAfterResplit.recentExpenses.size)
        assertEquals(2, stateAfterResplit.receivableExpenses.size)
        assertEquals(60.0, stateAfterResplit.receivablesTotalRemaining)
        assertTrue(LedgerIntegrityChecker.verify(repository.getAllExpenses()).isValid)
    }

    @Test
    fun testR1_reparsePendingTextPreservesReceiptProofType() = runTest(testDispatcher) {
        advanceUntilIdle()
        val receiptUri = "file:///sandbox/receipts/rcpt_proof_preserve.jpg"
        val itemWithReceipt = buildSharedBillBatch(receiptUri = receiptUri).items.first()
        assertEquals(ProofType.RECEIPT_PHOTO, itemWithReceipt.proofType)

        viewModel.handleIntent(ExpenseIntent.EditExpense(itemWithReceipt))
        advanceUntilIdle()

        viewModel.handleIntent(ExpenseIntent.ReparsePendingText("بنزين 90 شيكل"))
        advanceUntilIdle()

        val reparsedSingle = viewModel.uiState.value.reviewSheetPendingBatch
        assertNotNull(reparsedSingle)
        assertEquals(receiptUri, reparsedSingle.items.first().receiptPhotoPath)
        assertEquals(ProofType.RECEIPT_PHOTO, reparsedSingle.items.first().proofType)

        // Multi-item reparse also preserves ProofType.RECEIPT_PHOTO across all spawned items
        viewModel.handleIntent(ExpenseIntent.ReparsePendingText("دفعت 30 بنزين و15 قهوة"))
        advanceUntilIdle()

        val reparsedMulti = viewModel.uiState.value.reviewSheetPendingBatch
        assertNotNull(reparsedMulti)
        assertTrue(reparsedMulti.items.size >= 2)
        assertTrue(reparsedMulti.items.all { it.receiptPhotoPath == receiptUri && it.proofType == ProofType.RECEIPT_PHOTO })
    }

    @Test
    fun testR3_setPreferredCurrencyRefreshesReceivablesTotalRemaining() = runTest(testDispatcher) {
        advanceUntilIdle()
        val batch = buildSharedBillBatch(
            totalAmount = 100.0,
            myShare = 30.0,
            allocations = listOf("البيت" to 40.0, "أخيك" to 30.0)
        )
        viewModel.handleIntent(ExpenseIntent.ConfirmSplitBatch(batch))
        advanceUntilIdle()
        assertEquals(70.0, viewModel.uiState.value.receivablesTotalRemaining)

        viewModel.handleIntent(ExpenseIntent.SetPreferredCurrency("د.أ"))
        advanceUntilIdle()

        val jodState = viewModel.uiState.value
        assertEquals("د.أ", jodState.activeCurrency)
        assertEquals(0.0, jodState.todayTotal)
        assertEquals(0.0, jodState.weeklyTotal)
        assertEquals(0.0, jodState.receivablesTotalRemaining)

        viewModel.handleIntent(ExpenseIntent.SetPreferredCurrency("₪"))
        advanceUntilIdle()
        assertEquals(70.0, viewModel.uiState.value.receivablesTotalRemaining)
    }

    @Test
    fun testR3_splitItemAssignsUniqueNonBlankIdsAndSanitizesNegativeAndNanAmounts() = runTest(testDispatcher) {
        advanceUntilIdle()
        val now = Clock.System.now().toEpochMilliseconds()
        val blank1 = ExpenseItem(
            id = "",
            title = "قهوة",
            amount = 12.0,
            currency = "₪",
            category = ExpenseCategory.DINING,
            timestamp = now,
            split = null
        )
        val blank2 = ExpenseItem(
            id = "",
            title = "خبز",
            amount = 8.0,
            currency = "₪",
            category = ExpenseCategory.GROCERIES,
            timestamp = now,
            split = null
        )
        val splitBlankBatch = ExpenseBatch("batch_blanks_$now", listOf(blank1, blank2), 20.0, now)
        val spawned = ExpenseSplitSplitter.splitBatch(splitBlankBatch, now)
        assertEquals(2, spawned.size)
        assertTrue(spawned.all { it.id.isNotBlank() })
        assertEquals(2, spawned.map { it.id }.toSet().size)

        viewModel.handleIntent(ExpenseIntent.ConfirmSplitBatch(splitBlankBatch))
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.recentExpenses.size)
        assertEquals(20.0, viewModel.uiState.value.todayTotal)

        // Negative amount with split -> clampedEntries.isEmpty() -> sanitized to 0.0
        val selfPayer = Payer("self", "أنا", isCurrentUser = true)
        val other = Payer("other", "خالد", isCurrentUser = false)
        val negSplitItem = ExpenseItem(
            id = "",
            title = "سالب",
            amount = -10.0,
            currency = "₪",
            category = ExpenseCategory.GENERAL,
            timestamp = now,
            payer = selfPayer,
            split = ExpenseSplit(
                totalAmount = -10.0,
                payer = selfPayer,
                participants = listOf(selfPayer, other),
                amountPerPerson = -5.0,
                allocations = listOf(Allocation("a1", other, 5.0))
            )
        )
        val negResult = ExpenseSplitSplitter.splitItem(negSplitItem, now)
        assertEquals(1, negResult.size)
        assertTrue(negResult.first().id.isNotBlank())
        assertEquals(0.0, negResult.first().amount)
        assertEquals(0.0, negResult.first().effectiveAmount)

        // NaN amount on non-split item -> sanitized to 0.0
        val nanItem = blank1.copy(amount = Double.NaN, myShare = Double.NaN)
        val nanResult = ExpenseSplitSplitter.splitItem(nanItem, now)
        assertEquals(1, nanResult.size)
        assertTrue(nanResult.first().id.isNotBlank())
        assertEquals(0.0, nanResult.first().amount)
        assertEquals(0.0, nanResult.first().effectiveAmount)

        // Empty allocations with negative amount -> sanitized to 0.0
        val emptyAllocNeg = negSplitItem.copy(
            amount = -25.0,
            split = negSplitItem.split!!.copy(allocations = emptyList())
        )
        val emptyAllocResult = ExpenseSplitSplitter.splitItem(emptyAllocNeg, now)
        assertEquals(1, emptyAllocResult.size)
        assertTrue(emptyAllocResult.first().id.isNotBlank())
        assertEquals(0.0, emptyAllocResult.first().amount)

        // PAYABLE branch with -Infinity amount & negative myShare -> sanitized to 0.0
        val payableNeg = ExpenseItem(
            id = "",
            title = "مستحق سالب",
            amount = Double.NEGATIVE_INFINITY,
            currency = "₪",
            category = ExpenseCategory.GENERAL,
            timestamp = now,
            payer = other,
            split = ExpenseSplit(
                totalAmount = 0.0,
                payer = other,
                participants = listOf(selfPayer, other),
                amountPerPerson = 0.0
            ),
            myShare = -15.0
        )
        val payableResult = ExpenseSplitSplitter.splitItem(payableNeg, now)
        assertEquals(1, payableResult.size)
        assertTrue(payableResult.first().id.isNotBlank())
        assertEquals(ExpenseRecordType.PAYABLE, payableResult.first().recordType)
        assertEquals(0.0, payableResult.first().amount)
        assertEquals(0.0, payableResult.first().effectiveAmount)
    }
}
