package org.mohtech.app.ai.nlp

import org.mohtech.app.data.model.BankSmsProof
import org.mohtech.app.data.model.ExpenseBatch
import org.mohtech.app.data.model.ExpenseCategory
import org.mohtech.app.data.model.ExpenseItem
import org.mohtech.app.data.model.ExpenseSplit
import org.mohtech.app.data.model.Payer
import org.mohtech.app.data.model.ProofType

/**
 * LocalDialectExpenseParser:
 * 100% Offline, Rule-based Arabic computational linguistics engine for colloquial dialects.
 */
class LocalDialectExpenseParser {

    private val numberWordTokens = setOf(
        "نص", "ربع", "واحد", "واحدة", "اثنين", "اتنين", "ثلاثة", "تلاتة", "أربعة", "اربعة", "خمسة",
        "ستة", "سبعة", "ثمانية", "تمانية", "تسعة", "عشرة",
        "عشرين", "ثلاثين", "تلاتين", "أربعين", "اربعين", "خمسين", "ستين", "سبعين", "ثمانين", "تمانين", "تسعين",
        "مية", "مائة", "مئة", "ميتين", "مائتين", "مئتين", "ثلاثمية", "تلاتمية", "أربعمية", "اربعمية",
        "خمسمية", "خمسمائة", "ستمية", "سبعمية", "ثمانمية", "تمانمية", "تسعمية",
        "ألف", "الف", "ألفين", "الفين", "آلاف", "الاف"
    )

    private val currencyTokens = setOf(
        "شيكل", "شواكل", "ريال", "ريالات", "جنيه", "جنيهات", "دولار", "دينار", "ILS", "SAR", "EGP", "USD"
    )

    fun parseBankSms(smsBody: String, currentTimestamp: Long = 0L): BankSmsProof? {
        return BankSmsParser.parse(smsBody, currentTimestamp)
    }

    fun parseSpokenText(
        spokenText: String,
        defaultCurrency: String = "₪",
        currentTimestamp: Long = 0L
    ): ExpenseBatch {
        val normalizedText = ArabicNumberNormalizer.convertIndicDigitsToAscii(spokenText.trim())

        // 1. Detect Currency
        val currency = detectCurrency(normalizedText, defaultCurrency)

        // 2. Detect Amount
        val amount = extractAmount(normalizedText)

        // 3. Detect Category
        val category = detectCategory(normalizedText)

        // 4. Detect Payer
        val payer = detectPayer(normalizedText)

        // 5. Detect Split
        val split = detectSplit(normalizedText, amount, payer)

        // 6. Extract Clean Title
        val title = extractTitle(spokenText, category)

        val item = ExpenseItem(
            id = "exp_${currentTimestamp}_${(100..999).random()}",
            title = title,
            amount = amount,
            currency = currency,
            category = category,
            timestamp = currentTimestamp,
            payer = payer,
            split = split,
            proofType = ProofType.NONE,
            rawSpokenText = spokenText
        )

        return ExpenseBatch(
            batchId = "batch_${currentTimestamp}",
            items = listOf(item),
            totalBatchAmount = amount,
            createdAt = currentTimestamp
        )
    }

    private fun detectCurrency(text: String, defaultCurrency: String): String {
        return when {
            text.contains("شيكل") || text.contains("شواكل") || text.contains("ILS") -> "₪"
            text.contains("ريال") || text.contains("ريالات") || text.contains("SAR") -> "ر.س"
            text.contains("جنيه") || text.contains("جنيهات") || text.contains("EGP") -> "ج.م"
            text.contains("دولار") || text.contains("USD") -> "$"
            text.contains("دينار") || text.contains("JOD") -> "د.أ"
            else -> defaultCurrency
        }
    }

    private fun extractAmount(text: String): Double {
        // First check explicit digit preceded by 'ب' or adjacent to currency:
        // e.g., "ب 50 شيكل", "ب 15 شيكل", "ب 120 جنيه", "١٢٠ ريال" (120 ريال)
        val prepDigitRegex = Regex("""(?:^|\s)ب\s*([0-9]+(?:\.[0-9]+)?)""")
        prepDigitRegex.find(text)?.let {
            it.groupValues[1].toDoubleOrNull()?.let { amt -> return amt }
        }

        val currencyDigitRegex = Regex("""([0-9]+(?:\.[0-9]+)?)\s*(?:شيكل|شواكل|ريال|ريالات|جنيه|جنيهات|دولار|دينار|ILS|SAR|EGP|USD)""")
        currencyDigitRegex.find(text)?.let {
            it.groupValues[1].toDoubleOrNull()?.let { amt -> return amt }
        }

        // Generic standalone digit (fallback if no 'ب' or currency next to it)
        val standaloneDigitRegex = Regex("""\b([0-9]+(?:\.[0-9]+)?)\b""")
        standaloneDigitRegex.find(text)?.let {
            it.groupValues[1].toDoubleOrNull()?.let { amt -> return amt }
        }

        // Check Arabic Number Words (e.g. "بعشرة", "مية وخمسين", "خمسمية", "بستين", "بثلاثين", "ألفين وميتين", "عشرين")
        val words = text.split(Regex("\\s+"))
        var i = 0
        while (i < words.size) {
            val w = words[i].trim()
            val cleanW = w.removePrefix("ب").removePrefix("و")

            // Check if this token starts an Arabic number word sequence
            if (numberWordTokens.contains(cleanW) && cleanW != "نص" && cleanW != "ربع") {
                val candidateTokens = mutableListOf<String>()
                candidateTokens.add(w)
                var j = i + 1
                while (j < words.size) {
                    val nextW = words[j].trim()
                    val cleanNext = nextW.removePrefix("و").removePrefix("ب")
                    if (numberWordTokens.contains(cleanNext) && cleanNext != "نص" && cleanNext != "ربع") {
                        candidateTokens.add(nextW)
                        j++
                    } else {
                        break
                    }
                }
                val phrase = candidateTokens.joinToString(" ")
                val parsed = ArabicNumberNormalizer.parseArabicNumberWords(phrase)
                if (parsed != null && parsed > 0.0) {
                    return parsed
                }
                i = j
            } else {
                i++
            }
        }

        return 0.0
    }

    private fun detectCategory(text: String): ExpenseCategory {
        val lower = text.lowercase()
        val wordTokens = lower.split(Regex("[\\s,.:;!؟]+")).map { it.trim() }.toSet()

        return when {
            // Transport
            lower.contains("بنزين") || lower.contains("سولار") || lower.contains("ديزل") ||
            lower.contains("وقود") || lower.contains("تكسي") || lower.contains("تاكسي") ||
            lower.contains("أوبر") || lower.contains("كريم") || lower.contains("مواصلات") ||
            lower.contains("باص") || lower.contains("كارت باص") || lower.contains("كوشوك") ||
            lower.contains("إطارات") || lower.contains("تصليح") -> ExpenseCategory.TRANSPORT

            // Dining
            lower.contains("قهوة") || lower.contains("شاي") || lower.contains("كابتشينو") ||
            lower.contains("عشاء") || lower.contains("غداء") || lower.contains("فطور") ||
            lower.contains("مطعم") || lower.contains("كافيه") || lower.contains("شاورما") ||
            lower.contains("برجر") || lower.contains("بيتزا") || lower.contains("حلا") ||
            lower.contains("سناك") || wordTokens.contains("بن") -> ExpenseCategory.DINING

            // Groceries
            lower.contains("سوبرماركت") || lower.contains("ماركت") || lower.contains("كارفور") ||
            lower.contains("برافو") || lower.contains("بقالة") || lower.contains("تموينات") ||
            lower.contains("خضار") || lower.contains("فواكه") || lower.contains("حليب") ||
            lower.contains("لبن") || lower.contains("بيض") || lower.contains("جبنة") ||
            lower.contains("خبز") || wordTokens.contains("رز") -> ExpenseCategory.GROCERIES

            // Bills
            lower.contains("فاتورة") || lower.contains("كهرباء") || lower.contains("مياه") ||
            lower.contains("إنترنت") || lower.contains("رصيد") ||
            lower.contains("إيجار") || lower.contains("ايجار") || lower.contains("قسط") ||
            lower.contains("اشتراك") || wordTokens.contains("نت") -> ExpenseCategory.BILLS

            // Health
            lower.contains("صيدلية") || lower.contains("صيدليه") || lower.contains("دواء") ||
            lower.contains("علاج") || lower.contains("دكتور") || lower.contains("طبيب") ||
            lower.contains("مستشفى") || lower.contains("عيادة") || lower.contains("النهدي") -> ExpenseCategory.HEALTH

            else -> ExpenseCategory.GENERAL
        }
    }

    private fun detectPayer(text: String): Payer {
        // Pattern 1: "على حساب [الاسم]"
        val onAccountRegex = Regex("""على\s+حساب\s+([^\s,]+)""")
        onAccountRegex.find(text)?.let {
            val name = it.groupValues[1].trim()
            if (name != "أنا" && name != "حسابي") {
                return Payer(id = name, name = name, isCurrentUser = false)
            }
        }

        // Pattern 2: "دفعها [الاسم]" / "دفعه [الاسم]" / "دفع [الاسم]" / "سددها [الاسم]"
        val paidByRegex = Regex("""(?:دفعها|دفعه|دفع|سددها|سدد)\s+([^\s,]+)""")
        paidByRegex.find(text)?.let {
            val name = it.groupValues[1].trim()
            if (name != "أنا" && !name.startsWith("ت")) { // avoid "دفعت"
                return Payer(id = name, name = name, isCurrentUser = false)
            }
        }

        return Payer(id = "self", name = "أنا", isCurrentUser = true)
    }

    private fun detectSplit(text: String, totalAmount: Double, currentPayer: Payer): ExpenseSplit? {
        val betweenRegex = Regex("""بيني\s+وبين\s+([^\s,]+)""")
        val match = betweenRegex.find(text)
        if (match != null) {
            val otherPerson = match.groupValues[1].trim()
            val participants = listOf(
                Payer("self", "أنا", true),
                Payer(otherPerson, otherPerson, false)
            )
            val half = totalAmount / 2.0
            return ExpenseSplit(
                totalAmount = totalAmount,
                payer = currentPayer,
                participants = participants,
                amountPerPerson = half,
                note = "قسمة بيني وبين $otherPerson"
            )
        }

        if (text.contains("قسمة علينا") || text.contains("النص بالنص")) {
            val participants = listOf(
                Payer("self", "أنا", true),
                Payer("other", "الشريك", false)
            )
            return ExpenseSplit(
                totalAmount = totalAmount,
                payer = currentPayer,
                participants = participants,
                amountPerPerson = totalAmount / 2.0,
                note = "قسمة بالنصف"
            )
        }

        return null
    }

    private fun extractTitle(spokenText: String, category: ExpenseCategory): String {
        return when (category) {
            ExpenseCategory.GROCERIES -> {
                when {
                    spokenText.contains("سوبرماركت") -> "سوبرماركت"
                    spokenText.contains("نص كيلو جبنة") -> "نص كيلو جبنة"
                    spokenText.contains("خضار") -> "خضار"
                    spokenText.contains("حليب") -> "حليب وبقالة"
                    else -> "مشتريات بقالة"
                }
            }
            ExpenseCategory.DINING -> {
                when {
                    spokenText.contains("قهوة وحلا") -> "قهوة وحلا"
                    spokenText.contains("قهوة") -> "قهوة"
                    spokenText.contains("عشاء") -> "عشاء"
                    spokenText.contains("غداء") -> "غداء"
                    spokenText.contains("فطور") -> "فطور"
                    spokenText.contains("شاورما") -> "شاورما"
                    spokenText.contains("ربع كيلو بن") -> "ربع كيلو بن"
                    else -> "مطعم ومقهى"
                }
            }
            ExpenseCategory.TRANSPORT -> {
                when {
                    spokenText.contains("تصليح كوشوك السيارة") -> "تصليح كوشوك السيارة"
                    spokenText.contains("بنزين") -> "بنزين"
                    spokenText.contains("تكسي") -> "تكسي"
                    spokenText.contains("أوبر") -> "أوبر"
                    else -> "مواصلات ووقود"
                }
            }
            ExpenseCategory.BILLS -> {
                when {
                    spokenText.contains("فاتورة الكهرباء") -> "فاتورة الكهرباء"
                    spokenText.contains("إيجار البيت") || spokenText.contains("ايجار البيت") -> "إيجار البيت"
                    spokenText.contains("إنترنت") -> "إنترنت"
                    else -> "فواتير والتزامات"
                }
            }
            ExpenseCategory.HEALTH -> {
                when {
                    spokenText.contains("دواء من الصيدلية") || spokenText.contains("دواء") -> "دواء وصيدلية"
                    spokenText.contains("صيدلية") -> "صيدلية"
                    else -> "صحة وعلاج"
                }
            }
            ExpenseCategory.GENERAL -> {
                when {
                    spokenText.contains("هدية") -> "هدية"
                    else -> "مصروف عام"
                }
            }
        }
    }
}
