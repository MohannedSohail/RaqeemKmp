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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mohtech.app.core.haptics.HapticKind
import org.mohtech.app.core.haptics.LocalRaqeemHaptics
import org.mohtech.app.core.theme.RaqeemColors
import org.mohtech.app.core.ui.RaqeemIcons
import org.mohtech.app.feature.dashboard.ExpenseUiState

/**
 * SettingsTab:
 * Configuration, localized budget preferences, currency settings, and honest privacy assurances.
 */
@Composable
fun SettingsTab(
    state: ExpenseUiState,
    onOpenBudgetEditor: () -> Unit,
    onSelectCurrency: (String) -> Unit,
    onSelectWeekStartDay: (Int) -> Unit,
    onRequestMicPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalRaqeemHaptics.current

    val currencies = listOf(
        "₪" to "شيكل",
        "ر.س" to "ريال",
        "ج.م" to "جنيه",
        "د.أ" to "دينار",
        "$" to "دولار"
    )

    val weekStartDays = listOf(
        6 to "السبت",
        7 to "الأحد",
        1 to "الاثنين"
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: Finance Preferences
        item {
            Text(
                text = "التفضيلات المالية",
                color = RaqeemColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Weekly Budget Setting Row
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(RaqeemColors.SurfaceCard)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = RaqeemColors.EmeraldGreen.copy(alpha = 0.15f)),
                        onClick = {
                            haptics.perform(HapticKind.TICK)
                            onOpenBudgetEditor()
                        }
                    )
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "الميزانية الأسبوعية",
                            color = RaqeemColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (state.weeklyBudget > 0.0) {
                                "${state.weeklyBudget.toInt()} ${state.activeCurrency}"
                            } else {
                                "غير محددة (اضغط للتعيين)"
                            },
                            color = if (state.weeklyBudget > 0.0) RaqeemColors.EmeraldGreen else RaqeemColors.TextTertiary,
                            fontSize = 12.sp
                        )
                    }

                    RaqeemIcons.Edit(size = 18.dp, tint = RaqeemColors.TextSecondary)
                }
            }
        }

        // Currency Setting Row
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(RaqeemColors.SurfaceCard)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "العملة الأساسية",
                        color = RaqeemColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        currencies.forEach { (sym, name) ->
                            val isSelected = state.activeCurrency == sym
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) RaqeemColors.SurfaceElevated else Color.Transparent)
                                    .border(
                                        1.dp,
                                        if (isSelected) RaqeemColors.EmeraldGreen else Color.White.copy(alpha = 0.08f),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        haptics.perform(HapticKind.TICK)
                                        onSelectCurrency(sym)
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = sym,
                                    color = if (isSelected) RaqeemColors.EmeraldGreen else RaqeemColors.TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Week Start Day Setting Row
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(RaqeemColors.SurfaceCard)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "بداية الأسبوع المالي",
                        color = RaqeemColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        weekStartDays.forEach { (dayIso, dayName) ->
                            val isSelected = state.weekStartDay == dayIso
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) RaqeemColors.SurfaceElevated else Color.Transparent)
                                    .border(
                                        1.dp,
                                        if (isSelected) RaqeemColors.EmeraldGreen else Color.White.copy(alpha = 0.08f),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        haptics.perform(HapticKind.TICK)
                                        onSelectWeekStartDay(dayIso)
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = dayName,
                                    color = if (isSelected) RaqeemColors.EmeraldGreen else RaqeemColors.TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Hardware & Mic Permission
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(RaqeemColors.SurfaceCard)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "إذن الميكروفون",
                            color = RaqeemColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "مطلوب لتسجيل الصوت ومعالجة المصاريف على جهازك",
                            color = RaqeemColors.TextTertiary,
                            fontSize = 12.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(RaqeemColors.SurfaceElevated)
                            .border(1.dp, RaqeemColors.EmeraldGreen.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .clickable {
                                haptics.perform(HapticKind.TICK)
                                onRequestMicPermission()
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "فحص / طلب",
                            color = RaqeemColors.EmeraldGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Privacy Guarantee (Honest statements, zero exaggerated marketing)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(RaqeemColors.SurfaceCard)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RaqeemIcons.Shield(size = 18.dp, tint = RaqeemColors.EmeraldGreen)
                        Text(
                            text = "الخصوصية وسرية البيانات",
                            color = RaqeemColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = "يعمل رقيم بشكل كامل على هاتفك دون حاجة للإنترنت. لا يتم إرسال أي تسجيلات صوتية أو بيانات مالية إلى أي جهة أو خوادم خارجية.",
                        color = RaqeemColors.TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

