# 🗺️ Raqeem (رقيم) - Master Implementation Plan (plan.md)

This actionable roadmap details the step-by-step engineering execution under the **Spec-Kit SDLC Protocol**, specifying tasks, files, testing commands, and verification criteria.

---

## 📅 Roadmap Overview

```text
Milestone 1: Spec-Kit Artifacts & CI/CD Pipeline (AGENTS.md, intent.md, spec.md, plan.md, .github/workflows)
Milestone 2: KMP Core Dependencies & Design Tokens (Cairo font, Theme, Arabic RTL setup)
Milestone 3: Domain Models & TDD Test Suites (DialectParserTest, StorageSerializationTest, MviViewModelTest)
Milestone 4: Pure Kotlin Local Arabic Dialect Parser (LocalDialectExpenseParser implementation)
Milestone 5: Offline Storage & Preferences (AppPreferences expect/actual, PlatformExpenseStorage)
Milestone 6: MVI ViewModel & State Machine (ExpenseViewModel, Intent handling, Effect channel)
Milestone 7: Arabic RTL Compose Multiplatform UI (Dashboard, VoiceButton, Radar, ReviewSheet)
Milestone 8: Platform Hardware Bindings (Android MediaRecorder / SpeechRecognizer, iOS, Desktop)
Milestone 9: Quality Audit & Verification (RTL a11y, Zero Memory Leaks, Final Test Suite)
```

---

## 📍 Phase-by-Phase Execution Details

### Milestone 1: Spec-Kit Artifacts & CI/CD Pipeline
* **Lead Agent:** `@apex-orchestrator` & `@kmp-engineer`
* **Artifacts:**
  - `AGENTS.md` (Swarm definition)
  - `intent.md` (Product vision & KPIs)
  - `spec.md` (Domain contracts & MVI schemas)
  - `plan.md` (This master plan)
  - `.github/workflows/ci.yml` (GitHub Actions CI for Gradle KMP validation)
* **Verification:**
  ```powershell
  git status
  ```

### Milestone 2: KMP Dependencies & Cairo RTL Theme
* **Lead Agent:** `@ux-designer` & `@kmp-engineer`
* **Tasks:**
  - Update `gradle/libs.versions.toml` with `kotlinx-datetime`, `kotlinx-serialization-json`.
  - Add Cairo Arabic fonts to `shared/src/commonMain/composeResources/font/`.
  - Build `RaqeemTheme`, `RaqeemColors`, `RaqeemTypography`, enforcing `LayoutDirection.Rtl`.
* **Files:**
  - `gradle/libs.versions.toml`
  - `shared/build.gradle.kts`
  - `shared/src/commonMain/kotlin/core/theme/Color.kt`
  - `shared/src/commonMain/kotlin/core/theme/Typography.kt`
  - `shared/src/commonMain/kotlin/core/theme/Theme.kt`
* **Verification:**
  ```powershell
  ./gradlew :shared:assemble
  ```

### Milestone 3: Domain Contracts & TDD Unit Tests
* **Lead Agent:** `@qa-tester` & `@nlp-specialist`
* **Tasks:**
  - Create domain models (`ExpenseItem`, `ExpenseBatch`, `ExpenseCategory`, `Payer`, `ProofType`).
  - Write `DialectParserTest` in `shared/src/commonTest/kotlin/` with 15+ Arabic colloquial test cases BEFORE writing parser implementation.
  - Write `StorageSerializationTest` in `shared/src/commonTest/kotlin/`.
* **Files:**
  - `shared/src/commonMain/kotlin/data/model/ExpenseModels.kt`
  - `shared/src/commonTest/kotlin/ai/nlp/DialectParserTest.kt`
  - `shared/src/commonTest/kotlin/data/StorageSerializationTest.kt`
* **Verification (Tests MUST fail before Milestone 4):**
  ```powershell
  ./gradlew :shared:test
  ```

### Milestone 4: Offline Arabic NLP Engine (`LocalDialectExpenseParser`)
* **Lead Agent:** `@nlp-specialist`
* **Tasks:**
  - Implement full offline regex and token matching for Arabic digits (`١، ٢، ٣`) and numbers ("نص", "ربع", "خمسة", "عشرين", "مية", "ألف").
  - Implement payer detection ("أنا دفعت", "دفع محمد", "على حساب أحمد").
  - Implement shared expense calculation ("بيني وبين أحمد", "النص بالنص").
  - Implement smart category dictionary matching.
  - Implement Bank SMS transaction extraction.
* **Files:**
  - `shared/src/commonMain/kotlin/ai/nlp/LocalDialectExpenseParser.kt`
  - `shared/src/commonMain/kotlin/ai/nlp/ArabicNumberNormalizer.kt`
  - `shared/src/commonMain/kotlin/ai/nlp/BankSmsParser.kt`
* **Verification (All TDD tests MUST pass):**
  ```powershell
  ./gradlew :shared:test --tests "DialectParserTest"
  ```

### Milestone 5: Offline Persistence & Preferences
* **Lead Agent:** `@db-architect`
* **Tasks:**
  - Define `AppPreferences` and `PlatformExpenseStorage` `expect/actual` interfaces.
  - Implement Android actuals using `SharedPreferences` and private app storage.
  - Implement Desktop JVM actuals using home directory JSON store.
  - Implement `ExpenseRepository` and `LocalExpenseRepository`.
* **Files:**
  - `shared/src/commonMain/kotlin/data/storage/AppPreferences.kt`
  - `shared/src/commonMain/kotlin/data/storage/PlatformExpenseStorage.kt`
  - `shared/src/commonMain/kotlin/data/repository/ExpenseRepository.kt`
  - `shared/src/androidMain/kotlin/data/storage/AppPreferences.android.kt`
  - `shared/src/jvmMain/kotlin/data/storage/AppPreferences.jvm.kt`
* **Verification:**
  ```powershell
  ./gradlew :shared:test --tests "StorageSerializationTest"
  ```

### Milestone 6: MVI ViewModel & State Engine
* **Lead Agent:** `@kmp-engineer` & `@qa-tester`
* **Tasks:**
  - Implement `ExpenseViewModel` with coroutine scope.
  - Wire `ExpenseIntent` handlers (recording, parsing, saving, deleting).
  - Provide `StateFlow<ExpenseUiState>` and `SharedFlow<ExpenseEffect>`.
  - Author and run `ExpenseViewModelTest` to verify state transitions.
* **Files:**
  - `shared/src/commonMain/kotlin/feature/dashboard/ExpenseViewModel.kt`
  - `shared/src/commonMain/kotlin/feature/dashboard/ExpenseContract.kt`
  - `shared/src/commonTest/kotlin/feature/dashboard/ExpenseViewModelTest.kt`
* **Verification:**
  ```powershell
  ./gradlew :shared:test --tests "ExpenseViewModelTest"
  ```

### Milestone 7: Arabic RTL Compose Multiplatform UI
* **Lead Agent:** `@ux-designer`
* **Tasks:**
  - Decompose dashboard into modular composables:
    - `VoiceRecordButton` with pulsing dynamic amplitude ring.
    - `BudgetRadarCard` with smooth progress bar and dynamic currency symbol.
    - `ExpenseHistoryList` with category color badges and RTL alignment.
    - `ExpenseReviewSheet` showing parsed amounts, multi-payer split chip selector, and confirm button.
    - `PoliteFallbackSheet` offering Gboard dictation and manual quick chips.
* **Files:**
  - `shared/src/commonMain/kotlin/feature/dashboard/ui/DashboardScreen.kt`
  - `shared/src/commonMain/kotlin/feature/dashboard/ui/VoiceRecordButton.kt`
  - `shared/src/commonMain/kotlin/feature/dashboard/ui/BudgetRadarCard.kt`
  - `shared/src/commonMain/kotlin/feature/dashboard/ui/ExpenseReviewSheet.kt`
  - `shared/src/commonMain/kotlin/feature/dashboard/ui/PoliteFallbackSheet.kt`
* **Verification:**
  ```powershell
  ./gradlew :desktopApp:run --no-daemon
  ```

### Milestone 8: Android Hardware Integration & Audio Defense
* **Lead Agent:** `@security-perf` & `@kmp-engineer`
* **Tasks:**
  - Implement Android `SpeechRecognizer` + `MediaRecorder` dual fallback.
  - Implement runtime permission checks for `RECORD_AUDIO`.
  - Ensure immediate release of microphone hardware in `onCleared()`.
* **Files:**
  - `androidApp/src/main/AndroidManifest.xml`
  - `androidApp/src/main/kotlin/org/mohtech/app/MainActivity.kt`
  - `shared/src/androidMain/kotlin/ai/audio/AndroidAudioRecorder.kt`
* **Verification:**
  ```powershell
  ./gradlew :androidApp:assembleDebug
  ```

### Milestone 9: Audit, Benchmarks & Spec-Kit Review
* **Lead Agent:** `@security-perf` & `@apex-orchestrator`
* **Tasks:**
  - Contrast and touch target audit ($\ge 48\text{dp}$, WCAG AAA).
  - Verify zero memory leaks on microphone release.
  - Merge branch into `master` via pull request protocol.
  - Generate final audit report.
