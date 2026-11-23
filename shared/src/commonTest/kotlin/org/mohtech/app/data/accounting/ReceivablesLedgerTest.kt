package org.mohtech.app.data.accounting

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.mohtech.app.data.model.Allocation
import org.mohtech.app.data.model.DebtStatus
import org.mohtech.app.data.model.ExpenseCategory
import org.mohtech.app.data.model.ExpenseItem
import org.mohtech.app.data.model.ExpenseSplit
import org.mohtech.app.data.model.Payer

class ReceivablesLedgerTest {

    private val selfUser = Payer(id = "self", name = "أنا", isCurrentUser = true)
    private val ahmad = Payer(id = "user_ahmad", name = "أحمد", isCurrentUser = false)
    private val khaled = Payer(id = "user_khaled", name = "خالد", isCurrentUser = false)

    // 1. myShare enters budget without total
    @Test
    fun testMyShareEnteringBudgetWithoutTotal() {
        // Total expense is 100.0 ILS paid by user, but user's personal share is only 30.0 ILS.
        // Ahmad owes the remaining 70.0 ILS.
        val allocation = Allocation(
            id = "alloc_001",
            refNo = 101L,
            party = ahmad,
            amount = 70.0,
            note = "عشاء لأحمد"
        )
        val split = ExpenseSplit(
            totalAmount = 100.0,
            payer = selfUser,
            participants = listOf(selfUser, ahmad),
            amountPerPerson = 50.0,
            allocations = listOf(allocation)
        )
        val expense = ExpenseItem(
            id = "exp_001",
            title = "عشاء شاورما",
            amount = 100.0,
            currency = "₪",
            category = ExpenseCategory.DINING,
            timestamp = 1731168000000L,
            payer = selfUser,
            split = split,
            myShare = 30.0
        )

        // Effective amount for budget calculations must be myShare (30.0), NOT total amount (100.0)
        assertEquals(30.0, expense.effectiveAmount, "Effective amount must equal myShare")
        assertEquals(100.0, expense.amount, "Raw total amount remains unchanged for audit trail")

        // The receivables ledger tracks Ahmad's debt for the remaining 70.0
        val comp = ReceivablesLedger.computeAllocation(allocation, expense.currency)
        assertEquals(70.0, comp.totalAmount)
        assertEquals(70.0, comp.remainingAmount)
        assertEquals(0.0, comp.settledAmount)
        assertEquals(DebtStatus.OPEN, comp.status)

        // Verify mathematical integrity: allocations + myShare == total
        val integrity = LedgerIntegrityChecker.verify(listOf(expense), 1731168000000L)
        assertTrue(integrity.isValid, "Integrity check must pass when allocations + myShare == total")
    }

    // 2. allocations + myShare == total (including JOD with 3 decimals and remainder distribution)
    @Test
    fun testEvenSplitWithJodThreeDecimalsAndRemainderDistribution() {
        // 10.000 JOD split equally among 3 participants (User + Ahmad + Khaled)
        // 10.000 JOD = 10,000 fils.
        // 10,000 / 3 = 3,333 fils each, with remainder of 1 fils.
        // Ahmad and Khaled get 3.333 JOD each.
        // User gets 3.333 + 0.001 = 3.334 JOD.
        val splitResult = Money.distributeSplitEvenly(
            totalAmount = 10.0,
            currency = "د.أ",
            totalParticipants = 3
        )

        assertEquals("د.أ", splitResult.currency)
        assertEquals(10.0, splitResult.totalAmount)
        assertEquals(3.334, splitResult.myShare, "User share must absorb 1 fils remainder")
        assertEquals(3.333, splitResult.sharePerOther)
        assertEquals(2, splitResult.otherShares.size)
        assertEquals(3.333, splitResult.otherShares[0])
        assertEquals(3.333, splitResult.otherShares[1])

        // Verify exact equality: otherShares.sum() + myShare == totalAmount
        val othersSumMinor = splitResult.otherShares.sumOf { Money.doubleToMinor(it, "د.أ") }
        val myShareMinor = Money.doubleToMinor(splitResult.myShare, "د.أ")
        val totalMinor = Money.doubleToMinor(splitResult.totalAmount, "د.أ")
        assertEquals(totalMinor, othersSumMinor + myShareMinor, "Minor units must match total perfectly")

        val alloc1 = Allocation(id = "a1", party = ahmad, amount = splitResult.otherShares[0])
        val alloc2 = Allocation(id = "a2", party = khaled, amount = splitResult.otherShares[1])
        val expense = ExpenseItem(
            id = "exp_jod_01",
            title = "غداء عمان",
            amount = 10.0,
            currency = "د.أ",
            category = ExpenseCategory.DINING,
            timestamp = 1731168000000L,
            payer = selfUser,
            split = ExpenseSplit(
                totalAmount = 10.0,
                payer = selfUser,
                participants = listOf(selfUser, ahmad, khaled),
                amountPerPerson = 3.333,
                allocations = listOf(alloc1, alloc2)
            ),
            myShare = splitResult.myShare
        )

        val integrity = LedgerIntegrityChecker.verify(listOf(expense), 1731168000000L)
        assertTrue(integrity.isValid, "Ledger integrity check must pass for JOD 3-decimal split")
    }

    @Test
    fun testEvenSplitWithIlsTwoDecimalsAndRemainderDistribution() {
        // 50.00 ILS split between 3 participants
        // 5,000 agoras / 3 = 1,666 agoras each, remainder = 2 agoras.
        // Others get 16.66 ILS each. User gets 16.68 ILS.
        val splitResult = Money.distributeSplitEvenly(
            totalAmount = 50.0,
            currency = "₪",
            totalParticipants = 3
        )

        assertEquals(50.0, splitResult.totalAmount)
        assertEquals(16.68, splitResult.myShare)
        assertEquals(16.66, splitResult.sharePerOther)

        val sum = splitResult.otherShares.sum() + splitResult.myShare
        assertEquals(50.0, Money.roundAmount(sum, "₪"))
    }

    @Test
    fun testCalculateRemainderShareCustomAllocations() {
        // Total 100.0 SAR, Allocations: 30.0 SAR, 40.0 SAR -> Remainder to user: 30.0 SAR
        val remainder = Money.calculateRemainderShare(100.0, "ر.س", listOf(30.0, 40.0))
        assertEquals(30.0, remainder)

        // Custom allocations with decimals: 100.00 SAR with [33.33, 33.33] -> Remainder = 33.34
        val remainderWithDecimals = Money.calculateRemainderShare(100.0, "ر.س", listOf(33.33, 33.33))
        assertEquals(33.34, remainderWithDecimals)
    }

    // 3. Partial settlement then full settlement then voiding
    @Test
    fun testPartialSettlementThenFullSettlementThenVoidingLifecycle() {
        val fixedNow = 1731168000000L
        val alloc = Allocation(
            id = "alloc_lifecycle",
            refNo = 1001L,
            party = ahmad,
            amount = 100.0,
            note = "سلفة لأحمد"
        )
        var expense = ExpenseItem(
            id = "exp_lifecycle",
            title = "سلفة نقدية",
            amount = 100.0,
            currency = "₪",
            category = ExpenseCategory.GENERAL,
            timestamp = fixedNow - 3600000L,
            payer = selfUser,
            split = ExpenseSplit(
                totalAmount = 100.0,
                payer = selfUser,
                participants = listOf(selfUser, ahmad),
                amountPerPerson = 100.0,
                allocations = listOf(alloc)
            ),
            myShare = 0.0
        )

        // Phase 1: Initial state -> OPEN
        var comp = ReceivablesLedger.computeAllocation(expense.split!!.allocations.first(), expense.currency)
        assertEquals(DebtStatus.OPEN, comp.status)
        assertEquals(0.0, comp.settledAmount)
        assertEquals(100.0, comp.remainingAmount)

        // Phase 2: Partial settlement of 40.0 ILS
        expense = ReceivablesLedger.recordSettlement(
            expense = expense,
            allocationId = "alloc_lifecycle",
            amount = 40.0,
            settledAt = fixedNow - 1800000L,
            note = "دفعة أولى 40 شيكل",
            settlementId = "settle_1",
            nowMillis = fixedNow
        )

        comp = ReceivablesLedger.computeAllocation(expense.split!!.allocations.first(), expense.currency)
        assertEquals(DebtStatus.PARTIAL, comp.status)
        assertEquals(40.0, comp.settledAmount)
        assertEquals(60.0, comp.remainingAmount)
        assertEquals(1, comp.settlements.size)
        assertEquals(fixedNow - 1800000L, comp.lastSettledAt)

        // Phase 3: Settle the remaining 60.0 ILS in full
        expense = ReceivablesLedger.recordSettlement(
            expense = expense,
            allocationId = "alloc_lifecycle",
            amount = 60.0,
            settledAt = fixedNow,
            note = "سداد كامل المبلغ",
            settlementId = "settle_2",
            nowMillis = fixedNow
        )

        comp = ReceivablesLedger.computeAllocation(expense.split!!.allocations.first(), expense.currency)
        assertEquals(DebtStatus.SETTLED, comp.status)
        assertEquals(100.0, comp.settledAmount)
        assertEquals(0.0, comp.remainingAmount)
        assertEquals(2, comp.settlements.size)
        assertEquals(fixedNow, comp.lastSettledAt)

        // Phase 4: Void the first settlement (40.0 ILS)
        val voidTimestamp = fixedNow + 60000L
        expense = ReceivablesLedger.voidSettlement(
            expense = expense,
            settlementId = "settle_1",
            voidedAt = voidTimestamp
        )

        comp = ReceivablesLedger.computeAllocation(expense.split!!.allocations.first(), expense.currency)
        assertEquals(DebtStatus.PARTIAL, comp.status, "Status must revert to PARTIAL after voiding first settlement")
        assertEquals(60.0, comp.settledAmount)
        assertEquals(40.0, comp.remainingAmount)
        assertEquals(2, comp.settlements.size, "Voided settlement remains preserved in audit trail")
        assertNotNull(comp.settlements.first { it.id == "settle_1" }.voidedAt)

        // Phase 5: Void the second settlement (60.0 ILS) -> Reverts to OPEN
        expense = ReceivablesLedger.voidSettlement(
            expense = expense,
            settlementId = "settle_2",
            voidedAt = voidTimestamp + 1000L
        )

        comp = ReceivablesLedger.computeAllocation(expense.split!!.allocations.first(), expense.currency)
        assertEquals(DebtStatus.OPEN, comp.status, "Status must revert to OPEN when all settlements are voided")
        assertEquals(0.0, comp.settledAmount)
        assertEquals(100.0, comp.remainingAmount)
    }

    // 4. Reject overpayment and reject future settledAt
    @Test
    fun testRejectOverpayment() {
        val now = 1731168000000L
        val alloc = Allocation(id = "alloc_overpay", party = ahmad, amount = 50.0)
        val expense = ExpenseItem(
            id = "exp_overpay",
            title = "بنزين",
            amount = 50.0,
            currency = "₪",
            category = ExpenseCategory.TRANSPORT,
            timestamp = now,
            payer = selfUser,
            split = ExpenseSplit(
                totalAmount = 50.0,
                payer = selfUser,
                participants = listOf(selfUser, ahmad),
                amountPerPerson = 50.0,
                allocations = listOf(alloc)
            ),
            myShare = 0.0
        )

        // Attempting to settle 50.01 on a 50.00 debt must throw
        val exception = assertFailsWith<IllegalArgumentException> {
            ReceivablesLedger.recordSettlement(
                expense = expense,
                allocationId = "alloc_overpay",
                amount = 50.01,
                settledAt = now,
                nowMillis = now
            )
        }
        assertTrue(exception.message!!.contains("exceeds remaining debt"))
    }

    @Test
    fun testRejectFutureSettledAt() {
        val now = 1731168000000L
        val futureTime = now + 86400000L // 1 day in the future
        val alloc = Allocation(id = "alloc_future", party = ahmad, amount = 50.0)
        val expense = ExpenseItem(
            id = "exp_future",
            title = "بنزين",
            amount = 50.0,
            currency = "₪",
            category = ExpenseCategory.TRANSPORT,
            timestamp = now,
            payer = selfUser,
            split = ExpenseSplit(
                totalAmount = 50.0,
                payer = selfUser,
                participants = listOf(selfUser, ahmad),
                amountPerPerson = 50.0,
                allocations = listOf(alloc)
            ),
            myShare = 0.0
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            ReceivablesLedger.recordSettlement(
                expense = expense,
                allocationId = "alloc_future",
                amount = 20.0,
                settledAt = futureTime,
                nowMillis = now
            )
        }
        assertTrue(exception.message!!.contains("future"))
    }

    @Test
    fun testRejectNonPositiveSettlementAmount() {
        val now = 1731168000000L
        val alloc = Allocation(id = "alloc_zero", party = ahmad, amount = 50.0)
        val expense = ExpenseItem(
            id = "exp_zero",
            title = "قهوة",
            amount = 50.0,
            currency = "₪",
            category = ExpenseCategory.DINING,
            timestamp = now,
            payer = selfUser,
            split = ExpenseSplit(
                totalAmount = 50.0,
                payer = selfUser,
                participants = listOf(selfUser, ahmad),
                amountPerPerson = 50.0,
                allocations = listOf(alloc)
            ),
            myShare = 0.0
        )

        assertFailsWith<IllegalArgumentException> {
            ReceivablesLedger.recordSettlement(
                expense = expense,
                allocationId = "alloc_zero",
                amount = 0.0,
                settledAt = now,
                nowMillis = now
            )
        }

        assertFailsWith<IllegalArgumentException> {
            ReceivablesLedger.recordSettlement(
                expense = expense,
                allocationId = "alloc_zero",
                amount = -10.0,
                settledAt = now,
                nowMillis = now
            )
        }
    }

    // 5. Both debt directions and net balance
    @Test
    fun testBothDebtDirectionsAndNetBalanceCalculation() {
        val now = 1731168000000L

        // Direction 1 (Receivable): User paid 120 ILS for dinner, split with Ahmad (Ahmad owes user 60 ILS)
        val alloc1 = Allocation(id = "alloc_dir1", party = ahmad, amount = 60.0)
        val expense1 = ExpenseItem(
            id = "exp_dir1",
            title = "عشاء مطعم",
            amount = 120.0,
            currency = "₪",
            category = ExpenseCategory.DINING,
            timestamp = now - 7200000L,
            payer = selfUser,
            split = ExpenseSplit(
                totalAmount = 120.0,
                payer = selfUser,
                participants = listOf(selfUser, ahmad),
                amountPerPerson = 60.0,
                allocations = listOf(alloc1)
            ),
            myShare = 60.0
        )

        // Direction 2 (Payable): Ahmad paid 40 ILS for coffee, split with User (User owes Ahmad 20 ILS)
        val alloc2 = Allocation(id = "alloc_dir2", party = selfUser, amount = 20.0)
        val expense2 = ExpenseItem(
            id = "exp_dir2",
            title = "قهوة وكافيه",
            amount = 40.0,
            currency = "₪",
            category = ExpenseCategory.DINING,
            timestamp = now - 3600000L,
            payer = ahmad,
            split = ExpenseSplit(
                totalAmount = 40.0,
                payer = ahmad,
                participants = listOf(selfUser, ahmad),
                amountPerPerson = 20.0,
                allocations = listOf(alloc2)
            ),
            myShare = 20.0
        )

        val expenses = listOf(expense1, expense2)
        val summary = ReceivablesLedger.getPartySummary("user_ahmad", "₪", expenses)

        assertNotNull(summary, "Summary for Ahmad must be computed")
        assertEquals(60.0, summary.totalReceivable, "Ahmad owes user 60 ILS")
        assertEquals(20.0, summary.totalPayable, "User owes Ahmad 20 ILS")
        assertEquals(60.0, summary.totalRemainingToMe)
        assertEquals(20.0, summary.totalRemainingByMe)
        assertEquals(40.0, summary.netBalance, "Net balance must be +40.0 ILS (Ahmad owes user 40 ILS net)")

        // Scenario: Ahmad settles 40 ILS of what he owes
        val updatedExpense1 = ReceivablesLedger.recordSettlement(
            expense = expense1,
            allocationId = "alloc_dir1",
            amount = 40.0,
            settledAt = now,
            settlementId = "settle_dir1",
            nowMillis = now
        )

        val updatedSummary = ReceivablesLedger.getPartySummary("user_ahmad", "₪", listOf(updatedExpense1, expense2))
        assertNotNull(updatedSummary)
        assertEquals(60.0, updatedSummary.totalReceivable)
        assertEquals(40.0, updatedSummary.totalSettledToMe)
        assertEquals(20.0, updatedSummary.totalRemainingToMe)
        assertEquals(20.0, updatedSummary.totalRemainingByMe)
        assertEquals(0.0, updatedSummary.netBalance, "Net balance must be 0.0 (all even)")
    }

    // 6. Currency Isolation
    @Test
    fun testCurrencyIsolationEnforced() {
        val moneyIls = Money.of(100.0, "₪")
        val moneySar = Money.of(100.0, "ر.س")

        val exception = assertFailsWith<IllegalArgumentException> {
            moneyIls + moneySar
        }
        assertTrue(exception.message!!.contains("Currency mismatch"))
    }

    // 7. Ledger Integrity Checker detects mathematical mismatches
    @Test
    fun testLedgerIntegrityCheckerDetectsDiscrepancies() {
        val now = 1731168000000L
        // Tampered expense: Total is 100.0 ILS, but allocation is 60.0 and myShare is 30.0 (sum is 90 != 100)
        val badAlloc = Allocation(id = "bad_alloc", party = ahmad, amount = 60.0)
        val tamperedExpense = ExpenseItem(
            id = "exp_tampered",
            title = "حساب خاطئ",
            amount = 100.0,
            currency = "₪",
            category = ExpenseCategory.GENERAL,
            timestamp = now,
            payer = selfUser,
            split = ExpenseSplit(
                totalAmount = 100.0,
                payer = selfUser,
                participants = listOf(selfUser, ahmad),
                amountPerPerson = 60.0,
                allocations = listOf(badAlloc)
            ),
            myShare = 30.0
        )

        val result = LedgerIntegrityChecker.verify(listOf(tamperedExpense), now)
        assertFalse(result.isValid, "Integrity check must fail when allocations + myShare != total")
        assertEquals(1, result.errors.size)
        assertTrue(result.errors.first().contains("mathematical mismatch"))
    }

    // 8. Payable debt settlement lifecycle (user owes someone else and settles with them)
    @Test
    fun testPayableSettlementLifecycle() {
        val now = 1731168000000L
        val allocPayable = Allocation(id = "alloc_user_owes", party = selfUser, amount = 80.0)
        var expense = ExpenseItem(
            id = "exp_payable_life",
            title = "عشاء دفعه أحمد",
            amount = 160.0,
            currency = "₪",
            category = ExpenseCategory.DINING,
            timestamp = now - 3600000L,
            payer = ahmad,
            split = ExpenseSplit(
                totalAmount = 160.0,
                payer = ahmad,
                participants = listOf(selfUser, ahmad),
                amountPerPerson = 80.0,
                allocations = listOf(allocPayable)
            ),
            myShare = 80.0
        )

        // Phase 1: User owes Ahmad 80 ILS
        var summary = ReceivablesLedger.getPartySummary("user_ahmad", "₪", listOf(expense))
        assertNotNull(summary)
        assertEquals(80.0, summary.totalPayable)
        assertEquals(0.0, summary.totalSettledByMe)
        assertEquals(80.0, summary.totalRemainingByMe)
        assertEquals(-80.0, summary.netBalance, "User owes Ahmad 80 ILS -> net is -80")

        // Phase 2: User pays Ahmad 30 ILS
        expense = ReceivablesLedger.recordSettlement(
            expense = expense,
            allocationId = "alloc_user_owes",
            amount = 30.0,
            settledAt = now - 1800000L,
            note = "دفعت لأحمد 30 شيكل",
            settlementId = "settle_pay_1",
            nowMillis = now
        )

        summary = ReceivablesLedger.getPartySummary("user_ahmad", "₪", listOf(expense))
        assertNotNull(summary)
        assertEquals(80.0, summary.totalPayable)
        assertEquals(30.0, summary.totalSettledByMe)
        assertEquals(50.0, summary.totalRemainingByMe)
        assertEquals(-50.0, summary.netBalance)

        // Phase 3: User pays remaining 50 ILS
        expense = ReceivablesLedger.recordSettlement(
            expense = expense,
            allocationId = "alloc_user_owes",
            amount = 50.0,
            settledAt = now,
            settlementId = "settle_pay_2",
            nowMillis = now
        )

        summary = ReceivablesLedger.getPartySummary("user_ahmad", "₪", listOf(expense))
        assertNotNull(summary)
        assertEquals(80.0, summary.totalPayable)
        assertEquals(80.0, summary.totalSettledByMe)
        assertEquals(0.0, summary.totalRemainingByMe)
        assertEquals(0.0, summary.netBalance, "All settled -> net is 0")

        val comp = ReceivablesLedger.getAllocationComputations(listOf(expense)).first()
        assertEquals(DebtStatus.SETTLED, comp.status)
        assertEquals(0.0, comp.remainingAmount)
        assertFalse(comp.isOwedToMe)
    }

    // 9. Precision, currency aliases, and Half-up rounding validation
    @Test
    fun testCurrencyAliasesAndHalfUpRounding() {
        assertEquals(3, Money.getCurrencyDecimals("د.أ"))
        assertEquals(3, Money.getCurrencyDecimals("JOD"))
        assertEquals(3, Money.getCurrencyDecimals("JD"))
        assertEquals(3, Money.getCurrencyDecimals("دينار"))
        assertEquals(3, Money.getCurrencyDecimals("دينار أردني"))

        assertEquals(2, Money.getCurrencyDecimals("₪"))
        assertEquals(2, Money.getCurrencyDecimals("ILS"))
        assertEquals(2, Money.getCurrencyDecimals("ر.س"))
        assertEquals(2, Money.getCurrencyDecimals("SAR"))
        assertEquals(2, Money.getCurrencyDecimals("$"))
        assertEquals(2, Money.getCurrencyDecimals("USD"))

        // Exact Half-up rounding for floating point boundary: 1.005 with 2 decimals -> 1.01
        assertEquals(101L, Money.doubleToMinor(1.005, "₪"))
        assertEquals(1.01, Money.roundAmount(1.005, "₪"))

        // Distribute split evenly rejects negative amounts
        assertFailsWith<IllegalArgumentException> {
            Money.distributeSplitEvenly(-10.0, "₪", 2)
        }
    }

    // 10. Party name management: rename and merge without balance fracture
    @Test
    fun testPartyRenameAndMerge() {
        val expense1 = ExpenseItem(
            id = "exp_01",
            title = "غداء عمل",
            amount = 100.0,
            currency = "₪",
            category = ExpenseCategory.DINING,
            timestamp = 1731168000000L,
            payer = selfUser,
            split = ExpenseSplit(
                totalAmount = 100.0,
                payer = selfUser,
                participants = listOf(selfUser, Payer(id = "p1", name = "أحمد محمد")),
                amountPerPerson = 50.0,
                allocations = listOf(
                    Allocation(id = "a1", party = Payer(id = "p1", name = "أحمد محمد"), amount = 50.0)
                )
            ),
            myShare = 50.0
        )

        val expense2 = ExpenseItem(
            id = "exp_02",
            title = "قهوة",
            amount = 30.0,
            currency = "₪",
            category = ExpenseCategory.DINING,
            timestamp = 1731168100000L,
            payer = selfUser,
            split = ExpenseSplit(
                totalAmount = 30.0,
                payer = selfUser,
                participants = listOf(selfUser, Payer(id = "p2", name = "احمد")),
                amountPerPerson = 15.0,
                allocations = listOf(
                    Allocation(id = "a2", party = Payer(id = "p2", name = "احمد"), amount = 15.0)
                )
            ),
            myShare = 15.0
        )

        val list = listOf(expense1, expense2)

        // Rename "أحمد محمد" to "أحمد م."
        val renamed = ReceivablesLedger.renameParty(list, "أحمد محمد", "أحمد م.")
        assertEquals("أحمد م.", renamed[0].split?.allocations?.first()?.party?.name)

        // Merge "احمد" into "أحمد م."
        val merged = ReceivablesLedger.mergeParties(renamed, "احمد", "أحمد م.")
        assertEquals("أحمد م.", merged[1].split?.allocations?.first()?.party?.name)

        // Check summaries: both allocations now belong to "أحمد م." with total 65.0
        val summaries = ReceivablesLedger.getPartySummaries(merged)
        assertEquals(1, summaries.size)
        assertEquals("أحمد م.", summaries[0].party.name)
        assertEquals(65.0, summaries[0].totalRemainingToMe)
    }
}

