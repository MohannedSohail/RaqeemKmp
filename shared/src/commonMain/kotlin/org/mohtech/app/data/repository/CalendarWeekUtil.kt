package org.mohtech.app.data.repository

import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * CalendarWeekUtil:
 * Calculates dynamic calendar week boundaries based on a configurable week start day
 * (default Saturday = 6 in most Arab countries, with options for Sunday = 7 and Monday = 1).
 */
object CalendarWeekUtil {

    /**
     * Calculates the epoch millisecond timestamp corresponding to 00:00:00 of the first day
     * of the calendar week containing [currentTimestamp].
     *
     * @param currentTimestamp The instant in milliseconds.
     * @param weekStartDayIso ISO day number for start of week (1 = Monday, 6 = Saturday, 7 = Sunday). Default 6 (Saturday).
     * @param timeZone The local timezone.
     */
    fun getWeekStartTimestamp(
        currentTimestamp: Long,
        weekStartDayIso: Int = 6,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): Long {
        val instant = Instant.fromEpochMilliseconds(currentTimestamp)
        val ldt = instant.toLocalDateTime(timeZone)
        val today = ldt.date
        val currentIsoDay = today.dayOfWeek.isoDayNumber
        val daysToSubtract = (currentIsoDay - weekStartDayIso + 7) % 7
        val startOfWeekDate = today.minus(DatePeriod(days = daysToSubtract))
        val startOfWeekLdt = LocalDateTime(startOfWeekDate, LocalTime(0, 0, 0, 0))
        return startOfWeekLdt.toInstant(timeZone).toEpochMilliseconds()
    }

    /**
     * Calculates the epoch millisecond timestamp corresponding to 00:00:00 of today.
     */
    fun getTodayStartTimestamp(
        currentTimestamp: Long,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): Long {
        val instant = Instant.fromEpochMilliseconds(currentTimestamp)
        val ldt = instant.toLocalDateTime(timeZone)
        val todayLdt = LocalDateTime(ldt.date, LocalTime(0, 0, 0, 0))
        return todayLdt.toInstant(timeZone).toEpochMilliseconds()
    }
}

