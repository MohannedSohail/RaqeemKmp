package org.mohtech.app.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.mohtech.app.core.theme.RaqeemColors

/**
 * Raqeem (رقيم) Line Iconography System
 * Strictly compliant with Unslop Design Rule 3:
 * - Zero Emojis in UI Chrome.
 * - Consistent 1.5 - 2.0 dp stroke width across all glyphs.
 * - Crisp, geometric, fintech aesthetics.
 */
object RaqeemIcons {

    @Composable
    fun Keyboard(
        modifier: Modifier = Modifier,
        size: Dp = 18.dp,
        tint: Color = RaqeemColors.TextSecondary,
        strokeWidth: Dp = 1.75.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val sw = strokeWidth.toPx()
            val w = this.size.width
            val h = this.size.height

            // Keyboard body outline
            drawRoundRect(
                color = tint,
                topLeft = Offset(sw, h * 0.2f),
                size = Size(w - 2 * sw, h * 0.6f),
                cornerRadius = CornerRadius(w * 0.12f, w * 0.12f),
                style = Stroke(width = sw)
            )

            // Keys (geometric dots / dashes)
            val rowY1 = h * 0.40f
            val rowY2 = h * 0.58f
            val stepX = w * 0.18f

            for (i in 0..3) {
                drawCircle(
                    color = tint,
                    radius = sw * 0.7f,
                    center = Offset(w * 0.23f + i * stepX, rowY1)
                )
            }
            // Spacebar line
            drawLine(
                color = tint,
                start = Offset(w * 0.32f, rowY2),
                end = Offset(w * 0.68f, rowY2),
                strokeWidth = sw,
                cap = StrokeCap.Round
            )
        }
    }

    @Composable
    fun Close(
        modifier: Modifier = Modifier,
        size: Dp = 18.dp,
        tint: Color = RaqeemColors.TextSecondary,
        strokeWidth: Dp = 1.75.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val sw = strokeWidth.toPx()
            val pad = this.size.width * 0.25f
            val w = this.size.width
            val h = this.size.height

            drawLine(
                color = tint,
                start = Offset(pad, pad),
                end = Offset(w - pad, h - pad),
                strokeWidth = sw,
                cap = StrokeCap.Round
            )
            drawLine(
                color = tint,
                start = Offset(w - pad, pad),
                end = Offset(pad, h - pad),
                strokeWidth = sw,
                cap = StrokeCap.Round
            )
        }
    }

    @Composable
    fun Trash(
        modifier: Modifier = Modifier,
        size: Dp = 18.dp,
        tint: Color = RaqeemColors.CoralRose,
        strokeWidth: Dp = 1.75.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val sw = strokeWidth.toPx()
            val w = this.size.width
            val h = this.size.height

            // Can outline
            val path = Path().apply {
                moveTo(w * 0.25f, h * 0.32f)
                lineTo(w * 0.30f, h * 0.88f)
                lineTo(w * 0.70f, h * 0.88f)
                lineTo(w * 0.75f, h * 0.32f)
            }
            drawPath(path, color = tint, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))

            // Lid line
            drawLine(
                color = tint,
                start = Offset(w * 0.16f, h * 0.32f),
                end = Offset(w * 0.84f, h * 0.32f),
                strokeWidth = sw,
                cap = StrokeCap.Round
            )

            // Handle
            val handlePath = Path().apply {
                moveTo(w * 0.38f, h * 0.32f)
                lineTo(w * 0.38f, h * 0.20f)
                lineTo(w * 0.62f, h * 0.20f)
                lineTo(w * 0.62f, h * 0.32f)
            }
            drawPath(handlePath, color = tint, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))

            // Inner ribs
            drawLine(
                color = tint.copy(alpha = 0.7f),
                start = Offset(w * 0.42f, h * 0.42f),
                end = Offset(w * 0.42f, h * 0.76f),
                strokeWidth = sw * 0.8f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = tint.copy(alpha = 0.7f),
                start = Offset(w * 0.58f, h * 0.42f),
                end = Offset(w * 0.58f, h * 0.76f),
                strokeWidth = sw * 0.8f,
                cap = StrokeCap.Round
            )
        }
    }

    @Composable
    fun MicWave(
        modifier: Modifier = Modifier,
        size: Dp = 24.dp,
        tint: Color = RaqeemColors.EmeraldGreen,
        strokeWidth: Dp = 2.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val sw = strokeWidth.toPx()
            val w = this.size.width
            val h = this.size.height

            // Center capsule
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.36f, h * 0.15f),
                size = Size(w * 0.28f, h * 0.46f),
                cornerRadius = CornerRadius(w * 0.14f, w * 0.14f),
                style = Stroke(width = sw)
            )

            // Bottom cradle
            drawArc(
                color = tint,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(w * 0.24f, h * 0.32f),
                size = Size(w * 0.52f, h * 0.36f),
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )

            // Stem
            drawLine(
                color = tint,
                start = Offset(w * 0.5f, h * 0.68f),
                end = Offset(w * 0.5f, h * 0.84f),
                strokeWidth = sw,
                cap = StrokeCap.Round
            )

            // Base line
            drawLine(
                color = tint,
                start = Offset(w * 0.34f, h * 0.84f),
                end = Offset(w * 0.66f, h * 0.84f),
                strokeWidth = sw,
                cap = StrokeCap.Round
            )
        }
    }

    @Composable
    fun Users(
        modifier: Modifier = Modifier,
        size: Dp = 20.dp,
        tint: Color = RaqeemColors.ElectricCyan,
        strokeWidth: Dp = 1.75.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val sw = strokeWidth.toPx()
            val w = this.size.width
            val h = this.size.height

            // Main head
            drawCircle(
                color = tint,
                radius = w * 0.18f,
                center = Offset(w * 0.42f, h * 0.30f),
                style = Stroke(width = sw)
            )

            // Main shoulder arc
            drawArc(
                color = tint,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(w * 0.16f, h * 0.54f),
                size = Size(w * 0.52f, h * 0.36f),
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )

            // Secondary shoulder arc behind
            drawArc(
                color = tint.copy(alpha = 0.6f),
                startAngle = 240f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = Offset(w * 0.46f, h * 0.48f),
                size = Size(w * 0.42f, h * 0.34f),
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )
        }
    }

    @Composable
    fun Shield(
        modifier: Modifier = Modifier,
        size: Dp = 20.dp,
        tint: Color = RaqeemColors.EmeraldGreen,
        strokeWidth: Dp = 1.75.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val sw = strokeWidth.toPx()
            val w = this.size.width
            val h = this.size.height

            val path = Path().apply {
                moveTo(w * 0.5f, h * 0.12f)
                lineTo(w * 0.84f, h * 0.24f)
                lineTo(w * 0.84f, h * 0.56f)
                cubicTo(w * 0.84f, h * 0.76f, w * 0.5f, h * 0.90f, w * 0.5f, h * 0.90f)
                cubicTo(w * 0.5f, h * 0.90f, w * 0.16f, h * 0.76f, w * 0.16f, h * 0.56f)
                lineTo(w * 0.16f, h * 0.24f)
                close()
            }
            drawPath(path, color = tint, style = Stroke(width = sw, join = StrokeJoin.Round))

            // Inner checkmark
            val checkPath = Path().apply {
                moveTo(w * 0.36f, h * 0.50f)
                lineTo(w * 0.46f, h * 0.60f)
                lineTo(w * 0.64f, h * 0.40f)
            }
            drawPath(checkPath, color = tint, style = Stroke(width = sw * 0.9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    @Composable
    fun Check(
        modifier: Modifier = Modifier,
        size: Dp = 18.dp,
        tint: Color = RaqeemColors.BackgroundSlate,
        strokeWidth: Dp = 2.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val sw = strokeWidth.toPx()
            val w = this.size.width
            val h = this.size.height

            val checkPath = Path().apply {
                moveTo(w * 0.24f, h * 0.50f)
                lineTo(w * 0.44f, h * 0.72f)
                lineTo(w * 0.78f, h * 0.30f)
            }
            drawPath(checkPath, color = tint, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    @Composable
    fun Export(
        modifier: Modifier = Modifier,
        size: Dp = 18.dp,
        tint: Color = RaqeemColors.TextSecondary,
        strokeWidth: Dp = 1.75.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val sw = strokeWidth.toPx()
            val w = this.size.width
            val h = this.size.height

            // Tray / box bottom
            val trayPath = Path().apply {
                moveTo(w * 0.20f, h * 0.55f)
                lineTo(w * 0.20f, h * 0.85f)
                lineTo(w * 0.80f, h * 0.85f)
                lineTo(w * 0.80f, h * 0.55f)
            }
            drawPath(trayPath, color = tint, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))

            // Arrow pointing up
            drawLine(
                color = tint,
                start = Offset(w * 0.5f, h * 0.65f),
                end = Offset(w * 0.5f, h * 0.18f),
                strokeWidth = sw,
                cap = StrokeCap.Round
            )
            // Arrow head
            val arrowHead = Path().apply {
                moveTo(w * 0.32f, h * 0.34f)
                lineTo(w * 0.5f, h * 0.16f)
                lineTo(w * 0.68f, h * 0.34f)
            }
            drawPath(arrowHead, color = tint, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    @Composable
    fun Edit(
        modifier: Modifier = Modifier,
        size: Dp = 16.dp,
        tint: Color = RaqeemColors.ElectricCyan,
        strokeWidth: Dp = 1.75.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val sw = strokeWidth.toPx()
            val w = this.size.width
            val h = this.size.height

            val path = Path().apply {
                moveTo(w * 0.20f, h * 0.80f)
                lineTo(w * 0.22f, h * 0.65f)
                lineTo(w * 0.68f, h * 0.19f)
                lineTo(w * 0.81f, h * 0.32f)
                lineTo(w * 0.35f, h * 0.78f)
                close()
            }
            drawPath(path, color = tint, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))

            drawLine(
                color = tint,
                start = Offset(w * 0.20f, h * 0.80f),
                end = Offset(w * 0.16f, h * 0.84f),
                strokeWidth = sw,
                cap = StrokeCap.Round
            )
        }
    }

    @Composable
    fun Home(
        modifier: Modifier = Modifier,
        size: Dp = 20.dp,
        tint: Color = RaqeemColors.TextSecondary,
        strokeWidth: Dp = 1.75.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val sw = strokeWidth.toPx()
            val w = this.size.width
            val h = this.size.height

            // Roof and walls
            val housePath = Path().apply {
                moveTo(w * 0.18f, h * 0.45f)
                lineTo(w * 0.50f, h * 0.18f)
                lineTo(w * 0.82f, h * 0.45f)
                lineTo(w * 0.82f, h * 0.84f)
                lineTo(w * 0.18f, h * 0.84f)
                close()
            }
            drawPath(housePath, color = tint, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))

            // Doorway
            val doorPath = Path().apply {
                moveTo(w * 0.40f, h * 0.84f)
                lineTo(w * 0.40f, h * 0.58f)
                lineTo(w * 0.60f, h * 0.58f)
                lineTo(w * 0.60f, h * 0.84f)
            }
            drawPath(doorPath, color = tint, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    @Composable
    fun List(
        modifier: Modifier = Modifier,
        size: Dp = 20.dp,
        tint: Color = RaqeemColors.TextSecondary,
        strokeWidth: Dp = 1.75.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val sw = strokeWidth.toPx()
            val w = this.size.width
            val h = this.size.height

            val y1 = h * 0.28f
            val y2 = h * 0.50f
            val y3 = h * 0.72f

            // Bullet dots
            drawCircle(color = tint, radius = sw * 0.75f, center = Offset(w * 0.22f, y1))
            drawCircle(color = tint, radius = sw * 0.75f, center = Offset(w * 0.22f, y2))
            drawCircle(color = tint, radius = sw * 0.75f, center = Offset(w * 0.22f, y3))

            // Lines
            drawLine(color = tint, start = Offset(w * 0.36f, y1), end = Offset(w * 0.82f, y1), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(color = tint, start = Offset(w * 0.36f, y2), end = Offset(w * 0.82f, y2), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(color = tint, start = Offset(w * 0.36f, y3), end = Offset(w * 0.72f, y3), strokeWidth = sw, cap = StrokeCap.Round)
        }
    }

    @Composable
    fun Chart(
        modifier: Modifier = Modifier,
        size: Dp = 20.dp,
        tint: Color = RaqeemColors.TextSecondary,
        strokeWidth: Dp = 1.75.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val sw = strokeWidth.toPx()
            val w = this.size.width
            val h = this.size.height

            // 3 bars
            val bWidth = w * 0.16f
            val bottomY = h * 0.82f

            // Bar 1 (short)
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.18f, h * 0.52f),
                size = Size(bWidth, bottomY - h * 0.52f),
                cornerRadius = CornerRadius(w * 0.04f, w * 0.04f),
                style = Stroke(width = sw)
            )

            // Bar 2 (tall)
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.42f, h * 0.22f),
                size = Size(bWidth, bottomY - h * 0.22f),
                cornerRadius = CornerRadius(w * 0.04f, w * 0.04f),
                style = Stroke(width = sw)
            )

            // Bar 3 (medium)
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.66f, h * 0.38f),
                size = Size(bWidth, bottomY - h * 0.38f),
                cornerRadius = CornerRadius(w * 0.04f, w * 0.04f),
                style = Stroke(width = sw)
            )
        }
    }

    @Composable
    fun Settings(
        modifier: Modifier = Modifier,
        size: Dp = 20.dp,
        tint: Color = RaqeemColors.TextSecondary,
        strokeWidth: Dp = 1.75.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val sw = strokeWidth.toPx()
            val w = this.size.width
            val h = this.size.height

            // Sliders / Controls aesthetic
            val y1 = h * 0.32f
            val y2 = h * 0.68f

            drawLine(color = tint, start = Offset(w * 0.18f, y1), end = Offset(w * 0.82f, y1), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(color = tint, start = Offset(w * 0.18f, y2), end = Offset(w * 0.82f, y2), strokeWidth = sw, cap = StrokeCap.Round)

            // Sliders knobs
            drawCircle(color = tint, radius = w * 0.12f, center = Offset(w * 0.40f, y1), style = Stroke(width = sw))
            drawCircle(color = tint, radius = w * 0.12f, center = Offset(w * 0.64f, y2), style = Stroke(width = sw))
        }
    }

    @Composable
    fun Repeat(
        modifier: Modifier = Modifier,
        size: Dp = 20.dp,
        tint: Color = RaqeemColors.EmeraldGreen,
        strokeWidth: Dp = 1.75.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val sw = strokeWidth.toPx()
            val w = this.size.width
            val h = this.size.height

            // Top arc
            drawArc(
                color = tint,
                startAngle = 190f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(w * 0.18f, h * 0.18f),
                size = Size(w * 0.64f, h * 0.64f),
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )

            // Bottom arc
            drawArc(
                color = tint,
                startAngle = 10f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(w * 0.18f, h * 0.18f),
                size = Size(w * 0.64f, h * 0.64f),
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )

            // Top arrow head
            val arrow1 = Path().apply {
                moveTo(w * 0.76f, h * 0.32f)
                lineTo(w * 0.86f, h * 0.36f)
                lineTo(w * 0.82f, h * 0.48f)
            }
            drawPath(arrow1, color = tint, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))

            // Bottom arrow head
            val arrow2 = Path().apply {
                moveTo(w * 0.24f, h * 0.68f)
                lineTo(w * 0.14f, h * 0.64f)
                lineTo(w * 0.18f, h * 0.52f)
            }
            drawPath(arrow2, color = tint, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    @Composable
    fun Plus(
        modifier: Modifier = Modifier,
        size: Dp = 18.dp,
        tint: Color = RaqeemColors.EmeraldGreen,
        strokeWidth: Dp = 1.75.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val sw = strokeWidth.toPx()
            val w = this.size.width
            val h = this.size.height

            drawLine(
                color = tint,
                start = Offset(w * 0.5f, h * 0.22f),
                end = Offset(w * 0.5f, h * 0.78f),
                strokeWidth = sw,
                cap = StrokeCap.Round
            )
            drawLine(
                color = tint,
                start = Offset(w * 0.22f, h * 0.5f),
                end = Offset(w * 0.78f, h * 0.5f),
                strokeWidth = sw,
                cap = StrokeCap.Round
            )
        }
    }

    @Composable
    fun Refresh(
        modifier: Modifier = Modifier,
        size: Dp = 16.dp,
        tint: Color = RaqeemColors.ElectricCyan,
        strokeWidth: Dp = 1.75.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val sw = strokeWidth.toPx()
            val w = this.size.width
            val h = this.size.height

            drawArc(
                color = tint,
                startAngle = 45f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(w * 0.18f, h * 0.18f),
                size = Size(w * 0.64f, h * 0.64f),
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )

            // Arrow head
            val arrow = Path().apply {
                moveTo(w * 0.70f, h * 0.15f)
                lineTo(w * 0.85f, h * 0.28f)
                lineTo(w * 0.68f, h * 0.38f)
            }
            drawPath(arrow, color = tint, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    @Composable
    fun Receipt(
        modifier: Modifier = Modifier,
        size: Dp = 18.dp,
        tint: Color = RaqeemColors.TextSecondary,
        strokeWidth: Dp = 1.75.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val sw = strokeWidth.toPx()
            val w = this.size.width
            val h = this.size.height

            val receiptPath = Path().apply {
                moveTo(w * 0.22f, h * 0.15f)
                lineTo(w * 0.78f, h * 0.15f)
                lineTo(w * 0.78f, h * 0.85f)
                lineTo(w * 0.68f, h * 0.80f)
                lineTo(w * 0.58f, h * 0.85f)
                lineTo(w * 0.48f, h * 0.80f)
                lineTo(w * 0.38f, h * 0.85f)
                lineTo(w * 0.28f, h * 0.80f)
                lineTo(w * 0.22f, h * 0.85f)
                close()
            }
            drawPath(receiptPath, color = tint, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))

            // Text lines on receipt
            drawLine(color = tint.copy(alpha = 0.7f), start = Offset(w * 0.34f, h * 0.34f), end = Offset(w * 0.66f, h * 0.34f), strokeWidth = sw * 0.8f, cap = StrokeCap.Round)
            drawLine(color = tint.copy(alpha = 0.7f), start = Offset(w * 0.34f, h * 0.48f), end = Offset(w * 0.66f, h * 0.48f), strokeWidth = sw * 0.8f, cap = StrokeCap.Round)
            drawLine(color = tint.copy(alpha = 0.7f), start = Offset(w * 0.34f, h * 0.62f), end = Offset(w * 0.52f, h * 0.62f), strokeWidth = sw * 0.8f, cap = StrokeCap.Round)
        }
    }

    @Composable
    fun Back(
        modifier: Modifier = Modifier,
        size: Dp = 18.dp,
        tint: Color = RaqeemColors.TextPrimary,
        strokeWidth: Dp = 1.75.dp
    ) {
        Canvas(modifier = modifier.size(size)) {
            val sw = strokeWidth.toPx()
            val w = this.size.width
            val h = this.size.height

            // In RTL context, back arrow points rightward
            drawLine(
                color = tint,
                start = Offset(w * 0.25f, h * 0.5f),
                end = Offset(w * 0.75f, h * 0.5f),
                strokeWidth = sw,
                cap = StrokeCap.Round
            )
            val head = Path().apply {
                moveTo(w * 0.55f, h * 0.30f)
                lineTo(w * 0.75f, h * 0.50f)
                lineTo(w * 0.55f, h * 0.70f)
            }
            drawPath(head, color = tint, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    @Composable
    fun Share(
        modifier: Modifier = Modifier,
        size: Dp = 18.dp,
        tint: Color = RaqeemColors.EmeraldGreen,
        strokeWidth: Dp = 1.75.dp
    ) {
        Export(modifier = modifier, size = size, tint = tint, strokeWidth = strokeWidth)
    }
}

