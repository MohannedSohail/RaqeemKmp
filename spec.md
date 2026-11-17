# 📐 Raqeem (رقيم) - Technical & Domain Specification (spec.md)

## 📌 الإصدار 2.1: هندسة Whisper المحلي (100% On-Device Whisper Engine)

تحدد هذه الوثيقة العقود البرمجية، ومعمارية خط معالجة الصوت، وتكامل مكتبة whisper.cpp، وعقود MVI، وسيناريوهات القبول بصيغة Gherkin، وجدول تتبع المشاكل من P1 إلى P11.

---

## 📋 1. جدول تتبع المشاكل الفنية (Defect Matrix P1 - P11)

| المعرّف | وصف المشكلة | الملفات المعنية | الحل المعماري الملزم | الحالة |
|:---|:---|:---|:---|:---|
| **P1** | الصوت يعتمد على Google والإنترنت بدل Whisper المحلي | `AudioRecorder.android.kt`، `PlatformSpeechLauncher.*`، `MainActivity.kt`، `AndroidVoiceEngine.kt` | الاستبدال الكامل بـ `AudioRecord` الخام وتفريغ الصوت في `LocalSpeechTranscriber` (whisper.cpp) | قيد التنفيذ |
| **P2** | `LocalWhisperEngine` محاكاة بـ `delay()` ولا يوجد نموذج GGML حقيقي | `shared/src/commonMain/.../ai/whisper/*` | بناء محرك Whisper حقيقي يستدعي JNI لفك ترميز نماذج GGML الفعلية | قيد التنفيذ |
| **P3** | تحليل أخطاء هش بـ `contains("5")` واستخدام Reflection | `ExpenseViewModel.kt`، `PlatformSpeechLauncher.android.kt` | إزالة الـ Reflection نهائياً، واستخدام `sealed interface SpeechError` محددة النوع | قيد التنفيذ |
| **P4** | وجود صلاحية `INTERNET` واستعلامات `<queries>` لخدمات Google | `androidApp/src/main/AndroidManifest.xml` | حذف `INTERNET` و`ACCESS_NETWORK_STATE` و`<queries>` الخاصة بـ Google Speech تماماً | قيد التنفيذ |
| **P5** | نافذة `LocalWhisperSettingsDialog` شكلية تعرض خيارات غير مثبتة | `LocalWhisperSettingsDialog.kt` | ربط النافذة بـ `availableModels()` لعرض النماذج الموجودة فعلياً على القرص وحجمها | قيد التنفيذ |
| **P6** | مسجلات الصوت في iOS وWasm وJVM تعيد نصوصاً وهمية (Stubs) | `shared/src@ios\|wasmJs\|jvm/audio/*` | إعادة `SpeechError.ModelMissing` أو خطأ صريح بعدم الدعم بدلاً من أي نصوص تمثيلية | قيد التنفيذ |
| **P7** | قيم ابتدائية مكتوبة يدوياً في الواجهة (`dailyTotal = 135.0`) | `ExpenseViewModel.kt` | جعل القيمة الابتدائية صفرية `0.0` وحساب كافة المجاميع من `ExpenseRepository` فقط | قيد التنفيذ |
| **P8** | ميزانية الأسبوع ثابتة بـ 2500 دون إمكانية ضبط | `ExpenseViewModel.kt`، `AppPreferences` | إضافة إعداد لحفظ ميزانية الأسبوع في `AppPreferences` وتعديلها من واجهة الإعدادات | قيد التنفيذ |
| **P9** | إذن الميكروفون يُطلب مرة واحدة عند الإقلاع دون شرح أو معالجة رفض | `MainActivity.kt`، `DashboardScreen.kt` | طلب الإذن عند الضغط على المايك، وشرح الحاجة له، وزر لفتح إعدادات النظام عند الرفض الدائم | قيد التنفيذ |
| **P10** | غياب تعديل المصروف القائم، والتصدير، والنسخ الاحتياطي | `ExpenseRepository.kt`، `PlatformExpenseStorage.kt` | إضافة `updateExpense`، وتصدير CSV/JSON، والنسخ الاحتياطي المحلي | قيد التنفيذ |
| **P11** | وثائق غير دقيقة (ذكر Gemini، وادعاء 100% إنجاز قبل اكتماله) | `README.md`، `review.md` | تطهير التوثيق من أي ذكر لـ Gemini أو Google، وتوثيق الواقع الفعلي بالأدلة الملموسة | قيد التنفيذ |

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
1. **الخيار الأول (المعتمد): مكتبات JNI مسبقة التجميع (Pre-built JNI Shared Libraries):**
   - تجميع `libwhisper.so` و`libwhisper_jni.so` لمعمارية `arm64-v8a` (وهي المعمارية القياسية لـ 99% من هواتف أندرويد الحالية) باستخدام Android NDK.
   - وضع ملفات `.so` داخل مجلد `androidApp/src/main/jniLibs/arm64-v8a/`.
   - كتابة غلاف JNI نقي ومحكم داخل `shared/src/androidMain/` يربط دوال C++ بـ Kotlin عبر `external fun`.
   - **الميزة:** فصل كامل عن تعقيدات بناء CMake داخل بيئات التطوير، والتوافق بنسبة 100% مع كل من Gradle وAmper دون أدنى تضارب، واستقرار فوري للبناء.
2. **الخيار الثاني: دمج CMake وNDK في بناء Gradle/Amper المباشر:**
   - عالي المخاطر نظراً لاختلاف مسارات NDK على أجهزة المطورين وضعف دعم Amper المباشر لمهام C++.
3. **الخيار الثالث: استخدام مكتبة Maven جاهزة:**
   - معظم المكتبات غير متوافقة مع أحدث إصدارات NDK أو تحتوي تبعيات سحابية خفية.

### 3.2 القرار النهائي
**اعتماد الخيار الأول:** ربط مكتبة JNI الأصلية لـ whisper.cpp من خلال `jniLibs/arm64-v8a/` مع غلاف JNI آمن مكتوب بـ Kotlin في وحدة Android، وتوفير واجهات `expect/actual` نقية في `shared/src/commonMain/`.

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
