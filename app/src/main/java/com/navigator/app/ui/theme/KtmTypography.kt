package com.navigator.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The five roles beyond Material's 15 slots (design §3.2). Riding roles scale with the font setting
 * up to [RIDING_MAX_FONT_SCALE] (see [RidingTheme]).
 */
data class KtmTypeExtras(
    /** 48 dp buttons, dialog and snackbar actions, 72-84 dp tile buttons. */
    val buttonCompact: TextStyle,
    /** Riding chips, the "Then" band, the exit badge. */
    val ridingLabel: TextStyle,
    /** Riding instruction in the landscape panel. */
    val ridingInstructionLand: TextStyle,
    /** Trip-bar second line. */
    val ridingSupport: TextStyle,
    /** Logs, MAC addresses, hex and protocol codes only. */
    val mono: TextStyle,
)

private const val TABULAR = "tnum"

private fun condensedStyle(family: FontFamily, size: Int, line: Int) = TextStyle(
    fontFamily = family, fontWeight = FontWeight.Bold, fontSize = size.sp, lineHeight = line.sp,
    letterSpacing = 0.sp, fontFeatureSettings = TABULAR,
)

private fun plainStyle(family: FontFamily, weight: FontWeight, size: Int, line: Int) = TextStyle(
    fontFamily = family, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = 0.sp,
)

/**
 * Barlow Condensed 700 carries numbers, headings and buttons; Barlow carries everything read.
 * Families come from [AppFonts], so System font mode keeps every size and weight in the phone's font.
 */
fun ktmTypography(
    condensed: FontFamily = BarlowCondensed,
    barlow: FontFamily = Barlow,
): Typography = Typography(
    displayLarge = condensedStyle(condensed, 64, 64),
    displayMedium = condensedStyle(condensed, 46, 48),
    displaySmall = condensedStyle(condensed, 36, 40),
    headlineLarge = condensedStyle(condensed, 30, 36),
    headlineMedium = condensedStyle(condensed, 28, 32),
    headlineSmall = condensedStyle(condensed, 24, 28),
    titleLarge = condensedStyle(condensed, 19, 24),
    titleMedium = plainStyle(barlow, FontWeight.Medium, 20, 26),
    titleSmall = plainStyle(barlow, FontWeight.SemiBold, 14, 20),
    bodyLarge = plainStyle(barlow, FontWeight.Medium, 16, 24),
    bodyMedium = plainStyle(barlow, FontWeight.Normal, 15, 22),
    bodySmall = plainStyle(barlow, FontWeight.Normal, 14, 20),
    labelLarge = condensedStyle(condensed, 18, 22),
    labelMedium = plainStyle(barlow, FontWeight.SemiBold, 15, 20),
    labelSmall = plainStyle(barlow, FontWeight.Medium, 13, 18),
)

fun ktmTypeExtras(
    condensed: FontFamily = BarlowCondensed,
    barlow: FontFamily = Barlow,
    mono: FontFamily = JetBrainsMono,
) = KtmTypeExtras(
    buttonCompact = condensedStyle(condensed, 16, 20),
    ridingLabel = plainStyle(barlow, FontWeight.SemiBold, 17, 22),
    ridingInstructionLand = plainStyle(barlow, FontWeight.Medium, 22, 28),
    ridingSupport = plainStyle(barlow, FontWeight.Medium, 17, 22),
    mono = plainStyle(mono, FontWeight.Normal, 13, 18),
)

val LocalKtmTypeExtras = staticCompositionLocalOf { ktmTypeExtras() }
