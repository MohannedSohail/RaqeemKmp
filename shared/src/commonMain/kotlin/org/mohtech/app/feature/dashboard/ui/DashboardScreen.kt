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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mohtech.app.core.theme.RaqeemColors
import org.mohtech.app.core.ui.RaqeemIcons
import org.mohtech.app.feature.dashboard.ExpenseEffect
import org.mohtech.app.feature.dashboard.ExpenseIntent
import org.mohtech.app.feature.dashboard.ExpenseViewModel

@Composable
fun DashboardScreen(
    viewModel: ExpenseViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ExpenseEffect.TriggerHapticFeedback -> {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                else -> {}
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (state.isRecording) {
                viewModel.handleIntent(ExpenseIntent.StopRecording)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = RaqeemColors.BackgroundSlate,
        topBar = {
            DashboardTopBar(
                onOpenManualSheet = {
                    viewModel.handleIntent(ExpenseIntent.OpenManualSheet)
                }
            )
        },
        bottomBar = {
            // Centered Voice Action Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                VoiceRecordButton(
                    isRecording = state.isRecording,
                    amplitude = state.recordingAmplitude,
                    onRecordToggle = {
                        if (state.isRecording) {
                            viewModel.handleIntent(ExpenseIntent.StopRecording)
                        } else {
                            viewModel.handleIntent(ExpenseIntent.CheckPermissionsAndStartRecord)
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
                    }
                )
            }

            // Bottom Spacer to prevent floating record button from covering list content
            item {
                Spacer(modifier = Modifier.height(112.dp))
            }
        }

        // Review Sheet Overlay
        state.reviewSheetPendingBatch?.let { batch ->
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
    }
}

@Composable
private fun DashboardTopBar(
    onOpenManualSheet: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App Title & Brand Seal
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(RaqeemColors.SurfaceElevated)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
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

        // Keyboard / Quick entry button (Clean line icon instead of emoji)
        Box(
            modifier = Modifier
                .size(40.dp)
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
