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
}
