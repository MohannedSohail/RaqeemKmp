package org.mohtech.app.feature.dashboard.ui

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import org.mohtech.app.ai.nlp.LocalDialectExpenseParser
import org.mohtech.app.ai.nlp.TranscriptNormalizer
import org.mohtech.app.core.haptics.HapticFeedbackType
import org.mohtech.app.core.haptics.LocalRaqeemHaptics
import org.mohtech.app.core.theme.RaqeemColors
import org.mohtech.app.core.ui.RaqeemIcons
import org.mohtech.app.data.accounting.ReceivablesLedger
import org.mohtech.app.data.model.Allocation
import org.mohtech.app.data.model.ExpenseBatch
import org.mohtech.app.data.model.ExpenseCategory
import org.mohtech.app.data.model.ExpenseItem
import org.mohtech.app.data.model.ExpenseSplit
import org.mohtech.app.data.model.Payer
import org.mohtech.app.data.model.Settlement
import org.mohtech.app.feature.receipts.PlatformReceiptPicker

/**
 * Review state for an editable allocation row.
 */
data class AllocationDraft(
    val id: String,
    var partyName: String,
    var amountStr: String,
    var isSettledNow: Boolean = false
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
    initialReceiptPhotoPath: String? = null
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
        get() = amountStr.toDoubleOrNull() ?: 0.0

    val sumAllocations: Double
        get() = allocations.sumOf { it.amountStr.toDoubleOrNull() ?: 0.0 }

    val myShare: Double
        get() = (totalAmount - sumAllocations).coerceAtLeast(0.0)

    val isOverAllocated: Boolean
        get() = sumAllocations > totalAmount
}

/**
 * ExpenseReviewSheet:
 * Complete, multi-item, unslop digital receipt review sheet for Raqeem.
 * Supports:
 * - Multi-item list editing and deletion
 * - Raw spoken text editing with "إعادة التحليل"
 * - Dynamic split/allocation calculation with strict over-allocation validation
 * - Instant settlement switch ("سُدّد الآن")
 * - 100% offline receipt attachment via PlatformReceiptPicker
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseReviewSheet(
    batch: ExpenseBatch,
    onConfirm: (ExpenseBatch) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalRaqeemHaptics.current

    val initialRawText = remember(batch) {
        batch.items.mapNotNull { it.rawSpokenText.takeIf { t -> t.isNotBlank() } }.firstOrNull() ?: ""
    }
    var rawSpokenText by rememberSaveable(batch.batchId) { mutableStateOf(initialRawText) }
    var isEditingRawText by rememberSaveable { mutableStateOf(false) }

    // Draft list representing items in the batch
    val drafts = remember(batch) {
        mutableStateListOf<ExpenseItemDraft>().apply {
            batch.items.forEach { item ->
                val allocDrafts = item.split?.allocations?.map { alloc ->
                    AllocationDraft(
                        id = alloc.id,
                        partyName = alloc.party.name,
                        amountStr = if (alloc.amount % 1.0 == 0.0) "${alloc.amount.toInt()}" else "${alloc.amount}",
                        isSettledNow = alloc.settlements.any { it.voidedAt == null }
                    )
                } ?: emptyList()

                val amtStr = if (item.amount % 1.0 == 0.0) "${item.amount.toInt()}" else "${item.amount}"
                add(
                    ExpenseItemDraft(
                        originalId = item.id,
                        currency = item.currency,
                        initialTitle = item.title,
                        initialAmountStr = amtStr,
                        initialCategory = item.category,
                        initialPayer = item.payer,
                        initialAllocations = allocDrafts,
                        initialReceiptPhotoPath = item.receiptPhotoPath
                    )
                )
            }
        }
    }

    val hasOverAllocatedItem = drafts.any { it.isOverAllocated }
    val hasInvalidAmount = drafts.any { it.totalAmount <= 0.0 }
    val canConfirm = drafts.isNotEmpty() && !hasOverAllocatedItem && !hasInvalidAmount

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RaqeemColors.SurfaceCard,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (drafts.size > 1) "مراجعة المصاريف المستخرجة (${drafts.size})" else "مراجعة المصروف المستخرج",
                        color = RaqeemColors.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "تحقق من البنود والمستحقات قبل الحفظ",
                        color = RaqeemColors.TextTertiary,
                        fontSize = 12.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(RaqeemColors.SurfaceElevated)
                        .border(1.dp, Color.White.copy(alpha = 0.06f), CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true, color = RaqeemColors.TextPrimary),
                            onClick = onDismiss
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    RaqeemIcons.Close(size = 14.dp, tint = RaqeemColors.TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Raw Spoken Quote & Re-parse Section
            if (rawSpokenText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(RaqeemColors.SurfaceElevated)
                        .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "النص الصوتي الملتقط",
                                color = RaqeemColors.TextTertiary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        haptics.trigger(HapticFeedbackType.LightTick)
                                        // Re-parse with TranscriptNormalizer and LocalDialectExpenseParser
                                        val parser = LocalDialectExpenseParser()
                                        val currency = drafts.firstOrNull()?.currency ?: "₪"
                                        val normalized = TranscriptNormalizer.normalize(rawSpokenText)
                                        val reBatch = parser.parseSpokenText(normalized.normalizedText, currency)

                                        drafts.clear()
                                        reBatch.items.forEach { item ->
                                            val allocDrafts = item.split?.allocations?.map { alloc ->
                                                AllocationDraft(
                                                    id = alloc.id,
                                                    partyName = alloc.party.name,
                                                    amountStr = if (alloc.amount % 1.0 == 0.0) "${alloc.amount.toInt()}" else "${alloc.amount}",
                                                    isSettledNow = alloc.settlements.any { it.voidedAt == null }
                                                )
                                            } ?: emptyList()
                                            val amtStr = if (item.amount % 1.0 == 0.0) "${item.amount.toInt()}" else "${item.amount}"
                                            drafts.add(
                                                ExpenseItemDraft(
                                                    originalId = item.id,
                                                    currency = item.currency,
                                                    initialTitle = item.title,
                                                    initialAmountStr = amtStr,
                                                    initialCategory = item.category,
                                                    initialPayer = item.payer,
                                                    initialAllocations = allocDrafts,
                                                    initialReceiptPhotoPath = item.receiptPhotoPath
                                                )
                                            )
                                        }
                                        isEditingRawText = false
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                RaqeemIcons.Refresh(size = 13.dp, tint = RaqeemColors.ElectricCyan)
                                Text(
                                    text = "إعادة التحليل",
                                    color = RaqeemColors.ElectricCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        if (!isEditingRawText) {
                            Text(
                                text = "« $rawSpokenText »",
                                color = RaqeemColors.TextPrimary,
                                fontSize = 13.sp,
                                modifier = Modifier.clickable { isEditingRawText = true }
                            )
                        } else {
                            OutlinedTextField(
                                value = rawSpokenText,
                                onValueChange = { rawSpokenText = it },
                                singleLine = false,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = RaqeemColors.ElectricCyan,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                    focusedTextColor = RaqeemColors.TextPrimary,
                                    unfocusedTextColor = RaqeemColors.TextPrimary
                                )
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // List of Item Cards
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                itemsIndexed(drafts, key = { _, draft -> draft.originalId }) { index, draft ->
                    ReviewItemCard(
                        draft = draft,
                        itemIndex = index,
                        totalCount = drafts.size,
                        onDelete = if (drafts.size > 1) {
                            {
                                haptics.trigger(HapticFeedbackType.LightTick)
                                drafts.removeAt(index)
                            }
                        } else null
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Overall Summary & Error Message
            if (hasOverAllocatedItem) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(RaqeemColors.CoralRose.copy(alpha = 0.12f))
                        .border(1.dp, RaqeemColors.CoralRose.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "مجموع التخصيصات يتجاوز إجمالي المصروف، يرجى تعديل المبالغ",
                        color = RaqeemColors.CoralRose,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Confirm Button
            val totalBatchSum = drafts.sumOf { it.totalAmount }
            val batchCurrency = drafts.firstOrNull()?.currency ?: "₪"
            val totalFormatted = if (totalBatchSum % 1.0 == 0.0) "${totalBatchSum.toInt()}" else "$totalBatchSum"

            Button(
                onClick = {
                    if (!canConfirm) return@Button
                    haptics.trigger(HapticFeedbackType.SuccessImpact)

                    val now = Clock.System.now().toEpochMilliseconds()
                    val updatedItems = drafts.map { draft ->
                        val finalAllocations = draft.allocations.mapNotNull { allocDraft ->
                            val aAmt = allocDraft.amountStr.toDoubleOrNull()
                            if (aAmt != null && aAmt > 0.0 && allocDraft.partyName.isNotBlank()) {
                                val party = Payer(id = allocDraft.partyName, name = allocDraft.partyName, isCurrentUser = false)
                                val settlements = if (allocDraft.isSettledNow) {
                                    listOf(
                                        Settlement(
                                            id = "settle_${now}_${ReceivablesLedger.nextSettlementSeq()}",
                                            refNo = ReceivablesLedger.nextRefNo(),
                                            allocationId = allocDraft.id,
                                            amount = aAmt,
                                            settledAt = now,
                                            recordedAt = now,
                                            note = "سُدّد عند التسجيل"
                                        )
                                    )
                                } else emptyList()

                                Allocation(
                                    id = allocDraft.id,
                                    refNo = ReceivablesLedger.nextRefNo(),
                                    party = party,
                                    amount = aAmt,
                                    settlements = settlements,
                                    note = "مخصص لـ ${allocDraft.partyName}"
                                )
                            } else null
                        }

                        val split = if (finalAllocations.isNotEmpty()) {
                            ExpenseSplit(
                                totalAmount = draft.totalAmount,
                                payer = draft.payer,
                                participants = listOf(Payer("self", "أنا", true)) + finalAllocations.map { it.party },
                                amountPerPerson = draft.myShare,
                                note = "توزيع مستحقات",
                                allocations = finalAllocations
                            )
                        } else null

                        ExpenseItem(
                            id = draft.originalId,
                            title = draft.title.ifBlank { "مصروف" },
                            amount = draft.totalAmount,
                            currency = draft.currency,
                            category = draft.category,
                            timestamp = now,
                            payer = draft.payer,
                            split = split,
                            rawSpokenText = rawSpokenText,
                            receiptPhotoPath = draft.receiptPhotoPath,
                            myShare = if (finalAllocations.isNotEmpty()) draft.myShare else null
                        )
                    }

                    onConfirm(
                        batch.copy(
                            items = updatedItems,
                            totalBatchAmount = updatedItems.sumOf { it.amount }
                        )
                    )
                },
                enabled = canConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RaqeemColors.EmeraldGreen,
                    contentColor = RaqeemColors.BackgroundSlate,
                    disabledContainerColor = RaqeemColors.SurfaceElevated,
                    disabledContentColor = RaqeemColors.TextTertiary
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    RaqeemIcons.Check(
                        size = 18.dp,
                        tint = if (canConfirm) RaqeemColors.BackgroundSlate else RaqeemColors.TextTertiary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (drafts.size > 1) "حفظ ${drafts.size} مصاريف ($totalFormatted $batchCurrency)" else "حفظ المصروف ($totalFormatted $batchCurrency)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Clean Digital Receipt Card for each individual item.
 */
@Composable
private fun ReviewItemCard(
    draft: ExpenseItemDraft,
    itemIndex: Int,
    totalCount: Int,
    onDelete: (() -> Unit)?
) {
    val haptics = LocalRaqeemHaptics.current
    var isCategoryExpanded by remember { mutableStateOf(false) }
    var isSplitExpanded by remember { mutableStateOf(draft.allocations.isNotEmpty()) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RaqeemColors.SurfaceElevated)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Card Top Row: Title + Delete + Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (totalCount > 1) {
                        Text(
                            text = "بند ${itemIndex + 1}",
                            color = RaqeemColors.TextTertiary,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    OutlinedTextField(
                        value = draft.title,
                        onValueChange = { draft.title = it },
                        singleLine = true,
                        placeholder = { Text("اسم البند", fontSize = 14.sp) },
                        modifier = Modifier.fillMaxWidth(0.92f),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RaqeemColors.EmeraldGreen,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                            focusedTextColor = RaqeemColors.TextPrimary,
                            unfocusedTextColor = RaqeemColors.TextPrimary
                        )
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = draft.amountStr,
                                onValueChange = { draft.amountStr = it },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.width(96.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = RaqeemColors.EmeraldGreen,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                                    focusedTextColor = RaqeemColors.EmeraldGreen,
                                    unfocusedTextColor = RaqeemColors.EmeraldGreen
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = draft.currency,
                                color = RaqeemColors.EmeraldGreen,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (onDelete != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(RaqeemColors.CoralRose.copy(alpha = 0.12f))
                                .border(1.dp, RaqeemColors.CoralRose.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                .clickable(onClick = onDelete),
                            contentAlignment = Alignment.Center
                        ) {
                            RaqeemIcons.Trash(size = 14.dp, tint = RaqeemColors.CoralRose)
                        }
                    }
                }
            }

            // Category Selector Chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(ExpenseCategory.entries.toTypedArray(), key = { it.name }) { cat ->
                    val isSelected = draft.category == cat
                    val catColor = Color(cat.colorHex)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) catColor.copy(alpha = 0.18f) else RaqeemColors.SurfaceCard)
                            .border(
                                1.dp,
                                if (isSelected) catColor else Color.White.copy(alpha = 0.06f),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                haptics.trigger(HapticFeedbackType.LightTick)
                                draft.category = cat
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(catColor)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = cat.arabicTitle,
                                color = if (isSelected) RaqeemColors.TextPrimary else RaqeemColors.TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // Split / Allocations Section Header Toggle
            HorizontalDivider(color = Color.White.copy(alpha = 0.05f), thickness = 1.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isSplitExpanded = !isSplitExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RaqeemIcons.Users(size = 15.dp, tint = RaqeemColors.ElectricCyan)
                    Text(
                        text = "تقسيم وتخصيص الديون (${draft.allocations.size})",
                        color = RaqeemColors.ElectricCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "نصيبي: ${draft.myShare} ${draft.currency}",
                        color = if (draft.isOverAllocated) RaqeemColors.CoralRose else RaqeemColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Expanded Allocations Table
            AnimatedVisibility(visible = isSplitExpanded) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    draft.allocations.forEachIndexed { aIndex, alloc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(RaqeemColors.SurfaceCard)
                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = alloc.partyName,
                                onValueChange = {
                                    alloc.partyName = it
                                    // Force update
                                    draft.allocations[aIndex] = alloc.copy(partyName = it)
                                },
                                singleLine = true,
                                placeholder = { Text("الطرف (محمد، البيت...)", fontSize = 11.sp) },
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = RaqeemColors.ElectricCyan,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                                    focusedTextColor = RaqeemColors.TextPrimary,
                                    unfocusedTextColor = RaqeemColors.TextPrimary
                                )
                            )

                            OutlinedTextField(
                                value = alloc.amountStr,
                                onValueChange = {
                                    alloc.amountStr = it
                                    draft.allocations[aIndex] = alloc.copy(amountStr = it)
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                placeholder = { Text("المبلغ", fontSize = 11.sp) },
                                modifier = Modifier.weight(0.9f),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = RaqeemColors.ElectricCyan,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                                    focusedTextColor = RaqeemColors.TextPrimary,
                                    unfocusedTextColor = RaqeemColors.TextPrimary
                                )
                            )

                            // "سُدّد الآن" Switch / Checkbox
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    val updated = alloc.copy(isSettledNow = !alloc.isSettledNow)
                                    draft.allocations[aIndex] = updated
                                }
                            ) {
                                Checkbox(
                                    checked = alloc.isSettledNow,
                                    onCheckedChange = { checked ->
                                        draft.allocations[aIndex] = alloc.copy(isSettledNow = checked)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = RaqeemColors.EmeraldGreen,
                                        uncheckedColor = RaqeemColors.TextTertiary,
                                        checkmarkColor = RaqeemColors.BackgroundSlate
                                    ),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "سُدّد",
                                    color = if (alloc.isSettledNow) RaqeemColors.EmeraldGreen else RaqeemColors.TextTertiary,
                                    fontSize = 11.sp
                                )
                            }

                            // Delete Allocation Row
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        draft.allocations.removeAt(aIndex)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                RaqeemIcons.Close(size = 12.dp, tint = RaqeemColors.CoralRose)
                            }
                        }
                    }

                    // Add Allocation Party Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(RaqeemColors.SurfaceCard)
                            .clickable {
                                val newId = "alloc_${Clock.System.now().toEpochMilliseconds()}_${ReceivablesLedger.nextRefNo()}"
                                draft.allocations.add(
                                    AllocationDraft(
                                        id = newId,
                                        partyName = "",
                                        amountStr = "",
                                        isSettledNow = false
                                    )
                                )
                            }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RaqeemIcons.Plus(size = 14.dp, tint = RaqeemColors.ElectricCyan)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "إضافة طرف مخصص (مدين)",
                            color = RaqeemColors.ElectricCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Receipt Photo Attachment
            HorizontalDivider(color = Color.White.copy(alpha = 0.05f), thickness = 1.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RaqeemIcons.Receipt(size = 15.dp, tint = RaqeemColors.TextSecondary)
                    Text(
                        text = if (draft.receiptPhotoPath != null) "مرفق صورة الإيصال" else "إرفاق صورة الإيصال (اختياري)",
                        color = if (draft.receiptPhotoPath != null) RaqeemColors.EmeraldGreen else RaqeemColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }

                if (draft.receiptPhotoPath == null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(RaqeemColors.SurfaceCard)
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .clickable {
                                PlatformReceiptPicker.pickReceipt(draft.originalId) { path ->
                                    if (path != null) {
                                        draft.receiptPhotoPath = path
                                    }
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "اختيار صورة",
                            color = RaqeemColors.ElectricCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "تم الإرفاق",
                            color = RaqeemColors.EmeraldGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(RaqeemColors.CoralRose.copy(alpha = 0.15f))
                                .clickable { draft.receiptPhotoPath = null },
                            contentAlignment = Alignment.Center
                        ) {
                            RaqeemIcons.Close(size = 10.dp, tint = RaqeemColors.CoralRose)
                        }
                    }
                }
            }
        }
    }
}
