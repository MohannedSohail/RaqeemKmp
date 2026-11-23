package org.mohtech.app.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import org.mohtech.app.ai.audio.AudioProcessor
import org.mohtech.app.ai.audio.VoiceActivityDetector
import org.mohtech.app.ai.audio.VadResult
import org.mohtech.app.ai.audio.VoiceEngine
import org.mohtech.app.ai.nlp.LocalDialectExpenseParser
import org.mohtech.app.ai.nlp.TranscriptNormalizer
import org.mohtech.app.ai.whisper.LocalSpeechTranscriber
import org.mohtech.app.ai.whisper.RawAudioRecorder
import org.mohtech.app.ai.whisper.SpeechError
import org.mohtech.app.ai.whisper.TranscribeResult
import org.mohtech.app.ai.whisper.WhisperModelConfig
import org.mohtech.app.core.haptics.HapticKind
import org.mohtech.app.data.accounting.ReceivablesLedger
import org.mohtech.app.data.model.ExpenseBatch
import org.mohtech.app.data.model.ExpenseCategory
import org.mohtech.app.data.model.ExpenseItem
import org.mohtech.app.data.model.ProofType
import org.mohtech.app.data.repository.ExpenseRepository
import org.mohtech.app.data.storage.AppPreferences
import org.mohtech.app.feature.reports.ExportNamer
import org.mohtech.app.feature.reports.PdfReportWriter
import org.mohtech.app.feature.reports.ReportBuilder
import org.mohtech.app.feature.reports.ReportPeriod
import org.mohtech.app.feature.reports.XlsxWriter
import org.mohtech.app.feature.reports.writeExportFileToCache

class ExpenseViewModel(
    private val repository: ExpenseRepository,
    private val preferences: AppPreferences,
    private val parser: LocalDialectExpenseParser = LocalDialectExpenseParser(),
    private val voiceEngine: VoiceEngine? = null,
    private val transcriber: LocalSpeechTranscriber? = null,
    private val audioRecorder: RawAudioRecorder? = null
) : ViewModel() {

    private var processingJob: kotlinx.coroutines.Job? = null
    private var recordingStartTimeMs: Long = 0L
    private var smsSequence: Long = 1000L

    private val _uiState = MutableStateFlow(
        ExpenseUiState(
            activeCurrency = preferences.getPreferredCurrency(),
            isOnboardingDone = preferences.isOnboardingCompleted(),
            weeklyBudget = preferences.getWeeklyBudget(),
            weekStartDay = preferences.getWeekStartDay(),
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
            is ExpenseIntent.SelectTab -> selectTab(intent.tab)
            is ExpenseIntent.CheckPermissionsAndStartRecord -> startRecording()
            is ExpenseIntent.StopRecording -> stopRecording()
            is ExpenseIntent.CancelProcessing -> cancelProcessing()
            is ExpenseIntent.FinishProcessingReady -> finishProcessingReady()
            is ExpenseIntent.UpdateAmplitude -> updateAmplitude(intent.amplitude)
            is ExpenseIntent.ParseManualSpeechText -> parseManualSpeechText(intent.rawText)
            is ExpenseIntent.ParseBankSms -> parseBankSms(intent.smsBody)
            is ExpenseIntent.ConfirmBatch -> confirmBatch(intent.batch)
            is ExpenseIntent.DeleteExpense -> deleteExpense(intent.expenseId)
            is ExpenseIntent.RestoreExpense -> restoreExpense(intent.item)
            is ExpenseIntent.EditExpense -> editExpense(intent.item)
            is ExpenseIntent.FilterByCategory -> filterByCategory(intent.category)
            is ExpenseIntent.SearchExpenses -> searchExpenses(intent.query)
            is ExpenseIntent.UpdateWeeklyBudget -> updateWeeklyBudget(intent.budget)
            is ExpenseIntent.SetWeeklyBudget -> setWeeklyBudget(intent.budget)
            is ExpenseIntent.SetWeekStartDay -> setWeekStartDay(intent.dayIso)
            is ExpenseIntent.SetPreferredCurrency -> setPreferredCurrency(intent.currency)
            is ExpenseIntent.OpenBudgetEditor -> _uiState.update { it.copy(showBudgetEditor = true) }
            is ExpenseIntent.DismissBudgetEditor -> _uiState.update { it.copy(showBudgetEditor = false) }
            is ExpenseIntent.RequestDelete -> requestDelete(intent.item)
            is ExpenseIntent.RequestRepeat -> requestRepeat(intent.item)
            is ExpenseIntent.RepeatExpense -> repeatExpense(intent.item)
            is ExpenseIntent.ChangeCategory -> changeCategory(intent.item, intent.newCategory)
            is ExpenseIntent.ConfirmPendingAction -> confirmPendingAction()
            is ExpenseIntent.DismissPendingAction -> dismissPendingAction()
            is ExpenseIntent.UndoLast -> undoLast()
            is ExpenseIntent.ClearHighlight -> _uiState.update { it.copy(highlightedExpenseId = null) }
            is ExpenseIntent.SelectReportPeriod -> selectReportPeriod(intent.period)
            is ExpenseIntent.ExportExcelReport -> exportExcelReport()
            is ExpenseIntent.ExportPdfReport -> exportPdfReport()
            is ExpenseIntent.ExportReceivablesExcel -> exportReceivablesExcel()
            is ExpenseIntent.DismissExportResult -> dismissExportResult()
            is ExpenseIntent.OpenManualSheet -> openManualSheet()
            is ExpenseIntent.DismissReviewSheet -> dismissReviewSheet()
            is ExpenseIntent.DismissFallbackSheet -> dismissFallbackSheet()
            is ExpenseIntent.DismissPermissionRationale -> dismissPermissionRationale()
            is ExpenseIntent.CompleteOnboarding -> completeOnboarding()
            is ExpenseIntent.OpenReceivables -> _uiState.update { it.copy(showReceivablesScreen = true) }
            is ExpenseIntent.CloseReceivables -> _uiState.update { it.copy(showReceivablesScreen = false) }
            is ExpenseIntent.RecordSettlement -> recordSettlement(
                expenseId = intent.expenseId,
                allocationId = intent.allocationId,
                amount = intent.amount,
                settledAt = intent.settledAt,
                note = intent.note
            )
            is ExpenseIntent.VoidSettlement -> voidSettlement(
                expenseId = intent.expenseId,
                settlementId = intent.settlementId
            )
            is ExpenseIntent.RenameParty -> renameParty(intent.oldName, intent.newName)
            is ExpenseIntent.MergeParties -> mergeParties(intent.sourceName, intent.targetName)
        }
    }

    private fun selectTab(tab: MainTab) {
        _uiState.update { it.copy(currentTab = tab) }
        viewModelScope.launch {
            _effects.emit(ExpenseEffect.Haptic(HapticKind.TICK))
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.initialize()

            repository.expensesFlow.collect { rawExpenses ->
                val expenses = rawExpenses.distinctBy { it.id }
                val todayTotal = repository.getTodayTotal()
                val weeklyTotal = repository.getWeeklyTotal()
                val currentFilter = _uiState.value.selectedCategoryFilter
                val currentSearch = _uiState.value.searchQuery.trim().lowercase()

                val filtered = expenses.filter { item ->
                    val matchCat = currentFilter == null || item.category == currentFilter
                    val matchQuery = currentSearch.isEmpty() ||
                        item.title.lowercase().contains(currentSearch) ||
                        item.payer.name.lowercase().contains(currentSearch)
                    matchCat && matchQuery
                }.distinctBy { it.id }

                val currentPeriod = _uiState.value.selectedReportPeriod
                val report = ReportBuilder.buildReport(
                    expenses = expenses,
                    period = currentPeriod,
                    weekStartDayIso = preferences.getWeekStartDay()
                )

                val activeCur = preferences.getPreferredCurrency()
                val totalReceivables = ReceivablesLedger.getTotalRemaining(expenses, activeCur)

                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        recentExpenses = expenses,
                        filteredExpenses = filtered,
                        todayTotal = todayTotal,
                        weeklyTotal = weeklyTotal,
                        weeklyBudget = preferences.getWeeklyBudget(),
                        weekStartDay = preferences.getWeekStartDay(),
                        activeCurrency = activeCur,
                        isOnboardingDone = preferences.isOnboardingCompleted(),
                        isHardwareSpeechAvailable = voiceEngine?.isAvailable() ?: false,
                        reportData = report,
                        receivablesTotalRemaining = totalReceivables
                    )
                }
            }
        }
    }

    private fun startRecording() {
        viewModelScope.launch {
            if (_uiState.value.isRecording) return@launch
            _effects.emit(ExpenseEffect.TriggerHapticFeedback)
            _effects.emit(ExpenseEffect.Haptic(HapticKind.TICK))

            if (audioRecorder != null || transcriber != null) {
                if (transcriber?.isModelReady() == false) {
                    handleSpeechError(SpeechError.ModelMissing)
                    return@launch
                }
                if (audioRecorder == null) {
                    handleSpeechError(SpeechError.AudioCapture)
                    return@launch
                }

                recordingStartTimeMs = Clock.System.now().toEpochMilliseconds()
                _uiState.update {
                    it.copy(
                        isRecording = true,
                        recordingAmplitude = 0f,
                        userErrorMessage = null,
                        showFallbackManualSheet = false,
                        reviewSheetPendingBatch = null
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
                handleSpeechError(SpeechError.ModelMissing)
                return@launch
            }

            recordingStartTimeMs = Clock.System.now().toEpochMilliseconds()
            _uiState.update {
                it.copy(
                    isRecording = true,
                    recordingAmplitude = 0f,
                    userErrorMessage = null,
                    showFallbackManualSheet = false,
                    reviewSheetPendingBatch = null
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
                        _uiState.update {
                            it.copy(
                                isRecording = false,
                                recordingAmplitude = 0f,
                                processingStage = ProcessingStage.IDLE,
                                showFallbackManualSheet = true,
                                reviewSheetPendingBatch = null,
                                userErrorMessage = "لم يتم التقاط أي كلام، يمكنك إدخال المصروف يدوياً"
                            )
                        }
                        viewModelScope.launch {
                            _effects.emit(ExpenseEffect.ShowToast("لم يتم التقاط أي كلام، حاول مجدداً"))
                        }
                    }
                },
                onAmplitudeChanged = { amp ->
                    updateAmplitude(amp)
                },
                onError = { error ->
                    _uiState.update {
                        it.copy(
                            isRecording = false,
                            recordingAmplitude = 0f,
                            processingStage = ProcessingStage.IDLE,
                            showFallbackManualSheet = true,
                            reviewSheetPendingBatch = null
                        )
                    }
                    when (error) {
                        "PERMISSION_DENIED" -> {
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
                        "NO_MATCH" -> {
                            _uiState.update {
                                it.copy(
                                    userErrorMessage = "لم يتم التقاط أي كلام، يمكنك إدخال المصروف يدوياً"
                                )
                            }
                            viewModelScope.launch {
                                _effects.emit(ExpenseEffect.ShowToast("لم يتم التقاط أي كلام، حاول مجدداً"))
                            }
                        }
                        else -> {
                            _uiState.update {
                                it.copy(
                                    userErrorMessage = "تعذر التعرف على الصوت محلياً"
                                )
                            }
                            viewModelScope.launch {
                                _effects.emit(ExpenseEffect.ShowToast("تعذر التعرف على الصوت محلياً"))
                            }
                        }
                    }
                }
            )
        }
    }

    private fun stopRecording() {
        if (!_uiState.value.isRecording) return

        if (audioRecorder != null || transcriber != null) {
            val recorder = audioRecorder
            var audioCaptureFailed = false
            val pcm: ShortArray? = try {
                recorder?.stopRecording()
            } catch (_: Throwable) {
                audioCaptureFailed = true
                null
            } finally {
                _uiState.update {
                    it.copy(
                        isRecording = false,
                        recordingAmplitude = 0f
                    )
                }
            }

            if (audioCaptureFailed) {
                handleSpeechError(SpeechError.AudioCapture)
                return
            }

            if (pcm == null || pcm.isEmpty()) {
                handleSpeechError(SpeechError.EmptyResult)
                return
            }

            // Apply Voice Activity Detection (VAD) to trim silence and reject empty audio
            val vad = VoiceActivityDetector()
            val (speechPcm, speechDurationMs) = when (val vadResult = vad.process(pcm)) {
                is VadResult.Speech -> vadResult.trimmedPcm to vadResult.speechDurationMs
                is VadResult.NoSpeech -> {
                    handleSpeechError(SpeechError.EmptyResult)
                    return
                }
            }

            val transcriberEngine = transcriber
            if (transcriberEngine == null) {
                _uiState.update {
                    it.copy(
                        processingStage = ProcessingStage.IDLE,
                        showFallbackManualSheet = true,
                        reviewSheetPendingBatch = null,
                        userErrorMessage = "نموذج التفريغ الصوتي غير متوفر"
                    )
                }
                return
            }

            val recordedDurationMs = if (recordingStartTimeMs > 0L) {
                maxOf(0L, Clock.System.now().toEpochMilliseconds() - recordingStartTimeMs)
            } else {
                speechDurationMs
            }
            recordingStartTimeMs = 0L
            val effectiveDurationMs = maxOf(recordedDurationMs, speechDurationMs)
            val dynamicTimeoutMs = (effectiveDurationMs * 2).coerceIn(5_000L, 15_000L)

            processingJob?.cancel()
            processingJob = viewModelScope.launch {
                _uiState.update { it.copy(processingStage = ProcessingStage.FINALIZING_AUDIO) }
                _uiState.update { it.copy(processingStage = ProcessingStage.TRANSCRIBING) }

                try {
                    val result = kotlinx.coroutines.withTimeoutOrNull(dynamicTimeoutMs) {
                        transcriberEngine.transcribe(
                            speechPcm,
                            WhisperModelConfig(language = "ar", translate = false)
                        )
                    }

                    if (result == null) {
                        _uiState.update {
                            it.copy(
                                processingStage = ProcessingStage.IDLE,
                                showFallbackManualSheet = true,
                                reviewSheetPendingBatch = null,
                                userErrorMessage = "استغرقت المعالجة وقتاً أطول من المتوقع، يمكنك إدخال المصروف يدوياً"
                            )
                        }
                        _effects.emit(ExpenseEffect.ShowToast("استغرقت المعالجة وقتاً أطول من المتوقع"))
                        return@launch
                    }

                    when (result) {
                        is TranscribeResult.Success -> {
                            if (result.text.isNotBlank()) {
                                _uiState.update { it.copy(processingStage = ProcessingStage.UNDERSTANDING) }
                                val now = Clock.System.now().toEpochMilliseconds()
                                val normalized = TranscriptNormalizer.normalize(result.text)
                                val initialBatch = parser.parseSpokenText(
                                    spokenText = normalized.normalizedText,
                                    defaultCurrency = _uiState.value.activeCurrency,
                                    currentTimestamp = now
                                )
                                val batch = initialBatch.copy(
                                    items = initialBatch.items.map { it.copy(rawSpokenText = result.text) }
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
                } catch (ce: kotlinx.coroutines.CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    handleSpeechError(SpeechError.Unknown(t.message ?: "Transcription failed"))
                }
            }
            return
        }

        val engine = voiceEngine
        if (engine != null) {
            try {
                engine.stopListening()
            } catch (_: Throwable) {
            } finally {
                _uiState.update { it.copy(isRecording = false) }
            }
            return
        }
    }

    private fun cancelProcessing() {
        processingJob?.cancel()
        processingJob = null
        recordingStartTimeMs = 0L
        try {
            if (audioRecorder?.isRecording() == true) {
                audioRecorder.stopRecording()
            }
        } catch (_: Throwable) {}
        try {
            voiceEngine?.stopListening()
        } catch (_: Throwable) {}
        _uiState.update {
            it.copy(
                processingStage = ProcessingStage.IDLE,
                isRecording = false,
                recordingAmplitude = 0f,
                reviewSheetPendingBatch = null
            )
        }
    }

    private fun handleSpeechError(error: SpeechError) {
        _uiState.update {
            it.copy(
                isRecording = false,
                recordingAmplitude = 0f,
                processingStage = ProcessingStage.IDLE,
                showFallbackManualSheet = true,
                reviewSheetPendingBatch = null
            )
        }
        when (error) {
            is SpeechError.PermissionDenied -> {
                _uiState.update {
                    it.copy(
                        showFallbackManualSheet = true,
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
                _uiState.update {
                    it.copy(
                        showFallbackManualSheet = true,
                        userErrorMessage = "نموذج التعرف الصوتي غير متوفر"
                    )
                }
                viewModelScope.launch {
                    _effects.emit(ExpenseEffect.ShowToast("نموذج التعرف الصوتي غير متوفر"))
                }
            }
            is SpeechError.EmptyResult -> {
                _uiState.update {
                    it.copy(
                        showFallbackManualSheet = true,
                        userErrorMessage = "لم يتم التقاط أي كلام، يمكنك إدخال المصروف يدوياً"
                    )
                }
                viewModelScope.launch {
                    _effects.emit(ExpenseEffect.ShowToast("لم يتم التقاط أي كلام، حاول مجدداً"))
                }
            }
            is SpeechError.AudioCapture -> {
                _uiState.update {
                    it.copy(
                        showFallbackManualSheet = true,
                        userErrorMessage = "تعذر تسجيل الصوت من الميكروفون"
                    )
                }
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
                _uiState.update {
                    it.copy(
                        showFallbackManualSheet = true,
                        userErrorMessage = "فشلت عملية معالجة الصوت"
                    )
                }
                viewModelScope.launch {
                    _effects.emit(ExpenseEffect.ShowToast("فشلت عملية معالجة الصوت"))
                }
            }
            is SpeechError.OutOfMemory -> {
                _uiState.update {
                    it.copy(
                        showFallbackManualSheet = true,
                        userErrorMessage = "ذاكرة الجهاز غير كافية لمعالجة الصوت"
                    )
                }
                viewModelScope.launch {
                    _effects.emit(ExpenseEffect.ShowToast("ذاكرة الجهاز غير كافية لمعالجة الصوت"))
                }
            }
            is SpeechError.Unknown -> {
                val msg = error.message.ifBlank { "حدث خطأ غير متوقع أثناء المعالجة" }
                _uiState.update {
                    it.copy(
                        showFallbackManualSheet = true,
                        userErrorMessage = msg
                    )
                }
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
        val normalized = TranscriptNormalizer.normalize(rawText)
        val initialBatch = parser.parseSpokenText(
            spokenText = normalized.normalizedText,
            defaultCurrency = _uiState.value.activeCurrency,
            currentTimestamp = now
        )
        val batch = initialBatch.copy(
            items = initialBatch.items.map { it.copy(rawSpokenText = rawText) }
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
            val suffix = ++smsSequence
            val item = ExpenseItem(
                id = "exp_sms_${now}_$suffix",
                title = proof.merchant,
                amount = proof.extractedAmount,
                currency = proof.currency,
                category = ExpenseCategory.GROCERIES,
                timestamp = now,
                proofType = ProofType.BANK_SMS,
                proofDetails = proof,
                rawSpokenText = smsBody
            )
            val batch = ExpenseBatch(
                batchId = "batch_sms_${now}_$suffix",
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
            _effects.emit(ExpenseEffect.Haptic(HapticKind.CONFIRM))
            _effects.emit(ExpenseEffect.ShowToast("تم تسجيل المصروف بنجاح"))
        }
    }

    // Direct deletion (tests expect DeleteExpense to remove item directly)
    private fun deleteExpense(expenseId: String) {
        viewModelScope.launch {
            val itemToDelete = _uiState.value.recentExpenses.find { it.id == expenseId }
            repository.deleteExpense(expenseId)
            if (itemToDelete != null) {
                _uiState.update { it.copy(lastUndoOperation = UndoOperation.Deleted(itemToDelete)) }
                _effects.emit(ExpenseEffect.Haptic(HapticKind.HEAVY))
                _effects.emit(ExpenseEffect.ShowUndoSnackbar(itemToDelete))
                _effects.emit(
                    ExpenseEffect.ShowMessage(
                        message = "تم حذف ${itemToDelete.title}",
                        kind = MessageKind.SUCCESS,
                        undo = UndoOperation.Deleted(itemToDelete)
                    )
                )
            } else {
                _effects.emit(ExpenseEffect.ShowToast("تم حذف المصروف"))
            }
        }
    }

    private fun requestDelete(item: ExpenseItem) {
        _uiState.update { it.copy(pendingCardAction = CardAction.Delete(item)) }
        viewModelScope.launch {
            _effects.emit(ExpenseEffect.Haptic(HapticKind.TICK))
        }
    }

    private fun requestRepeat(item: ExpenseItem) {
        _uiState.update { it.copy(pendingCardAction = CardAction.Repeat(item)) }
        viewModelScope.launch {
            _effects.emit(ExpenseEffect.Haptic(HapticKind.TICK))
        }
    }

    private fun confirmPendingAction() {
        val action = _uiState.value.pendingCardAction
        _uiState.update { it.copy(pendingCardAction = null) }
        when (action) {
            is CardAction.Delete -> deleteExpense(action.item.id)
            is CardAction.Repeat -> repeatExpense(action.item)
            null -> {}
        }
    }

    private fun dismissPendingAction() {
        _uiState.update { it.copy(pendingCardAction = null) }
    }

    private fun repeatExpense(item: ExpenseItem) {
        viewModelScope.launch {
            val now = Clock.System.now().toEpochMilliseconds()
            val seq = LocalDialectExpenseParser.nextSequence()
            val newItem = item.copy(
                id = "exp_${now}_$seq",
                timestamp = now
            )
            repository.saveExpense(newItem)
            _uiState.update {
                it.copy(
                    highlightedExpenseId = newItem.id,
                    lastUndoOperation = UndoOperation.Repeated(newItem.id)
                )
            }
            _effects.emit(ExpenseEffect.Haptic(HapticKind.CONFIRM))
            _effects.emit(
                ExpenseEffect.ShowMessage(
                    message = "تم تكرار المصروف",
                    kind = MessageKind.SUCCESS,
                    undo = UndoOperation.Repeated(newItem.id)
                )
            )
        }
    }

    private fun changeCategory(item: ExpenseItem, newCategory: ExpenseCategory) {
        viewModelScope.launch {
            val prevCat = item.category
            val updated = item.copy(category = newCategory)
            repository.updateExpense(updated)
            _uiState.update {
                it.copy(
                    highlightedExpenseId = updated.id,
                    lastUndoOperation = UndoOperation.CategoryChanged(updated, prevCat)
                )
            }
            _effects.emit(ExpenseEffect.Haptic(HapticKind.TICK))
            _effects.emit(
                ExpenseEffect.ShowMessage(
                    message = "تم تغيير التصنيف إلى ${newCategory.arabicTitle}",
                    kind = MessageKind.SUCCESS,
                    undo = UndoOperation.CategoryChanged(updated, prevCat)
                )
            )
        }
    }

    private fun undoLast() {
        viewModelScope.launch {
            val undo = _uiState.value.lastUndoOperation ?: return@launch
            _uiState.update { it.copy(lastUndoOperation = null) }
            _effects.emit(ExpenseEffect.Haptic(HapticKind.CONFIRM))

            when (undo) {
                is UndoOperation.Deleted -> {
                    repository.saveExpense(undo.item)
                    _uiState.update { it.copy(highlightedExpenseId = undo.item.id) }
                    _effects.emit(ExpenseEffect.ShowToast("تم استرجاع المصروف"))
                }
                is UndoOperation.Repeated -> {
                    repository.deleteExpense(undo.newItemId)
                    _effects.emit(ExpenseEffect.ShowToast("تم التراجع عن التكرار"))
                }
                is UndoOperation.CategoryChanged -> {
                    val restored = undo.item.copy(category = undo.previousCategory)
                    repository.updateExpense(restored)
                    _uiState.update { it.copy(highlightedExpenseId = restored.id) }
                    _effects.emit(ExpenseEffect.ShowToast("تم استرجاع التصنيف السابق"))
                }
                is UndoOperation.SettlementRecorded -> {
                    repository.updateExpense(undo.previousExpense)
                    _effects.emit(ExpenseEffect.ShowToast("تم التراجع عن تسجيل السداد"))
                }
                is UndoOperation.SettlementVoided -> {
                    repository.updateExpense(undo.previousExpense)
                    _effects.emit(ExpenseEffect.ShowToast("تم التراجع عن إلغاء السداد"))
                }
            }
        }
    }

    private fun restoreExpense(item: ExpenseItem) {
        viewModelScope.launch {
            repository.saveExpense(item)
            _uiState.update { it.copy(highlightedExpenseId = item.id) }
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
            val q = state.searchQuery.trim().lowercase()
            val filtered = state.recentExpenses.distinctBy { it.id }.filter { item ->
                val matchCat = category == null || item.category == category
                val matchQuery = q.isEmpty() ||
                    item.title.lowercase().contains(q) ||
                    item.payer.name.lowercase().contains(q)
                matchCat && matchQuery
            }.distinctBy { it.id }
            state.copy(
                selectedCategoryFilter = category,
                filteredExpenses = filtered
            )
        }
    }

    private fun searchExpenses(query: String) {
        _uiState.update { state ->
            val q = query.trim().lowercase()
            val filtered = state.recentExpenses.distinctBy { it.id }.filter { item ->
                val matchCat = state.selectedCategoryFilter == null || item.category == state.selectedCategoryFilter
                val matchQuery = q.isEmpty() ||
                    item.title.lowercase().contains(q) ||
                    item.payer.name.lowercase().contains(q)
                matchCat && matchQuery
            }.distinctBy { it.id }
            state.copy(
                searchQuery = query,
                filteredExpenses = filtered
            )
        }
    }

    private fun updateWeeklyBudget(budget: Double) {
        setWeeklyBudget(budget)
    }

    private fun setWeeklyBudget(budget: Double) {
        preferences.setWeeklyBudget(budget)
        _uiState.update { it.copy(weeklyBudget = budget, showBudgetEditor = false) }
        viewModelScope.launch {
            val wt = repository.getWeeklyTotal()
            _uiState.update { it.copy(weeklyTotal = wt) }
            _effects.emit(ExpenseEffect.ShowMessage("تم تحديث الميزانية بنجاح", MessageKind.SUCCESS))
        }
    }

    private fun setWeekStartDay(dayIso: Int) {
        preferences.setWeekStartDay(dayIso)
        _uiState.update { it.copy(weekStartDay = dayIso) }
        viewModelScope.launch {
            val wt = repository.getWeeklyTotal()
            _uiState.update { it.copy(weeklyTotal = wt) }
        }
    }

    private fun setPreferredCurrency(currency: String) {
        preferences.setPreferredCurrency(currency)
        _uiState.update { it.copy(activeCurrency = currency) }
        viewModelScope.launch {
            val tt = repository.getTodayTotal()
            val wt = repository.getWeeklyTotal()
            _uiState.update { it.copy(todayTotal = tt, weeklyTotal = wt) }
        }
    }

    private fun selectReportPeriod(period: ReportPeriod) {
        val report = ReportBuilder.buildReport(
            expenses = _uiState.value.recentExpenses,
            period = period,
            weekStartDayIso = _uiState.value.weekStartDay
        )
        _uiState.update { it.copy(selectedReportPeriod = period, reportData = report) }
    }

    private fun exportExcelReport() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExportingReport = true, exportErrorMessage = null) }
            try {
                val report = _uiState.value.reportData ?: ReportBuilder.buildReport(
                    _uiState.value.recentExpenses,
                    _uiState.value.selectedReportPeriod,
                    _uiState.value.weekStartDay
                )
                val bytes = XlsxWriter.write(report)
                val scope = ExportNamer.scopeForPeriod(_uiState.value.selectedReportPeriod)
                val fileName = ExportNamer.generateFileName(
                    type = "report",
                    scope = scope,
                    extension = "xlsx"
                )
                val path = writeExportFileToCache(fileName, bytes)
                _uiState.update {
                    it.copy(
                        isExportingReport = false,
                        exportSuccessPath = path,
                        exportSuccessName = fileName,
                        exportSuccessSize = bytes.size.toLong(),
                        exportMimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    )
                }
                _effects.emit(ExpenseEffect.Haptic(HapticKind.CONFIRM))
                _effects.emit(ExpenseEffect.ShowMessage("تم تجهيز ملف Excel بنجاح", MessageKind.SUCCESS))
            } catch (_: Throwable) {
                _uiState.update {
                    it.copy(
                        isExportingReport = false,
                        exportErrorMessage = "تعذر إنشاء ملف Excel، يرجى المحاولة لاحقاً"
                    )
                }
                _effects.emit(ExpenseEffect.ShowMessage("تعذر إنشاء ملف Excel", MessageKind.ERROR))
            }
        }
    }

    private fun exportPdfReport() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExportingReport = true, exportErrorMessage = null) }
            try {
                val report = _uiState.value.reportData ?: ReportBuilder.buildReport(
                    _uiState.value.recentExpenses,
                    _uiState.value.selectedReportPeriod,
                    _uiState.value.weekStartDay
                )
                val writer = PdfReportWriter()
                val bytes = writer.generatePdf(report)
                if (bytes != null && bytes.isNotEmpty()) {
                    val scope = ExportNamer.scopeForPeriod(_uiState.value.selectedReportPeriod)
                    val fileName = ExportNamer.generateFileName(
                        type = "report",
                        scope = scope,
                        extension = "pdf"
                    )
                    val path = writeExportFileToCache(fileName, bytes)
                    _uiState.update {
                        it.copy(
                            isExportingReport = false,
                            exportSuccessPath = path,
                            exportSuccessName = fileName,
                            exportSuccessSize = bytes.size.toLong(),
                            exportMimeType = "application/pdf"
                        )
                    }
                    _effects.emit(ExpenseEffect.Haptic(HapticKind.CONFIRM))
                    _effects.emit(ExpenseEffect.ShowMessage("تم تجهيز ملف PDF بنجاح", MessageKind.SUCCESS))
                } else {
                    _uiState.update {
                        it.copy(
                            isExportingReport = false,
                            exportErrorMessage = "تصدير PDF غير مدعوم على هذا النظام"
                        )
                    }
                    _effects.emit(ExpenseEffect.ShowMessage("تصدير PDF غير متاح حالياً", MessageKind.ERROR))
                }
            } catch (_: Throwable) {
                _uiState.update {
                    it.copy(
                        isExportingReport = false,
                        exportErrorMessage = "تعذر إنشاء ملف PDF، يرجى المحاولة لاحقاً"
                    )
                }
                _effects.emit(ExpenseEffect.ShowMessage("تعذر إنشاء ملف PDF", MessageKind.ERROR))
            }
        }
    }

    private fun exportReceivablesExcel() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExportingReport = true, exportErrorMessage = null) }
            try {
                val bytes = XlsxWriter.writeReceivablesWorkbook(_uiState.value.recentExpenses)
                val fileName = ExportNamer.generateFileName(
                    type = "receivables",
                    scope = "ledger",
                    extension = "xlsx"
                )
                val path = writeExportFileToCache(fileName, bytes)
                _uiState.update {
                    it.copy(
                        isExportingReport = false,
                        exportSuccessPath = path,
                        exportSuccessName = fileName,
                        exportSuccessSize = bytes.size.toLong(),
                        exportMimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    )
                }
                _effects.emit(ExpenseEffect.Haptic(HapticKind.CONFIRM))
                _effects.emit(ExpenseEffect.ShowMessage("تم تجهيز دفتر المستحقات بصيغة Excel بنجاح", MessageKind.SUCCESS))
            } catch (_: Throwable) {
                _uiState.update {
                    it.copy(
                        isExportingReport = false,
                        exportErrorMessage = "تعذر إنشاء دفتر المستحقات"
                    )
                }
                _effects.emit(ExpenseEffect.ShowMessage("تعذر تصدير دفتر المستحقات", MessageKind.ERROR))
            }
        }
    }

    private fun dismissExportResult() {
        _uiState.update {
            it.copy(
                exportSuccessPath = null,
                exportSuccessName = null,
                exportSuccessSize = 0L,
                exportMimeType = null,
                exportErrorMessage = null
            )
        }
    }

    private fun openManualSheet() {
        _uiState.update {
            it.copy(
                showFallbackManualSheet = true,
                reviewSheetPendingBatch = null
            )
        }
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

    private fun recordSettlement(
        expenseId: String,
        allocationId: String,
        amount: Double,
        settledAt: Long,
        note: String
    ) {
        viewModelScope.launch {
            try {
                val expense = repository.getAllExpenses().firstOrNull { it.id == expenseId } ?: return@launch
                val updatedExpense = ReceivablesLedger.recordSettlement(
                    expense = expense,
                    allocationId = allocationId,
                    amount = amount,
                    settledAt = settledAt,
                    note = note
                )
                val newSettlementId = updatedExpense.split?.allocations
                    ?.firstOrNull { it.id == allocationId }
                    ?.settlements?.lastOrNull()?.id ?: ""

                repository.updateExpense(updatedExpense)
                val undoOp = UndoOperation.SettlementRecorded(
                    expenseId = expenseId,
                    allocationId = allocationId,
                    settlementId = newSettlementId,
                    previousExpense = expense
                )
                _uiState.update { it.copy(lastUndoOperation = undoOp) }
                _effects.emit(ExpenseEffect.Haptic(HapticKind.CONFIRM))
                _effects.emit(
                    ExpenseEffect.ShowMessage(
                        message = "تم تسجيل السداد بنجاح",
                        kind = MessageKind.SUCCESS,
                        undo = undoOp
                    )
                )
            } catch (t: Throwable) {
                _effects.emit(ExpenseEffect.Haptic(HapticKind.REJECT))
                _effects.emit(ExpenseEffect.ShowMessage(t.message ?: "تعذر تسجيل السداد", MessageKind.ERROR))
            }
        }
    }

    private fun voidSettlement(
        expenseId: String,
        settlementId: String
    ) {
        viewModelScope.launch {
            try {
                val expense = repository.getAllExpenses().firstOrNull { it.id == expenseId } ?: return@launch
                val updatedExpense = ReceivablesLedger.voidSettlement(
                    expense = expense,
                    settlementId = settlementId
                )
                repository.updateExpense(updatedExpense)
                val undoOp = UndoOperation.SettlementVoided(
                    expenseId = expenseId,
                    settlementId = settlementId,
                    previousExpense = expense
                )
                _uiState.update { it.copy(lastUndoOperation = undoOp) }
                _effects.emit(ExpenseEffect.Haptic(HapticKind.CONFIRM))
                _effects.emit(
                    ExpenseEffect.ShowMessage(
                        message = "تم إلغاء السداد",
                        kind = MessageKind.INFO,
                        undo = undoOp
                    )
                )
            } catch (t: Throwable) {
                _effects.emit(ExpenseEffect.Haptic(HapticKind.REJECT))
                _effects.emit(ExpenseEffect.ShowMessage(t.message ?: "تعذر إلغاء السداد", MessageKind.ERROR))
            }
        }
    }

    private fun renameParty(oldName: String, newName: String) {
        viewModelScope.launch {
            try {
                val current = repository.getAllExpenses()
                val updated = ReceivablesLedger.renameParty(current, oldName, newName)
                updated.forEach { repository.updateExpense(it) }
                _effects.emit(ExpenseEffect.Haptic(HapticKind.CONFIRM))
                _effects.emit(ExpenseEffect.ShowMessage("تم تعديل الاسم بنجاح", MessageKind.SUCCESS))
            } catch (t: Throwable) {
                _effects.emit(ExpenseEffect.Haptic(HapticKind.REJECT))
                _effects.emit(ExpenseEffect.ShowMessage(t.message ?: "تعذر تعديل الاسم", MessageKind.ERROR))
            }
        }
    }

    private fun mergeParties(sourceName: String, targetName: String) {
        viewModelScope.launch {
            try {
                val current = repository.getAllExpenses()
                val updated = ReceivablesLedger.mergeParties(current, sourceName, targetName)
                updated.forEach { repository.updateExpense(it) }
                _effects.emit(ExpenseEffect.Haptic(HapticKind.CONFIRM))
                _effects.emit(ExpenseEffect.ShowMessage("تم دمج الأسماء وتوحيد الأرصدة بنجاح", MessageKind.SUCCESS))
            } catch (t: Throwable) {
                _effects.emit(ExpenseEffect.Haptic(HapticKind.REJECT))
                _effects.emit(ExpenseEffect.ShowMessage(t.message ?: "تعذر دمج الأسماء", MessageKind.ERROR))
            }
        }
    }

    private fun completeOnboarding() {
        preferences.setOnboardingCompleted(true)
        _uiState.update { it.copy(isOnboardingDone = true) }
    }

    public override fun onCleared() {
        super.onCleared()
        processingJob?.cancel()
        processingJob = null
        recordingStartTimeMs = 0L
        try {
            if (audioRecorder?.isRecording() == true) {
                audioRecorder.stopRecording()
            }
        } catch (_: Throwable) {
        } finally {
            try {
                audioRecorder?.release()
            } catch (_: Throwable) {}
        }
        try {
            voiceEngine?.release()
        } catch (_: Throwable) {}
        try {
            transcriber?.release()
        } catch (_: Throwable) {}
    }
}
