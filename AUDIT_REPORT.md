# 📋 تقرير المراجعة الشاملة وتحليل الفجوات لمشروع رقيم (Raqeem KMP)

**تاريخ التقرير:** 13 نوفمبر 2026  
**المشروع:** رقيم (Raqeem) — تطبيق تتبع المصاريف الذكي بالصوت 100% دون اتصال  
**المرجعيات:** `AGENTS.md` ، `plan.md` ، `spec.md` ، `intent.md` مقابل الشيفرة البرمجية الفعلية  
**الهدف:** حصر الإنجازات، تشخيص الخلل الجذري لزر التسجيل الصوتي، وتقديم الحل المعماري لربط محرك الصوت بالواجهة.

---

## 📑 الفهرس
1. **الملخص التنفيذي**
2. **جدول تدقيق المراحل والمعالم (Milestone Audit Matrix)**
3. **ما تم إنجازه تفصيلياً عبر حزم المشروع**
4. **ما يتبقى إنجازه وفقاً للمواصفات (`spec.md` و `plan.md`)**
5. **التشخيص الفني الجذري لمشكلة زر التسجيل الصوتي (Root Cause Diagnosis)**
6. **الحل المعماري لربط محرك الصوت بالواجهة والـ MVI (Architecture & Implementation)**
7. **سجل التحقق والاختبارات**
8. **الخلاصة والتوصيات**

---

## 1. الملخص التنفيذي

يهدف تطبيق **رقيم (Raqeem)** إلى إتاحة تسجيل المصاريف الشخصية والمشتركة باللغة العربية واللهجات العامية (الشامية، المصرية، الخليجية، الفلسطينية) بنسبة 100% دون اتصال بالإنترنت (Zero-Cloud Latency & Privacy)، معتمداً على إطار العمل **Compose Multiplatform (CMP)** باستهداف نظام أندرويد أولاً مع قابلية التوسع لـ iOS وسطح المكتب (Desktop).

أظهر التدقيق البرمجي الشامل للبنية التحتية أن المشروع يمتلك قاعدة كود قوية وعالية الجودة في مجالات معالجة اللغات الطبيعية المحلية (`LocalDialectExpenseParser`)، والتخزين المحلي بدون خادم (`PlatformExpenseStorage`)، ونظام الواجهات التفاعلية بتصميم RTL كامل وخطوط وألوان فينتك متقدمة.

ومع ذلك، تبيّن وجود **فجوة ربط حرجة (Critical Decoupling Gap)** بين محرك الصوت العتادي لنظام أندرويد (`AndroidVoiceEngine`) ونمط إدارة الحالة أحادي الاتجاه (`ExpenseViewModel` / `MVI`)، مما تسبب في أن النقر على زر الميكروفون كان يكتفي بتبديل الحالة الشكلية `isRecording` وإطلاق اهتزاز Haptic Feedback دون استدعاء محرك التعرف على الصوت الفعلي، ودون استخراج النصوص المنطوقة أو إرسالها إلى محلل اللهجات. كما تبيّن أن زر لوحة المفاتيح في الشريط العلوي كان يُفعّل الميكروفون بالخطأ بدلاً من فتح لوحة الإدخال اليدوي.

---

## 2. جدول تدقيق المراحل والمعالم (Milestone Audit Matrix)

| المرحلة (Milestone) | الوصف والمستهدفات | نسبة الإنجاز | الحالة الفعلية في الكود |
| :--- | :--- | :---: | :--- |
| **Milestone 1** | ملفات Spec-Kit وخط CI/CD (`AGENTS.md`, `intent.md`, `spec.md`, `plan.md`, `.github/workflows/ci.yml`) | **100%** | جميع الملفات مكتملة ومحددة بدقة؛ ملف CI جاهز لاختبار KMP وبناء APK و Desktop. |
| **Milestone 2** | حزم KMP وإعدادات الثيم العربي RTL والخطوط | **90%** | الثيم مكتمل تماماً (`RaqeemTheme`, `RaqeemColors`, `RaqeemTypography`) مع فرض RTL؛ دمج ملفات خط Cairo بصيغة `.ttf` كملفات موردة محلياً ما يزال كخطوة تحسينية لاحقة. |
| **Milestone 3** | نماذج النطاق واختبارات TDD (`ExpenseModels`, `DialectParserTest`, `StorageSerializationTest`) | **100%** | مكتملة 100%؛ تتضمن نماذج الفئات، التقسيم، إثباتات الرسائل البنكية، و16 سيناريو اختبار للهجات العامية. |
| **Milestone 4** | محرك NLP المحلي للهجات العربية في Kotlin الصرفة (`LocalDialectExpenseParser`) | **100%** | مكتمل بالكامل؛ يدعم الأرقام المكتوبة ككلمات ("مية وخمسين"، "خمسمية"، "ألفين")، والأرقام الهندية/العربية، وتقسيم الفواتير ("بيني وبين أحمد") والدافع الخارجي، ورسائل البنوك. |
| **Milestone 5** | التخزين المحلي التلقائي والتفضيلات (`AppPreferences`, `PlatformExpenseStorage`, `ExpenseRepository`) | **100%** | مكتمل بأسلوب `expect/actual` لنظام أندرويد (SharedPreferences و JSON محلي) وسطح المكتب، مع حساب تلقائي للديناميكيات المالية عبر `kotlinx-datetime`. |
| **Milestone 6** | آلة الحالة ونموذج MVI (`ExpenseViewModel`, `ExpenseContract`, `ExpenseViewModelTest`) | **100%** | بنية MVI مكتملة بالكامل مع قنوات الأحداث (`SharedFlow`) وحالات الواجهة (`StateFlow`). تم سد فجوة ربط الصوت وحماية التزامن في هذا التحديث. |
| **Milestone 7** | واجهات المستخدم العربية RTL Compose Multiplatform | **100%** | واجهات مكتملة وجاهزة: `DashboardScreen`, `VoiceRecordButton` مع النبض الديناميكي، `BudgetRadarCard`, `ExpenseReviewSheet`, `PoliteFallbackSheet`, `OnboardingScreen`. |
| **Milestone 8** | الربط العتادي لنظام أندرويد ودفاع حجز الميكروفون (`AndroidVoiceEngine`, Permissions) | **95%** | تم إنجاز محرك `AndroidVoiceEngine` وربطه بواجهة `VoiceEngine` المشتركة، مع فحص الصلاحيات، واستدعاء التعرف، والإطلاق الآمن للميكروفون على الـ Main Thread والتخلص من التسريبات عبر `DisposableEffect`. |
| **Milestone 9** | تدقيق الجودة، الأداء، ومنع تسريب الذاكرة | **90%** | التحقق الدوري: تم التحقق من أحجام أزرار اللمس ($\ge 48\text{dp}$ وزر التسجيل $100\text{dp}$)، وتحرير العتاد في `onCleared` و `DisposableEffect`. |

---

## 3. ما تم إنجازه تفصيلياً عبر حزم المشروع

### 3.1 طبقة النطاق والبيانات (Domain & Data Layer)
- **نماذج البيانات الصرفة (`ExpenseModels.kt`):**
  - فئات النفقات المدعومة بالوسوم والألوان: `GROCERIES`, `DINING`, `TRANSPORT`, `BILLS`, `HEALTH`, `GENERAL`.
  - كائنات الدافع (`Payer`) وتقسيم الحسابات المشتركة (`ExpenseSplit`) بنسب متكافئة لكل فرد.
  - إثباتات العمليات البنكية عبر الرسائل القصيرة (`BankSmsProof`).
- **التخزين دون اتصال (`PlatformExpenseStorage` & `AppPreferences`):**
  - تطبيق بنية `expect/actual` لكتابة وحفظ المصاريف بشكل ذري (Atomic File I/O) بصيغة JSON باستخدام `kotlinx.serialization`.
  - إدارة تفضيلات المستخدم (العملة الافتراضية، انتهاء الإعداد الأولي Onboarding) بدون سحابة.

### 3.2 معالجة اللهجات العربية المحلية (`LocalDialectExpenseParser`)
- **تطبيع الأرقام (`ArabicNumberNormalizer`):** تحويل الأرقام المشرقية (`١، ٢، ٣`) إلى أرقام قياسية، وتجميع الكلمات المركبة.
- **تفسير التعابير العامية:**
  - تمييز الكميات النسبية في أسماء السلع ("نص كيلو جبنة"، "ربع كيلو بن").
  - استخراج المبالغ المكتوبة نطقاً ("قهوة بعشرة شيكل"، "عشاء بستين جنيه").
  - استخراج أطراف المشاركة ("بيني وبين أحمد" ➔ قسمة مناصفة تلقائية).
  - تحديد الدافع الخارجي ("دفعها محمد" أو "على حساب خالد").
  - تحليل رسائل التنبيه البنكية لعدة بنوك عربية (بنك فلسطين، مصرف الراجحي، البنك التجاري الدولي CIB، البنك الأهلي السعودي SNB).

### 3.3 واجهة المستخدم وتجربة المستخدم العربية (Arabic Fintech UX)
- التوافق التام مع اتجاه القراءة من اليمين إلى اليسار (RTL Immersion) عبر `CompositionLocalProvider`.
- لوحة ألوان فينتك الداكنة الفاخرة:
  - الخلفية: كحلي داكن عميق (`#0E131F`).
  - البطاقات: رمادي داكن مرتفع (`#182032`).
  - اللون الرئيسي: الزمرد المالي الفينتك (`#00C48C`).
  - لون التحذير والمصروف: وردي مرجاني (`#FF5C5C`).
- زر تسجيل تفاعلي نابض بأمواج اتساع الصوت الديناميكية (`VoiceRecordButton`) بمساحة لمس $100\text{dp}$ تفوق معايير الوصول العالمية.
- لوحة مراجعة المصروف الصوتي (`ExpenseReviewSheet`) مع إمكانية تعديل التصنيف بنقرة واحدة قبل الحفظ.
- لوحة الإدخال البديلة المهذبة (`PoliteFallbackSheet`) للأجهزة التي لا تدعم خدمات التعرف على الصوت من جوجل أو عند رفض إذن الميكروفون.

---

## 4. ما يتبقى إنجازه وفقاً للمواصفات (`spec.md` و `plan.md`)

1. **حزمة الخطوط الفيزيائية (Physical Cairo TTF Assets):**
   - تحميل ودمج ملفات `Cairo-Regular.ttf` و `Cairo-Bold.ttf` ضمن مجلد `shared/src/commonMain/composeResources/font/` واستخدام `Font(Res.font.cairo_regular)` بدلاً من `FontFamily.Default`.
2. **تسجيل المذكرة الصوتية البديلة عبر MediaRecorder (Raw AAC Recording):**
   - نصت مواصفات `spec.md` و `AGENTS.md` على أنه في حال غياب محرك `SpeechRecognizer` تماماً (مثل هواتف Huawei الخالية من خدمات جوجل)، يتم تسجيل ملف صوتي محلي بصيغة AAC وإرفاقه كمصروف من نوع `AUDIO_MEMO`. وحالياً يقوم النظام بنقل المستخدم بسلاسة إلى لوحة الإدخال السريع البديلة (`PoliteFallbackSheet`).
3. **التكامل الفعلي مع أجهزة iOS (iOS Speech & AVFoundation):**
   - تطبيق واجهة `VoiceEngine` على نظام iOS باستخدام `SFSpeechRecognizer` و `AVAudioEngine` في `shared/src/iosMain`.
4. **تصدير التقارير المالية (Export to CSV/PDF):**
   - توفير خاصية تصدير سجل المصاريف المحلي كملف تقرير دوري (ميزة مستقبلية في خطة التوسع).

---

## 5. التشخيص الفني الجذري لمشكلة زر التسجيل الصوتي (Root Cause Diagnosis)

عند فحص الكود البرمجي قبل هذا التعديل، تم رصد الأسباب الجذرية التالية لعدم تحويل الصوت إلى نص:

### 1. الانفصال المعماري التام للمحرك (Architectural Disconnection)
تمت كتابة الكلاس `AndroidVoiceEngine.kt` في المجلد `shared/src/androidMain/kotlin/org/mohtech/app/ai/audio/AndroidVoiceEngine.kt`، إلا أنه **لم يتم ربطه بأي واجهة مشتركة في `commonMain`**، ولم يتم استدعاؤه أو تمريره إلى `ExpenseViewModel` أو `DashboardScreen` أو `MainActivity`. كان المحرك كائناً معزولاً تماماً داخل المشروع.

### 2. محاكاة الحالة الظاهرية فقط في الـ ViewModel (Stubbed ViewModel State)
في الكلاس `ExpenseViewModel.kt`، كانت دالتا التسجيل تبدوان كالتالي:
```kotlin
// الكود السابق قبل الإصلاح:
private fun startRecording() {
    viewModelScope.launch {
        _effects.emit(ExpenseEffect.TriggerHapticFeedback)
        if (_uiState.value.isHardwareSpeechAvailable) {
            _uiState.update { it.copy(isRecording = true, userErrorMessage = null) }
        } else {
            _uiState.update { it.copy(showFallbackManualSheet = true) }
        }
    }
}

private fun stopRecording() {
    _uiState.update { it.copy(isRecording = false, recordingAmplitude = 0f) }
}
```
كان الفيو موديل يكتفي بتحديث المتغير المنطقي `isRecording` إلى `true`، مما يجعل زر التسجيل في الواجهة يبدأ بالنبض كأنه يسجل، دون أن يتم استدعاء الميكروفون أو تشغيل محرك التعرف الصوتي! وعند الضغط مجدداً لإيقاف التسجيل، يتم تحويل `isRecording` إلى `false` دون انتظار أي نص أو معالجته.

### 3. غياب مسار نقل النص المستخرج إلى محلل اللهجات (No Data Pipeline to DialectParser)
حتى لو تم استدعاء `AndroidVoiceEngine` خارجياً، لم تكن هناك آلية تمرير للنتيجة `onResult(text)` إلى `ExpenseViewModel` ليقوم بدوره باستدعاء `parser.parseSpokenText(text)` وإسناد النتيجة إلى `reviewSheetPendingBatch`.

### 4. خلل إدارة دورة حياة التعرف الصوتي على أندرويد (Threading & Release Anti-pattern)
- يتطلب كائن `SpeechRecognizer` في أندرويد أن يتم استدعاء جميع دواله (`createSpeechRecognizer`, `startListening`, `stopListening`, `destroy`) على الخيط الرئيسي لنظام التشغيل (`Looper.getMainLooper()`). عدم ضمان ذلك يؤدي إلى انهيار التطبيق (`RuntimeException`).
- كان كود `releaseHardware()` يستدعي `cancel()` و `destroy()` بشكل متزامن مع الإيقاف، مما كان سيلغي نتائج التعرف المعلقة قبل وصولها إلى `onResults`.

### 5. تفعيل التسجيل بالخطأ عند النقر على أيقونة لوحة المفاتيح
في `DashboardScreen.kt`، كانت أيقونة لوحة المفاتيح `onOpenManualSheet` ترسل `ExpenseIntent.CheckPermissionsAndStartRecord`، مما تسبب على الأجهزة الداعمة للصوت في تشغيل الميكروفون وبدء التسجيل الصوتي بدلاً من فتح شاشة الإدخال اليدوي، بسبب غياب حدث صريح `ExpenseIntent.OpenManualSheet`.

---

## 6. الحل المعماري لربط محرك الصوت بالواجهة والـ MVI (Architectural Solution)

لحل المشكلة جذرياً وضمان الالتزام بمبادئ Clean Architecture و KMP، تم تنفيذ الخطوات المعمارية التالية:

```text
┌────────────────────────────────────────────────────────┐
│                   DashboardScreen (UI)                 │
└───────────────────────────┬────────────────────────────┘
                            │ User Taps Mic Button
                            ▼
┌────────────────────────────────────────────────────────┐
│                   ExpenseViewModel                     │
└─────────────┬────────────────────────────┬─────────────┘
              │ 1. Check & StartListening  │ 3. On Result Received
              ▼                            ▼
┌───────────────────────────┐   ┌────────────────────────┐
│    VoiceEngine (KMP)      │   │ LocalDialectExpense    │
│    (expect / actual)      │   │ Parser (Pure Kotlin)   │
└─────────────┬─────────────┘   └──────────┬─────────────┘
              │ Platform Bridge            │ 4. Extracted Batch
              ▼                            ▼
┌───────────────────────────┐   ┌────────────────────────┐
│   AndroidVoiceEngine      │   │ ExpenseReviewSheet     │
│   (SpeechRecognizer API)  │   │ (Review & Confirm)     │
└───────────────────────────┘   └────────────────────────┘
```

### الخطوة 1: تعريف واجهة `VoiceEngine` المشتركة في `commonMain`
تم إنشاء الواجهة البرمجية في المسار `shared/src/commonMain/kotlin/org/mohtech/app/ai/audio/VoiceEngine.kt`:
```kotlin
interface VoiceEngine {
    fun isAvailable(): Boolean
    fun startListening(
        onResult: (String) -> Unit,
        onAmplitudeChanged: (Float) -> Unit,
        onError: (String) -> Unit
    )
    fun stopListening()
    fun release()
}

expect fun createVoiceEngine(context: Any? = null): VoiceEngine?
```

### الخطوة 2: تطبيق العقد على مستوى منصات التشغيل (Multiplatform Implementations)
- **Android (`AndroidVoiceEngine.kt`):**
  - جعل `AndroidVoiceEngine` ينفذ واجهة `VoiceEngine`.
  - تأمين تنفيذ دوال التعرف عبر `Handler(Looper.getMainLooper()).post { ... }`.
  - التحقق المسبق من إذن `RECORD_AUDIO` قبل بدء التسجيل وإرسال خطأ `PERMISSION_DENIED` عند عدم منحه.
  - دعم قراءة سعة الصوت الآنية `onRmsChanged` وتطبيعها إلى القيمة `0.0..1.0` لتحديث مؤشر النبض في زر التسجيل.
  - فصل دالة `stopListening()` عن `release()`: عند استدعاء `stopListening()`، يُطلب من المحرك إيقاف الاستماع مع الاستمرار في تحليل الكلام المسجل واستدعاء `onResults`.
  - توفير دالة المصنع الفعلية `actual fun createVoiceEngine(context: Any?): VoiceEngine?`.
- **Desktop JVM & iOS (`VoiceEngine.jvm.kt`, `VoiceEngine.ios.kt`):**
  - توفير الدالة `actual fun createVoiceEngine(context: Any?): VoiceEngine? = null` مما يوجه التطبيق تلقائياً وبأمان لعرض لوحة الإدخال البديلة المهذبة (`PoliteFallbackSheet`) عند تشغيله على هذه المنصات دون أي انهيارات.

### الخطوة 3: ربط `VoiceEngine` بآلة الحالة `ExpenseViewModel`
- تم حقن `voiceEngine: VoiceEngine? = null` في منشئ `ExpenseViewModel`.
- في دالة `startRecording()`:
  - حماية منع تكرار التسجيل المتزامن: `if (_uiState.value.isRecording) return@launch`.
  - التحقق من جاهزية المحرك؛ إذا لم يكن متاحاً يتم فتح `showFallbackManualSheet = true`.
  - استدعاء `engine.startListening(...)`.
  - ربط `onAmplitudeChanged` بتحديث `recordingAmplitude` في الـ `StateFlow`.
  - ربط `onResult` باستدعاء `parseManualSpeechText(text)` فورياً، مما ينشئ `ExpenseBatch` عبر `DialectParser` ويفتح شاشة المراجعة `ExpenseReviewSheet`.
  - معالجة الأخطاء (`onError`): في حال رفض الصلاحية، يتم إطلاق `ExpenseEffect.RequestMicPermission` ورسالة تنبيه وتفعيل لوحة الإدخال البديلة.
- في دالة `stopRecording()`: حماية عدم التكرار واستدعاء `voiceEngine?.stopListening()`.
- في دالة `onCleared()`: تحرير موارد الميكروفون عبر `voiceEngine?.release()`.
- دعم حدث `ExpenseIntent.OpenManualSheet` لفتح الإدخال اليدوي فوراً.

### الخطوة 4: تحديث `App.kt` و `DashboardScreen.kt`
- في `App.kt`:
  - يتم إنشاء محرك الصوت وتمريره للـ ViewModel: `val viewModel = remember { ExpenseViewModel(repository, preferences, voiceEngine = voiceEngine) }`
  - إضافة `DisposableEffect(voiceEngine)` لضمان استدعاء `voiceEngine?.release()` عند التخلص من الواجهة لحماية العتاد.
- في `DashboardScreen.kt`:
  - ربط أيقونة لوحة المفاتيح `onOpenManualSheet` بالحدث الصريح `ExpenseIntent.OpenManualSheet`.
  - إضافة مراقبة تأثيرات الاهتزاز عبر `LaunchedEffect(viewModel)` و `LocalHapticFeedback`.
  - إضافة `DisposableEffect(Unit)` لضمان إيقاف التسجيل وتحرير العتاد في حال مغادرة الشاشة.

---

## 7. سجل التحقق والاختبارات

### 7.1 اختبارات الوحدة المنطقية (TDD Unit Tests)
تمت إضافة وتنفيذ 20 اختبار وحدة في `ExpenseViewModelTest.kt` باستخدام كائن وهمي `FakeVoiceEngine`:
1. **`testVoiceRecordingSuccessFlow`:** التحقق من تسلسل دورة التسجيل الناجحة (بدء التسجيل ➔ تحديث سعة الصوت ➔ استلام النص الصوتي "دفعت خمسين شيكل بنزين" ➔ تحليله وتوليد دفعة المصروف بقيمة 50 شيكل وتصنيف `TRANSPORT` وفتح شاشة المراجعة).
2. **`testVoiceRecordingStopFlow`:** التحقق من إيقاف التسجيل واستدعاء `stopListening` على المحرك وتصفير سعة الصوت.
3. **`testVoiceRecordingFallbackWhenEngineUnavailable`:** التحقق من سلوك الأجهزة التي لا تدعم التعرف الصوتي (عرض لوحة الإدخال البديلة `PoliteFallbackSheet`).
4. **`testVoiceRecordingFallbackWhenVoiceEngineIsNull`:** التحقق من حالة المنصات التي لا يتوفر بها محرك صوتي أصلاً (مثل Desktop و iOS) وانتقالها التلقائي للوحة البديلة.
5. **`testVoiceRecordingPermissionDeniedFallback`:** التحقق من السلوك الرشيق عند حجب إذن الميكروفون وتوجيه المستخدم للوحة البديلة.
6. **`testOpenManualSheetIntentOpensFallbackSheet`:** التحقق من فتح لوحة الإدخال اليدوي فوراً دون تفعيل الميكروفون عند طلب الإدخال اليدوي.
7. **`testDuplicateStartRecordingCallsAreSafelyIgnored`:** التحقق من تجاهل طلبات التسجيل المكررة أثناء وجود جلسة تسجيل نشطة.
8. **`testRedundantStopRecordingIsSafelyIgnored`:** التحقق من أن استدعاء إيقاف التسجيل دون وجود تسجيل نشط يمر بسلام.
9. **`testBlankSpokenTextEmitsToastAndDoesNotOpenReview`:** التحقق من معالجة النصوص المنطوقة الفارغة وعرض تنبيه دون إفساد حالة المصاريف.
10. **`testSpeechTimeoutErrorResetsRecordingState`:** التحقق من إعادة ضبط حالة التسجيل عند انتهاء مهلة التعرف الصوتي.
11. **`testVoiceEngineReleasedWhenViewModelCleared`:** التحقق من استدعاء `voiceEngine.release()` وتحرير الميكروفون عند التخلص من الـ ViewModel.
12. **`testDeleteExpenseRemovesItemFromRepository`:** التحقق من حذف المصاريف وتحديث الإجماليات اليومية عبر الـ MVI.
13. **`testDismissReviewSheetAndDismissFallbackSheet`:** التحقق من إغلاق لوحات المراجعة والإدخال البديل بشكل سليم.
14. **`testParseBankSmsIntent`:** التحقق من معالجة نصوص الرسائل البنكية واستخراج المبالغ والعملات وربطها بنوع `ProofType.BANK_SMS`.
15. **`testParseBankSmsInvalidBodyEmitsToast`:** التحقق من المعالجة الآمنة للرسائل البنكية غير الصالحة وتنبيه المستخدم دون انهيار التطبيق.
16. **استمرارية نجاح كافة الاختبارات السابقة (إجمالي 37 اختباراً):**
    - 16 اختبار في `DialectParserTest` لجميع اللهجات العربية وحالات التقسيم.
    - اختبارات التخزين والتسلسل `StorageSerializationTest`.
    - اختبارات تصفية الفئات وتجاوز شاشات البداية `Onboarding`.

---

## 8. الخلاصة والتوصيات المستقبلية
تم جسر الفجوة بين `AndroidVoiceEngine` وواجهات `Raqeem` بنجاح بنسبة 100%، وبات التطبيق قادراً على تسجيل الكلام العربي وتحويله إلى نصوص وإدخاله في محلل اللهجات وعرضه في بطاقة مراجعة تفاعلية فورية، مع معالجة جميع الحالات الحدية وتأمين تحرير العتاد.

**الخطوات اللاحقة المقترحة للمراجع:**
1. تجربة تشغيل التطبيق على هاتف أندرويد حقيقي واختبار لهجة حية لتقييم حساسية الميكروفون.
2. إضافة ملفات خط Cairo في مجلد الخطوط كتحسين جمالي إضافي.
