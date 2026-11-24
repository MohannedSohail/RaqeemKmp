package org.mohtech.app.ai.nlp

import org.mohtech.app.data.model.ExpenseCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DialectParserTest {

    private val parser = LocalDialectExpenseParser()
    private val fixedTimestamp = 1731168000000L

    // 1. Single item with Latin digits (Palestinian/Levantine)
    @Test
    fun testSingleItemWithLatinDigits() {
        val result = parser.parseSpokenText("بنزين ب 50 شيكل", defaultCurrency = "₪", currentTimestamp = fixedTimestamp)
        assertEquals(1, result.items.size)
        val item = result.items.first()
        assertEquals(50.0, item.amount)
        assertEquals("₪", item.currency)
        assertEquals(ExpenseCategory.TRANSPORT, item.category)
        assertTrue(item.payer.isCurrentUser)
    }

    // 2. Single item with Arabic number word
    @Test
    fun testSingleItemWithArabicWordAmount() {
        val result = parser.parseSpokenText("قهوة بعشرة شيكل", defaultCurrency = "₪", currentTimestamp = fixedTimestamp)
        assertEquals(1, result.items.size)
        val item = result.items.first()
        assertEquals(10.0, item.amount)
        assertEquals(ExpenseCategory.DINING, item.category)
    }

    // 3. Egyptian dialect with digits and Egyptian Pound
    @Test
    fun testEgyptianDialectWithDigits() {
        val result = parser.parseSpokenText("اشتريت دواء من الصيدلية ب 120 جنيه", defaultCurrency = "ج.م", currentTimestamp = fixedTimestamp)
        assertEquals(1, result.items.size)
        val item = result.items.first()
        assertEquals(120.0, item.amount)
        assertEquals("ج.م", item.currency)
        assertEquals(ExpenseCategory.HEALTH, item.category)
    }

    // 4. Egyptian dialect with compound word amount ("مية وخمسين")
    @Test
    fun testEgyptianCompoundWordAmount() {
        val result = parser.parseSpokenText("دفعت مية وخمسين جنيه في السوبرماركت", defaultCurrency = "ج.م", currentTimestamp = fixedTimestamp)
        val item = result.items.first()
        assertEquals(150.0, item.amount)
        assertEquals(ExpenseCategory.GROCERIES, item.category)
    }

    // 5. Gulf dialect with Arabic-Indic numerals (١٢٠ ريال)
    @Test
    fun testGulfDialectWithArabicIndicNumerals() {
        val result = parser.parseSpokenText("سوبرماركت ١٢٠ ريال حليب وبيض", defaultCurrency = "ر.س", currentTimestamp = fixedTimestamp)
        val item = result.items.first()
        assertEquals(120.0, item.amount)
        assertEquals("ر.س", item.currency)
        assertEquals(ExpenseCategory.GROCERIES, item.category)
    }

    // 6. Gulf dialect with word amount ("خمسمية ريال")
    @Test
    fun testGulfDialectWithWordAmount() {
        val result = parser.parseSpokenText("فاتورة الكهرباء خمسمية ريال", defaultCurrency = "ر.س", currentTimestamp = fixedTimestamp)
        val item = result.items.first()
        assertEquals(500.0, item.amount)
        assertEquals(ExpenseCategory.BILLS, item.category)
    }

    // 7. Fractional amount in item name ("نص كيلو جبنة ب 15 شيكل")
    @Test
    fun testFractionalQuantityItemName() {
        val result = parser.parseSpokenText("نص كيلو جبنة ب 15 شيكل", defaultCurrency = "₪", currentTimestamp = fixedTimestamp)
        val item = result.items.first()
        assertEquals(15.0, item.amount)
        assertEquals(ExpenseCategory.GROCERIES, item.category)
    }

    // 8. Shared expense split ("بيني وبين أحمد")
    @Test
    fun testSharedExpenseSplitBetweenTwo() {
        val result = parser.parseSpokenText("عشاء بستين شيكل بيني وبين أحمد", defaultCurrency = "₪", currentTimestamp = fixedTimestamp)
        val item = result.items.first()
        assertEquals(60.0, item.amount)
        assertEquals(ExpenseCategory.DINING, item.category)
        val split = item.split
        assertNotNull(split)
        assertEquals(30.0, split.amountPerPerson)
        assertEquals(2, split.participants.size)
    }

    // 9. Shared bill with explicit external payer ("دفعها محمد")
    @Test
    fun testSharedBillWithExplicitExternalPayer() {
        val result = parser.parseSpokenText("غداء ب 80 شيكل دفعها محمد", defaultCurrency = "₪", currentTimestamp = fixedTimestamp)
        val item = result.items.first()
        assertEquals(80.0, item.amount)
        assertEquals(ExpenseCategory.DINING, item.category)
        assertEquals("محمد", item.payer.name)
        assertTrue(!item.payer.isCurrentUser)
    }

    // 10. Explicit payer phrasing ("على حساب خالد")
    @Test
    fun testExplicitPayerOnAccountOf() {
        val result = parser.parseSpokenText("قهوة وحلا بثلاثين ريال على حساب خالد", defaultCurrency = "ر.س", currentTimestamp = fixedTimestamp)
        val item = result.items.first()
        assertEquals(30.0, item.amount)
        assertEquals("خالد", item.payer.name)
        assertTrue(!item.payer.isCurrentUser)
    }

    // 11. Large compound number word ("ألفين وميتين")
    @Test
    fun testLargeCompoundNumberWords() {
        val result = parser.parseSpokenText("إيجار البيت ألفين وميتين شيكل", defaultCurrency = "₪", currentTimestamp = fixedTimestamp)
        val item = result.items.first()
        assertEquals(2200.0, item.amount)
        assertEquals(ExpenseCategory.BILLS, item.category)
    }

    // 12. Multi-word item name
    @Test
    fun testMultiWordItemName() {
        val result = parser.parseSpokenText("تصليح كوشوك السيارة ب 70 شيكل", defaultCurrency = "₪", currentTimestamp = fixedTimestamp)
        val item = result.items.first()
        assertEquals(70.0, item.amount)
        assertEquals(ExpenseCategory.TRANSPORT, item.category)
    }

    // 13. Quarter fraction in name
    @Test
    fun testQuarterFractionInName() {
        val result = parser.parseSpokenText("ربع كيلو بن ب 25 شيكل", defaultCurrency = "₪", currentTimestamp = fixedTimestamp)
        val item = result.items.first()
        assertEquals(25.0, item.amount)
        assertEquals(ExpenseCategory.DINING, item.category)
    }

    // 14. Bank SMS - Bank of Palestine
    @Test
    fun testBankSmsBankOfPalestine() {
        val sms = "حركات بطاقة: تم شراء بمبلغ 150.00 ILS في سوبرماركت برافو"
        val proof = parser.parseBankSms(sms, fixedTimestamp)
        assertNotNull(proof)
        assertEquals(150.0, proof.extractedAmount)
        assertEquals("ILS", proof.currency)
        assertTrue(proof.merchant.contains("برافو"))
    }

    // 15. Bank SMS - Al Rajhi Bank (Saudi Arabia)
    @Test
    fun testBankSmsAlRajhi() {
        val sms = "شراء عبر مدى بمبلغ 85.50 SAR لدى صيدلية النهدي"
        val proof = parser.parseBankSms(sms, fixedTimestamp)
        assertNotNull(proof)
        assertEquals(85.50, proof.extractedAmount)
        assertEquals("SAR", proof.currency)
        assertTrue(proof.merchant.contains("النهدي"))
    }

    // 16. Fallback to GENERAL category
    @Test
    fun testGeneralFallbackCategory() {
        val result = parser.parseSpokenText("دفعت عشرين شيكل هدية", defaultCurrency = "₪", currentTimestamp = fixedTimestamp)
        val item = result.items.first()
        assertEquals(20.0, item.amount)
        assertEquals(ExpenseCategory.GENERAL, item.category)
    }

    // 17. Multiplied thousands: "ثلاثة آلاف شيكل"
    @Test
    fun testMultipliedThousandsArabicNumber() {
        val result = parser.parseSpokenText("إيجار ثلاثة آلاف شيكل", defaultCurrency = "₪", currentTimestamp = fixedTimestamp)
        val item = result.items.first()
        assertEquals(3000.0, item.amount)
        assertEquals(ExpenseCategory.BILLS, item.category)
    }

    // 18. Thousand and fraction: "ألف ونص شيكل"
    @Test
    fun testThousandAndHalfFraction() {
        val result = parser.parseSpokenText("قسط ألف ونص شيكل", defaultCurrency = "₪", currentTimestamp = fixedTimestamp)
        val item = result.items.first()
        assertEquals(1500.0, item.amount)
        assertEquals(ExpenseCategory.BILLS, item.category)
    }

    // 19. Hundred and quarter fraction: "مية وربع ريال"
    @Test
    fun testHundredAndQuarterFraction() {
        val result = parser.parseSpokenText("فاتورة مية وربع ريال", defaultCurrency = "ر.س", currentTimestamp = fixedTimestamp)
        val item = result.items.first()
        assertEquals(125.0, item.amount)
        assertEquals(ExpenseCategory.BILLS, item.category)
    }

    // 20. Explicit on-behalf: "سددت عن أحمد خمسين شيكل"
    @Test
    fun testPaidOnBehalfOfOther() {
        val result = parser.parseSpokenText("سددت عن أحمد خمسين شيكل غداء", defaultCurrency = "₪", currentTimestamp = fixedTimestamp)
        val item = result.items.first()
        assertEquals(50.0, item.amount)
        assertTrue(item.payer.isCurrentUser)
        assertNotNull(item.split)
        assertEquals("أحمد", item.split?.participants?.firstOrNull()?.name)
        assertEquals(50.0, item.split?.amountPerPerson)
    }

    // 21. Explicit paid for me: "دفع عني محمد ثمانين شيكل"
    @Test
    fun testPaidForMeByOther() {
        val result = parser.parseSpokenText("دفع عني محمد ثمانين شيكل عشاء", defaultCurrency = "₪", currentTimestamp = fixedTimestamp)
        val item = result.items.first()
        assertEquals(80.0, item.amount)
        assertEquals("محمد", item.payer.name)
        assertEquals(false, item.payer.isCurrentUser)
        assertNotNull(item.split)
        assertTrue(item.split?.participants?.firstOrNull()?.isCurrentUser == true)
    }

    // 22. Normalized contextual currency: "بنزين ب 50 شكل"
    @Test
    fun testNormalizedContextualCurrency() {
        val normalized = TranscriptNormalizer.normalize("بنزين ب 50 شكل")
        val result = parser.parseSpokenText(normalized.normalizedText, defaultCurrency = "₪", currentTimestamp = fixedTimestamp)
        val item = result.items.first()
        assertEquals(50.0, item.amount)
        assertEquals("₪", item.currency)
        assertEquals(ExpenseCategory.TRANSPORT, item.category)
    }

    // 23. Compound number with decimal fraction: "مية وخمسين ونص" -> 150.5
    @Test
    fun testCompoundNumberWithDecimalFraction() {
        val parsed = ArabicNumberNormalizer.parseArabicNumberWords("مية وخمسين ونص")
        assertEquals(150.5, parsed)
    }

    // 24. Units and tens with decimal fraction: "خمسة وثلاثين ونص" -> 35.5
    @Test
    fun testUnitsAndTensWithDecimalFraction() {
        val parsed = ArabicNumberNormalizer.parseArabicNumberWords("خمسة وثلاثين ونص")
        assertEquals(35.5, parsed)
    }

    // 25. ID uniqueness and valid prefix
    @Test
    fun testParseSpokenTextGeneratesUniqueIds() {
        val result1 = parser.parseSpokenText("قهوة بعشرة شيكل")
        val result2 = parser.parseSpokenText("قهوة بعشرة شيكل")
        assertEquals(1, result1.items.size)
        assertEquals(1, result2.items.size)
        val id1 = result1.items.first().id
        val id2 = result2.items.first().id
        assertTrue(id1.startsWith("exp_"), "Item ID must start with exp_")
        assertTrue(id2.startsWith("exp_"), "Item ID must start with exp_")
        assertTrue(id1 != id2, "Rapidly parsed items must have unique IDs")
    }

    // 26. Multi-item parsing: "دفعت 30 بنزين و15 قهوة و100 سوبرماركت"
    @Test
    fun testMultiItemAmountAnchoredParsing() {
        val result = parser.parseSpokenText(
            "دفعت 30 بنزين و15 قهوة و100 سوبرماركت",
            defaultCurrency = "₪",
            currentTimestamp = fixedTimestamp
        )
        assertEquals(3, result.items.size, "Batch must contain exactly 3 items")
        assertEquals(145.0, result.totalBatchAmount)

        val item1 = result.items[0]
        assertEquals(30.0, item1.amount)
        assertEquals("بنزين", item1.title)
        assertEquals(ExpenseCategory.TRANSPORT, item1.category)

        val item2 = result.items[1]
        assertEquals(15.0, item2.amount)
        assertEquals("قهوة", item2.title)
        assertEquals(ExpenseCategory.DINING, item2.category)

        val item3 = result.items[2]
        assertEquals(100.0, item3.amount)
        assertEquals("سوبرماركت", item3.title)
        assertEquals(ExpenseCategory.GROCERIES, item3.category)
    }

    // 27. Multi-party allocation: "مية شيكل ثلاثين إلي وأربعين للبيت وثلاثين لأخوي"
    @Test
    fun testMultiPartyAllocationExtraction() {
        val result = parser.parseSpokenText(
            "مية شيكل ثلاثين إلي وأربعين للبيت وثلاثين لأخوي",
            defaultCurrency = "₪",
            currentTimestamp = fixedTimestamp
        )
        assertEquals(1, result.items.size)
        val item = result.items.first()
        assertEquals(100.0, item.amount)
        assertEquals(30.0, item.myShare)

        val split = item.split
        assertNotNull(split)
        assertEquals(2, split.allocations.size)

        val allocHome = split.allocations.find { it.party.name == "البيت" }
        assertNotNull(allocHome)
        assertEquals(40.0, allocHome.amount)

        val allocBrother = split.allocations.find { it.party.name == "أخوي" }
        assertNotNull(allocBrother)
        assertEquals(30.0, allocBrother.amount)

        // Strict accounting identity: myShare + sum(allocations) == totalAmount
        val allocSum = split.allocations.sumOf { it.amount }
        assertEquals(100.0, (item.myShare ?: 0.0) + allocSum)
    }

    // 28. Remainder allocation: "مية شيكل نصيبي ثلاثين والباقي على محمد"
    @Test
    fun testRemainderAllocationExtraction() {
        val result = parser.parseSpokenText(
            "مية شيكل نصيبي ثلاثين والباقي على محمد",
            defaultCurrency = "₪",
            currentTimestamp = fixedTimestamp
        )
        assertEquals(1, result.items.size)
        val item = result.items.first()
        assertEquals(100.0, item.amount)
        assertEquals(30.0, item.myShare)

        val split = item.split
        assertNotNull(split)
        assertEquals(1, split.allocations.size)

        val allocMohammed = split.allocations.first()
        assertEquals("محمد", allocMohammed.party.name)
        assertEquals(70.0, allocMohammed.amount)
        assertEquals(100.0, (item.myShare ?: 0.0) + allocMohammed.amount)
    }

    // 29. Multi-item with "ب" notation: "بنزين ب 40 وقهوة ب 12"
    @Test
    fun testMultiItemWithPrepositionNotation() {
        val result = parser.parseSpokenText(
            "بنزين ب 40 وقهوة ب 12",
            defaultCurrency = "₪",
            currentTimestamp = fixedTimestamp
        )
        assertEquals(2, result.items.size)
        assertEquals(52.0, result.totalBatchAmount)

        assertEquals(40.0, result.items[0].amount)
        assertEquals(ExpenseCategory.TRANSPORT, result.items[0].category)

        assertEquals(12.0, result.items[1].amount)
        assertEquals(ExpenseCategory.DINING, result.items[1].category)
    }

    // 30. Quantity rejection with digit: "2 كيلو تفاح ب 20 شيكل"
    @Test
    fun testQuantityRejectionWithDigit() {
        val result = parser.parseSpokenText(
            "2 كيلو تفاح ب 20 شيكل",
            defaultCurrency = "₪",
            currentTimestamp = fixedTimestamp
        )
        val item = result.items.first()
        assertEquals(20.0, item.amount)
        assertEquals(ExpenseCategory.GROCERIES, item.category)
        assertTrue(item.title.contains("تفاح"), "Dynamic title must retain item name 'تفاح'")
    }

    // 31. Dynamic Title Extraction for novel item/merchant
    @Test
    fun testDynamicTitleExtractionForNovelItem() {
        val result = parser.parseSpokenText(
            "اشتريت كتب ب 50 شيكل",
            defaultCurrency = "₪",
            currentTimestamp = fixedTimestamp
        )
        val item = result.items.first()
        assertEquals(50.0, item.amount)
        assertEquals("كتب", item.title, "Title should be dynamically derived as 'كتب'")
    }

    // 32. Multi-item phrase with word amounts
    @Test
    fun testMultiItemPhraseWithWordAmounts() {
        val result = parser.parseSpokenText(
            "بنزين بأربعين وقهوة باثني عشر",
            defaultCurrency = "₪",
            currentTimestamp = fixedTimestamp
        )
        assertEquals(2, result.items.size)
        assertEquals(52.0, result.totalBatchAmount)
        assertEquals(40.0, result.items[0].amount)
        assertEquals(ExpenseCategory.TRANSPORT, result.items[0].category)
        assertEquals(12.0, result.items[1].amount)
        assertEquals(ExpenseCategory.DINING, result.items[1].category)
    }
}
