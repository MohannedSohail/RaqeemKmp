package org.mohtech.app.feature.dashboard.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import org.mohtech.app.core.haptics.HapticKind
import org.mohtech.app.core.haptics.LocalRaqeemHaptics
import org.mohtech.app.core.theme.RaqeemColors

/**
 * VoiceRecordButton:
 * High-precision tactile voice input instrument.
 * Unslop & Performance Principles (Axis D2):
 * - Single-hue fintech brand color strictly: EmeraldGreen #00C48C (no multi-color neon haze).
 * - Spring expansion on recording state transition.
 * - Amplitude is read strictly in Draw Phase (drawBehind) to guarantee 60fps without recomposing button every frame.
 * - Start/stop haptic feedback via RaqeemHaptics.
 */
@Composable
fun VoiceRecordButton(
    isRecording: Boolean,
    amplitude: Float,
    onRecordToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalRaqeemHaptics.current
    val currentAmpState = rememberUpdatedState(amplitude)

    // Spring expansion for tactile state transition
    val springScale by animateFloatAsState(
        targetValue = if (isRecording) 1.12f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "micSpringScale"
    )

    Box(
        modifier = modifier
            .size(92.dp)
            .drawBehind {
                // DRAW PHASE: read amplitude here to eliminate per-frame recomposition
                val amp = currentAmpState.value.coerceIn(0f, 1f)
                val centerOffset = Offset(size.width / 2f, size.height / 2f)
                val baseRadius = (32.dp.toPx()) * springScale

                if (isRecording) {
                    // Outer quiet wave ring 1
                    val ring1Radius = baseRadius + (14.dp.toPx() * amp)
                    drawCircle(
                        color = RaqeemColors.EmeraldGreen.copy(alpha = 0.12f + (amp * 0.18f)),
                        radius = ring1Radius,
                        center = centerOffset
                    )

                    // Outer hairline acoustic ring 2
                    val ring2Radius = baseRadius + (8.dp.toPx() * amp)
                    drawCircle(
                        color = RaqeemColors.EmeraldGreen.copy(alpha = 0.25f + (amp * 0.35f)),
                        radius = ring2Radius,
                        center = centerOffset,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Core Tactile Mic Trigger: Single brand hue (EmeraldGreen)
        Box(
            modifier = Modifier
                .size(64.dp * springScale)
                .clip(CircleShape)
                .background(RaqeemColors.EmeraldGreen)
                .border(
                    1.dp,
                    Color.White.copy(alpha = 0.20f),
                    CircleShape
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = RaqeemColors.BackgroundSlate),
                    onClick = {
                        haptics.perform(HapticKind.CONFIRM)
                        onRecordToggle()
                    }
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
