package org.mohtech.app.feature.dashboard.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mohtech.app.core.theme.RaqeemColors

/**
 * BudgetRadarCard:
 * The signature focal element of the dashboard.
 * Unslop Craft Principles:
 * - Nested radius: outer 20dp, inner badge 8dp.
 * - Hairline border: 1dp @ 8% opacity.
 * - Calm, exact fintech metrics without decorative noise.
 * - Dynamically calibrated to user weekly budget:
 *   * <= 70%: Emerald Green
 *   * 70%..100%: Amber Warning
 *   * > 100%: Coral Rose Over-budget Alert
 * - Clean unset state inviting user to configure weekly budget.
 */
@Composable
fun BudgetRadarCard(
    todayTotal: Double,
    weeklyTotal: Double,
    currency: String,
    weeklyBudget: Double = 0.0,
    onConfigureBudget: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isBudgetSet = weeklyBudget > 0.0
    val budgetRatio = if (isBudgetSet) {
        (weeklyTotal / weeklyBudget).toFloat()
    } else {
        0.0f
    }

    val progressClamped = budgetRatio.coerceIn(0.0f, 1.0f)
    val animatedProgress by animateFloatAsState(
        targetValue = if (isBudgetSet) maxOf(0.04f, progressClamped) else 0.0f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "radarProgress"
    )

    val targetStatusColor = when {
        !isBudgetSet -> RaqeemColors.TextSecondary
        budgetRatio > 1.0f -> RaqeemColors.CoralRose
        budgetRatio >= 0.70f -> Color(0xFFFFA000) // Amber
        else -> RaqeemColors.EmeraldGreen
    }

    val animatedStatusColor by animateColorAsState(
        targetValue = targetStatusColor,
        animationSpec = tween(durationMillis = 400),
        label = "statusColor"
    )

    val badgeText = if (isBudgetSet) {
        val budgetFormatted = if (weeklyBudget % 1.0 == 0.0) "${weeklyBudget.toInt()}" else "$weeklyBudget"
        "الميزانية $budgetFormatted $currency"
    } else {
        "حدد الميزانية"
    }

    val statusSubtitle = when {
        !isBudgetSet -> "حدد ميزانيتك الأسبوعية"
        budgetRatio > 1.0f -> {
            val exceeded = weeklyTotal - weeklyBudget
            val diffFormatted = if (exceeded % 1.0 == 0.0) "${exceeded.toInt()}" else "${exceeded.toInt()}"
            "تجاوز بمقدار $diffFormatted $currency"
        }
        else -> {
            val remaining = weeklyBudget - weeklyTotal
            val remFormatted = if (remaining % 1.0 == 0.0) "${remaining.toInt()}" else "${remaining.toInt()}"
            "المتبقي $remFormatted $currency"
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(RaqeemColors.SurfaceCard)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = RaqeemColors.EmeraldGreen.copy(alpha = 0.15f)),
                onClick = onConfigureBudget
            )
            .padding(20.dp)
    ) {
        Column {
            // Header: Section Context & Budget Badge
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
                            .background(animatedStatusColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "مؤشر الإنفاق",
                        color = RaqeemColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(RaqeemColors.SurfaceElevated)
                        .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = if (isBudgetSet) RaqeemColors.TextPrimary else RaqeemColors.EmeraldGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Totals Breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // Today's Total
                Column {
                    Text(
                        text = "اليوم",
                        color = RaqeemColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (todayTotal % 1.0 == 0.0) "${todayTotal.toInt()}" else "$todayTotal",
                            color = RaqeemColors.TextPrimary,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = currency,
                            color = RaqeemColors.EmeraldGreen,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }

                // Weekly Total
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "الأسبوع",
                        color = RaqeemColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (weeklyTotal % 1.0 == 0.0) "${weeklyTotal.toInt()}" else "$weeklyTotal",
                            color = RaqeemColors.TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = currency,
                            color = RaqeemColors.TextTertiary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Calibrated Ratio Bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = animatedStatusColor,
                trackColor = RaqeemColors.SurfaceElevated,
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Footer Subtitle Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = statusSubtitle,
                    color = animatedStatusColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                if (isBudgetSet) {
                    val percentageInt = (budgetRatio * 100).toInt()
                    Text(
                        text = "$percentageInt%",
                        color = RaqeemColors.TextTertiary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
