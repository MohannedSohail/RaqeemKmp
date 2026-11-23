package org.mohtech.app.feature.reports

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.mohtech.app.data.model.ExpenseCategory
import org.mohtech.app.data.model.ExpenseItem
import org.mohtech.app.data.repository.CalendarWeekUtil

enum class ReportPeriod(val title: String) {
    THIS_WEEK("الأسبوع الحالي"),
    THIS_MONTH("الشهر الحالي"),
    LAST_30_DAYS("آخر 30 يوماً"),
    ALL_TIME("كل المصاريف")
}

data class CategorySummary(
    val category: ExpenseCategory,
    val totalAmount: Double,
    val percentage: Float,
    val count: Int
)

data class ReportData(
    val period: ReportPeriod,
    val periodTitle: String,
    val generatedDateFormatted: String,
    val items: List<ExpenseItem>,
    val totalsByCurrency: Map<String, Double>,
    val categoryBreakdownByCurrency: Map<String, List<CategorySummary>>,
    val dailyTotals: Map<LocalDate, Double>
) {
    val isEmpty: Boolean get() = items.isEmpty()
    val totalOperationsCount: Int get() = items.size
}

/**
 * Pure Kotlin builder for compiling structured [ReportData] from expenses and period filter.
 */
object ReportBuilder {

    fun buildReport(
        expenses: List<ExpenseItem>,
        period: ReportPeriod,
        weekStartDayIso: Int = 6,
        nowMillis: Long = kotlin.time.Clock.System.now().toEpochMilliseconds(),
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): ReportData {
        val nowLdt = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(timeZone)
        val today = nowLdt.date

        val filteredExpenses = when (period) {
            ReportPeriod.THIS_WEEK -> {
                val weekStartMillis = CalendarWeekUtil.getWeekStartTimestamp(nowMillis, weekStartDayIso, timeZone)
                expenses.filter { it.timestamp >= weekStartMillis && it.timestamp <= nowMillis }
            }
            ReportPeriod.THIS_MONTH -> {
                expenses.filter { item ->
                    val itemDate = Instant.fromEpochMilliseconds(item.timestamp).toLocalDateTime(timeZone).date
                    itemDate.year == today.year && itemDate.monthNumber == today.monthNumber
                }
            }
            ReportPeriod.LAST_30_DAYS -> {
                val thirtyDaysAgo = nowMillis - (30L * 24L * 60L * 60L * 1000L)
                expenses.filter { it.timestamp in thirtyDaysAgo..nowMillis }
            }
            ReportPeriod.ALL_TIME -> expenses
        }.sortedByDescending { it.timestamp }

        // Totals per currency
        val totalsByCurrency = mutableMapOf<String, Double>()
        for (item in filteredExpenses) {
            val curr = item.currency.ifBlank { "₪" }
            totalsByCurrency[curr] = (totalsByCurrency[curr] ?: 0.0) + item.effectiveAmount
        }

        // Category breakdown per currency
        val categoryBreakdown = mutableMapOf<String, List<CategorySummary>>()
        for ((curr, total) in totalsByCurrency) {
            val currencyItems = filteredExpenses.filter { (it.currency.ifBlank { "₪" }) == curr }
            val list = mutableListOf<CategorySummary>()
            ExpenseCategory.entries.forEach { cat ->
                val catItems = currencyItems.filter { it.category == cat }
                val catSum = catItems.sumOf { it.effectiveAmount }
                if (catSum > 0.0 || catItems.isNotEmpty()) {
                    val pct = if (total > 0.0) (catSum / total).toFloat() else 0.0f
                    list.add(
                        CategorySummary(
                            category = cat,
                            totalAmount = catSum,
                            percentage = pct,
                            count = catItems.size
                        )
                    )
                }
            }
            categoryBreakdown[curr] = list.sortedByDescending { it.totalAmount }
        }

        // Daily totals
        val dailyTotals = mutableMapOf<LocalDate, Double>()
        for (item in filteredExpenses) {
            val itemDate = Instant.fromEpochMilliseconds(item.timestamp).toLocalDateTime(timeZone).date
            dailyTotals[itemDate] = (dailyTotals[itemDate] ?: 0.0) + item.effectiveAmount
        }

        val dateStr = "${today.year}-${today.monthNumber.toString().padStart(2, '0')}-${today.dayOfMonth.toString().padStart(2, '0')}"

        return ReportData(
            period = period,
            periodTitle = period.title,
            generatedDateFormatted = dateStr,
            items = filteredExpenses,
            totalsByCurrency = totalsByCurrency,
            categoryBreakdownByCurrency = categoryBreakdown,
            dailyTotals = dailyTotals
        )
    }
}

