package org.mohtech.app.feature.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mohtech.app.core.theme.RaqeemColors
import org.mohtech.app.feature.dashboard.ExpenseIntent
import org.mohtech.app.feature.dashboard.ExpenseViewModel

@Composable
fun DashboardScreen(
    viewModel: ExpenseViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = RaqeemColors.BackgroundSlate,
        topBar = {
            DashboardTopBar(
                onOpenManualSheet = {
                    viewModel.handleIntent(ExpenseIntent.UpdateAmplitude(0f))
                    // Trigger manual fallback sheet
                    viewModel.handleIntent(ExpenseIntent.CheckPermissionsAndStartRecord)
                }
            )
        },
        bottomBar = {
            // Centered Voice Action Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
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
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                // Radar Card
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
                Spacer(modifier = Modifier.height(110.dp))
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
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App Title & Logo
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(RaqeemColors.EmeraldGreen),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ر",
                    color = RaqeemColors.BackgroundSlate,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "رقيم",
                    color = RaqeemColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "تتبع المصاريف بالصوت 100% محلي",
                    color = RaqeemColors.TextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        // Keyboard / Quick entry button
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(RaqeemColors.SurfaceElevated)
                .clickable(onClick = onOpenManualSheet),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "⌨️", fontSize = 18.sp)
        }
    }
}

