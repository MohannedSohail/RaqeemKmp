package org.mohtech.app.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import org.mohtech.app.ai.audio.VoiceEngine
import org.mohtech.app.ai.nlp.LocalDialectExpenseParser
import org.mohtech.app.ai.whisper.LocalSpeechTranscriber
import org.mohtech.app.ai.whisper.RawAudioRecorder
import org.mohtech.app.ai.whisper.SpeechError
import org.mohtech.app.ai.whisper.TranscribeResult
import org.mohtech.app.ai.whisper.WhisperModelConfig
import org.mohtech.app.data.model.ExpenseBatch
import org.mohtech.app.data.model.ExpenseCategory
import org.mohtech.app.data.model.ExpenseItem
import org.mohtech.app.data.model.ProofType
import org.mohtech.app.data.repository.ExpenseRepository
import org.mohtech.app.data.storage.AppPreferences

class ExpenseViewModel(
    private val repository: ExpenseRepository,
    private val preferences: AppPreferences,
    private val parser: LocalDialectExpenseParser = LocalDialectExpenseParser(),
    private val voiceEngine: VoiceEngine? = null,
    private val transcriber: LocalSpeechTranscriber? = null,
    private val audioRecorder: RawAudioRecorder? = null
) : ViewModel() {

    private var processingJob: kotlinx.coroutines.Job? = null

    private val _uiState = MutableStateFlow(
        ExpenseUiState(
            activeCurrency = preferences.getPreferredCurrency(),
            isOnboardingDone = preferences.isOnboardingCompleted(),
            weeklyBudget = preferences.getWeeklyBudget(),
            isHardwareSpeechAvailable = voiceEngine?.isAvailable() ?: false
        )
    )
    val uiState: StateFlow<ExpenseUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ExpenseEffect>()
    val effects: SharedFlow<ExpenseEffect> = _effects.asSharedFlow()

    init {
        handleIntent(ExpenseIntent.LoadInitialData)
    }

    fun handleIntent(intent: ExpenseIntent) {
        when (intent) {
            is ExpenseIntent.LoadInitialData -> loadInitialData()
            is ExpenseIntent.CheckPermissionsAndStartRecord -> startRecording()
            is ExpenseIntent.StopRecording -> stopRecording()
            is ExpenseIntent.CancelProcessing -> cancelProcessing()
            is ExpenseIntent.FinishProcessingReady -> finishProcessingReady()
            is ExpenseIntent.UpdateAmplitude -> updateAmplitude(intent.amplitude)
            is ExpenseIntent.ParseManualSpeechText -> parseManualSpeechText(intent.rawText)
            is ExpenseIntent.ParseBankSms -> parseBankSms(intent.smsBody)
            is ExpenseIntent.ConfirmBatch -> confirmBatch(intent.batch)
            is ExpenseIntent.DeleteExpense -> deleteExpense(intent.expenseId)
            is ExpenseIntent.EditExpense -> editExpense(intent.item)
            is ExpenseIntent.FilterByCategory -> filterByCategory(intent.category)
            is ExpenseIntent.UpdateWeeklyBudget -> updateWeeklyBudget(intent.budget)
            is ExpenseIntent.OpenManualSheet -> openManualSheet()
            is ExpenseIntent.DismissReviewSheet -> dismissReviewSheet()
            is ExpenseIntent.DismissFallbackSheet -> dismissFallbackSheet()
            is ExpenseIntent.DismissPermissionRationale -> dismissPermissionRationale()
            is ExpenseIntent.CompleteOnboarding -> completeOnboarding()
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.initialize()

            repository.expensesFlow.collect { expenses ->
                val todayTotal = repository.getTodayTotal()
                val weeklyTotal = repository.getWeeklyTotal()
                val currentFilter = _uiState.value.selectedCategoryFilter

                val filtered = if (currentFilter == null) {
                    expenses
                } else {
                    expenses.filter { it.category == currentFilter }
                }

                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        recentExpenses = expenses,
                        filteredExpenses = filtered,
                        todayTotal = todayTotal,
                        weeklyTotal = weeklyTotal,
                        weeklyBudget = preferences.getWeeklyBudget(),
                        activeCurrency = preferences.getPreferredCurrency(),
                        isOnboardingDone = preferences.isOnboardingCompleted(),
                        isHardwareSpeechAvailable = voiceEngine?.isAvailable() ?: false
                    )
                }
            }
        }
    }

    private fun startRecording() {
        viewModelScope.launch {
            if (_uiState.value.isRecording) return@launch
            _effects.emit(ExpenseEffect.TriggerHapticFeedback)

            if (audioRecorder != null || transcriber != null) {
                if (transcriber?.isModelReady() == false) {
                    _uiState.update {
                        it.copy(
                            isRecording = false,
                            showFallbackManualSheet = true
                        )
                    }
                    return@launch
                }
                if (audioRecorder == null) {
                    _uiState.update {
                        it.copy(
                            isRecording = false,
                            showFallbackManualSheet = true
                        )
                    }
                    return@launch
                }

                _uiState.update {
                    it.copy(
                        isRecording = true,
                        recordingAmplitude = 0f,
                        userErrorMessage = null
                    )
                }

                audioRecorder.startRecording(
                    onAmplitudeChanged = { amp ->
                        updateAmplitude(amp)
                    },
                    onError = { error ->
                        handleSpeechError(error)
                    }
                )
                return@launch
            }

            val engine = voiceEngine
            if (engine == null || !engine.isAvailable()) {
                _uiState.update {
                    it.copy(
                        isHardwareSpeechAvailable = false,
                        showFallbackManualSheet = true
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isRecording = true,
                    recordingAmplitude = 0f,
                    userErrorMessage = null
                )
            }

            engine.startListening(
                onResult = { text ->
                    _uiState.update { it.copy(isRecording = false, recordingAmplitude = 0f) }
                    if (text.isNotBlank()) {
                        _uiState.update { it.copy(processingStage = ProcessingStage.UNDERSTANDING) }
                        parseManualSpeechText(text)
                        _uiState.update { it.copy(processingStage = ProcessingStage.READY) }
                    } else {
                        _uiState.update { it.copy(processingStage = ProcessingStage.IDLE) }
                        viewModelScope.launch {
                            _effects.emit(ExpenseEffect.ShowToast("لم يتم التقاط أي كلام، حاول مجدداً"))
                        }
                    }
                },
                onAmplitudeChanged = { amp ->
                    updateAmplitude(amp)
                },
                onError = { error ->
                    _uiState.update { it.copy(isRecording = false, recordingAmplitude = 0f) }
                    when (error) {
                        "PERMISSION_DENIED" -> {
                            viewModelScope.launch {
                                _effects.emit(ExpenseEffect.RequestMicPermission)
                                _effects.emit(ExpenseEffect.ShowToast("يرجى منح إذن استخدام الميكروفون"))
                            }
                            _uiState.update { it.copy(showFallbackManualSheet = true) }
                        }
                        "SPEECH_NOT_AVAILABLE" -> {
                            _uiState.update {
                                it.copy(
                                    isHardwareSpeechAvailable = false,
                                    showFallbackManualSheet = true
                                )
                            }
                        }
                        "NETWORK_ERROR" -> {
                            viewModelScope.launch {
                                _effects.emit(ExpenseEffect.ShowToast("يتطلب التعرف الصوتي اتصالاً بالإنترنت أو تنزيل حزمة العربية دون اتصال"))
                            }
                        }
                        "NO_MATCH", "SPEECH_TIMEOUT" -> {
                            viewModelScope.launch {
                                _effects.emit(ExpenseEffect.ShowToast("لم نتمكن من سماع الصوت بوضوح، يرجى المحاولة ثانية"))
                            }
                        }
                        "SPEECH_CANCELLED" -> {
                            // User dismissed or closed the speech dialog, cleanly stay on dashboard
                        }
                        else -> {
                            viewModelScope.launch {
                                _effects.emit(ExpenseEffect.ShowToast("تعذر التعرف على الصوت، يمكنك استخدام الإدخال اليدوي"))
                            }
                            _uiState.update { it.copy(showFallbackManualSheet = true) }
                        }
                    }
                }
            )
        }
    }

    private fun cancelProcessing() {
        processingJob?.cancel()
        processingJob = null
        _uiState.update {
            it.copy(
                isRecording = false,
                recordingAmplitude = 0f,
                processingStage = ProcessingStage.IDLE
            )
        }
    }

    private fun updateWeeklyBudget(budget: Double) {
        preferences.setWeeklyBudget(budget)
        _uiState.update { it.copy(weeklyBudget = budget) }
    }

    private fun stopRecording() {
        if (!_uiState.value.isRecording) return
        _uiState.update { it.copy(isRecording = false, recordingAmplitude = 0f) }

        if (audioRecorder != null) {
            val pcm = audioRecorder.stopRecording()
            if (pcm == null) {
                _uiState.update {
                    it.copy(
                        processingStage = ProcessingStage.IDLE,
                        userErrorMessage = "تعذر استرجاع التسجيل الصوتي"
                    )
                }
                viewModelScope.launch {
                    _effects.emit(ExpenseEffect.ShowToast("تعذر استرجاع التسجيل الصوتي"))
                }
                return
            }

            if (transcriber != null) {
                processingJob?.cancel()
                processingJob = viewModelScope.launch {
                    _uiState.update { it.copy(processingStage = ProcessingStage.FINALIZING_AUDIO) }
                    _uiState.update { it.copy(processingStage = ProcessingStage.TRANSCRIBING) }

                    val result = transcriber.transcribe(
                        pcm,
                        WhisperModelConfig(language = "ar", translate = false)
                    )
                    when (result) {
                        is TranscribeResult.Success -> {
                            if (result.text.isNotBlank()) {
                                _uiState.update { it.copy(processingStage = ProcessingStage.UNDERSTANDING) }
                                val now = Clock.System.now().toEpochMilliseconds()
                                val batch = parser.parseSpokenText(
                                    spokenText = result.text,
                                    defaultCurrency = _uiState.value.activeCurrency,
                                    currentTimestamp = now
                                )
                                _uiState.update {
                                    it.copy(
                                        processingStage = ProcessingStage.READY,
                                        reviewSheetPendingBatch = batch,
                                        showFallbackManualSheet = false
                                    )
                                }
                            } else {
                                handleSpeechError(SpeechError.EmptyResult)
                            }
                        }
                        is TranscribeResult.Failure -> {
                            handleSpeechError(result.error)
                        }
                    }
                }
            }
            return
        }

        if (voiceEngine != null) {
            _uiState.update { it.copy(processingStage = ProcessingStage.FINALIZING_AUDIO) }
            voiceEngine.stopListening()
        }
    }

    private fun handleSpeechError(error: SpeechError) {
        _uiState.update {
            it.copy(
                isRecording = false,
                recordingAmplitude = 0f,
                processingStage = ProcessingStage.IDLE
            )
        }
        when (error) {
            is SpeechError.PermissionDenied -> {
                _uiState.update {
                    it.copy(
                        showPermissionRationaleDialog = true,
                        showPermissionRationaleSheet = true
                    )
                }
                viewModelScope.launch {
                    _effects.emit(ExpenseEffect.RequestMicPermission)
                    _effects.emit(ExpenseEffect.ShowToast("يرجى منح إذن استخدام الميكروفون"))
                }
            }
            is SpeechError.ModelMissing -> {
                _uiState.update { it.copy(showFallbackManualSheet = true) }
            }
            is SpeechError.EmptyResult -> {
                _uiState.update { it.copy(userErrorMessage = "لم يتم التقاط أي كلام، حاول مجدداً") }
                viewModelScope.launch {
                    _effects.emit(ExpenseEffect.ShowToast("لم يتم التقاط أي كلام، حاول مجدداً"))
                }
            }
            is SpeechError.AudioCapture -> {
                _uiState.update { it.copy(userErrorMessage = "تعذر تسجيل الصوت من الميكروفون") }
                viewModelScope.launch {
                    _effects.emit(ExpenseEffect.ShowToast("تعذر تسجيل الصوت من الميكروفون"))
                }
            }
            is SpeechError.ModelLoadFailed, is SpeechError.InitializationFailed -> {
                _uiState.update {
                    it.copy(
                        showFallbackManualSheet = true,
                        userErrorMessage = "تعذر تحميل نموذج التعرف الصوتي"
                    )
                }
                viewModelScope.launch {
                    _effects.emit(ExpenseEffect.ShowToast("تعذر تحميل نموذج التعرف الصوتي"))
                }
            }
            is SpeechError.TranscriptionFailed -> {
                _uiState.update { it.copy(userErrorMessage = "فشلت عملية معالجة الصوت") }
                viewModelScope.launch {
                    _effects.emit(ExpenseEffect.ShowToast("فشلت عملية معالجة الصوت"))
                }
            }
            is SpeechError.OutOfMemory -> {
                _uiState.update { it.copy(userErrorMessage = "ذاكرة الجهاز غير كافية لمعالجة الصوت") }
                viewModelScope.launch {
                    _effects.emit(ExpenseEffect.ShowToast("ذاكرة الجهاز غير كافية لمعالجة الصوت"))
                }
            }
            is SpeechError.Unknown -> {
                val msg = error.message.ifBlank { "حدث خطأ غير متوقع أثناء التعرف على الصوت" }
                _uiState.update { it.copy(userErrorMessage = msg) }
                viewModelScope.launch {
                    _effects.emit(ExpenseEffect.ShowToast(msg))
                }
            }
        }
    }

    private fun updateAmplitude(amplitude: Float) {
        _uiState.update { it.copy(recordingAmplitude = amplitude.coerceIn(0f, 1f)) }
    }

    private fun parseManualSpeechText(rawText: String) {
        if (rawText.isBlank()) return
        val now = Clock.System.now().toEpochMilliseconds()
        val batch = parser.parseSpokenText(
            spokenText = rawText,
            defaultCurrency = _uiState.value.activeCurrency,
            currentTimestamp = now
        )

        _uiState.update {
            it.copy(
                isRecording = false,
                reviewSheetPendingBatch = batch,
                showFallbackManualSheet = false
            )
        }
    }

    private fun parseBankSms(smsBody: String) {
        if (smsBody.isBlank()) return
        val now = Clock.System.now().toEpochMilliseconds()
        val proof = parser.parseBankSms(smsBody, now)

        if (proof != null) {
            val item = ExpenseItem(
                id = "exp_sms_$now",
                title = proof.merchant,
                amount = proof.extractedAmount,
                currency = proof.currency,
                category = ExpenseCategory.GROCERIES, // default fallback
                timestamp = now,
                proofType = ProofType.BANK_SMS,
                proofDetails = proof,
                rawSpokenText = smsBody
            )
            val batch = ExpenseBatch(
                batchId = "batch_sms_$now",
                items = listOf(item),
                totalBatchAmount = item.amount,
                createdAt = now
            )
            _uiState.update { it.copy(reviewSheetPendingBatch = batch) }
        } else {
            viewModelScope.launch {
                _effects.emit(ExpenseEffect.ShowToast("لم نتمكن من قراءة الإشعار البنكي"))
            }
        }
    }

    private fun finishProcessingReady() {
        _uiState.update { it.copy(processingStage = ProcessingStage.IDLE) }
    }

    private fun confirmBatch(batch: ExpenseBatch) {
        viewModelScope.launch {
            repository.saveBatch(batch)
            _uiState.update {
                it.copy(
                    reviewSheetPendingBatch = null,
                    processingStage = ProcessingStage.IDLE
                )
            }
            _effects.emit(ExpenseEffect.TriggerHapticFeedback)
            _effects.emit(ExpenseEffect.ShowToast("تم تسجيل المصروف بنجاح"))
        }
    }

    private fun deleteExpense(expenseId: String) {
        viewModelScope.launch {
            repository.deleteExpense(expenseId)
            _effects.emit(ExpenseEffect.ShowToast("تم حذف المصروف"))
        }
    }

    private fun editExpense(item: ExpenseItem) {
        val batch = ExpenseBatch(
            batchId = "batch_edit_${item.id}",
            items = listOf(item),
            totalBatchAmount = item.amount,
            createdAt = item.timestamp
        )
        _uiState.update { it.copy(reviewSheetPendingBatch = batch) }
    }

    fun exportCsv(): String = repository.exportCsv(_uiState.value.recentExpenses)

    fun exportJson(): String = repository.exportJson(_uiState.value.recentExpenses)

    private fun filterByCategory(category: ExpenseCategory?) {
        _uiState.update { state ->
            val filtered = if (category == null) {
                state.recentExpenses
            } else {
                state.recentExpenses.filter { it.category == category }
            }
            state.copy(
                selectedCategoryFilter = category,
                filteredExpenses = filtered
            )
        }
    }

    private fun openManualSheet() {
        _uiState.update { it.copy(showFallbackManualSheet = true) }
    }

    private fun dismissReviewSheet() {
        _uiState.update {
            it.copy(
                reviewSheetPendingBatch = null,
                processingStage = ProcessingStage.IDLE
            )
        }
    }

    private fun dismissFallbackSheet() {
        _uiState.update { it.copy(showFallbackManualSheet = false) }
    }

    private fun dismissPermissionRationale() {
        _uiState.update {
            it.copy(
                showPermissionRationaleDialog = false,
                showPermissionRationaleSheet = false
            )
        }
    }

    private fun completeOnboarding() {
        preferences.setOnboardingCompleted(true)
        _uiState.update { it.copy(isOnboardingDone = true) }
    }

    public override fun onCleared() {
        super.onCleared()
        voiceEngine?.release()
        transcriber?.release()
        audioRecorder?.release()
    }
}
