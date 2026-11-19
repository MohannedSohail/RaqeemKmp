package org.mohtech.app.core.ui

import androidx.compose.runtime.Composable

/**
 * Platform-aware BackHandler.
 * On Android, attaches to the system OnBackPressedDispatcher.
 * On desktop (JVM) and iOS, safe no-op.
 */
@Composable
expect fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)

