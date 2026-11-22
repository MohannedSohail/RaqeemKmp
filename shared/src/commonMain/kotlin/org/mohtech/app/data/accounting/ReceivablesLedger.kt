package org.mohtech.app.data.accounting

import kotlin.time.Clock
import org.mohtech.app.data.model.Allocation
import org.mohtech.app.data.model.DebtStatus
import org.mohtech.app.data.model.ExpenseItem
import org.mohtech.app.data.model.Payer
import org.mohtech.app.data.model.Settlement

/**
 * Detailed computation state of a single debt allocation.
 */
data class AllocationComputation(
    val allocationId: String,
    val refNo: Long,
    val party: Payer,
    val totalAmount: Double,
    val settledAmount: Double,
    val remainingAmount: Double,
    val status: DebtStatus,
    val lastSettledAt: Long?,
    val note: String,
    val currency: String,
    val isOwedToMe: Boolean,
    val expenseId: String,
    val expenseTitle: String,
    val expenseTimestamp: Long,
    val settlements: List<Settlement>
)

/**
 * Comprehensive ledger balance summary for a specific party and currency.
 */
data class PartyBalanceSummary(
    val party: Payer,
    val currency: String,
    val totalReceivable: Double,  // Total owed to me (before settlements)
    val totalSettledToMe: Double, // Total settled of what was owed to me
    val totalRemainingToMe: Double, // Remaining owed to me
    val totalPayable: Double,     // Total owed by me (before settlements)
    val totalSettledByMe: Double, // Total settled of what I owed
    val totalRemainingByMe: Double, // Remaining owed by me
    val netBalance: Double        // totalRemainingToMe - totalRemainingByMe (> 0 party owes me; < 0 I owe party)
) {
    val totalSettled: Double get() = totalSettledToMe
    val totalRemaining: Double get() = totalRemainingToMe
}

/**
 * Result of the ledger mathematical integrity audit.
 */
data class IntegrityCheckResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

/**
 * ReceivablesLedger:
 * Single Source of Truth for debt and receivables accounting in Raqeem.
 * Enforces zero-cloud, 100% mathematical integrity across all currency allocations.
 */
object ReceivablesLedger {

    private var settlementSequence: Long = 1000L
    private var refNoSequence: Long = 100L

    fun nextRefNo(): Long = ++refNoSequence
    fun nextSettlementSeq(): Long = ++settlementSequence

    /**
     * Computes settled amount, remaining amount, and debt status for an allocation.
     */
    fun computeAllocation(
        allocation: Allocation,
        currency: String,
        expenseId: String = "",
        expenseTitle: String = "",
        expenseTimestamp: Long = 0L,
        isOwedToMe: Boolean = true
    ): AllocationComputation {
        val activeSettlements = allocation.settlements.filter { it.voidedAt == null }
        val settledMinor = activeSettlements.sumOf { Money.doubleToMinor(it.amount, currency) }
        val totalMinor = Money.doubleToMinor(allocation.amount, currency)
        val remainingMinor = (totalMinor - settledMinor).coerceAtLeast(0L)

        val settledAmount = Money.minorToDouble(settledMinor, currency)
        val remainingAmount = Money.minorToDouble(remainingMinor, currency)

        val status = when {
            settledMinor == 0L -> DebtStatus.OPEN
            settledMinor >= totalMinor -> DebtStatus.SETTLED
            else -> DebtStatus.PARTIAL
        }
        val lastSettledAt = activeSettlements.maxOfOrNull { it.settledAt }

        return AllocationComputation(
            allocationId = allocation.id,
            refNo = allocation.refNo,
            party = allocation.party,
            totalAmount = Money.minorToDouble(totalMinor, currency),
            settledAmount = settledAmount,
            remainingAmount = remainingAmount,
            status = status,
            lastSettledAt = lastSettledAt,
            note = allocation.note,
            currency = currency,
            isOwedToMe = isOwedToMe,
            expenseId = expenseId,
            expenseTitle = expenseTitle,
            expenseTimestamp = expenseTimestamp,
            settlements = allocation.settlements
        )
    }

    /**
     * Extracts and computes all active allocation states from a list of expenses.
     */
    fun getAllocationComputations(expenses: List<ExpenseItem>): List<AllocationComputation> {
        val results = mutableListOf<AllocationComputation>()

        for (expense in expenses) {
            if (expense.deletedAt != null) continue

            val split = expense.split ?: continue
            val currency = expense.currency

            if (expense.payer.isCurrentUser) {
                // User paid -> Debts owed to user
                if (split.allocations.isNotEmpty()) {
                    for (alloc in split.allocations) {
                        results.add(
                            computeAllocation(
                                allocation = alloc,
                                currency = currency,
                                expenseId = expense.id,
                                expenseTitle = expense.title,
                                expenseTimestamp = expense.timestamp,
                                isOwedToMe = true
                            )
                        )
                    }
                } else {
                    // Legacy split fallback
                    val otherParticipants = split.participants.filterNot { it.isCurrentUser }
                    for (p in otherParticipants) {
                        val legacyAlloc = Allocation(
                            id = "alloc_${expense.id}_${p.id}",
                            party = p,
                            amount = split.amountPerPerson,
                            note = split.note
                        )
                        results.add(
                            computeAllocation(
                                allocation = legacyAlloc,
                                currency = currency,
                                expenseId = expense.id,
                                expenseTitle = expense.title,
                                expenseTimestamp = expense.timestamp,
                                isOwedToMe = true
                            )
                        )
                    }
                }
            } else {
                // Someone else paid -> Debt owed by user to payer (creditor)
                val creditor = expense.payer
                val userOwedAmount = expense.myShare ?: split.amountPerPerson
                val matchedAlloc = split.allocations.firstOrNull { it.party.isCurrentUser || it.party.id == "self" }
                val payableAlloc = if (matchedAlloc != null) {
                    matchedAlloc.copy(party = creditor)
                } else {
                    Allocation(
                        id = "alloc_payable_${expense.id}",
                        party = creditor,
                        amount = userOwedAmount,
                        note = split.note
                    )
                }

                results.add(
                    computeAllocation(
                        allocation = payableAlloc,
                        currency = currency,
                        expenseId = expense.id,
                        expenseTitle = expense.title,
                        expenseTimestamp = expense.timestamp,
                        isOwedToMe = false
                    )
                )
            }
        }

        return results
    }

    /**
     * Aggregates balances by party and currency, handling both debt directions (owed to me vs owed by me)
     * and calculating exact net balances.
     */
    fun getPartySummaries(expenses: List<ExpenseItem>): List<PartyBalanceSummary> {
        val computations = getAllocationComputations(expenses)
        val grouped = computations.groupBy { Pair(it.party.id.ifBlank { it.party.name }, it.currency) }

        return grouped.map { (key, items) ->
            val sampleParty = items.first().party
            val currency = key.second

            var receivableMinor = 0L
            var settledToMeMinor = 0L
            var remainingToMeMinor = 0L

            var payableMinor = 0L
            var settledByMeMinor = 0L
            var remainingByMeMinor = 0L

            for (item in items) {
                val totalMinor = Money.doubleToMinor(item.totalAmount, currency)
                val settledMinor = Money.doubleToMinor(item.settledAmount, currency)
                val remainingMinor = Money.doubleToMinor(item.remainingAmount, currency)

                if (item.isOwedToMe) {
                    receivableMinor += totalMinor
                    settledToMeMinor += settledMinor
                    remainingToMeMinor += remainingMinor
                } else {
                    payableMinor += totalMinor
                    settledByMeMinor += settledMinor
                    remainingByMeMinor += remainingMinor
                }
            }

            val netMinor = remainingToMeMinor - remainingByMeMinor

            PartyBalanceSummary(
                party = sampleParty,
                currency = currency,
                totalReceivable = Money.minorToDouble(receivableMinor, currency),
                totalSettledToMe = Money.minorToDouble(settledToMeMinor, currency),
                totalRemainingToMe = Money.minorToDouble(remainingToMeMinor, currency),
                totalPayable = Money.minorToDouble(payableMinor, currency),
                totalSettledByMe = Money.minorToDouble(settledByMeMinor, currency),
                totalRemainingByMe = Money.minorToDouble(remainingByMeMinor, currency),
                netBalance = Money.minorToDouble(netMinor, currency)
            )
        }
    }

    /**
     * Retrieves summary for a specific party ID and currency.
     */
    fun getPartySummary(partyId: String, currency: String, expenses: List<ExpenseItem>): PartyBalanceSummary? {
        return getPartySummaries(expenses).firstOrNull {
            (it.party.id == partyId || it.party.name == partyId) && it.currency == currency
        }
    }

    /**
     * Computes total remaining receivables (debts owed to user) across all parties for a currency.
     */
    fun getTotalRemaining(expenses: List<ExpenseItem>, currency: String): Double {
        val summaries = getPartySummaries(expenses).filter { it.currency == currency }
        val totalMinor = summaries.sumOf { Money.doubleToMinor(it.totalRemainingToMe, currency) }
        return Money.minorToDouble(totalMinor, currency)
    }

    /**
     * Computes total settled receivables for a currency.
     */
    fun getTotalSettled(expenses: List<ExpenseItem>, currency: String): Double {
        val summaries = getPartySummaries(expenses).filter { it.currency == currency }
        val totalMinor = summaries.sumOf { Money.doubleToMinor(it.totalSettledToMe, currency) }
        return Money.minorToDouble(totalMinor, currency)
    }

    /**
     * Records a new settlement for an allocation inside an ExpenseItem.
     * Validates:
     * - Settlement amount must be positive.
     * - Settlement amount cannot exceed remaining unpaid balance.
     * - Settlement timestamp cannot be in the future.
     */
    fun recordSettlement(
        expense: ExpenseItem,
        allocationId: String,
        amount: Double,
        settledAt: Long,
        note: String = "",
        refNo: Long = 0L,
        settlementId: String = "",
        nowMillis: Long = Clock.System.now().toEpochMilliseconds()
    ): ExpenseItem {
        require(amount > 0.0) { "Settlement amount must be positive, was: $amount" }
        require(settledAt <= nowMillis) { "Settlement date cannot be in the future ($settledAt > $nowMillis)" }

        val split = expense.split ?: throw IllegalArgumentException("Expense ${expense.id} has no split configuration")
        val allocations = split.allocations.toMutableList()
        val index = allocations.indexOfFirst { it.id == allocationId }
        require(index >= 0) { "Allocation with id '$allocationId' not found in expense ${expense.id}" }

        val targetAlloc = allocations[index]
        val currency = expense.currency

        val totalAllocMinor = Money.doubleToMinor(targetAlloc.amount, currency)
        val activeSettledMinor = targetAlloc.settlements
            .filter { it.voidedAt == null }
            .sumOf { Money.doubleToMinor(it.amount, currency) }
        val remainingMinor = (totalAllocMinor - activeSettledMinor).coerceAtLeast(0L)
        val settleAmountMinor = Money.doubleToMinor(amount, currency)

        require(settleAmountMinor <= remainingMinor) {
            val remainingDouble = Money.minorToDouble(remainingMinor, currency)
            val requestedDouble = Money.minorToDouble(settleAmountMinor, currency)
            "Settlement amount ($requestedDouble $currency) exceeds remaining debt ($remainingDouble $currency)"
        }

        val sId = if (settlementId.isNotBlank()) settlementId else "settle_${nowMillis}_${nextSettlementSeq()}"
        val sRefNo = if (refNo > 0L) refNo else nextRefNo()

        val settlement = Settlement(
            id = sId,
            refNo = sRefNo,
            allocationId = allocationId,
            amount = Money.minorToDouble(settleAmountMinor, currency),
            settledAt = settledAt,
            recordedAt = nowMillis,
            note = note,
            voidedAt = null
        )

        val updatedAlloc = targetAlloc.copy(
            settlements = targetAlloc.settlements + settlement
        )
        allocations[index] = updatedAlloc

        return expense.copy(
            split = split.copy(allocations = allocations)
        )
    }

    /**
     * Voids an existing settlement without deleting it from historical records.
     * Sets [voidedAt] timestamp.
     */
    fun voidSettlement(
        expense: ExpenseItem,
        settlementId: String,
        voidedAt: Long = Clock.System.now().toEpochMilliseconds()
    ): ExpenseItem {
        val split = expense.split ?: throw IllegalArgumentException("Expense ${expense.id} has no split configuration")
        var found = false

        val updatedAllocations = split.allocations.map { alloc ->
            val hasSettlement = alloc.settlements.any { it.id == settlementId }
            if (hasSettlement) {
                found = true
                alloc.copy(
                    settlements = alloc.settlements.map { s ->
                        if (s.id == settlementId) s.copy(voidedAt = voidedAt) else s
                    }
                )
            } else {
                alloc
            }
        }

        require(found) { "Settlement with id '$settlementId' not found in expense ${expense.id}" }

        return expense.copy(
            split = split.copy(allocations = updatedAllocations)
        )
    }
}

/**
 * LedgerIntegrityChecker:
 * Audits mathematical consistency across all expenses, splits, allocations, and settlements.
 */
object LedgerIntegrityChecker {

    fun verify(
        expenses: List<ExpenseItem>,
        nowMillis: Long = Clock.System.now().toEpochMilliseconds()
    ): IntegrityCheckResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        for (expense in expenses) {
            // Soft-deleted items are excluded from active integrity checks
            if (expense.deletedAt != null) continue

            val currency = expense.currency
            val split = expense.split ?: continue

            if (split.allocations.isNotEmpty()) {
                val totalMinor = Money.doubleToMinor(expense.amount, currency)
                val allocationsMinor = split.allocations.sumOf { Money.doubleToMinor(it.amount, currency) }
                val myShare = expense.myShare ?: (expense.amount - split.allocations.sumOf { it.amount })
                val myShareMinor = Money.doubleToMinor(myShare, currency)

                if (expense.payer.isCurrentUser) {
                    if (allocationsMinor + myShareMinor != totalMinor) {
                        errors.add(
                            "Expense ${expense.id} mathematical mismatch: allocations sum ($allocationsMinor) + myShare ($myShareMinor) != total ($totalMinor)"
                        )
                    }
                }

                for (alloc in split.allocations) {
                    val allocTotalMinor = Money.doubleToMinor(alloc.amount, currency)
                    val activeSettlements = alloc.settlements.filter { it.voidedAt == null }
                    val settledMinor = activeSettlements.sumOf { Money.doubleToMinor(it.amount, currency) }

                    if (settledMinor > allocTotalMinor) {
                        errors.add(
                            "Allocation ${alloc.id} in expense ${expense.id} overpaid: settled ($settledMinor) > total ($allocTotalMinor)"
                        )
                    }

                    for (settle in alloc.settlements) {
                        if (settle.amount <= 0.0) {
                            errors.add("Settlement ${settle.id} in allocation ${alloc.id} has invalid non-positive amount: ${settle.amount}")
                        }
                        if (settle.voidedAt == null && settle.settledAt > nowMillis) {
                            errors.add("Settlement ${settle.id} has future settledAt timestamp (${settle.settledAt} > $nowMillis)")
                        }
                    }
                }
            }
        }

        return IntegrityCheckResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
}

