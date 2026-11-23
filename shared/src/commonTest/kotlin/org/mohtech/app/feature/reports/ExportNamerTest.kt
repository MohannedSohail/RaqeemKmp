package org.mohtech.app.feature.reports

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ExportNamerTest {

    @Test
    fun testGenerateFileNameDeterministicFormat() {
        val nowMillis = 1732374600000L // Specific epoch
        val name = ExportNamer.generateFileName(
            type = "report",
            scope = "this_month",
            extension = "xlsx",
            nowMillis = nowMillis
        )

        assertTrue(name.startsWith("raqeem_report_this_month_"))
        assertTrue(name.endsWith(".xlsx"))
        // Check regex matching pattern raqeem_{type}_{scope}_{yyyyMMdd-HHmmss}.{ext}
        val regex = Regex("^raqeem_report_this_month_\\d{8}-\\d{6}\\.xlsx$")
        assertTrue(regex.matches(name), "Generated name '$name' must match expected format")
    }

    @Test
    fun testScopeForPeriods() {
        assertEquals("this_week", ExportNamer.scopeForPeriod(ReportPeriod.THIS_WEEK))
        assertEquals("this_month", ExportNamer.scopeForPeriod(ReportPeriod.THIS_MONTH))
        assertEquals("last_30_days", ExportNamer.scopeForPeriod(ReportPeriod.LAST_30_DAYS))
        assertEquals("all_time", ExportNamer.scopeForPeriod(ReportPeriod.ALL_TIME))
    }

    @Test
    fun testSanitizationOfSpecialCharactersAndSpaces() {
        val name = ExportNamer.generateFileName(
            type = "Receivables / Statement",
            scope = "Ahmad & Brothers (Personal)!",
            extension = ".XLSX",
            nowMillis = 1732374600000L
        )

        assertTrue(name.startsWith("raqeem_receivables_statement_ahmad_brothers_personal_"))
        assertTrue(name.endsWith(".xlsx"))
    }

    @Test
    fun testUniqueTimestampsPreventOverwrites() {
        val t1 = 1732374600000L
        val t2 = 1732374601000L // 1 second later

        val name1 = ExportNamer.generateFileName("report", "all", "xlsx", nowMillis = t1)
        val name2 = ExportNamer.generateFileName("report", "all", "xlsx", nowMillis = t2)

        assertNotEquals(name1, name2, "Consecutive exports must produce unique file names")
    }
}

