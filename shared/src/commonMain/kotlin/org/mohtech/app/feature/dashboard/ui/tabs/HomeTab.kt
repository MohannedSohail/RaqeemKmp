package org.mohtech.app.feature.dashboard.ui.tabs

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mohtech.app.core.theme.RaqeemColors
import org.mohtech.app.data.model.ExpenseCategory
import org.mohtech.app.data.model.ExpenseItem
import org.mohtech.app.feature.dashboard.ExpenseUiState
import org.mohtech.app.feature.dashboard.ui.BudgetRadarCard
import org.mohtech.app.feature.dashboard.ui.ExpenseItemCard

/**
 * HomeTab:
 * The primary dashboard tab in Raqeem.
 * Contains:
 * - BudgetRadarCard (focal point with dynamic ratio, status color and budget editor)
 * - Last 5 expenses with "عرض الكل" action leading to HistoryTab
 */
@Composable
fun HomeTab(
    state: ExpenseUiState,
    onConfigureBudget: () -> Unit,
    onShowAllHistory: () -> Unit,
    onEditExpense: (ExpenseItem) -> Unit,
    onRequestDelete: (ExpenseItem) -> Unit,
    onRequestRepeat: (ExpenseItem) -> Unit,
    onChangeCategory: (ExpenseItem, ExpenseCategory) -> Unit,
    onOpenReceivables: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val top5Expenses = remember(state.recentExpenses) {
        state.recentExpenses.distinctBy { it.id }.take(5)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            BudgetRadarCard(
                todayTotal = state.todayTotal,
                weeklyTotal = state.weeklyTotal,
                currency = state.activeCurrency,
                weeklyBudget = state.weeklyBudget,
                onConfigureBudget = onConfigureBudget
            )
        }

        if (state.receivablesTotalRemaining > 0.0) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                        .background(RaqeemColors.SurfaceCard)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(color = RaqeemColors.ElectricCyan.copy(alpha = 0.2f)),
                            onClick = onOpenReceivables
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "مستحقات لك لدى الآخرين",
                                color = RaqeemColors.TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${state.receivablesTotalRemaining} ${state.activeCurrency}",
                                color = RaqeemColors.ElectricCyan,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "عرض الدفتر",
                            color = RaqeemColors.EmeraldGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "آخر المصروفات",
                    color = RaqeemColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                if (state.recentExpenses.size > 5) {
                    Text(
                        text = "عرض الكل",
                        color = RaqeemColors.EmeraldGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(color = RaqeemColors.EmeraldGreen.copy(alpha = 0.2f)),
                                onClick = onShowAllHistory
                            )
                            .padding(4.dp)
                    )
                }
            }
        }

        if (top5Expenses.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "لا توجد مصروفات مسجلة بعد. اضغط على الميكروفون للتسجيل.",
                        color = RaqeemColors.TextTertiary,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            items(
                items = top5Expenses,
                key = { it.id }
            ) { item ->
                ExpenseItemCard(
                    item = item,
                    isHighlighted = item.id == state.highlightedExpenseId,
                    onEdit = onEditExpense,
                    onRequestDelete = onRequestDelete,
                    onRequestRepeat = onRequestRepeat,
                    onChangeCategory = onChangeCategory,
                    modifier = Modifier.animateItem()
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(88.dp))
        }
    }
}

