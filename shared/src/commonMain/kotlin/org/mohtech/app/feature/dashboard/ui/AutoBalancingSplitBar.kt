package org.mohtech.app.feature.dashboard.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import org.mohtech.app.core.theme.RaqeemColors
import org.mohtech.app.data.accounting.AutoSplitSummary

private val partyAccentPalette = listOf(
    RaqeemColors.ElectricCyan,
    RaqeemColors.AmberWarning,
    RaqeemColors.CatHealth,
    RaqeemColors.CoralRose
)

/**
 * Proportional, color-coded auto-balancing split bar backed by [AutoSplitSummary].
 * Zero per-frame allocations inside Canvas draw phase; respects RTL layout direction.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutoBalancingSplitBar(
    summary: AutoSplitSummary,
    partyNames: List<String>,
    currency: String,
    modifier: Modifier = Modifier
) {
    val partyEntries = remember(partyNames, summary.partyShares) {
        summary.partyShares.mapIndexed { idx, share ->
            val name = partyNames.getOrNull(idx).orEmpty()
            name to share
        }
    }
    val (_, segments) = remember(summary, partyEntries, currency) {
        buildSplitBarSegments(
            totalAmount = summary.totalAmount,
            currency = currency,
            partyEntries = partyEntries
        )
    }

    val animatedPersonalRatio by animateFloatAsState(
        targetValue = summary.personalRatio,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "personalSplitRatio"
    )
    val animatedPartyRatios = summary.partyRatios.mapIndexed { idx, ratio ->
        val animated by animateFloatAsState(
            targetValue = ratio,
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            label = "partySplitRatio_$idx"
        )
        animated
    }

    val layoutDirection = LocalLayoutDirection.current
    val trackEmptyColor = Color.White.copy(alpha = 0.06f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(RaqeemColors.SurfaceCard)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "توزيع الحصص التلقائي",
                color = RaqeemColors.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "حصتي: ${formatDraftAmount(summary.personalShare, currency)} $currency",
                color = RaqeemColors.EmeraldGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Proportional Bar (zero per-frame allocations inside Canvas)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
        ) {
            val totalWidth = size.width
            val barHeight = size.height
            drawRect(
                color = trackEmptyColor,
                topLeft = Offset.Zero,
                size = Size(totalWidth, barHeight)
            )
            if (summary.totalAmount <= 0.0 || totalWidth <= 0f) return@Canvas

            val isRtl = layoutDirection == LayoutDirection.Rtl
            var cursorX = if (isRtl) totalWidth else 0f

            // Personal segment
            if (animatedPersonalRatio > 0f) {
                val segWidth = (totalWidth * animatedPersonalRatio).coerceIn(0f, totalWidth)
                val left = if (isRtl) cursorX - segWidth else cursorX
                drawRect(
                    color = RaqeemColors.EmeraldGreen,
                    topLeft = Offset(left, 0f),
                    size = Size(segWidth, barHeight)
                )
                cursorX = if (isRtl) left else cursorX + segWidth
            }

            // External party segments
            for (idx in animatedPartyRatios.indices) {
                val ratio = animatedPartyRatios[idx]
                if (ratio <= 0f) continue
                val maxAvailable = if (isRtl) cursorX else (totalWidth - cursorX)
                val segWidth = (totalWidth * ratio).coerceIn(0f, maxAvailable)
                if (segWidth <= 0f) continue
                val left = if (isRtl) cursorX - segWidth else cursorX
                val color = partyAccentPalette[idx % partyAccentPalette.size]
                drawRect(
                    color = color,
                    topLeft = Offset(left, 0f),
                    size = Size(segWidth, barHeight)
                )
                cursorX = if (isRtl) left else cursorX + segWidth
            }
        }

        // Compact RTL Legend
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            segments.forEach { seg ->
                val dotColor = if (seg.isPersonal) {
                    RaqeemColors.EmeraldGreen
                } else {
                    partyAccentPalette[seg.colorIndex % partyAccentPalette.size]
                }
                val pct = (seg.ratio * 100f).roundToInt().coerceIn(0, 100)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Text(
                        text = "${seg.label}: ${formatDraftAmount(seg.amount, currency)} $currency ($pct%)",
                        color = RaqeemColors.TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}

