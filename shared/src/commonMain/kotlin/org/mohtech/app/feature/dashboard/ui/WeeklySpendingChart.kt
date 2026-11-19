package org.mohtech.app.feature.dashboard.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.mohtech.app.core.haptics.HapticFeedbackType
import org.mohtech.app.core.haptics.LocalRaqeemHaptics
import org.mohtech.app.core.theme.RaqeemColors
import org.mohtech.app.data.model.ExpenseItem
import kotlin.math.roundToLong

data class DaySpendingData(
    val dayIndex: Int,
    val dayName: String,
    val dayShortName: String,
    val date: LocalDate,
    val totalAmount: Double,
    val isToday: Boolean,
    val isExceeded: Boolean
)

fun calculateWeeklySpending(
    expenses: List<ExpenseItem>,
    weeklyBudget: Double,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    nowInstant: Instant = Clock.System.now()
): List<DaySpendingData> {
    val todayDate = nowInstant.toLocalDateTime(timeZone).date
    val daysSinceSaturday = when (todayDate.dayOfWeek) {
        DayOfWeek.SATURDAY -> 0
        DayOfWeek.SUNDAY -> 1
        DayOfWeek.MONDAY -> 2
        DayOfWeek.TUESDAY -> 3
        DayOfWeek.WEDNESDAY -> 4
        DayOfWeek.THURSDAY -> 5
        DayOfWeek.FRIDAY -> 6
    }

    val saturdayEpochDays = todayDate.toEpochDays() - daysSinceSaturday
    val dailyBudgetBenchmark = if (weeklyBudget > 0.0) weeklyBudget / 7.0 else 0.0
    val dayNames = listOf("السبت", "الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة")
    val dayShortNames = listOf("سبت", "أحد", "اثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة")

    val expensesByDate = expenses.groupBy { item ->
        Instant.fromEpochMilliseconds(item.timestamp).toLocalDateTime(timeZone).date
    }

    return (0..6).map { i ->
        val date = LocalDate.fromEpochDays(saturdayEpochDays + i)
        val dayExpenses = expensesByDate[date].orEmpty()
        val total = dayExpenses.sumOf { it.amount }
        val isToday = (date == todayDate)
        val isExceeded = (dailyBudgetBenchmark > 0.0 && total > dailyBudgetBenchmark)

        DaySpendingData(
            dayIndex = i,
            dayName = dayNames[i],
            dayShortName = dayShortNames[i],
            date = date,
            totalAmount = total,
            isToday = isToday,
            isExceeded = isExceeded
        )
    }
}

@Composable
fun WeeklySpendingChart(
    expenses: List<ExpenseItem>,
    weeklyBudget: Double,
    currency: String,
    modifier: Modifier = Modifier,
    onBarTap: ((DaySpendingData) -> Unit)? = null
) {
    val haptics = LocalRaqeemHaptics.current
    val days = remember(expenses, weeklyBudget) {
        calculateWeeklySpending(expenses, weeklyBudget)
    }
    val todayIndex = days.indexOfFirst { it.isToday }.coerceAtLeast(0)
    var selectedIndex by rememberSaveable { mutableStateOf(todayIndex) }
    val selectedDay = days.getOrNull(selectedIndex) ?: days[todayIndex]
    val dailyBudget = if (weeklyBudget > 0.0) weeklyBudget / 7.0 else 0.0
    val maxDaily = (days.maxOfOrNull { it.totalAmount } ?: 0.0)
        .coerceAtLeast(dailyBudget)
        .coerceAtLeast(1.0)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(RaqeemColors.SurfaceCard)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Column {
            // Header & Selected Day Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(RaqeemColors.ElectricCyan)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "الإنفاق الأسبوعي",
                        color = RaqeemColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Tooltip Pill showing selected day metrics
                AnimatedContent(
                    targetState = selectedDay,
                    label = "selectedDayTooltip"
                ) { day ->
                    val isExceeded = day.isExceeded
                    val badgeBg = if (isExceeded) RaqeemColors.CoralRose.copy(alpha = 0.15f) else RaqeemColors.SurfaceElevated
                    val badgeBorder = if (isExceeded) RaqeemColors.CoralRose.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f)
                    val textColor = if (isExceeded) RaqeemColors.CoralRose else RaqeemColors.EmeraldGreen
                    val formattedAmount = if (day.totalAmount % 1.0 == 0.0) {
                        day.totalAmount.toLong().toString()
                    } else {
                        ((day.totalAmount * 100).roundToLong() / 100.0).toString()
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(badgeBg)
                            .border(1.dp, badgeBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${day.dayName}${if (day.isToday) " (اليوم)" else ""}:",
                                color = RaqeemColors.TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal
                            )
                            Text(
                                text = "$formattedAmount $currency",
                                color = textColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 7-Day Bars Grid
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                days.forEach { day ->
                    val isSelected = (day.dayIndex == selectedIndex)
                    val heightRatio = (day.totalAmount / maxDaily).toFloat().coerceIn(0f, 1f)
                    val targetHeight = 6.dp + (80.dp * heightRatio)
                    val animatedHeight by animateDpAsState(
                        targetValue = targetHeight,
                        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                        label = "barHeight_${day.dayIndex}"
                    )

                    val barColor = when {
                        day.isExceeded -> RaqeemColors.CoralRose
                        day.totalAmount > 0.0 -> RaqeemColors.EmeraldGreen
                        else -> RaqeemColors.SurfaceElevated
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Color.White.copy(alpha = 0.04f) else Color.Transparent)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true, color = RaqeemColors.EmeraldGreen)
                            ) {
                                selectedIndex = day.dayIndex
                                haptics.trigger(HapticFeedbackType.LightTick)
                                onBarTap?.invoke(day)
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        // Bar Track & Active Fill
                        Box(
                            modifier = Modifier
                                .width(14.dp)
                                .height(86.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            // Background Track
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(RaqeemColors.SurfaceElevated.copy(alpha = 0.45f))
                            )

                            // Active Value Bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(animatedHeight)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(barColor)
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.dp,
                                        color = if (isSelected) Color.White.copy(alpha = 0.7f) else Color.Transparent,
                                        shape = RoundedCornerShape(7.dp)
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Day Label (>= 12sp)
                        Text(
                            text = day.dayShortName,
                            color = when {
                                day.isToday -> RaqeemColors.EmeraldGreen
                                isSelected -> RaqeemColors.TextPrimary
                                else -> RaqeemColors.TextTertiary
                            },
                            fontSize = 12.sp,
                            fontWeight = if (day.isToday || isSelected) FontWeight.Bold else FontWeight.Normal
                        )

                        // Today Indicator Dot
                        if (day.isToday) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(RaqeemColors.EmeraldGreen)
                            )
                        } else {
                            Spacer(modifier = Modifier.height(7.dp))
                        }
                    }
                }
            }
        }
    }
}
