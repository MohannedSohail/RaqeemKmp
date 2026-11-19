package org.mohtech.app.feature.dashboard.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mohtech.app.core.haptics.HapticFeedbackType
import org.mohtech.app.core.haptics.LocalRaqeemHaptics
import org.mohtech.app.core.theme.RaqeemColors
import org.mohtech.app.core.ui.RaqeemIcons
import org.mohtech.app.data.model.ExpenseBatch
import org.mohtech.app.feature.dashboard.ProcessingStage

/**
 * VoiceProcessingOverlay:
 * Elite FinTech loading card shown between voice recording finish and ExpenseReviewSheet appearance.
 * Adheres strictly to Unslop rules:
 * - One Bright Element (Fintech Emerald Green).
 * - Hairline borders (1dp @ 8% opacity).
 * - 4px spacing grid and nested radiuses.
 * - Zero artificial delay in ViewModel; smooth Compose animation pacing.
 */
@Composable
fun VoiceProcessingOverlay(
    stage: ProcessingStage,
    lastAmplitude: Float,
    pendingBatch: ExpenseBatch?,
    onCancel: () -> Unit,
    onReadyFinished: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isVisible = stage != ProcessingStage.IDLE
    val haptics = LocalRaqeemHaptics.current

    LaunchedEffect(stage) {
        if (stage != ProcessingStage.IDLE) {
            haptics.trigger(HapticFeedbackType.ProcessingStep)
        }
        if (stage == ProcessingStage.READY) {
            kotlinx.coroutines.delay(420)
            onReadyFinished()
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(250)) + slideInVertically(tween(350, easing = FastOutSlowInEasing)) { it / 3 },
        exit = fadeOut(tween(250)) + slideOutVertically(tween(300)) { it / 3 },
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* Scrim eats outside taps */ },
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(RaqeemColors.SurfaceCard)
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp)
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = when (stage) {
                            ProcessingStage.FINALIZING_AUDIO -> "جارٍ تجهيز التسجيل"
                            ProcessingStage.TRANSCRIBING -> "جارٍ تحويل الصوت إلى نص"
                            ProcessingStage.UNDERSTANDING -> "جارٍ استخراج المبلغ والتصنيف"
                            ProcessingStage.READY -> "تمت معالجة المصروف بنجاح"
                            ProcessingStage.IDLE -> ""
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Drag Handle Hairline Pill
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Central Focal Graphic: Rotating Emerald Arc with Waveform / Checkmark
                ProcessingCentralGraphic(
                    stage = stage,
                    amplitude = lastAmplitude
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Animated Stage Title
                AnimatedContent(
                    targetState = stage,
                    transitionSpec = {
                        (slideInVertically(tween(280)) { 8 } + fadeIn(tween(280)))
                            .togetherWith(slideOutVertically(tween(200)) { -8 } + fadeOut(tween(200)))
                    },
                    label = "ProcessingStageTextTransition"
                ) { targetStage ->
                    val titleText = when (targetStage) {
                        ProcessingStage.FINALIZING_AUDIO -> "جارٍ تجهيز التسجيل"
                        ProcessingStage.TRANSCRIBING -> "جارٍ تحويل الصوت إلى نص"
                        ProcessingStage.UNDERSTANDING -> "جارٍ استخراج المبلغ والتصنيف"
                        ProcessingStage.READY -> "تم الفهم بنجاح"
                        ProcessingStage.IDLE -> ""
                    }
                    Text(
                        text = titleText,
                        color = RaqeemColors.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Calm Subtitle
                Text(
                    text = "تتم المعالجة على جهازك دون إنترنت",
                    color = RaqeemColors.TextTertiary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

                // 3-Step Progress Indicator
                ProcessingStepIndicator(stage = stage)

                Spacer(modifier = Modifier.height(18.dp))

                // Result Preview Skeleton / Content
                ProcessingResultPreview(
                    stage = stage,
                    pendingBatch = pendingBatch
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Cancel Button (Touch Target >= 48dp)
                if (stage != ProcessingStage.READY) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(color = RaqeemColors.CoralRose)
                            ) { onCancel() }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "إلغاء المعالجة",
                            color = RaqeemColors.TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun ProcessingCentralGraphic(
    stage: ProcessingStage,
    amplitude: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ProcessingRingTransition")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ArcRotation"
    )

    val breathingBreath by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "WaveformBreathing"
    )

    Box(
        modifier = modifier.size(96.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 2.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
            val arcSize = Size(diameter, diameter)

            // Background quiet hairline ring
            drawArc(
                color = Color.White.copy(alpha = 0.08f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth)
            )

            // Dynamic Emerald Progress Arc
            if (stage != ProcessingStage.READY) {
                drawArc(
                    color = RaqeemColors.EmeraldGreen,
                    startAngle = rotation,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            } else {
                drawArc(
                    color = RaqeemColors.EmeraldGreen,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth)
                )
            }
        }

        // Inner graphic: waveform bars or checkmark
        if (stage == ProcessingStage.READY) {
            RaqeemIcons.Check(
                size = 36.dp,
                tint = RaqeemColors.EmeraldGreen,
                strokeWidth = 2.5.dp
            )
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val heights = listOf(0.4f, 0.7f, 1.0f, 0.7f, 0.4f)
                val ampFactor = if (stage == ProcessingStage.FINALIZING_AUDIO) {
                    amplitude.coerceIn(0.2f, 1.0f)
                } else {
                    breathingBreath
                }

                heights.forEach { baseHeightRatio ->
                    val barHeight = (28.dp * (baseHeightRatio * ampFactor).coerceIn(0.25f, 1.0f))
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(barHeight)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(RaqeemColors.EmeraldGreen)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProcessingStepIndicator(
    stage: ProcessingStage,
    modifier: Modifier = Modifier
) {
    val step = when (stage) {
        ProcessingStage.FINALIZING_AUDIO -> 1
        ProcessingStage.TRANSCRIBING -> 2
        ProcessingStage.UNDERSTANDING, ProcessingStage.READY -> 3
        ProcessingStage.IDLE -> 0
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 1..3) {
            val isCurrentOrCompleted = i <= step
            val dotColor = if (isCurrentOrCompleted) RaqeemColors.EmeraldGreen else Color.White.copy(alpha = 0.15f)

            Box(
                modifier = Modifier
                    .size(if (i == step) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )

            if (i < 3) {
                val lineProgress = if (step > i) 1f else if (step == i) 0.5f else 0f
                val lineColor = if (step > i) RaqeemColors.EmeraldGreen else Color.White.copy(alpha = 0.10f)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(lineColor)
                )
            }
        }
    }
}

@Composable
private fun ProcessingResultPreview(
    stage: ProcessingStage,
    pendingBatch: ExpenseBatch?,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ShimmerTransition")
    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 300f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShimmerTranslate"
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            RaqeemColors.SurfaceElevated,
            RaqeemColors.SurfaceElevated.copy(alpha = 0.4f),
            RaqeemColors.SurfaceElevated
        ),
        start = Offset(shimmerTranslate, 0f),
        end = Offset(shimmerTranslate + 200f, 0f)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(RaqeemColors.SurfaceElevated)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        if (stage == ProcessingStage.READY && pendingBatch != null && pendingBatch.items.isNotEmpty()) {
            val item = pendingBatch.items.first()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title,
                    color = RaqeemColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${if (item.amount % 1.0 == 0.0) item.amount.toInt() else item.amount} ${item.currency}",
                    color = RaqeemColors.EmeraldGreen,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.35f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush)
                )
            }
        }
    }
}

