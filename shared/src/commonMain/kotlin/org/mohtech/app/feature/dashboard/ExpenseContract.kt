package org.mohtech.app.feature.dashboard

import org.mohtech.app.data.model.ExpenseBatch
import org.mohtech.app.data.model.ExpenseCategory
import org.mohtech.app.data.model.ExpenseItem

/**
 * MVI Contract for Raqeem Dashboard & Expense Management
 */
data class ExpenseUiState(
    val isLoading: Boolean = false,
    val isRecording: Boolean = false,
    val recordingAmplitude: Float = 0f,
    val todayTotal: Double = 0.0,
    val weeklyTotal: Double = 0.0,
    val activeCurrency: String = "₪",
    val recentExpenses: List<ExpenseItem> = emptyList(),
    val filteredExpenses: List<ExpenseItem> = emptyList(),
    val selectedCategoryFilter: ExpenseCategory? = null,
    val reviewSheetPendingBatch: ExpenseBatch? = null,
    val isHardwareSpeechAvailable: Boolean = true,
    val showFallbackManualSheet: Boolean = false,
    val isOnboardingDone: Boolean = false,
    val userErrorMessage: String? = null
)

sealed interface ExpenseIntent {
    data object LoadInitialData : ExpenseIntent
    data object CheckPermissionsAndStartRecord : ExpenseIntent
    data object StopRecording : ExpenseIntent
    data class UpdateAmplitude(val amplitude: Float) : ExpenseIntent
    data class ParseManualSpeechText(val rawText: String) : ExpenseIntent
    data class ParseBankSms(val smsBody: String) : ExpenseIntent
    data class ConfirmBatch(val batch: ExpenseBatch) : ExpenseIntent
    data class DeleteExpense(val expenseId: String) : ExpenseIntent
    data class FilterByCategory(val category: ExpenseCategory?) : ExpenseIntent
    data object DismissReviewSheet : ExpenseIntent
    data object DismissFallbackSheet : ExpenseIntent
    data object CompleteOnboarding : ExpenseIntent
}

sealed interface ExpenseEffect {
    data class ShowToast(val message: String) : ExpenseEffect
    data object TriggerHapticFeedback : ExpenseEffect
    data object RequestMicPermission : ExpenseEffect
    data class NavigateToDetails(val expenseId: String) : ExpenseEffect
}
