package org.mohtech.app.feature.dashboard.ui.receivables

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.mohtech.app.core.haptics.HapticKind
import org.mohtech.app.core.haptics.LocalRaqeemHaptics
import org.mohtech.app.core.theme.RaqeemColors
import org.mohtech.app.core.ui.BackHandler
import org.mohtech.app.core.ui.RaqeemIcons
import org.mohtech.app.data.accounting.AllocationComputation
import org.mohtech.app.data.accounting.PartyBalanceSummary
import org.mohtech.app.data.accounting.ReceivablesLedger
import org.mohtech.app.data.model.DebtStatus
import org.mohtech.app.feature.dashboard.ExpenseIntent
import org.mohtech.app.feature.dashboard.ExpenseUiState
import org.mohtech.app.feature.dashboard.ExpenseViewModel

private enum class ReceivablesFilter(val title: String) {
    ALL("الكل"),
    OPEN("قيد التحصيل"),
    SETTLED("مسدّد")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceivablesScreen(
    state: ExpenseUiState,
    viewModel: ExpenseViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalRaqeemHaptics.current
    BackHandler { onClose() }

    val partySummaries = remember(state.recentExpenses) {
        ReceivablesLedger.getPartySummaries(state.recentExpenses)
    }

    var selectedFilter by rememberSaveable { mutableStateOf(ReceivablesFilter.ALL) }
    var selectedPartyId by rememberSaveable { mutableStateOf<String?>(null) }
    var settlementTargetAllocation by remember { mutableStateOf<AllocationComputation?>(null) }
    var voidTargetSettlement by remember { mutableStateOf<Pair<String, String>?>(null) } // expenseId, settlementId

    val allAllocations: List<AllocationComputation> = remember(state.recentExpenses) {
        ReceivablesLedger.getAllocationComputations(state.recentExpenses)
    }

    val filteredAllocations = remember(allAllocations, selectedFilter, selectedPartyId) {
        allAllocations.filter { alloc ->
            val matchesParty = selectedPartyId == null || alloc.party.id == selectedPartyId || alloc.party.name == selectedPartyId
            val matchesFilter = when (selectedFilter) {
                ReceivablesFilter.ALL -> true
                ReceivablesFilter.OPEN -> alloc.status != DebtStatus.SETTLED
                ReceivablesFilter.SETTLED -> alloc.status == DebtStatus.SETTLED
            }
            matchesParty && matchesFilter
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RaqeemColors.BackgroundSlate)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(RaqeemColors.SurfaceElevated)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(color = RaqeemColors.TextPrimary),
                            onClick = {
                                haptics.perform(HapticKind.TICK)
                                onClose()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    RaqeemIcons.Close(size = 14.dp, tint = RaqeemColors.TextPrimary)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "دفتر المستحقات والديون",
                        color = RaqeemColors.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "إجمالي ما لك في ذمة الآخرين: ${state.receivablesTotalRemaining} ${state.activeCurrency}",
                        color = RaqeemColors.EmeraldGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Filter Chips (All, Open, Settled)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(ReceivablesFilter.entries.toTypedArray()) { filter ->
                val isSelected = selectedFilter == filter
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) RaqeemColors.SurfaceElevated else RaqeemColors.SurfaceCard)
                        .border(
                            1.dp,
                            if (isSelected) RaqeemColors.EmeraldGreen.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.08f),
                            RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            haptics.perform(HapticKind.TICK)
                            selectedFilter = filter
                        }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = filter.title,
                        color = if (isSelected) RaqeemColors.TextPrimary else RaqeemColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }

        // Party Selector Chips
        if (partySummaries.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    val isAllSelected = selectedPartyId == null
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isAllSelected) RaqeemColors.SurfaceElevated else RaqeemColors.SurfaceCard)
                            .border(
                                1.dp,
                                if (isAllSelected) RaqeemColors.ElectricCyan.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.06f),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                haptics.perform(HapticKind.TICK)
                                selectedPartyId = null
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "جميع الأطراف (${partySummaries.size})",
                            color = if (isAllSelected) RaqeemColors.TextPrimary else RaqeemColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
                items(partySummaries) { summary ->
                    val isSelected = selectedPartyId == summary.party.id || selectedPartyId == summary.party.name
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) RaqeemColors.SurfaceElevated else RaqeemColors.SurfaceCard)
                            .border(
                                1.dp,
                                if (isSelected) RaqeemColors.ElectricCyan.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.06f),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                haptics.perform(HapticKind.TICK)
                                selectedPartyId = if (isSelected) null else summary.party.id
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${summary.party.name} (${summary.totalRemainingToMe} ${summary.currency})",
                            color = if (isSelected) RaqeemColors.ElectricCyan else RaqeemColors.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Allocation Transactions List
        if (filteredAllocations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "لا توجد مستحقات مسجلة مطابقة للمرشحات.",
                    color = RaqeemColors.TextTertiary,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredAllocations, key = { it.allocationId }) { alloc ->
                    AllocationRowCard(
                        alloc = alloc,
                        onSettleRequested = {
                            haptics.perform(HapticKind.TICK)
                            settlementTargetAllocation = alloc
                        },
                        onVoidRequested = { settlementId ->
                            haptics.perform(HapticKind.REJECT)
                            voidTargetSettlement = Pair(alloc.expenseId, settlementId)
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(40.dp)) }
            }
        }
    }

    // Settlement Bottom Sheet
    settlementTargetAllocation?.let { alloc ->
        RecordSettlementSheet(
            alloc = alloc,
            onConfirm = { amount, dateMillis, note ->
                viewModel.handleIntent(
                    ExpenseIntent.RecordSettlement(
                        expenseId = alloc.expenseId,
                        allocationId = alloc.allocationId,
                        amount = amount,
                        settledAt = dateMillis,
                        note = note
                    )
                )
                settlementTargetAllocation = null
            },
            onDismiss = { settlementTargetAllocation = null }
        )
    }

    // Void Settlement Dialog
    voidTargetSettlement?.let { pair ->
        VoidConfirmDialog(
            onConfirm = {
                viewModel.handleIntent(
                    ExpenseIntent.VoidSettlement(
                        expenseId = pair.first,
                        settlementId = pair.second
                    )
                )
                voidTargetSettlement = null
            },
            onDismiss = { voidTargetSettlement = null }
        )
    }
}

@Composable
private fun AllocationRowCard(
    alloc: AllocationComputation,
    onSettleRequested: () -> Unit,
    onVoidRequested: (String) -> Unit
) {
    val dateStr = remember(alloc.expenseTimestamp) {
        if (alloc.expenseTimestamp > 0L) {
            val dt = Instant.fromEpochMilliseconds(alloc.expenseTimestamp)
                .toLocalDateTime(TimeZone.currentSystemDefault())
            "${dt.year}-${dt.monthNumber.toString().padStart(2, '0')}-${dt.dayOfMonth.toString().padStart(2, '0')}"
        } else "تاريخ غير محدد"
    }

    val isFullySettled = alloc.status == DebtStatus.SETTLED

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(RaqeemColors.SurfaceCard)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isFullySettled,
                        onCheckedChange = { checked ->
                            if (checked) {
                                onSettleRequested()
                            } else {
                                alloc.settlements.lastOrNull { it.voidedAt == null }?.id?.let { sId ->
                                    onVoidRequested(sId)
                                }
                            }
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = RaqeemColors.EmeraldGreen,
                            uncheckedColor = Color.White.copy(alpha = 0.3f),
                            checkmarkColor = RaqeemColors.BackgroundSlate
                        ),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "#${alloc.refNo.toString().padStart(6, '0')}",
                                color = RaqeemColors.TextTertiary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = alloc.party.name,
                                color = RaqeemColors.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "${alloc.expenseTitle} • $dateStr",
                            color = RaqeemColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${alloc.remainingAmount} ${alloc.currency}",
                        color = if (isFullySettled) RaqeemColors.EmeraldGreen else RaqeemColors.AmberWarning,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    val statusText = when (alloc.status) {
                        DebtStatus.SETTLED -> "مسدَّد بالكامل"
                        DebtStatus.PARTIAL -> "مسدَّد جزئياً (${alloc.settledAmount})"
                        DebtStatus.OPEN -> "قيد التحصيل"
                    }
                    Text(
                        text = statusText,
                        color = if (isFullySettled) RaqeemColors.EmeraldGreen else RaqeemColors.TextTertiary,
                        fontSize = 11.sp
                    )
                }
            }

            // Settle action button if not fully settled
            if (!isFullySettled) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "تسجيل سداد",
                        color = RaqeemColors.ElectricCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onSettleRequested() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordSettlementSheet(
    alloc: AllocationComputation,
    onConfirm: (amount: Double, dateMillis: Long, note: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var amountStr by rememberSaveable { mutableStateOf("${alloc.remainingAmount}") }
    var note by rememberSaveable { mutableStateOf("") }
    val nowMillis = remember { Clock.System.now().toEpochMilliseconds() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RaqeemColors.SurfaceCard
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(20.dp)
        ) {
            Text(
                text = "تسجيل سداد مستحق",
                color = RaqeemColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "لصالح ${alloc.party.name} عن '${alloc.expenseTitle}'",
                color = RaqeemColors.TextSecondary,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = amountStr,
                onValueChange = { amountStr = it },
                label = { Text("المبلغ المسدد (${alloc.currency})") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RaqeemColors.EmeraldGreen,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                    focusedTextColor = RaqeemColors.TextPrimary,
                    unfocusedTextColor = RaqeemColors.TextPrimary
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("ملاحظة السداد (اختياري)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RaqeemColors.EmeraldGreen,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                    focusedTextColor = RaqeemColors.TextPrimary,
                    unfocusedTextColor = RaqeemColors.TextPrimary
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            val parsedAmount = amountStr.toDoubleOrNull()
            val isValid = parsedAmount != null && parsedAmount > 0.0 && parsedAmount <= alloc.remainingAmount

            Button(
                onClick = {
                    if (parsedAmount != null && isValid) {
                        onConfirm(parsedAmount, nowMillis, note.trim())
                    }
                },
                enabled = isValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RaqeemColors.EmeraldGreen,
                    contentColor = RaqeemColors.BackgroundSlate
                )
            ) {
                Text(
                    text = "تأكيد السداد",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun VoidConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "إلغاء السداد",
                color = RaqeemColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Text(
                text = "هل أنت متأكد من رغبتك في إلغاء هذا السداد؟ سيبقى مسجلاً في سجل التدقيق التاريخي كملغى.",
                color = RaqeemColors.TextSecondary,
                fontSize = 13.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = RaqeemColors.CoralRose,
                    contentColor = RaqeemColors.BackgroundSlate
                )
            ) {
                Text("إلغاء السداد")
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = RaqeemColors.SurfaceElevated,
                    contentColor = RaqeemColors.TextPrimary
                )
            ) {
                Text("تراجع")
            }
        },
        containerColor = RaqeemColors.SurfaceCard
    )
}

