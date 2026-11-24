# 📐 Raqeem (رقيم) - Technical & Domain Specification (spec.md)

## 📌 الإصدار 2.1: هندسة Whisper المحلي (100% On-Device Whisper Engine)

تحدد هذه الوثيقة العقود البرمجية، ومعمارية خط معالجة الصوت، وتكامل مكتبة whisper.cpp، وعقود MVI، وسيناريوهات القبول بصيغة Gherkin، وجدول تتبع المشاكل من P1 إلى P11.

---

## 📋 1. جدول تتبع المشاكل الفنية (Defect Matrix P1 - P13)

| المعرّف | وصف المشكلة | الملفات المعنية | الحل المعماري الملزم | الحالة |
|:---|:---|:---|:---|:---|
| **P1** | الصوت يعتمد على Google والإنترنت بدل Whisper المحلي | `AudioRecorder.android.kt`، `PlatformSpeechLauncher.*`، `MainActivity.kt`، `AndroidVoiceEngine.kt` | الاستبدال الكامل بـ `AudioRecord` الخام وتفريغ الصوت في `LocalSpeechTranscriber` (whisper.cpp) | مكتمل ومثبت برمجياً ✅ |
| **P2** | `LocalWhisperEngine` محاكاة بـ `delay()` ولا يوجد نموذج GGML حقيقي | `shared/src/commonMain/.../ai/whisper/*` | بناء محرك Whisper حقيقي يستدعي JNI لفك ترميز نماذج GGML الفعلية عبر `libwhisper.so` وتضمين `ggml-tiny-q5_1.bin` | مكتمل ومثبت برمجياً ✅ |
| **P3** | تحليل أخطاء هش بـ `contains("5")` واستخدام Reflection | `ExpenseViewModel.kt`، `PlatformSpeechLauncher.android.kt` | إزالة الـ Reflection نهائياً، واستخدام `sealed interface SpeechError` محددة النوع | مكتمل ومثبت برمجياً ✅ |
| **P4** | وجود صلاحية `INTERNET` واستعلامات `<queries>` لخدمات Google | `androidApp/src/main/AndroidManifest.xml` | حذف `INTERNET` و`ACCESS_NETWORK_STATE` و`<queries>` الخاصة بـ Google Speech تماماً | مكتمل ومثبت برمجياً ✅ |
| **P5** | نافذة `LocalWhisperSettingsDialog` شكلية تعرض خيارات غير مثبتة | `LocalWhisperSettingsDialog.kt` | ربط النافذة بـ `availableModels()` لعرض النماذج الموجودة فعلياً على القرص وحجمها | مكتمل ومثبت برمجياً ✅ |
| **P6** | مسجلات الصوت في iOS وWasm وJVM تعيد نصوصاً وهمية (Stubs) | `shared/src@ios\|wasmJs\|jvm/audio/*` | إعادة `SpeechError.ModelMissing` أو خطأ صريح بعدم الدعم بدلاً من أي نصوص تمثيلية | مكتمل ومثبت برمجياً ✅ |
| **P7** | قيم ابتدائية مكتوبة يدوياً في الواجهة (`dailyTotal = 135.0`) | `ExpenseViewModel.kt` | جعل القيمة الابتدائية صفرية `0.0` وحساب كافة المجاميع من `ExpenseRepository` فقط | مكتمل ومثبت برمجياً ✅ |
| **P8** | ميزانية الأسبوع ثابتة بـ 2500 دون إمكانية ضبط أو ربط بالرادار | `ExpenseViewModel.kt`، `AppPreferences`، `BudgetRadarCard.kt` | ربط ميزانية الأسبوع في `AppPreferences` بحالة `ExpenseUiState` وحساب نسبة الاستهلاك الفعلي في رادار الميزانية | مكتمل ومثبت برمجياً ✅ |
| **P9** | إذن الميكروفون يُطلب تلقائياً عند الإقلاع دون شرح | `MainActivity.kt`، `DashboardScreen.kt` | إزالة الطلب التلقائي من onCreate، وطلب الإذن عند التفاعل مع شرح وافٍ | مكتمل ومثبت برمجياً ✅ |
| **P10** | غياب تعديل المصروف القائم، والتصدير، والنسخ الاحتياطي | `ExpenseRepository.kt`، `PlatformExpenseStorage.kt` | إضافة `updateExpense`، وتصدير CSV/JSON، والنسخ الاحتياطي المحلي | مكتمل ومثبت برمجياً ✅ |
| **P11** | وثائق غير دقيقة (ذكر Gemini، وادعاء 100% إنجاز قبل اكتماله) | `README.md`، `review.md` | تطهير التوثيق من أي ذكر لـ Gemini أو Google، وتوثيق الواقع الفعلي بالأدلة الملموسة | مكتمل ومثبت برمجياً ✅ |
| **P12** | إعادة تشغيل التطبيق وعودته للـ Splash عند تدوير الجوال | `App.kt`، `AppNavigationState.kt`، `AndroidManifest.xml` | حفظ الوجهة بـ Saver، والاحتفاظ بـ ViewModel عبر `viewModel()`، وإضافة `configChanges` | مكتمل ومثبت برمجياً ✅ |
| **P13** | غياب شاشة معالجة مرئية بين التسجيل وظهور شيت المراجعة | `VoiceProcessingOverlay.kt`، `ExpenseViewModel.kt` | تطبيق حالة `ProcessingStage` التدرجية وعارض موجات وشيمر هيكلي وزر إلغاء فوري | مكتمل ومثبت برمجياً ✅ |
| **P14** | ضعف دقة تفريغ الصوت والهلوسة على الصمت وأخطاء الأعداد المركبة | `VoiceActivityDetector.kt`، `TranscriptNormalizer.kt`، `ArabicNumberNormalizer.kt` | VAD محلي بـ Kotlin لقص الصمت ورفض التسجيل الفارغ، ومصحح صوتي سياقي، ودعم ضرب الآلاف والكسور المركبة | تم واختُبر آلياً ✅ (بانتظار تقييم ميداني) |
| **P15** | تكديس الشاشة الرئيسية (شاشة واحدة طويلة) وعدم وجود هيكل تبويبات | `MainShell.kt`، `tabs/*`، `BackHandler.*` | هيكل تبويبات 4 بـ `MainTab` مع حفظ الحالة عبر التدوير، وشريط تنقل سفلي وNavigationRail للأفقي | تم واختُبر آلياً ✅ (فحص الواجهة يدوي) |
| **P16** | غياب محرر الميزانية وتثبيتها بـ 2500 وظهور عبارات تسويقية | `BudgetEditorDialog.kt`، `CalendarWeekUtil.kt`، `AppPreferences.kt` | محرر ميزانية تفاعلي، بداية أسبوع قابلة للتعديل، وإزالة كاملة لعبارة «محلي 100%» من الواجهة | تم واختُبر آلياً ✅ |
| **P17** | أزرار التصدير تكتفي بالنسخ للحافظة دون إنتاج ملفات حقيقية | `XlsxWriter.kt`، `PdfReportWriter.android.kt`، `PlatformReportBridge.*` | كاتب XLSX نقي بـ Kotlin بدون تبعيات، ومولد PDF بـ Android Canvas، ومشاركة FileProvider وحفظ SAF | تم واختُبر آلياً ✅ (الـ PDF بانتظار فحص الخط يدوي) |
| **P18** | احتساب ديون وحصص الآخرين ضمن نفقات وميزانية المستخدم الشخصية | `ExpenseModels.kt`، `ReportModels.kt`، `ExpenseRepository.kt` | اعتماد `effectiveAmount = myShare ?: amount` لجميع المجاميع، وعزل حصص الشركاء كذمم دائنة | تم واختُبر آلياً ✅ |
| **P19** | مخاطر فقدان البيانات عند تلف JSON أو انقطاع الطاقة المفاجئ | `PlatformExpenseStorage.kt` | تخزين ذري يكتب أولاً إلى `.tmp` ثم يستبدل ذرياً، ونقل الملف التالف إلى `.corrupt` مع ختم زمني | تم واختُبر آلياً ✅ |
| **P20** | شيت المراجعة محدودة بمصروف أحادي وتفتقر لتخصيص الحصص وإرفاق الإيصال | `ExpenseReviewSheet.kt`، `PlatformReceiptPicker.*` | دعم الدفعات المتعددة، بطاقات المراجعة المستقلة، تخصيص الحصص، وإرفاق صور الإيصالات أوفلاين | تم واختُبر آلياً ✅ (واجهة المستخدم فحص يدوي) |
| **P21** | غياب سجل ذمم ومستحقات الشركاء والتسديدات الجزئية والكلية | `ReceivablesLedger.kt`، `ReceivablesScreen.kt` | دفتر أستاذ موحد (`ReceivablesLedger`)، فحص مطابقة رياضي، تسديدات ملغاة بـ `voided`، وحذف ناعم | تم واختُبر آلياً ✅ |
| **P22** | غياب كشوفات الحساب وأعمار الديون وتسمية ملفات التصدير بصيغة موحدة | `ReportModels.kt`، `ExportNamer.kt`، `XlsxWriter.kt` | كشوفات حساب مع رصيد تراكمي مستمر، تقارير أعمار الديون (Aging)، وتسمية مانعة للتصادم | تم واختُبر آلياً ✅ |

---

## 🎙️ 2. هندسة خط معالجة الصوت (On-Device Audio Pipeline)

### 2.1 المخطط الانسيابي للمعالجة
```text
[المستخدم يضغط الميكروفون]
          │
          ▼
   AudioRecord (Android)
   16kHz Mono 16-bit PCM
          │
          ├────────────────────────────────────────┐
          ▼                                        ▼
   حساب السعة الآنية RMS                    مخزن PCM في الذاكرة
   (Amplitude: 0.0f..1.0f)                 (ShortArray in-memory buffer)
          │                                        │
          ▼                                        ▼
   تحديث حلقة الموجة في الواجهة              إيقاف التسجيل (User Stop / Auto Silence)
                                                   │
                                                   ▼
                                       تقسيم المقاطع (> 30 ثانية)
                                                   │
                                                   ▼
                                        Dispatchers.Default (خلفية)
                                                   │
                                                   ▼
                                        whisper.cpp عبر JNI
                                        (GGML Tiny - ggml-tiny.bin ~75MB)
                                        initial_prompt: مفردات مالية
                                                   │
                                                   ▼
                                          نص عربي خالص (Arabic Text)
                                                   │
                                                   ▼
                                       LocalDialectExpenseParser
                                                   │
                                                   ▼
                                         بطاقة مراجعة المصروف
                                         (ExpenseReviewSheet)
```

### 2.2 مواصفات الصوت والالتقاط
* **معدل العينات:** 16,000 Hz (16kHz).
* **القنوات:** Mono (قناة واحدة).
* **تنسيق العينات:** 16-bit Linear PCM (كل عينة `Short`).
* **حساب السعة (Amplitude):**
  $$RMS = \sqrt{\frac{1}{N} \sum_{i=1}^{N} x_i^2}$$
  يتم تطبيع القيمة إلى نطاق `0.0f .. 1.0f` وتمريرها في تدفق نبضات الواجهة.
* **الأمان وإدارة العتاد:**
  - يتم تحرير كائن `AudioRecord` بالكامل واستدعاء `stop()` و`release()` داخل كتلة `finally`.
  - لا يُكتب الصوت إلى القرص إلا في حال تجاوز سعة الذاكرة، ويُحذف الملف المؤقت فور انتهاء المعالجة.

### 2.3 إعدادات Whisper واستخراج النصوص
* **اللغة:** `language = "ar"` (العربية).
* **الترجمة:** `translate = false` (لا ترجمة للإنجليزية، الحفاظ على العربية الأصلية).
* **استراتيجية فك الترميز:** Greedy decoding لتوفير استهلاك المعالج والبطارية.
* **عدد الخيوط (Threads):** ديناميكي حسب أنوية الجهاز، بحد أقصى 4 خيوط على الهاتف.
* **الموجِّه الأولي (Initial Prompt):**
  قائمة كلمات مالية مركزة لتوجيه فك الترميز الصوتي دون فرض جمل جاهزة:
  > `"شيكل، ريال، جنيه، بنزين، قهوة، مطعم، سوبرماركت، خضار، لحمة، حاسبت، دفعت، اشتريت، فطور، غداء، عشاء، تكسي، فاتورة، صيدلية، دواء."`

---

## ⚙️ 3. قرار ربط whisper.cpp مع نظام البناء (JNI Binding Decision)

### 3.1 تقييم الخيارات المتاحة
1. **الخيار الأول (المعتمد): بناء CMake عبر externalNativeBuild مع Git Submodule:**
   - تضمين مستودع `whisper.cpp` كموديول فرعي Git مثبت على الوسم المستقر `v1.7.4` (commit `8a9ad784`).
   - بناء مكتبة `libwhisper.so` لمعمارية `arm64-v8a` مباشرة من المصدر عبر `CMakeLists.txt` داخل وحدة `androidApp`.
   - كتابة كود JNI داخل `androidApp/src/main/cpp/jni.cpp` وربطه بدوال `AndroidLocalSpeechTranscriber`.
   - **الميزة:** بناء آلي قابل للتكرار 100% (Reproducible Builds) ومدمج مع ProGuard/R8، مع تصغير حجم الحزمة النهائي إلى ~35.3 MB.
2. **الخيار الثاني: ملفات .so جاهزة داخل jniLibs:**
   - تم استبعاده لأنه غير قابل للتكرار ويصعب تتبع التغييرات أو تحسين الرموز عبر LTO.
3. **الخيار الثالث: استخدام مكتبة Maven جاهزة:**
   - تم استبعاده نظراً لخطر وجود تبعيات شبكية أو تأخر التحديثات ومخالفة معايير التطهير.

### 3.2 القرار النهائي
**اعتماد الخيار الأول (CMake + Git Submodule):** بناء `libwhisper.so` من المصدر عبر `externalNativeBuild` في `androidApp/build.gradle.kts`، مع نموذج مكمم `ggml-tiny-q5_1.bin` (~31MB) مدمج بالأصول، وتوفير واجهات `expect/actual` نقية في `shared/src/commonMain/`.

---

## 🔌 4. عقود واجهات KMP (Pure Kotlin Interfaces)

```kotlin
// shared/src/commonMain/kotlin/org/mohtech/app/ai/whisper/WhisperContracts.kt

package org.mohtech.app.ai.whisper

/**
 * معلومات النموذج المثبت محلياً على الجهاز
 */
data class InstalledModel(
    val id: String,
    val name: String,
    val fileName: String,
    val sizeBytes: Long,
    val isInstalled: Boolean,
    val isDefault: Boolean
)

/**
 * إعدادات جلسة تحويل Whisper
 */
data class WhisperModelConfig(
    val modelFileName: String,
    val language: String = "ar",
    val initialPrompt: String = "شيكل، ريال، جنيه، بنزين، قهوة، سوبرماركت، مطعم، دفعت، حاسبت، اشتريت",
    val numThreads: Int = 4
)

/**
 * أنواع الأخطاء المحددة والآمنة لتحويل الصوت
 */
sealed interface SpeechError {
    data object ModelMissing : SpeechError
    data object ModelLoadFailed : SpeechError
    data object PermissionDenied : SpeechError
    data object AudioCapture : SpeechError
    data object EmptyResult : SpeechError
    data object OutOfMemory : SpeechError
    data class Unknown(val message: String) : SpeechError
}

/**
 * نتيجة عملية التحويل الصوتي
 */
sealed interface TranscribeResult {
    data class Success(val text: String, val durationMs: Long) : TranscribeResult
    data class Failure(val error: SpeechError) : TranscribeResult
}

/**
 * العقد المشترك لمحرك Whisper المحلي
 */
interface LocalSpeechTranscriber {
    suspend fun transcribe(pcm16kMono: ShortArray, config: WhisperModelConfig): TranscribeResult
    fun isModelReady(): Boolean
    fun getInstalledModels(): List<InstalledModel>
    fun release()
}

/**
 * مسجل الصوت الخام الذي يعيد عينات PCM
 */
interface RawAudioRecorder {
    fun startRecording(onAmplitudeChanged: (Float) -> Unit, onError: (SpeechError) -> Unit)
    fun stopRecording(): ShortArray?
    fun isRecording(): Boolean
    fun release()
}
```

---

## 💬 5. رسائل المستخدم (Arabic UX Copy)

| نوع الحدث / الخطأ | نص الرسالة المعروضة للمستخدم | الإجراء المتاح |
|:---|:---|:---|
| **`ModelMissing`** | «نموذج Whisper غير موجود على الجهاز. راجع الإعدادات لتثبيته. يمكنك الآن استخدام الإدخال الكتابي.» | زر فتح الإدخال الكتابي + زر الإعدادات |
| **`PermissionDenied`** | «يحتاج رقيم إذن الميكروفون لتسجيل مصروفك بصوتك دون إرساله لأي خادم.» | زر «منح الإذن» وزر «فتح إعدادات الهاتف» |
| **`EmptyResult`** | «لم أسمع كلاماً واضحاً، جرّب مرة أخرى أو اكتب المصروف.» | زر إعادة التسجيل أو فتح لوحة المفاتيح |
| **`ModelLoadFailed`** | «تعذر تحميل نموذج المعالجة الصوتية، يرجى إعادة تشغيل التطبيق أو اختيار نموذج آخر من الإعدادات.» | زر التوجه للإعدادات |
| **`AudioCapture`** | «تعذر تشغيل لاقط الصوت، تأكد من عدم استخدام تطبيق آخر للميكروفون.» | زر إعادة المحاولة |

> **قاعدة صارمة:** يُمنع منعاً باتاً ذكر «Google» أو «الإنترنت» أو «الاتصال بالشبكة» في أي رسالة، لأن التطبيق أوفلاين بالكامل.

---

## 🧪 6. سيناريوهات القبول (Gherkin Acceptance Criteria)

### سيناريو 1: تسجيل مصروف بالصوت في وضع الطيران مع نموذج Tiny (ggml-tiny.bin)
```gherkin
Feature: 100% Offline Voice Expense Tracking
  Scenario: Commuter records expense in Airplane Mode with zero network
    Given the device is in Airplane Mode with Wi-Fi and Mobile Data OFF
    And the ggml-tiny.bin model is present in filesDir
    And microphone permission is granted
    When the user taps the mic button
    And speaks "بنزين بخمسين شيكل كاش"
    And stops the recording
    Then AudioRecord captures the 16kHz PCM stream
    And whisper.cpp decodes the PCM stream on Dispatchers.Default
    And the transcribed text "بنزين بخمسين شيكل كاش" is delivered
    And LocalDialectExpenseParser extracts:
      | Title    | بنزين                  |
      | Amount   | 50.0                   |
      | Currency | ₪                      |
      | Category | TRANSPORT (مواصلات)   |
    And the ExpenseReviewSheet is displayed with the extracted data
    And zero Google dialogs or network requests are triggered
```

### سيناريو 2: الضغط على الميكروفون مع غياب نموذج Whisper
```gherkin
Feature: Missing Model Handling
  Scenario: User taps mic before installing the GGML model
    Given no GGML model file exists in filesDir or assets
    When the user taps the mic button
    Then the app does NOT crash
    And the message "نموذج Whisper غير موجود على الجهاز. راجع الإعدادات لتثبيته. يمكنك الآن استخدام الإدخال الكتابي." is shown
    And the manual text input sheet is opened automatically
```

### سيناريو 3: رفض إذن الميكروفون
```gherkin
Feature: Graceful Mic Permission Handling
  Scenario: User denies microphone permission permanently
    Given the RECORD_AUDIO permission is denied by the user
    When the user taps the mic button
    Then an explanation sheet explains why the mic is needed locally
    And a button "فتح إعدادات الهاتف" is provided to navigate to App Settings
    And no crash occurs
```

### سيناريو 4: تسجيل صوتي ممتد أطول من 30 ثانية
```gherkin
Feature: Long Audio Chunking
  Scenario: User records a long list of groceries exceeding 30 seconds
    Given the user records audio for 35 seconds
    When recording is stopped
    Then the PCM buffer is chunked into 30-second sliding segments
    And each segment is transcribed through whisper.cpp
    And the resulting Arabic texts are stitched cleanly
    And the combined expense batch is rendered in ExpenseReviewSheet without OOM
```

### سيناريو 5: تصفير المجاميع الابتدائية وتعديل المصروف
```gherkin
Feature: Data Integrity and Expense Update
  Scenario: Cold start initial totals and editing an existing expense
    Given the user opens the app on a fresh day
    Then dailyTotal starts at 0.0 unless read from stored expenses
    When the user edits an existing expense title or amount
    Then updateExpense persists the changes atomically
    And the history list and radar card update immediately
```

---

## 🏗️ 5. العقود المعمارية للإصدار المتقدم (V2.2 Contracts)

### 5.1 كشف النشاط الصوتي (VoiceActivityDetector)
```kotlin
class VoiceActivityDetector(
    private val sampleRate: Int = 16000,
    private val frameSizeMs: Int = 25,
    private val minVoicedDurationMs: Int = 300,
    private val paddingDurationMs: Int = 250,
    private val silenceHangoverMs: Int = 300
) {
    fun process(pcmData: ShortArray): VadResult
}

sealed interface VadResult {
    data class Speech(val trimmedPcm: ShortArray, val speechDurationMs: Long) : VadResult
    data object NoSpeech : VadResult
}
```

### 5.2 المصحح اللاحق للتفريغ الصوتي (TranscriptNormalizer)
```kotlin
class TranscriptNormalizer {
    fun normalize(text: String): NormalizationResult
}

data class NormalizationResult(
    val normalizedText: String,
    val replacements: List<ReplacementRecord> = emptyList()
)
```

### 5.3 هيكل التبويبات (MainTab)
```kotlin
enum class MainTab {
    HOME,
    HISTORY,
    REPORTS,
    SETTINGS
}
```

### 5.4 تفاعلات البطاقات والتراجع (CardAction & UndoOperation)
```kotlin
sealed interface CardAction {
    data class Delete(val item: ExpenseItem) : CardAction
    data class Repeat(val item: ExpenseItem) : CardAction
}

sealed interface UndoOperation {
    data class Deleted(val item: ExpenseItem) : UndoOperation
    data class Repeated(val newItemId: String) : UndoOperation
    data class CategoryChanged(val item: ExpenseItem, val previousCategory: ExpenseCategory) : UndoOperation
}
```

### 5.5 تصدير التقارير (ReportData, XlsxWriter, PdfReportWriter)
```kotlin
data class ReportData(
    val period: ReportPeriod,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val rows: List<ReportRow>,
    val currencyTotals: Map<String, Double>,
    val categoryBreakdowns: Map<String, List<CategorySummary>>,
    val dailyTotals: Map<LocalDate, Double>
)

class XlsxWriter {
    fun writeReport(report: ReportData): ByteArray
}

expect class PdfReportWriter {
    val isSupported: Boolean
    suspend fun generatePdf(report: ReportData): ByteArray
}

expect fun writeExportFileToCache(fileName: String, bytes: ByteArray): String

### 5.6 عقود دفتر المستحقات والمحاسبة المالية (Accounting & Receivables Contracts)
```kotlin
@Serializable
enum class DebtStatus {
    OPEN,
    PARTIAL,
    SETTLED
}

@Serializable
data class Settlement(
    val id: String,
    val refNo: Long = 0L,
    val allocationId: String = "",
    val amount: Double,
    val settledAt: Long,
    val recordedAt: Long,
    val note: String = "",
    val voidedAt: Long? = null
)

@Serializable
data class Allocation(
    val id: String,
    val party: Payer,
    val amount: Double,
    val refNo: Long = 0L,
    val settlements: List<Settlement> = emptyList(),
    val note: String = ""
)

@Serializable
data class Money(
    val amountMinor: Long,
    val currency: String
) : Comparable<Money>

object ReceivablesLedger {
    fun computeAllocation(allocation: Allocation, currency: String, ...): AllocationComputation
    fun getAllocationComputations(expenses: List<ExpenseItem>): List<AllocationComputation>
    fun getPartySummaries(expenses: List<ExpenseItem>): List<PartyBalanceSummary>
    fun getPartySummary(partyId: String, currency: String, expenses: List<ExpenseItem>): PartyBalanceSummary?
    fun getTotalRemaining(expenses: List<ExpenseItem>, currency: String): Double
    fun getTotalSettled(expenses: List<ExpenseItem>, currency: String): Double
    fun recordSettlement(expense: ExpenseItem, allocationId: String, amount: Double, settledAt: Long, note: String, ...): ExpenseItem
    fun voidSettlement(expense: ExpenseItem, settlementId: String, voidedAt: Long): ExpenseItem
    fun renameParty(expenses: List<ExpenseItem>, oldName: String, newName: String): List<ExpenseItem>
    fun mergeParties(expenses: List<ExpenseItem>, sourceName: String, targetName: String): List<ExpenseItem>
}

object LedgerIntegrityChecker {
    fun verify(expenses: List<ExpenseItem>, nowMillis: Long): IntegrityCheckResult
}

object ExportNamer {
    fun generateFileName(type: String, scope: String, extension: String, nowMillis: Long, timeZone: TimeZone, exists: (String) -> Boolean): String
    fun sanitizeAscii(input: String): String
    fun scopeForPeriod(period: ReportPeriod): String
}
```

### 5.7 سيناريوهات القبول بصيغة Gherkin (v3 Accounting & Receivables Scenarios)

#### Scenario: تقسيم فاتورة 100 شيكل إلى 30 إلي و40 للبيت و30 لأخوي
```gherkin
Given نطق المستخدم عبارة "مية شيكل ثلاثين إلي وأربعين للبيت وثلاثين لأخوي"
When يقوم المحلل المحلي باستخراج البيانات
Then يتم إنشاء مصروف بقيمة إجمالية 100 شيكل
And يُحدد نصيب المستخدم myShare بقيمة 30 شيكل
And يُنشأ تخصيصان: "البيت" بقيمة 40 شيكل، و"أخوي" بقيمة 30 شيكل
And يتطابق مجموع myShare مع التخصيصات تماماً (30 + 40 + 30 = 100)
```

#### Scenario: استثناء حصص الآخرين من إجمالي المصروف الشخصي والميزانية
```gherkin
Given المستخدم قام بتسجيل مصروف بمبلغ 100 شيكل
And حدد أن نصيبه الشخصي myShare هو 30 شيكل و70 شيكل تخصيص لطرف آخر
When يتم حساب إجمالي المصاريف اليومية وميزانية الأسبوع
Then يجب أن يُحتسب مبلغ 30 شيكل فقط ضمن النفقات (effectiveAmount)
And يظهر مبلغ 70 شيكل في دفتر المستحقات كدين قيد التحصيل
```

#### Scenario: تسجيل تسديد جزئي بتاريخ محدد وإلغاء التسديد دون حذف صامت
```gherkin
Given وجود مستحقات بقيمة 100 شيكل على طرف "محمد"
When يقوم المستخدم بتسجيل تسديد جزئي بقيمة 40 شيكل بتاريخ الأربعاء 2026-11-19
Then تصبح حالة الدين مسدد جزئياً (PARTIAL)
And يصبح الرصيد المتبقي على محمد 60 شيكل مع حفظ وقت التسجيل المنفصل
When يقوم المستخدم بإلغاء التسديد (Void)
Then تُسجل حالة التسديد بملء حقل voidedAt بتاريخ الإلغاء
And يعود الرصيد المتبقي على محمد إلى 100 شيكل
And يبقى سجل التسديد محفوظاً في دفتر التدقيق ولا يُمحى
```

#### Scenario: تصدير تقريرين متتاليين في نفس الثانية باسمين مستقلين خاليين من التصادم
```gherkin
Given المستخدم يطلب تصدير تقرير المستحقات بصيغة XLSX
And يطلب مباشرة تصدير تقرير ثانٍ في نفس الثانية
When يتم توليد اسم الملف عبر ExportNamer
Then يُنشأ الملف الأول باسم raqeem_receivables_all_20261124-120000.xlsx
And يُنشأ الملف الثاني برقم تسلسلي مانع للتصادم raqeem_receivables_all_20261124-120000-2.xlsx
And لا يكتب أحدهما فوق الآخر في مجلد الكاش
And يعرض التطبيق الاسم الفعلي لكل ملف
```

#### Scenario: فشل محرك الصوت أو غياب النموذج يفتح شيت الإدخال اليدوي تلقائياً
```gherkin
Given تعذر تحميل نموذج Whisper أو انقطاع التسجيل الصوتي بسبب خطأ
When يكتمل مسار المعالجة
Then يُضبط showFallbackManualSheet = true فورياً
And لا يُترك المستخدم أمام شاشة معلقة أو فارغة
And تظهر رسالة عربية هادئة مع فتح حقول الإدخال اليدوي والبطاقات الجاهزة
```

```
