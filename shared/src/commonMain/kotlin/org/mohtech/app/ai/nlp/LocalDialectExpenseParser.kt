package org.mohtech.app.ai.nlp

import org.mohtech.app.data.model.BankSmsProof
import org.mohtech.app.data.model.ExpenseBatch

/**
 * LocalDialectExpenseParser:
 * Rule-based offline Arabic computational linguistics engine for colloquial dialects.
 */
class LocalDialectExpenseParser {

    /**
     * Parse colloquial Arabic spoken text into a structured ExpenseBatch.
     */
    fun parseSpokenText(
        spokenText: String,
        defaultCurrency: String = "₪",
        currentTimestamp: Long = 0L
    ): ExpenseBatch {
        throw NotImplementedError("LocalDialectExpenseParser is not yet implemented. TDD phase active.")
    }

    /**
     * Parse incoming bank SMS notifications into a structured BankSmsProof.
     */
    fun parseBankSms(
        smsBody: String,
        currentTimestamp: Long = 0L
    ): BankSmsProof? {
        throw NotImplementedError("BankSmsParser is not yet implemented. TDD phase active.")
    }
}
