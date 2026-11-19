package org.mohtech.app.feature.dashboard.ui.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.mohtech.app.core.theme.RaqeemColors
import org.mohtech.app.data.model.ExpenseCategory
import org.mohtech.app.data.model.ExpenseItem
import org.mohtech.app.feature.dashboard.ExpenseUiState
import org.mohtech.app.feature.dashboard.ui.ExpenseItemCard

/**
 * HistoryTab:
 * Full expenses ledger with:
 * - Search text field
 * - Category filter chips
 * - Date grouped sticky headers
 * - Interactive ExpenseItemCard items with swipe to delete and long press menu
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryTab(
    state: ExpenseUiState,
    onSearchQueryChange: (String) -> Unit,
    onSelectCategoryFilter: (ExpenseCategory?) -> Unit,
    onEditExpense: (ExpenseItem) -> Unit,
    onRequestDelete: (ExpenseItem) -> Unit,
    onRequestRepeat: (ExpenseItem) -> Unit,
    onChangeCategory: (ExpenseItem, ExpenseCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val timeZone = TimeZone.currentSystemDefault()

    val distinctFilteredExpenses = remember(state.filteredExpenses) {
        state.filteredExpenses.distinctBy { it.id }
    }

    // Group expenses by formatted date
    val groupedExpenses = remember(distinctFilteredExpenses) {
        distinctFilteredExpenses.groupBy { item ->
            val date = Instant.fromEpochMilliseconds(item.timestamp).toLocalDateTime(timeZone).date
            "${date.year}-${date.monthNumber.toString().padStart(2, '0')}-${date.dayOfMonth.toString().padStart(2, '0')}"
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Search & Filter Header
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Search Input Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(RaqeemColors.SurfaceCard)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    if (state.searchQuery.isEmpty()) {
                        Text(
                            text = "بحث بالاسم أو الدافع...",
                            color = RaqeemColors.TextTertiary,
                            fontSize = 13.sp
                        )
                    }
                    BasicTextField(
                        value = state.searchQuery,
                        onValueChange = onSearchQueryChange,
                        textStyle = TextStyle(
                            color = RaqeemColors.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(RaqeemColors.EmeraldGreen),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Category Filter Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        val isAllSelected = state.selectedCategoryFilter == null
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isAllSelected) RaqeemColors.SurfaceElevated else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (isAllSelected) RaqeemColors.EmeraldGreen else Color.White.copy(alpha = 0.08f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onSelectCategoryFilter(null) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "الكل",
                                color = if (isAllSelected) RaqeemColors.EmeraldGreen else RaqeemColors.TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    items(ExpenseCategory.entries, key = { it.name }) { cat ->
                        val isSelected = state.selectedCategoryFilter == cat
                        val catClr = when (cat) {
                            ExpenseCategory.GROCERIES -> RaqeemColors.CatGroceries
                            ExpenseCategory.DINING -> RaqeemColors.CatDining
                            ExpenseCategory.TRANSPORT -> RaqeemColors.CatTransport
                            ExpenseCategory.BILLS -> RaqeemColors.CatBills
                            ExpenseCategory.HEALTH -> RaqeemColors.CatHealth
                            ExpenseCategory.GENERAL -> RaqeemColors.CatGeneral
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) catClr.copy(alpha = 0.15f) else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (isSelected) catClr else Color.White.copy(alpha = 0.08f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onSelectCategoryFilter(cat) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(catClr))
                                Text(
                                    text = cat.arabicTitle,
                                    color = if (isSelected) catClr else RaqeemColors.TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }

        if (distinctFilteredExpenses.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (state.searchQuery.isNotEmpty()) "لا توجد نتائج مطابقة للبحث" else "السجل فارغ",
                        color = RaqeemColors.TextTertiary,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            groupedExpenses.forEach { (dateKey, itemsForDate) ->
                stickyHeader(key = "header_$dateKey") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(RaqeemColors.BackgroundSlate)
                            .padding(vertical = 6.dp)
                    ) {
                        Text(
                            text = dateKey,
                            color = RaqeemColors.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                items(
                    items = itemsForDate.distinctBy { it.id },
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
        }

        item {
            Spacer(modifier = Modifier.height(88.dp))
        }
    }
}

