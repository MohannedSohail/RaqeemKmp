package org.mohtech.app.core.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mohtech.app.core.theme.RaqeemColors

/**
 * RollingNumberTicker:
 * Non-overshooting vertical rolling number ticker for fintech metrics.
 * 
 * Rules (Axis D1):
 * - Duration: ~300ms.
 * - Easing: FastOutSlowInEasing strictly (no EaseOutBack so numbers never bounce/overshoot past their true value).
 * - Directional: Slides up on increase, slides down on decrease.
 */
@Composable
fun RollingNumberTicker(
    value: Double,
    currency: String? = null,
    color: Color = RaqeemColors.TextPrimary,
    fontSize: Int = 32,
    fontWeight: FontWeight = FontWeight.SemiBold,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom
    ) {
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                val isIncrease = targetState >= initialState
                if (isIncrease) {
                    (slideInVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)) { height -> height } +
                        fadeIn(animationSpec = tween(200)))
                        .togetherWith(
                            slideOutVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)) { height -> -height } +
                                fadeOut(animationSpec = tween(150))
                        )
                } else {
                    (slideInVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)) { height -> -height } +
                        fadeIn(animationSpec = tween(200)))
                        .togetherWith(
                            slideOutVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)) { height -> height } +
                                fadeOut(animationSpec = tween(150))
                        )
                }
            },
            label = "rollingNumberTicker"
        ) { targetVal ->
            val formatted = if (targetVal % 1.0 == 0.0) {
                targetVal.toInt().toString()
            } else {
                targetVal.toString()
            }

            Text(
                text = formatted,
                color = color,
                fontSize = fontSize.sp,
                fontWeight = fontWeight
            )
        }

        if (!currency.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = currency,
                color = RaqeemColors.EmeraldGreen,
                fontSize = (fontSize / 2).coerceAtLeast(13).sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

