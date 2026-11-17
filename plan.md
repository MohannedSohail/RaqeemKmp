# 🗺️ Raqeem (رقيم) - Master Implementation Plan (plan.md)

## 📌 الإصدار 2.1: خطة تنفيذ Whisper الأوفلاين 100% (Spec Kit SDLC)

تتبع هذه الخطة منهجية التطوير الموجه بالمواصفات (TDD / Spec-Kit)، بتسلسل صارم: **Red (فشل الاختبارات أولاً) ➔ Green (كتابة الكود لتمرير الاختبارات) ➔ Refactor & Audit (التنقيح والتدقيق)**.
تم تشغيل الأوامر الفعلية وتوثيق مخرجاتها في `review.md`.

---

## 🏗️ 0. حسم قرار ربط Whisper JNI مع نظام البناء (Build & JNI Decision)

* **القرار المعماري المعتمد:**
  بناء مكتبات `libwhisper.so` و`libwhisper_jni.so` لمعمارية `arm64-v8a` ووضعها في `androidApp/src/main/jniLibs/arm64-v8a/` مع واجهات JNI محكمة في `shared/src/androidMain/`.
* **السبب:**
  يضمن التوافق المطلق مع Gradle وAmper دون الاعتماد على مسارات NDK الخارجية في بيئة البناء، ويمنع أي تعطل أثناء تجميع التطبيق (`assembleDebug`).
* **إثبات الجدوى (PoC):**
  التحقق من قدرة مكتبة JNI وغلاف Kotlin على تحميل النموذج من `filesDir` ومعالجة مصفوفة `ShortArray` صوتية وإرجاع نص عربي.

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
  - تنفيذ `.\gradlew.bat :shared:jvmTest` — 126 اختباراً ناجحاً بنسبة 100% (0 فشل).
- [x] **بناء حزمة أندرويد النهائية:**
  - تنفيذ `.\gradlew.bat :androidApp:assembleDebug` — بناء نظيف ناجح 100%.
- [x] **فحص التطهير التام بالأوامر (Sanitization Grep):**
  - فحص خلو الكود والمانيفست من `INTERNET` و `android.speech`.
- [x] **تحديث وثائق المشروع (README.md و review.md) (P11):**
  - توثيق مصفوفة العيوب P1-P11 كاملة في `review.md`.
