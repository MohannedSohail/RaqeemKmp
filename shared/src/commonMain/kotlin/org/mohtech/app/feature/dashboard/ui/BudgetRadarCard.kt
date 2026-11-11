package org.mohtech.app.feature.dashboard.ui

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mohtech.app.core.theme.RaqeemColors

/**
 * BudgetRadarCard:
 * Displays today's spending radar and weekly totals with dynamic currency formatting.
 */
@Composable
fun BudgetRadarCard(
    todayTotal: Double,
    weeklyTotal: Double,
    currency: String,
    modifier: Modifier = Modifier
) {
    val progressRatio = if (weeklyTotal > 0.0) {
        (todayTotal / weeklyTotal).toFloat().coerceIn(0.05f, 1.0f)
    } else {
        0.0f
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progressRatio,
        animationSpec = tween(durationMillis = 800),
        label = "radarProgress"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(RaqeemColors.SurfaceCard)
            .border(1.dp, RaqeemColors.SurfaceBorder, RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Column {
            // Header: Radar Title & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(RaqeemColors.EmeraldGreen)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "الرادار المالي لرقيم",
                        color = RaqeemColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(RaqeemColors.SurfaceElevated)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "100% محلي",
                        color = RaqeemColors.EmeraldGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
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
                        text = "مصروف اليوم",
                        color = RaqeemColors.TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (todayTotal % 1.0 == 0.0) "${todayTotal.toInt()}" else "$todayTotal",
                            color = RaqeemColors.TextPrimary,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = currency,
                            color = RaqeemColors.EmeraldGreen,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }

                // Weekly Total
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "المجموع الأسبوعي",
                        color = RaqeemColors.TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (weeklyTotal % 1.0 == 0.0) "${weeklyTotal.toInt()}" else "$weeklyTotal",
                            color = RaqeemColors.ElectricCyan,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = currency,
                            color = RaqeemColors.TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Animated Visual Ratio Bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = RaqeemColors.EmeraldGreen,
                trackColor = RaqeemColors.SurfaceElevated,
                strokeCap = StrokeCap.Round
            )
        }
    }
}

