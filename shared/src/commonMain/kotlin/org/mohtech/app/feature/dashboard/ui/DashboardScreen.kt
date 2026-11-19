package org.mohtech.app.feature.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.mohtech.app.core.theme.RaqeemColors
import org.mohtech.app.core.ui.RaqeemIcons
import org.mohtech.app.feature.dashboard.ExpenseEffect
import org.mohtech.app.feature.dashboard.ExpenseIntent
import org.mohtech.app.feature.dashboard.ExpenseViewModel
import org.mohtech.app.feature.dashboard.ProcessingStage

/**
 * Raqeem (رقيم) Main Dashboard Screen
 *
 * Mobile layout polish:
 * - Proper SafeArea handling via explicit WindowInsets and statusBarsPadding / navigationBarsPadding.
 * - Generous side margins (20.dp - 24.dp) preventing notch or rounded display corner clipping.
 * - Smooth haptics, responsive voice record control, and clean line iconography.
 */
@Composable
fun DashboardScreen(
    viewModel: ExpenseViewModel,
    modifier: Modifier = Modifier,
    onRequestMicPermission: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    var showExportSheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ExpenseEffect.TriggerHapticFeedback -> {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                is ExpenseEffect.RequestMicPermission -> {
                    onRequestMicPermission?.invoke()
                }
                is ExpenseEffect.ShowToast -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                else -> {}
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = RaqeemColors.BackgroundSlate,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 96.dp)
            )
        },
        topBar = {
            DashboardTopBar(
                onOpenManualSheet = {
                    viewModel.handleIntent(ExpenseIntent.OpenManualSheet)
                },
                onOpenExportSheet = {
                    showExportSheet = true
                }
            )
        },
        bottomBar = {
            // Centered Voice Action Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.Center
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
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                // Radar Card (The One Bright Element)
                BudgetRadarCard(
                    todayTotal = state.todayTotal,
                    weeklyTotal = state.weeklyTotal,
                    weeklyBudget = state.weeklyBudget,
                    currency = state.activeCurrency
                )
            }

            item {
                // History & Category Chips
                ExpenseHistorySection(
                    expenses = state.filteredExpenses,
                    selectedCategory = state.selectedCategoryFilter,
                    onSelectCategory = { cat ->
                        viewModel.handleIntent(ExpenseIntent.FilterByCategory(cat))
                    },
                    onDeleteExpense = { id ->
                        viewModel.handleIntent(ExpenseIntent.DeleteExpense(id))
                    },
                    onEditExpense = { item ->
                        viewModel.handleIntent(ExpenseIntent.EditExpense(item))
                    }
                )
            }

            // Bottom spacer so content scrolls past floating record button
            item {
                Spacer(modifier = Modifier.height(32.dp))
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

        // Export Report Sheet Overlay
        if (showExportSheet) {
            ExportReportSheet(
                totalExpensesCount = state.recentExpenses.size,
                onExportCsv = {
                    val csv = viewModel.exportCsv()
                    clipboardManager.setText(AnnotatedString(csv))
                    showExportSheet = false
                    scope.launch {
                        snackbarHostState.showSnackbar("تم نسخ تقرير CSV إلى الحافظة بنجاح")
                    }
                },
                onExportJson = {
                    val json = viewModel.exportJson()
                    clipboardManager.setText(AnnotatedString(json))
                    showExportSheet = false
                    scope.launch {
                        snackbarHostState.showSnackbar("تم نسخ تقرير JSON إلى الحافظة بنجاح")
                    }
                },
                onDismiss = { showExportSheet = false }
            )
        }
    }
}

@Composable
private fun DashboardTopBar(
    onOpenManualSheet: () -> Unit,
    onOpenExportSheet: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App Title & Brand Seal
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
                Text(
                    text = "رقيم",
                    color = RaqeemColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "تتبع المصاريف بالصوت • محلي 100%",
                    color = RaqeemColors.TextTertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }

        // Action Buttons: Export & Keyboard
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Export button with tactile ripple
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(RaqeemColors.SurfaceElevated)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = RaqeemColors.TextPrimary),
                        onClick = onOpenExportSheet
                    ),
                contentAlignment = Alignment.Center
            ) {
                RaqeemIcons.Export(
                    size = 18.dp,
                    tint = RaqeemColors.TextSecondary
                )
            }

            // Keyboard / Quick entry button with tactile ripple & min touch target
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
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ExportReportSheet(
    totalExpensesCount: Int,
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = RaqeemColors.SurfaceCard,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Drag indicator bar
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            )

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "تصدير التقرير المالي",
                        color = RaqeemColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "إجمالي المصاريف المسجلة: $totalExpensesCount مصروف",
                        color = RaqeemColors.TextTertiary,
                        fontSize = 12.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(RaqeemColors.SurfaceElevated)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    RaqeemIcons.Close(size = 16.dp, tint = RaqeemColors.TextSecondary)
                }
            }

            // CSV Option Card
            ExportFormatCard(
                formatTitle = "ملف جدول بيانات (CSV)",
                formatDesc = "متوافق مع Microsoft Excel و Google Sheets مع ترميز UTF-8",
                badgeText = "RFC 4180",
                badgeColor = RaqeemColors.EmeraldGreen,
                buttonText = "نسخ تقرير CSV",
                onClick = onExportCsv
            )

            // JSON Option Card
            ExportFormatCard(
                formatTitle = "ملف بيانات كامل (JSON)",
                formatDesc = "نسخة احتياطية برمجية تتضمن تفاصيل الأطراف، الإثباتات والتواريخ",
                badgeText = "Backup",
                badgeColor = RaqeemColors.ElectricCyan,
                buttonText = "نسخ تقرير JSON",
                onClick = onExportJson
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ExportFormatCard(
    formatTitle: String,
    formatDesc: String,
    badgeText: String,
    badgeColor: Color,
    buttonText: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RaqeemColors.SurfaceElevated)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTitle,
                    color = RaqeemColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = formatDesc,
                color = RaqeemColors.TextSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(RaqeemColors.SurfaceCard)
                    .border(1.dp, badgeColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = badgeColor),
                        onClick = onClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = buttonText,
                    color = badgeColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
