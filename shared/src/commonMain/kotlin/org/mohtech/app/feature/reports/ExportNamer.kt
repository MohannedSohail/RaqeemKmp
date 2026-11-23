package org.mohtech.app.feature.reports

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * ExportNamer:
 * Pure Kotlin deterministic and collision-free file naming engine for all Raqeem exports.
 * 
 * Rules:
 * 1. Pattern: raqeem_{type}_{scope}_{yyyyMMdd-HHmmss}.{ext}
 * 2. Uniqueness: If file exists, appends counter (-2, -3...) before extension.
 * 3. ASCII-only: Only alphanumeric, dash, and underscore. No Arabic characters in filenames
 *    (Arabic names are displayed in document header and in-app UI).
 */
object ExportNamer {

    fun generateFileName(
        type: String,
        scope: String,
        extension: String,
        nowMillis: Long = kotlin.time.Clock.System.now().toEpochMilliseconds(),
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
        exists: (String) -> Boolean = { false }
    ): String {
        val safeType = sanitizeAscii(type).ifBlank { "report" }
        val safeScope = sanitizeAscii(scope).ifBlank { "all" }
        val ext = extension.trim().removePrefix(".").ifBlank { "dat" }

        val ldt = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(timeZone)
        val yyyy = ldt.year.toString().padStart(4, '0')
        val mm = ldt.monthNumber.toString().padStart(2, '0')
        val dd = ldt.dayOfMonth.toString().padStart(2, '0')
        val hh = ldt.hour.toString().padStart(2, '0')
        val min = ldt.minute.toString().padStart(2, '0')
        val ss = ldt.second.toString().padStart(2, '0')

        val timestampStr = "${yyyy}${mm}${dd}-${hh}${min}${ss}"
        val baseName = "raqeem_${safeType}_${safeScope}_${timestampStr}"

        var candidate = "$baseName.$ext"
        var counter = 2
        while (exists(candidate)) {
            candidate = "$baseName-$counter.$ext"
            counter++
        }

        return candidate
    }

    /**
     * Sanitizes input to safe ASCII characters [a-zA-Z0-9_-].
     * Any other character is replaced with an underscore or stripped.
     */
    fun sanitizeAscii(input: String): String {
        val sb = StringBuilder()
        for (ch in input.trim()) {
            when {
                ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '-' || ch == '_' -> sb.append(ch)
                ch == ' ' || ch == '.' -> sb.append('_')
                else -> {
                    // Non-ASCII or special characters are skipped
                }
            }
        }
        return sb.toString().replace(Regex("_+"), "_").trim('_')
    }

    /**
     * Resolves scope string for a ReportPeriod.
     */
    fun scopeForPeriod(period: ReportPeriod): String {
        return when (period) {
            ReportPeriod.THIS_WEEK -> "this_week"
            ReportPeriod.THIS_MONTH -> "this_month"
            ReportPeriod.LAST_30_DAYS -> "30_days"
            ReportPeriod.ALL_TIME -> "all"
        }
    }
}

