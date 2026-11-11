package org.mohtech.app.feature.dashboard.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.mohtech.app.core.theme.RaqeemColors

/**
 * VoiceRecordButton:
 * Tactile, pulsating voice recording button with dynamic amplitude feedback.
 * Touch target: 76dp (well exceeding the 48dp minimum standard).
 */
@Composable
fun VoiceRecordButton(
    isRecording: Boolean,
    amplitude: Float,
    onRecordToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isRecording) 1.25f + (amplitude * 0.25f) else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isRecording) 600 else 1800,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = if (isRecording) 0.6f else 0.2f,
        targetValue = if (isRecording) 0.1f else 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isRecording) 600 else 1800,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = modifier.size(100.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer Pulsing Glow Ring
        Box(
            modifier = Modifier
                .size(92.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(
                    if (isRecording) {
                        RaqeemColors.CoralRose.copy(alpha = glowAlpha)
                    } else {
                        RaqeemColors.EmeraldGreen.copy(alpha = glowAlpha)
                    }
                )
        )

        // Middle Gradient Ring
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(
                    if (isRecording) {
                        RaqeemColors.CoralRose.copy(alpha = 0.3f)
                    } else {
                        RaqeemColors.ElectricCyan.copy(alpha = 0.2f)
                    }
                )
        )

        // Core Interactive Mic Button
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(
                    if (isRecording) {
                        Brush.linearGradient(
                            listOf(RaqeemColors.CoralRose, Color(0xFFE53935))
                        )
                    } else {
                        Brush.linearGradient(
                            listOf(RaqeemColors.EmeraldGreen, RaqeemColors.ElectricCyan)
                        )
                    }
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = RaqeemColors.TextPrimary),
                    onClick = onRecordToggle
                ),
            contentAlignment = Alignment.Center
        ) {
            // Microphone Visual Shape (Vector Canvas or Icon)
            MicIcon(
                tint = RaqeemColors.BackgroundSlate,
                isRecording = isRecording
            )
        }
    }
}

@Composable
private fun MicIcon(tint: Color, isRecording: Boolean) {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(30.dp)) {
        val w = size.width
        val h = size.height

        // Top capsule (microphone head)
        drawRoundRect(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.35f, h * 0.15f),
            size = androidx.compose.ui.geometry.Size(w * 0.3f, h * 0.45f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.15f, w * 0.15f)
        )

        // Bottom arc (stand)
        drawArc(
            color = tint,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.22f, h * 0.25f),
            size = androidx.compose.ui.geometry.Size(w * 0.56f, h * 0.42f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.5.dp.toPx())
        )

        // Vertical stem
        drawLine(
            color = tint,
            start = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.67f),
            end = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.85f),
            strokeWidth = 3.5.dp.toPx()
        )

        // Base horizontal bar
        drawLine(
            color = tint,
            start = androidx.compose.ui.geometry.Offset(w * 0.32f, h * 0.85f),
            end = androidx.compose.ui.geometry.Offset(w * 0.68f, h * 0.85f),
            strokeWidth = 3.5.dp.toPx()
        )
    }
}

