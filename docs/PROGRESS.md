# 📊 جدول تقدم الترقية المحاسبية الشاملة (docs/PROGRESS.md)

تحديث تراكمي لحالة كافة بنود «التحقق النهائي» وفق متطلبات الترقية المحاسبية الشاملة (Axes B, A, C1-C3, C4, D) لتطبيق **رقيم (RaqeemKmp)**.

---

## 📈 الحالة التراكمية لبنود التحقق النهائي

| الخانة / البند | المحور | الحالة | الدليل | التقرير المرتبط |
|---|---|---|---|---|
| فحص الحالة الراهنة وتسميات المستودع | 00 | تم واختُبر آلياً | مطابقة الأسماء الفعلية في الكود | [stage-00-audit](file:///e:/AI%20Projects/RaqeemKmp/docs/reports/stage-00-audit-20261124-133000.md) |
| مهلة ديناميكية للمعالجة الصوتية (5s-15s) | B | تم واختُبر آلياً | `ExpenseViewModel.kt` حساب المهلة ديناميكياً | [stage-01-axis-b](file:///e:/AI%20Projects/RaqeemKmp/docs/reports/stage-01-axis-b-20261124-133500.md) |
| تحرير الميكروفون الحتمي في onCleared | B | تم واختُبر آلياً | `audioRecorder.release()` في `onCleared()` | [stage-01-axis-b](file:///e:/AI%20Projects/RaqeemKmp/docs/reports/stage-01-axis-b-20261124-133500.md) |
| مسار السقوط الآمن للشيت اليدوي الهادئ | B | تم واختُبر آلياً | `showFallbackManualSheet = true` عند أي تعثر | [stage-01-axis-b](file:///e:/AI%20Projects/RaqeemKmp/docs/reports/stage-01-axis-b-20261124-133500.md) |
| التوافق الخلفي لمخطط JSON القديم | A | تم واختُبر آلياً | `StorageSerializationTest.testLegacyJsonSchemaMigrationWithoutDataLoss` | [stage-02-axis-a](file:///e:/AI%20Projects/RaqeemKmp/docs/reports/stage-02-axis-a-20261124-134000.md) |
| اعتماد effectiveAmount للمجاميع | A | تم واختُبر آلياً | `effectiveAmount = myShare ?: amount` في كافة الحسابات | [stage-02-axis-a](file:///e:/AI%20Projects/RaqeemKmp/docs/reports/stage-02-axis-a-20261124-134000.md) |
| نماذج التخصيصات والتسديدات والتدقيق | A | تم واختُبر آلياً | `Allocation`, `Settlement`, `AuditRecord` | [stage-02-axis-a](file:///e:/AI%20Projects/RaqeemKmp/docs/reports/stage-02-axis-a-20261124-134000.md) |
| حسابات وتقريب العملات وتوزيع الفروق | A | تم واختُبر آلياً | `Money.kt` و `distributeSplitEvenly` | [stage-02-axis-a](file:///e:/AI%20Projects/RaqeemKmp/docs/reports/stage-02-axis-a-20261124-134000.md) |
| دفتر الأستاذ الموحد وفحص النزاهة | A | تم واختُبر آلياً | `ReceivablesLedger.kt` و `LedgerIntegrityChecker.verify()` | [stage-02-axis-a](file:///e:/AI%20Projects/RaqeemKmp/docs/reports/stage-02-axis-a-20261124-134000.md) |
| التخزين الذري وحماية الملف التالف | A | تم واختُبر آلياً | الكتابة لـ `.tmp` ونقل التالف لـ `.corrupt` بختم زمني | [stage-02-axis-a](file:///e:/AI%20Projects/RaqeemKmp/docs/reports/stage-02-axis-a-20261124-134000.md) |
| شيت المراجعة للدفعات المتعددة | C | تم وبانتظار اختبار يدوي | بطاقات مراجعة مستقلة في `ExpenseReviewSheet.kt` | [stage-03-axis-c](file:///e:/AI%20Projects/RaqeemKmp/docs/reports/stage-03-axis-c-20261124-134500.md) |
| إرفاق صور الإيصالات في المراجعة | C | تم وبانتظار اختبار يدوي | `ReceiptImagePicker.kt` و `receiptPhotoPath` | [stage-03-axis-c](file:///e:/AI%20Projects/RaqeemKmp/docs/reports/stage-03-axis-c-20261124-134500.md) |
| زر إعادة التحليل من النص الخام | C | تم وبانتظار اختبار يدوي | زر إعادة التحليل وتحديث البطاقات تلقائياً | [stage-03-axis-c](file:///e:/AI%20Projects/RaqeemKmp/docs/reports/stage-03-axis-c-20261124-134500.md) |
| استخراج التخصيصات والدفعات المتعددة | C | تم واختُبر آلياً | `LocalDialectExpenseParser.kt` واجتياز 30 سيناريو | [stage-03-axis-c](file:///e:/AI%20Projects/RaqeemKmp/docs/reports/stage-03-axis-c-20261124-134500.md) |
| شاشة المستحقات وإدارة الذمم | C | تم وبانتظار اختبار يدوي | `ReceivablesScreen.kt` كاملة بقوائمها وتفاصيلها | [stage-03-axis-c](file:///e:/AI%20Projects/RaqeemKmp/docs/reports/stage-03-axis-c-20261124-134500.md) |
| التسديد الجزئي والكلي وإلغاء التسديد | C | تم وبانتظار اختبار يدوي | تسجيل تسديد، وإلغاء بـ void دون حذف صامت | [stage-03-axis-c](file:///e:/AI%20Projects/RaqeemKmp/docs/reports/stage-03-axis-c-20261124-134500.md) |
| بطاقة ملخص المستحقات بالرئيسية | C | تم وبانتظار اختبار يدوي | `ReceivablesSummaryCard` مع عداد متحرك | [stage-03-axis-c](file:///e:/AI%20Projects/RaqeemKmp/docs/reports/stage-03-axis-c-20261124-134500.md) |
| كشف الحساب التفصيلي مع رصيد متحرك | C4 | تم واختُبر آلياً | `AccountingReportUtil.generateStatement` | [stage-04-axis-c4](file:///e:/AI%20Projects/RaqeemKmp/docs/reports/stage-04-axis-c4-20261124-135000.md) |
| تصنيف أعمار الديون (Aging Brackets) | C4 | تم واختُبر آلياً | `AccountingReportUtil.classifyAging` الفئات الأربع | [stage-04-axis-c4](file:///e:/AI%20Projects/RaqeemKmp/docs/reports/stage-04-axis-c4-20261124-135000.md) |
| تصدير XLSX بخمس صفحات ومعادلات SUM | C4 | تم واختُبر آلياً | `XlsxWriter.writeReceivablesWorkbook` | [stage-04-axis-c4](file:///e:/AI%20Projects/RaqeemKmp/docs/reports/stage-04-axis-c4-20261124-135000.md) |
| تسمية التصدير المانعة للتصادم وتنظيف الكاش | C4 | تم واختُبر آلياً | `ExportNamer.kt` و `PlatformReportBridge.*` | [stage-04-axis-c4](file:///e:/AI%20Projects/RaqeemKmp/docs/reports/stage-04-axis-c4-20261124-135000.md) |
| عداد الأرقام المتدحرجة الرأسي Ticker | D | تم وبانتظار اختبار يدوي | `RollingNumberTicker.kt` في الرادار والمستحقات | [stage-05-axis-d](file:///e:/AI%20Projects/RaqeemKmp/docs/reports/stage-05-axis-d-20261124-135500.md) |
| زر التسجيل الموحد وقراءة السعة بالرسم | D | تم وبانتظار اختبار يدوي | `VoiceRecordButton.kt` بلون EmeraldGreen داخل drawBehind | [stage-05-axis-d](file:///e:/AI%20Projects/RaqeemKmp/docs/reports/stage-05-axis-d-20261124-135500.md) |
| رسم بياني تفاعلي بسحب يدعم RTL | D | تم وبانتظار اختبار يدوي | `WeeklySpendingChart.kt` بعكس الإحداثيات وتلميح مصمت | [stage-05-axis-d](file:///e:/AI%20Projects/RaqeemKmp/docs/reports/stage-05-axis-d-20261124-135500.md) |
| حزمة Release AAB بحجم مثالي | الكل | تم واختُبر آلياً | بناء ناجح بحجم 35.7MB (< 200MB) | [stage-06-final](file:///e:/AI%20Projects/RaqeemKmp/docs/reports/stage-06-final-20261124-140000.md) |
