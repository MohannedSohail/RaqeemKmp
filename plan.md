# 🗺️ Raqeem (رقيم) - Master Implementation Plan (plan.md)

## 📌 الإصدار 2.1: خطة تنفيذ Whisper الأوفلاين 100% (Spec Kit SDLC)

تتبع هذه الخطة منهجية التطوير الموجه بالمواصفات (TDD / Spec-Kit)، بتسلسل صارم: **Red (فشل الاختبارات أولاً) ➔ Green (كتابة الكود لتمرير الاختبارات) ➔ Refactor & Audit (التنقيح والتدقيق)**.
تم تشغيل الأوامر الفعلية وتوثيق مخرجاتها في `review.md`.

---

## 🏗️ 0. حسم قرار ربط Whisper JNI مع نظام البناء (Build & JNI Decision)

* **القرار المعماري المعتمد:**
  بناء مكتبة `libwhisper.so` لمعمارية `arm64-v8a` مباشرة من المصدر عبر **CMake & externalNativeBuild** في وحدة `androidApp`، باستخدام مستودع `whisper.cpp` كـ **Git submodule** مثبت على الوسم المستقر `v1.7.4` (commit `8a9ad784`) مع NDK `30.0.16248370`.
* **السبب:**
  استبدال ملفات `.so` الثابتة بالبناء الآلي المباشر يضمن قابلية التكرار (Reproducible Builds) والاتساق مع CI/CD وR8 Proguard، ودمج كود JNI ومكتبة Whisper في مكتبة مشتركة واحدة محسنة بحجم مصغر (~1.1 MB).
* **إثبات الجدوى (PoC):**
  التحقق من ترجمة C++ عبر CMake بنجاح وإنتاج AAB بحجم ~35.3 MB وتمرير كافة الاختبارات دون أخطاء.

---

## 📋 قائمة مراحل التنفيذ (Implementation Checklist)

### المرحلة 0: إثبات جدوى Whisper والبيئة (Feasibility Proof & Setup)
- [x] فحص وتأكيد بنية المجلدات لـ `jniLibs` في `androidApp/src/main/jniLibs/arm64-v8a/`.
- [x] إضافة ملفات النماذج الثنائية (`*.bin`, `*.ggml`) إلى `.gitignore` لمنع رفعها إلى المستودع.
- [x] توثيق خطوات نسخ النموذج عبر `adb push` وتجهيز مجلد `assets` في التوثيق.

---

### المرحلة 1: الاختبارات الفاشلة أولاً (Red Phase - TDD in `commonTest`)
- [x] **اختبارات محاكي Whisper (`FakeTranscriberTest`):**
  - بناء `FakeTranscriber` و`FakeAudioRecorder` داخل `shared/src/commonTest/kotlin/`.
  - اختبار معالجة حالات `SpeechError.ModelMissing` و`SpeechError.EmptyResult` و`SpeechError.PermissionDenied`.
- [x] **اختبارات `ExpenseViewModelTest` المحدثة:**
  - التحقق من أن الحالة الابتدائية `dailyTotal == 0.0` تماماً (حل P7).
  - اختبار تدفق التقاط الصوت عبر PCM وظهور `ExpenseReviewSheet` فورياً عند نجاح التحويل.
  - اختبار عرض رسالة `ModelMissing` وفتح الإدخال الكتابي عند غياب النموذج.
  - اختبار عرض حوار شرح الإذن عند رفض الميكروفون (حل P9).
- [x] **اختبارات معالجة الصوت والرياضيات:**
  - كتابة اختبارات حساب سعة الصوت (RMS) على مصفوفة `ShortArray` ثابتة والتحقق من تطبيع الناتج بين `0.0f` و`1.0f`.
  - كتابة اختبارات تقطيع الصوت (Audio Chunking) للمقاطع الصوتية التي تتجاوز 30 ثانية.
- [x] **اختبارات تطهير المانيفست والكود (Purity Check):**
  - كتابة اختبار تحليلي/فحص نصي يؤكد خلو المشروع من `android.speech.*` و`RecognizerIntent` و`INTERNET`.
- [x] **اختبارات المستودع للميزات الجديدة (`ExpenseRepositoryTest`):**
  - اختبار دالة تعديل المصروف `updateExpense` وحفظ التغييرات ذرياً (حل P10).
  - اختبار تصدير المصروفات بصيغة CSV وJSON محلياً (حل P10).
  - اختبار حفظ واسترجاع ميزانية الأسبوع في `AppPreferences` (حل P8).

---

### المرحلة 2: تطبيق خط الصوت ومحرك Whisper (Green Phase - Core Implementation)
- [x] **تطهير المانيفست وحذف خدمات Google (P1, P4):**
  - حذف صلاحيات `INTERNET` و`ACCESS_NETWORK_STATE` من `AndroidManifest.xml`.
  - حذف استعلامات `<queries>` لخدمات Google Speech (`RecognitionService`, Google Quick Search Box).
- [x] **واجهات KMP النقية (`shared/src/commonMain`):**
  - إنشاء ملف `WhisperContracts.kt` متضمناً: `LocalSpeechTranscriber`، `RawAudioRecorder`، `SpeechError`، `TranscribeResult`، `WhisperModelConfig`، `InstalledModel`.
  - حذف الواجهات القديمة المشوهة: `getRecognizedText()`، `isProcessing()`، `PlatformSpeechLauncher`، و`ExpenseEffect.LaunchNativeSpeechRecognition`.
- [x] **مسجل الصوت الخام (Android `AudioRecord`):**
  - تطبيق `AndroidRawAudioRecorder` باستخدام `AudioRecord` مع تنسيق 16kHz Mono 16-bit PCM.
  - حساب RMS المباشر لتغذية موجة الواجهة `getAmplitude()`.
  - ضمان تحرير الميكروفون بالكامل في كتلة `finally`.
- [x] **غلاف JNI ومحرك Whisper الفعلي (Android JNI Wrapper):**
  - ربط دوال JNI الأصلية لـ whisper.cpp: `initModel(path)`، `freeModel()`، `fullTranscribe(pcm, lang, prompt)`.
  - معالجة عملية التحويل الصوتي في خيط خلفي (`Dispatchers.Default`) مع تقسيم التسجيلات الطويلة (> 30 ثانية).
- [x] **تطبيق الواجهات على المنصات الأخرى (iOS / Desktop / Wasm) (P6):**
  - استبدال النصوص الوهمية (Stubs) في المنصات الأخرى بإرجاع صريح لـ `SpeechError.ModelMissing` أو خطأ عدم الدعم.
- [x] **تحديث `ExpenseViewModel` وحذف تحليل الأخطاء الهش (P3, P7):**
  - إزالة `contains("5")` وReflection نهائياً.
  - ضبط القيمة الابتدائية للمجاميع `dailyTotal = 0.0` وحسابها ديناميكياً من المستودع.
  - ربط استجابات الأخطاء بنصوص عربية واضحة (دون ذكر Google أو الإنترنت).

---

### المرحلة 3: الأولويات الإضافية وتجربة الاستخدام (P8, P9, P10, P5)
- [x] **إعدادات ميزانية الأسبوع (P8):**
  - إضافة `weeklyBudget` إلى `AppPreferences` بقيمة افتراضية (2500) مع إمكانية تعديلها وحفظها.
- [x] **إدارة أذونات الميكروفون المتقدمة (P9):**
  - بناء بطاقة شرح الحاجة للميكروفون عند الضغط عليه، وتوفير زر لفتح إعدادات النظام `ACTION_APPLICATION_DETAILS_SETTINGS` عند الرفض الدائم.
- [x] **تعديل المصروف وتصدير البيانات (P10):**
  - إضافة دالة `updateExpense(item)` في `ExpenseRepository` وواجهة التعديل في `ExpenseReviewSheet`.
  - إضافة دالة لتصدير المصروفات بصيغة CSV وJSON محلياً عبر المشاركة المحلية للنظام.
- [x] **نافذة نماذج Whisper الحقيقية (P5):**
  - ربط `LocalWhisperSettingsDialog` بقائمة `getInstalledModels()` لعرض النماذج الموجودة فعلياً على القرص وحجمها.
- [x] **شاشات Splash و Onboarding وهوامش الجوال UnSlop:**
  - بناء شاشة Splash تفاعلية بنبضات صوتية ومساواة موجية واختفاء سلس.
  - بناء شاشات Onboarding ثلاثية تفاعلية بـ Pager ومؤشرات حركية.
  - حماية حواف الجوال (StatusBars و NavigationBars و Safe Insets) ومنع قص الثقوب العلوية.

---

### المرحلة 4: التنقيح، والتدقيق، والتوثيق (Refactor & Review)
- [x] **تشغيل حزمة الاختبارات الشاملة:**
  - تنفيذ `.\gradlew.bat :shared:jvmTest` — 139 اختباراً ناجحاً بنسبة 100% (0 فشل).
- [x] **بناء حزمة أندرويد النهائية:**
  - تنفيذ `.\gradlew.bat :androidApp:assembleDebug` — بناء نظيف ناجح 100%.
  - تنفيذ `.\gradlew.bat :androidApp:bundleRelease` — حزمة Release AAB بحجم 76.2MB جاهزة للمتجر (< 200MB).
- [x] **فحص التطهير التام بالأوامر (Sanitization Grep):**
  - فحص خلو الكود والمانيفست من `INTERNET` و `android.speech`.
- [x] **تحديث وثائق المشروع (README.md و review.md و docs/WHISPER_ARCHITECTURE_GUIDE.md):**
  - توثيق تفاصيل JNI وربط الميزانية ومراحل المعالجة الصوتية وحفظ الحالة عند التدوير.

---

### المرحلة 5: ترقية دقة الصوت وهيكل التبويبات والميزانية والتصدير (V2.2 - Parts A through F)
- [x] **الجزء A: دقة الصوت والمصحح اللاحق والـ VAD:**
  - تلقيم whisper.cpp بجملة طبيعية ومحددات خفض الهلوسة في JNI.
  - VAD بـ Kotlin الخالص في `VoiceActivityDetector.kt` لاقتطاع الصمت ورفض التسجيل الفارغ.
  - مصحح لاحق سياقي `TranscriptNormalizer.kt` للأخطاء الشائعة وحماية الأسماء.
  - إصلاح مضاعفة الآلاف والكسور في `ArabicNumberNormalizer.kt` وإضافة أنماط «سددت عن».
  - توثيق منهجية التقييم في `docs/VOICE_EVAL.md`.
  - **الحالة:** تم واختُبر آلياً ✅ (168 اختباراً في `:shared:jvmTest`).
- [x] **الجزء B: هيكل التنقل متعدد الشاشات (MainShell & 4 Tabs):**
  - إنشاء `MainTab` (HOME, HISTORY, REPORTS, SETTINGS) دون المساس بـ `AppDestination`.
  - بناء `MainShell.kt` بشريط تنقل سفلي UnSlop وNavigationRail للأفقي مع حفظ الحالة عبر التدوير وزر رجوع.
  - **الحالة:** تم واختُبر آلياً ✅ (فحص الواجهة والتدوير يدوي على جهاز).
- [x] **الجزء C: الميزانية الأسبوعية وإزالة «محلي 100%»:**
  - محرر الميزانية `BudgetEditorDialog.kt` وحساب الأسبوع التقويمي `CalendarWeekUtil.kt`.
  - إزالة كاملة لعبارة «محلي 100%» من واجهات التطبيق والاستعاضة بصياغة هادئة.
  - **الحالة:** تم واختُبر آلياً ✅.
- [x] **الجزء D: الـ Snackbar المخصص والتراجع (RaqeemSnackbarHost):**
  - مكوّن `RaqeemSnackbarHost.kt` بموضع صحيح مخصص ونسب تباين WCAG AAA (15.8:1 للنص و6.9:1 للأزرار).
  - خط تقدم زمني للتراجع ودعم تراجع الحذف والتكرار وتغيير التصنيف.
  - **الحالة:** تم واختُبر آلياً ✅.
- [x] **الجزء E: بطاقات المصاريف التفاعلية والحوارات:**
  - `ExpenseItemCard.kt` مع `combinedClickable` وقائمة سريعة لتغيير التصنيف.
  - `SwipeToDismissBox` باتجاه واحد وحوار تأكيد أنيق `ConfirmActionDialog.kt` بحركة دخول وخروج ناعمة.
  - **الحالة:** تم واختُبر آلياً ✅.
- [x] **الجزء F: تصدير التقارير (Excel و PDF):**
  - كاتب XLSX نقي بـ Kotlin في `XlsxWriter.kt` دون مكتبات خارجية بصيغ XML و rightToLeft و `=SUM`.
  - كاتب PDF عبر Android Canvas في `PdfReportWriter.android.kt`.
  - مشاركة FileProvider وتخزين SAF عبر `PlatformReportBridge`.
  - إزالة خيارات JSON/CSV من الواجهة.
  - **الحالة:** تم واختُبر آلياً ✅ (فحص فتح الملفات في Excel وقارئ PDF يدوي على جهاز).

---

### المرحلة 6: الترقية المحاسبية الشاملة ودفتر المستحقات UnSlop (v3 Accounting & Receivables Upgrade)
- [x] **المحور B: خط التقاط الصوت ومعالجة السقوط الآمن (Audio Pipeline & Fallback):**
  - تعيين مهلة ديناميكية للمعالجة (`effectiveDurationMs * 2` محصورة بين 5 و15 ثانية).
  - تحرير الميكروفون الحتمي عند `onCleared()`.
  - ضمان انتهاء أي مسار خطأ بظهور الشيت اليدوي أو حفظ الدفعة الحالية دون ضياع.
  - **الحالة:** تم واختُبر آلياً ✅.
- [x] **المحور A: نماذج البيانات ودفتر الأستاذ الموحد والتخزين الذري (Model, Ledger & Storage):**
  - اعتماد `effectiveAmount = myShare ?: amount` للمجاميع والميزانية والتقارير.
  - نموذج التخصيصات `Allocation`، والتسديدات `Settlement`، وسجل التدقيق `AuditRecord`.
  - معالجة العملات والكسور في `Money.kt` وتقريب النصف للأعلى وتوزيع الفروق على حصة المستخدم.
  - بناء `ReceivablesLedger` كمصدر وحيد للحقيقة وفحص المطابقة `LedgerIntegrityChecker`.
  - حماية التوافق الخلفي للـ JSON والتخزين الذري عبر `.tmp` و`.corrupt`.
  - **الحالة:** تم واختُبر آلياً ✅ (اختبارات الترحيل والتسلسل في StorageSerializationTest).
- [x] **المحور C (C1 - C3): شيت المراجعة، المحلل الذكي، وشاشة المستحقات:**
  - `ExpenseReviewSheet`: بطاقات مراجعة تفاعلية متعددة، مسودة التخصيصات، إعادة التحليل، وإرفاق الإيصالات.
  - `LocalDialectExpenseParser`: استخراج الدفعات المتعددة، التخصيصات، وتجاهل وحدات الوزن (30 سيناريو لهجات).
  - `ReceivablesScreen`: إدارة المستحقات، بطاقة تسوية جزئية وكلية، إلغاء التسديد (Void) دون حذف صامت.
  - بطاقة ملخص المستحقات `ReceivablesSummaryCard` في الشاشة الرئيسية مع عداد متحرك.
  - **الحالة:** تم واختُبر آلياً ✅ (واجهات المستخدم فحص يدوي على جهاز).
- [x] **المحور C4: التقارير المحاسبية المتقدمة وتسمية التصدير (Accounting Reports & Export Namer):**
  - تقارير كشوف الحساب مع رصيد تراكمي متحرك يستبعد التسديدات الملغاة.
  - تصنيف أعمار الديون (Aging Brackets).
  - مصنف XLSX متكامل بخمس صفحات (الملخص، المستحقات، التسديدات، كشوف الحساب، الأعمار).
  - تسمية مانعة للتصادم `ExportNamer` وتطهير الأسماء اللاتينية.
  - **الحالة:** تم واختُبر آلياً ✅.
- [x] **المحور D: التلميع الحركي وصقل الواجهة (Motion Polish & UnSlop):**
  - عداد الأرقام المتدحرجة `RollingNumberTicker` بـ `FastOutSlowInEasing`.
  - زر الميكروفون الموحد بدرجة EmeraldGreen ورسم السعة داخل طور `drawBehind`.
  - رسم الإنفاق الأسبوعي التفاعلي `WeeklySpendingChart` مع سحب تفاعلي يدعم RTL وتلميح عائم مصمت.
  - **الحالة:** تم واختُبر آلياً ✅ (الحركة واللمس فحص يدوي على جهاز).
