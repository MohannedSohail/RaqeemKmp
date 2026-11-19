# 🪙 رقيم (Raqeem) — ذكاء المصاريف الصوتي العربي

**رقيم (Raqeem)** هو تطبيق مصاريف شخصية ومشتركة، عربي أصيل، صوتي أولاً (Voice-First)، ويعمل بنسبة **100% دون إنترنت (Offline-First)** مبني بأحدث تقنيات **Compose Multiplatform (CMP)**.

---

## ✨ المميزات المعمارية والتقنية

1. **تحويل صوتي محلي بالكامل (100% On-Device Whisper.cpp):**
   - محرك تفريغ صوتي أصلي مبني من المصدر عبر **CMake & Android NDK** (`libwhisper.so` لمعمارية `arm64-v8a`) باستخدام whisper.cpp كموديول فرعي (`v1.7.4`).
   - نموذج مدمج ومكمّم `ggml-tiny-q5_1.bin` (32MB) في الأصول دون أي تنزيل شبكي وقت التشغيل.
   - كاش لسياق النموذج في الذاكرة لتسريع الاستجابة المتتالية مع تحرير كامل للموارد.
   - حزمة Release AAB للمتجر بحجم **~35.3 MB** (أقل بكثير من سقف 200MB لـ Google Play).
2. **شاشة معالجة صوتية احترافية (`VoiceProcessingOverlay`):**
   - تسلسل مراحل MVI واضح: `FINALIZING_AUDIO` ➔ `TRANSCRIBING` ➔ `UNDERSTANDING` ➔ `READY`.
   - عارض موجات صوتية هادئ، ومؤشر تقدم ثلاثي المراحل، وشيمر هيكلي RTL، وزر إلغاء فوري.
3. **حصانة كاملة ضد تدوير الشاشة (Screen Rotation Survival):**
   - حفظ وجهة التنقل وحالة الترحيب بـ `AppNavigationState` ومخصص `Saver`.
   - الاحتفاظ بـ `ExpenseViewModel` عبر تغير التهيئة بواسطة `androidx.lifecycle.viewmodel.compose.viewModel`.
   - تعريف `android:configChanges` في المانيفست.
   - تحويل حقول الإدخال والشرائح المنبثقة إلى `rememberSaveable`.
4. **واجهات مستخدم وفق معايير /unslop:**
   - ثيم بدء `Theme.Raqeem.Starting` عبر `androidx.core:core-splashscreen` لمنع الوميض الأبيض عند الإقلاع.
   - خط **Cairo** المدمج الرسمي عبر كافة مكونات الطباعة.
   - صفر إيموجي في الـ Chrome (أيقونات خطية هندسية مخصصة `RaqeemIcons`).
   - شاشة ترحيب Onboarding تفاعلية من 4 صفحات تعمل بمحرك اللهجات العربي الفعلي `LocalDialectExpenseParser` وتتيح ضبط العملة والميزانية الأسبوعية وربطها برادار الميزانية.
5. **صفر سحابة وخصوصية مطلقة:**
   - المانيفست خالٍ تماماً من أذونات `INTERNET` أو `ACCESS_NETWORK_STATE`.
   - تخزين محلي مباشر عبر `kotlinx.serialization` بصيغة JSON.
6. **هيكل تنقل متعدد التبويبات (MainShell & 4 Tabs):**
   - 4 تبويبات متخصصة (`HOME`, `HISTORY`, `REPORTS`, `SETTINGS`) مع شريط سفلي UnSlop وNavigationRail للأفقي مع حفظ الحالة عبر التدوير وزر رجوع.
7. **كشف النشاط الصوتي والمصحح اللاحق (VAD & Transcript Normalizer):**
   - VAD نقي بـ Kotlin في `commonMain` يقتطع الصمت ويرفض التسجيلات الفارغة لمنع الهلوسة.
   - مصحح صوتي سياقي للأخطاء الشائعة وحماية الأسماء ودعم ضرب الآلاف والكسور المركبة.
8. **تصدير احترافي للتقارير (Excel و PDF حقيقيان):**
   - كاتب XLSX نقي بـ Kotlin دون مكتبات خارجية (ZIP/XML مع صيغ `=SUM` ودعم اتجاه RTL).
   - كاتب PDF أصلي بـ Android Canvas مع BiDi ودعم كامل للخط العربي.
   - مشاركة وحفظ محلي عبر Android FileProvider و Storage Access Framework (SAF).

---

## 🏗️ هيكلية المشروع

* `shared/`: كود Kotlin Multiplatform المشترك:
  * `commonMain/`: نماذج البيانات، محلل اللهجات `LocalDialectExpenseParser`، عقود MVI، الواجهات المشتركة (Splash, Onboarding, Dashboard, Sheets).
  * `androidMain/`: ربط محرك الصوت الخام `AudioRecord`، ومحرك `AndroidLocalSpeechTranscriber` عبر JNI، والتخزين المحلي.
  * `jvmTest/`: اختبارات وحدوية وتكاملية واختبارات التطهير وتدوير الشاشة ودورة حياة النموذج.
* `androidApp/`: تطبيق أندرويد:
  * `src/main/cpp/`: ملفات CMakeLists.txt و jni.cpp و whisper.cpp (Git submodule v1.7.4).
  * `src/main/assets/models/ggml-tiny-q5_1.bin`: نموذج GGML المكمم (31MB).
  * `proguard-rules.pro`: قواعد R8 لحفظ دوال JNI.
* `desktopApp/`: تطبيق سطح المكتب (JVM).
* `iosApp/`: تطبيق iOS.

---

## 🛠️ إعداد النموذج للمطورين (Model Setup)

قبل بناء التطبيق للمرة الأولى على بيئة تطوير جديدة أو CI، تأكد من تهيئة الموديول الفرعي وتنزيل ملف النموذج:

```powershell
# 1. تهيئة الـ Submodule
git submodule update --init --recursive

# 2. تنزيل نموذج whisper.cpp المكمم (31MB)
mkdir -p androidApp/src/main/assets/models
curl.exe -L -o "androidApp/src/main/assets/models/ggml-tiny-q5_1.bin" "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny-q5_1.bin"

# 3. التحقق من تطابق الـ Checksum (SHA-256)
Get-FileHash -Algorithm SHA256 "androidApp/src/main/assets/models/ggml-tiny-q5_1.bin"
# Expected Hash: 818710568DA3CA15689E31A743197B520007872FF9576237BDA97BD1B469C3D7
```

---

## 🚀 أوامر البناء والاختبار (Build & Verification)

### 1. تشغيل الاختبارات الشاملة:
```powershell
.\gradlew.bat :shared:jvmTest
```

### 2. بناء حزمة أندرويد التجريبية (Debug APK):
```powershell
.\gradlew.bat :androidApp:assembleDebug
```

### 3. بناء النسخة النهائية لـ Google Play (Release AAB):
```powershell
.\gradlew.bat :androidApp:bundleRelease
```
*يتم بناء مكتبة C++ الأصلية عبر CMake تلقائياً وحفظ ملف AAB النهائي في:*
`androidApp/build/outputs/bundle/release/androidApp-release.aab`
*(الحجم: **~35.3 MB** مع R8 Minification و ProGuard).*
