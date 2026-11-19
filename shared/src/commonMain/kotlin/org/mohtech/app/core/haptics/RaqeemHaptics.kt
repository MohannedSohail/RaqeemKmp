package org.mohtech.app.core.haptics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Semantic tactile feedback types for Raqeem (رقيم).
 */
enum class HapticFeedbackType {
    /** Crisp, subtle tick on audio record button press */
    SubtleTick,

    /** Multi-tier progressive haptic pulse during voice processing overlay steps */
    ProcessingStep,

    /** Rich, authoritative success confirmation impact when saving an expense batch */
    SuccessImpact,

    /** Light tactile tick on chart bar tap and category chip selection */
    LightTick,

    /** Warning alert buzz when crossing the swipe-to-delete threshold */
    WarningAlert
}

enum class HapticKind {
    TICK,
    CONFIRM,
    REJECT,
    HEAVY
}

/**
 * Platform-agnostic contract for tactile haptic feedback.
 */
interface RaqeemHaptics {
    fun trigger(type: HapticFeedbackType)

    fun perform(kind: HapticKind) {
        when (kind) {
            HapticKind.TICK -> trigger(HapticFeedbackType.SubtleTick)
            HapticKind.CONFIRM -> trigger(HapticFeedbackType.SuccessImpact)
            HapticKind.REJECT -> trigger(HapticFeedbackType.WarningAlert)
            HapticKind.HEAVY -> trigger(HapticFeedbackType.WarningAlert)
        }
    }

    fun tick() = perform(HapticKind.TICK)
    fun confirm() = perform(HapticKind.CONFIRM)
    fun reject() = perform(HapticKind.REJECT)
    fun heavy() = perform(HapticKind.HEAVY)
}

/**
 * Safe no-op implementation for non-supported platforms or tests.
 */
object NoOpRaqeemHaptics : RaqeemHaptics {
    override fun trigger(type: HapticFeedbackType) {
        // Safe no-op
    }
}

/**
 * CompositionLocal providing access to [RaqeemHaptics] across composables.
 */
val LocalRaqeemHaptics = staticCompositionLocalOf<RaqeemHaptics> {
    NoOpRaqeemHaptics
}

/**
 * Platform factory to instantiate platform-backed RaqeemHaptics.
 */
expect fun createRaqeemHaptics(context: Any? = null): RaqeemHaptics

/**
 * Composable helper to remember the platform-backed RaqeemHaptics.
 */
@Composable
expect fun rememberRaqeemHaptics(): RaqeemHaptics
