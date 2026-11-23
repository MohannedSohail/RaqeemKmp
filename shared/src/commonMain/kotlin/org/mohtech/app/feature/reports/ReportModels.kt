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

        // Totals per currency calculated strictly from effectiveAmount (myShare ?: amount)
        val totalsByCurrency = mutableMapOf<String, Double>()
        for (item in filteredExpenses) {
            val curr = item.currency.ifBlank { "₪" }
            totalsByCurrency[curr] = (totalsByCurrency[curr] ?: 0.0) + item.effectiveAmount
        }

        // Category breakdown per currency calculated strictly from effectiveAmount
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

        // Daily totals calculated strictly from effectiveAmount
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

/**
 * Standard Debt Aging Categories compliant with financial accounting principles.
 */
enum class AgingBracket(val labelArabic: String) {
    DAYS_0_TO_7("0 إلى 7 أيام"),
    DAYS_8_TO_30("8 إلى 30 يوماً"),
    DAYS_31_TO_60("31 إلى 60 يوماً"),
    DAYS_OVER_60("أكثر من 60 يوماً")
}

data class AgingSummary(
    val bracket: AgingBracket,
    val amount: Double,
    val count: Int
)

enum class StatementMovementType {
    DEBIT,           // مدين (مصروف / تخصيص يرفع رصيد الدين)
    CREDIT,          // دائن (تسديد يخفض رصيد الدين)
    VOIDED_CREDIT    // تسديد ملغى (يبقى مسجلاً بوضوح ولا يؤثر على الرصيد الجاري)
}

data class StatementMovement(
    val refNo: Long,
    val timestamp: Long,
    val dateFormatted: String,
    val dayName: String,
    val type: StatementMovementType,
    val description: String,
    val amount: Double,
    val runningBalance: Double,
    val isVoided: Boolean = false,
    val voidedAt: Long? = null,
    val note: String = ""
)

data class PersonStatement(
    val partyName: String,
    val currency: String,
    val openingBalance: Double = 0.0,
    val closingBalance: Double,
    val movements: List<StatementMovement>
)

object AccountingReportUtil {

    fun arabicDayName(date: LocalDate): String {
        return when (date.dayOfWeek) {
            kotlinx.datetime.DayOfWeek.SATURDAY -> "السبت"
            kotlinx.datetime.DayOfWeek.SUNDAY -> "الأحد"
            kotlinx.datetime.DayOfWeek.MONDAY -> "الاثنين"
            kotlinx.datetime.DayOfWeek.TUESDAY -> "الثلاثاء"
            kotlinx.datetime.DayOfWeek.WEDNESDAY -> "الأربعاء"
            kotlinx.datetime.DayOfWeek.THURSDAY -> "الخميس"
            kotlinx.datetime.DayOfWeek.FRIDAY -> "الجمعة"
        }
    }

    fun formatDateWithArabicDay(
        epochMillis: Long,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): String {
        val ldt = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone)
        val dayName = arabicDayName(ldt.date)
        val yyyy = ldt.year
        val mm = ldt.monthNumber.toString().padStart(2, '0')
        val dd = ldt.dayOfMonth.toString().padStart(2, '0')
        return "$dayName $yyyy-$mm-$dd"
    }

    /**
     * Categorizes open debt allocations into standard aging brackets:
     * 0-7, 8-30, 31-60, 60+ days.
     */
    fun classifyAging(
        expenseTimestamp: Long,
        nowMillis: Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
    ): AgingBracket {
        val ageDays = ((nowMillis - expenseTimestamp) / (24L * 60L * 60L * 1000L)).coerceAtLeast(0L)
        return when {
            ageDays <= 7L -> AgingBracket.DAYS_0_TO_7
            ageDays <= 30L -> AgingBracket.DAYS_8_TO_30
            ageDays <= 60L -> AgingBracket.DAYS_31_TO_60
            else -> AgingBracket.DAYS_OVER_60
        }
    }

    /**
     * Builds a chronological Statement of Account (كشف حساب شخص) for a given party and currency.
     * Enforces:
     * - Chronological ordering
     * - Running balance updated ONLY by active movements (voided settlements do not alter running balance)
     * - Voided settlements clearly marked with voidedAt timestamp
     */
    fun buildStatementOfAccount(
        partyName: String,
        currency: String,
        expenses: List<ExpenseItem>,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): PersonStatement {
        // Collect all raw movements for this party and currency
        class RawMovement(
            val refNo: Long,
            val timestamp: Long,
            val isDebit: Boolean,
            val description: String,
            val amount: Double,
            val isVoided: Boolean,
            val voidedAt: Long?,
            val note: String
        )

        val rawList = mutableListOf<RawMovement>()

        for (expense in expenses) {
            if (expense.currency != currency || expense.deletedAt != null) continue
            val split = expense.split ?: continue

            for (alloc in split.allocations) {
                if (alloc.party.name != partyName && alloc.party.id != partyName) continue

                // 1. Allocation incurred (Debit for receivables owed to me)
                rawList.add(
                    RawMovement(
                        refNo = if (alloc.refNo > 0L) alloc.refNo else expense.refNo,
                        timestamp = expense.timestamp,
                        isDebit = true,
                        description = expense.title,
                        amount = alloc.amount,
                        isVoided = false,
                        voidedAt = null,
                        note = alloc.note
                    )
                )

                // 2. Settlements recorded for this allocation
                for (settle in alloc.settlements) {
                    val isVoided = settle.voidedAt != null
                    rawList.add(
                        RawMovement(
                            refNo = settle.refNo,
                            timestamp = settle.settledAt,
                            isDebit = false,
                            description = "سداد - ${expense.title}",
                            amount = settle.amount,
                            isVoided = isVoided,
                            voidedAt = settle.voidedAt,
                            note = settle.note
                        )
                    )
                }
            }
        }

        // Sort chronologically by movement timestamp, placing debits before credits if timestamps match
        val sorted = rawList.sortedWith(compareBy({ it.timestamp }, { if (it.isDebit) 0 else 1 }))

        var currentBalanceMinor = 0L
        val movements = mutableListOf<StatementMovement>()

        for (raw in sorted) {
            val amountMinor = org.mohtech.app.data.accounting.Money.doubleToMinor(raw.amount, currency)
            val ldt = Instant.fromEpochMilliseconds(raw.timestamp).toLocalDateTime(timeZone)
            val day = arabicDayName(ldt.date)
            val dateStr = "${ldt.year}-${ldt.monthNumber.toString().padStart(2, '0')}-${ldt.dayOfMonth.toString().padStart(2, '0')}"

            val type = when {
                raw.isVoided -> StatementMovementType.VOIDED_CREDIT
                raw.isDebit -> StatementMovementType.DEBIT
                else -> StatementMovementType.CREDIT
            }

            if (!raw.isVoided) {
                if (raw.isDebit) {
                    currentBalanceMinor += amountMinor
                } else {
                    currentBalanceMinor -= amountMinor
                }
            }

            movements.add(
                StatementMovement(
                    refNo = raw.refNo,
                    timestamp = raw.timestamp,
                    dateFormatted = dateStr,
                    dayName = day,
                    type = type,
                    description = raw.description,
                    amount = raw.amount,
                    runningBalance = org.mohtech.app.data.accounting.Money.minorToDouble(currentBalanceMinor, currency),
                    isVoided = raw.isVoided,
                    voidedAt = raw.voidedAt,
                    note = raw.note
                )
            )
        }

        val closingBalance = org.mohtech.app.data.accounting.Money.minorToDouble(currentBalanceMinor, currency)

        return PersonStatement(
            partyName = partyName,
            currency = currency,
            openingBalance = 0.0,
            closingBalance = closingBalance,
            movements = movements
        )
    }
}


