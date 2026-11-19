package org.mohtech.app.feature.dashboard.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.mohtech.app.core.haptics.HapticKind
import org.mohtech.app.core.haptics.LocalRaqeemHaptics
import org.mohtech.app.core.theme.RaqeemColors
import org.mohtech.app.core.ui.RaqeemIcons
import org.mohtech.app.data.model.ExpenseCategory
import org.mohtech.app.data.model.ExpenseItem
import org.mohtech.app.feature.dashboard.CardAction

/**
 * ConfirmActionDialog:
 * Unified, calm confirmation dialog for destructive deletion and expense duplication.
 * Compliant with UnSlop guidelines:
 * - 24dp radius, hairline border @ 8% alpha.
 * - Mini expense preview card with category indicator.
 * - Gentle text with zero exclamation points.
 * - Tactile haptic feedback, spring scale entrance (0.92 -> 1.0) and animated exit before unmounting.
 */
@Composable
fun ConfirmActionDialog(
    action: CardAction?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var cachedAction by remember { mutableStateOf(action) }
    if (action != null) {
        cachedAction = action
    }

    val visibleState = remember { MutableTransitionState(false) }
    var pendingDecision by remember { mutableStateOf<(() -> Unit)?>(null) }

    val haptics = LocalRaqeemHaptics.current

    LaunchedEffect(action) {
        if (action != null) {
            visibleState.targetState = true
            haptics.perform(HapticKind.TICK)
        } else {
            visibleState.targetState = false
        }
    }

    LaunchedEffect(visibleState.currentState, visibleState.targetState) {
        if (!visibleState.currentState && !visibleState.targetState && pendingDecision != null) {
            val decision = pendingDecision
            pendingDecision = null
            decision?.invoke()
        }
    }

    fun triggerDismiss() {
        haptics.perform(HapticKind.TICK)
        pendingDecision = onDismiss
        visibleState.targetState = false
    }

    fun triggerConfirm(isDelete: Boolean) {
        if (isDelete) {
            haptics.perform(HapticKind.HEAVY)
        } else {
            haptics.perform(HapticKind.CONFIRM)
        }
        pendingDecision = onConfirm
        visibleState.targetState = false
    }

    val effectiveAction = action ?: cachedAction ?: return

    val item: ExpenseItem = when (effectiveAction) {
        is CardAction.Delete -> effectiveAction.item
        is CardAction.Repeat -> effectiveAction.item
    }

    val isDelete = effectiveAction is CardAction.Delete

    // Retain dialog window while exit animation plays
    if (action != null || visibleState.currentState || visibleState.targetState) {
        Dialog(
            onDismissRequest = { triggerDismiss() },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedVisibility(
                    visibleState = visibleState,
                    enter = scaleIn(
                        initialScale = 0.92f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(tween(200)),
                    exit = scaleOut(
                        targetScale = 0.92f,
                        animationSpec = tween(150)
                    ) + fadeOut(tween(150))
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 420.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(RaqeemColors.SurfaceCard)
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                            .padding(24.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Header Icon Seal
                            if (isDelete) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(RaqeemColors.CoralRose.copy(alpha = 0.12f))
                                        .border(1.dp, RaqeemColors.CoralRose.copy(alpha = 0.3f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    RaqeemIcons.Trash(size = 26.dp, tint = RaqeemColors.CoralRose)
                                }
                            } else {
                                var rotationAngle by remember { mutableStateOf(0f) }
                                LaunchedEffect(Unit) {
                                    rotationAngle = 360f
                                }
                                val animatedRotation by animateFloatAsState(
                                    targetValue = rotationAngle,
                                    animationSpec = tween(600, easing = FastOutSlowInEasing),
                                    label = "repeatIconRotate"
                                )
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(RaqeemColors.EmeraldGreen.copy(alpha = 0.12f))
                                        .border(1.dp, RaqeemColors.EmeraldGreen.copy(alpha = 0.3f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    RaqeemIcons.Repeat(
                                        size = 26.dp,
                                        tint = RaqeemColors.EmeraldGreen,
                                        modifier = Modifier.rotate(animatedRotation)
                                    )
                                }
                            }

                            // Dialog Title & Subtitle
                            Text(
                                text = if (isDelete) "حذف هذا المصروف؟" else "تكرار هذا المصروف؟",
                                color = RaqeemColors.TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Text(
                                text = if (isDelete) {
                                    "لن يظهر في السجل والتقارير، ويمكنك التراجع بعد الحذف مباشرة."
                                } else {
                                    "سيُسجَّل بتاريخ اليوم بنفس المبلغ والتصنيف."
                                },
                                color = RaqeemColors.TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal,
                                lineHeight = 19.sp
                            )

                            // Expense Preview Card
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(RaqeemColors.SurfaceElevated)
                                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                                    .padding(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val catColor = when (item.category) {
                                            ExpenseCategory.GROCERIES -> RaqeemColors.CatGroceries
                                            ExpenseCategory.DINING -> RaqeemColors.CatDining
                                            ExpenseCategory.TRANSPORT -> RaqeemColors.CatTransport
                                            ExpenseCategory.BILLS -> RaqeemColors.CatBills
                                            ExpenseCategory.HEALTH -> RaqeemColors.CatHealth
                                            ExpenseCategory.GENERAL -> RaqeemColors.CatGeneral
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(catColor)
                                        )
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = item.title,
                                                color = RaqeemColors.TextPrimary,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = item.category.arabicTitle,
                                                color = RaqeemColors.TextTertiary,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    val amtFormatted = if (item.amount % 1.0 == 0.0) "${item.amount.toInt()}" else "${item.amount}"
                                    Text(
                                        text = "$amtFormatted ${item.currency}",
                                        color = if (isDelete) RaqeemColors.CoralRose else RaqeemColors.EmeraldGreen,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Action Buttons Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Cancel Button
                                OutlinedButton(
                                    onClick = { triggerDismiss() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = Color.Transparent,
                                        contentColor = RaqeemColors.TextSecondary
                                    )
                                ) {
                                    Text(
                                        text = "إلغاء",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                // Confirm Button
                                Button(
                                    onClick = { triggerConfirm(isDelete) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isDelete) RaqeemColors.CoralRose else RaqeemColors.EmeraldGreen,
                                        contentColor = RaqeemColors.BackgroundSlate
                                    )
                                ) {
                                    Text(
                                        text = if (isDelete) "حذف" else "تكرار",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


