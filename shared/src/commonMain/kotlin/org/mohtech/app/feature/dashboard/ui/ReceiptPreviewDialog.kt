package org.mohtech.app.feature.dashboard.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.mohtech.app.core.theme.RaqeemColors
import org.mohtech.app.core.ui.RaqeemIcons
import org.mohtech.app.feature.receipts.PlatformReceiptStorage

private sealed interface ReceiptPreviewState {
    data object Loading : ReceiptPreviewState
    data class Loaded(val bitmap: ImageBitmap) : ReceiptPreviewState
    data object Unavailable : ReceiptPreviewState
}

/**
 * Compact receipt thumbnail backed by [PlatformReceiptStorage.loadReceiptThumbnail],
 * falling back cleanly to [RaqeemIcons.Receipt] if decoding fails or file is missing.
 */
@Composable
fun ReceiptThumbnailBox(
    fileUri: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = fileUri) {
        value = PlatformReceiptStorage.loadReceiptThumbnail(fileUri, maxDimensionPx = 256)
    }

    Box(
        modifier = modifier
            .size(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(RaqeemColors.SurfaceCard)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = RaqeemColors.ElectricCyan),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded,
                contentDescription = "صورة مصغرة للإيصال",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            RaqeemIcons.Receipt(size = 20.dp, tint = RaqeemColors.ElectricCyan)
        }
    }
}

/**
 * Full-screen offline receipt preview lightbox with 1dp 8%-alpha hairline framing,
 * >= 48dp accessible touch targets, and optional remove callback.
 */
@Composable
fun ReceiptPreviewDialog(
    fileUri: String,
    onDismiss: () -> Unit,
    onRemove: (() -> Unit)? = null
) {
    val previewState by produceState<ReceiptPreviewState>(initialValue = ReceiptPreviewState.Loading, key1 = fileUri) {
        val decoded = PlatformReceiptStorage.loadReceiptThumbnail(fileUri, maxDimensionPx = 1280)
        value = if (decoded != null) {
            ReceiptPreviewState.Loaded(decoded)
        } else {
            ReceiptPreviewState.Unavailable
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(RaqeemColors.SurfaceCard)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                    .clickable(enabled = false) {}
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        RaqeemIcons.Receipt(size = 18.dp, tint = RaqeemColors.ElectricCyan)
                        Text(
                            text = "معاينة الإيصال",
                            color = RaqeemColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onRemove != null) {
                            Row(
                                modifier = Modifier
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(RaqeemColors.CoralRose.copy(alpha = 0.14f))
                                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                                    .clickable {
                                        onRemove()
                                        onDismiss()
                                    }
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                RaqeemIcons.Trash(size = 15.dp, tint = RaqeemColors.CoralRose)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "إزالة",
                                    color = RaqeemColors.CoralRose,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(RaqeemColors.SurfaceElevated)
                                .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = true, color = RaqeemColors.TextPrimary),
                                    onClick = onDismiss
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            RaqeemIcons.Close(size = 16.dp, tint = RaqeemColors.TextPrimary)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp, max = 460.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(RaqeemColors.BackgroundSlate)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    when (val current = previewState) {
                        is ReceiptPreviewState.Loading -> {
                            CircularProgressIndicator(
                                color = RaqeemColors.EmeraldGreen,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        is ReceiptPreviewState.Loaded -> {
                            Image(
                                bitmap = current.bitmap,
                                contentDescription = "صورة الإيصال المرفق",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            )
                        }
                        is ReceiptPreviewState.Unavailable -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(24.dp)
                            ) {
                                RaqeemIcons.Receipt(size = 32.dp, tint = RaqeemColors.TextTertiary)
                                Text(
                                    text = "تعذر تحميل صورة الإيصال",
                                    color = RaqeemColors.TextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

