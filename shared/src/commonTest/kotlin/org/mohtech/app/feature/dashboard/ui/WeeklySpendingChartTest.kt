package org.mohtech.app.feature.dashboard.ui

import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.mohtech.app.data.model.ExpenseCategory
import org.mohtech.app.data.model.ExpenseItem
import org.mohtech.app.data.model.Payer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WeeklySpendingChartTest {

    // 2026-11-18 is Wednesday
    // Wednesday 2026-11-18T12:00:00Z -> epoch millis: 1795003200000
    private val wednesdayInstant = Instant.parse("2026-11-18T12:00:00Z")
    private val timeZone = TimeZone.UTC

    @Test
    fun testWeeklySpendingCycleStartsOnSaturdayAndEndsOnFriday() {
        val days = calculateWeeklySpending(
            expenses = emptyList(),
            weeklyBudget = 700.0,
            timeZone = timeZone,
            nowInstant = wednesdayInstant
        )

        assertEquals(7, days.size)
        // Saturday to Friday ordering
        val expectedNames = listOf("السبت", "الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة")
        val expectedShortNames = listOf("سبت", "أحد", "اثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة")

        for (i in 0..6) {
            assertEquals(i, days[i].dayIndex)
            assertEquals(expectedNames[i], days[i].dayName)
            assertEquals(expectedShortNames[i], days[i].dayShortName)
        }

        assertEquals(LocalDate(2026, 11, 14), days[0].date) // Saturday
        assertEquals(DayOfWeek.SATURDAY, days[0].date.dayOfWeek)
        assertEquals(LocalDate(2026, 11, 15), days[1].date) // Sunday
        assertEquals(LocalDate(2026, 11, 16), days[2].date) // Monday
        assertEquals(LocalDate(2026, 11, 17), days[3].date) // Tuesday
        assertEquals(LocalDate(2026, 11, 18), days[4].date) // Wednesday (Today)
        assertEquals(LocalDate(2026, 11, 19), days[5].date) // Thursday
        assertEquals(LocalDate(2026, 11, 20), days[6].date) // Friday
        assertEquals(DayOfWeek.FRIDAY, days[6].date.dayOfWeek)

        assertFalse(days[0].isToday)
        assertFalse(days[3].isToday)
        assertTrue(days[4].isToday, "Wednesday should be flagged as isToday = true")
        assertFalse(days[5].isToday)
    }

    @Test
    fun testDailyAggregationAndExceededBudgetThreshold() {
        val saturdayMillis = Instant.parse("2026-11-14T09:00:00Z").toEpochMilliseconds()
        val mondayMillis1 = Instant.parse("2026-11-16T10:00:00Z").toEpochMilliseconds()
        val mondayMillis2 = Instant.parse("2026-11-16T15:30:00Z").toEpochMilliseconds()
        val wednesdayMillis = Instant.parse("2026-11-18T08:00:00Z").toEpochMilliseconds()

        val expenses = listOf(
            ExpenseItem(
                id = "1",
                title = "بنزين",
                amount = 80.0,
                category = ExpenseCategory.TRANSPORT,
                timestamp = saturdayMillis
            ),
            ExpenseItem(
                id = "2",
                title = "سوبرماركت",
                amount = 120.0,
                category = ExpenseCategory.GROCERIES,
                timestamp = mondayMillis1
            ),
            ExpenseItem(
                id = "3",
                title = "قهوة",
                amount = 35.0,
                category = ExpenseCategory.DINING,
                timestamp = mondayMillis2
            ),
            ExpenseItem(
                id = "4",
                title = "فطور",
                amount = 50.0,
                category = ExpenseCategory.DINING,
                timestamp = wednesdayMillis
            )
        )

        // Weekly budget 700.0 -> daily threshold is 100.0
        val days = calculateWeeklySpending(
            expenses = expenses,
            weeklyBudget = 700.0,
            timeZone = timeZone,
            nowInstant = wednesdayInstant
        )

        // Saturday: 80.0 <= 100.0 -> isExceeded = false
        assertEquals(80.0, days[0].totalAmount)
        assertFalse(days[0].isExceeded, "Saturday (80.0) is within daily budget (100.0)")

        // Sunday: 0.0 -> isExceeded = false
        assertEquals(0.0, days[1].totalAmount)
        assertFalse(days[1].isExceeded)

        // Monday: 120.0 + 35.0 = 155.0 > 100.0 -> isExceeded = true (CoralRose)
        assertEquals(155.0, days[2].totalAmount)
        assertTrue(days[2].isExceeded, "Monday (155.0) exceeds daily budget (100.0)")

        // Tuesday: 0.0
        assertEquals(0.0, days[3].totalAmount)
        assertFalse(days[3].isExceeded)

        // Wednesday: 50.0 <= 100.0 -> isExceeded = false
        assertEquals(50.0, days[4].totalAmount)
        assertFalse(days[4].isExceeded)

        // Thursday & Friday: 0.0
        assertEquals(0.0, days[5].totalAmount)
        assertEquals(0.0, days[6].totalAmount)
    }

    @Test
    fun testZeroWeeklyBudgetDoesNotFlagExceeded() {
        val saturdayMillis = Instant.parse("2026-11-14T10:00:00Z").toEpochMilliseconds()
        val expenses = listOf(
            ExpenseItem(
                id = "1",
                title = "شراء هاتف",
                amount = 2500.0,
                category = ExpenseCategory.GENERAL,
                timestamp = saturdayMillis
            )
        )

        val days = calculateWeeklySpending(
            expenses = expenses,
            weeklyBudget = 0.0,
            timeZone = timeZone,
            nowInstant = wednesdayInstant
        )

        assertEquals(2500.0, days[0].totalAmount)
        assertFalse(days[0].isExceeded, "When weekly budget is 0.0, days should not be marked as exceeded")
    }

    @Test
    fun testSaturdayAsCurrentDaySetsFirstDayAsToday() {
        val saturdayInstant = Instant.parse("2026-11-14T14:00:00Z")
        val days = calculateWeeklySpending(
            expenses = emptyList(),
            weeklyBudget = 500.0,
            timeZone = timeZone,
            nowInstant = saturdayInstant
        )

        assertTrue(days[0].isToday)
        assertFalse(days[1].isToday)
        assertFalse(days[6].isToday)
    }

    @Test
    fun testFridayAsCurrentDaySetsLastDayAsToday() {
        val fridayInstant = Instant.parse("2026-11-20T20:00:00Z")
        val days = calculateWeeklySpending(
            expenses = emptyList(),
            weeklyBudget = 500.0,
            timeZone = timeZone,
            nowInstant = fridayInstant
        )

        assertFalse(days[0].isToday)
        assertFalse(days[4].isToday)
        assertTrue(days[6].isToday)
    }
}
