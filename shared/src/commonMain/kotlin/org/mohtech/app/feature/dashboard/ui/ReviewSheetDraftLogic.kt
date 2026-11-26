package org.mohtech.app.feature.dashboard.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mohtech.app.ai.nlp.ArabicNumberNormalizer
import org.mohtech.app.data.accounting.AutoSplitSummary
import org.mohtech.app.data.accounting.Money
import org.mohtech.app.data.accounting.ReceivablesLedger
import org.mohtech.app.data.accounting.SplitCalculator
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

/**
 * Normalizes Arabic-Indic numerals (٠-٩), strips Arabic thousands separators (٬),
 * and converts localized decimal separators (٫, ,) to standard finite Double.
 */
fun parseLocalizedAmount(str: String): Double? {
    if (str.isBlank()) return null
    val ascii = ArabicNumberNormalizer.convertIndicDigitsToAscii(str.trim())
        .replace("٬", "")
        .replace('٫', '.')
        .replace(',', '.')
    return ascii.toDoubleOrNull()?.takeIf { it.isFinite() }
}

/**
 * Filters raw keyboard text into valid non-negative localized decimal input
 * (allows ASCII/Arabic-Indic digits and at most one decimal separator, rejects minus signs).
 */
fun sanitizeLocalizedDecimalInput(input: String): String {
    if (input.isEmpty()) return ""
    val builder = StringBuilder(input.length)
    var hasDecimalSeparator = false
    for (ch in input) {
        when {
            ch in '0'..'9' || ch in '٠'..'٩' -> builder.append(ch)
            (ch == '.' || ch == '٫' || ch == ',') && !hasDecimalSeparator -> {
                builder.append(ch)
                hasDecimalSeparator = true
            }
        }
    }
    return builder.toString()
}

/**
 * Formats an amount using currency minor units (2 decimals for ₪/SAR/EGP/USD, 3 for JOD)
 * without trailing zeroes or IEEE-754 precision artifacts.
 */
fun formatDraftAmount(amount: Double, currency: String): String {
    if (!amount.isFinite() || amount <= 0.0) return "0"
    val decimals = Money.getCurrencyDecimals(currency)
    val factor = if (decimals == 3) 1000L else 100L
    val minor = Money.doubleToMinor(amount, currency).coerceAtLeast(0L)
    val whole = minor / factor
    val frac = minor % factor
    if (frac == 0L) return whole.toString()
    val fracStr = frac.toString().padStart(decimals, '0').trimEnd('0')
    return "$whole.$fracStr"
}

/**
 * Saturating conversion from finite non-negative Double amount to minor units.
 */
private fun safeNonNegativeMinor(amount: Double, currency: String): Long {
    if (!amount.isFinite() || amount <= 0.0) return 0L
    val decimals = Money.getCurrencyDecimals(currency)
    val factor = if (decimals == 3) 1000.0 else 100.0
    val scaled = amount * factor
    if (!scaled.isFinite() || scaled >= Long.MAX_VALUE.toDouble()) return Long.MAX_VALUE
    return Money.doubleToMinor(amount, currency).coerceAtLeast(0L)
}

/**
 * Saturating addition on non-negative minor-unit Longs to prevent signed overflow on extreme inputs.
 */
private fun saturatingAddMinor(a: Long, b: Long): Long {
    val safeA = a.coerceAtLeast(0L)
    val safeB = b.coerceAtLeast(0L)
    return if (safeA > Long.MAX_VALUE - safeB) Long.MAX_VALUE else safeA + safeB
}

/**
 * Builds a unique per-pick receipt key so picking a replacement image during re-edit
 * never overwrites a previously persisted receipt file on disk before Save.
 */
fun buildReceiptPickKey(batchId: String, timestampMs: Long, sequence: Int): String =
    "${batchId}_pick_${timestampMs}_$sequence"

/**
 * Returns true only when an external batch receipt should hydrate the sheet's receipt state,
 * preventing receipt resurrection if the user explicitly removed it and then edited raw text.
 */
fun shouldSyncBatchReceipt(
    userRemovedReceipt: Boolean,
    currentBillReceiptPath: String?,
    batchReceiptPath: String?
): Boolean = !userRemovedReceipt && currentBillReceiptPath.isNullOrBlank() && !batchReceiptPath.isNullOrBlank()

/**
 * Immutable review state for an editable allocation row.
 */
data class AllocationDraft(
    val id: String,
    val partyId: String = "",
    val partyName: String,
    val amountStr: String,
    val isSettledNow: Boolean = false,
    val refNo: Long = 0L,
    val existingSettlements: List<Settlement> = emptyList(),
    val isCurrentUser: Boolean = false
) {
    fun activeSettledMinor(currency: String): Long = existingSettlements
        .asSequence()
        .filter { it.voidedAt == null }
        .fold(0L) { acc, settlement ->
            saturatingAddMinor(acc, safeNonNegativeMinor(settlement.amount, currency))
        }

    fun activeSettledAmount(currency: String): Double =
        Money.minorToDouble(activeSettledMinor(currency), currency)

    fun effectiveSettledFloorMinor(currency: String): Long =
        if (isSettledNow) activeSettledMinor(currency) else 0L

    fun effectiveSettledFloor(currency: String): Double =
        Money.minorToDouble(effectiveSettledFloorMinor(currency), currency)
}

/**
 * Pure Kotlin snapshot of a review draft item for testing and batch confirmation.
 */
data class ConfirmedDraftItem(
    val originalId: String,
    val currency: String,
    val title: String,
    val amountStr: String,
    val category: ExpenseCategory,
    val payer: Payer,
    val allocations: List<AllocationDraft> = emptyList(),
    val initialTimestamp: Long = 0L,
    val initialRecordType: ExpenseRecordType = ExpenseRecordType.PERSONAL,
    val initialSplitGroupId: String? = null,
    val initialProofType: ProofType = ProofType.NONE,
    val initialProofDetails: BankSmsProof? = null,
    val initialRefNo: Long = 0L,
    val initialMyShare: Double? = null,
    val initialSplit: ExpenseSplit? = null
)

/**
 * Review state for an individual expense item in the batch.
 */
class ExpenseItemDraft(
    val originalId: String,
    val currency: String,
    initialTitle: String,
    initialAmountStr: String,
    initialCategory: ExpenseCategory,
    initialPayer: Payer,
    initialAllocations: List<AllocationDraft> = emptyList(),
    initialReceiptPhotoPath: String? = null,
    val initialTimestamp: Long = 0L,
    val initialRecordType: ExpenseRecordType = ExpenseRecordType.PERSONAL,
    val initialSplitGroupId: String? = null,
    val initialProofType: ProofType = ProofType.NONE,
    val initialProofDetails: BankSmsProof? = null,
    val initialRefNo: Long = 0L,
    val initialMyShare: Double? = null,
    val initialSplit: ExpenseSplit? = null
) {
    var title by mutableStateOf(initialTitle)
    var amountStr by mutableStateOf(initialAmountStr)
    var category by mutableStateOf(initialCategory)
    var payer by mutableStateOf(initialPayer)
    var receiptPhotoPath by mutableStateOf(initialReceiptPhotoPath)
    val allocations = mutableStateListOf<AllocationDraft>().apply {
        addAll(initialAllocations)
    }

    val totalAmount: Double
        get() = (parseLocalizedAmount(amountStr) ?: 0.0).coerceAtLeast(0.0)

    val sumAllocations: Double
        get() {
            val minorSum = allocations.fold(0L) { acc, alloc ->
                val v = (parseLocalizedAmount(alloc.amountStr) ?: 0.0).coerceAtLeast(0.0)
                saturatingAddMinor(acc, safeNonNegativeMinor(v, currency))
            }
            return Money.minorToDouble(minorSum, currency)
        }

    val myShare: Double
        get() {
            if (!payer.isCurrentUser) {
                val selfAllocs = allocations.filter { it.isCurrentUser || it.partyId == "self" }
                return if (selfAllocs.isNotEmpty()) {
                    val selfMinor = selfAllocs.fold(0L) { acc, alloc ->
                        val v = (parseLocalizedAmount(alloc.amountStr) ?: 0.0).coerceAtLeast(0.0)
                        saturatingAddMinor(acc, safeNonNegativeMinor(v, currency))
                    }
                    Money.minorToDouble(selfMinor, currency).coerceAtMost(totalAmount)
                } else if (allocations.isEmpty()) {
                    (initialMyShare ?: initialSplit?.amountPerPerson ?: totalAmount).coerceIn(0.0, totalAmount)
                } else {
                    (initialMyShare ?: SplitCalculator.computeAutoBalancedSplit(
                        totalAmount = totalAmount,
                        currency = currency,
                        partyShares = allocations.map { (parseLocalizedAmount(it.amountStr) ?: 0.0).coerceAtLeast(0.0) }
                    ).personalShare).coerceIn(0.0, totalAmount)
                }
            }
            return SplitCalculator.computeAutoBalancedSplit(
                totalAmount = totalAmount,
                currency = currency,
                partyShares = allocations.map { (parseLocalizedAmount(it.amountStr) ?: 0.0).coerceAtLeast(0.0) }
            ).personalShare
        }

    val totalActiveSettledMinor: Long
        get() = allocations.fold(0L) { acc, alloc ->
            saturatingAddMinor(acc, alloc.effectiveSettledFloorMinor(currency))
        }

    val hasSettledFloorViolation: Boolean
        get() {
            val totalMinor = safeNonNegativeMinor(totalAmount, currency)
            if (totalActiveSettledMinor > totalMinor) return true
            return allocations.any { alloc ->
                val floorMinor = alloc.effectiveSettledFloorMinor(currency)
                if (floorMinor <= 0L) {
                    false
                } else {
                    val v = (parseLocalizedAmount(alloc.amountStr) ?: 0.0).coerceAtLeast(0.0)
                    safeNonNegativeMinor(v, currency) < floorMinor
                }
            }
        }

    val isOverAllocated: Boolean
        get() {
            val totalMinor = safeNonNegativeMinor(totalAmount, currency)
            val allocMinor = allocations.fold(0L) { acc, alloc ->
                val v = (parseLocalizedAmount(alloc.amountStr) ?: 0.0).coerceAtLeast(0.0)
                saturatingAddMinor(acc, safeNonNegativeMinor(v, currency))
            }
            return allocMinor > totalMinor
        }

    fun toConfirmedDraftItem(): ConfirmedDraftItem = ConfirmedDraftItem(
        originalId = originalId,
        currency = currency,
        title = title,
        amountStr = amountStr,
        category = category,
        payer = payer,
        allocations = allocations.toList(),
        initialTimestamp = initialTimestamp,
        initialRecordType = initialRecordType,
        initialSplitGroupId = initialSplitGroupId,
        initialProofType = initialProofType,
        initialProofDetails = initialProofDetails,
        initialRefNo = initialRefNo,
        initialMyShare = initialMyShare,
        initialSplit = initialSplit
    )
}

fun ExpenseItem.toExpenseItemDraft(): ExpenseItemDraft {
    val allocDrafts = split?.allocations?.map { alloc ->
        AllocationDraft(
            id = alloc.id,
            partyId = alloc.party.id,
            partyName = alloc.party.name,
            amountStr = if (alloc.amount <= 0.0) "" else formatDraftAmount(alloc.amount, currency),
            isSettledNow = alloc.settlements.any { it.voidedAt == null },
            refNo = alloc.refNo,
            existingSettlements = alloc.settlements,
            isCurrentUser = alloc.party.isCurrentUser
        )
    } ?: emptyList()

    val amtStr = if (amount <= 0.0) "" else formatDraftAmount(amount, currency)
    return ExpenseItemDraft(
        originalId = id,
        currency = currency,
        initialTitle = title,
        initialAmountStr = amtStr,
        initialCategory = category,
        initialPayer = payer,
        initialAllocations = allocDrafts,
        initialReceiptPhotoPath = receiptPhotoPath,
        initialTimestamp = timestamp,
        initialRecordType = recordType,
        initialSplitGroupId = splitGroupId,
        initialProofType = proofType,
        initialProofDetails = proofDetails,
        initialRefNo = refNo,
        initialMyShare = myShare,
        initialSplit = split
    )
}

/**
 * Keeps the raw spoken text section visible whenever it had initial text, currently has text,
 * or the user is actively editing it (preventing mid-edit collapse when cleared to "").
 */
fun shouldShowRawTextSection(
    hasInitialRawText: Boolean,
    currentRawText: String,
    isEditing: Boolean
): Boolean = hasInitialRawText || currentRawText.isNotBlank() || isEditing

/**
 * Guards against re-parsing a saved expense batch ("batch_edit_*"), which would generate new item IDs
 * and risk duplicating the saved DB record on ConfirmSplitBatch.
 */
fun isEditingSavedBatch(batchId: String): Boolean = batchId.startsWith("batch_edit_")

/**
 * Structural key of batch items ignoring rawSpokenText so UpdatePendingRawText never wipes draft edits.
 */
fun structuralItemsSignature(items: List<ExpenseItem>): List<ExpenseItem> =
    items.map { it.copy(rawSpokenText = "") }

data class PartyShareInputResult(
    val amountStr: String,
    val clampedAmount: Double,
    val wasClamped: Boolean,
    val maxAllowed: Double
)

/**
 * Applies real-time clamping to a single external party's share input using SplitCalculator
 * while respecting an optional already-settled minimum floor.
 * Preserves intermediate decimal states (e.g. "", "12.") when minFloor == 0.0.
 */
fun applyPartyShareInput(
    totalAmount: Double,
    currency: String,
    currentPartyShares: List<String>,
    targetIndex: Int,
    requestedInput: String,
    minFloor: Double = 0.0
): PartyShareInputResult {
    val otherShares = currentPartyShares.mapIndexedNotNull { idx, raw ->
        if (idx == targetIndex) null
        else (parseLocalizedAmount(raw) ?: 0.0).coerceAtLeast(0.0)
    }
    val zeroClamp = SplitCalculator.clampPartyShare(totalAmount, currency, otherShares, 0.0)
    val maxAllowedMinor = safeNonNegativeMinor(zeroClamp.maxAllowed, currency)
    val floorMinor = safeNonNegativeMinor(minFloor, currency).coerceAtMost(maxAllowedMinor)
    val floorAmount = Money.minorToDouble(floorMinor, currency)

    if (requestedInput.contains('-')) {
        return PartyShareInputResult(
            amountStr = formatDraftAmount(floorAmount, currency),
            clampedAmount = floorAmount,
            wasClamped = true,
            maxAllowed = zeroClamp.maxAllowed
        )
    }

    val sanitized = sanitizeLocalizedDecimalInput(requestedInput)
    if (sanitized.isEmpty()) {
        return if (minFloor > 0.0 && floorMinor > 0L) {
            PartyShareInputResult(
                amountStr = formatDraftAmount(floorAmount, currency),
                clampedAmount = floorAmount,
                wasClamped = true,
                maxAllowed = zeroClamp.maxAllowed
            )
        } else {
            PartyShareInputResult(
                amountStr = "",
                clampedAmount = 0.0,
                wasClamped = false,
                maxAllowed = zeroClamp.maxAllowed
            )
        }
    }

    val parsed = parseLocalizedAmount(sanitized)
    if (parsed == null) {
        return if (minFloor > 0.0 && floorMinor > 0L) {
            PartyShareInputResult(
                amountStr = formatDraftAmount(floorAmount, currency),
                clampedAmount = floorAmount,
                wasClamped = true,
                maxAllowed = zeroClamp.maxAllowed
            )
        } else {
            PartyShareInputResult(
                amountStr = sanitized,
                clampedAmount = 0.0,
                wasClamped = false,
                maxAllowed = zeroClamp.maxAllowed
            )
        }
    }

    val clamp = SplitCalculator.clampPartyShare(totalAmount, currency, otherShares, parsed)
    val clampedMinor = safeNonNegativeMinor(clamp.clampedAmount, currency)
    return when {
        clampedMinor < floorMinor -> PartyShareInputResult(
            amountStr = formatDraftAmount(floorAmount, currency),
            clampedAmount = floorAmount,
            wasClamped = true,
            maxAllowed = clamp.maxAllowed
        )
        clamp.wasClamped -> PartyShareInputResult(
            amountStr = formatDraftAmount(clamp.clampedAmount, currency),
            clampedAmount = clamp.clampedAmount,
            wasClamped = true,
            maxAllowed = clamp.maxAllowed
        )
        else -> PartyShareInputResult(
            amountStr = sanitized,
            clampedAmount = clamp.clampedAmount,
            wasClamped = false,
            maxAllowed = clamp.maxAllowed
        )
    }
}

data class BillTotalInputResult(
    val amountStr: String,
    val partyShares: List<String>,
    val anyClamped: Boolean,
    val belowSettledFloor: Boolean = false
)

/**
 * Clamps external party shares down when a positive bill total shrinks below their sum,
 * never shrinking any share below its already-settled floor.
 * Leaves party shares untouched while the total input is blank or <= 0 (e.g., mid-retype "" -> "150").
 */
fun applyBillTotalInput(
    newTotalInput: String,
    currency: String,
    currentPartyShares: List<String>,
    minPartyFloors: List<Double> = emptyList()
): BillTotalInputResult {
    val sanitized = sanitizeLocalizedDecimalInput(newTotalInput)
    val parsedTotal = parseLocalizedAmount(sanitized)
    if (parsedTotal == null || parsedTotal <= 0.0) {
        return BillTotalInputResult(
            amountStr = sanitized,
            partyShares = currentPartyShares,
            anyClamped = false,
            belowSettledFloor = false
        )
    }

    val numericShares = currentPartyShares.map { (parseLocalizedAmount(it) ?: 0.0).coerceAtLeast(0.0) }
    val totalMinor = safeNonNegativeMinor(parsedTotal, currency)
    val floorMinors = currentPartyShares.indices.map { idx ->
        safeNonNegativeMinor(minPartyFloors.getOrElse(idx) { 0.0 }, currency)
    }
    val totalFloorMinor = floorMinors.fold(0L) { acc, f -> saturatingAddMinor(acc, f) }
    val belowFloor = totalMinor < totalFloorMinor
    var remainingUnsettledBudget = (totalMinor - totalFloorMinor).coerceAtLeast(0L)

    var anyClamped = false
    val updatedStrings = currentPartyShares.mapIndexed { idx, originalStr ->
        val rawMinor = safeNonNegativeMinor(numericShares[idx], currency)
        val floorMinor = floorMinors[idx]
        val excessMinor = (rawMinor - floorMinor).coerceAtLeast(0L)
        val allowedExcess = excessMinor.coerceAtMost(remainingUnsettledBudget)
        remainingUnsettledBudget -= allowedExcess
        val finalMinor = saturatingAddMinor(floorMinor, allowedExcess)
        if (finalMinor != rawMinor) {
            anyClamped = true
            formatDraftAmount(Money.minorToDouble(finalMinor, currency), currency)
        } else {
            originalStr
        }
    }
    return BillTotalInputResult(
        amountStr = sanitized,
        partyShares = updatedStrings,
        anyClamped = anyClamped,
        belowSettledFloor = belowFloor
    )
}

data class SplitBarSegment(
    val label: String,
    val amount: Double,
    val ratio: Float,
    val colorIndex: Int,
    val isPersonal: Boolean
)

fun buildSplitBarSegments(
    totalAmount: Double,
    currency: String,
    partyEntries: List<Pair<String, Double>>
): Pair<AutoSplitSummary, List<SplitBarSegment>> {
    val summary = SplitCalculator.computeAutoBalancedSplit(
        totalAmount = totalAmount,
        currency = currency,
        partyShares = partyEntries.map { it.second }
    )
    val segments = buildList {
        if (summary.personalShare > 0.0 || partyEntries.isEmpty()) {
            add(
                SplitBarSegment(
                    label = "حصتي",
                    amount = summary.personalShare,
                    ratio = summary.personalRatio,
                    colorIndex = -1,
                    isPersonal = true
                )
            )
        }
        partyEntries.forEachIndexed { idx, (rawName, _) ->
            add(
                SplitBarSegment(
                    label = rawName.trim().ifBlank { "طرف ${idx + 1}" },
                    amount = summary.partyShares.getOrElse(idx) { 0.0 },
                    ratio = summary.partyRatios.getOrElse(idx) { 0f },
                    colorIndex = idx,
                    isPersonal = false
                )
            )
        }
    }
    return summary to segments
}

/**
 * Tracks bill receipt URIs within an editing session so that only newly picked unsaved sandbox files
 * are deleted on remove or dismiss, NEVER deleting a pre-existing persisted receipt URI that may
 * be referenced by sibling split records in storage.
 */
class ReceiptSessionTracker(
    initialPersistedPath: String?
) {
    val initialPersistedUri: String? = initialPersistedPath?.takeIf { it.isNotBlank() }
    var currentPath: String? = initialPersistedUri
        private set
    private val sessionPickedPaths = mutableSetOf<String>()

    fun onReceiptPicked(newPath: String): List<String> {
        val normalized = newPath.takeIf { it.isNotBlank() } ?: return emptyList()
        val staleToDelete = sessionPickedPaths.filter { it != normalized && it != initialPersistedUri }
        sessionPickedPaths.removeAll(staleToDelete.toSet())
        if (normalized != initialPersistedUri) {
            sessionPickedPaths.add(normalized)
        }
        currentPath = normalized
        return staleToDelete
    }

    fun syncExternalPath(path: String?) {
        val normalized = path?.takeIf { it.isNotBlank() }
        if (normalized != null && currentPath == null) {
            currentPath = normalized
        }
    }

    fun onRemoveReceipt(): List<String> {
        currentPath = null
        val toDelete = sessionPickedPaths.filter { it != initialPersistedUri }
        sessionPickedPaths.clear()
        return toDelete
    }

    fun onConfirmSaved(): List<String> {
        val staleToDelete = sessionPickedPaths.filter { it != currentPath && it != initialPersistedUri }
        sessionPickedPaths.removeAll(staleToDelete.toSet())
        return staleToDelete
    }

    fun onDismissWithoutSave(): List<String> {
        val toDelete = sessionPickedPaths.filter { it != initialPersistedUri }
        sessionPickedPaths.clear()
        return toDelete
    }
}

/**
 * Builds the confirmed ExpenseBatch from draft inputs, assigning a single bill-level receipt URI
 * identically across all items (and setting ProofType.RECEIPT_PHOTO cleanly) while preserving
 * existing record metadata, party IDs, PAYABLE/external-payer semantics, and settled-amount floors.
 */
fun buildConfirmedBatch(
    originalBatch: ExpenseBatch,
    drafts: List<ConfirmedDraftItem>,
    rawSpokenText: String,
    billReceiptPath: String?,
    nowEpochMs: Long
): ExpenseBatch {
    val isEditing = isEditingSavedBatch(originalBatch.batchId)
    val normalizedReceipt = billReceiptPath?.takeIf { it.isNotBlank() }

    val updatedItems = drafts.map { draft ->
        val totalAmt = (parseLocalizedAmount(draft.amountStr) ?: 0.0).coerceAtLeast(0.0)
        val rawTotalMinor = safeNonNegativeMinor(totalAmt, draft.currency)

        data class ValidAllocEntry(
            val allocDraft: AllocationDraft,
            val requestedMinor: Long,
            val settledFloorMinor: Long,
            val activeExisting: List<Settlement>,
            val voidedExisting: List<Settlement>
        )

        val validAllocInputs = draft.allocations.mapNotNull { allocDraft ->
            if (allocDraft.partyName.isBlank()) return@mapNotNull null
            val activeExisting = allocDraft.existingSettlements.filter { it.voidedAt == null }
            val voidedExisting = allocDraft.existingSettlements.filter { it.voidedAt != null }
            val settledFloorMinor = allocDraft.effectiveSettledFloorMinor(draft.currency)
            val rawAmt = parseLocalizedAmount(allocDraft.amountStr)
            val rawMinor = if (rawAmt != null && rawAmt > 0.0) safeNonNegativeMinor(rawAmt, draft.currency) else 0L
            val effectiveReqMinor = maxOf(rawMinor, settledFloorMinor)
            if (effectiveReqMinor > 0L) {
                ValidAllocEntry(
                    allocDraft = allocDraft,
                    requestedMinor = effectiveReqMinor,
                    settledFloorMinor = settledFloorMinor,
                    activeExisting = activeExisting,
                    voidedExisting = voidedExisting
                )
            } else {
                null
            }
        }

        val totalSettledFloorMinor = validAllocInputs.fold(0L) { acc, entry ->
            saturatingAddMinor(acc, entry.settledFloorMinor)
        }
        val effectiveTotalMinor = maxOf(rawTotalMinor, totalSettledFloorMinor)
        val effectiveTotalDouble = Money.minorToDouble(effectiveTotalMinor, draft.currency)

        var remainingExcessBudget = (effectiveTotalMinor - totalSettledFloorMinor).coerceAtLeast(0L)
        val finalMinors = validAllocInputs.map { entry ->
            val excessMinor = (entry.requestedMinor - entry.settledFloorMinor).coerceAtLeast(0L)
            val allowedExcess = excessMinor.coerceAtMost(remainingExcessBudget)
            remainingExcessBudget -= allowedExcess
            saturatingAddMinor(entry.settledFloorMinor, allowedExcess)
        }

        val sumAllocsMinor = finalMinors.fold(0L) { acc, m -> saturatingAddMinor(acc, m) }
        val personalMinor = (effectiveTotalMinor - sumAllocsMinor).coerceAtLeast(0L)
        val personalShare = Money.minorToDouble(personalMinor, draft.currency)

        val finalAllocations = validAllocInputs.mapIndexed { idx, entry ->
            val allocDraft = entry.allocDraft
            val clampedAmt = Money.minorToDouble(finalMinors[idx], draft.currency)
            val trimmedName = allocDraft.partyName.trim()
            val resolvedPartyId = allocDraft.partyId.ifBlank {
                if (allocDraft.isCurrentUser) "self" else trimmedName
            }
            val party = Payer(
                id = resolvedPartyId,
                name = trimmedName,
                isCurrentUser = allocDraft.isCurrentUser
            )

            val settlements = if (allocDraft.isSettledNow) {
                if (entry.activeExisting.isNotEmpty()) {
                    allocDraft.existingSettlements
                } else {
                    entry.voidedExisting + Settlement(
                        id = "settle_${nowEpochMs}_${ReceivablesLedger.nextSettlementSeq()}",
                        refNo = ReceivablesLedger.nextRefNo(),
                        allocationId = allocDraft.id,
                        amount = clampedAmt,
                        settledAt = nowEpochMs,
                        recordedAt = nowEpochMs,
                        note = "سُدّد عند التسجيل"
                    )
                }
            } else {
                entry.voidedExisting
            }

            Allocation(
                id = allocDraft.id,
                refNo = if (allocDraft.refNo > 0L) allocDraft.refNo else ReceivablesLedger.nextRefNo(),
                party = party,
                amount = clampedAmt,
                settlements = settlements,
                note = if (allocDraft.isCurrentUser && !draft.payer.isCurrentUser) {
                    "دفع ${draft.payer.name} عني"
                } else {
                    "مخصص لـ $trimmedName"
                }
            )
        }

        val isExternalPayer = !draft.payer.isCurrentUser
        val (split, resolvedMyShare) = if (finalAllocations.isNotEmpty()) {
            if (isExternalPayer) {
                val hasSelfAlloc = finalAllocations.any { it.party.isCurrentUser || it.party.id == "self" }
                val externalMyShare = if (hasSelfAlloc) {
                    val selfMinor = finalAllocations
                        .filter { it.party.isCurrentUser || it.party.id == "self" }
                        .fold(0L) { acc, alloc ->
                            saturatingAddMinor(acc, safeNonNegativeMinor(alloc.amount, draft.currency))
                        }
                    Money.minorToDouble(selfMinor, draft.currency).coerceAtMost(effectiveTotalDouble)
                } else {
                    (draft.initialMyShare ?: personalShare).coerceIn(0.0, effectiveTotalDouble)
                }
                val participants = finalAllocations.map { it.party }
                    .distinctBy { it.id.ifBlank { it.name } }
                val builtSplit = ExpenseSplit(
                    totalAmount = effectiveTotalDouble,
                    payer = draft.payer,
                    participants = participants,
                    amountPerPerson = externalMyShare,
                    note = draft.initialSplit?.note?.takeIf { it.isNotBlank() } ?: "دفع ${draft.payer.name} عني",
                    allocations = finalAllocations
                )
                builtSplit to externalMyShare
            } else {
                val builtSplit = ExpenseSplit(
                    totalAmount = effectiveTotalDouble,
                    payer = draft.payer,
                    participants = listOf(Payer("self", "أنا", true)) + finalAllocations.map { it.party },
                    amountPerPerson = personalShare,
                    note = "توزيع مستحقات",
                    allocations = finalAllocations
                )
                builtSplit to personalShare
            }
        } else if (isExternalPayer && (draft.initialSplit != null || draft.initialRecordType == ExpenseRecordType.PAYABLE)) {
            val preservedMyShare = (draft.initialMyShare
                ?: draft.initialSplit?.amountPerPerson
                ?: effectiveTotalDouble).coerceIn(0.0, effectiveTotalDouble)
            val preservedSplit = (draft.initialSplit ?: ExpenseSplit(
                totalAmount = effectiveTotalDouble,
                payer = draft.payer,
                participants = listOf(draft.payer, Payer("self", "أنا", true)),
                amountPerPerson = preservedMyShare,
                note = "على حساب ${draft.payer.name}"
            )).copy(
                totalAmount = effectiveTotalDouble,
                payer = draft.payer,
                amountPerPerson = preservedMyShare,
                allocations = emptyList()
            )
            preservedSplit to preservedMyShare
        } else {
            null to null
        }

        val resolvedRecordType = if (isExternalPayer && split != null) {
            ExpenseRecordType.PAYABLE
        } else {
            draft.initialRecordType
        }

        val resolvedProofType = when {
            normalizedReceipt != null && draft.initialProofType == ProofType.NONE -> ProofType.RECEIPT_PHOTO
            normalizedReceipt == null && draft.initialProofType == ProofType.RECEIPT_PHOTO -> ProofType.NONE
            else -> draft.initialProofType
        }

        val resolvedTimestamp = if (isEditing && draft.initialTimestamp > 0L) {
            draft.initialTimestamp
        } else {
            nowEpochMs
        }

        ExpenseItem(
            id = draft.originalId,
            title = draft.title.ifBlank { "مصروف" },
            amount = effectiveTotalDouble,
            currency = draft.currency,
            category = draft.category,
            timestamp = resolvedTimestamp,
            payer = draft.payer,
            split = split,
            proofType = resolvedProofType,
            proofDetails = draft.initialProofDetails,
            rawSpokenText = rawSpokenText,
            myShare = resolvedMyShare,
            receiptPhotoPath = normalizedReceipt,
            refNo = draft.initialRefNo,
            recordType = resolvedRecordType,
            splitGroupId = draft.initialSplitGroupId
        )
    }

    return originalBatch.copy(
        items = updatedItems,
        totalBatchAmount = updatedItems.sumOf { it.amount }
    )
}

