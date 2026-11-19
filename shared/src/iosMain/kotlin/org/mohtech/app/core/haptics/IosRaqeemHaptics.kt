package org.mohtech.app.core.haptics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

actual fun createRaqeemHaptics(context: Any?): RaqeemHaptics = NoOpRaqeemHaptics

@Composable
actual fun rememberRaqeemHaptics(): RaqeemHaptics = remember { NoOpRaqeemHaptics }
