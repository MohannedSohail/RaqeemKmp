package org.mohtech.app.feature.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mohtech.app.core.theme.RaqeemColors
import org.mohtech.app.data.model.ExpenseBatch
import org.mohtech.app.data.model.ExpenseCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseReviewSheet(
    batch: ExpenseBatch,
    onConfirm: (ExpenseBatch) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val firstItem = batch.items.firstOrNull() ?: return

    var selectedCategory by remember { mutableStateOf(firstItem.category) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RaqeemColors.SurfaceCard,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(RaqeemColors.SurfaceBorder)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "مراجعة المصروف الصوتي",
                    color = RaqeemColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(RaqeemColors.SurfaceElevated)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "✕", color = RaqeemColors.TextSecondary, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Raw Spoken Quote
            if (firstItem.rawSpokenText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(RaqeemColors.SurfaceElevated)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "« ${firstItem.rawSpokenText} »",
                        color = RaqeemColors.TextSecondary,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Extracted Title & Amount Hero Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(RaqeemColors.SurfaceElevated)
                    .border(1.dp, RaqeemColors.SurfaceBorder, RoundedCornerShape(16.dp))
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "المشتريات المكتشفة",
                            color = RaqeemColors.TextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = firstItem.title,
                            color = RaqeemColors.TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "المبلغ المحسوب",
                            color = RaqeemColors.TextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${firstItem.amount}",
                                color = RaqeemColors.EmeraldGreen,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = firstItem.currency,
                                color = RaqeemColors.EmeraldGreen,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Payer & Split Info
            if (!firstItem.payer.isCurrentUser || firstItem.split != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(RaqeemColors.SurfaceElevated)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "الدافع: ${firstItem.payer.name}",
                        color = RaqeemColors.AmberWarning,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    firstItem.split?.let { split ->
                        Text(
                            text = "حصة كل شخص: ${split.amountPerPerson} ${firstItem.currency}",
                            color = RaqeemColors.ElectricCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            // Category Change Row
            Text(
                text = "التصنيف المقترح (انقر لتعديله):",
                color = RaqeemColors.TextSecondary,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ExpenseCategory.entries.toTypedArray()) { cat ->
                    val isSelected = selectedCategory == cat
                    val catColor = Color(cat.colorHex)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) catColor.copy(alpha = 0.25f) else RaqeemColors.SurfaceElevated)
                            .border(1.dp, if (isSelected) catColor else RaqeemColors.SurfaceBorder, RoundedCornerShape(10.dp))
                            .clickable { selectedCategory = cat }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = cat.arabicTitle,
                            color = if (isSelected) catColor else RaqeemColors.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // Confirm Button
            Button(
                onClick = {
                    val updatedItems = batch.items.map { it.copy(category = selectedCategory) }
                    onConfirm(batch.copy(items = updatedItems))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RaqeemColors.EmeraldGreen,
                    contentColor = RaqeemColors.BackgroundSlate
                )
            ) {
                Text(
                    text = "✓ تأكيد وحفظ المصروف",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

