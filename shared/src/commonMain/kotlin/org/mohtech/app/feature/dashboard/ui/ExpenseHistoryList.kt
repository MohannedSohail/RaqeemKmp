package org.mohtech.app.feature.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mohtech.app.core.theme.RaqeemColors
import org.mohtech.app.core.ui.RaqeemIcons
import org.mohtech.app.data.model.ExpenseCategory
import org.mohtech.app.data.model.ExpenseItem

@Composable
fun ExpenseHistorySection(
    expenses: List<ExpenseItem>,
    selectedCategory: ExpenseCategory?,
    onSelectCategory: (ExpenseCategory?) -> Unit,
    onDeleteExpense: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "المصروفات الأخيرة",
                color = RaqeemColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${expenses.size} عملية",
                color = RaqeemColors.TextTertiary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Horizontal Category Filter Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            item {
                CategoryChip(
                    title = "الكل",
                    isSelected = selectedCategory == null,
                    categoryColor = RaqeemColors.EmeraldGreen,
                    onClick = { onSelectCategory(null) }
                )
            }
            items(ExpenseCategory.entries.toTypedArray()) { category ->
                CategoryChip(
                    title = category.arabicTitle,
                    isSelected = selectedCategory == category,
                    categoryColor = Color(category.colorHex),
                    onClick = { onSelectCategory(category) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Expenses List or Empty State
        if (expenses.isEmpty()) {
            EmptyExpensesCard()
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                expenses.forEach { item ->
                    ExpenseItemCard(
                        item = item,
                        onDelete = { onDeleteExpense(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(
    title: String,
    isSelected: Boolean,
    categoryColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) RaqeemColors.SurfaceElevated else RaqeemColors.SurfaceCard)
            .border(
                1.dp,
                if (isSelected) categoryColor.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.06f),
                RoundedCornerShape(10.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = categoryColor),
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(categoryColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                color = if (isSelected) RaqeemColors.TextPrimary else RaqeemColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun ExpenseItemCard(
    item: ExpenseItem,
    onDelete: () -> Unit
) {
    val categoryColor = Color(item.category.colorHex)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RaqeemColors.SurfaceCard)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Right: Category indicator + Details
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Category Visual Anchor (Geometric Badge)
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(categoryColor.copy(alpha = 0.12f))
                        .border(1.dp, categoryColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(categoryColor)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = item.title,
                        color = RaqeemColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.category.arabicTitle,
                            color = RaqeemColors.TextTertiary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal
                        )

                        // Payer or Split status chip
                        if (!item.payer.isCurrentUser) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "• دفعها ${item.payer.name}",
                                color = RaqeemColors.AmberWarning,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        } else if (item.split != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "• حصتك ${item.split.amountPerPerson} ${item.currency}",
                                color = RaqeemColors.ElectricCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Left: Amount + Quiet Delete button
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (item.amount % 1.0 == 0.0) "${item.amount.toInt()}" else "${item.amount}",
                            color = RaqeemColors.TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = item.currency,
                            color = RaqeemColors.EmeraldGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Delete Button (Clean Line Trash Icon, touch target >= 48dp)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false, radius = 20.dp, color = RaqeemColors.CoralRose),
                            onClick = onDelete
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    RaqeemIcons.Trash(
                        size = 16.dp,
                        tint = RaqeemColors.TextTertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyExpensesCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RaqeemColors.SurfaceCard)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .padding(horizontal = 24.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(RaqeemColors.SurfaceElevated)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                RaqeemIcons.MicWave(
                    size = 24.dp,
                    tint = RaqeemColors.EmeraldGreen
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "لا توجد مصروفات مسجلة حتى الآن",
                color = RaqeemColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "اضغط على زر التسجيل وتحدث بطبيعتك:\n«بنزين بـ 50 شيكل» أو «قهوة بعشرة شيكل»",
                color = RaqeemColors.TextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}
