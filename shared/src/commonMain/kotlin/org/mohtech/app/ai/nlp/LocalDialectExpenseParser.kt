package org.mohtech.app.ai.nlp

import kotlin.time.Clock
import org.mohtech.app.data.accounting.Money
import org.mohtech.app.data.model.Allocation
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

    companion object {
        private var idCounter: Long = 1000L
        fun nextSequence(): Long = ++idCounter

        private val quantityUnits = setOf(
            "كيلو", "كيلوغرام", "كيلو غرام", "كغ", "غرام", "جرام", "لتر", "علبة", "علب",
            "حبة", "حبات", "كرتونة", "باكيت", "ربطة", "قرص"
        )
    }

    private val numberWordTokens = setOf(
        "نص", "ربع", "واحد", "واحدة", "اثنين", "اتنين", "ثلاث", "تلات", "ثلاثة", "تلاتة",
        "أربع", "اربع", "أربعة", "اربعة", "خمس", "خمسة",
        "ست", "ستة", "سبع", "سبعة", "ثمان", "تمان", "ثمانية", "تمانية", "تسع", "تسعة", "عشر", "عشرة",
        "عشرين", "ثلاثين", "تلاتين", "أربعين", "اربعين", "خمسين", "ستين", "سبعين", "ثمانين", "تمانين", "تسعين",
        "مية", "مائة", "مئة", "ميه", "ميتين", "مائتين", "مئتين", "ثلاثمية", "تلاتمية", "أربعمية", "اربعمية",
        "خمسمية", "خمسمائة", "خمسمئة", "ستمية", "سبعمية", "ثمانمية", "تمانمية", "تسعمية",
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
        val trimmed = spokenText.trim()
        val normalizedText = ArabicNumberNormalizer.convertIndicDigitsToAscii(trimmed)
        val ts = if (currentTimestamp > 0L) currentTimestamp else Clock.System.now().toEpochMilliseconds()

        // 1. Detect Currency
        val currency = detectCurrency(normalizedText, defaultCurrency)

        // 2. Check for Allocation / Debt Distribution format
        val allocationBatch = tryParseAllocations(spokenText, normalizedText, currency, ts, currentTimestamp)
        if (allocationBatch != null) {
            return allocationBatch
        }

        // 3. Check for Multi-Item Spoken Phrase (e.g. "دفعت 30 بنزين و15 قهوة و100 سوبرماركت")
        val multiItemBatch = tryParseMultiItems(spokenText, normalizedText, currency, ts, currentTimestamp)
        if (multiItemBatch != null) {
            return multiItemBatch
        }

        // 4. Single-Item Fallback Parsing
        val amount = extractAmount(normalizedText)
        val category = detectCategory(normalizedText)
        val payer = detectPayer(normalizedText)
        val seq = nextSequence()
        val (split, calculatedMyShare) = detectSplit(normalizedText, amount, currency, payer, ts, seq)
        val title = extractTitle(spokenText, category)

        val item = ExpenseItem(
            id = "exp_${ts}_$seq",
            title = title,
            amount = amount,
            currency = currency,
            category = category,
            timestamp = currentTimestamp,
            payer = payer,
            split = split,
            proofType = ProofType.NONE,
            rawSpokenText = spokenText,
            myShare = calculatedMyShare
        )

        return ExpenseBatch(
            batchId = "batch_$ts",
            items = listOf(item),
            totalBatchAmount = amount,
            createdAt = ts
        )
    }

    private fun tryParseAllocations(
        spokenText: String,
        normalizedText: String,
        currency: String,
        ts: Long,
        currentTimestamp: Long
    ): ExpenseBatch? {
        val hasMyShareKeyword = normalizedText.contains("إلي") || normalizedText.contains("لي") ||
            normalizedText.contains("نصيبي") || normalizedText.contains("حصتي")
        val hasRemainderKeyword = normalizedText.contains("الباقي على") || normalizedText.contains("والباقي على") ||
            normalizedText.contains("الباقي ل") || normalizedText.contains("والباقي ل")
        val hasPartyKeyword = Regex("""(?:^|\s)(?:و)?(?:[0-9]+|[^\s,]+)\s+ل[^\s,]+""").containsMatchIn(normalizedText)

        if (!hasMyShareKeyword && !hasRemainderKeyword && !hasPartyKeyword) {
            return null
        }

        // Try extracting total amount from the first clause or overall text
        val firstClause = normalizedText.split(
            Regex("""(?=\b(?:ثلاثين|أربعين|عشرين|خمسين|ستين|سبعين|ثمانين|تسعين|مية|مائة|خمسمية|ألف|نصيبي|حصتي|إلي|لي|[0-9]+\s*(?:إلي|لي|نصيبي|حصتي|للبيت|ل))\b)""")
        ).firstOrNull() ?: normalizedText
        var totalAmount = extractAmount(firstClause)
        if (totalAmount <= 0.0) {
            totalAmount = extractAmount(normalizedText)
        }
        if (totalAmount <= 0.0) return null

        var myShare: Double? = null
        val allocations = mutableListOf<Allocation>()
        val seq = nextSequence()

        // 1. My Share: "ثلاثين إلي", "30 إلي", "نصيبي ثلاثين", "نصيبي 30", "حصتي 30", "30 لي"
        val mySharePatterns = listOf(
            Regex("""(?:نصيبي|حصتي)\s+([0-9]+(?:\.[0-9]+)?|[^\s,]+)"""),
            Regex("""([0-9]+(?:\.[0-9]+)?|[^\s,]+)\s+(?:إلي|لي|نصيبي|حصتي)""")
        )
        for (p in mySharePatterns) {
            val m = p.find(normalizedText)
            if (m != null) {
                val token = m.groupValues[1].trim()
                val parsedAmt = parseAmountToken(token)
                if (parsedAmt != null && parsedAmt > 0.0) {
                    myShare = parsedAmt
                    break
                }
            }
        }

        // 2. Explicitly allocated parties: e.g. "أربعين للبيت", "وثلاثين لأخوي", "40 للبيت", "30 لمحمد"
        val allocRegex = Regex("""(?:^|\s)(?:و)?([0-9]+(?:\.[0-9]+)?|[^\s,]+)\s+ل([^\s,]+)""")
        allocRegex.findAll(normalizedText).forEach { match ->
            val amtToken = match.groupValues[1].trim()
            val rawParty = match.groupValues[2].trim()

            if (rawParty != "ي" && rawParty != "نا" && rawParty != "ي_" && rawParty != "كم") {
                val parsedAmt = parseAmountToken(amtToken)
                if (parsedAmt != null && parsedAmt > 0.0) {
                    val partyName = when {
                        rawParty.startsWith("ل") && !rawParty.startsWith("لا") -> "ا" + rawParty
                        else -> rawParty
                    }
                    if (allocations.none { it.party.name == partyName }) {
                        val payer = Payer(id = partyName, name = partyName, isCurrentUser = false)
                        allocations.add(
                            Allocation(
                                id = "alloc_${ts}_${seq}_${partyName}",
                                party = payer,
                                amount = parsedAmt,
                                note = "مخصص لـ $partyName"
                            )
                        )
                    }
                }
            }
        }

        // 3. Remainder: e.g. "والباقي على محمد", "الباقي على البيت"
        val remainderRegex = Regex("""(?:والباقي|الباقي)\s+(?:على|ل)\s+([^\s,]+)""")
        val remainderMatch = remainderRegex.find(normalizedText)
        if (remainderMatch != null) {
            val partyName = remainderMatch.groupValues[1].trim()
            val currentSum = (myShare ?: 0.0) + allocations.sumOf { it.amount }
            val remainderAmt = (totalAmount - currentSum).coerceAtLeast(0.0)
            if (remainderAmt > 0.0 && allocations.none { it.party.name == partyName }) {
                val payer = Payer(id = partyName, name = partyName, isCurrentUser = false)
                allocations.add(
                    Allocation(
                        id = "alloc_${ts}_${seq}_${partyName}",
                        party = payer,
                        amount = remainderAmt,
                        note = "الباقي على $partyName"
                    )
                )
            }
        }

        if (allocations.isEmpty() && myShare == null) {
            return null
        }

        if (myShare == null && allocations.isNotEmpty()) {
            myShare = (totalAmount - allocations.sumOf { it.amount }).coerceAtLeast(0.0)
        }

        val category = detectCategory(normalizedText)
        val payer = detectPayer(normalizedText)
        val title = extractTitle(spokenText, category)

        val split = ExpenseSplit(
            totalAmount = totalAmount,
            payer = payer,
            participants = listOf(Payer("self", "أنا", true)) + allocations.map { it.party },
            amountPerPerson = myShare ?: totalAmount,
            note = "توزيع مستحقات",
            allocations = allocations
        )

        val item = ExpenseItem(
            id = "exp_${ts}_$seq",
            title = title,
            amount = totalAmount,
            currency = currency,
            category = category,
            timestamp = currentTimestamp,
            payer = payer,
            split = split,
            proofType = ProofType.NONE,
            rawSpokenText = spokenText,
            myShare = myShare
        )

        return ExpenseBatch(
            batchId = "batch_$ts",
            items = listOf(item),
            totalBatchAmount = totalAmount,
            createdAt = ts
        )
    }

    private fun tryParseMultiItems(
        spokenText: String,
        normalizedText: String,
        currency: String,
        ts: Long,
        currentTimestamp: Long
    ): ExpenseBatch? {
        if (normalizedText.contains("بيني وبين") || normalizedText.contains("قسمة علينا") ||
            normalizedText.contains("النص بالنص") || normalizedText.contains("سددت عن") ||
            normalizedText.contains("دفعت عن") || normalizedText.contains("دفع عني") ||
            normalizedText.contains("سدد عني") || normalizedText.contains("على حساب")
        ) {
            return null
        }

        // Split on conjunction 'و' when followed by a number or an item with amount
        val rawSegments = normalizedText.split(Regex("""(?<=[^\s])\s+و(?=\s*[0-9]+|\s*[^\s]+\s+ب\s*[0-9]+)"""))
        if (rawSegments.size < 2) {
            return null
        }

        val items = mutableListOf<ExpenseItem>()
        for (seg in rawSegments) {
            val trimmedSeg = seg.trim()
            val segAmt = extractAmount(trimmedSeg)
            if (segAmt <= 0.0) {
                return null
            }
            val segCategory = detectCategory(trimmedSeg)
            val segTitle = extractTitle(trimmedSeg, segCategory)
            val segSeq = nextSequence()
            items.add(
                ExpenseItem(
                    id = "exp_${ts}_$segSeq",
                    title = segTitle,
                    amount = segAmt,
                    currency = currency,
                    category = segCategory,
                    timestamp = currentTimestamp,
                    payer = Payer("self", "أنا", true),
                    proofType = ProofType.NONE,
                    rawSpokenText = trimmedSeg
                )
            )
        }

        if (items.size < 2) return null

        return ExpenseBatch(
            batchId = "batch_$ts",
            items = items,
            totalBatchAmount = items.sumOf { it.amount },
            createdAt = ts
        )
    }

    private fun parseAmountToken(token: String): Double? {
        val clean = token.removePrefix("و").removePrefix("ب").trim()
        clean.toDoubleOrNull()?.let { return it }
        return ArabicNumberNormalizer.parseArabicNumberWords(clean)
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

        // Generic standalone digit (make sure it's not a quantity like "2 كيلو")
        val standaloneDigitRegex = Regex("""\b([0-9]+(?:\.[0-9]+)?)\b""")
        for (match in standaloneDigitRegex.findAll(text)) {
            val amt = match.groupValues[1].toDoubleOrNull()
            if (amt != null) {
                val afterIndex = match.range.last + 1
                val afterText = if (afterIndex < text.length) text.substring(afterIndex).trimStart() else ""
                val firstWordAfter = afterText.split(Regex("\\s+")).firstOrNull() ?: ""
                if (!quantityUnits.contains(firstWordAfter)) {
                    return amt
                }
            }
        }

        // Check Arabic Number Words (e.g. "بعشرة", "مية وخمسين", "خمسمية", "بستين", "بثلاثين", "ألفين وميتين", "عشرين")
        val words = text.split(Regex("\\s+"))
        var i = 0
        while (i < words.size) {
            val w = words[i].trim()
            val cleanW = w.removePrefix("ب").removePrefix("و")

            val isFollowedByQuantity = (i + 1 < words.size && quantityUnits.contains(words[i + 1].trim()))

            // Check if this token starts an Arabic number word sequence
            if (numberWordTokens.contains(cleanW) && !isFollowedByQuantity && cleanW != "نص" && cleanW != "ربع") {
                val candidateTokens = mutableListOf<String>()
                candidateTokens.add(w)
                var j = i + 1
                while (j < words.size) {
                    val nextW = words[j].trim()
                    val cleanNext = nextW.removePrefix("و").removePrefix("ب")
                    if (numberWordTokens.contains(cleanNext)) {
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
            lower.contains("خبز") || lower.contains("تفاح") || lower.contains("لحمة") ||
            lower.contains("دجاج") || wordTokens.contains("رز") -> ExpenseCategory.GROCERIES

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
        // Pattern 0: "دفع عني [الاسم]" / "سدد عني [الاسم]" / "دفعها لي [الاسم]"
        val paidForMeRegex = Regex("""(?:دفع\s+عني|سدد\s+عني|دفعها\s+لي|سددها\s+لي)\s+([^\s,]+)""")
        paidForMeRegex.find(text)?.let {
            val name = it.groupValues[1].trim()
            if (name != "أنا") {
                return Payer(id = name, name = name, isCurrentUser = false)
            }
        }

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
            if (name != "أنا" && !name.startsWith("ت") && name != "عني" && name != "لي") { // avoid "دفعت"
                return Payer(id = name, name = name, isCurrentUser = false)
            }
        }

        return Payer(id = "self", name = "أنا", isCurrentUser = true)
    }

    private fun detectSplit(
        text: String,
        totalAmount: Double,
        currency: String,
        currentPayer: Payer,
        ts: Long,
        seq: Long
    ): Pair<ExpenseSplit?, Double?> {
        // Pattern A: "سددت عن [الاسم]" / "دفعت عن [الاسم]" (payer is self, beneficiary is other)
        val paidOnBehalfRegex = Regex("""(?:دفعت\s+عن|سددت\s+عن)\s+([^\s,]+)""")
        val onBehalfMatch = paidOnBehalfRegex.find(text)
        if (onBehalfMatch != null) {
            val beneficiary = onBehalfMatch.groupValues[1].trim()
            val party = Payer(id = beneficiary, name = beneficiary, isCurrentUser = false)
            val participants = listOf(party)
            val note = "سددت نيابة عن $beneficiary"
            val alloc = Allocation(
                id = "alloc_${ts}_${seq}_$beneficiary",
                party = party,
                amount = totalAmount,
                note = note
            )
            return Pair(
                ExpenseSplit(
                    totalAmount = totalAmount,
                    payer = currentPayer,
                    participants = participants,
                    amountPerPerson = totalAmount,
                    note = note,
                    allocations = listOf(alloc)
                ),
                0.0 // User paid on behalf of someone else; user's own share is 0
            )
        }

        // Pattern B: "دفع عني [الاسم]" / "سدد عني [الاسم]"
        val paidForMeRegex = Regex("""(?:دفع\s+عني|سدد\s+عني)\s+([^\s,]+)""")
        val forMeMatch = paidForMeRegex.find(text)
        if (forMeMatch != null) {
            val payerName = forMeMatch.groupValues[1].trim()
            val selfParty = Payer(id = "self", name = "أنا", isCurrentUser = true)
            val participants = listOf(selfParty)
            val note = "دفع $payerName عني"
            val alloc = Allocation(
                id = "alloc_${ts}_${seq}_self",
                party = selfParty,
                amount = totalAmount,
                note = note
            )
            return Pair(
                ExpenseSplit(
                    totalAmount = totalAmount,
                    payer = currentPayer,
                    participants = participants,
                    amountPerPerson = totalAmount,
                    note = note,
                    allocations = listOf(alloc)
                ),
                totalAmount // User owes payer the full amount
            )
        }

        // Pattern C: "بيني وبين [الاسم]"
        val betweenRegex = Regex("""بيني\s+وبين\s+([^\s,]+)""")
        val match = betweenRegex.find(text)
        if (match != null) {
            val otherPerson = match.groupValues[1].trim()
            val otherParty = Payer(otherPerson, otherPerson, false)
            val participants = listOf(
                Payer("self", "أنا", true),
                otherParty
            )
            val splitResult = Money.distributeSplitEvenly(totalAmount, currency, 2)
            val note = "قسمة بيني وبين $otherPerson"
            val alloc = Allocation(
                id = "alloc_${ts}_${seq}_$otherPerson",
                party = otherParty,
                amount = splitResult.sharePerOther,
                note = note
            )
            return Pair(
                ExpenseSplit(
                    totalAmount = totalAmount,
                    payer = currentPayer,
                    participants = participants,
                    amountPerPerson = splitResult.sharePerOther,
                    note = note,
                    allocations = listOf(alloc)
                ),
                splitResult.myShare
            )
        }

        if (text.contains("قسمة علينا") || text.contains("النص بالنص")) {
            val otherParty = Payer("other", "الشريك", false)
            val participants = listOf(
                Payer("self", "أنا", true),
                otherParty
            )
            val splitResult = Money.distributeSplitEvenly(totalAmount, currency, 2)
            val note = "قسمة بالنصف"
            val alloc = Allocation(
                id = "alloc_${ts}_${seq}_partner",
                party = otherParty,
                amount = splitResult.sharePerOther,
                note = note
            )
            return Pair(
                ExpenseSplit(
                    totalAmount = totalAmount,
                    payer = currentPayer,
                    participants = participants,
                    amountPerPerson = splitResult.sharePerOther,
                    note = note,
                    allocations = listOf(alloc)
                ),
                splitResult.myShare
            )
        }

        return Pair(null, null)
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
                    spokenText.contains("للبيت") || spokenText.contains("نصيبي") || spokenText.contains("إلي") -> "مصروف مشترك"
                    else -> "مصروف عام"
                }
            }
        }
    }
}
