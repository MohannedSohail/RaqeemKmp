package org.mohtech.app.ai.nlp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TranscriptNormalizerTest {

    data class NormalizerTestCase(
        val name: String,
        val input: String,
        val expectedOutput: String,
        val expectedReplacementCount: Int? = null
    )

    @Test
    fun testTranscriptNormalizerTable() {
        val testCases = listOf(
            // 1. Positive: "شكل" adjacent to digit -> "شيكل"
            NormalizerTestCase(
                name = "Contextual currency: ب 50 شكل -> ب 50 شيكل",
                input = "بنزين ب 50 شكل",
                expectedOutput = "بنزين ب 50 شيكل"
            ),
            // 2. Positive: "شكل" adjacent to number word -> "شيكل"
            NormalizerTestCase(
                name = "Contextual currency: خمسين شكل -> خمسين شيكل",
                input = "دفعت خمسين شكل في المطعم",
                expectedOutput = "دفعت خمسين شيكل في المطعم"
            ),
            // 3. Negative: "شكل" in general text -> NOT modified
            NormalizerTestCase(
                name = "Negative: شكل الفاتورة غريب -> preserved",
                input = "شكل الفاتورة غريب",
                expectedOutput = "شكل الفاتورة غريب"
            ),
            // 4. Negative: "فاتورة مياه" -> NOT modified to "مية"
            NormalizerTestCase(
                name = "Negative: فاتورة مياه -> preserved as water bill",
                input = "فاتورة مياه بمية وخمسين",
                expectedOutput = "فاتورة مياه بمية وخمسين"
            ),
            // 5. Positive: "ميه" in number context -> "مية"
            NormalizerTestCase(
                name = "Number phrase: ميه وخمسين -> مية وخمسين",
                input = "دفعت ميه وخمسين جنيه",
                expectedOutput = "دفعت مية وخمسين جنيه"
            ),
            // 6. Negative: Person names after "على حساب" -> preserved
            NormalizerTestCase(
                name = "Preserve name after على حساب: على حساب طارق",
                input = "قهوة على حساب طارق",
                expectedOutput = "قهوة على حساب طارق"
            ),
            // 7. Negative: Person names after "بيني وبين" -> preserved
            NormalizerTestCase(
                name = "Preserve name after بيني وبين: بيني وبين شكري",
                input = "عشاء بيني وبين شكري",
                expectedOutput = "عشاء بيني وبين شكري"
            ),
            // 8. Negative: Person names after "دفعها" -> preserved
            NormalizerTestCase(
                name = "Preserve name after دفعها: دفعها شادي",
                input = "حساب ب 80 دفعها شادي",
                expectedOutput = "حساب ب 80 دفعها شادي"
            ),
            // 9. Negative: Person names after "دفعت عن" -> preserved
            NormalizerTestCase(
                name = "Preserve name after دفعت عن: دفعت عن حسام",
                input = "دفعت عن حسام خمسين ريال",
                expectedOutput = "دفعت عن حسام خمسين ريال"
            ),
            // 10. Phonetic distance 1: "صيدليه" -> "صيدلية"
            NormalizerTestCase(
                name = "Phonetic correction: صيدليه -> صيدلية",
                input = "دواء من صيدليه ب 40",
                expectedOutput = "دواء من صيدلية ب 40"
            ),
            // 11. Phonetic distance 1: "سوبرماركت" variant
            NormalizerTestCase(
                name = "Phonetic category: سوبرماركت",
                input = "مشتريات سوبرماركت بمية",
                expectedOutput = "مشتريات سوبرماركت بمية"
            ),
            // 12. Levantine dialect with word number
            NormalizerTestCase(
                name = "Levantine: قهوة بعشرة شيكل",
                input = "قهوة بعشرة شيكل",
                expectedOutput = "قهوة بعشرة شيكل"
            ),
            // 13. Egyptian dialect with compound number
            NormalizerTestCase(
                name = "Egyptian: ميتين وخمسين جنيه",
                input = "دفعت ميتين وخمسين جنيه",
                expectedOutput = "دفعت ميتين وخمسين جنيه"
            ),
            // 14. Gulf dialect with SAR
            NormalizerTestCase(
                name = "Gulf: بنزين بخمسين ريال",
                input = "بنزين بخمسين ريال",
                expectedOutput = "بنزين بخمسين ريال"
            ),
            // 15. Thousand multiplication: ثلاث آلاف
            NormalizerTestCase(
                name = "Thousand: ثلاث آلاف شيكل",
                input = "إيجار ثلاث آلاف شيكل",
                expectedOutput = "إيجار ثلاث آلاف شيكل"
            ),
            // 16. Fraction phrase: ألف ونص
            NormalizerTestCase(
                name = "Fraction: ألف ونص شيكل",
                input = "قسط ألف ونص شيكل",
                expectedOutput = "قسط ألف ونص شيكل"
            ),
            // 17. Fraction phrase: مية وربع
            NormalizerTestCase(
                name = "Fraction: مية وربع ريال",
                input = "فاتورة مية وربع ريال",
                expectedOutput = "فاتورة مية وربع ريال"
            ),
            // 18. Ambiguous word not touched
            NormalizerTestCase(
                name = "Ambiguous word untouched: كتاب جديد",
                input = "اشتريت كتاب جديد",
                expectedOutput = "اشتريت كتاب جديد"
            ),
            // 19. Whisper typo fixes for colloquial dialect terms
            NormalizerTestCase(
                name = "Whisper typo: بنزن ب 50 شايكل -> بنزين ب 50 شيكل",
                input = "بنزن ب 50 شايكل",
                expectedOutput = "بنزين ب 50 شيكل"
            ),
            // 20. Whisper typo fixes for dining & market
            NormalizerTestCase(
                name = "Whisper typo: كبوتشينو في سبرماركت -> كابتشينو في سوبرماركت",
                input = "كبوتشينو في سبرماركت",
                expectedOutput = "كابتشينو في سوبرماركت"
            )
        )

        for (tc in testCases) {
            val result = TranscriptNormalizer.normalize(tc.input)
            assertEquals(
                tc.expectedOutput,
                result.normalizedText,
                "Failed test case: ${tc.name}"
            )
        }
    }
}

