package org.mohtech.app.feature.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.mohtech.app.core.theme.RaqeemColors
import org.mohtech.app.core.ui.RaqeemIcons

private data class OnboardingPageData(
    val title: String,
    val subtitle: String,
    val tagColor: Color,
    val content: @Composable () -> Unit
)

/**
 * Raqeem (رقيم) Interactive Onboarding Screen
 *
 * Designed according to UnSlop & Arab-Fintech guidelines:
 * - 3 interactive presentation cards (Voice recording, Bill splitting, Offline privacy).
 * - Full Arabic RTL compliance with Cairo typography.
 * - Smooth animated HorizontalPager with expanding dot indicators.
 * - Responsive Next / Previous / Skip navigation with tactile ripples.
 * - SafeArea protection: statusBarsPadding, navigationBarsPadding, generous 24.dp side margins.
 */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    val pages = listOf(
        OnboardingPageData(
            title = "سجل مصروفك بصوتك في 3 ثوانٍ",
            subtitle = "تحدث بطبيعتك وبلهجتك العامية دون أي تعقيد. يفهم رقيم قصدك ويستخرج المبلغ والتصنيف فوراً.",
            tagColor = RaqeemColors.EmeraldGreen,
            content = { VoiceRecordingInteractiveCard() }
        ),
        OnboardingPageData(
            title = "تقسيم النفقات والمشاركات",
            subtitle = "حسابات الطلعات والمشاريع أصبحت أسهل. حدد الدافع والمستفيدين بالصوت ليتم التوزيع بالتساوي.",
            tagColor = RaqeemColors.ElectricCyan,
            content = { ExpenseSplitInteractiveCard() }
        ),
        OnboardingPageData(
            title = "خصوصية مطلقة دون إنترنت",
            subtitle = "كل عمليات المعالجة والذكاء تعمل 100% داخل هاتفك. لا توجد خوادم خارجية، وسريتك مضمونة.",
            tagColor = RaqeemColors.EmeraldGreen,
            content = { OfflinePrivacyInteractiveCard() }
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RaqeemColors.BackgroundSlate)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Bar: Brand Seal & Skip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Brand Logo Seal
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(RaqeemColors.SurfaceElevated)
                    .border(1.dp, RaqeemColors.EmeraldGreen.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ر",
                    color = RaqeemColors.EmeraldGreen,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Skip button (visible on pages 0 and 1)
            AnimatedVisibility(
                visible = pagerState.currentPage < pages.size - 1,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                TextButton(
                    onClick = onComplete,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                ) {
                    Text(
                        text = "تخطي",
                        color = RaqeemColors.TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Horizontal Pager Content
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { pageIndex ->
            val page = pages[pageIndex]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Interactive Feature Presentation Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(RaqeemColors.SurfaceCard)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    page.content()
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Title
                Text(
                    text = page.title,
                    color = RaqeemColors.TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 32.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Subtitle
                Text(
                    text = page.subtitle,
                    color = RaqeemColors.TextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }

        // Bottom Controls: Page Indicator + Action Buttons
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Expanding Indicator Dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pages.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    val indicatorWidth by animateDpAsState(
                        targetValue = if (isSelected) 28.dp else 8.dp,
                        animationSpec = tween(durationMillis = 300),
                        label = "indicatorWidth"
                    )
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(indicatorWidth)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isSelected) RaqeemColors.EmeraldGreen else Color.White.copy(alpha = 0.18f)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Navigation Buttons Row (Previous / Next / Finish)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous Button (shown when page > 0)
                if (pagerState.currentPage > 0) {
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        },
                        modifier = Modifier
                            .weight(0.35f)
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = RaqeemColors.SurfaceElevated,
                            contentColor = RaqeemColors.TextSecondary
                        )
                    ) {
                        Text(
                            text = "السابق",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Primary Next / Complete Button
                Button(
                    onClick = {
                        if (pagerState.currentPage < pages.size - 1) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        } else {
                            onComplete()
                        }
                    },
                    modifier = Modifier
                        .weight(if (pagerState.currentPage > 0) 0.65f else 1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RaqeemColors.EmeraldGreen,
                        contentColor = RaqeemColors.BackgroundSlate
                    )
                ) {
                    Text(
                        text = if (pagerState.currentPage < pages.size - 1) "متابعة" else "بدء الاستخدام",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Interactive Presentation Cards for Onboarding
// -------------------------------------------------------------

@Composable
private fun VoiceRecordingInteractiveCard() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Voice Phrase Pill
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(RaqeemColors.SurfaceElevated)
                .border(1.dp, RaqeemColors.EmeraldGreen.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RaqeemIcons.MicWave(size = 20.dp, tint = RaqeemColors.EmeraldGreen)
            Text(
                text = "«بنزين بـ 50 شيكل»",
                color = RaqeemColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Extracted Result Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(RaqeemColors.BackgroundSlate)
                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(RaqeemColors.CatTransport)
                        )
                        Text(
                            text = "مواصلات وطاقة",
                            color = RaqeemColors.CatTransport,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = "محطة الوقود • استخراج تلقائي",
                        color = RaqeemColors.TextTertiary,
                        fontSize = 11.sp
                    )
                }

                Text(
                    text = "50.0 ₪",
                    color = RaqeemColors.EmeraldGreen,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ExpenseSplitInteractiveCard() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Speech Bubble
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(RaqeemColors.SurfaceElevated)
                .border(1.dp, RaqeemColors.ElectricCyan.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RaqeemIcons.Users(size = 20.dp, tint = RaqeemColors.ElectricCyan)
            Text(
                text = "«عشاء بستين شيكل بيني وبين أحمد»",
                color = RaqeemColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Split Calculation Box
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(RaqeemColors.BackgroundSlate)
                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Participant 1: You
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "أنت",
                    color = RaqeemColors.TextSecondary,
                    fontSize = 12.sp
                )
                Text(
                    text = "30.0 ₪",
                    color = RaqeemColors.ElectricCyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "50%",
                    color = RaqeemColors.TextTertiary,
                    fontSize = 10.sp
                )
            }

            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(36.dp)
                    .background(Color.White.copy(alpha = 0.1f))
            )

            // Participant 2: Ahmed
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "أحمد",
                    color = RaqeemColors.TextSecondary,
                    fontSize = 12.sp
                )
                Text(
                    text = "30.0 ₪",
                    color = RaqeemColors.ElectricCyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "50%",
                    color = RaqeemColors.TextTertiary,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun OfflinePrivacyInteractiveCard() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Shield Badge
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(RaqeemColors.SurfaceElevated)
                .border(1.5.dp, RaqeemColors.EmeraldGreen.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            RaqeemIcons.Shield(size = 28.dp, tint = RaqeemColors.EmeraldGreen)
        }

        // 3 Key Guarantees
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(RaqeemColors.BackgroundSlate)
                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PrivacyCheckRow(text = "صفر اتصال بسيرفرات خارجية أو تتبع")
            PrivacyCheckRow(text = "معالجة الصوت والذكاء داخل معالج هاتفك")
            PrivacyCheckRow(text = "قاعدة بيانات مشفرة ومحفوظة على جهازك فقط")
        }
    }
}

@Composable
private fun PrivacyCheckRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(RaqeemColors.EmeraldGreen)
        )
        Text(
            text = text,
            color = RaqeemColors.TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
