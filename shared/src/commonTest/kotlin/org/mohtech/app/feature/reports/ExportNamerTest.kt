package org.mohtech.app.feature.reports

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ExportNamerTest {

    @Test
    fun testTwoExportsInSameSecondProduceDistinctFilenames() {
        val fixedTimestamp = 1731168000000L
        val existingFiles = mutableSetOf<String>()

        val file1 = ExportNamer.generateFileName(
            type = "expenses",
            scope = "all",
            extension = "xlsx",
            nowMillis = fixedTimestamp,
            exists = { existingFiles.contains(it) }
        )
        existingFiles.add(file1)

        val file2 = ExportNamer.generateFileName(
            type = "expenses",
            scope = "all",
            extension = "xlsx",
            nowMillis = fixedTimestamp,
            exists = { existingFiles.contains(it) }
        )

        assertNotEquals(file1, file2, "Exports in the same second must produce distinct filenames")
        assertTrue(file1.endsWith(".xlsx"))
        assertTrue(file2.endsWith("-2.xlsx"))
        assertTrue(file1.startsWith("raqeem_expenses_all_"))
        assertTrue(file2.startsWith("raqeem_expenses_all_"))
    }

    @Test
    fun testNonAsciiAndArabicCharactersAreSanitized() {
        val sanitized = ExportNamer.sanitizeAscii("تقرير مستحقات محمد & أحمد!")
        // Non-ASCII and symbols stripped/underscored, leaving no Arabic chars
        for (ch in sanitized) {
            assertTrue(
                ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '-' || ch == '_',
                "Character '$ch' must be safe ASCII"
            )
        }
    }

    @Test
    fun testStandardFilenamePattern() {
        val fixedTimestamp = 1732024800000L // 2024-11-19
        val fileName = ExportNamer.generateFileName(
            type = "receivables",
            scope = "p0012",
            extension = "pdf",
            nowMillis = fixedTimestamp
        )

        assertTrue(fileName.startsWith("raqeem_receivables_p0012_"))
        assertTrue(fileName.endsWith(".pdf"))
    }
}

