package org.mohtech.app.core.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import raqeemkmp.shared.generated.resources.Res
import raqeemkmp.shared.generated.resources.cairo

/**
 * Raqeem (رقيم) Arabic Typography with Cairo Font Family
 * Crafted according to Unslop Design principles:
 * - Generous line-height for Arabic diacritics
 * - Restrained weights (Normal 400, Medium 500, SemiBold 600)
 * - Clear typographic scale for instant readability
 */
@Composable
fun getCairoFontFamily(): FontFamily {
    return FontFamily(
        Font(Res.font.cairo, FontWeight.Normal),
        Font(Res.font.cairo, FontWeight.Medium),
        Font(Res.font.cairo, FontWeight.SemiBold),
        Font(Res.font.cairo, FontWeight.Bold)
    )
}

fun createRaqeemTypography(cairoFont: FontFamily): Typography {
    return Typography(
        displayLarge = TextStyle(
            fontFamily = cairoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            lineHeight = 44.sp,
            color = RaqeemColors.TextPrimary
        ),
        displayMedium = TextStyle(
            fontFamily = cairoFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 26.sp,
            lineHeight = 36.sp,
            color = RaqeemColors.TextPrimary
        ),
        headlineMedium = TextStyle(
            fontFamily = cairoFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 30.sp,
            color = RaqeemColors.TextPrimary
        ),
        titleLarge = TextStyle(
            fontFamily = cairoFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            lineHeight = 24.sp,
            color = RaqeemColors.TextPrimary
        ),
        titleMedium = TextStyle(
            fontFamily = cairoFont,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = RaqeemColors.TextPrimary
        ),
        bodyLarge = TextStyle(
            fontFamily = cairoFont,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            color = RaqeemColors.TextSecondary
        ),
        bodyMedium = TextStyle(
            fontFamily = cairoFont,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = RaqeemColors.TextSecondary
        ),
        labelLarge = TextStyle(
            fontFamily = cairoFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = RaqeemColors.TextPrimary
        ),
        labelSmall = TextStyle(
            fontFamily = cairoFont,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = RaqeemColors.TextTertiary
        )
    )
}

// Fallback typography using system font when font resource context is outside Composable
val RaqeemTypography = createRaqeemTypography(FontFamily.Default)
