package org.mohtech.app.feature.dashboard

import org.mohtech.app.core.haptics.HapticKind
import org.mohtech.app.data.model.ExpenseBatch
import org.mohtech.app.data.model.ExpenseCategory
import org.mohtech.app.data.model.ExpenseItem
import org.mohtech.app.feature.reports.ReportData
import org.mohtech.app.feature.reports.ReportPeriod

enum class ProcessingStage {
    IDLE,
    FINALIZING_AUDIO,
    TRANSCRIBING,
    UNDERSTANDING,
    READY
}

/**
 * Main application tabs inside DASHBOARD destination.
 */
enum class MainTab {
    HOME,
    HISTORY,
    REPORTS,
    SETTINGS
}

/**
 * Pending card action requiring explicit confirmation dialog.
 */
sealed interface CardAction {
    data class Delete(val item: ExpenseItem) : CardAction
    data class Repeat(val item: ExpenseItem) : CardAction
}

/**
 * Supported undoable actions in Raqeem.
 */
sealed interface UndoOperation {
    data class Deleted(val item: ExpenseItem) : UndoOperation
    data class Repeated(val newItemId: String) : UndoOperation
    data class CategoryChanged(val item: ExpenseItem, val previousCategory: ExpenseCategory) : UndoOperation
    data class SettlementRecorded(
        val expenseId: String,
        val allocationId: String,
        val settlementId: String,
        val previousExpense: ExpenseItem
    ) : UndoOperation
    data class SettlementVoided(
        val expenseId: String,
        val settlementId: String,
        val previousExpense: ExpenseItem
    ) : UndoOperation
}

enum class MessageKind {
    SUCCESS,
    ERROR,
    INFO
}

/**
 * MVI Contract for Raqeem Dashboard & Expense Management
 */
data class ExpenseUiState(
    val currentTab: MainTab = MainTab.HOME,
    val isLoading: Boolean = false,
    val isRecording: Boolean = false,
    val recordingAmplitude: Float = 0f,
    val processingStage: ProcessingStage = ProcessingStage.IDLE,
    val todayTotal: Double = 0.0,
    val weeklyTotal: Double = 0.0,
    val weeklyBudget: Double = 0.0, // Unset by default (0.0 means unconfigured)
    val weekStartDay: Int = 6, // 6 = Saturday (default in Arab region)
    val activeCurrency: String = "₪",
    val recentExpenses: List<ExpenseItem> = emptyList(),
    val filteredExpenses: List<ExpenseItem> = emptyList(),
    val searchQuery: String = "",
    val selectedCategoryFilter: ExpenseCategory? = null,
    val reviewSheetPendingBatch: ExpenseBatch? = null,
    val isHardwareSpeechAvailable: Boolean = true,
    val showFallbackManualSheet: Boolean = false,
    val showPermissionRationaleDialog: Boolean = false,
    val showPermissionRationaleSheet: Boolean = false,
    val isOnboardingDone: Boolean = false,
    val userErrorMessage: String? = null,
    val pendingCardAction: CardAction? = null,
    val highlightedExpenseId: String? = null,
    val lastUndoOperation: UndoOperation? = null,
    val showBudgetEditor: Boolean = false,
    val selectedReportPeriod: ReportPeriod = ReportPeriod.THIS_WEEK,
    val reportData: ReportData? = null,
    val isExportingReport: Boolean = false,
    val exportSuccessPath: String? = null,
    val exportSuccessName: String? = null,
    val exportSuccessSize: Long = 0L,
    val exportMimeType: String? = null,
    val exportErrorMessage: String? = null,
    val showReceivablesScreen: Boolean = false,
    val receivablesTotalRemaining: Double = 0.0
) {
    val dailyTotal: Double get() = todayTotal
    val isProcessing: Boolean get() = processingStage != ProcessingStage.IDLE
    val isBudgetSet: Boolean get() = weeklyBudget > 0.0
    val budgetProgressRatio: Float get() = if (weeklyBudget > 0.0) {
        (weeklyTotal / weeklyBudget).toFloat().coerceIn(0f, 1f)
    } else 0f
}

sealed interface ExpenseIntent {
    data object LoadInitialData : ExpenseIntent
    data class SelectTab(val tab: MainTab) : ExpenseIntent
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
    data class SearchExpenses(val query: String) : ExpenseIntent
    data class UpdateWeeklyBudget(val budget: Double) : ExpenseIntent
    data class SetWeeklyBudget(val budget: Double) : ExpenseIntent
    data class SetWeekStartDay(val dayIso: Int) : ExpenseIntent
    data class SetPreferredCurrency(val currency: String) : ExpenseIntent
    data object OpenBudgetEditor : ExpenseIntent
    data object DismissBudgetEditor : ExpenseIntent
    data class RequestDelete(val item: ExpenseItem) : ExpenseIntent
    data class RequestRepeat(val item: ExpenseItem) : ExpenseIntent
    data class RepeatExpense(val item: ExpenseItem) : ExpenseIntent
    data class ChangeCategory(val item: ExpenseItem, val newCategory: ExpenseCategory) : ExpenseIntent
    data object ConfirmPendingAction : ExpenseIntent
    data object DismissPendingAction : ExpenseIntent
    data object UndoLast : ExpenseIntent
    data object ClearHighlight : ExpenseIntent
    data class SelectReportPeriod(val period: ReportPeriod) : ExpenseIntent
    data object ExportExcelReport : ExpenseIntent
    data object ExportPdfReport : ExpenseIntent
    data object DismissExportResult : ExpenseIntent
    data object OpenManualSheet : ExpenseIntent
    data object DismissReviewSheet : ExpenseIntent
    data object DismissFallbackSheet : ExpenseIntent
    data object DismissPermissionRationale : ExpenseIntent
    data object CompleteOnboarding : ExpenseIntent
    data object OpenReceivables : ExpenseIntent
    data object CloseReceivables : ExpenseIntent
    data class RecordSettlement(
        val expenseId: String,
        val allocationId: String,
        val amount: Double,
        val settledAt: Long,
        val note: String = ""
    ) : ExpenseIntent
    data class VoidSettlement(
        val expenseId: String,
        val settlementId: String
    ) : ExpenseIntent
    data class RenameParty(val oldName: String, val newName: String) : ExpenseIntent
    data class MergeParties(val sourceName: String, val targetName: String) : ExpenseIntent
}

sealed interface ExpenseEffect {
    data class ShowToast(val message: String) : ExpenseEffect
    data class ShowUndoSnackbar(val item: ExpenseItem) : ExpenseEffect
    data class ShowMessage(
        val message: String,
        val kind: MessageKind = MessageKind.INFO,
        val undo: UndoOperation? = null
    ) : ExpenseEffect
    data class Haptic(val kind: HapticKind) : ExpenseEffect
    data object TriggerHapticFeedback : ExpenseEffect
    data object RequestMicPermission : ExpenseEffect
    data class NavigateToDetails(val expenseId: String) : ExpenseEffect
    data class ShareReportFile(val filePath: String, val mimeType: String) : ExpenseEffect
    data class SaveReportFile(val sourcePath: String, val defaultFileName: String, val mimeType: String) : ExpenseEffect
}
