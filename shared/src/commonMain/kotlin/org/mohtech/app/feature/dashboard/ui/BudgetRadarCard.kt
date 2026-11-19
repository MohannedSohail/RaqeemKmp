package org.mohtech.app.feature.dashboard.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
 */
@Composable
fun BudgetRadarCard(
    todayTotal: Double,
    weeklyTotal: Double,
    currency: String,
    weeklyBudget: Double = 2500.0,
    modifier: Modifier = Modifier
) {
    val progressRatio = if (weeklyBudget > 0.0) {
        (weeklyTotal / weeklyBudget).toFloat().coerceIn(0.04f, 1.0f)
    } else if (weeklyTotal > 0.0) {
        (todayTotal / weeklyTotal).toFloat().coerceIn(0.04f, 1.0f)
    } else {
        0.0f
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progressRatio,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "radarProgress"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(RaqeemColors.SurfaceCard)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Column {
            // Header: Section Context & Quiet Status
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
                            .background(RaqeemColors.EmeraldGreen)
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
                        .clip(RoundedCornerShape(6.dp))
                        .background(RaqeemColors.SurfaceElevated)
                        .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "محلي 100%",
                        color = RaqeemColors.TextTertiary,
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
                color = RaqeemColors.EmeraldGreen,
                trackColor = RaqeemColors.SurfaceElevated,
                strokeCap = StrokeCap.Round
            )

            if (weeklyBudget > 0.0) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val percent = ((weeklyTotal / weeklyBudget) * 100).toInt().coerceAtLeast(0)
                    Text(
                        text = "استهلاك الميزانية ($percent%)",
                        color = RaqeemColors.TextTertiary,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "${if (weeklyBudget % 1.0 == 0.0) weeklyBudget.toInt() else weeklyBudget} $currency",
                        color = RaqeemColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
