package org.mohtech.app.feature.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import kotlinx.coroutines.delay
import org.mohtech.app.core.theme.RaqeemColors

/**
 * Raqeem (رقيم) Interactive Splash Screen
 *
 * UnSlop & Quiet Luxury Fintech design:
 * - Precision Arabic "ر" seal with hairline emerald contour.
 * - Dual concentric acoustic ripple micro-interactions + subtle equalizer bars.
 * - Tap-to-continue or 1.2s timeout.
 * - Safe area compliance (statusBarsPadding, navigationBarsPadding).
 */
@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit,
    modifier: Modifier = Modifier,
    displayDurationMillis: Long = 1200L
) {
    var startAnimation by remember { mutableStateOf(false) }
    var hasFinished by remember { mutableStateOf(false) }

    fun triggerFinish() {
        if (!hasFinished) {
            hasFinished = true
            onSplashFinished()
        }
    }

    val sealScale by animateFloatAsState(
        targetValue = if (startAnimation) 1.0f else 0.85f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "sealScale"
    )

    val contentAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1.0f else 0.0f,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "contentAlpha"
    )

    // Acoustic wave infinite transitions
    val infiniteTransition = rememberInfiniteTransition(label = "acousticWave")

    // Ripple 1
    val waveScale1 by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.36f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveScale1"
    )
    val waveAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.40f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveAlpha1"
    )

    // Ripple 2 (out of phase)
    val waveScale2 by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.62f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, delayMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveScale2"
    )
    val waveAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, delayMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveAlpha2"
    )

    // Offline dot pulse
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(displayDurationMillis)
        triggerFinish()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(RaqeemColors.BackgroundSlate)
            .statusBarsPadding()
            .navigationBarsPadding()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { triggerFinish() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            // Brand Seal with Acoustic Wave micro-interaction
            Box(
                modifier = Modifier
                    .scale(sealScale)
                    .alpha(contentAlpha),
                contentAlignment = Alignment.Center
            ) {
                // Secondary outer acoustic ring
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .scale(waveScale2)
                        .clip(RoundedCornerShape(30.dp))
                        .border(
                            1.dp,
                            RaqeemColors.ElectricCyan.copy(alpha = waveAlpha2),
                            RoundedCornerShape(30.dp)
                        )
                )

                // Primary inner acoustic ring
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .scale(waveScale1)
                        .clip(RoundedCornerShape(28.dp))
                        .border(
                            1.2.dp,
                            RaqeemColors.EmeraldGreen.copy(alpha = waveAlpha1),
                            RoundedCornerShape(28.dp)
                        )
                )

                // Elevated Brand Seal Squircle
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(RaqeemColors.SurfaceElevated)
                        .border(
                            1.5.dp,
                            RaqeemColors.EmeraldGreen.copy(alpha = 0.45f),
                            RoundedCornerShape(24.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ر",
                        color = RaqeemColors.EmeraldGreen,
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // App Name
            Text(
                text = "رقيم",
                color = RaqeemColors.TextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 40.sp,
                modifier = Modifier.alpha(contentAlpha)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Acoustic Wave Micro-visualizer
            AcousticWaveformBars(
                modifier = Modifier
                    .alpha(contentAlpha)
                    .padding(vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle
            Text(
                text = "ذكاء المصاريف بالصوت • محلي 100%",
                color = RaqeemColors.TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.alpha(contentAlpha)
            )
        }

        // Bottom Offline Status Badge
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
                .alpha(contentAlpha)
                .clip(RoundedCornerShape(20.dp))
                .background(RaqeemColors.SurfaceElevated)
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .alpha(dotAlpha)
                        .clip(CircleShape)
                        .background(RaqeemColors.EmeraldGreen)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "دون إنترنت • سرية تامة",
                    color = RaqeemColors.TextTertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Subtle 5-bar equalizer micro-waveform representing audio readiness
 */
@Composable
private fun AcousticWaveformBars(
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "waveform")

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

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(h1, h2, h3, h4, h5).forEachIndexed { index, heightDp ->
            val barColor = if (index % 2 == 0) RaqeemColors.EmeraldGreen else RaqeemColors.ElectricCyan
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(heightDp.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(barColor.copy(alpha = 0.7f))
            )
        }
    }
}
