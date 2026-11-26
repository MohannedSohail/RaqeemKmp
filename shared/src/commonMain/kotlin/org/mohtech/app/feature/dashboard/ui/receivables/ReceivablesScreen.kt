package org.mohtech.app.feature.dashboard.ui.receivables

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.time.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.mohtech.app.core.haptics.HapticKind
import org.mohtech.app.core.haptics.LocalRaqeemHaptics
import org.mohtech.app.core.theme.RaqeemColors
import org.mohtech.app.core.ui.BackHandler
import org.mohtech.app.core.ui.RaqeemIcons
import org.mohtech.app.data.accounting.AllocationComputation
import org.mohtech.app.data.accounting.Money
import org.mohtech.app.data.accounting.PartyBalanceSummary
import org.mohtech.app.data.accounting.ReceivablesLedger
import org.mohtech.app.data.model.DebtStatus
import org.mohtech.app.data.model.ExpenseItem
import org.mohtech.app.feature.dashboard.ExpenseIntent
import org.mohtech.app.feature.dashboard.ExpenseUiState
import org.mohtech.app.feature.dashboard.ExpenseViewModel
import org.mohtech.app.feature.dashboard.ui.ReceiptPreviewDialog
import org.mohtech.app.feature.dashboard.ui.formatDraftAmount
import org.mohtech.app.feature.dashboard.ui.parseLocalizedAmount
import org.mohtech.app.feature.reports.AccountingReportUtil

enum class ReceivablesFilter(val title: String) {
    ALL("الكل"),
    OPEN("قيد التحصيل"),
    SETTLED("مسدد بالكامل")
}

/**
 * Currency-scoped party selection key so multi-currency balances for the same party
 * remain strictly isolated across PartyDetailView and SettleAllDialog.
 */
data class SelectedPartyTarget(
    val partyId: String,
    val partyName: String,
    val currency: String
)

private val SelectedPartyTargetSaver = listSaver<SelectedPartyTarget?, String>(
    save = { target ->
        if (target == null) emptyList() else listOf(target.partyId, target.partyName, target.currency)
    },
    restore = { list ->
        if (list.size == 3) SelectedPartyTarget(list[0], list[1], list[2]) else null
    }
)

internal fun AllocationComputation.matchesPartyTarget(target: SelectedPartyTarget): Boolean {
    if (currency != target.currency) return false
    val compKey = party.id.ifBlank { party.name }
    val targetKey = target.partyId.ifBlank { target.partyName }
    return compKey == targetKey || (party.name == target.partyName && target.partyId.isBlank())
}

fun filterComputationsForParty(
    expenses: List<ExpenseItem>,
    target: SelectedPartyTarget
): List<AllocationComputation> = ReceivablesLedger.getAllocationComputations(expenses)
    .filter { it.matchesPartyTarget(target) }

fun computePartyRemainingForTarget(
    computations: List<AllocationComputation>,
    target: SelectedPartyTarget
): Double {
    val minorSum = computations
        .filter { it.isOwedToMe && it.matchesPartyTarget(target) }
        .sumOf { Money.doubleToMinor(it.remainingAmount, it.currency) }
    return Money.minorToDouble(minorSum, target.currency)
}

fun filterOpenSettleAllComputations(
    expenses: List<ExpenseItem>,
    target: SelectedPartyTarget
): List<AllocationComputation> = ReceivablesLedger.getAllocationComputations(expenses)
    .filter { it.matchesPartyTarget(target) && it.remainingAmount > 0.0 && it.isOwedToMe }

/**
 * ReceivablesScreen:
 * Comprehensive, 100% offline personal debt and shared expense collection manager.
 * UnSlop Principles:
 * - Single bright accent: EmeraldGreen #00C48C.
 * - Hairline 1dp borders @ 8% opacity.
 * - Pure Cairo typography in RTL direction.
 * - Zero emoji or text symbols.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceivablesScreen(
    state: ExpenseUiState,
    viewModel: ExpenseViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalRaqeemHaptics.current
    var selectedParty by rememberSaveable(stateSaver = SelectedPartyTargetSaver) {
        mutableStateOf<SelectedPartyTarget?>(null)
    }
    var activeFilter by rememberSaveable { mutableStateOf(ReceivablesFilter.ALL) }

    // Settlement Bottom Sheet state
    var pendingSettlementTarget by remember { mutableStateOf<AllocationComputation?>(null) }
    var voidSettlementTarget by remember { mutableStateOf<Pair<String, String>?>(null) } // expenseId, settlementId
    var showSettleAllConfirm by remember { mutableStateOf(false) }
    var previewReceiptUri by remember { mutableStateOf<String?>(null) }

    // Intercept back button: if a person detail is open, return to list; otherwise exit screen
    BackHandler(enabled = true) {
        if (selectedParty != null) {
            selectedParty = null
        } else {
            onBack()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(RaqeemColors.BackgroundSlate)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top Bar
            ReceivablesTopBar(
                selectedParty = selectedParty,
                onBack = {
                    if (selectedParty != null) {
                        selectedParty = null
                    } else {
                        onBack()
                    }
                },
                onExportExcel = {
                    viewModel.handleIntent(ExpenseIntent.ExportReceivablesExcel)
                }
            )

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.08f),
                thickness = 1.dp
            )

            val currentParty = selectedParty
            if (currentParty == null) {
                // View 1: List of all debtors / shared expense parties
                PartiesListView(
                    expenses = state.receivableExpenses,
                    activeCurrency = state.activeCurrency,
                    onSelectParty = { target ->
                        haptics.perform(HapticKind.TICK)
                        selectedParty = target
                    }
                )
            } else {
                // View 2: Detailed person allocations list (strictly scoped by party & currency)
                PartyDetailView(
                    selectedParty = currentParty,
                    expenses = state.receivableExpenses,
                    activeFilter = activeFilter,
                    onFilterChange = { activeFilter = it },
                    onCheckSettlement = { comp ->
                        haptics.perform(HapticKind.TICK)
                        pendingSettlementTarget = comp
                    },
                    onUncheckSettlement = { expenseId, settlementId ->
                        haptics.perform(HapticKind.TICK)
                        voidSettlementTarget = Pair(expenseId, settlementId)
                    },
                    onRequestSettleAll = {
                        haptics.perform(HapticKind.TICK)
                        showSettleAllConfirm = true
                    },
                    onViewReceipt = { uri ->
                        previewReceiptUri = uri
                    }
                )
            }
        }

        // Bottom Sheet: Record Settlement
        pendingSettlementTarget?.let { comp ->
            RecordSettlementBottomSheet(
                target = comp,
                currency = comp.currency,
                onConfirm = { amount, settledAt, note ->
                    viewModel.handleIntent(
                        ExpenseIntent.RecordSettlement(
                            expenseId = comp.expenseId,
                            allocationId = comp.allocationId,
                            amount = amount,
                            settledAt = settledAt,
                            note = note
                        )
                    )
                    pendingSettlementTarget = null
                },
                onDismiss = { pendingSettlementTarget = null }
            )
        }

        // Void Settlement Confirmation Dialog
        voidSettlementTarget?.let { (expenseId, settlementId) ->
            VoidSettlementDialog(
                onConfirm = {
                    viewModel.handleIntent(
                        ExpenseIntent.VoidSettlement(
                            expenseId = expenseId,
                            settlementId = settlementId
                        )
                    )
                    voidSettlementTarget = null
                },
                onDismiss = { voidSettlementTarget = null }
            )
        }

        // Settle All Confirmation Dialog (strictly scoped to selected party + currency)
        val settleAllParty = selectedParty
        if (showSettleAllConfirm && settleAllParty != null) {
            SettleAllDialog(
                selectedParty = settleAllParty,
                onConfirm = {
                    val openItems = filterOpenSettleAllComputations(state.receivableExpenses, settleAllParty)
                    val now = Clock.System.now().toEpochMilliseconds()

                    openItems.forEach { item ->
                        viewModel.handleIntent(
                            ExpenseIntent.RecordSettlement(
                                expenseId = item.expenseId,
                                allocationId = item.allocationId,
                                amount = item.remainingAmount,
                                settledAt = now,
                                note = "تسديد جماعي"
                            )
                        )
                    }
                    showSettleAllConfirm = false
                },
                onDismiss = { showSettleAllConfirm = false }
            )
        }

        previewReceiptUri?.let { uri ->
            ReceiptPreviewDialog(
                fileUri = uri,
                onDismiss = { previewReceiptUri = null }
            )
        }
    }
}

@Composable
private fun ReceivablesTopBar(
    selectedParty: SelectedPartyTarget?,
    onBack: () -> Unit,
    onExportExcel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(RaqeemColors.SurfaceCard)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                RaqeemIcons.Back(size = 18.dp, tint = RaqeemColors.TextPrimary)
            }

            Text(
                text = if (selectedParty != null) {
                    "مستحقات ${selectedParty.partyName} (${selectedParty.currency})"
                } else {
                    "سجل المستحقات والديون"
                },
                color = RaqeemColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Export Excel Button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(RaqeemColors.SurfaceCard)
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                .clickable(onClick = onExportExcel)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RaqeemIcons.Share(size = 15.dp, tint = RaqeemColors.EmeraldGreen)
                Text(
                    text = "تصدير XLSX",
                    color = RaqeemColors.EmeraldGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun PartiesListView(
    expenses: List<ExpenseItem>,
    activeCurrency: String,
    onSelectParty: (SelectedPartyTarget) -> Unit
) {
    val summaries = remember(expenses) {
        ReceivablesLedger.getPartySummaries(expenses)
    }

    if (summaries.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "لا توجد أي مستحقات أو ديون مسجلة حالياً.",
                color = RaqeemColors.TextSecondary,
                fontSize = 14.sp
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = summaries,
            key = { "${it.party.id.ifBlank { it.party.name }}_${it.party.name}_${it.currency}" },
            contentType = { "party_summary" }
        ) { sum ->
            PartySummaryCard(
                summary = sum,
                onClick = {
                    onSelectParty(
                        SelectedPartyTarget(
                            partyId = sum.party.id.ifBlank { sum.party.name },
                            partyName = sum.party.name,
                            currency = sum.currency
                        )
                    )
                }
            )
        }
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PartySummaryCard(
    summary: PartyBalanceSummary,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RaqeemColors.SurfaceCard)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = summary.party.name,
                    color = RaqeemColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                val balanceText = when {
                    summary.netBalance > 0.0 ->
                        "لك عنده ${formatDraftAmount(summary.netBalance, summary.currency)} ${summary.currency}"
                    summary.netBalance < 0.0 ->
                        "له عندك ${formatDraftAmount(-summary.netBalance, summary.currency)} ${summary.currency}"
                    else -> "الحساب متطابق (خالص)"
                }
                val balanceColor = when {
                    summary.netBalance > 0.0 -> RaqeemColors.EmeraldGreen
                    summary.netBalance < 0.0 -> RaqeemColors.CoralRose
                    else -> RaqeemColors.TextTertiary
                }
                Text(
                    text = balanceText,
                    color = balanceColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "المسدد: ${formatDraftAmount(summary.totalSettledToMe, summary.currency)} ${summary.currency}",
                    color = RaqeemColors.TextSecondary,
                    fontSize = 12.sp
                )
                Text(
                    text = "من إجمالي: ${formatDraftAmount(summary.totalReceivable, summary.currency)} ${summary.currency}",
                    color = RaqeemColors.TextTertiary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun PartyDetailView(
    selectedParty: SelectedPartyTarget,
    expenses: List<ExpenseItem>,
    activeFilter: ReceivablesFilter,
    onFilterChange: (ReceivablesFilter) -> Unit,
    onCheckSettlement: (AllocationComputation) -> Unit,
    onUncheckSettlement: (expenseId: String, settlementId: String) -> Unit,
    onRequestSettleAll: () -> Unit,
    onViewReceipt: (String) -> Unit
) {
    val expensesById = remember(expenses) { expenses.associateBy { it.id } }

    val allPartyComputations = remember(expenses, selectedParty) {
        filterComputationsForParty(expenses, selectedParty)
    }

    val filtered = remember(allPartyComputations, activeFilter) {
        when (activeFilter) {
            ReceivablesFilter.ALL -> allPartyComputations
            ReceivablesFilter.OPEN -> allPartyComputations.filter { it.status != DebtStatus.SETTLED }
            ReceivablesFilter.SETTLED -> allPartyComputations.filter { it.status == DebtStatus.SETTLED }
        }
    }

    val targetCurrency = selectedParty.currency
    val totalRemaining = remember(allPartyComputations, selectedParty) {
        computePartyRemainingForTarget(allPartyComputations, selectedParty)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Person Header Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(RaqeemColors.SurfaceElevated)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "إجمالي المتبقي للتحصيل",
                            color = RaqeemColors.TextSecondary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "${formatDraftAmount(totalRemaining, targetCurrency)} $targetCurrency",
                            color = if (totalRemaining > 0.0) RaqeemColors.EmeraldGreen else RaqeemColors.TextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (totalRemaining > 0.0) {
                        Button(
                            onClick = onRequestSettleAll,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RaqeemColors.EmeraldGreen,
                                contentColor = RaqeemColors.BackgroundSlate
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "تسديد الكل الآن",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // Filter Chips
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ReceivablesFilter.entries, key = { it.name }) { filter ->
                    val isSelected = filter == activeFilter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) RaqeemColors.EmeraldGreen.copy(alpha = 0.2f) else RaqeemColors.SurfaceCard)
                            .border(
                                1.dp,
                                if (isSelected) RaqeemColors.EmeraldGreen else Color.White.copy(alpha = 0.08f),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { onFilterChange(filter) }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = filter.title,
                            color = if (isSelected) RaqeemColors.EmeraldGreen else RaqeemColors.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "لا توجد سجلات تطابق الفلتر المحدد.",
                        color = RaqeemColors.TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            items(
                items = filtered,
                key = { "${it.expenseId}_${it.allocationId}" },
                contentType = { "allocation_detail_row" }
            ) { comp ->
                val receiptUri = expensesById[comp.expenseId]?.receiptPhotoPath?.takeIf { it.isNotBlank() }
                AllocationDetailRow(
                    computation = comp,
                    receiptPhotoPath = receiptUri,
                    onViewReceipt = onViewReceipt,
                    onCheck = { onCheckSettlement(comp) },
                    onUncheck = {
                        val activeSettlement = comp.settlements.lastOrNull { it.voidedAt == null }
                        if (activeSettlement != null) {
                            onUncheckSettlement(comp.expenseId, activeSettlement.id)
                        }
                    }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AllocationDetailRow(
    computation: AllocationComputation,
    receiptPhotoPath: String? = null,
    onViewReceipt: ((String) -> Unit)? = null,
    onCheck: () -> Unit,
    onUncheck: () -> Unit
) {
    val isSettled = computation.status == DebtStatus.SETTLED
    val formattedDate = AccountingReportUtil.formatDateWithArabicDay(computation.expenseTimestamp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(RaqeemColors.SurfaceCard)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Checkbox for settling
            Checkbox(
                checked = isSettled,
                onCheckedChange = { checked ->
                    if (checked) {
                        onCheck()
                    } else {
                        onUncheck()
                    }
                },
                colors = CheckboxDefaults.colors(
                    checkedColor = RaqeemColors.EmeraldGreen,
                    uncheckedColor = RaqeemColors.TextSecondary,
                    checkmarkColor = RaqeemColors.BackgroundSlate
                )
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = computation.expenseTitle,
                        color = RaqeemColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "#${computation.refNo.toString().padStart(6, '0')}",
                        color = RaqeemColors.TextTertiary,
                        fontSize = 11.sp
                    )
                }

                Text(
                    text = formattedDate,
                    color = RaqeemColors.TextSecondary,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "المستحق: ${formatDraftAmount(computation.totalAmount, computation.currency)} ${computation.currency} (المسدد: ${formatDraftAmount(computation.settledAmount, computation.currency)})",
                        color = RaqeemColors.TextSecondary,
                        fontSize = 12.sp
                    )

                    val statusText = when (computation.status) {
                        DebtStatus.OPEN -> "قيد التحصيل"
                        DebtStatus.PARTIAL -> "مسدد جزئياً"
                        DebtStatus.SETTLED -> "مسدد بالكامل"
                    }
                    val statusColor = when (computation.status) {
                        DebtStatus.OPEN -> RaqeemColors.CoralRose
                        DebtStatus.PARTIAL -> RaqeemColors.AmberWarning
                        DebtStatus.SETTLED -> RaqeemColors.EmeraldGreen
                    }
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (receiptPhotoPath != null) {
                    Box(
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = onViewReceipt != null) {
                                onViewReceipt?.invoke(receiptPhotoPath)
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(RaqeemColors.ElectricCyan.copy(alpha = 0.12f))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            RaqeemIcons.Receipt(size = 12.dp, tint = RaqeemColors.ElectricCyan)
                            Text(
                                text = "عرض الإيصال",
                                color = RaqeemColors.ElectricCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                if (computation.lastSettledAt != null) {
                    val settleDateStr = AccountingReportUtil.formatDateWithArabicDay(computation.lastSettledAt)
                    Text(
                        text = "آخر سداد: $settleDateStr",
                        color = RaqeemColors.ElectricCyan,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordSettlementBottomSheet(
    target: AllocationComputation,
    currency: String,
    onConfirm: (amount: Double, settledAt: Long, note: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var amountInput by rememberSaveable { mutableStateOf(formatDraftAmount(target.remainingAmount, currency)) }
    var noteInput by rememberSaveable { mutableStateOf("") }
    val now = Clock.System.now().toEpochMilliseconds()
    var settledAtTimestamp by rememberSaveable { mutableStateOf(now) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RaqeemColors.SurfaceElevated,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "تسجيل سداد مستحق",
                color = RaqeemColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "الطرف: ${target.party.name} • البند: ${target.expenseTitle}",
                color = RaqeemColors.TextSecondary,
                fontSize = 13.sp
            )

            // Amount field
            OutlinedTextField(
                value = amountInput,
                onValueChange = {
                    amountInput = it
                    errorMessage = null
                },
                label = { Text("المبلغ المسدد ($currency)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = RaqeemColors.TextPrimary,
                    unfocusedTextColor = RaqeemColors.TextPrimary,
                    focusedBorderColor = RaqeemColors.EmeraldGreen,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.12f)
                )
            )

            // Note field
            OutlinedTextField(
                value = noteInput,
                onValueChange = { noteInput = it },
                label = { Text("ملاحظة (اختياري)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = RaqeemColors.TextPrimary,
                    unfocusedTextColor = RaqeemColors.TextPrimary,
                    focusedBorderColor = RaqeemColors.EmeraldGreen,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.12f)
                )
            )

            errorMessage?.let { err ->
                Text(
                    text = err,
                    color = RaqeemColors.CoralRose,
                    fontSize = 12.sp
                )
            }

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RaqeemColors.TextSecondary)
                ) {
                    Text("إلغاء")
                }

                Button(
                    onClick = {
                        val parsed = parseLocalizedAmount(amountInput)
                        if (parsed == null || parsed <= 0.0) {
                            errorMessage = "يرجى إدخال مبلغ صحيح أكبر من صفر"
                            return@Button
                        }
                        if (parsed > target.remainingAmount) {
                            errorMessage = "المبلغ المطلوب (${formatDraftAmount(parsed, currency)}) يتجاوز المتبقي (${formatDraftAmount(target.remainingAmount, currency)})"
                            return@Button
                        }
                        onConfirm(parsed, settledAtTimestamp, noteInput)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RaqeemColors.EmeraldGreen,
                        contentColor = RaqeemColors.BackgroundSlate
                    )
                ) {
                    Text("تأكيد السداد", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun VoidSettlementDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(20.dp))
                .background(RaqeemColors.SurfaceElevated)
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                .clickable(enabled = false) {}
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "إلغاء السداد",
                color = RaqeemColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "هل تريد إلغاء هذا السداد؟ سيبقى مسجلاً في دفتر الحسابات كعملية ملغاة للتدقيق دون حذفه نهائياً.",
                color = RaqeemColors.TextSecondary,
                fontSize = 13.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RaqeemColors.TextSecondary)
                ) {
                    Text("رجوع")
                }

                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RaqeemColors.CoralRose,
                        contentColor = Color.White
                    )
                ) {
                    Text("إلغاء السداد", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SettleAllDialog(
    selectedParty: SelectedPartyTarget,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(20.dp))
                .background(RaqeemColors.SurfaceElevated)
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                .clickable(enabled = false) {}
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "تسديد كل المستحقات المفتوحة",
                color = RaqeemColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "هل أنت متأكد من تسديد كافة العمليات المفتوحة للطرف «${selectedParty.partyName} (${selectedParty.currency})» بتاريخ اليوم؟",
                color = RaqeemColors.TextSecondary,
                fontSize = 13.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RaqeemColors.TextSecondary)
                ) {
                    Text("تراجع")
                }

                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RaqeemColors.EmeraldGreen,
                        contentColor = RaqeemColors.BackgroundSlate
                    )
                ) {
                    Text("تأكيد التسديد", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

