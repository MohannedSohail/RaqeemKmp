package org.mohtech.app.feature.dashboard.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mohtech.app.core.haptics.HapticKind
import org.mohtech.app.core.haptics.LocalRaqeemHaptics
import org.mohtech.app.core.theme.RaqeemColors
import org.mohtech.app.core.ui.BackHandler
import org.mohtech.app.core.ui.RaqeemIcons
import org.mohtech.app.core.ui.RaqeemSnackbarData
import org.mohtech.app.core.ui.RaqeemSnackbarHost
import org.mohtech.app.feature.dashboard.ExpenseEffect
import org.mohtech.app.feature.dashboard.ExpenseIntent
import org.mohtech.app.feature.dashboard.ExpenseUiState
import org.mohtech.app.feature.dashboard.ExpenseViewModel
import org.mohtech.app.feature.dashboard.MainTab
import org.mohtech.app.feature.dashboard.MessageKind
import org.mohtech.app.feature.dashboard.ProcessingStage
import org.mohtech.app.feature.dashboard.UndoOperation
import org.mohtech.app.feature.dashboard.ui.tabs.HistoryTab
import org.mohtech.app.feature.dashboard.ui.tabs.HomeTab
import org.mohtech.app.feature.dashboard.ui.tabs.ReportsTab
import org.mohtech.app.feature.dashboard.ui.tabs.SettingsTab
import org.mohtech.app.feature.reports.PlatformReportBridge

/**
 * MainShell:
 * Comprehensive multi-tab container for Raqeem (رقيم).
 * Implements:
 * - 4 core tabs (Home, History, Reports, Settings)
 * - Adaptive layout: Bottom navigation bar in portrait (< 600dp), Navigation rail in landscape (>= 600dp)
 * - Floating voice record button visible strictly on Home and History tabs
 * - Fluid RTL crossfade transitions
 * - System back key interceptor returning to Home tab
 * - Calm, UnSlop design tokens (1dp hairline borders @ 8% alpha, Cairo typography, single bright emerald focal point)
 */
@Composable
fun MainShell(
    viewModel: ExpenseViewModel,
    modifier: Modifier = Modifier,
    onRequestMicPermission: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsState()
    val haptics = LocalRaqeemHaptics.current

    var activeSnackbarData by remember { mutableStateOf<RaqeemSnackbarData?>(null) }
    var showBudgetEditor by rememberSaveable { mutableStateOf(false) }

    // Intercept Back button: if on another tab, return to HomeTab first
    BackHandler(enabled = state.currentTab != MainTab.HOME) {
        viewModel.handleIntent(ExpenseIntent.SelectTab(MainTab.HOME))
    }

    // Effect collector
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ExpenseEffect.ShowMessage -> {
                    activeSnackbarData = RaqeemSnackbarData(
                        message = effect.message,
                        kind = effect.kind,
                        undo = effect.undo,
                        onAction = if (effect.undo != null) {
                            { viewModel.handleIntent(ExpenseIntent.UndoLast) }
                        } else null,
                        onDismiss = { activeSnackbarData = null }
                    )
                }
                is ExpenseEffect.ShowToast -> {
                    activeSnackbarData = RaqeemSnackbarData(
                        message = effect.message,
                        kind = MessageKind.INFO,
                        undo = null,
                        onAction = null,
                        onDismiss = { activeSnackbarData = null }
                    )
                }
                is ExpenseEffect.ShowUndoSnackbar -> {
                    activeSnackbarData = RaqeemSnackbarData(
                        message = "تم حذف ${effect.item.title}",
                        kind = MessageKind.SUCCESS,
                        undo = UndoOperation.Deleted(effect.item),
                        onAction = { viewModel.handleIntent(ExpenseIntent.UndoLast) },
                        onDismiss = { activeSnackbarData = null }
                    )
                }
                is ExpenseEffect.Haptic -> {
                    haptics.perform(effect.kind)
                }
                is ExpenseEffect.TriggerHapticFeedback -> {
                    haptics.perform(HapticKind.CONFIRM)
                }
                is ExpenseEffect.RequestMicPermission -> {
                    onRequestMicPermission?.invoke()
                }
                is ExpenseEffect.ShareReportFile -> {
                    PlatformReportBridge.shareFile(effect.filePath, effect.mimeType)
                }
                is ExpenseEffect.SaveReportFile -> {
                    PlatformReportBridge.saveFile(effect.sourcePath, effect.defaultFileName, effect.mimeType)
                }
                else -> {}
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(RaqeemColors.BackgroundSlate)) {
        val isLandscapeRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = RaqeemColors.BackgroundSlate,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                MainShellTopBar(
                    currentTab = state.currentTab,
                    recentCount = state.filteredExpenses.size,
                    onOpenManualSheet = {
                        viewModel.handleIntent(ExpenseIntent.OpenManualSheet)
                    }
                )
            },
            bottomBar = {
                if (!isLandscapeRail) {
                    RaqeemBottomNavBar(
                        currentTab = state.currentTab,
                        onSelectTab = { tab ->
                            if (tab != state.currentTab) {
                                haptics.perform(HapticKind.TICK)
                                viewModel.handleIntent(ExpenseIntent.SelectTab(tab))
                            }
                        }
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Landscape Navigation Rail
                if (isLandscapeRail) {
                    RaqeemNavRail(
                        currentTab = state.currentTab,
                        onSelectTab = { tab ->
                            if (tab != state.currentTab) {
                                haptics.perform(HapticKind.TICK)
                                viewModel.handleIntent(ExpenseIntent.SelectTab(tab))
                            }
                        }
                    )
                }

                // Main Tab Content Area
                MainTabContainer(
                    state = state,
                    viewModel = viewModel,
                    isLandscapeRail = isLandscapeRail,
                    activeSnackbarData = activeSnackbarData,
                    onRequestMicPermission = onRequestMicPermission,
                    onOpenBudgetEditor = { showBudgetEditor = true },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }

        // Voice Processing Overlay
        VoiceProcessingOverlay(
            stage = state.processingStage,
            lastAmplitude = state.recordingAmplitude,
            pendingBatch = state.reviewSheetPendingBatch,
            onCancel = {
                viewModel.handleIntent(ExpenseIntent.CancelProcessing)
            },
            onReadyFinished = {
                viewModel.handleIntent(ExpenseIntent.FinishProcessingReady)
            }
        )

        // Review Sheet Overlay
        state.reviewSheetPendingBatch?.let { batch ->
            if (state.processingStage == ProcessingStage.IDLE) {
                ExpenseReviewSheet(
                    batch = batch,
                    onConfirm = { confirmedBatch ->
                        viewModel.handleIntent(ExpenseIntent.ConfirmBatch(confirmedBatch))
                    },
                    onDismiss = {
                        viewModel.handleIntent(ExpenseIntent.DismissReviewSheet)
                    }
                )
            }
        }

        // Polite Fallback Sheet Overlay
        if (state.showFallbackManualSheet) {
            PoliteFallbackSheet(
                currency = state.activeCurrency,
                onParseText = { text ->
                    viewModel.handleIntent(ExpenseIntent.ParseManualSpeechText(text))
                },
                onDismiss = {
                    viewModel.handleIntent(ExpenseIntent.DismissFallbackSheet)
                }
            )
        }

        // Destructive / Duplication Confirmation Dialog
        if (state.pendingCardAction != null) {
            ConfirmActionDialog(
                action = state.pendingCardAction,
                onConfirm = {
                    viewModel.handleIntent(ExpenseIntent.ConfirmPendingAction)
                },
                onDismiss = {
                    viewModel.handleIntent(ExpenseIntent.DismissPendingAction)
                }
            )
        }

        // Budget Editor Dialog
        if (showBudgetEditor) {
            BudgetEditorDialog(
                currentBudget = state.weeklyBudget,
                currency = state.activeCurrency,
                onSave = { newBudget ->
                    viewModel.handleIntent(ExpenseIntent.SetWeeklyBudget(newBudget))
                    showBudgetEditor = false
                },
                onDismiss = { showBudgetEditor = false }
            )
        }
    }
}

/**
 * Top bar header adapting calmly to the active tab.
 */
@Composable
private fun MainShellTopBar(
    currentTab: MainTab,
    recentCount: Int,
    onOpenManualSheet: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(RaqeemColors.SurfaceElevated)
                    .border(1.dp, RaqeemColors.EmeraldGreen.copy(alpha = 0.35f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ر",
                    color = RaqeemColors.EmeraldGreen,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                when (currentTab) {
                    MainTab.HOME -> {
                        Text(
                            text = "رقيم",
                            color = RaqeemColors.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "تتبع المصاريف بالصوت",
                            color = RaqeemColors.TextTertiary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                    MainTab.HISTORY -> {
                        Text(
                            text = "سجل المصروفات",
                            color = RaqeemColors.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (recentCount > 0) "$recentCount عملية مسجلة" else "لا توجد مصاريف بعد",
                            color = RaqeemColors.TextTertiary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                    MainTab.REPORTS -> {
                        Text(
                            text = "التقارير المالية",
                            color = RaqeemColors.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "تحليل وتصدير المستندات",
                            color = RaqeemColors.TextTertiary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                    MainTab.SETTINGS -> {
                        Text(
                            text = "الإعدادات",
                            color = RaqeemColors.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "تفضيلات الميزانية والعملة",
                            color = RaqeemColors.TextTertiary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }
        }

        // Quick Keyboard Entry Button
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(RaqeemColors.SurfaceElevated)
                .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = RaqeemColors.TextPrimary),
                    onClick = onOpenManualSheet
                ),
            contentAlignment = Alignment.Center
        ) {
            RaqeemIcons.Keyboard(
                size = 18.dp,
                tint = RaqeemColors.TextSecondary
            )
        }
    }
}

/**
 * Mobile Bottom Navigation Bar (Portrait)
 */
@Composable
private fun RaqeemBottomNavBar(
    currentTab: MainTab,
    onSelectTab: (MainTab) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(RaqeemColors.SurfaceElevated)
            .border(width = 1.dp, color = Color.White.copy(alpha = 0.08f), shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .navigationBarsPadding()
            .height(64.dp)
            .padding(horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(
                tab = MainTab.HOME,
                isSelected = currentTab == MainTab.HOME,
                title = "الرئيسية",
                onClick = { onSelectTab(MainTab.HOME) }
            )
            BottomNavItem(
                tab = MainTab.HISTORY,
                isSelected = currentTab == MainTab.HISTORY,
                title = "السجل",
                onClick = { onSelectTab(MainTab.HISTORY) }
            )
            BottomNavItem(
                tab = MainTab.REPORTS,
                isSelected = currentTab == MainTab.REPORTS,
                title = "التقارير",
                onClick = { onSelectTab(MainTab.REPORTS) }
            )
            BottomNavItem(
                tab = MainTab.SETTINGS,
                isSelected = currentTab == MainTab.SETTINGS,
                title = "الإعدادات",
                onClick = { onSelectTab(MainTab.SETTINGS) }
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    tab: MainTab,
    isSelected: Boolean,
    title: String,
    onClick: () -> Unit
) {
    val tint = if (isSelected) RaqeemColors.EmeraldGreen else RaqeemColors.TextTertiary

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = RaqeemColors.EmeraldGreen.copy(alpha = 0.15f)),
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        when (tab) {
            MainTab.HOME -> RaqeemIcons.Home(size = 20.dp, tint = tint)
            MainTab.HISTORY -> RaqeemIcons.List(size = 20.dp, tint = tint)
            MainTab.REPORTS -> RaqeemIcons.Chart(size = 20.dp, tint = tint)
            MainTab.SETTINGS -> RaqeemIcons.Settings(size = 20.dp, tint = tint)
        }
        Text(
            text = title,
            color = tint,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/**
 * Tablet / Landscape Navigation Rail
 */
@Composable
private fun RaqeemNavRail(
    currentTab: MainTab,
    onSelectTab: (MainTab) -> Unit
) {
    Column(
        modifier = Modifier
            .width(88.dp)
            .fillMaxHeight()
            .background(RaqeemColors.SurfaceElevated)
            .border(width = 1.dp, color = Color.White.copy(alpha = 0.08f))
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        BottomNavItem(
            tab = MainTab.HOME,
            isSelected = currentTab == MainTab.HOME,
            title = "الرئيسية",
            onClick = { onSelectTab(MainTab.HOME) }
        )
        BottomNavItem(
            tab = MainTab.HISTORY,
            isSelected = currentTab == MainTab.HISTORY,
            title = "السجل",
            onClick = { onSelectTab(MainTab.HISTORY) }
        )
        BottomNavItem(
            tab = MainTab.REPORTS,
            isSelected = currentTab == MainTab.REPORTS,
            title = "التقارير",
            onClick = { onSelectTab(MainTab.REPORTS) }
        )
        BottomNavItem(
            tab = MainTab.SETTINGS,
            isSelected = currentTab == MainTab.SETTINGS,
            title = "الإعدادات",
            onClick = { onSelectTab(MainTab.SETTINGS) }
        )
    }
}

@Composable
private fun MainTabContainer(
    state: ExpenseUiState,
    viewModel: ExpenseViewModel,
    isLandscapeRail: Boolean,
    activeSnackbarData: RaqeemSnackbarData?,
    onRequestMicPermission: (() -> Unit)?,
    onOpenBudgetEditor: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        AnimatedContent(
            targetState = state.currentTab,
            transitionSpec = {
                (fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                    scaleIn(initialScale = 0.98f, animationSpec = tween(220, easing = FastOutSlowInEasing)))
                    .togetherWith(fadeOut(animationSpec = tween(180)))
            },
            label = "MainTabTransition"
        ) { targetTab ->
            when (targetTab) {
                MainTab.HOME -> {
                    HomeTab(
                        state = state,
                        onConfigureBudget = onOpenBudgetEditor,
                        onShowAllHistory = {
                            viewModel.handleIntent(ExpenseIntent.SelectTab(MainTab.HISTORY))
                        },
                        onEditExpense = { item ->
                            viewModel.handleIntent(ExpenseIntent.EditExpense(item))
                        },
                        onRequestDelete = { item ->
                            viewModel.handleIntent(ExpenseIntent.RequestDelete(item))
                        },
                        onRequestRepeat = { item ->
                            viewModel.handleIntent(ExpenseIntent.RequestRepeat(item))
                        },
                        onChangeCategory = { item, cat ->
                            viewModel.handleIntent(ExpenseIntent.ChangeCategory(item, cat))
                        }
                    )
                }
                MainTab.HISTORY -> {
                    HistoryTab(
                        state = state,
                        onSearchQueryChange = { query ->
                            viewModel.handleIntent(ExpenseIntent.SearchExpenses(query))
                        },
                        onSelectCategoryFilter = { cat ->
                            viewModel.handleIntent(ExpenseIntent.FilterByCategory(cat))
                        },
                        onEditExpense = { item ->
                            viewModel.handleIntent(ExpenseIntent.EditExpense(item))
                        },
                        onRequestDelete = { item ->
                            viewModel.handleIntent(ExpenseIntent.RequestDelete(item))
                        },
                        onRequestRepeat = { item ->
                            viewModel.handleIntent(ExpenseIntent.RequestRepeat(item))
                        },
                        onChangeCategory = { item, cat ->
                            viewModel.handleIntent(ExpenseIntent.ChangeCategory(item, cat))
                        }
                    )
                }
                MainTab.REPORTS -> {
                    ReportsTab(
                        state = state,
                        onSelectPeriod = { period ->
                            viewModel.handleIntent(ExpenseIntent.SelectReportPeriod(period))
                        },
                        onExportExcel = {
                            viewModel.handleIntent(ExpenseIntent.ExportExcelReport)
                        },
                        onExportPdf = {
                            viewModel.handleIntent(ExpenseIntent.ExportPdfReport)
                        },
                        onDismissExportResult = {
                            viewModel.handleIntent(ExpenseIntent.DismissExportResult)
                        },
                        onShareExportedFile = { path, mime ->
                            PlatformReportBridge.shareFile(path, mime)
                        },
                        onSaveExportedFile = { path, name, mime ->
                            PlatformReportBridge.saveFile(path, name, mime)
                        }
                    )
                }
                MainTab.SETTINGS -> {
                    SettingsTab(
                        state = state,
                        onOpenBudgetEditor = onOpenBudgetEditor,
                        onSelectCurrency = { currency ->
                            viewModel.handleIntent(ExpenseIntent.SetPreferredCurrency(currency))
                        },
                        onSelectWeekStartDay = { dayIso ->
                            viewModel.handleIntent(ExpenseIntent.SetWeekStartDay(dayIso))
                        },
                        onRequestMicPermission = {
                            onRequestMicPermission?.invoke()
                        }
                    )
                }
            }
        }

        // Floating Voice Record Button: strictly visible on HOME and HISTORY tabs (or active recording)
        val showRecordFab = (state.currentTab == MainTab.HOME || state.currentTab == MainTab.HISTORY) || state.isRecording
        AnimatedVisibility(
            visible = showRecordFab,
            enter = scaleIn(animationSpec = tween(250)) + fadeIn(animationSpec = tween(200)),
            exit = scaleOut(animationSpec = tween(200)) + fadeOut(animationSpec = tween(150)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (isLandscapeRail) 24.dp else 16.dp)
        ) {
            VoiceRecordButton(
                isRecording = state.isRecording,
                amplitude = state.recordingAmplitude,
                onRecordToggle = {
                    if (!state.isProcessing) {
                        if (state.isRecording) {
                            viewModel.handleIntent(ExpenseIntent.StopRecording)
                        } else {
                            viewModel.handleIntent(ExpenseIntent.CheckPermissionsAndStartRecord)
                        }
                    }
                }
            )
        }

        // RaqeemSnackbarHost positioned above bottom bar / mic button
        val snackbarBottomPadding = when {
            isLandscapeRail -> 16.dp
            showRecordFab -> 84.dp
            else -> 14.dp
        }

        RaqeemSnackbarHost(
            snackbarData = activeSnackbarData,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = snackbarBottomPadding)
        )
    }
}


