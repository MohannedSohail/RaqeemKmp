package org.mohtech.app.feature.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mohtech.app.core.theme.RaqeemColors

/**
 * Raqeem (رقيم) Interactive Splash Screen
 *
 * UnSlop & Quiet Luxury Fintech design:
 * - Single Emerald Green accent (no competing cyan clashing).
 * - Single Animatable progress driver (0..1 over 1600ms).
 * - Full-word Arabic typography reveal (preserves cursive ligatures).
 * - Landscape-adaptive layout with BoxWithConstraints.
 * - Tap-to-continue or 1.6s auto-completion.
 * - Safe area compliance (statusBarsPadding, navigationBarsPadding).
 */
@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit,
    modifier: Modifier = Modifier,
    displayDurationMillis: Long = 1600L
) {
    var hasFinished by remember { mutableStateOf(false) }

    fun triggerFinish() {
        if (!hasFinished) {
            hasFinished = true
            onSplashFinished()
        }
    }

    // Unified animation driver
    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = displayDurationMillis.toInt(),
                easing = FastOutSlowInEasing
            )
        )
        triggerFinish()
    }

    val progress = animProgress.value

    // Derived animation phases
    val sealScale = (0.82f + 0.18f * (progress / 0.45f).coerceIn(0f, 1f))
    val sealAlpha = (progress / 0.35f).coerceIn(0f, 1f)
    val textAlpha = ((progress - 0.25f) / 0.4f).coerceIn(0f, 1f)
    val badgeAlpha = ((progress - 0.5f) / 0.4f).coerceIn(0f, 1f)

    // Pulse wave micro-interaction
    val infiniteTransition = rememberInfiniteTransition(label = "splashPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(RaqeemColors.BackgroundSlate)
            .statusBarsPadding()
            .navigationBarsPadding()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { triggerFinish() }
            )
    ) {
        val isLandscape = maxWidth > maxHeight

        if (isLandscape) {
            // Adaptive Landscape layout: Side-by-side Brand Seal and Typography
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Brand Emblem
                BrandSeal(
                    sealScale = sealScale,
                    sealAlpha = sealAlpha,
                    pulseScale = pulseScale,
                    pulseAlpha = pulseAlpha
                )

                Spacer(modifier = Modifier.width(36.dp))

                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "رقيم",
                        color = RaqeemColors.TextPrimary,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 42.sp,
                        modifier = Modifier.alpha(textAlpha)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    AcousticWaveformBars(
                        modifier = Modifier
                            .alpha(textAlpha)
                            .padding(vertical = 4.dp),
                        settledProgress = progress
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "ذكاء المصاريف بالصوت",
                        color = RaqeemColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.alpha(badgeAlpha)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OfflineStatusChip(modifier = Modifier.alpha(badgeAlpha))
                }
            }
        } else {
            // Portrait Layout: Vertical Harmony
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.weight(1f))

                BrandSeal(
                    sealScale = sealScale,
                    sealAlpha = sealAlpha,
                    pulseScale = pulseScale,
                    pulseAlpha = pulseAlpha
                )

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = "رقيم",
                    color = RaqeemColors.TextPrimary,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 44.sp,
                    modifier = Modifier.alpha(textAlpha)
                )

                Spacer(modifier = Modifier.height(10.dp))

                AcousticWaveformBars(
                    modifier = Modifier
                        .alpha(textAlpha)
                        .padding(vertical = 4.dp),
                    settledProgress = progress
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "ذكاء المصاريف بالصوت",
                    color = RaqeemColors.TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.alpha(badgeAlpha)
                )

                Spacer(modifier = Modifier.weight(1f))

                OfflineStatusChip(
                    modifier = Modifier
                        .alpha(badgeAlpha)
                        .padding(bottom = 28.dp)
                )
            }
        }
    }
}

@Composable
private fun BrandSeal(
    sealScale: Float,
    sealAlpha: Float,
    pulseScale: Float,
    pulseAlpha: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .scale(sealScale)
            .alpha(sealAlpha),
        contentAlignment = Alignment.Center
    ) {
        // Subtle emerald pulse ring
        Box(
            modifier = Modifier
                .size(88.dp)
                .scale(pulseScale)
                .clip(RoundedCornerShape(30.dp))
                .border(
                    1.dp,
                    RaqeemColors.EmeraldGreen.copy(alpha = pulseAlpha * sealAlpha),
                    RoundedCornerShape(30.dp)
                )
        )

        // Precision Seal Squircle
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(RaqeemColors.SurfaceElevated)
                .border(
                    1.5.dp,
                    RaqeemColors.EmeraldGreen.copy(alpha = 0.55f),
                    RoundedCornerShape(26.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "ر",
                color = RaqeemColors.EmeraldGreen,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun OfflineStatusChip(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(RaqeemColors.SurfaceElevated)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(RaqeemColors.EmeraldGreen)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "دون إنترنت • سرية تامة",
                color = RaqeemColors.TextTertiary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 5-bar acoustic visualizer with settled progress damping
 */
@Composable
private fun AcousticWaveformBars(
    settledProgress: Float,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "waveformBars")

    val h1 by transition.animateFloat(
        initialValue = 4f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h1"
    )
    val h2 by transition.animateFloat(
        initialValue = 8f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h2"
    )
    val h3 by transition.animateFloat(
        initialValue = 12f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, delayMillis = 200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h3"
    )
    val h4 by transition.animateFloat(
        initialValue = 6f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, delayMillis = 150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h4"
    )
    val h5 by transition.animateFloat(
        initialValue = 4f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(480, delayMillis = 50, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h5"
    )

    val settleMultiplier = if (settledProgress > 0.8f) {
        (1f - (settledProgress - 0.8f) / 0.2f).coerceIn(0.4f, 1f)
    } else {
        1f
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(h1, h2, h3, h4, h5).forEachIndexed { index, heightVal ->
            val effectiveHeight = (heightVal * settleMultiplier).coerceAtLeast(3f)
            val barAlpha = if (index == 2) 0.95f else 0.7f
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(effectiveHeight.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(RaqeemColors.EmeraldGreen.copy(alpha = barAlpha))
            )
        }
    }
}
