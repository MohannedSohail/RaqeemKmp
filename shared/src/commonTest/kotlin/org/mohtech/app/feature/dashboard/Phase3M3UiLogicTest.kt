package org.mohtech.app.feature.dashboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.mohtech.app.ai.nlp.LocalDialectExpenseParser
import org.mohtech.app.data.accounting.ExpenseSplitSplitter
import org.mohtech.app.data.accounting.LedgerIntegrityChecker
import org.mohtech.app.data.accounting.ReceivablesLedger
import org.mohtech.app.data.model.Allocation
import org.mohtech.app.data.model.BankSmsProof
import org.mohtech.app.data.model.ExpenseBatch
import org.mohtech.app.data.model.ExpenseCategory
import org.mohtech.app.data.model.ExpenseItem
import org.mohtech.app.data.model.ExpenseRecordType
import org.mohtech.app.data.model.ExpenseSplit
import org.mohtech.app.data.model.Payer
import org.mohtech.app.data.model.ProofType
import org.mohtech.app.data.model.Settlement
import org.mohtech.app.feature.dashboard.ui.AllocationDraft
import org.mohtech.app.feature.dashboard.ui.ConfirmedDraftItem
import org.mohtech.app.feature.dashboard.ui.ExpenseItemDraft
import org.mohtech.app.feature.dashboard.ui.ReceiptSessionTracker
import org.mohtech.app.feature.dashboard.ui.applyBillTotalInput
import org.mohtech.app.feature.dashboard.ui.applyPartyShareInput
import org.mohtech.app.feature.dashboard.ui.buildConfirmedBatch
import org.mohtech.app.feature.dashboard.ui.buildReceiptPickKey
import org.mohtech.app.feature.dashboard.ui.buildSplitBarSegments
import org.mohtech.app.feature.dashboard.ui.formatDraftAmount
import org.mohtech.app.feature.dashboard.ui.isEditingSavedBatch
import org.mohtech.app.feature.dashboard.ui.parseLocalizedAmount
import org.mohtech.app.feature.dashboard.ui.receivables.SelectedPartyTarget
import org.mohtech.app.feature.dashboard.ui.receivables.computePartyRemainingForTarget
import org.mohtech.app.feature.dashboard.ui.receivables.filterComputationsForParty
import org.mohtech.app.feature.dashboard.ui.receivables.filterOpenSettleAllComputations
import org.mohtech.app.feature.dashboard.ui.shouldShowRawTextSection
import org.mohtech.app.feature.dashboard.ui.shouldSyncBatchReceipt
import org.mohtech.app.feature.dashboard.ui.structuralItemsSignature
import org.mohtech.app.feature.dashboard.ui.toExpenseItemDraft

class Phase3M3UiLogicTest {

    // ── R1 / F2: Editable Raw Spoken Text & Batch Edit Guard ────────────────

    @Test
    fun testShouldShowRawTextSectionStaysVisibleWhenClearedDuringEditing() {
        assertTrue(
            shouldShowRawTextSection(
                hasInitialRawText = true,
                currentRawText = "",
                isEditing = true
            )
        )
        assertTrue(
            shouldShowRawTextSection(
                hasInitialRawText = true,
                currentRawText = "",
                isEditing = false
            )
        )
        assertTrue(
            shouldShowRawTextSection(
                hasInitialRawText = false,
                currentRawText = "",
                isEditing = true
            )
        )
        assertFalse(
            shouldShowRawTextSection(
                hasInitialRawText = false,
                currentRawText = "",
                isEditing = false
            )
        )
    }

    @Test
    fun testStructuralItemsSignatureIgnoresRawTextOnlyEdits() {
        val item1 = ExpenseItem(
            id = "exp_1",
            title = "قهوة",
            amount = 20.0,
            category = ExpenseCategory.DINING,
            timestamp = 1000L,
            rawSpokenText = "قهوة بعشرين"
        )
        val item1EditedRaw = item1.copy(rawSpokenText = "قهوة وكعكة بعشرين")
        val item1Reparsed = item1.copy(id = "exp_2", title = "قهوة وكعكة", amount = 25.0)

        assertEquals(
            structuralItemsSignature(listOf(item1)),
            structuralItemsSignature(listOf(item1EditedRaw))
        )
        assertNotEquals(
            structuralItemsSignature(listOf(item1)),
            structuralItemsSignature(listOf(item1Reparsed))
        )
    }

    @Test
    fun testBatchEditGuardDetectsSavedEditBatchesOnly() {
        assertTrue(isEditingSavedBatch("batch_edit_exp_123"))
        assertFalse(isEditingSavedBatch("batch_voice_123"))
        assertFalse(isEditingSavedBatch("batch_draft_123"))
    }

    @Test
    fun testBuildConfirmedBatchAppliesEditedRawTextWithoutReparsingFields() {
        val originalItem = ExpenseItem(
            id = "exp_1",
            title = "قهوة",
            amount = 20.0,
            currency = "₪",
            category = ExpenseCategory.DINING,
            timestamp = 1_000L,
            rawSpokenText = "قهوة بعشرين"
        )
        val batch = ExpenseBatch(
            batchId = "batch_1",
            items = listOf(originalItem),
            totalBatchAmount = 20.0,
            createdAt = 1_000L
        )
        val draft = ConfirmedDraftItem(
            originalId = "exp_1",
            currency = "₪",
            title = "قهوة مختصة",
            amountStr = "20",
            category = ExpenseCategory.DINING,
            payer = Payer("self", "أنا", true)
        )

        val confirmed = buildConfirmedBatch(
            originalBatch = batch,
            drafts = listOf(draft),
            rawSpokenText = "نص معدل يدوياً 999 شيكل",
            billReceiptPath = null,
            nowEpochMs = 2_000L
        )

        assertEquals(1, confirmed.items.size)
        assertEquals("نص معدل يدوياً 999 شيكل", confirmed.items.first().rawSpokenText)
        assertEquals("قهوة مختصة", confirmed.items.first().title)
        assertEquals(20.0, confirmed.items.first().amount)
    }

    // ── R2 / F4: Single-File Receipt URI Linking & Session Cleanup ──────────

    @Test
    fun testBuildConfirmedBatchStampsSingleReceiptUriAcrossMultiItemAndSplitBatch() {
        val batch = ExpenseBatch(
            batchId = "batch_multi_1",
            items = emptyList(),
            totalBatchAmount = 150.0,
            createdAt = 1_000L
        )
        val drafts = listOf(
            ConfirmedDraftItem(
                originalId = "exp_a",
                currency = "₪",
                title = "عشاء مشترك",
                amountStr = "100",
                category = ExpenseCategory.DINING,
                payer = Payer("self", "أنا", true),
                allocations = listOf(
                    AllocationDraft(id = "alloc_1", partyName = "البيت", amountStr = "40"),
                    AllocationDraft(id = "alloc_2", partyName = "أخيك", amountStr = "30")
                )
            ),
            ConfirmedDraftItem(
                originalId = "exp_b",
                currency = "₪",
                title = "مواصلات",
                amountStr = "50",
                category = ExpenseCategory.TRANSPORT,
                payer = Payer("self", "أنا", true)
            )
        )

        val receiptUri = "file:///sandbox/receipts/batch_multi_1.jpg"
        val confirmed = buildConfirmedBatch(
            originalBatch = batch,
            drafts = drafts,
            rawSpokenText = "عشاء ومواصلات",
            billReceiptPath = receiptUri,
            nowEpochMs = 5_000L
        )

        assertEquals(2, confirmed.items.size)
        assertTrue(confirmed.items.all { it.receiptPhotoPath == receiptUri })
        assertTrue(confirmed.items.all { it.proofType == ProofType.RECEIPT_PHOTO })
        assertEquals(30.0, confirmed.items[0].myShare)
        assertEquals(2, confirmed.items[0].split?.allocations?.size)
    }

    @Test
    fun testBuildConfirmedBatchPreservesBankSmsProofAndClearsRemovedReceiptProof() {
        val smsProof = BankSmsProof("بنك فلسطين", "خصم 50", 50.0, "₪", "سوبرماركت", 1000L)
        val batch = ExpenseBatch("batch_edit_exp_sms", emptyList(), 50.0, 1000L)

        val smsDraft = ConfirmedDraftItem(
            originalId = "exp_sms",
            currency = "₪",
            title = "سوبرماركت",
            amountStr = "50",
            category = ExpenseCategory.GROCERIES,
            payer = Payer("self", "أنا", true),
            initialTimestamp = 1000L,
            initialProofType = ProofType.BANK_SMS,
            initialProofDetails = smsProof
        )
        val receiptRemovedDraft = ConfirmedDraftItem(
            originalId = "exp_rec",
            currency = "₪",
            title = "فاتورة",
            amountStr = "30",
            category = ExpenseCategory.BILLS,
            payer = Payer("self", "أنا", true),
            initialTimestamp = 1000L,
            initialProofType = ProofType.RECEIPT_PHOTO
        )

        val confirmedWithReceipt = buildConfirmedBatch(batch, listOf(smsDraft), "", "file:///r.jpg", 2000L)
        assertEquals(ProofType.BANK_SMS, confirmedWithReceipt.items.first().proofType)
        assertEquals(smsProof, confirmedWithReceipt.items.first().proofDetails)
        assertEquals(1000L, confirmedWithReceipt.items.first().timestamp)

        val confirmedRemovedReceipt = buildConfirmedBatch(batch, listOf(receiptRemovedDraft), "", null, 2000L)
        assertEquals(ProofType.NONE, confirmedRemovedReceipt.items.first().proofType)
        assertNull(confirmedRemovedReceipt.items.first().receiptPhotoPath)
    }

    @Test
    fun testReceiptSessionTrackerProtectsPersistedUriAndCleansOnlyUnsavedPicks() {
        val persistedUri = "file:///sandbox/receipts/persisted_shared.jpg"
        val tracker = ReceiptSessionTracker(persistedUri)

        // Removing a pre-existing persisted URI must NOT return it for file deletion (shared with sibling records)
        val onRemovePersisted = tracker.onRemoveReceipt()
        assertTrue(onRemovePersisted.isEmpty())
        assertNull(tracker.currentPath)

        // Picking two new files in session: second pick returns first unsaved pick for cleanup
        val pick1 = "file:///sandbox/receipts/temp_1.jpg"
        val pick2 = "file:///sandbox/receipts/temp_2.jpg"
        assertTrue(tracker.onReceiptPicked(pick1).isEmpty())
        assertEquals(listOf(pick1), tracker.onReceiptPicked(pick2))

        // Dismissing without save deletes temp_2, never persistedUri
        val onDismiss = tracker.onDismissWithoutSave()
        assertEquals(listOf(pick2), onDismiss)
        assertFalse(persistedUri in onDismiss)
    }

    // ── R4 / F7: Localized Numbers, Clamping & Auto-Balancing Split Bar ─────

    @Test
    fun testParseLocalizedAmountHandlesArabicIndicThousandsAndDecimals() {
        assertEquals(50.0, parseLocalizedAmount("٥٠"))
        assertEquals(12.5, parseLocalizedAmount("١٢٫٥"))
        assertEquals(1250.75, parseLocalizedAmount("١٬٢٥٠٫٧٥"))
        assertEquals(10.75, parseLocalizedAmount("10,75"))
        assertNull(parseLocalizedAmount(""))
        assertNull(parseLocalizedAmount("   "))
        assertNull(parseLocalizedAmount("NaN"))
        assertNull(parseLocalizedAmount("Infinity"))
    }

    @Test
    fun testApplyPartyShareInputClampsExcessAndPreservesDecimalTyping() {
        // Other party has 40 out of 100 -> requesting 75 clamps to 60
        val clamped = applyPartyShareInput(
            totalAmount = 100.0,
            currency = "₪",
            currentPartyShares = listOf("40", ""),
            targetIndex = 1,
            requestedInput = "75"
        )
        assertTrue(clamped.wasClamped)
        assertEquals(60.0, clamped.clampedAmount)
        assertEquals(60.0, clamped.maxAllowed)
        assertEquals("60", clamped.amountStr)

        // Intermediate decimal typing ("12.") is preserved without truncation
        val typingDecimal = applyPartyShareInput(
            totalAmount = 100.0,
            currency = "₪",
            currentPartyShares = listOf("40", "12"),
            targetIndex = 1,
            requestedInput = "12."
        )
        assertFalse(typingDecimal.wasClamped)
        assertEquals("12.", typingDecimal.amountStr)

        // Arabic-Indic decimal within limit is preserved
        val indicInput = applyPartyShareInput(
            totalAmount = 100.0,
            currency = "₪",
            currentPartyShares = listOf("40", ""),
            targetIndex = 1,
            requestedInput = "١٢٫٥"
        )
        assertFalse(indicInput.wasClamped)
        assertEquals(12.5, indicInput.clampedAmount)

        // Negative input is rejected
        val negative = applyPartyShareInput(
            totalAmount = 100.0,
            currency = "₪",
            currentPartyShares = listOf("40", "10"),
            targetIndex = 1,
            requestedInput = "-5"
        )
        assertTrue(negative.wasClamped)
        assertEquals(0.0, negative.clampedAmount)
        assertEquals("0", negative.amountStr)
    }

    @Test
    fun testApplyBillTotalInputClampsSharesOnShrinkAndPreservesDuringEmptyRetype() {
        val shrunk = applyBillTotalInput(
            newTotalInput = "50",
            currency = "₪",
            currentPartyShares = listOf("40", "30")
        )
        assertTrue(shrunk.anyClamped)
        assertEquals(listOf("40", "10"), shrunk.partyShares)

        // Clearing total input mid-retype must not wipe existing party shares
        val cleared = applyBillTotalInput(
            newTotalInput = "",
            currency = "₪",
            currentPartyShares = listOf("40", "30")
        )
        assertFalse(cleared.anyClamped)
        assertEquals(listOf("40", "30"), cleared.partyShares)
    }

    @Test
    fun testBuildSplitBarSegmentsSupportsStandardAndJodThreeDecimals() {
        val (ilsSummary, ilsSegments) = buildSplitBarSegments(
            totalAmount = 100.0,
            currency = "₪",
            partyEntries = listOf("البيت" to 40.0, "أخيك" to 30.0)
        )
        assertEquals(30.0, ilsSummary.personalShare)
        assertEquals(0.3f, ilsSummary.personalRatio, 0.001f)
        assertEquals(3, ilsSegments.size)
        assertEquals("حصتي", ilsSegments[0].label)
        assertTrue(ilsSegments[0].isPersonal)
        assertEquals("البيت", ilsSegments[1].label)
        assertEquals("أخيك", ilsSegments[2].label)

        // 3-decimal JOD precision
        val (jodSummary, _) = buildSplitBarSegments(
            totalAmount = 10.0,
            currency = "د.أ",
            partyEntries = listOf("أحمد" to 3.333, "خالد" to 3.333)
        )
        assertEquals(3.334, jodSummary.personalShare, 0.0001)
        assertEquals("3.334", formatDraftAmount(jodSummary.personalShare, "د.أ"))
    }

    @Test
    fun testBuildConfirmedBatchPreservesPartyIdsAndSettlementsOnEdit() {
        val existingSettlement = Settlement(
            id = "settle_existing_1",
            refNo = 77L,
            allocationId = "alloc_1",
            amount = 40.0,
            settledAt = 1_500L,
            recordedAt = 1_500L,
            note = "دفعة قديمة"
        )
        val batch = ExpenseBatch("batch_edit_exp_1", emptyList(), 100.0, 1_000L)
        val draft = ConfirmedDraftItem(
            originalId = "exp_1",
            currency = "₪",
            title = "عشاء",
            amountStr = "100",
            category = ExpenseCategory.DINING,
            payer = Payer("self", "أنا", true),
            initialTimestamp = 1_000L,
            initialRecordType = ExpenseRecordType.RECEIVABLE,
            initialSplitGroupId = "split_grp_1",
            initialRefNo = 42L,
            allocations = listOf(
                AllocationDraft(
                    id = "alloc_1",
                    partyId = "party_uuid_1",
                    partyName = "أحمد",
                    amountStr = "40",
                    isSettledNow = true,
                    refNo = 55L,
                    existingSettlements = listOf(existingSettlement)
                )
            )
        )

        val confirmed = buildConfirmedBatch(batch, listOf(draft), "عشاء", "file:///r.jpg", 9_999L)
        val item = confirmed.items.first()
        assertEquals(1_000L, item.timestamp)
        assertEquals(ExpenseRecordType.RECEIVABLE, item.recordType)
        assertEquals("split_grp_1", item.splitGroupId)
        assertEquals(42L, item.refNo)
        val alloc = item.split!!.allocations.first()
        assertEquals("party_uuid_1", alloc.party.id)
        assertEquals(55L, alloc.refNo)
        assertEquals(listOf(existingSettlement), alloc.settlements)
    }

    // ── R3 / F7: ReceivablesLedger Compatibility with Split + Legacy Records

    @Test
    fun testReceivablesLedgerHandlesPhase3ReceivablePayableAndLegacySplitWithReceiptLookup() {
        val sharedReceipt = "file:///sandbox/receipts/bill_99.jpg"
        val phase3Receivable = ExpenseItem(
            id = "exp_rec_1",
            title = "عشاء",
            amount = 40.0,
            currency = "₪",
            category = ExpenseCategory.DINING,
            timestamp = 2_000L,
            payer = Payer("self", "أنا", true),
            recordType = ExpenseRecordType.RECEIVABLE,
            splitGroupId = "split_99",
            receiptPhotoPath = sharedReceipt,
            myShare = 0.0,
            split = ExpenseSplit(
                totalAmount = 40.0,
                payer = Payer("self", "أنا", true),
                participants = listOf(Payer("party_beit", "البيت", false)),
                amountPerPerson = 40.0,
                allocations = listOf(
                    Allocation(
                        id = "alloc_beit",
                        party = Payer("party_beit", "البيت", false),
                        amount = 40.0
                    )
                )
            )
        )
        val legacySplit = ExpenseItem(
            id = "exp_legacy_1",
            title = "غداء",
            amount = 60.0,
            currency = "₪",
            category = ExpenseCategory.DINING,
            timestamp = 1_000L,
            payer = Payer("self", "أنا", true),
            recordType = ExpenseRecordType.PERSONAL,
            myShare = 30.0,
            split = ExpenseSplit(
                totalAmount = 60.0,
                payer = Payer("self", "أنا", true),
                participants = listOf(Payer("self", "أنا", true), Payer("party_akh", "أخيك", false)),
                amountPerPerson = 30.0,
                allocations = listOf(
                    Allocation(
                        id = "alloc_akh",
                        party = Payer("party_akh", "أخيك", false),
                        amount = 30.0
                    )
                )
            )
        )
        val payable = ExpenseItem(
            id = "exp_payable_1",
            title = "فاتورة نت",
            amount = 100.0,
            currency = "₪",
            category = ExpenseCategory.BILLS,
            timestamp = 3_000L,
            payer = Payer("party_sami", "سامي", false),
            recordType = ExpenseRecordType.PAYABLE,
            myShare = 50.0,
            split = ExpenseSplit(
                totalAmount = 100.0,
                payer = Payer("party_sami", "سامي", false),
                participants = listOf(Payer("party_sami", "سامي", false), Payer("self", "أنا", true)),
                amountPerPerson = 50.0
            )
        )

        val receivableExpenses = listOf(phase3Receivable, legacySplit, payable)
        val computations = ReceivablesLedger.getAllocationComputations(receivableExpenses)
        val summaries = ReceivablesLedger.getPartySummaries(receivableExpenses)
        val expensesById = receivableExpenses.associateBy { it.id }

        assertEquals(3, computations.size)
        assertEquals(3, summaries.size)
        assertEquals(70.0, ReceivablesLedger.getTotalRemaining(receivableExpenses, "₪"))

        val beitComp = computations.first { it.party.name == "البيت" }
        assertTrue(beitComp.isOwedToMe)
        assertEquals(sharedReceipt, expensesById[beitComp.expenseId]?.receiptPhotoPath)

        val samiSummary = summaries.first { it.party.name == "سامي" }
        assertEquals(-50.0, samiSummary.netBalance)
    }

    // ── Iteration 2 Regression Tests ─────────────────────────────────────────

    @Test
    fun testReEditReceiptPickKeyIsUniqueAndRemovedReceiptDoesNotResurrectOnRawTextEdit() {
        val batchId = "batch_edit_exp_1"
        val persistedUri = "file:///sandbox/receipts/batch_edit_exp_1.jpg"
        val tracker = ReceiptSessionTracker(persistedUri)

        // Unique per-pick keys never match bare batchId so disk file batch_edit_exp_1.jpg is never overwritten before Save
        val pickKey1 = buildReceiptPickKey(batchId, 10_000L, 1)
        val pickKey2 = buildReceiptPickKey(batchId, 10_000L, 2)
        assertNotEquals(batchId, pickKey1)
        assertNotEquals(pickKey1, pickKey2)
        assertTrue(pickKey1.startsWith("${batchId}_pick_"))

        val replacementPath = "file:///sandbox/receipts/$pickKey1.jpg"
        assertTrue(tracker.onReceiptPicked(replacementPath).isEmpty())

        // Dismissing after replacement pick schedules ONLY the uncommitted replacement for deletion, preserving persistedUri
        val toDeleteOnDismiss = tracker.onDismissWithoutSave()
        assertEquals(listOf(replacementPath), toDeleteOnDismiss)
        assertFalse(persistedUri in toDeleteOnDismiss)

        // Removal + UpdatePendingRawText (where batch.items still holds persistedUri) must NOT resurrect receipt
        val trackerAfterRemove = ReceiptSessionTracker(persistedUri)
        assertTrue(trackerAfterRemove.onRemoveReceipt().isEmpty())
        val userRemoved = true
        assertFalse(
            shouldSyncBatchReceipt(
                userRemovedReceipt = userRemoved,
                currentBillReceiptPath = trackerAfterRemove.currentPath,
                batchReceiptPath = persistedUri
            )
        )
        // Picking a fresh receipt clears the removed flag
        assertTrue(
            shouldSyncBatchReceipt(
                userRemovedReceipt = false,
                currentBillReceiptPath = null,
                batchReceiptPath = persistedUri
            )
        )
    }

    @Test
    fun testPayableWithEmptyAllocationsRoundTripPreservesPayableAndMyShare() {
        val payable = ExpenseItem(
            id = "exp_payable_1",
            title = "فاتورة نت",
            amount = 100.0,
            currency = "₪",
            category = ExpenseCategory.BILLS,
            timestamp = 3_000L,
            payer = Payer("party_sami", "سامي", false),
            recordType = ExpenseRecordType.PAYABLE,
            myShare = 50.0,
            split = ExpenseSplit(
                totalAmount = 100.0,
                payer = Payer("party_sami", "سامي", false),
                participants = listOf(Payer("party_sami", "سامي", false), Payer("self", "أنا", true)),
                amountPerPerson = 50.0
            )
        )
        val batch = ExpenseBatch("batch_edit_exp_payable_1", listOf(payable), 100.0, 3_000L)
        val draft = payable.toExpenseItemDraft()
        assertEquals(50.0, draft.myShare)

        val confirmed = buildConfirmedBatch(
            originalBatch = batch,
            drafts = listOf(draft.toConfirmedDraftItem()),
            rawSpokenText = "فاتورة نت",
            billReceiptPath = null,
            nowEpochMs = 4_000L
        )
        val splitItems = ExpenseSplitSplitter.splitBatch(confirmed, 4_000L)
        assertEquals(1, splitItems.size)
        val saved = splitItems.first()
        assertEquals(ExpenseRecordType.PAYABLE, saved.recordType)
        assertEquals(50.0, saved.myShare)
        assertEquals(50.0, saved.effectiveAmount)
        assertEquals(100.0, saved.amount)
        assertTrue(saved.split != null)

        val summary = assertNotNull(
            ReceivablesLedger.getPartySummaries(splitItems).firstOrNull { it.party.name == "سامي" }
        )
        assertEquals(-50.0, summary.netBalance)
    }

    @Test
    fun testPaidForMeKhaledRoundTripPreservesMyShareAndCurrentUserAllocation() {
        val parser = LocalDialectExpenseParser()
        val parsedBatch = parser.parseSpokenText("دفع عني خالد 50 شيكل", "₪", 1_000L)
        assertEquals(1, parsedBatch.items.size)
        val parsedItem = parsedBatch.items.first()
        assertFalse(parsedItem.payer.isCurrentUser)
        assertEquals(50.0, parsedItem.myShare)

        val draft = parsedItem.toExpenseItemDraft()
        assertEquals(50.0, draft.myShare)
        assertTrue(draft.allocations.first().isCurrentUser)

        val confirmed = buildConfirmedBatch(
            originalBatch = parsedBatch,
            drafts = listOf(draft.toConfirmedDraftItem()),
            rawSpokenText = "دفع عني خالد 50 شيكل",
            billReceiptPath = null,
            nowEpochMs = 2_000L
        )
        val splitItems = ExpenseSplitSplitter.splitBatch(confirmed, 2_000L)
        assertEquals(1, splitItems.size)
        val saved = splitItems.first()
        assertEquals(ExpenseRecordType.PAYABLE, saved.recordType)
        assertEquals(50.0, saved.myShare)
        assertEquals(50.0, saved.effectiveAmount)
        assertTrue(saved.split?.allocations?.firstOrNull()?.party?.isCurrentUser == true)

        val integrity = LedgerIntegrityChecker.verify(splitItems, 2_000L)
        assertTrue(integrity.isValid, "Integrity errors: ${integrity.errors}")
        val summary = assertNotNull(
            ReceivablesLedger.getPartySummaries(splitItems).firstOrNull { it.party.name == "خالد" }
        )
        assertEquals(-50.0, summary.netBalance)
    }

    @Test
    fun testSettledAmountFloorPreventsOverpaidAllocationAndKeepsLedgerValid() {
        val settled40 = Settlement(
            id = "settle_40",
            refNo = 10L,
            allocationId = "alloc_settled_1",
            amount = 40.0,
            settledAt = 1_500L,
            recordedAt = 1_500L
        )
        val allocDraft = AllocationDraft(
            id = "alloc_settled_1",
            partyId = "party_1",
            partyName = "أحمد",
            amountStr = "40",
            isSettledNow = true,
            existingSettlements = listOf(settled40)
        )

        // 1. Draft flags violation if user types 30 (< 40 settled)
        val draft = ExpenseItemDraft(
            originalId = "exp_settled_1",
            currency = "₪",
            initialTitle = "عشاء",
            initialAmountStr = "30",
            initialCategory = ExpenseCategory.DINING,
            initialPayer = Payer("self", "أنا", true),
            initialAllocations = listOf(allocDraft.copy(amountStr = "30")),
            initialRecordType = ExpenseRecordType.RECEIVABLE
        )
        assertTrue(draft.hasSettledFloorViolation)

        // 2. applyPartyShareInput with minFloor clamps upward from 30 -> 40
        val partyClamped = applyPartyShareInput(
            totalAmount = 100.0,
            currency = "₪",
            currentPartyShares = listOf("40"),
            targetIndex = 0,
            requestedInput = "30",
            minFloor = 40.0
        )
        assertTrue(partyClamped.wasClamped)
        assertEquals(40.0, partyClamped.clampedAmount)
        assertEquals("40", partyClamped.amountStr)

        // 3. applyBillTotalInput shrinking from 100 -> 30 never shrinks settled party below 40 and flags belowSettledFloor
        val billShrunk = applyBillTotalInput(
            newTotalInput = "30",
            currency = "₪",
            currentPartyShares = listOf("40", "30"),
            minPartyFloors = listOf(40.0, 0.0)
        )
        assertTrue(billShrunk.belowSettledFloor)
        assertEquals(listOf("40", "0"), billShrunk.partyShares)

        // 4. buildConfirmedBatch + ExpenseSplitSplitter enforces settled floor so LedgerIntegrityChecker is valid
        val confirmed = buildConfirmedBatch(
            originalBatch = ExpenseBatch("batch_edit_exp_settled_1", emptyList(), 30.0, 1_000L),
            drafts = listOf(draft.toConfirmedDraftItem()),
            rawSpokenText = "",
            billReceiptPath = null,
            nowEpochMs = 2_000L
        )
        val splitItems = ExpenseSplitSplitter.splitBatch(confirmed, 2_000L)
        val integrity = LedgerIntegrityChecker.verify(splitItems, 2_000L)
        assertTrue(integrity.isValid, "Integrity errors: ${integrity.errors}")
        assertTrue(splitItems.all { item ->
            item.split?.allocations?.all { it.amount >= 40.0 } ?: true
        })
    }

    @Test
    fun testExtremeAmountLongOverflowGuardInExpenseItemDraft() {
        val draft = ExpenseItemDraft(
            originalId = "exp_overflow",
            currency = "₪",
            initialTitle = "اختبار",
            initialAmountStr = "100",
            initialCategory = ExpenseCategory.GENERAL,
            initialPayer = Payer("self", "أنا", true),
            initialAllocations = listOf(
                AllocationDraft(id = "a1", partyName = "أحمد", amountStr = "90000000000000000"),
                AllocationDraft(id = "a2", partyName = "خالد", amountStr = "90000000000000000")
            )
        )
        assertTrue(draft.isOverAllocated)
        assertTrue(draft.sumAllocations > 0.0)
    }

    @Test
    fun testCurrencyScopedPartyDetailFilteringAndSettleAll() {
        val ilsExpense = ExpenseItem(
            id = "exp_ils",
            title = "قهوة",
            amount = 50.0,
            currency = "₪",
            category = ExpenseCategory.DINING,
            timestamp = 1_000L,
            payer = Payer("self", "أنا", true),
            recordType = ExpenseRecordType.RECEIVABLE,
            myShare = 0.0,
            split = ExpenseSplit(
                totalAmount = 50.0,
                payer = Payer("self", "أنا", true),
                participants = listOf(Payer("أحمد", "أحمد", false)),
                amountPerPerson = 50.0,
                allocations = listOf(
                    Allocation(id = "alloc_ils", party = Payer("أحمد", "أحمد", false), amount = 50.0)
                )
            )
        )
        val jodExpense = ExpenseItem(
            id = "exp_jod",
            title = "عشاء",
            amount = 20.0,
            currency = "د.أ",
            category = ExpenseCategory.DINING,
            timestamp = 2_000L,
            payer = Payer("self", "أنا", true),
            recordType = ExpenseRecordType.RECEIVABLE,
            myShare = 0.0,
            split = ExpenseSplit(
                totalAmount = 20.0,
                payer = Payer("self", "أنا", true),
                participants = listOf(Payer("أحمد", "أحمد", false)),
                amountPerPerson = 20.0,
                allocations = listOf(
                    Allocation(id = "alloc_jod", party = Payer("أحمد", "أحمد", false), amount = 20.0)
                )
            )
        )
        val expenses = listOf(ilsExpense, jodExpense)

        val ilsTarget = SelectedPartyTarget(partyId = "أحمد", partyName = "أحمد", currency = "₪")
        val jodTarget = SelectedPartyTarget(partyId = "أحمد", partyName = "أحمد", currency = "د.أ")

        val ilsRows = filterComputationsForParty(expenses, ilsTarget)
        assertEquals(1, ilsRows.size)
        assertEquals("₪", ilsRows.first().currency)
        assertEquals("قهوة", ilsRows.first().expenseTitle)
        assertEquals(50.0, computePartyRemainingForTarget(ilsRows, ilsTarget))
        assertEquals(listOf("alloc_ils"), filterOpenSettleAllComputations(expenses, ilsTarget).map { it.allocationId })

        val jodRows = filterComputationsForParty(expenses, jodTarget)
        assertEquals(1, jodRows.size)
        assertEquals("د.أ", jodRows.first().currency)
        assertEquals("عشاء", jodRows.first().expenseTitle)
        assertEquals(20.0, computePartyRemainingForTarget(jodRows, jodTarget))
        assertEquals(listOf("alloc_jod"), filterOpenSettleAllComputations(expenses, jodTarget).map { it.allocationId })
    }
}

