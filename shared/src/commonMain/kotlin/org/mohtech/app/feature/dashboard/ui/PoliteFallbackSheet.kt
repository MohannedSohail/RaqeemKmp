package org.mohtech.app.feature.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mohtech.app.core.theme.RaqeemColors
import org.mohtech.app.core.ui.RaqeemIcons

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PoliteFallbackSheet(
    currency: String,
    onParseText: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var textInput by rememberSaveable { mutableStateOf("") }

    val quickChips = listOf(
        Pair("قهوة بعشرة $currency", RaqeemColors.CatDining),
        Pair("بنزين بـ 50 $currency", RaqeemColors.CatTransport),
        Pair("سوبرماركت 120 $currency", RaqeemColors.CatGroceries),
        Pair("صيدلية 45 $currency", RaqeemColors.CatHealth),
        Pair("فاتورة كهرباء 200 $currency", RaqeemColors.CatBills),
        Pair("عشاء بستين $currency بيني وبين أحمد", RaqeemColors.ElectricCyan)
    )

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
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "التسجيل النصي السريع",
                        color = RaqeemColors.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "يمكنك الكتابة أو استخدام ميكروفون لوحة المفاتيح",
                        color = RaqeemColors.TextSecondary,
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

            Spacer(modifier = Modifier.height(16.dp))

            // Text Input Field
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "مثال: «بنزين بـ 50 $currency» أو «عشاء 80 $currency على حساب أحمد»",
                        color = RaqeemColors.TextTertiary,
                        fontSize = 13.sp
                    )
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RaqeemColors.EmeraldGreen,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                    focusedTextColor = RaqeemColors.TextPrimary,
                    unfocusedTextColor = RaqeemColors.TextPrimary,
                    focusedContainerColor = RaqeemColors.SurfaceElevated,
                    unfocusedContainerColor = RaqeemColors.SurfaceElevated
                ),
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Preset Expense Chips (Clean Dots, zero emojis)
            Text(
                text = "أو اختر صيغة جاهزة:",
                color = RaqeemColors.TextTertiary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal
            )
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickChips.forEach { (phrase, dotColor) ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(RaqeemColors.SurfaceElevated)
                            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
                            .clickable {
                                onParseText(phrase)
                                onDismiss()
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = phrase,
                                color = RaqeemColors.TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Button
            Button(
                onClick = {
                    if (textInput.isNotBlank()) {
                        onParseText(textInput)
                        onDismiss()
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
                    text = "تحليل وحفظ",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
