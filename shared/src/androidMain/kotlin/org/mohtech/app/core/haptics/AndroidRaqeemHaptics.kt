package org.mohtech.app.core.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import org.mohtech.app.data.storage.AndroidContextHolder

class AndroidRaqeemHaptics(
    private val context: Context,
    private val viewProvider: (() -> View?)? = null
) : RaqeemHaptics {

    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Throwable) {
            null
        }
    }

    override fun trigger(type: HapticFeedbackType) {
        runCatching {
            val vib = vibrator
            if (vib != null && vib.hasVibrator()) {
                vibrateWithVibrator(vib, type)
            } else {
                fallbackToViewHaptic(type)
            }
        }.onFailure {
            // Guarantee crash-free fallback
            runCatching { fallbackToViewHaptic(type) }
        }
    }

    private fun vibrateWithVibrator(vib: Vibrator, type: HapticFeedbackType) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = when (type) {
                HapticFeedbackType.SubtleTick -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                    } else {
                        VibrationEffect.createOneShot(10, 80)
                    }
                }
                HapticFeedbackType.LightTick -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    } else {
                        VibrationEffect.createOneShot(14, 110)
                    }
                }
                HapticFeedbackType.ProcessingStep -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                    } else {
                        VibrationEffect.createOneShot(12, 90)
                    }
                }
                HapticFeedbackType.SuccessImpact -> {
                    // Double pulse impact: 20ms tap, 40ms silence, 35ms authoritative confirmation
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 20, 40, 35),
                        intArrayOf(0, 140, 0, 255),
                        -1
                    )
                }
                HapticFeedbackType.WarningAlert -> {
                    // Double warning buzz: 35ms buzz, 45ms silence, 35ms buzz
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 35, 45, 35),
                        intArrayOf(0, 200, 0, 200),
                        -1
                    )
                }
            }
            vib.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            when (type) {
                HapticFeedbackType.SubtleTick -> vib.vibrate(10)
                HapticFeedbackType.LightTick -> vib.vibrate(15)
                HapticFeedbackType.ProcessingStep -> vib.vibrate(12)
                HapticFeedbackType.SuccessImpact -> vib.vibrate(longArrayOf(0, 20, 40, 35), -1)
                HapticFeedbackType.WarningAlert -> vib.vibrate(longArrayOf(0, 35, 45, 35), -1)
            }
        }
    }

    private fun fallbackToViewHaptic(type: HapticFeedbackType) {
        val view = viewProvider?.invoke() ?: return
        when (type) {
            HapticFeedbackType.SubtleTick, HapticFeedbackType.LightTick, HapticFeedbackType.ProcessingStep -> {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            HapticFeedbackType.SuccessImpact -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                } else {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            }
            HapticFeedbackType.WarningAlert -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                } else {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            }
        }
    }
}

actual fun createRaqeemHaptics(context: Any?): RaqeemHaptics {
    val ctx = (context as? Context) ?: AndroidContextHolder.context
        ?: return NoOpRaqeemHaptics
    return AndroidRaqeemHaptics(ctx.applicationContext)
}

@Composable
actual fun rememberRaqeemHaptics(): RaqeemHaptics {
    val context = LocalContext.current
    val view = LocalView.current
    return remember(context, view) {
        AndroidRaqeemHaptics(context.applicationContext) { view }
    }
}
