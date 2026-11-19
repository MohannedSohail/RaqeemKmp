package org.mohtech.app.feature.dashboard

import org.mohtech.app.data.model.ExpenseBatch
import org.mohtech.app.data.model.ExpenseCategory
import org.mohtech.app.data.model.ExpenseItem

enum class ProcessingStage {
    IDLE,
    FINALIZING_AUDIO,
    TRANSCRIBING,
    UNDERSTANDING,
    READY
}

/**
 * MVI Contract for Raqeem Dashboard & Expense Management
 */
data class ExpenseUiState(
    val isLoading: Boolean = false,
    val isRecording: Boolean = false,
    val recordingAmplitude: Float = 0f,
    val processingStage: ProcessingStage = ProcessingStage.IDLE,
    val todayTotal: Double = 0.0,
    val weeklyTotal: Double = 0.0,
    val weeklyBudget: Double = 2500.0,
    val activeCurrency: String = "₪",
    val recentExpenses: List<ExpenseItem> = emptyList(),
    val filteredExpenses: List<ExpenseItem> = emptyList(),
    val selectedCategoryFilter: ExpenseCategory? = null,
    val reviewSheetPendingBatch: ExpenseBatch? = null,
    val isHardwareSpeechAvailable: Boolean = true,
    val showFallbackManualSheet: Boolean = false,
    val showPermissionRationaleDialog: Boolean = false,
    val showPermissionRationaleSheet: Boolean = false,
    val isOnboardingDone: Boolean = false,
    val userErrorMessage: String? = null
) {
    val dailyTotal: Double get() = todayTotal
    val isProcessing: Boolean get() = processingStage != ProcessingStage.IDLE
    val budgetProgressRatio: Float get() = if (weeklyBudget > 0.0) {
        (weeklyTotal / weeklyBudget).toFloat().coerceIn(0f, 1f)
    } else 0f
}

sealed interface ExpenseIntent {
    data object LoadInitialData : ExpenseIntent
    data object CheckPermissionsAndStartRecord : ExpenseIntent
    data object StopRecording : ExpenseIntent
    data object CancelProcessing : ExpenseIntent
    data object FinishProcessingReady : ExpenseIntent
    data class UpdateAmplitude(val amplitude: Float) : ExpenseIntent
    data class ParseManualSpeechText(val rawText: String) : ExpenseIntent
    data class ParseBankSms(val smsBody: String) : ExpenseIntent
    data class ConfirmBatch(val batch: ExpenseBatch) : ExpenseIntent
    data class DeleteExpense(val expenseId: String) : ExpenseIntent
    data class RestoreExpense(val item: ExpenseItem) : ExpenseIntent
    data class EditExpense(val item: ExpenseItem) : ExpenseIntent
    data class FilterByCategory(val category: ExpenseCategory?) : ExpenseIntent
    data class UpdateWeeklyBudget(val budget: Double) : ExpenseIntent
    data object OpenManualSheet : ExpenseIntent
    data object DismissReviewSheet : ExpenseIntent
    data object DismissFallbackSheet : ExpenseIntent
    data object DismissPermissionRationale : ExpenseIntent
    data object CompleteOnboarding : ExpenseIntent
}

sealed interface ExpenseEffect {
    data class ShowToast(val message: String) : ExpenseEffect
    data class ShowUndoSnackbar(val item: ExpenseItem) : ExpenseEffect
    data object TriggerHapticFeedback : ExpenseEffect
    data object RequestMicPermission : ExpenseEffect
    data class NavigateToDetails(val expenseId: String) : ExpenseEffect
}
