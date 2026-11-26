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
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.mohtech.app.ai.nlp.LocalDialectExpenseParser
import org.mohtech.app.ai.nlp.TranscriptNormalizer
import org.mohtech.app.core.haptics.HapticFeedbackType
import org.mohtech.app.core.haptics.LocalRaqeemHaptics
import org.mohtech.app.core.theme.RaqeemColors
import org.mohtech.app.core.ui.RaqeemIcons
import org.mohtech.app.data.accounting.ReceivablesLedger
import org.mohtech.app.data.accounting.SplitCalculator
import org.mohtech.app.data.model.ExpenseBatch
import org.mohtech.app.data.model.ExpenseCategory
import org.mohtech.app.data.model.Payer
import org.mohtech.app.feature.receipts.PlatformReceiptPicker
import org.mohtech.app.feature.receipts.PlatformReceiptStorage

/**
 * ExpenseReviewSheet:
 * Complete, multi-item, unslop digital receipt review sheet for Raqeem.
 * Supports:
 * - Multi-item list editing and deletion
 * - Direct raw spoken text Edit/Done toggle without auto-reparse, plus explicit "إعادة التحليل"
 * - Live AutoBalancingSplitBar + real-time SplitCalculator party/total clamping
 * - Instant settlement switch ("سُدّد")
 * - 100% offline single-file bill receipt attachment, thumbnail, lightbox preview, and cleanup
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseReviewSheet(
    batch: ExpenseBatch,
    onConfirm: (ExpenseBatch) -> Unit,
    onDismiss: () -> Unit,
    onRawTextChange: (String) -> Unit = {},
    onReparseText: ((String) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalRaqeemHaptics.current
    val coroutineScope = rememberCoroutineScope()

    val isEditingSaved = remember(batch.batchId) { isEditingSavedBatch(batch.batchId) }
    val initialRawText = remember(batch.batchId) {
        batch.items.mapNotNull { it.rawSpokenText.takeIf { t -> t.isNotBlank() } }.firstOrNull() ?: ""
    }
    val hasInitialRawText = remember(batch.batchId) { initialRawText.isNotBlank() }
    var rawSpokenText by rememberSaveable(batch.batchId) { mutableStateOf(initialRawText) }
    var isEditingRawText by rememberSaveable(batch.batchId) { mutableStateOf(false) }

    // Bill-level receipt state & session tracker (prevents deleting pre-existing shared URIs)
    val initialPersistedReceipt = remember(batch.batchId) {
        batch.items.firstNotNullOfOrNull { it.receiptPhotoPath?.takeIf { p -> p.isNotBlank() } }
    }
    val receiptTracker = remember(batch.batchId) { ReceiptSessionTracker(initialPersistedReceipt) }
    var billReceiptPath by rememberSaveable(batch.batchId) { mutableStateOf(initialPersistedReceipt) }
    var userRemovedPersistedReceipt by rememberSaveable(batch.batchId) { mutableStateOf(false) }
    var receiptPickSeq by remember(batch.batchId) { mutableStateOf(0) }
    var manualItemSeq by remember(batch.batchId) { mutableStateOf(0) }
    var previewReceiptUri by remember { mutableStateOf<String?>(null) }

    val structuralSignature = remember(batch.items) { structuralItemsSignature(batch.items) }

    LaunchedEffect(batch.batchId, structuralSignature) {
        val batchReceipt = batch.items.firstNotNullOfOrNull { it.receiptPhotoPath?.takeIf { p -> p.isNotBlank() } }
        if (shouldSyncBatchReceipt(userRemovedPersistedReceipt, billReceiptPath, batchReceipt)) {
            billReceiptPath = batchReceipt
            receiptTracker.syncExternalPath(batchReceipt)
        }
    }

    // Draft list keyed on batchId + structural signature (ignores UpdatePendingRawText changes)
    val drafts = remember(batch.batchId, structuralSignature) {
        mutableStateListOf<ExpenseItemDraft>().apply {
            batch.items.forEach { item ->
                add(item.toExpenseItemDraft())
            }
        }
    }

    val handleDismiss: () -> Unit = {
        val toDelete = receiptTracker.onDismissWithoutSave()
        if (toDelete.isNotEmpty()) {
            coroutineScope.launch(Dispatchers.Default) {
                toDelete.forEach { PlatformReceiptStorage.deleteReceipt(it) }
            }
        }
        onDismiss()
    }

    val handleRemoveReceipt: () -> Unit = {
        userRemovedPersistedReceipt = true
        val toDelete = receiptTracker.onRemoveReceipt()
        billReceiptPath = null
        if (toDelete.isNotEmpty()) {
            coroutineScope.launch(Dispatchers.Default) {
                toDelete.forEach { PlatformReceiptStorage.deleteReceipt(it) }
            }
        }
    }

    val hasOverAllocatedItem = drafts.any { it.isOverAllocated }
    val hasSettledFloorViolation = drafts.any { it.hasSettledFloorViolation }
    val hasInvalidAmount = drafts.any { it.totalAmount <= 0.0 }
    val canConfirm = drafts.isNotEmpty() && !hasOverAllocatedItem && !hasSettledFloorViolation && !hasInvalidAmount

    ModalBottomSheet(
        onDismissRequest = handleDismiss,
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
                Column(modifier = Modifier.weight(1f)) {
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
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(RaqeemColors.SurfaceElevated)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true, color = RaqeemColors.TextPrimary),
                            onClick = handleDismiss
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    RaqeemIcons.Close(size = 16.dp, tint = RaqeemColors.TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Raw Spoken Quote & Re-parse Section (stays visible even when cleared during edit)
            if (shouldShowRawTextSection(hasInitialRawText, rawSpokenText, isEditingRawText)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(RaqeemColors.SurfaceElevated)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
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
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Explicit Edit / Done toggle (never triggers re-parsing)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(RaqeemColors.SurfaceCard)
                                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                        .clickable {
                                            haptics.trigger(HapticFeedbackType.LightTick)
                                            isEditingRawText = !isEditingRawText
                                        }
                                        .padding(horizontal = 10.dp)
                                ) {
                                    if (isEditingRawText) {
                                        RaqeemIcons.Check(size = 13.dp, tint = RaqeemColors.EmeraldGreen)
                                        Text(
                                            text = "تم",
                                            color = RaqeemColors.EmeraldGreen,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    } else {
                                        RaqeemIcons.Edit(size = 13.dp, tint = RaqeemColors.TextSecondary)
                                        Text(
                                            text = "تعديل",
                                            color = RaqeemColors.TextSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                if (isEditingSaved) {
                                    Box(
                                        modifier = Modifier
                                            .sizeIn(minHeight = 48.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(RaqeemColors.SurfaceCard)
                                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "تعديل سجل محفوظ",
                                            color = RaqeemColors.TextTertiary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Normal
                                        )
                                    }
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier
                                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(RaqeemColors.ElectricCyan.copy(alpha = 0.1f))
                                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                            .clickable(enabled = rawSpokenText.isNotBlank()) {
                                                haptics.trigger(HapticFeedbackType.LightTick)
                                                isEditingRawText = false
                                                val externalReparse = onReparseText
                                                if (externalReparse != null) {
                                                    externalReparse(rawSpokenText)
                                                } else {
                                                    val parser = LocalDialectExpenseParser()
                                                    val currency = drafts.firstOrNull()?.currency ?: "₪"
                                                    val normalized = TranscriptNormalizer.normalize(rawSpokenText)
                                                    val reBatch = parser.parseSpokenText(normalized.normalizedText, currency)
                                                    drafts.clear()
                                                    reBatch.items.forEach { item ->
                                                        drafts.add(
                                                            item.copy(
                                                                rawSpokenText = rawSpokenText,
                                                                receiptPhotoPath = billReceiptPath
                                                            ).toExpenseItemDraft()
                                                        )
                                                    }
                                                }
                                            }
                                            .padding(horizontal = 10.dp)
                                    ) {
                                        RaqeemIcons.Refresh(
                                            size = 13.dp,
                                            tint = if (rawSpokenText.isNotBlank()) RaqeemColors.ElectricCyan else RaqeemColors.TextTertiary
                                        )
                                        Text(
                                            text = "إعادة التحليل",
                                            color = if (rawSpokenText.isNotBlank()) RaqeemColors.ElectricCyan else RaqeemColors.TextTertiary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        if (!isEditingRawText) {
                            Text(
                                text = if (rawSpokenText.isNotBlank()) "« $rawSpokenText »" else "لا يوجد نص — اضغط تعديل للكتابة",
                                color = if (rawSpokenText.isNotBlank()) RaqeemColors.TextPrimary else RaqeemColors.TextTertiary,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isEditingRawText = true }
                                    .padding(vertical = 4.dp)
                            )
                        } else {
                            OutlinedTextField(
                                value = rawSpokenText,
                                onValueChange = { updated ->
                                    rawSpokenText = updated
                                    onRawTextChange(updated)
                                },
                                singleLine = false,
                                placeholder = { Text("اكتب أو عدّل النص الصوتي هنا...", fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = RaqeemColors.ElectricCyan,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
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
            if (drafts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(RaqeemColors.SurfaceElevated)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "لا توجد بنود حالياً، أضف بنداً للمتابعة",
                        color = RaqeemColors.TextTertiary,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    itemsIndexed(
                        items = drafts,
                        key = { _, draft -> draft.originalId },
                        contentType = { _, _ -> "review_item_card" }
                    ) { index, draft ->
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
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Add Item Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        haptics.trigger(HapticFeedbackType.LightTick)
                        val nextSeq = ++manualItemSeq
                        drafts.add(
                            ExpenseItemDraft(
                                originalId = "exp_manual_${Clock.System.now().toEpochMilliseconds()}_$nextSeq",
                                currency = drafts.firstOrNull()?.currency ?: "₪",
                                initialTitle = "",
                                initialAmountStr = "",
                                initialCategory = ExpenseCategory.GENERAL,
                                initialPayer = Payer("self", "أنا", true)
                            )
                        )
                    }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RaqeemIcons.Plus(size = 14.dp, tint = RaqeemColors.EmeraldGreen)
                    Text(
                        text = "إضافة بند آخر للمصروف",
                        color = RaqeemColors.EmeraldGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bill-level Single-File Receipt Attachment Section (shared across all items/splits)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(RaqeemColors.SurfaceElevated)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val activeReceipt = billReceiptPath?.takeIf { it.isNotBlank() }
                    if (activeReceipt == null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            RaqeemIcons.Receipt(size = 16.dp, tint = RaqeemColors.TextSecondary)
                            Text(
                                text = "إرفاق صورة الإيصال (اختياري)",
                                color = RaqeemColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(RaqeemColors.SurfaceCard)
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                                .clickable {
                                    haptics.trigger(HapticFeedbackType.LightTick)
                                    val pickKey = buildReceiptPickKey(
                                        batchId = batch.batchId,
                                        timestampMs = Clock.System.now().toEpochMilliseconds(),
                                        sequence = ++receiptPickSeq
                                    )
                                    PlatformReceiptPicker.pickReceipt(pickKey) { pickedPath ->
                                        if (!pickedPath.isNullOrBlank()) {
                                            userRemovedPersistedReceipt = false
                                            val stale = receiptTracker.onReceiptPicked(pickedPath)
                                            billReceiptPath = pickedPath
                                            if (stale.isNotEmpty()) {
                                                coroutineScope.launch(Dispatchers.Default) {
                                                    stale.forEach { PlatformReceiptStorage.deleteReceipt(it) }
                                                }
                                            }
                                        }
                                    }
                                }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "اختيار صورة",
                                color = RaqeemColors.ElectricCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            ReceiptThumbnailBox(
                                fileUri = activeReceipt,
                                onClick = { previewReceiptUri = activeReceipt }
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "تم إرفاق الإيصال",
                                    color = RaqeemColors.EmeraldGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "اضغط للمعاينة — يرتبط بكافة السجلات",
                                    color = RaqeemColors.TextTertiary,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(RaqeemColors.CoralRose.copy(alpha = 0.14f))
                                    .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                                    .clickable {
                                        haptics.trigger(HapticFeedbackType.LightTick)
                                        handleRemoveReceipt()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                RaqeemIcons.Close(size = 14.dp, tint = RaqeemColors.CoralRose)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Overall Summary, Settled Floor & Over-Allocation Error Messages
            if (hasSettledFloorViolation) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(RaqeemColors.CoralRose.copy(alpha = 0.12f))
                        .border(1.dp, RaqeemColors.CoralRose.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "لا يمكن تقليص الحصة عن المبلغ المسدد مسبقاً",
                        color = RaqeemColors.CoralRose,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

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
            val totalFormatted = formatDraftAmount(totalBatchSum, batchCurrency)

            Button(
                onClick = {
                    if (!canConfirm) return@Button
                    haptics.trigger(HapticFeedbackType.SuccessImpact)

                    val staleOnSave = receiptTracker.onConfirmSaved()
                    if (staleOnSave.isNotEmpty()) {
                        coroutineScope.launch(Dispatchers.Default) {
                            staleOnSave.forEach { PlatformReceiptStorage.deleteReceipt(it) }
                        }
                    }

                    val now = Clock.System.now().toEpochMilliseconds()
                    val confirmedBatch = buildConfirmedBatch(
                        originalBatch = batch,
                        drafts = drafts.map { it.toConfirmedDraftItem() },
                        rawSpokenText = rawSpokenText,
                        billReceiptPath = billReceiptPath,
                        nowEpochMs = now
                    )
                    onConfirm(confirmedBatch)
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

    previewReceiptUri?.let { uri ->
        ReceiptPreviewDialog(
            fileUri = uri,
            onDismiss = { previewReceiptUri = null },
            onRemove = handleRemoveReceipt
        )
    }
}

private fun categoryTokenColor(category: ExpenseCategory): Color = when (category) {
    ExpenseCategory.GROCERIES -> RaqeemColors.CatGroceries
    ExpenseCategory.DINING -> RaqeemColors.CatDining
    ExpenseCategory.TRANSPORT -> RaqeemColors.CatTransport
    ExpenseCategory.BILLS -> RaqeemColors.CatBills
    ExpenseCategory.HEALTH -> RaqeemColors.CatHealth
    ExpenseCategory.GENERAL -> RaqeemColors.CatGeneral
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
    var isSplitExpanded by remember { mutableStateOf(draft.allocations.isNotEmpty()) }
    var clampWarningText by remember { mutableStateOf<String?>(null) }
    var totalFieldHadFocus by remember { mutableStateOf(false) }

    val partyNumericShares = draft.allocations.map { (parseLocalizedAmount(it.amountStr) ?: 0.0).coerceAtLeast(0.0) }
    val splitSummary = SplitCalculator.computeAutoBalancedSplit(
        totalAmount = draft.totalAmount,
        currency = draft.currency,
        partyShares = partyNumericShares
    )

    val applyTotalShrinkClamping: () -> Unit = {
        val currentShares = draft.allocations.map { it.amountStr }
        val minFloors = draft.allocations.map { it.effectiveSettledFloor(draft.currency) }
        val result = applyBillTotalInput(
            newTotalInput = draft.amountStr,
            currency = draft.currency,
            currentPartyShares = currentShares,
            minPartyFloors = minFloors
        )
        if (result.anyClamped) {
            result.partyShares.forEachIndexed { idx, updatedStr ->
                draft.allocations[idx] = draft.allocations[idx].copy(amountStr = updatedStr)
            }
            clampWarningText = if (result.belowSettledFloor) {
                "لا يمكن تقليص الحصة عن المبلغ المسدد مسبقاً"
            } else {
                "تم تعديل حصص الأطراف تلقائياً لتتوافق مع إجمالي الفاتورة"
            }
            haptics.trigger(HapticFeedbackType.LightTick)
        } else if (result.belowSettledFloor) {
            clampWarningText = "لا يمكن تقليص الحصة عن المبلغ المسدد مسبقاً"
            haptics.trigger(HapticFeedbackType.LightTick)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RaqeemColors.SurfaceElevated)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
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
                                onValueChange = { raw ->
                                    draft.amountStr = sanitizeLocalizedDecimalInput(raw)
                                    clampWarningText = null
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .width(96.dp)
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            totalFieldHadFocus = true
                                        } else if (totalFieldHadFocus) {
                                            totalFieldHadFocus = false
                                            applyTotalShrinkClamping()
                                        }
                                    },
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
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(RaqeemColors.CoralRose.copy(alpha = 0.12f))
                                .border(1.dp, RaqeemColors.CoralRose.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                                .clickable(onClick = onDelete),
                            contentAlignment = Alignment.Center
                        ) {
                            RaqeemIcons.Trash(size = 16.dp, tint = RaqeemColors.CoralRose)
                        }
                    }
                }
            }

            // Category Selector Chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(
                    items = ExpenseCategory.entries,
                    key = { it.name },
                    contentType = { "category_chip" }
                ) { cat ->
                    val isSelected = draft.category == cat
                    val catColor = categoryTokenColor(cat)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) catColor.copy(alpha = 0.18f) else RaqeemColors.SurfaceCard)
                            .border(
                                1.dp,
                                if (isSelected) catColor else Color.White.copy(alpha = 0.08f),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                haptics.trigger(HapticFeedbackType.LightTick)
                                draft.category = cat
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
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
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 48.dp)
                    .clickable { isSplitExpanded = !isSplitExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RaqeemIcons.Users(size = 15.dp, tint = RaqeemColors.ElectricCyan)
                    Text(
                        text = if (!draft.payer.isCurrentUser) {
                            "على حساب ${draft.payer.name}"
                        } else {
                            "تقسيم وتخصيص الديون (${draft.allocations.size})"
                        },
                        color = RaqeemColors.ElectricCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = "حصتي: ${formatDraftAmount(draft.myShare, draft.currency)} ${draft.currency}",
                    color = if (draft.isOverAllocated) RaqeemColors.CoralRose else RaqeemColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Auto-Balancing Split Bar (strictly for user-paid splits) or calm external-payer summary
            if (draft.payer.isCurrentUser) {
                if (draft.allocations.isNotEmpty() || isSplitExpanded) {
                    AutoBalancingSplitBar(
                        summary = splitSummary,
                        partyNames = draft.allocations.map { it.partyName },
                        currency = draft.currency
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(RaqeemColors.SurfaceCard)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "على حساب ${draft.payer.name}",
                        color = RaqeemColors.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "حصتي: ${formatDraftAmount(draft.myShare, draft.currency)} ${draft.currency}",
                        color = RaqeemColors.ElectricCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (draft.isOverAllocated && draft.totalAmount > 0.0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(RaqeemColors.AmberWarning.copy(alpha = 0.14f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "مجموع حصص الأطراف يتجاوز الإجمالي",
                        color = RaqeemColors.AmberWarning,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Box(
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { applyTotalShrinkClamping() }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "موازنة الحصص",
                            color = RaqeemColors.ElectricCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (draft.hasSettledFloorViolation) {
                Text(
                    text = "لا يمكن تقليص الحصة عن المبلغ المسدد مسبقاً",
                    color = RaqeemColors.CoralRose,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            clampWarningText?.let { warning ->
                Text(
                    text = warning,
                    color = RaqeemColors.AmberWarning,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
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
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = alloc.partyName,
                                onValueChange = { updatedName ->
                                    draft.allocations[aIndex] = alloc.copy(partyName = updatedName)
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

                            var partyFieldHadFocus by remember(alloc.id) { mutableStateOf(false) }
                            val settledFloor = alloc.effectiveSettledFloor(draft.currency)

                            OutlinedTextField(
                                value = alloc.amountStr,
                                onValueChange = { requested ->
                                    val result = applyPartyShareInput(
                                        totalAmount = draft.totalAmount,
                                        currency = draft.currency,
                                        currentPartyShares = draft.allocations.map { it.amountStr },
                                        targetIndex = aIndex,
                                        requestedInput = requested
                                    )
                                    draft.allocations[aIndex] = alloc.copy(amountStr = result.amountStr)
                                    if (result.wasClamped) {
                                        clampWarningText = "تم ضبط الحصة للحد الأقصى: ${formatDraftAmount(result.maxAllowed, draft.currency)} ${draft.currency}"
                                        haptics.trigger(HapticFeedbackType.LightTick)
                                    } else {
                                        clampWarningText = null
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                placeholder = { Text("المبلغ", fontSize = 11.sp) },
                                modifier = Modifier
                                    .weight(0.9f)
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            partyFieldHadFocus = true
                                        } else if (partyFieldHadFocus) {
                                            partyFieldHadFocus = false
                                            if (settledFloor > 0.0 && aIndex in draft.allocations.indices) {
                                                val currentAlloc = draft.allocations[aIndex]
                                                val floorResult = applyPartyShareInput(
                                                    totalAmount = draft.totalAmount,
                                                    currency = draft.currency,
                                                    currentPartyShares = draft.allocations.map { it.amountStr },
                                                    targetIndex = aIndex,
                                                    requestedInput = currentAlloc.amountStr,
                                                    minFloor = settledFloor
                                                )
                                                if (floorResult.wasClamped) {
                                                    draft.allocations[aIndex] = currentAlloc.copy(amountStr = floorResult.amountStr)
                                                    clampWarningText = "لا يمكن تقليص الحصة عن المبلغ المسدد مسبقاً"
                                                    haptics.trigger(HapticFeedbackType.LightTick)
                                                }
                                            }
                                        }
                                    },
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = RaqeemColors.ElectricCyan,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                                    focusedTextColor = RaqeemColors.TextPrimary,
                                    unfocusedTextColor = RaqeemColors.TextPrimary
                                )
                            )

                            // "سُدّد" Switch / Checkbox
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .sizeIn(minHeight = 48.dp)
                                    .clickable {
                                        draft.allocations[aIndex] = alloc.copy(isSettledNow = !alloc.isSettledNow)
                                    }
                                    .padding(horizontal = 2.dp)
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
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        draft.allocations.removeAt(aIndex)
                                        clampWarningText = null
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                RaqeemIcons.Close(size = 13.dp, tint = RaqeemColors.CoralRose)
                            }
                        }
                    }

                    // Add Allocation Party Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .sizeIn(minHeight = 48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(RaqeemColors.SurfaceCard)
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
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
        }
    }
}
