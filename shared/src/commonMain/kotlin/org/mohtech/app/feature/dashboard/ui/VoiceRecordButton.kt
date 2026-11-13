package org.mohtech.app.feature.dashboard.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import org.mohtech.app.core.theme.RaqeemColors

/**
 * VoiceRecordButton:
 * High-precision, tactile voice input instrument.
 * Unslop Principles:
 * - Quiet chrome, purposeful state transition.
 * - Single-hue acoustic ripple without multi-color neon haze.
 * - Generous touch target (88dp container, 64dp core).
 */
@Composable
fun VoiceRecordButton(
    isRecording: Boolean,
    amplitude: Float,
    onRecordToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voicePulse")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isRecording) 1.28f + (amplitude * 0.22f) else 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isRecording) 550 else 2200,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val rippleAlpha by infiniteTransition.animateFloat(
        initialValue = if (isRecording) 0.35f else 0.12f,
        targetValue = if (isRecording) 0.05f else 0.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isRecording) 550 else 2200,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rippleAlpha"
    )

    Box(
        modifier = modifier.size(92.dp),
        contentAlignment = Alignment.Center
    ) {
        // Acoustic Wave Ring (Single-hue, purposeful, quiet)
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(
                    if (isRecording) {
                        RaqeemColors.CoralRose.copy(alpha = rippleAlpha)
                    } else {
                        RaqeemColors.EmeraldGreen.copy(alpha = rippleAlpha)
                    }
                )
        )

        // Outer Hairline Ring (Expands dynamically on sound amplitude)
        Canvas(modifier = Modifier.size(72.dp * pulseScale)) {
            val sw = 1.5.dp.toPx()
            val ringColor = if (isRecording) {
                RaqeemColors.CoralRose.copy(alpha = rippleAlpha * 2f)
            } else {
                Color.White.copy(alpha = 0.08f)
            }
            drawCircle(
                color = ringColor,
                radius = size.minDimension / 2f - sw,
                style = Stroke(width = sw)
            )
        }

        // Core Tactile Mic Trigger
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    if (isRecording) RaqeemColors.CoralRose else RaqeemColors.EmeraldGreen
                )
                .border(
                    1.dp,
                    if (isRecording) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.15f),
                    CircleShape
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = RaqeemColors.BackgroundSlate),
                    onClick = onRecordToggle
                ),
            contentAlignment = Alignment.Center
        ) {
            MicGlyph(
                tint = RaqeemColors.BackgroundSlate,
                isRecording = isRecording
            )
        }
    }
}

@Composable
private fun MicGlyph(
    tint: Color,
    isRecording: Boolean
) {
    Canvas(modifier = Modifier.size(28.dp)) {
        val sw = 2.dp.toPx()
        val w = size.width
        val h = size.height

        // Top capsule (microphone head)
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.35f, h * 0.16f),
            size = Size(w * 0.30f, h * 0.44f),
            cornerRadius = CornerRadius(w * 0.15f, w * 0.15f)
        )

        // Bottom cradle arc
        drawArc(
            color = tint,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.24f, h * 0.32f),
            size = Size(w * 0.52f, h * 0.36f),
            style = Stroke(width = sw, cap = StrokeCap.Round)
        )

        // Vertical stem
        drawLine(
            color = tint,
            start = Offset(w * 0.5f, h * 0.68f),
            end = Offset(w * 0.5f, h * 0.84f),
            strokeWidth = sw,
            cap = StrokeCap.Round
        )

        // Base foot
        drawLine(
            color = tint,
            start = Offset(w * 0.34f, h * 0.84f),
            end = Offset(w * 0.66f, h * 0.84f),
            strokeWidth = sw,
            cap = StrokeCap.Round
        )
    }
}
