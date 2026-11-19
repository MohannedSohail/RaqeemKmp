package org.mohtech.app.ai.nlp

/**
 * ArabicNumberNormalizer:
 * Converts Eastern Arabic-Indic numerals (٠-٩) and colloquial Arabic number words into standard Double values.
 * Supports composite compound numbers, thousand multipliers ("ثلاث آلاف"), and fraction phrases ("ألف ونص", "مية وربع").
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
        "تنين" to 2.0,
        "ثلاث" to 3.0,
        "تلات" to 3.0,
        "ثلاثة" to 3.0,
        "تلاتة" to 3.0,
        "تلاته" to 3.0,
        "ثلاثه" to 3.0,
        "أربع" to 4.0,
        "اربع" to 4.0,
        "أربعة" to 4.0,
        "اربعة" to 4.0,
        "اربعه" to 4.0,
        "خمس" to 5.0,
        "خمسة" to 5.0,
        "خمسه" to 5.0,
        "ست" to 6.0,
        "ستة" to 6.0,
        "سته" to 6.0,
        "سبع" to 7.0,
        "سبعة" to 7.0,
        "سبعه" to 7.0,
        "ثمان" to 8.0,
        "تمان" to 8.0,
        "ثمانية" to 8.0,
        "تمانية" to 8.0,
        "ثمانيه" to 8.0,
        "تمانيه" to 8.0,
        "تسع" to 9.0,
        "تسعة" to 9.0,
        "تسعه" to 9.0,
        "عشر" to 10.0,
        "عشرة" to 10.0,
        "عشره" to 10.0,
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
        "ميه" to 100.0,
        "مائة" to 100.0,
        "مئة" to 100.0,
        "ميتين" to 200.0,
        "مائتين" to 200.0,
        "مئتين" to 200.0,
        "ثلاثمية" to 300.0,
        "تلاتمية" to 300.0,
        "تلاتميه" to 300.0,
        "ثلاثمائة" to 300.0,
        "أربعمية" to 400.0,
        "اربعمية" to 400.0,
        "اربعميه" to 400.0,
        "أربعمائة" to 400.0,
        "خمسمية" to 500.0,
        "خمسميه" to 500.0,
        "خمسمائة" to 500.0,
        "خمسمئة" to 500.0,
        "ستمية" to 600.0,
        "ستميه" to 600.0,
        "ستمائة" to 600.0,
        "سبعمية" to 700.0,
        "سبعميه" to 700.0,
        "سبعمائة" to 700.0,
        "ثمانمية" to 800.0,
        "تمانمية" to 800.0,
        "تمانميه" to 800.0,
        "ثمانمائة" to 800.0,
        "تسعمية" to 900.0,
        "تسعميه" to 900.0,
        "تسعمائة" to 900.0,
        "ألف" to 1000.0,
        "الف" to 1000.0,
        "ألفين" to 2000.0,
        "الفين" to 2000.0
    )

    private val thousandsTokens = setOf("آلاف", "الاف", "ألاف", "الافاً")

    /**
     * Converts Eastern Arabic-Indic numerals to ASCII digits.
     */
    fun convertIndicDigitsToAscii(input: String): String {
        val sb = StringBuilder()
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
     * - "ألفين وميتين وخمسين" -> 2250.0
     * - "ثلاث آلاف" -> 3000.0
     * - "عشرة آلاف" -> 10000.0
     * - "ألف ونص" -> 1500.0
     * - "مية وربع" -> 125.0
     * - "خمسة وثلاثين" -> 35.0
     * - "ستين" -> 60.0
     */
    fun parseArabicNumberWords(input: String): Double? {
        val cleanInput = input.trim()
        if (cleanInput.isEmpty()) return null

        // Direct lookup with or without 'ب' prefix
        val stripped = cleanInput.removePrefix("ب")
        wordToNumberMap[cleanInput]?.let { return it }
        wordToNumberMap[stripped]?.let { return it }

        // Tokenize by spaces and 'و'
        val rawTokens = cleanInput.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (rawTokens.isEmpty()) return null

        var total = 0.0
        var i = 0
        var matchedAny = false

        while (i < rawTokens.size) {
            val token = rawTokens[i].removePrefix("ب").removePrefix("و")
            val nextToken = if (i + 1 < rawTokens.size) rawTokens[i + 1].removePrefix("و").removePrefix("ب") else null

            // Check if this is a thousand multiplication e.g. "ثلاث آلاف", "ثلاثة الاف", "عشرة الاف"
            if (nextToken != null && thousandsTokens.contains(nextToken)) {
                val multiplier = wordToNumberMap[token] ?: wordToNumberMap[rawTokens[i].removePrefix("ب")]
                if (multiplier != null) {
                    total += multiplier * 1000.0
                    matchedAny = true
                    i += 2
                    continue
                }
            }

            // Check fraction phrases:
            // "ألف ونص" -> 1500, "ألف وربع" -> 1250 (fraction directly qualifies thousands)
            // "مية وربع" -> 125, "مية ونص" -> 150 (fraction directly qualifies hundreds)
            // "مية وخمسين ونص" -> 150.5, "خمسة وثلاثين ونص" -> 35.5, "نص" -> 0.5
            if (token == "نص" || token == "نصف") {
                val prevToken = if (i > 0) rawTokens[i - 1].removePrefix("ب").removePrefix("و") else null
                val isPrevThousand = prevToken != null && (prevToken == "ألف" || prevToken == "الف" || prevToken == "ألفين" || prevToken == "الفين" || thousandsTokens.contains(prevToken))
                val isPrevHundred = prevToken != null && (prevToken == "مية" || prevToken == "ميه" || prevToken == "مائة" || prevToken == "مئة" || prevToken == "ميتين" || prevToken == "مائتين" || prevToken == "مئتين")

                if (isPrevThousand) {
                    total += 500.0
                } else if (isPrevHundred) {
                    total += 50.0
                } else {
                    total += 0.5
                }
                matchedAny = true
                i++
                continue
            }

            if (token == "ربع") {
                val prevToken = if (i > 0) rawTokens[i - 1].removePrefix("ب").removePrefix("و") else null
                val isPrevThousand = prevToken != null && (prevToken == "ألف" || prevToken == "الف" || prevToken == "ألفين" || prevToken == "الفين" || thousandsTokens.contains(prevToken))
                val isPrevHundred = prevToken != null && (prevToken == "مية" || prevToken == "ميه" || prevToken == "مائة" || prevToken == "مئة" || prevToken == "ميتين" || prevToken == "مائتين" || prevToken == "مئتين")

                if (isPrevThousand) {
                    total += 250.0
                } else if (isPrevHundred) {
                    total += 25.0
                } else {
                    total += 0.25
                }
                matchedAny = true
                i++
                continue
            }

            val valDirect = wordToNumberMap[token] ?: wordToNumberMap[rawTokens[i].removePrefix("ب")]
            if (valDirect != null) {
                total += valDirect
                matchedAny = true
            }

            i++
        }

        if (matchedAny && total > 0.0) {
            return total
        }

        return null
    }
}
