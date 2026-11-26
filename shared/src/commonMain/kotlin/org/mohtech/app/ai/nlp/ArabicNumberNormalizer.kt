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
        "أحد" to 1.0,
        "احد" to 1.0,
        "إحدى" to 1.0,
        "احدى" to 1.0,
        "اثنين" to 2.0,
        "إثنين" to 2.0,
        "اتنين" to 2.0,
        "إتنين" to 2.0,
        "تنين" to 2.0,
        "اثنان" to 2.0,
        "إثنان" to 2.0,
        "اثني" to 2.0,
        "إثني" to 2.0,
        "اثنا" to 2.0,
        "إثنا" to 2.0,
        "اتنان" to 2.0,
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
        "عشرون" to 20.0,
        "ثلاثين" to 30.0,
        "ثلاثون" to 30.0,
        "تلاتين" to 30.0,
        "أربعين" to 40.0,
        "أربعون" to 40.0,
        "اربعين" to 40.0,
        "اربعون" to 40.0,
        "خمسين" to 50.0,
        "خمسون" to 50.0,
        "ستين" to 60.0,
        "ستون" to 60.0,
        "سبعين" to 70.0,
        "سبعون" to 70.0,
        "ثمانين" to 80.0,
        "ثمانون" to 80.0,
        "تمانين" to 80.0,
        "تمانون" to 80.0,
        "تسعين" to 90.0,
        "تسعون" to 90.0,
        "أحد عشر" to 11.0,
        "احد عشر" to 11.0,
        "إحدى عشر" to 11.0,
        "احدى عشر" to 11.0,
        "احداعش" to 11.0,
        "حداعش" to 11.0,
        "حداشر" to 11.0,
        "اثنا عشر" to 12.0,
        "اثني عشر" to 12.0,
        "إثنا عشر" to 12.0,
        "إثني عشر" to 12.0,
        "اثناعش" to 12.0,
        "اثنعش" to 12.0,
        "اتناعش" to 12.0,
        "اتناشر" to 12.0,
        "ثلاثة عشر" to 13.0,
        "تلاتة عشر" to 13.0,
        "تلاتاشر" to 13.0,
        "أربعة عشر" to 14.0,
        "اربعة عشر" to 14.0,
        "اربعتاشر" to 14.0,
        "خمسة عشر" to 15.0,
        "خمستاشر" to 15.0,
        "ستة عشر" to 16.0,
        "ستاشر" to 16.0,
        "سبعة عشر" to 17.0,
        "سبعتاشر" to 17.0,
        "ثمانية عشر" to 18.0,
        "تمانية عشر" to 18.0,
        "تمنتاشر" to 18.0,
        "تسعة عشر" to 19.0,
        "تسعتاشر" to 19.0,
        "مية" to 100.0,
        "ميه" to 100.0,
        "ميت" to 100.0,
        "مائة" to 100.0,
        "مئة" to 100.0,
        "ميتين" to 200.0,
        "مائتين" to 200.0,
        "مئتين" to 200.0,
        "ثلاثمية" to 300.0,
        "ثلاثميت" to 300.0,
        "تلاتمية" to 300.0,
        "تلاتميت" to 300.0,
        "تلاتميه" to 300.0,
        "ثلاثمائة" to 300.0,
        "أربعمية" to 400.0,
        "أربعميت" to 400.0,
        "اربعمية" to 400.0,
        "اربعميت" to 400.0,
        "اربعميه" to 400.0,
        "أربعمائة" to 400.0,
        "خمسمية" to 500.0,
        "خمسميت" to 500.0,
        "خمسميه" to 500.0,
        "خمسمائة" to 500.0,
        "خمسمئة" to 500.0,
        "ستمية" to 600.0,
        "ستميت" to 600.0,
        "ستميه" to 600.0,
        "ستمائة" to 600.0,
        "سبعمية" to 700.0,
        "سبعميت" to 700.0,
        "سبعميه" to 700.0,
        "سبعمائة" to 700.0,
        "ثمانمية" to 800.0,
        "ثمانميت" to 800.0,
        "تمانمية" to 800.0,
        "تمانميت" to 800.0,
        "تمانميه" to 800.0,
        "ثمانمائة" to 800.0,
        "تسعمية" to 900.0,
        "تسعميت" to 900.0,
        "تسعميه" to 900.0,
        "تسعمائة" to 900.0,
        "ألف" to 1000.0,
        "الف" to 1000.0,
        "ألفين" to 2000.0,
        "الفين" to 2000.0,
        "احدعش" to 11.0,
        "حدعش" to 11.0,
        "تلطعش" to 13.0,
        "تلاتعش" to 13.0,
        "اربعتعش" to 14.0,
        "اربعطعش" to 14.0,
        "خمستعش" to 15.0,
        "خمسطعش" to 15.0,
        "ستعش" to 16.0,
        "ستطعش" to 16.0,
        "سبعتعش" to 17.0,
        "سبعطعش" to 17.0,
        "تمنتعش" to 18.0,
        "تمنطعش" to 18.0,
        "تسعتعش" to 19.0,
        "تسعطعش" to 19.0
    )

    private val thousandsTokens = setOf("آلاف", "الاف", "ألاف", "الافاً", "تلاف", "تآلاف")

    private val dualCurrencyTokens = setOf(
        "شيكلين", "شكلين", "ريالين", "جنيهين", "جنهين", "دينارين", "دولارين", "ليرتين"
    )

    private val unitWords = setOf(
        "واحد", "واحدة", "أحد", "احد", "إحدى", "احدى",
        "اثنين", "إثنين", "اتنين", "إتنين", "تنين", "اثنان", "إثنان", "اثني", "إثني", "اثنا", "إثنا", "اتنان",
        "ثلاث", "تلات", "ثلاثة", "تلاتة", "تلاته", "ثلاثه",
        "أربع", "اربع", "أربعة", "اربعة", "اربعه",
        "خمس", "خمسة", "خمسه",
        "ست", "ستة", "سته",
        "سبع", "سبعة", "سبعه",
        "ثمان", "تمان", "ثمانية", "تمانية", "ثمانيه", "تمانيه",
        "تسع", "تسعة", "تسعه"
    )

    private val tensWords = setOf(
        "عشرين", "عشرون", "ثلاثين", "ثلاثون", "تلاتين",
        "أربعين", "أربعون", "اربعين", "اربعون", "خمسين", "خمسون",
        "ستين", "ستون", "سبعين", "سبعون", "ثمانين", "ثمانون", "تمانين", "تمانون",
        "تسعين", "تسعون"
    )

    private val hundredsWords = setOf(
        "مية", "ميه", "ميت", "مائة", "مئة", "ميتين", "مائتين", "مئتين",
        "ثلاثمية", "ثلاثميت", "تلاتمية", "تلاتميت", "تلاتميه", "ثلاثمائة",
        "أربعمية", "أربعميت", "اربعمية", "اربعميت", "اربعميه", "أربعمائة",
        "خمسمية", "خمسميت", "خمسميه", "خمسمائة", "خمسمئة",
        "ستمية", "ستميت", "ستميه", "ستمائة",
        "سبعمية", "سبعميت", "سبعميه", "سبعمائة",
        "ثمانمية", "ثمانميت", "تمانمية", "تمانميت", "تمانميه", "ثمانمائة",
        "تسعمية", "تسعميت", "تسعميه", "تسعمائة"
    )

    private val thousandsWords = setOf("ألف", "الف", "ألفين", "الفين", "آلاف", "الاف", "ألاف", "تلاف", "تآلاف")
    private val fractionWords = setOf("نص", "نصف", "ربع")

    /**
     * Checks if a dual currency noun represents 2 units (e.g., "شيكلين" -> 2.0).
     */
    fun parseDualCurrencyAmount(token: String): Double? {
        val clean = token.removePrefix("ب").removePrefix("و").trim()
        return if (dualCurrencyTokens.contains(clean)) 2.0 else null
    }

    /**
     * Determines whether `prevRaw` + "و" + `nextRaw` represents a single compound Arabic number
     * (e.g., "مية وخمسين", "خمسة وعشرين", "ألفين وميتين", "مية ونص") rather than two distinct expense amounts
     * (e.g., "خمسين ومية" or "50 و 20").
     */
    fun isValidCompoundJoin(prevRaw: String, nextRaw: String): Boolean {
        val prev = prevRaw.removePrefix("ب").removePrefix("و").trim()
        val next = nextRaw.removePrefix("ب").removePrefix("و").trim()

        val prevIsDigits = prev.isNotEmpty() && prev.all { it.isDigit() || it == '.' }
        val nextIsDigits = next.isNotEmpty() && next.all { it.isDigit() || it == '.' }

        // Digits joined by "و" (e.g., "50 و 20" or "50 ومية") are always separate items
        if (prevIsDigits || nextIsDigits) return false

        val prevRank = getNumberPlaceRank(prev)
        val nextRank = getNumberPlaceRank(next)
        if (prevRank < 0 || nextRank < 0) return false

        // Descending magnitude: thousands -> hundreds/tens/units/fraction, hundreds -> tens/units/fraction, tens -> fraction
        if (prevRank > nextRank) return true

        // Special Arabic syntax: units (1-9) precede tens (20-90), e.g., "خمسة وعشرين"
        if (unitWords.contains(prev) && tensWords.contains(next)) return true

        return false
    }

    private fun getNumberPlaceRank(cleanToken: String): Int = when {
        thousandsWords.contains(cleanToken) -> 4
        hundredsWords.contains(cleanToken) -> 3
        tensWords.contains(cleanToken) -> 2
        unitWords.contains(cleanToken) || wordToNumberMap.containsKey(cleanToken) -> 1
        fractionWords.contains(cleanToken) -> 0
        else -> -1
    }

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
