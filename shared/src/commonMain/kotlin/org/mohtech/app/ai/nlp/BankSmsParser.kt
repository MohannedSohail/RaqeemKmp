package org.mohtech.app.ai.nlp

import org.mohtech.app.data.model.BankSmsProof

/**
 * BankSmsParser:
 * Offline deterministic parser for incoming bank transaction SMS notifications.
 */
object BankSmsParser {

    private val amountRegex = Regex(
        """(?:بمبلغ|خصم|شراء|مبلغ|سحب)\s*([0-9]+(?:\.[0-9]+)?)\s*([A-Za-z]{3}|شيكل|ريال|جنيه)?""",
        RegexOption.IGNORE_CASE
    )

    private val fallbackAmountRegex = Regex(
        """([0-9]+(?:\.[0-9]+)?)\s*(ILS|SAR|EGP|USD|EUR|شيكل|ريال|جنيه)""",
        RegexOption.IGNORE_CASE
    )

    private val merchantRegex = Regex(
        """(?:في|لدى|عند)\s+([^,.\n\r]+?)(?:\s*(?:بتاريخ|\.|في\s+\d|\s+الساعة|$))""",
        RegexOption.IGNORE_CASE
    )

    fun parse(smsBody: String, timestamp: Long): BankSmsProof? {
        val cleanBody = ArabicNumberNormalizer.convertIndicDigitsToAscii(smsBody.trim())

        // 1. Extract Amount & Currency
        var extractedAmount: Double? = null
        var extractedCurrency = "ILS"

        val match = amountRegex.find(cleanBody) ?: fallbackAmountRegex.find(cleanBody)
        if (match != null) {
            extractedAmount = match.groupValues[1].toDoubleOrNull()
            if (match.groupValues.size > 2 && match.groupValues[2].isNotBlank()) {
                extractedCurrency = normalizeCurrency(match.groupValues[2])
            }
        }

        if (extractedAmount == null) return null

        // 2. Identify Bank Name
        val bankName = when {
            cleanBody.contains("بنك فلسطين") -> "بنك فلسطين"
            cleanBody.contains("الراجحي") || cleanBody.contains("مدى") -> "مصرف الراجحي"
            cleanBody.contains("CIB") || cleanBody.contains("التجاري الدولي") -> "CIB"
            cleanBody.contains("الأهلي") || cleanBody.contains("SNB") -> "البنك الأهلي SNB"
            else -> "إشعار مصرفي"
        }

        // 3. Extract Merchant
        val merchantMatch = merchantRegex.find(cleanBody)
        val merchant = merchantMatch?.groupValues?.get(1)?.trim() ?: bankName

        return BankSmsProof(
            bankName = bankName,
            rawText = smsBody,
            extractedAmount = extractedAmount,
            currency = extractedCurrency,
            merchant = merchant,
            transactionTimestamp = timestamp
        )
    }

    private fun normalizeCurrency(raw: String): String {
        val upper = raw.trim().uppercase()
        return when {
            upper.contains("ILS") || upper.contains("شيكل") -> "ILS"
            upper.contains("SAR") || upper.contains("ريال") -> "SAR"
            upper.contains("EGP") || upper.contains("جنيه") -> "EGP"
            upper.contains("USD") || upper.contains("دولار") -> "USD"
            else -> upper
        }
    }
}
