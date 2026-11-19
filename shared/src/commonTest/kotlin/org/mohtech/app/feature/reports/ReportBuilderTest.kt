package org.mohtech.app.feature.reports

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.mohtech.app.data.model.ExpenseCategory
import org.mohtech.app.data.model.ExpenseItem
import org.mohtech.app.data.model.Payer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReportBuilderTest {

    private val tz = TimeZone.UTC
    // Wednesday 2026-11-18 12:00:00 UTC
    private val nowMillis = Instant.parse("2026-11-18T12:00:00Z").toEpochMilliseconds()

    @Test
    fun testBuildReportFiltersAndGroupsByCurrencyAndCategory() {
        val expenses = listOf(
            // Nov 18 (Wednesday, this week)
            ExpenseItem(
                id = "e1",
                title = "قهوة",
                amount = 15.0,
                currency = "₪",
                category = ExpenseCategory.DINING,
                timestamp = Instant.parse("2026-11-18T10:00:00Z").toEpochMilliseconds(),
                payer = Payer("u1", "أنا", true)
            ),
            // Nov 16 (Monday, this week)
            ExpenseItem(
                id = "e2",
                title = "بنزين",
                amount = 50.0,
                currency = "₪",
                category = ExpenseCategory.TRANSPORT,
                timestamp = Instant.parse("2026-11-16T10:00:00Z").toEpochMilliseconds(),
                payer = Payer("u1", "أنا", true)
            ),
            // Nov 15 (Sunday, this week) in SAR
            ExpenseItem(
                id = "e3",
                title = "تموينات",
                amount = 100.0,
                currency = "ر.س",
                category = ExpenseCategory.GROCERIES,
                timestamp = Instant.parse("2026-11-15T10:00:00Z").toEpochMilliseconds(),
                payer = Payer("u1", "أنا", true)
            ),
            // Nov 10 (Last week, excluded from THIS_WEEK)
            ExpenseItem(
                id = "e4",
                title = "صيدلية",
                amount = 80.0,
                currency = "₪",
                category = ExpenseCategory.HEALTH,
                timestamp = Instant.parse("2026-11-10T10:00:00Z").toEpochMilliseconds(),
                payer = Payer("u1", "أنا", true)
            )
        )

        val report = ReportBuilder.buildReport(
            expenses = expenses,
            period = ReportPeriod.THIS_WEEK,
            weekStartDayIso = 6, // Saturday
            nowMillis = nowMillis,
            timeZone = tz
        )

        assertFalse(report.isEmpty)
        assertEquals(3, report.items.size, "Should include e1, e2, e3 from current week (starting Nov 14)")
        assertEquals(65.0, report.totalsByCurrency["₪"])
        assertEquals(100.0, report.totalsByCurrency["ر.س"])

        val ilsSummaries = report.categoryBreakdownByCurrency["₪"]!!
        assertEquals(2, ilsSummaries.size)
        val transport = ilsSummaries.first { it.category == ExpenseCategory.TRANSPORT }
        assertEquals(50.0, transport.totalAmount)
        assertEquals(1, transport.count)
    }

    @Test
    fun testBuildEmptyReport() {
        val report = ReportBuilder.buildReport(
            expenses = emptyList(),
            period = ReportPeriod.THIS_MONTH,
            nowMillis = nowMillis,
            timeZone = tz
        )

        assertTrue(report.isEmpty)
        assertEquals(0, report.totalOperationsCount)
        assertTrue(report.totalsByCurrency.isEmpty())
    }
}

