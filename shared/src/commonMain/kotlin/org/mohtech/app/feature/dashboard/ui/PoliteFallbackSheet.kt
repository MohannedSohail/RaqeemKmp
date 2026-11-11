package org.mohtech.app.feature.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mohtech.app.core.theme.RaqeemColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PoliteFallbackSheet(
    currency: String,
    onParseText: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var textInput by remember { mutableStateOf("") }

    val quickChips = listOf(
        "☕ قهوة بعشرة $currency",
        "⛽ بنزين ب 50 $currency",
        "🛒 سوبرماركت 120 $currency",
        "💊 دواء من الصيدلية 45 $currency",
        "⚡ فاتورة كهرباء 200 $currency",
        "🍽️ عشاء بستين $currency بيني وبين أحمد"
    )

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
                Column {
                    Text(
                        text = "التسجيل السريع بالكتابة أو الميكروفون",
                        color = RaqeemColors.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "يمكنك استخدام ميكروفون لوحة المفاتيح (Gboard) أو الكتابة",
                        color = RaqeemColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }

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

            Spacer(modifier = Modifier.height(18.dp))

            // Text Input Field
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "اكتب أو أملِ صوتياً: «بنزين بـ 50 $currency» أو «عشاء 80 $currency دفعها محمد»",
                        color = RaqeemColors.TextTertiary,
                        fontSize = 13.sp
                    )
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RaqeemColors.EmeraldGreen,
                    unfocusedBorderColor = RaqeemColors.SurfaceBorder,
                    focusedTextColor = RaqeemColors.TextPrimary,
                    unfocusedTextColor = RaqeemColors.TextPrimary,
                    focusedContainerColor = RaqeemColors.SurfaceElevated,
                    unfocusedContainerColor = RaqeemColors.SurfaceElevated
                ),
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Preset Expense Chips
            Text(
                text = "أو اختر من المصاريف الجاهزة بنقرة واحدة:",
                color = RaqeemColors.TextSecondary,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                quickChips.forEach { chipText ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(RaqeemColors.SurfaceElevated)
                            .border(1.dp, RaqeemColors.SurfaceBorder, RoundedCornerShape(10.dp))
                            .clickable {
                                onParseText(chipText)
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = chipText,
                            color = RaqeemColors.TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Submit Button
            Button(
                onClick = {
                    if (textInput.isNotBlank()) {
                        onParseText(textInput)
                    }
                },
                enabled = textInput.isNotBlank(),
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
                Text(
                    text = "تحليل وحفظ المصروف",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

