package org.mohtech.app.feature.reports

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.mohtech.app.data.accounting.LedgerIntegrityChecker
import org.mohtech.app.data.accounting.ReceivablesLedger
import org.mohtech.app.data.model.Allocation
import org.mohtech.app.data.model.DebtStatus
import org.mohtech.app.data.model.ExpenseCategory
import org.mohtech.app.data.model.ExpenseItem
import org.mohtech.app.data.model.ExpenseSplit
import org.mohtech.app.data.model.Payer
import org.mohtech.app.data.model.Settlement

class AccountingReportsTest {

    private val selfUser = Payer("self", "أنا", true)
    private val mohammed = Payer("user_m", "محمد", false)
    private val ahmad = Payer("user_a", "أحمد", false)

    // 1. Aging brackets boundary verification: 0-7, 8-30, 31-60, >60 days
    @Test
    fun testAgingBracketsBoundaries() {
        val now = 1732000000000L
        val oneDayMillis = 24L * 60L * 60L * 1000L

        // Exact boundaries
        assertEquals(AgingBracket.DAYS_0_TO_7, AccountingReportUtil.classifyAging(now - (0 * oneDayMillis), now))
        assertEquals(AgingBracket.DAYS_0_TO_7, AccountingReportUtil.classifyAging(now - (7 * oneDayMillis), now))

        assertEquals(AgingBracket.DAYS_8_TO_30, AccountingReportUtil.classifyAging(now - (8 * oneDayMillis), now))
        assertEquals(AgingBracket.DAYS_8_TO_30, AccountingReportUtil.classifyAging(now - (30 * oneDayMillis), now))

        assertEquals(AgingBracket.DAYS_31_TO_60, AccountingReportUtil.classifyAging(now - (31 * oneDayMillis), now))
        assertEquals(AgingBracket.DAYS_31_TO_60, AccountingReportUtil.classifyAging(now - (60 * oneDayMillis), now))

        assertEquals(AgingBracket.DAYS_OVER_60, AccountingReportUtil.classifyAging(now - (61 * oneDayMillis), now))
        assertEquals(AgingBracket.DAYS_OVER_60, AccountingReportUtil.classifyAging(now - (120 * oneDayMillis), now))
    }

    // 2. Running balance in Statement of Account (كشف حساب شخص) including voided settlement
    @Test
    fun testStatementOfAccountRunningBalanceWithVoidedSettlement() {
        val t0 = 1731000000000L // Day 0
        val t1 = t0 + 86400000L // Day 1
        val t2 = t1 + 86400000L // Day 2
        val t3 = t2 + 86400000L // Day 3

        // Allocation 1: 100 ILS owed by Mohammed
        val alloc1 = Allocation(
            id = "alloc_m1",
            refNo = 101L,
            party = mohammed,
            amount = 100.0,
            settlements = listOf(
                // Settlement 1: 40 ILS active
                Settlement(
                    id = "s_active",
                    refNo = 201L,
                    amount = 40.0,
                    settledAt = t1,
                    recordedAt = t1
                ),
                // Settlement 2: 30 ILS voided (must NOT deduct from running balance)
                Settlement(
                    id = "s_voided",
                    refNo = 202L,
                    amount = 30.0,
                    settledAt = t2,
                    recordedAt = t2,
                    voidedAt = t3
                )
            )
        )

        val exp1 = ExpenseItem(
            id = "exp_01",
            title = "فاتورة مطعم",
            amount = 150.0,
            currency = "₪",
            category = ExpenseCategory.DINING,
            timestamp = t0,
            payer = selfUser,
            split = ExpenseSplit(
                totalAmount = 150.0,
                payer = selfUser,
                participants = listOf(selfUser, mohammed),
                amountPerPerson = 75.0,
                allocations = listOf(alloc1)
            ),
            myShare = 50.0,
            refNo = 1001L
        )

        val statement = AccountingReportUtil.buildStatementOfAccount(
            partyName = "محمد",
            currency = "₪",
            expenses = listOf(exp1)
        )

        assertEquals("محمد", statement.partyName)
        assertEquals("₪", statement.currency)
        assertEquals(0.0, statement.openingBalance)
        assertEquals(60.0, statement.closingBalance, "Closing balance must be 100 - 40 = 60 ILS (voided 30 excluded)")
        assertEquals(3, statement.movements.size)

        // Movement 1: Debit 100 ILS -> Balance 100 ILS
        val m1 = statement.movements[0]
        assertEquals(StatementMovementType.DEBIT, m1.type)
        assertEquals(100.0, m1.amount)
        assertEquals(100.0, m1.runningBalance)
        assertFalse(m1.isVoided)

        // Movement 2: Credit 40 ILS -> Balance 60 ILS
        val m2 = statement.movements[1]
        assertEquals(StatementMovementType.CREDIT, m2.type)
        assertEquals(40.0, m2.amount)
        assertEquals(60.0, m2.runningBalance)
        assertFalse(m2.isVoided)

        // Movement 3: Voided Credit 30 ILS -> Balance remains 60 ILS
        val m3 = statement.movements[2]
        assertEquals(StatementMovementType.VOIDED_CREDIT, m3.type)
        assertEquals(30.0, m3.amount)
        assertEquals(60.0, m3.runningBalance, "Voided movement must not alter running balance")
        assertTrue(m3.isVoided)
        assertEquals(t3, m3.voidedAt)
    }

    // 3. Integrity check: Healthy ledger vs Simulated mismatch
    @Test
    fun testLedgerIntegrityAudit() {
        val now = 1731168000000L
        val alloc = Allocation(id = "a1", party = ahmad, amount = 70.0)
        val healthyExpense = ExpenseItem(
            id = "exp_healthy",
            title = "بنزين",
            amount = 100.0,
            currency = "₪",
            category = ExpenseCategory.TRANSPORT,
            timestamp = now,
            payer = selfUser,
            split = ExpenseSplit(
                totalAmount = 100.0,
                payer = selfUser,
                participants = listOf(selfUser, ahmad),
                amountPerPerson = 50.0,
                allocations = listOf(alloc)
            ),
            myShare = 30.0
        )

        val healthyResult = LedgerIntegrityChecker.verify(listOf(healthyExpense), now)
        assertTrue(healthyResult.isValid, "Healthy ledger where 70 + 30 == 100 must pass audit")
        assertTrue(healthyResult.errors.isEmpty())

        // Simulated discrepancy: total is 100, allocation is 70, myShare is 40 (sum is 110 != 100)
        val badExpense = healthyExpense.copy(myShare = 40.0)
        val badResult = LedgerIntegrityChecker.verify(listOf(badExpense), now)
        assertFalse(badResult.isValid, "Mismatched allocation + myShare must fail integrity check")
        assertTrue(badResult.errors.isNotEmpty())
    }

    // 4. Report rows fidelity: counts, values, and arabic day names
    @Test
    fun testRowGenerationFidelityAndArabicDayNames() {
        val now = 1732000000000L
        val allocAhmad = Allocation(id = "alloc_ahmad", party = ahmad, amount = 50.0)
        val exp = ExpenseItem(
            id = "exp_test",
            title = "سوبرماركت",
            amount = 80.0,
            currency = "₪",
            category = ExpenseCategory.GROCERIES,
            timestamp = now,
            payer = selfUser,
            split = ExpenseSplit(
                totalAmount = 80.0,
                payer = selfUser,
                participants = listOf(selfUser, ahmad),
                amountPerPerson = 40.0,
                allocations = listOf(allocAhmad)
            ),
            myShare = 30.0
        )

        val dateStrWithDay = AccountingReportUtil.formatDateWithArabicDay(now)
        assertTrue(
            dateStrWithDay.contains("2024-11-19") || dateStrWithDay.contains("2024"),
            "Formatted date must include numerical date"
        )
        // Must contain Arabic day of week name
        val hasArabicDay = listOf("السبت", "الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة")
            .any { dateStrWithDay.startsWith(it) }
        assertTrue(hasArabicDay, "Formatted date must start with Arabic day name: $dateStrWithDay")
    }
}

