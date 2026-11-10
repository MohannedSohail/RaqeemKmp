package org.mohtech.app.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class ExpenseCategory(
    val id: String,
    val arabicTitle: String,
    val colorHex: Long
) {
    GROCERIES("groceries", "بقالة وتموينات", 0xFF00C48C),
    DINING("dining", "مطاعم ومقاهي", 0xFFFFA000),
    TRANSPORT("transport", "مواصلات وطاقة", 0xFF22D3EE),
    BILLS("bills", "فواتير والتزامات", 0xFFFF5C5C),
    HEALTH("health", "صحة وصيدلية", 0xFFAB47BC),
    GENERAL("general", "أخرى ومصروفات عامة", 0xFF94A3B8)
}

@Serializable
data class Payer(
    val id: String,
    val name: String,
    val isCurrentUser: Boolean = true
)

@Serializable
data class ExpenseSplit(
    val totalAmount: Double,
    val payer: Payer,
    val participants: List<Payer>,
    val amountPerPerson: Double,
    val note: String = ""
)

@Serializable
enum class ProofType {
    NONE,
    BANK_SMS,
    RECEIPT_PHOTO,
    AUDIO_MEMO
}

@Serializable
data class BankSmsProof(
    val bankName: String,
    val rawText: String,
    val extractedAmount: Double,
    val currency: String,
    val merchant: String,
    val transactionTimestamp: Long
)

@Serializable
data class ExpenseItem(
    val id: String,
    val title: String,
    val amount: Double,
    val currency: String = "₪",
    val category: ExpenseCategory,
    val timestamp: Long,
    val payer: Payer = Payer(id = "self", name = "أنا", isCurrentUser = true),
    val split: ExpenseSplit? = null,
    val proofType: ProofType = ProofType.NONE,
    val proofDetails: BankSmsProof? = null,
    val rawSpokenText: String = ""
)

@Serializable
data class ExpenseBatch(
    val batchId: String,
    val items: List<ExpenseItem>,
    val totalBatchAmount: Double,
    val createdAt: Long
)

