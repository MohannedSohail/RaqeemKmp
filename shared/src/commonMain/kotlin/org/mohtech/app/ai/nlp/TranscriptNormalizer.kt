package org.mohtech.app.ai.nlp

import kotlin.math.min

/**
 * Record of a single textual or phonetic replacement made by the [TranscriptNormalizer].
 */
data class ReplacementRecord(
    val original: String,
    val replacement: String,
    val tokenIndex: Int,
    val reason: String
)

/**
 * Output of [TranscriptNormalizer.normalize].
 */
data class NormalizationResult(
    val normalizedText: String,
    val replacements: List<ReplacementRecord>
)

/**
 * TranscriptNormalizer:
 * Pure Kotlin on-device post-Whisper text normalizer.
 * Applies context-sensitive spelling corrections and restricted phonetic edit-distance matching
 * (currencies, numbers, payments, categories) without altering semantic meaning.
 * Strictly preserves person names and context words (e.g. "شكل الفاتورة", "فاتورة مياه").
 */
object TranscriptNormalizer {

    private val numberWordTokens = setOf(
        "صفر", "واحد", "واحدة", "اثنين", "اتنين", "تنين", "ثلاث", "تلات", "ثلاثة", "تلاتة", "ثلاثه", "تلاته",
        "أربع", "اربع", "أربعة", "اربعة", "اربعه", "خمس", "خمسة", "خمسه", "ست", "ستة", "سته",
        "سبع", "سبعة", "سبعه", "ثمان", "تمان", "ثمانية", "تمانية", "ثمانيه", "تمانيه",
        "تسع", "تسعة", "تسعه", "عشر", "عشرة", "عشره", "عشرين", "ثلاثين", "تلاتين",
        "أربعين", "اربعين", "خمسين", "ستين", "سبعين", "ثمانين", "تمانين", "تسعين",
        "مية", "ميه", "ميت", "مائة", "مئة", "ميتين", "مائتين", "مئتين", "ثلاثمية", "تلاتمية",
        "أربعمية", "اربعمية", "خمسمية", "خمسمائة", "خمسمئة", "ستمية", "سبعمية", "ثمانمية",
        "تمانمية", "تسعمية", "ألف", "الف", "ألفين", "الفين", "آلاف", "الاف", "تلاف", "نص", "ربع"
    )

    private val protectedPrefixPhrases = listOf(
        "بيني وبين",
        "على حساب",
        "دفعها",
        "دفعه",
        "دفع",
        "سددها",
        "سدده",
        "سدد",
        "دفعت عن",
        "سددت عن",
        "دفع عني",
        "سدد عني",
        "دفعها لي",
        "سددها لي"
    )

    private val targetVocabulary = setOf(
        "شيكل", "شواكل", "شيكلين", "ريال", "ريالات", "ريالين", "جنيه", "جنيهات", "جنيهين", "دولار", "دينار", "دينارين",
        "واحد", "اثنين", "ثلاثة", "اربعة", "خمسة", "ستة", "سبعة", "ثمانية", "تسعة", "عشرة",
        "عشرين", "ثلاثين", "اربعين", "خمسين", "ستين", "سبعين", "ثمانين", "تسعين",
        "مية", "ميتين", "الف", "الفين", "الاف",
        "دفعت", "حاسبت", "سددت", "اشتريت",
        "بنزين", "سوبرماركت", "صيدلية", "فاتورة", "مطعم", "قهوة", "خضار", "حليب", "كهرباء",
        "مواصلات", "شاورما", "فلافل", "كابتشينو", "إيجار", "إنترنت", "توصيل", "دليفري",
        "كارفور", "بقالة", "تموينات", "مستشفى", "علاج", "كوشوك", "تاكسي", "كافيه", "ميكانيكي"
    )

    private val explicitWhisperTypos = mapOf(
        "شايكل" to "شيكل",
        "شواكيل" to "شواكل",
        "شكلين" to "شيكلين",
        "بنزن" to "بنزين",
        "سبرماركت" to "سوبرماركت",
        "سوبارماركت" to "سوبرماركت",
        "كبوتشينو" to "كابتشينو",
        "صيضلية" to "صيدلية",
        "صيضليه" to "صيدلية",
        "كهربه" to "كهرباء",
        "إجار" to "إيجار",
        "جنية" to "جنيه",
        "جنه" to "جنيه",
        "توكسي" to "تاكسي",
        "شاورمه" to "شاورما"
    )

    /**
     * Context-aware normalization pass.
     */
    fun normalize(spokenText: String): NormalizationResult {
        if (spokenText.isBlank()) {
            return NormalizationResult(spokenText, emptyList())
        }

        val rawTokens = spokenText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val replacements = mutableListOf<ReplacementRecord>()
        val outputTokens = rawTokens.toMutableList()

        // 1. Identify token positions that are protected person names
        val protectedIndices = mutableSetOf<Int>()
        for (i in rawTokens.indices) {
            val window2 = if (i >= 2) "${rawTokens[i - 2]} ${rawTokens[i - 1]}" else ""
            val window1 = if (i >= 1) rawTokens[i - 1] else ""
            if (protectedPrefixPhrases.any { it == window2 || it == window1 }) {
                protectedIndices.add(i)
            }
        }

        for (i in outputTokens.indices) {
            if (protectedIndices.contains(i)) {
                // Never touch person names
                continue
            }

            val current = outputTokens[i]

            // Direct colloquial/Whisper typo check (check full word first, then prefix-stripped)
            val directFullTypo = explicitWhisperTypos[current]
            if (directFullTypo != null) {
                outputTokens[i] = directFullTypo
                replacements.add(ReplacementRecord(current, directFullTypo, i, "Colloquial typo: '$current' -> '$directFullTypo'"))
                continue
            }

            val stripped = current.removePrefix("ب").removePrefix("و")
            val directStrippedTypo = explicitWhisperTypos[stripped]
            if (directStrippedTypo != null) {
                val pref = when {
                    current.startsWith("ب") && current != stripped -> "ب"
                    current.startsWith("و") && current != stripped -> "و"
                    else -> ""
                }
                val replaced = pref + directStrippedTypo
                outputTokens[i] = replaced
                replacements.add(ReplacementRecord(current, replaced, i, "Colloquial typo: '$current' -> '$replaced'"))
                continue
            }

            // Context Rule 1: "شكل" -> "شيكل" only if adjacent to a digit or number word
            if (stripped == "شكل") {
                val prev = if (i > 0) outputTokens[i - 1] else null
                val next = if (i + 1 < outputTokens.size) outputTokens[i + 1] else null
                val isPrevNumber = prev != null && isNumberOrDigit(prev)
                val isNextNumber = next != null && isNumberOrDigit(next)

                if (isPrevNumber || isNextNumber) {
                    val replaced = if (current.startsWith("ب")) "بشيكل" else if (current.startsWith("و")) "وشيكل" else "شيكل"
                    outputTokens[i] = replaced
                    replacements.add(ReplacementRecord(current, replaced, i, "Contextual currency: 'شكل' -> 'شيكل'"))
                    continue
                }
            }

            // Context Rule 2: "ميه" / "مياه" -> "مية" only inside a compound number phrase
            if (stripped == "ميه" || stripped == "مياه") {
                val prev = if (i > 0) outputTokens[i - 1] else null
                val next = if (i + 1 < outputTokens.size) outputTokens[i + 1] else null
                val isPrevNumber = prev != null && isNumberOrDigit(prev)
                val isNextNumber = next != null && isNumberOrDigit(next)

                // Only normalize to "مية" if part of compound number (e.g. "ميه وخمسين", "تلات ميه")
                // Do NOT normalize if preceded by "فاتورة" (e.g. "فاتورة مياه") or standalone
                val isPrecededByBill = prev != null && (prev.contains("فاتورة") || prev.contains("فاتوره"))
                if (!isPrecededByBill && (isPrevNumber || isNextNumber)) {
                    val replaced = if (current.startsWith("ب")) "بمية" else if (current.startsWith("و")) "ومية" else "مية"
                    outputTokens[i] = replaced
                    replacements.add(ReplacementRecord(current, replaced, i, "Contextual number: '$current' -> '$replaced'"))
                    continue
                }
            }

            // If the token is already a known vocabulary or number word or protected word, do not touch
            if (targetVocabulary.contains(current) || numberWordTokens.contains(current) || current == "مياه" || current == "شكل") {
                continue
            }

            // Phonetic Levenshtein matching on restricted vocabulary
            val prefix = when {
                current.startsWith("ب") && current.length > 2 && targetVocabulary.contains(current.removePrefix("ب")) -> "ب"
                current.startsWith("و") && current.length > 2 && targetVocabulary.contains(current.removePrefix("و")) -> "و"
                current.startsWith("ب") && current.length > 3 -> "ب"
                current.startsWith("و") && current.length > 3 -> "و"
                else -> ""
            }
            val coreWord = current.removePrefix(prefix)

            // If the core word is not already in vocabulary or number words, check distance
            if (!targetVocabulary.contains(coreWord) && !numberWordTokens.contains(coreWord) && coreWord != "مياه" && coreWord != "شكل") {
                val bestMatch = findPhoneticMatch(coreWord)
                if (bestMatch != null && bestMatch != coreWord) {
                    val replaced = prefix + bestMatch
                    outputTokens[i] = replaced
                    replacements.add(ReplacementRecord(current, replaced, i, "Phonetic match: '$coreWord' -> '$bestMatch'"))
                }
            }
        }

        return NormalizationResult(
            normalizedText = outputTokens.joinToString(" "),
            replacements = replacements
        )
    }

    private fun isNumberOrDigit(token: String): Boolean {
        val clean = token.removePrefix("ب").removePrefix("و")
        if (clean.any { it in '0'..'9' || it in '٠'..'٩' }) return true
        return numberWordTokens.contains(clean)
    }

    /**
     * Normalized phonetic representation for colloquial Arabic:
     * - Removes diacritics and tatweel
     * - Unifies alefs (أ, إ, آ -> ا)
     * - Unifies teh marbuta (ة -> ه)
     * - Unifies alef maqsura (ى -> ي)
     * - Maps th/dh/z dialect shifts (ث -> س, ذ -> د/ز, ظ -> ض)
     * - Maps qaf dialect shifts (ق -> ك or hamza)
     */
    fun phoneticKey(word: String): String {
        val sb = StringBuilder()
        for (ch in word) {
            when (ch) {
                'أ', 'إ', 'آ', 'ٱ' -> sb.append('ا')
                'ة' -> sb.append('ه')
                'ى' -> sb.append('ي')
                'ث' -> sb.append('س')
                'ذ' -> sb.append('ز')
                'ظ' -> sb.append('ض')
                'ق' -> sb.append('ك')
                'َ', 'ُ', 'ِ', 'ً', 'ٌ', 'ٍ', 'ْ', 'ّ', 'ـ' -> { /* ignore diacritics / tatweel */ }
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun findPhoneticMatch(word: String): String? {
        if (word.length < 3) return null
        val wordKey = phoneticKey(word)

        var bestMatch: String? = null
        var bestDist = Int.MAX_VALUE

        for (candidate in targetVocabulary) {
            val candidateKey = phoneticKey(candidate)
            val dist = levenshteinDistance(wordKey, candidateKey)
            if (dist <= 1 && dist < bestDist) {
                bestDist = dist
                bestMatch = candidate
            } else if (dist == bestDist && bestMatch != null) {
                // Ambiguity / tie -> do not replace
                bestMatch = null
            }
        }

        return bestMatch
    }

    fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(
                    dp[i - 1][j] + 1,
                    min(dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
                )
            }
        }
        return dp[m][n]
    }
}

