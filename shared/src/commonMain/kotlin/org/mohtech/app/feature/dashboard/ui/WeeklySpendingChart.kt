package org.mohtech.app.feature.dashboard.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.mohtech.app.core.haptics.HapticKind
import org.mohtech.app.core.haptics.LocalRaqeemHaptics
import org.mohtech.app.core.theme.RaqeemColors
import org.mohtech.app.data.model.ExpenseItem

data class DaySpendingData(
    val dayIndex: Int,
    val dayName: String,
    val dayShortName: String,
    val date: LocalDate,
    val totalAmount: Double,
    val expensesCount: Int,
    val isToday: Boolean,
    val isExceeded: Boolean
)

fun calculateWeeklySpending(
    expenses: List<ExpenseItem>,
    weeklyBudget: Double,
    weekStartDayIso: Int = 6, // 6 = Saturday (Default in Arab region)
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    nowInstant: Instant = Clock.System.now()
): List<DaySpendingData> {
    val todayDate = nowInstant.toLocalDateTime(timeZone).date
    val todayIso = todayDate.dayOfWeek.ordinal + 1 // Monday=1 ... Sunday=7

    val daysSinceStart = (todayIso - weekStartDayIso + 7) % 7
    val startEpochDays = todayDate.toEpochDays() - daysSinceStart
    val dailyBudgetBenchmark = if (weeklyBudget > 0.0) weeklyBudget / 7.0 else 0.0

    val arabicNames = mapOf(
        DayOfWeek.SATURDAY to Pair("السبت", "سبت"),
        DayOfWeek.SUNDAY to Pair("الأحد", "أحد"),
        DayOfWeek.MONDAY to Pair("الاثنين", "اثنين"),
        DayOfWeek.TUESDAY to Pair("الثلاثاء", "ثلاثاء"),
        DayOfWeek.WEDNESDAY to Pair("الأربعاء", "أربعاء"),
        DayOfWeek.THURSDAY to Pair("الخميس", "خميس"),
        DayOfWeek.FRIDAY to Pair("الجمعة", "جمعة")
    )

    val expensesByDate = expenses
        .filter { it.deletedAt == null }
        .groupBy { item ->
            Instant.fromEpochMilliseconds(item.timestamp).toLocalDateTime(timeZone).date
        }

    return (0..6).map { i ->
        val date = LocalDate.fromEpochDays(startEpochDays + i)
        val dayExpenses = expensesByDate[date].orEmpty()
        val total = dayExpenses.sumOf { it.effectiveAmount }
        val isToday = (date == todayDate)
        val isExceeded = (dailyBudgetBenchmark > 0.0 && total > dailyBudgetBenchmark)
        val names = arabicNames[date.dayOfWeek] ?: Pair("اليوم", "يوم")

        DaySpendingData(
            dayIndex = i,
            dayName = names.first,
            dayShortName = names.second,
            date = date,
            totalAmount = total,
            expensesCount = dayExpenses.size,
            isToday = isToday,
            isExceeded = isExceeded
        )
    }
}

/**
 * WeeklySpendingChart:
 * Interactive scrubbing bar chart with tactile feedback and RTL coordinate reversal.
 * 
 * Rules (Axis D3):
 * - Horizontal drag scrubbing (detectHorizontalDragGestures) with slop threshold.
 * - RTL x coordinate to day-index reversal.
 * - Week start day from user preference (Saturday default).
 * - Tooltip on solid SurfaceElevated with hairline border (no fake blur).
 * - Tactile tick haptic on day change.
 * - Screen reader semantics description.
 */
@Composable
fun WeeklySpendingChart(
    expenses: List<ExpenseItem>,
    weeklyBudget: Double,
    currency: String,
    weekStartDayIso: Int = 6,
    modifier: Modifier = Modifier,
    onBarTap: ((DaySpendingData) -> Unit)? = null
) {
    val haptics = LocalRaqeemHaptics.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    val days = remember(expenses, weeklyBudget, weekStartDayIso) {
        calculateWeeklySpending(expenses, weeklyBudget, weekStartDayIso)
    }
    val todayIndex = days.indexOfFirst { it.isToday }.coerceAtLeast(0)
    var selectedIndex by rememberSaveable { mutableStateOf(todayIndex) }
    val selectedDay = days.getOrNull(selectedIndex) ?: days[todayIndex]
    val dailyBudget = if (weeklyBudget > 0.0) weeklyBudget / 7.0 else 0.0
    val maxDaily = (days.maxOfOrNull { it.totalAmount } ?: 0.0)
        .coerceAtLeast(dailyBudget)
        .coerceAtLeast(1.0)

    var chartRowWidthPx by remember { mutableStateOf(1f) }

    val formattedAmount = if (selectedDay.totalAmount % 1.0 == 0.0) {
        selectedDay.totalAmount.toLong().toString()
    } else {
        ((selectedDay.totalAmount * 100).roundToLong() / 100.0).toString()
    }

    val countText = when (selectedDay.expensesCount) {
        0 -> "بلا مصاريف"
        1 -> "مصروف واحد"
        2 -> "مصروفان"
        in 3..10 -> "${selectedDay.expensesCount} مصاريف"
        else -> "${selectedDay.expensesCount} مصروفاً"
    }

    val a11yDescription = "رسم بياني للإنفاق الأسبوعي. اليوم المحدد: ${selectedDay.dayName}، بمبلغ $formattedAmount $currency، $countText."

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(RaqeemColors.SurfaceCard)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .semantics { contentDescription = a11yDescription }
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

                // Tooltip: Solid SurfaceElevated with hairline border (strictly no fake glass blur)
                AnimatedContent(
                    targetState = selectedDay,
                    label = "selectedDayTooltip"
                ) { day ->
                    val isExceeded = day.isExceeded
                    val badgeBg = if (isExceeded) RaqeemColors.CoralRose.copy(alpha = 0.15f) else RaqeemColors.SurfaceElevated
                    val badgeBorder = if (isExceeded) RaqeemColors.CoralRose.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f)
                    val textColor = if (isExceeded) RaqeemColors.CoralRose else RaqeemColors.EmeraldGreen

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
                                text = "$formattedAmount $currency، $countText",
                                color = textColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 7-Day Bars Grid with Horizontal Drag Scrubbing
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .onSizeChanged { chartRowWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                    .pointerInput(days, isRtl) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                val normalized = if (isRtl) {
                                    1f - (offset.x / chartRowWidthPx).coerceIn(0f, 0.999f)
                                } else {
                                    (offset.x / chartRowWidthPx).coerceIn(0f, 0.999f)
                                }
                                val newIdx = (normalized * 7).toInt().coerceIn(0, 6)
                                if (newIdx != selectedIndex) {
                                    selectedIndex = newIdx
                                    haptics.perform(HapticKind.TICK)
                                    onBarTap?.invoke(days[newIdx])
                                }
                            },
                            onHorizontalDrag = { change, _ ->
                                val x = change.position.x
                                val normalized = if (isRtl) {
                                    1f - (x / chartRowWidthPx).coerceIn(0f, 0.999f)
                                } else {
                                    (x / chartRowWidthPx).coerceIn(0f, 0.999f)
                                }
                                val newIdx = (normalized * 7).toInt().coerceIn(0, 6)
                                if (newIdx != selectedIndex) {
                                    selectedIndex = newIdx
                                    haptics.perform(HapticKind.TICK)
                                    onBarTap?.invoke(days[newIdx])
                                }
                                change.consume()
                            }
                        )
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                days.forEach { day ->
                    val isSelected = (day.dayIndex == selectedIndex)
                    val heightRatio = (day.totalAmount / maxDaily).toFloat().coerceIn(0f, 1f)
                    val targetHeight = 6.dp + (80.dp * heightRatio)
                    val animatedHeight by animateDpAsState(
                        targetValue = targetHeight,
                        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
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
                                if (selectedIndex != day.dayIndex) {
                                    selectedIndex = day.dayIndex
                                    haptics.perform(HapticKind.TICK)
                                    onBarTap?.invoke(day)
                                }
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
                                        color = if (isSelected) Color.White.copy(alpha = 0.85f) else Color.Transparent,
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
