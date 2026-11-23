package org.mohtech.app.feature.dashboard.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.mohtech.app.core.haptics.HapticKind
import org.mohtech.app.core.haptics.LocalRaqeemHaptics
import org.mohtech.app.core.theme.RaqeemColors
import org.mohtech.app.core.ui.RaqeemIcons
import org.mohtech.app.data.model.ExpenseCategory
import org.mohtech.app.data.model.ExpenseItem

/**
 * ExpenseItemCard:
 * Signature interactive expense card for Raqeem.
 * Unslop Craft Principles:
 * - Nested radius: outer 16dp, inner badge 6dp.
 * - Hairline border: 1dp @ 6% alpha (pulsing emerald @ 45% when highlighted upon undo/repeat).
 * - Combined clickable: tap to edit, long press for contextual actions menu.
 * - One-way SwipeToDismissBox revealing Coral Rose background and tactile haptic threshold.
 * - Release snaps back and opens confirmation dialog (safe non-destructive gesture).
 * - Quick in-menu category switcher with animated category badge color transition.
 * - Fully accessible via screen reader custom actions.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ExpenseItemCard(
    item: ExpenseItem,
    isHighlighted: Boolean = false,
    onEdit: (ExpenseItem) -> Unit,
    onRequestDelete: (ExpenseItem) -> Unit,
    onRequestRepeat: (ExpenseItem) -> Unit,
    onChangeCategory: (ExpenseItem, ExpenseCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalRaqeemHaptics.current
    var showMenu by remember { mutableStateOf(false) }
    var isCategoryRowExpanded by remember { mutableStateOf(false) }

    val categoryColor = when (item.category) {
        ExpenseCategory.GROCERIES -> RaqeemColors.CatGroceries
        ExpenseCategory.DINING -> RaqeemColors.CatDining
        ExpenseCategory.TRANSPORT -> RaqeemColors.CatTransport
        ExpenseCategory.BILLS -> RaqeemColors.CatBills
        ExpenseCategory.HEALTH -> RaqeemColors.CatHealth
        ExpenseCategory.GENERAL -> RaqeemColors.CatGeneral
    }

    val animatedCatColor by animateColorAsState(
        targetValue = categoryColor,
        animationSpec = tween(350),
        label = "itemCatColor"
    )

    val borderColor = if (isHighlighted) {
        RaqeemColors.EmeraldGreen.copy(alpha = 0.5f)
    } else {
        Color.White.copy(alpha = 0.07f)
    }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue != SwipeToDismissBoxValue.Settled) {
                haptics.perform(HapticKind.TICK)
                onRequestDelete(item)
                false // Snap back with spring
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            val progress = dismissState.progress
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(RaqeemColors.CoralRose.copy(alpha = 0.16f + progress * 0.2f))
                    .border(1.dp, RaqeemColors.CoralRose.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                RaqeemIcons.Trash(
                    size = 22.dp,
                    tint = RaqeemColors.CoralRose,
                    modifier = Modifier.scale(0.85f + progress * 0.25f)
                )
            }
        },
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (showMenu) RaqeemColors.SurfaceElevated else RaqeemColors.SurfaceCard)
                .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = RaqeemColors.EmeraldGreen.copy(alpha = 0.12f)),
                    onClick = { onEdit(item) },
                    onLongClick = {
                        haptics.perform(HapticKind.TICK)
                        showMenu = true
                    }
                )
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction("تكرار المصروف") { onRequestRepeat(item); true },
                        CustomAccessibilityAction("تعديل المصروف") { onEdit(item); true },
                        CustomAccessibilityAction("حذف المصروف") { onRequestDelete(item); true }
                    )
                }
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Leading: Category color dot & titles
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(animatedCatColor)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = item.title,
                            color = RaqeemColors.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = item.category.arabicTitle,
                                color = animatedCatColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal
                            )

                            if (!item.payer.isCurrentUser) {
                                Text(
                                    text = "• دفعها ${item.payer.name}",
                                    color = RaqeemColors.TextTertiary,
                                    fontSize = 11.sp
                                )
                            } else if (item.split != null) {
                                if (item.split.allocations.isNotEmpty()) {
                                    val rem = item.split.allocations.sumOf { alloc ->
                                        val settled = alloc.settlements.filter { it.voidedAt == null }.sumOf { it.amount }
                                        (alloc.amount - settled).coerceAtLeast(0.0)
                                    }
                                    val badgeText = if (rem <= 0.0) "مسدَّد" else "قيد التحصيل $rem ${item.currency}"
                                    val badgeColor = if (rem <= 0.0) RaqeemColors.EmeraldGreen else RaqeemColors.AmberWarning
                                    Text(
                                        text = "• $badgeText",
                                        color = badgeColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                } else {
                                    Text(
                                        text = "• ${item.split.note.ifBlank { "مشاركة" }}",
                                        color = RaqeemColors.TextTertiary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Trailing: Amount and currency
                val amtFormatted = if (item.amount % 1.0 == 0.0) "${item.amount.toInt()}" else "${item.amount}"
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = amtFormatted,
                        color = RaqeemColors.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = item.currency,
                        color = RaqeemColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.padding(bottom = 1.dp)
                    )
                }
            }

            // Context Menu Dropdown
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = {
                    showMenu = false
                    isCategoryRowExpanded = false
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(RaqeemColors.SurfaceElevated)
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            ) {
                DropdownMenuItem(
                    text = { Text("تكرار المصروف", color = RaqeemColors.TextPrimary, fontSize = 14.sp) },
                    leadingIcon = { RaqeemIcons.Repeat(size = 18.dp, tint = RaqeemColors.EmeraldGreen) },
                    onClick = {
                        showMenu = false
                        onRequestRepeat(item)
                    }
                )

                DropdownMenuItem(
                    text = { Text("تغيير التصنيف", color = RaqeemColors.TextPrimary, fontSize = 14.sp) },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(animatedCatColor)
                        )
                    },
                    onClick = {
                        isCategoryRowExpanded = !isCategoryRowExpanded
                    }
                )

                // Quick Category Selector Chips
                if (isCategoryRowExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ExpenseCategory.entries.chunked(3).forEach { rowCats ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                rowCats.forEach { cat ->
                                    val catClr = when (cat) {
                                        ExpenseCategory.GROCERIES -> RaqeemColors.CatGroceries
                                        ExpenseCategory.DINING -> RaqeemColors.CatDining
                                        ExpenseCategory.TRANSPORT -> RaqeemColors.CatTransport
                                        ExpenseCategory.BILLS -> RaqeemColors.CatBills
                                        ExpenseCategory.HEALTH -> RaqeemColors.CatHealth
                                        ExpenseCategory.GENERAL -> RaqeemColors.CatGeneral
                                    }
                                    val isCurrent = cat == item.category
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isCurrent) catClr.copy(alpha = 0.2f) else RaqeemColors.SurfaceCard)
                                            .border(
                                                1.dp,
                                                if (isCurrent) catClr else Color.White.copy(alpha = 0.06f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                haptics.perform(HapticKind.TICK)
                                                onChangeCategory(item, cat)
                                                showMenu = false
                                                isCategoryRowExpanded = false
                                            }
                                            .padding(horizontal = 6.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(catClr))
                                            Text(
                                                text = cat.arabicTitle.take(6),
                                                color = if (isCurrent) catClr else RaqeemColors.TextSecondary,
                                                fontSize = 10.sp,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                DropdownMenuItem(
                    text = { Text("تعديل", color = RaqeemColors.TextPrimary, fontSize = 14.sp) },
                    leadingIcon = { RaqeemIcons.Edit(size = 18.dp, tint = RaqeemColors.ElectricCyan) },
                    onClick = {
                        showMenu = false
                        onEdit(item)
                    }
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

                DropdownMenuItem(
                    text = { Text("حذف", color = RaqeemColors.CoralRose, fontSize = 14.sp) },
                    leadingIcon = { RaqeemIcons.Trash(size = 18.dp, tint = RaqeemColors.CoralRose) },
                    onClick = {
                        showMenu = false
                        onRequestDelete(item)
                    }
                )
            }
        }
    }
}

