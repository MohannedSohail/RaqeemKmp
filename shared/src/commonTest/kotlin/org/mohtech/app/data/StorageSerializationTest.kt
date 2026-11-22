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
import kotlin.test.assertTrue

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

    @Test
    fun testLegacyJsonCompatibilityWithoutNewFields() {
        // Raw JSON string strictly matching legacy v2 format (no myShare, receiptPhotoPath, refNo, deletedAt, allocations)
        val legacyJson = """
            {
                "id": "exp_legacy_101",
                "title": "فطور صباحي",
                "amount": 45.0,
                "currency": "₪",
                "category": "DINING",
                "timestamp": 1731168000000,
                "payer": {
                    "id": "self",
                    "name": "أنا",
                    "isCurrentUser": true
                },
                "split": {
                    "totalAmount": 45.0,
                    "payer": { "id": "self", "name": "أنا", "isCurrentUser": true },
                    "participants": [
                        { "id": "self", "name": "أنا", "isCurrentUser": true },
                        { "id": "ahmad", "name": "أحمد", "isCurrentUser": false }
                    ],
                    "amountPerPerson": 22.5,
                    "note": "فطور قديم"
                },
                "proofType": "NONE",
                "rawSpokenText": "فطور بخمسة وأربعين شيكل"
            }
        """.trimIndent()

        val decoded = json.decodeFromString<ExpenseItem>(legacyJson)

        assertEquals("exp_legacy_101", decoded.id)
        assertEquals(45.0, decoded.amount)
        assertEquals(45.0, decoded.effectiveAmount, "Legacy item without myShare must have effectiveAmount == amount")
        assertEquals(null, decoded.myShare)
        assertEquals(null, decoded.receiptPhotoPath)
        assertEquals(0L, decoded.refNo)
        assertEquals(null, decoded.deletedAt)
        assertNotNull(decoded.split)
        assertEquals(22.5, decoded.split.amountPerPerson)
        assertTrue(decoded.split.allocations.isEmpty(), "Legacy split must default to empty allocations")
    }

    @Test
    fun testExpenseWithAllocationsAndSettlementsSerializationRoundTrip() {
        val ahmad = Payer("user_ahmad", "أحمد", false)
        val settlement = org.mohtech.app.data.model.Settlement(
            id = "settle_01",
            refNo = 501L,
            allocationId = "alloc_01",
            amount = 25.0,
            settledAt = 1731168100000L,
            recordedAt = 1731168105000L,
            note = "دفعة نقدية",
            voidedAt = null
        )
        val allocation = org.mohtech.app.data.model.Allocation(
            id = "alloc_01",
            party = ahmad,
            amount = 50.0,
            refNo = 201L,
            settlements = listOf(settlement),
            note = "نصيب أحمد"
        )
        val item = ExpenseItem(
            id = "exp_v3_full",
            title = "عشاء عمل",
            amount = 120.0,
            currency = "₪",
            category = ExpenseCategory.DINING,
            timestamp = 1731168000000L,
            payer = Payer("self", "أنا", true),
            split = ExpenseSplit(
                totalAmount = 120.0,
                payer = Payer("self", "أنا", true),
                participants = listOf(Payer("self", "أنا", true), ahmad),
                amountPerPerson = 60.0,
                note = "تقسيم مخصص",
                allocations = listOf(allocation)
            ),
            myShare = 70.0,
            receiptPhotoPath = "/data/receipts/rec_01.jpg",
            refNo = 1001L,
            deletedAt = null
        )

        val serialized = json.encodeToString(item)
        val deserialized = json.decodeFromString<ExpenseItem>(serialized)

        assertEquals(item.id, deserialized.id)
        assertEquals(70.0, deserialized.myShare)
        assertEquals(70.0, deserialized.effectiveAmount)
        assertEquals(120.0, deserialized.amount)
        assertEquals("/data/receipts/rec_01.jpg", deserialized.receiptPhotoPath)
        assertEquals(1001L, deserialized.refNo)
        assertEquals(null, deserialized.deletedAt)

        val desSplit = deserialized.split
        assertNotNull(desSplit)
        assertEquals(1, desSplit.allocations.size)
        val desAlloc = desSplit.allocations.first()
        assertEquals("alloc_01", desAlloc.id)
        assertEquals(50.0, desAlloc.amount)
        assertEquals(1, desAlloc.settlements.size)
        assertEquals(25.0, desAlloc.settlements.first().amount)
        assertEquals("settle_01", desAlloc.settlements.first().id)
    }

    @Test
    fun testAuditRecordSerialization() {
        val audit = org.mohtech.app.data.model.AuditRecord(
            id = "audit_01",
            timestamp = 1731168000000L,
            entityType = "ExpenseItem",
            entityId = "exp_123",
            action = "SETTLE",
            details = "Recorded settlement of 25.0 ₪"
        )
        val serialized = json.encodeToString(audit)
        val deserialized = json.decodeFromString<org.mohtech.app.data.model.AuditRecord>(serialized)

        assertEquals(audit.id, deserialized.id)
        assertEquals(audit.action, deserialized.action)
        assertEquals(audit.details, deserialized.details)
    }
}
