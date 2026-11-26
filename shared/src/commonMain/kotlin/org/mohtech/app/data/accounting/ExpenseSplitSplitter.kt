package org.mohtech.app.data.accounting

import kotlin.time.Clock
import org.mohtech.app.data.model.Allocation
import org.mohtech.app.data.model.ExpenseBatch
import org.mohtech.app.data.model.ExpenseItem
import org.mohtech.app.data.model.ExpenseRecordType
import org.mohtech.app.data.model.ExpenseSplit

/**
 * Programmatically splits shared bills into separated database records:
 * 1 PERSONAL expense record for the user's remaining share (when > 0) and
 * N independent RECEIVABLE records (1 per external party allocation),
 * linking the identical splitGroupId and receiptPhotoPath URI across all spawned records.
 */
object ExpenseSplitSplitter {

    private var groupSeq = 1000L
    private var itemSeq = 1000L

    fun nextSplitGroupId(nowEpochMs: Long = Clock.System.now().toEpochMilliseconds()): String {
        return "split_${nowEpochMs}_${++groupSeq}"
    }

    fun splitBatch(
        batch: ExpenseBatch,
        nowEpochMs: Long = Clock.System.now().toEpochMilliseconds()
    ): List<ExpenseItem> {
        return batch.items.flatMap { splitItem(it, nowEpochMs) }
    }

    fun splitItem(
        item: ExpenseItem,
        nowEpochMs: Long = item.timestamp
    ): List<ExpenseItem> {
        val split = item.split
        val sanitizedAmount = if (!item.amount.isFinite() || item.amount <= 0.0) 0.0 else item.amount
        val sanitizedMyShare = item.myShare?.let { if (!it.isFinite() || it < 0.0) 0.0 else it }

        if (!item.payer.isCurrentUser && split != null) {
            return listOf(
                item.copy(
                    id = item.id.ifBlank { "exp_payable_${nowEpochMs}_${++itemSeq}" },
                    amount = sanitizedAmount,
                    myShare = sanitizedMyShare,
                    recordType = ExpenseRecordType.PAYABLE
                )
            )
        }

        if (split == null) {
            return listOf(
                item.copy(
                    id = item.id.ifBlank { "exp_personal_${nowEpochMs}_${++itemSeq}" },
                    amount = sanitizedAmount,
                    myShare = sanitizedMyShare,
                    recordType = ExpenseRecordType.PERSONAL
                )
            )
        }

        if (split.allocations.isEmpty()) {
            return listOf(
                item.copy(
                    id = item.id.ifBlank { "exp_personal_${nowEpochMs}_${++itemSeq}" },
                    amount = sanitizedAmount,
                    recordType = ExpenseRecordType.PERSONAL,
                    split = null,
                    myShare = null
                )
            )
        }

        val currency = item.currency.ifBlank { "₪" }
        val totalMinor = if (sanitizedAmount <= 0.0) {
            0L
        } else {
            Money.doubleToMinor(sanitizedAmount, currency).coerceAtLeast(0L)
        }

        // Clamp external allocations sequentially against totalMinor so LedgerIntegrityChecker invariants always hold
        var remainingMinor = totalMinor
        val clampedEntries = mutableListOf<Pair<Allocation, Long>>()
        for (alloc in split.allocations) {
            val rawMinor = if (!alloc.amount.isFinite() || alloc.amount <= 0.0) {
                0L
            } else {
                Money.doubleToMinor(alloc.amount, currency).coerceAtLeast(0L)
            }
            val allowedMinor = rawMinor.coerceAtMost(remainingMinor)
            remainingMinor -= allowedMinor
            if (allowedMinor > 0L) {
                clampedEntries.add(alloc to allowedMinor)
            }
        }

        if (clampedEntries.isEmpty()) {
            return listOf(
                item.copy(
                    id = item.id.ifBlank { "exp_personal_${nowEpochMs}_${++itemSeq}" },
                    amount = sanitizedAmount,
                    recordType = ExpenseRecordType.PERSONAL,
                    split = null,
                    myShare = null
                )
            )
        }

        val sumAllocMinor = clampedEntries.sumOf { it.second }
        val personalMinor = (totalMinor - sumAllocMinor).coerceAtLeast(0L)
        val groupId = item.splitGroupId?.takeIf { it.isNotBlank() } ?: nextSplitGroupId(nowEpochMs)
        val result = mutableListOf<ExpenseItem>()

        if (personalMinor > 0L) {
            val personalDouble = Money.minorToDouble(personalMinor, currency)
            result.add(
                item.copy(
                    id = item.id.ifBlank { "exp_personal_${nowEpochMs}_${++itemSeq}" },
                    amount = personalDouble,
                    currency = currency,
                    split = null,
                    myShare = null,
                    recordType = ExpenseRecordType.PERSONAL,
                    splitGroupId = groupId,
                    receiptPhotoPath = item.receiptPhotoPath
                )
            )
        }

        val baseId = item.id.ifBlank { "exp_${nowEpochMs}" }
        clampedEntries.forEachIndexed { index, (alloc, allocMinor) ->
            val allocDouble = Money.minorToDouble(allocMinor, currency)
            val allocId = alloc.id.ifBlank { "alloc_${nowEpochMs}_${++itemSeq}" }
            val normalizedAlloc = alloc.copy(
                id = allocId,
                refNo = if (alloc.refNo > 0L) alloc.refNo else ReceivablesLedger.nextRefNo(),
                amount = allocDouble
            )
            val singleSplit = ExpenseSplit(
                totalAmount = allocDouble,
                payer = item.payer,
                participants = listOf(normalizedAlloc.party),
                amountPerPerson = allocDouble,
                note = normalizedAlloc.note.ifBlank { split.note },
                allocations = listOf(normalizedAlloc)
            )
            val receivableId = if (
                item.recordType == ExpenseRecordType.RECEIVABLE &&
                item.id.isNotBlank() &&
                personalMinor == 0L &&
                index == 0
            ) {
                item.id
            } else {
                "${baseId}_rec_${index}_${++itemSeq}"
            }
            result.add(
                ExpenseItem(
                    id = receivableId,
                    title = item.title,
                    amount = allocDouble,
                    currency = currency,
                    category = item.category,
                    timestamp = item.timestamp,
                    payer = item.payer,
                    split = singleSplit,
                    proofType = item.proofType,
                    proofDetails = item.proofDetails,
                    rawSpokenText = item.rawSpokenText,
                    myShare = 0.0,
                    receiptPhotoPath = item.receiptPhotoPath,
                    refNo = if (item.refNo > 0L) item.refNo else ReceivablesLedger.nextRefNo(),
                    deletedAt = item.deletedAt,
                    recordType = ExpenseRecordType.RECEIVABLE,
                    splitGroupId = groupId
                )
            )
        }

        return result
    }
}

