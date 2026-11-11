package org.mohtech.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import org.mohtech.app.core.theme.RaqeemTheme
import org.mohtech.app.data.repository.LocalExpenseRepository
import org.mohtech.app.data.storage.createAppPreferences
import org.mohtech.app.data.storage.createPlatformExpenseStorage
import org.mohtech.app.feature.dashboard.ExpenseIntent
import org.mohtech.app.feature.dashboard.ExpenseViewModel
import org.mohtech.app.feature.dashboard.ui.DashboardScreen
import org.mohtech.app.feature.onboarding.OnboardingScreen

@Composable
fun App() {
    val preferences = remember { createAppPreferences() }
    val storage = remember { createPlatformExpenseStorage() }
    val repository = remember { LocalExpenseRepository(storage, preferences) }
    val viewModel = remember { ExpenseViewModel(repository, preferences) }

    val state by viewModel.uiState.collectAsState()

    RaqeemTheme {
        if (!state.isOnboardingDone) {
            OnboardingScreen(
                onComplete = {
                    viewModel.handleIntent(ExpenseIntent.CompleteOnboarding)
                }
            )
        } else {
            DashboardScreen(viewModel = viewModel)
        }
    }
}
