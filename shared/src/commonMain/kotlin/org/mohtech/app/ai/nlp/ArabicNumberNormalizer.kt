package org.mohtech.app.ai.nlp

/**
 * ArabicNumberNormalizer:
 * Converts Eastern Arabic-Indic numerals (٠-٩) and colloquial Arabic number words into standard Double values.
 */
object ArabicNumberNormalizer {

    private val indicToArabicMap = mapOf(
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9'
    )

    private val wordToNumberMap = mapOf(
        "صفر" to 0.0,
        "واحد" to 1.0,
        "واحدة" to 1.0,
        "اثنين" to 2.0,
        "اتنين" to 2.0,
        "ثلاثة" to 3.0,
        "تلاتة" to 3.0,
        "أربعة" to 4.0,
        "اربعة" to 4.0,
        "خمسة" to 5.0,
        "ستة" to 6.0,
        "سبعة" to 7.0,
        "ثمانية" to 8.0,
        "تمانية" to 8.0,
        "تسعة" to 9.0,
        "عشرة" to 10.0,
        "عشرين" to 20.0,
        "ثلاثين" to 30.0,
        "تلاتين" to 30.0,
        "أربعين" to 40.0,
        "اربعين" to 40.0,
        "خمسين" to 50.0,
        "ستين" to 60.0,
        "سبعين" to 70.0,
        "ثمانين" to 80.0,
        "تمانين" to 80.0,
        "تسعين" to 90.0,
        "مية" to 100.0,
        "مائة" to 100.0,
        "مئة" to 100.0,
        "ميتين" to 200.0,
        "مائتين" to 200.0,
        "مئتين" to 200.0,
        "ثلاثمية" to 300.0,
        "تلاتمية" to 300.0,
        "أربعمية" to 400.0,
        "اربعمية" to 400.0,
        "خمسمية" to 500.0,
        "خمسمائة" to 500.0,
        "ستمية" to 600.0,
        "سبعمية" to 700.0,
        "ثمانمية" to 800.0,
        "تمانمية" to 800.0,
        "تسعمية" to 900.0,
        "ألف" to 1000.0,
        "الف" to 1000.0,
        "ألفين" to 2000.0,
        "الفين" to 2000.0
    )

    /**
     * Converts Eastern Arabic-Indic numerals to ASCII digits.
     */
    fun convertIndicDigitsToAscii(input: String): String {
        val sb = java.lang.StringBuilder()
        for (ch in input) {
            sb.append(indicToArabicMap[ch] ?: ch)
        }
        return sb.toString()
    }

    /**
     * Attempts to parse an Arabic number word or compound phrase into a Double.
     * Handles:
     * - "عشرة" -> 10.0
     * - "مية وخمسين" -> 150.0
     * - "ألفين وميتين" -> 2200.0
     * - "خمسة وثلاثين" -> 35.0
     * - "ستين" -> 60.0
     */
    fun parseArabicNumberWords(input: String): Double? {
        val cleanInput = input.trim()
        if (cleanInput.isEmpty()) return null

        // Direct single word lookup
        wordToNumberMap[cleanInput]?.let { return it }

        // Strip leading 'ب' (e.g. "بعشرة", "بستين", "بثلاثين")
        if (cleanInput.startsWith("ب") && cleanInput.length > 2) {
            val stripped = cleanInput.substring(1)
            wordToNumberMap[stripped]?.let { return it }
        }

        // Split by "و" (and) for compound words like "مية وخمسين", "ألفين وميتين", "خمسة وثلاثين"
        val parts = cleanInput.split(Regex("\\s+و|\\s+")).map { it.trim().removePrefix("ب") }
        if (parts.size > 1) {
            var sum = 0.0
            var matchedAny = false
            for (p in parts) {
                val cleanP = p.removePrefix("و")
                val value = wordToNumberMap[cleanP]
                if (value != null) {
                    sum += value
                    matchedAny = true
                }
            }
            if (matchedAny && sum > 0.0) {
                return sum
            }
        }

        return null
    }
}
