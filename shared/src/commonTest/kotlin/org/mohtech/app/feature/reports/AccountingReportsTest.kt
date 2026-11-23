package org.mohtech.app.feature.reports

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.mohtech.app.data.model.Allocation
import org.mohtech.app.data.model.DebtStatus
import org.mohtech.app.data.model.ExpenseCategory
import org.mohtech.app.data.model.ExpenseItem
import org.mohtech.app.data.model.ExpenseSplit
import org.mohtech.app.data.model.Payer
import org.mohtech.app.data.model.Settlement

class AccountingReportsTest {

    private val selfUser = Payer(id = "self", name = "أنا", isCurrentUser = true)
    private val ahmad = Payer(id = "p1", name = "أحمد", isCurrentUser = false)
    private val khaled = Payer(id = "p2", name = "خالد", isCurrentUser = false)

    @Test
    fun testReportBuilderUsesEffectiveAmountRatherThanTotal() {
        // User paid 100 ILS total for a group lunch, but user's personal share is only 30 ILS.
        // Ahmad owes 40, Khaled owes 30.
        val expense = ExpenseItem(
            id = "exp_split_01",
            title = "غداء جماعي",
            amount = 100.0,
            currency = "₪",
            category = ExpenseCategory.DINING,
            timestamp = 1731168000000L,
            payer = selfUser,
            split = ExpenseSplit(
                totalAmount = 100.0,
                payer = selfUser,
                participants = listOf(selfUser, ahmad, khaled),
                amountPerPerson = 33.33,
                allocations = listOf(
                    Allocation(id = "a1", party = ahmad, amount = 40.0),
                    Allocation(id = "a2", party = khaled, amount = 30.0)
                )
            ),
            myShare = 30.0
        )

        val report = ReportBuilder.buildReport(
            expenses = listOf(expense),
            period = ReportPeriod.ALL_TIME
        )

        // Totals by currency must reflect effectiveAmount (30.0), NOT 100.0
        assertEquals(30.0, report.totalsByCurrency["₪"], "User's currency total must equal effectiveAmount")

        // Category breakdown must also use effectiveAmount
        val diningSummary = report.categoryBreakdownByCurrency["₪"]?.firstOrNull { it.category == ExpenseCategory.DINING }
        assertEquals(30.0, diningSummary?.totalAmount, "Category breakdown must sum effectiveAmount")
    }

    @Test
    fun testXlsxWriterGeneratesValidOpenXmlZipStructure() {
        val expense = ExpenseItem(
            id = "exp_01",
            title = "بنزين",
            amount = 50.0,
            currency = "₪",
            category = ExpenseCategory.TRANSPORT,
            timestamp = 1731168000000L,
            payer = selfUser
        )

        val report = ReportBuilder.buildReport(
            expenses = listOf(expense),
            period = ReportPeriod.ALL_TIME
        )

        val xlsxBytes = XlsxWriter.write(report)
        assertTrue(xlsxBytes.isNotEmpty(), "XLSX output must not be empty")

        // Standard ZIP signature (PK\x03\x04)
        assertEquals(0x50.toByte(), xlsxBytes[0])
        assertEquals(0x4B.toByte(), xlsxBytes[1])
        assertEquals(0x03.toByte(), xlsxBytes[2])
        assertEquals(0x04.toByte(), xlsxBytes[3])
    }

    @Test
    fun testXlsxWriterReceivablesWorkbookGeneration() {
        val alloc = Allocation(
            id = "alloc_01",
            refNo = 1001L,
            party = ahmad,
            amount = 70.0,
            settlements = listOf(
                Settlement(
                    id = "s_01",
                    refNo = 2001L,
                    allocationId = "alloc_01",
                    amount = 20.0,
                    settledAt = 1731168100000L,
                    recordedAt = 1731168100000L,
                    note = "دفعة نقدية"
                )
            )
        )
        val expense = ExpenseItem(
            id = "exp_02",
            title = "فاتورة مطعم",
            amount = 100.0,
            currency = "₪",
            category = ExpenseCategory.DINING,
            timestamp = 1731168000000L,
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

        val bytes = XlsxWriter.writeReceivablesWorkbook(
            expenses = listOf(expense),
            nowMillis = 1731168200000L
        )

        assertTrue(bytes.isNotEmpty(), "Receivables workbook must generate valid bytes")
        assertEquals(0x50.toByte(), bytes[0])
        assertEquals(0x4B.toByte(), bytes[1])
    }
}

