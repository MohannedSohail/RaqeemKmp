# 🎯 Raqeem (رقيم) - Project Intent & Product Vision (intent.md)

## 📌 Executive Summary
**Raqeem (رقيم)** is an elite, 100% Offline-First, Arabic-first, Voice-first personal and shared expense intelligence tracker built with **Compose Multiplatform (CMP)** targeting **Android** (with architectural expansion to iOS and Desktop).

The core thesis of Raqeem is simple: **Tracking daily expenses should not feel like data entry.** Users talk naturally in their native colloquial Arabic dialect ("دفعت خمسين شيكل بنزين", "أنا وأحمد أكلنا شاورما بستين شيكل ودفعها أحمد"), and Raqeem instantly extracts the amount, currency, category, payer, and split—completely on-device without cloud latency, cloud fees, or privacy leaks.

---

## 🛑 Problem Statement
1. **The Friction of Manual Entry:** Standard budgeting apps (Spendee, Wallet, Toshl) require 7+ taps to log a simple cup of coffee: open app, select account, enter numbers, choose category, select date, write note, save. In the rush of daily life in Cairo, Riyadh, Amman, or Gaza, users abandon these apps within 4 days.
2. **English & Western Bias:** Existing financial apps are designed with Left-to-Right (LTR) mental models, clumsy Arabic translations, rigid Western categorization, and zero support for Arabic numerals (`١، ٢، ٣`) or colloquial spoken amounts ("مية وخمسين", "عشرة شواكل").
3. **Cloud & Privacy Risks:** Users are uncomfortable uploading their personal receipts, bank notifications, and daily financial habits to external AI clouds (OpenAI, Google Cloud, Azure).
4. **Hardware Failure & Device Fragmentation:** On regional budget Android phones (Xiaomi, HyperOS, Huawei, Transsion), lack of pre-installed Google Speech Services causes voice apps to crash or stall silently.

---

## 👥 Target Audience & Personas

| Persona | Archetype | Core Needs | Voice Scenario Example |
| :--- | :--- | :--- | :--- |
| **Tariq (26)** | Daily Commuter / Driver | Fast, hands-free entry right after paying fuel or taxi | *"بنزين بخمسين شيكل"* |
| **Nour (32)** | Household Budget Manager | Weekly grocery tracking, categorizing produce & staples | *"مشتريات السوبرماركت مية وعشرين شيكل حليب وبيض وجبنة"* |
| **Omar (22)** | University Student / Flatmate | Fair expense sharing and splitting between roommates | *"عشاء بستين شيكل بيني وبين أحمد على حسابي"* |
| **Salma (29)** | Professional Shopper | Logging bank transactions immediately after SMS alert | *"سحب كاش 500 ريال صراف الراجحي"* / Bank SMS paste |

---

## 🚀 Key Value Propositions
1. **Zero-Friction Voice Logging (< 3 Seconds):**
   - Tap microphone ➔ Speak 1 sentence ➔ Instant parsed expense card with 1-tap confirmation.
2. **100% Offline Reliability:**
   - Zero network permissions required for core tracking.
   - Works in underground metros, rural roads, and during network blackouts.
3. **Colloquial Dialect Intelligence:**
   - Native recognition of Levantine (شامي), Egyptian (مصري), Gulf (خليجي), and Palestinian dialects.
   - Converts colloquial number words ("نص", "ربع", "مية وخمسين", "خمسمية", "ألفين") into precise monetary values.
4. **Smart Multi-Payer Split ("من دفع ولمن"):**
   - Automatically identifies who covered the bill and calculates per-person liabilities for shared dinners or flatmates.
5. **Bank SMS & Notification Proof (Optional):**
   - Clean, local regex-based transaction parser for incoming bank SMS (Bank of Palestine, Al Rajhi, CIB, SNB, etc.), extracting merchant, amount, and timestamp without reading sensitive credentials.

---

## 📊 Success Metrics & KPIs

| Metric | Target | Measurement Method |
| :--- | :--- | :--- |
| **Voice-to-Logged Latency** | $< 3.0\text{ seconds}$ | Time from mic button tap to rendered review card |
| **NLP Parsing Accuracy** | $\ge 95\%$ on benchmark | 15+ standard dialect test suites in `commonTest` |
| **Cold-Start Launch Time** | $< 800\text{ ms}$ on mid-range Android | Synchronous `AppPreferences` check, zero jank |
| **Offline Uptime** | $100\%$ | No network API dependencies whatsoever |
| **Crash-Free Sessions** | $\ge 99.8\%$ | Graceful fallback on devices lacking Google Speech Services |
| **Accessibility & RTL** | 100% RTL compliance | All screens verified with `LayoutDirection.Rtl`, $\ge 48\text{dp}$ touch targets |

---

## 🎨 Visual Identity & Emotional Design Tone
- **Fintech Dark Slate (`#0E131F`)**: Conveys privacy, security, and battery efficiency.
- **Emerald Green (`#00C48C`)**: Represents growth, financial balance, and positive cash flow.
- **Cairo Font Family**: Geometric, clear Arabic letterforms with balanced x-height, eliminating awkward vertical clipping of Arabic diacritics.
- **RTL Fluid Motion**: Micro-interactions originate from the right, respecting natural Arabic reading flow.
