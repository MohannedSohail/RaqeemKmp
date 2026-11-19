package org.mohtech.app.feature.dashboard.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.mohtech.app.feature.dashboard.ExpenseViewModel

/**
 * Raqeem (رقيم) Main Dashboard Screen
 * Delegates to the unified multi-tab [MainShell].
 */
@Composable
fun DashboardScreen(
    viewModel: ExpenseViewModel,
    modifier: Modifier = Modifier,
    onRequestMicPermission: (() -> Unit)? = null
) {
    MainShell(
        viewModel = viewModel,
        modifier = modifier,
        onRequestMicPermission = onRequestMicPermission
    )
}
