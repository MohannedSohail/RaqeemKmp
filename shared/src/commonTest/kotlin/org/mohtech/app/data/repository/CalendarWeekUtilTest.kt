package org.mohtech.app.data.repository

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class CalendarWeekUtilTest {

    private val tz = TimeZone.UTC

    data class WeekBoundaryCase(
        val name: String,
        val currentLdt: LocalDateTime,
        val weekStartIso: Int, // 6 = Saturday, 7 = Sunday, 1 = Monday
        val expectedStartLdt: LocalDateTime
    )

    @Test
    fun testCalendarWeekBoundariesTable() {
        val cases = listOf(
            // 1. Saturday 00:00:00 (exact start of week)
            WeekBoundaryCase(
                name = "Saturday midnight boundary (week start is Saturday)",
                currentLdt = LocalDateTime(2026, 11, 21, 0, 0, 0, 0), // Nov 21, 2026 is Saturday
                weekStartIso = 6,
                expectedStartLdt = LocalDateTime(2026, 11, 21, 0, 0, 0, 0)
            ),
            // 2. Saturday 15:30:00
            WeekBoundaryCase(
                name = "Saturday afternoon (week start is Saturday)",
                currentLdt = LocalDateTime(2026, 11, 21, 15, 30, 0, 0),
                weekStartIso = 6,
                expectedStartLdt = LocalDateTime(2026, 11, 21, 0, 0, 0, 0)
            ),
            // 3. Sunday morning
            WeekBoundaryCase(
                name = "Sunday morning (week start is Saturday)",
                currentLdt = LocalDateTime(2026, 11, 22, 9, 0, 0, 0),
                weekStartIso = 6,
                expectedStartLdt = LocalDateTime(2026, 11, 21, 0, 0, 0, 0)
            ),
            // 4. Wednesday
            WeekBoundaryCase(
                name = "Wednesday middle of week (week start is Saturday)",
                currentLdt = LocalDateTime(2026, 11, 25, 12, 0, 0, 0),
                weekStartIso = 6,
                expectedStartLdt = LocalDateTime(2026, 11, 21, 0, 0, 0, 0)
            ),
            // 5. Friday 23:59:59 (exact end of week)
            WeekBoundaryCase(
                name = "Friday end of week boundary (week start is Saturday)",
                currentLdt = LocalDateTime(2026, 11, 27, 23, 59, 59, 999_000_000),
                weekStartIso = 6,
                expectedStartLdt = LocalDateTime(2026, 11, 21, 0, 0, 0, 0)
            ),
            // 6. Next Saturday 00:00:00 (rolls over to new week)
            WeekBoundaryCase(
                name = "Next Saturday rollover (week start is Saturday)",
                currentLdt = LocalDateTime(2026, 11, 28, 0, 0, 0, 0),
                weekStartIso = 6,
                expectedStartLdt = LocalDateTime(2026, 11, 28, 0, 0, 0, 0)
            ),
            // 7. Sunday week start option (Sunday = 7)
            WeekBoundaryCase(
                name = "Sunday week start: Monday date",
                currentLdt = LocalDateTime(2026, 11, 23, 10, 0, 0, 0),
                weekStartIso = 7,
                expectedStartLdt = LocalDateTime(2026, 11, 22, 0, 0, 0, 0)
            ),
            // 8. Monday week start option (Monday = 1)
            WeekBoundaryCase(
                name = "Monday week start: Tuesday date",
                currentLdt = LocalDateTime(2026, 11, 24, 10, 0, 0, 0),
                weekStartIso = 1,
                expectedStartLdt = LocalDateTime(2026, 11, 23, 0, 0, 0, 0)
            )
        )

        for (c in cases) {
            val inputMillis = c.currentLdt.toInstant(tz).toEpochMilliseconds()
            val expectedMillis = c.expectedStartLdt.toInstant(tz).toEpochMilliseconds()
            val actualMillis = CalendarWeekUtil.getWeekStartTimestamp(inputMillis, c.weekStartIso, tz)
            assertEquals(expectedMillis, actualMillis, "Failed case: ${c.name}")
        }
    }
}

