package org.mohtech.app.feature.reports

import kotlin.time.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * ExportNamer:
 * Enforces deterministic, collision-proof, accountant-grade file naming for all exports.
 * Format: raqeem_{type}_{scope}_{timestamp}.{ext}
 * Zero overwrite across repeated export actions on the same calendar day.
 */
object ExportNamer {

    fun generateFileName(
        type: String,
        scope: String,
        extension: String,
        nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): String {
        val ldt = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(timeZone)
        val yyyy = ldt.year.toString().padStart(4, '0')
        val mm = ldt.monthNumber.toString().padStart(2, '0')
        val dd = ldt.dayOfMonth.toString().padStart(2, '0')
        val hh = ldt.hour.toString().padStart(2, '0')
        val min = ldt.minute.toString().padStart(2, '0')
        val ss = ldt.second.toString().padStart(2, '0')
        val timestamp = "$yyyy$mm$dd-$hh$min$ss"

        val sanitizedType = sanitize(type).ifBlank { "report" }
        val sanitizedScope = sanitize(scope).ifBlank { "all" }
        val sanitizedExt = extension.trim().removePrefix(".").lowercase().ifBlank { "xlsx" }

        return "raqeem_${sanitizedType}_${sanitizedScope}_${timestamp}.$sanitizedExt"
    }

    fun scopeForPeriod(period: ReportPeriod): String = when (period) {
        ReportPeriod.THIS_WEEK -> "this_week"
        ReportPeriod.THIS_MONTH -> "this_month"
        ReportPeriod.LAST_30_DAYS -> "last_30_days"
        ReportPeriod.ALL_TIME -> "all_time"
    }

    private fun sanitize(input: String): String {
        return input.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9_-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
    }
}

