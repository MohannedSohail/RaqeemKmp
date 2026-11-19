package org.mohtech.app.feature.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.mohtech.app.ai.nlp.LocalDialectExpenseParser
import org.mohtech.app.core.theme.RaqeemColors
import org.mohtech.app.core.ui.RaqeemIcons
import org.mohtech.app.data.model.ExpenseCategory

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
 * - 4 interactive presentation cards:
 *   1. Voice: Real LocalDialectExpenseParser live dialect demo with phrase chips.
 *   2. Splitting: Dynamic bill split preview (equal vs specific payer).
 *   3. Privacy & Mic: Honest offline guarantees (no false encryption claims) + mic permission button.
 *   4. Setup: Currency picker and weekly budget configuration.
 * - Full Arabic RTL compliance with Cairo typography.
 * - Landscape adaptive 2-column layout with BoxWithConstraints.
 * - State preserved across rotation via rememberSaveable.
 * - Zero emojis in UI chrome (pure vector iconography).
 */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    onRequestMicPermission: (() -> Unit)? = null,
    onPreferencesConfigured: ((currency: String, budget: Double) -> Unit)? = null,
    initialCurrency: String = "₪",
    initialBudget: Double = 1000.0
) {
    val coroutineScope = rememberCoroutineScope()

    var selectedCurrency by rememberSaveable { mutableStateOf(initialCurrency) }
    var selectedBudget by rememberSaveable { mutableDoubleStateOf(initialBudget) }
    var micRequested by rememberSaveable { mutableStateOf(false) }

    val pages = listOf(
        OnboardingPageData(
            title = "سجل مصروفك بصوتك في ثوانٍ",
            subtitle = "تحدث بطبيعتك وبلهجتك اليومية. يستخرج محرك رقيم المحلي المبلغ والتصنيف فوراً على جهازك.",
            tagColor = RaqeemColors.EmeraldGreen,
            content = { VoiceRecordingInteractiveCard(currency = selectedCurrency) }
        ),
        OnboardingPageData(
            title = "تقسيم النفقات والمشاركات",
            subtitle = "حسابات الطلعات والمشاريع أصبحت أسهل. حدد الدافع والمستفيدين بالصوت ليتم التوزيع فوراً.",
            tagColor = RaqeemColors.EmeraldGreen,
            content = { ExpenseSplitInteractiveCard(currency = selectedCurrency) }
        ),
        OnboardingPageData(
            title = "خصوصية مطلقة دون إنترنت",
            subtitle = "معالجة الصوت والبيانات بالكامل على معالج هاتفك. لا توجد خوادم خارجية ولا جمع للبيانات.",
            tagColor = RaqeemColors.EmeraldGreen,
            content = {
                OfflinePrivacyInteractiveCard(
                    micRequested = micRequested,
                    onRequestMic = {
                        micRequested = true
                        onRequestMicPermission?.invoke()
                    }
                )
            }
        ),
        OnboardingPageData(
            title = "إعداد العملة والميزانية",
            subtitle = "حدد عملتك الأساسية وميزانيتك الأسبوعية لبدء متابعة نفقاتك بدقة وذكاء.",
            tagColor = RaqeemColors.EmeraldGreen,
            content = {
                InitialSetupInteractiveCard(
                    selectedCurrency = selectedCurrency,
                    onSelectCurrency = { selectedCurrency = it },
                    selectedBudget = selectedBudget,
                    onSelectBudget = { selectedBudget = it }
                )
            }
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })

    fun handleFinish() {
        onPreferencesConfigured?.invoke(selectedCurrency, selectedBudget)
        onComplete()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(RaqeemColors.BackgroundSlate)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        val isLandscape = maxWidth > maxHeight

        if (isLandscape) {
            // Landscape 2-Column Layout
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Column 1: Interactive Card
                Box(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(RaqeemColors.SurfaceCard)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    pages[pagerState.currentPage].content()
                }

                // Column 2: Typography & Navigation Controls
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Row with Seal & Skip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BrandSealMini()
                        if (pagerState.currentPage < pages.size - 1) {
                            TextButton(onClick = { handleFinish() }) {
                                Text(
                                    text = "تخطي",
                                    color = RaqeemColors.TextSecondary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = pages[pagerState.currentPage].title,
                        color = RaqeemColors.TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 28.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = pages[pagerState.currentPage].subtitle,
                        color = RaqeemColors.TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    PagerIndicatorDots(pageCount = pages.size, currentPage = pagerState.currentPage)

                    Spacer(modifier = Modifier.height(20.dp))

                    NavigationButtonsRow(
                        currentPage = pagerState.currentPage,
                        pageCount = pages.size,
                        onPrev = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        },
                        onNext = {
                            if (pagerState.currentPage < pages.size - 1) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            } else {
                                handleFinish()
                            }
                        }
                    )
                }
            }
        } else {
            // Portrait Layout: Single Column with Pager
            Column(
                modifier = Modifier
                    .fillMaxSize()
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
                    BrandSealMini()

                    AnimatedVisibility(
                        visible = pagerState.currentPage < pages.size - 1,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        TextButton(
                            onClick = { handleFinish() },
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
                            .padding(vertical = 12.dp),
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
                                .padding(18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            page.content()
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = page.title,
                            color = RaqeemColors.TextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 32.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(10.dp))

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
                    PagerIndicatorDots(pageCount = pages.size, currentPage = pagerState.currentPage)

                    Spacer(modifier = Modifier.height(24.dp))

                    NavigationButtonsRow(
                        currentPage = pagerState.currentPage,
                        pageCount = pages.size,
                        onPrev = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        },
                        onNext = {
                            if (pagerState.currentPage < pages.size - 1) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            } else {
                                handleFinish()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BrandSealMini() {
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
}

@Composable
private fun PagerIndicatorDots(
    pageCount: Int,
    currentPage: Int
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val isSelected = currentPage == index
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
}

@Composable
private fun NavigationButtonsRow(
    currentPage: Int,
    pageCount: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (currentPage > 0) {
            OutlinedButton(
                onClick = onPrev,
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

        Button(
            onClick = onNext,
            modifier = Modifier
                .weight(if (currentPage > 0) 0.65f else 1f)
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = RaqeemColors.EmeraldGreen,
                contentColor = RaqeemColors.BackgroundSlate
            )
        ) {
            Text(
                text = if (currentPage < pageCount - 1) "متابعة" else "بدء الاستخدام",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// -------------------------------------------------------------
// Interactive Presentation Cards for Onboarding
// -------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoiceRecordingInteractiveCard(currency: String) {
    val samplePhrases = listOf(
        "بنزين بـ 50 شيكل",
        "قهوة بعشرة شيكل",
        "سوبرماركت بـ 120"
    )
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val parser = remember { LocalDialectExpenseParser() }

    val activePhrase = samplePhrases[selectedIndex]
    val parsedBatch = remember(activePhrase, currency) {
        parser.parseSpokenText(activePhrase, defaultCurrency = currency)
    }
    val item = parsedBatch.items.firstOrNull()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Sample Chips
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            samplePhrases.forEachIndexed { index, phrase ->
                val isSelected = selectedIndex == index
                val chipBorderColor by animateColorAsState(
                    targetValue = if (isSelected) RaqeemColors.EmeraldGreen else Color.White.copy(alpha = 0.12f),
                    label = "chipBorder"
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) RaqeemColors.SurfaceElevated else Color.Transparent)
                        .border(1.dp, chipBorderColor, RoundedCornerShape(12.dp))
                        .clickable { selectedIndex = index }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = phrase,
                        color = if (isSelected) RaqeemColors.EmeraldGreen else RaqeemColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }

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
                text = "«$activePhrase»",
                color = RaqeemColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Extracted Result Card from real parser
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
                        val category = item?.category ?: ExpenseCategory.TRANSPORT
                        val catColor = when (category) {
                            ExpenseCategory.GROCERIES -> RaqeemColors.CatGroceries
                            ExpenseCategory.DINING -> RaqeemColors.CatDining
                            ExpenseCategory.TRANSPORT -> RaqeemColors.CatTransport
                            ExpenseCategory.BILLS -> RaqeemColors.CatBills
                            ExpenseCategory.HEALTH -> RaqeemColors.CatHealth
                            ExpenseCategory.GENERAL -> RaqeemColors.CatGeneral
                        }
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(catColor)
                        )
                        Text(
                            text = category.arabicTitle,
                            color = catColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = item?.title ?: "استخراج تلقائي",
                        color = RaqeemColors.TextTertiary,
                        fontSize = 12.sp
                    )
                }

                Text(
                    text = "${item?.amount ?: 50.0} ${item?.currency ?: currency}",
                    color = RaqeemColors.EmeraldGreen,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ExpenseSplitInteractiveCard(currency: String) {
    var splitMode by rememberSaveable { mutableIntStateOf(0) } // 0: equal split, 1: paid by one
    val parser = remember { LocalDialectExpenseParser() }

    val phrase = if (splitMode == 0) {
        "عشاء بستين شيكل بيني وبين أحمد"
    } else {
        "تاكسي بثلاثين على حساب أحمد"
    }

    val batch = remember(phrase, currency) {
        parser.parseSpokenText(phrase, defaultCurrency = currency)
    }
    val item = batch.items.firstOrNull()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Mode Selector Chips
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(RaqeemColors.SurfaceElevated)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val isOpt0 = splitMode == 0
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isOpt0) RaqeemColors.SurfaceCard else Color.Transparent)
                    .clickable { splitMode = 0 }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "قسمة بالتساوي",
                    color = if (isOpt0) RaqeemColors.EmeraldGreen else RaqeemColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (isOpt0) FontWeight.SemiBold else FontWeight.Normal
                )
            }

            val isOpt1 = splitMode == 1
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isOpt1) RaqeemColors.SurfaceCard else Color.Transparent)
                    .clickable { splitMode = 1 }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "على حساب شخص",
                    color = if (isOpt1) RaqeemColors.EmeraldGreen else RaqeemColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (isOpt1) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }

        // Speech Bubble
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(RaqeemColors.SurfaceElevated)
                .border(1.dp, RaqeemColors.EmeraldGreen.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RaqeemIcons.Users(size = 20.dp, tint = RaqeemColors.EmeraldGreen)
            Text(
                text = "«$phrase»",
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
            if (splitMode == 0) {
                // 50% / 50%
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "أنت", color = RaqeemColors.TextSecondary, fontSize = 12.sp)
                    Text(
                        text = "30.0 $currency",
                        color = RaqeemColors.EmeraldGreen,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = "50%", color = RaqeemColors.TextTertiary, fontSize = 10.sp)
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(36.dp)
                        .background(Color.White.copy(alpha = 0.1f))
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "أحمد", color = RaqeemColors.TextSecondary, fontSize = 12.sp)
                    Text(
                        text = "30.0 $currency",
                        color = RaqeemColors.EmeraldGreen,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = "50%", color = RaqeemColors.TextTertiary, fontSize = 10.sp)
                }
            } else {
                // Paid by Ahmed
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "الدافع", color = RaqeemColors.TextSecondary, fontSize = 12.sp)
                    Text(
                        text = "أحمد",
                        color = RaqeemColors.EmeraldGreen,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = "30.0 $currency", color = RaqeemColors.TextTertiary, fontSize = 10.sp)
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(36.dp)
                        .background(Color.White.copy(alpha = 0.1f))
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "المستفيد", color = RaqeemColors.TextSecondary, fontSize = 12.sp)
                    Text(
                        text = "الجميع",
                        color = RaqeemColors.EmeraldGreen,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = "100%", color = RaqeemColors.TextTertiary, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun OfflinePrivacyInteractiveCard(
    micRequested: Boolean,
    onRequestMic: () -> Unit
) {
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

        // Honest Guarantees (No false encryption claims)
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
            PrivacyCheckRow(text = "تخزين محلي مباشر بصيغة JSON على جهازك")
        }

        // Mic Permission Request Button
        OutlinedButton(
            onClick = onRequestMic,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (micRequested) RaqeemColors.EmeraldGreen else Color.White.copy(alpha = 0.15f)
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = RaqeemColors.SurfaceElevated,
                contentColor = if (micRequested) RaqeemColors.EmeraldGreen else RaqeemColors.TextPrimary
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (micRequested) {
                    RaqeemIcons.Check(size = 16.dp, tint = RaqeemColors.EmeraldGreen)
                    Text(
                        text = "تم طلب إذن الميكروفون",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    RaqeemIcons.MicWave(size = 16.dp, tint = RaqeemColors.EmeraldGreen)
                    Text(
                        text = "منح إذن الميكروفون للتسجيل",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InitialSetupInteractiveCard(
    selectedCurrency: String,
    onSelectCurrency: (String) -> Unit,
    selectedBudget: Double,
    onSelectBudget: (Double) -> Unit
) {
    val currencies = listOf(
        "₪" to "شيكل",
        "ر.س" to "ريال",
        "ج.م" to "جنيه",
        "د.أ" to "دينار",
        "$" to "دولار"
    )

    val budgetPresets = listOf(500.0, 1000.0, 1500.0, 2000.0, 3000.0)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Currency Header
        Text(
            text = "العملة الأساسية",
            color = RaqeemColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )

        // Currency Chips
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            currencies.forEach { (symbol, name) ->
                val isSelected = selectedCurrency == symbol
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) RaqeemColors.SurfaceElevated else RaqeemColors.BackgroundSlate)
                        .border(
                            1.dp,
                            if (isSelected) RaqeemColors.EmeraldGreen else Color.White.copy(alpha = 0.08f),
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelectCurrency(symbol) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "$symbol $name",
                        color = if (isSelected) RaqeemColors.EmeraldGreen else RaqeemColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Weekly Budget Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "الميزانية الأسبوعية التقديرية",
                color = RaqeemColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${selectedBudget.toInt()} $selectedCurrency",
                color = RaqeemColors.EmeraldGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Budget Preset Chips
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            budgetPresets.forEach { amount ->
                val isSelected = selectedBudget == amount
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) RaqeemColors.SurfaceElevated else RaqeemColors.BackgroundSlate)
                        .border(
                            1.dp,
                            if (isSelected) RaqeemColors.EmeraldGreen else Color.White.copy(alpha = 0.08f),
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelectBudget(amount) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "${amount.toInt()}",
                        color = if (isSelected) RaqeemColors.EmeraldGreen else RaqeemColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Text(
            text = "يمكنك تعديل العملة والميزانية في أي وقت لاحقاً.",
            color = RaqeemColors.TextTertiary,
            fontSize = 12.sp
        )
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
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
