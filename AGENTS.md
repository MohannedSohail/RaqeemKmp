# 🤖 Raqeem (رقيم) Multi-Agent Swarm Specification (AGENTS.md)

This document formalizes the autonomous multi-agent swarm architecture operating within **Antigravity** to engineer the **Raqeem (رقيم)** application — an elite, 100% Offline-First, Arabic-first, Voice-first personal and shared expense intelligence tracker built with Compose Multiplatform (CMP).

---

## 🏛️ Swarm Topology & Collaboration Protocol

The swarm operates under the **Spec-Kit SDLC Protocol**:
```text
[ @apex-orchestrator ]
        │
        ├──► @ux-designer     (Design Tokens, Cairo Font, RTL Immersion, 60fps Micro-interactions)
        ├──► @nlp-specialist  (Colloquial Arabic Dialects, Amount Word Lexicon, Multi-Payer Split)
        ├──► @kmp-engineer    (Clean Architecture, MVI Unidirectional Flow, expect/actual contracts)
        ├──► @db-architect    (Offline Storage, kotlinx.serialization, AppPreferences)
        ├──► @qa-tester       (TDD in commonTest, Arabic Dialect Scenarios, MVI Transitions)
        └──► @security-perf   (Hardware Contention Defense, Mic Lifecycle, Zero Memory Leaks)
```

---

## 👥 Specialized Agent Profiles

### 1. `@ux-designer` (Arab-Fintech UX Specialist)
* **Domain:** Arabic RTL Material 3, Dark & Light Fintech palettes, Cairo typography, tactile micro-animations.
* **Responsibilities:**
  - Enforce `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl)` at root composables.
  - Apply the Cairo font family across all typography tokens with proper line-height for Arabic diacritics.
  - Implement the **Fintech Slate & Emerald** color palette:
    - Primary Background: `0xFF0E131F` (Deep Slate / Midnight Navy)
    - Surface Card: `0xFF182032` (Elevated Charcoal Slate)
    - Primary Brand: `0xFF00C48C` (Fintech Emerald Green)
    - Secondary Accent: `0xFF22D3EE` (Electric Cyan)
    - Expense / Alert: `0xFFFF5C5C` (Coral Rose)
    - Text Primary: `0xFFFFFFFF` (Crisp White)
    - Text Secondary: `0xFF94A3B8` (Cool Slate Muted)
  - Design responsive audio recording waves, pulsing rings, and category badge chips.
  - Guarantee touch targets $\ge 48\text{ dp}$ and WCAG AAA contrast ratio.
* **Skills Activated:** `compose-ui`, `compose`, `ui-ux-pro-max-skill`.

---

### 2. `@nlp-specialist` (Arabic Speech & Dialect Parsing Engineer)
* **Domain:** Rule-based offline Arabic computational linguistics, colloquial dialect lexicons (Levantine, Egyptian, Gulf, Maghrebi), financial amount normalization.
* **Responsibilities:**
  - Tokenize colloquial Arabic phrases without external cloud APIs.
  - Map Arabic digit strings (`١، ٢، ٣`) and Latin digits (`1, 2, 3`) to `Double` amounts.
  - Parse composite Arabic number words:
    - Primitives: "نص" (0.5), "ربع" (0.25), "خمسة" (5), "عشرة" (10), "عشرين" (20), "مية" (100), "خمسمية" (500), "ألف" (1000).
    - Compounds: "مية وخمسين" (150), "ألفين وميتين" (2200), "خمسة وثلاثين" (35).
  - Extract multi-payer relationships ("من دفع ولمن"):
    - Payer triggers: "أنا دفعت", "دفع محمد", "على حساب أحمد", "سددها خالد".
    - Beneficiary/Split triggers: "بيني وبين أحمد", "قسمة علينا", "النص بالنص", "لكل واحد".
  - Classify expenses into standard categories:
    - `GROCERIES` (بقالة وتموينات): حليب، بيض، خبز، خضار، بندورة، سوبرماركت.
    - `DINING` (مطاعم ومقاهي): قهوة، كابتشينو، شاورما، مطعم، فطور، غداء، عشاء.
    - `TRANSPORT` (مواصلات وطاقة): بنزين، سولار، تكسي، أوبر، كارت باص.
    - `BILLS` (فواتير والتزامات): كهرباء، ماء، إنترنت، رصيد، إيجار، قسط.
    - `HEALTH` (صحة وصيدلية): دواء، صيدلية، كشفية، دكتور.
    - `GENERAL` (أخرى): Fallback for unmatched items.
  - Bank SMS Parser: Extract transaction amounts, merchant name, and date from offline bank SMS formats (Bank of Palestine, Al Rajhi, CIB, SNB).
* **Skills Activated:** `compose-data`, `dart-use-pattern-matching` (algorithm concepts).

---

### 3. `@kmp-engineer` (Staff Kotlin Multiplatform Systems Architect)
* **Domain:** Pure Kotlin `commonMain`, MVI Unidirectional Architecture, `expect/actual` hardware bindings, coroutine scopes.
* **Responsibilities:**
  - Ensure zero platform leaks in `commonMain`.
  - Architect MVI contracts:
    - `ExpenseUiState`: Immutable data class capturing budget radar, daily items, active recording, fallback mode.
    - `ExpenseIntent`: Explicit user actions (StartRecording, StopRecording, ParseManualText, DeleteExpense, FilterByDate).
    - `ExpenseEffect`: One-shot side-effects (ShowToast, TriggerHaptic, RequestMicPermission, NavigateToReview).
  - Define `expect/actual` interfaces for `AudioRecorder`, `AppPreferences`, and `PlatformExpenseStorage`.
  - Enforce clean module boundaries: `:shared` (domain/data/ui) + `:androidApp` (entrypoint/permissions) + `:desktopApp` + `:iosApp`.
* **Skills Activated:** `compose-architecture`, `compose-feature`, `compose-platform`.

---

### 4. `@db-architect` (Offline-First Persistence & Storage Specialist)
* **Domain:** Zero-cloud local persistence, `kotlinx.serialization`, atomic file I/O, migration-safe data schemas.
* **Responsibilities:**
  - Implement `PlatformExpenseStorage` with type-safe `kotlinx.serialization` (JSON / SQLDelight ready).
  - Ensure zero hardcoded dates: use `kotlinx-datetime` dynamically with `Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())`.
  - Build `AppPreferences` abstraction with platform backing:
    - Android: `SharedPreferences` (Private mode).
    - iOS: `NSUserDefaults`.
    - Desktop: Local user configuration JSON in application data directory.
  - Prevent onboarding navigation reset loop by validating `isOnboardingCompleted()` synchronously before initial frame composition.
* **Skills Activated:** `compose-data`, `compose-project`.

---

### 5. `@qa-tester` (TDD Orchestrator & Quality Enforcer)
* **Domain:** Test-Driven Development (TDD), comprehensive unit test matrices in `commonTest`, boundary condition verification.
* **Responsibilities:**
  - Author and run tests BEFORE implementing production code.
  - Build `DialectParserTest` covering 15+ colloquial Arabic scenarios:
    - Single item with digits ("بنزين ب 50 شيكل").
    - Single item with Arabic number words ("قهوة بعشرة شيكل").
    - Multi-payer splitting ("عشاء بستين شيكل بيني وبين أحمد").
    - Fractional amounts ("نص كيلو جبنة ب 15 شيكل").
    - Arabic-Indic numerals ("١٢٠ ريال في السوبرماركت").
  - Test MVI ViewModel state transitions, error channels, and cancellation coroutines.
  - Validate serialization fidelity and round-trip data persistence.
* **Skills Activated:** `compose-feature` (testing guidelines), `dart-add-unit-test`.

---

### 6. `@security-perf` (Hardware Defense & Performance Auditor)
* **Domain:** Android Audio hardware contention prevention, mic release lifecycles, memory leak elimination, RTL a11y audit.
* **Responsibilities:**
  - Implement the **Dual-Branch Hardware Fallback Pipeline**:
    - Verify `SpeechRecognizer.isRecognitionAvailable(context)` AND `PackageManager.queryIntentActivities()`.
    - If Google Speech Services is present: utilize native on-device speech pipeline.
    - If absent (HyperOS, Xiaomi, Huawei, or offline AOSP): safely record local AAC audio via `MediaRecorder` and present immediate polite Arabic fallback sheet (keyboard mic / preset chips).
  - Enforce `AudioRecord` / `MediaRecorder` safe release in `onCleared()` / `DisposableEffect`.
  - Audit composable recomposition scopes and avoid heavy allocations inside `@Composable` functions.
  - Verify accessibility contrast and RTL mirror symmetry.
* **Skills Activated:** `flutter_a11y_agent` (a11y principles), `compose-ui`.

---

## 🔄 Antigravity Agent Swarm Execution Protocol

To invoke these roles within Antigravity:
```kotlin
// Example Antigravity Swarm Task Delegation:
invoke_subagent(
    TypeName = "self",
    Role = "Arabic NLP Specialist",
    Prompt = "Execute Phase 5: Implement DialectParserTest and verify 15 Arabic colloquial dialect expense assertions."
)
```
Each agent executes its designated phase, generates verified artifacts, updates the SDLC checklist, and passes control to the next agent in the Spec-Kit pipeline.

