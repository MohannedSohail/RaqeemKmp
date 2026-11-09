package org.mohtech.app.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.mohtech.app.data.model.BankSmsProof
import org.mohtech.app.data.model.ExpenseBatch
import org.mohtech.app.data.model.ExpenseCategory
import org.mohtech.app.data.model.ExpenseItem
import org.mohtech.app.data.model.ExpenseSplit
import org.mohtech.app.data.model.Payer
import org.mohtech.app.data.model.ProofType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class StorageSerializationTest {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    @Test
    fun testExpenseItemSerializationRoundTrip() {
        val item = ExpenseItem(
            id = "exp-123",
            title = "بنزين",
            amount = 50.0,
            currency = "₪",
            category = ExpenseCategory.TRANSPORT,
            timestamp = 1731168000000L,
            payer = Payer("user-1", "طارق", true),
            rawSpokenText = "بنزين ب 50 شيكل"
        )

        val serialized = json.encodeToString(item)
        val deserialized = json.decodeFromString<ExpenseItem>(serialized)

        assertEquals(item.id, deserialized.id)
        assertEquals(item.title, deserialized.title)
        assertEquals(item.amount, deserialized.amount)
        assertEquals(item.currency, deserialized.currency)
        assertEquals(item.category, deserialized.category)
        assertEquals(item.payer.name, deserialized.payer.name)
    }

    @Test
    fun testSharedExpenseWithSplitSerialization() {
        val payerAhmad = Payer("user-2", "أحمد", false)
        val payerSelf = Payer("user-1", "أنا", true)

        val split = ExpenseSplit(
            totalAmount = 60.0,
            payer = payerAhmad,
            participants = listOf(payerSelf, payerAhmad),
            amountPerPerson = 30.0,
            note = "قسمة عشاء"
        )

        val item = ExpenseItem(
            id = "exp-split-456",
            title = "عشاء شاورما",
            amount = 60.0,
            currency = "₪",
            category = ExpenseCategory.DINING,
            timestamp = 1731168000000L,
            payer = payerAhmad,
            split = split,
            rawSpokenText = "عشاء بستين شيكل بيني وبين أحمد على حساب أحمد"
        )

        val batch = ExpenseBatch(
            batchId = "batch-1",
            items = listOf(item),
            totalBatchAmount = 60.0,
            createdAt = 1731168000000L
        )

        val serialized = json.encodeToString(batch)
        val deserialized = json.decodeFromString<ExpenseBatch>(serialized)

        assertEquals(1, deserialized.items.size)
        val deserializedItem = deserialized.items.first()
        val splitDetails = deserializedItem.split
        assertNotNull(splitDetails)
        assertEquals(30.0, splitDetails.amountPerPerson)
        assertEquals("أحمد", splitDetails.payer.name)
        assertEquals(2, splitDetails.participants.size)
    }

    @Test
    fun testBankSmsProofSerialization() {
        val proof = BankSmsProof(
            bankName = "بنك فلسطين",
            rawText = "حركات بطاقة: تم خصم 150.00 ILS في كارفور",
            extractedAmount = 150.0,
            currency = "ILS",
            merchant = "كارفور",
            transactionTimestamp = 1731168000000L
        )

        val item = ExpenseItem(
            id = "exp-sms-789",
            title = "كارفور",
            amount = 150.0,
            currency = "ILS",
            category = ExpenseCategory.GROCERIES,
            timestamp = 1731168000000L,
            proofType = ProofType.BANK_SMS,
            proofDetails = proof
        )

        val serialized = json.encodeToString(item)
        val deserialized = json.decodeFromString<ExpenseItem>(serialized)

        assertEquals(ProofType.BANK_SMS, deserialized.proofType)
        val proofDetails = deserialized.proofDetails
        assertNotNull(proofDetails)
        assertEquals("كارفور", proofDetails.merchant)
        assertEquals(150.0, proofDetails.extractedAmount)
    }
}
