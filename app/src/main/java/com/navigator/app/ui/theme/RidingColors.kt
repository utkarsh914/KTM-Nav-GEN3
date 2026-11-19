package com.navigator.app.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Colours of riding surfaces (maneuver card, trip bar, chips, trip sheet). They have a fixed meaning
 * and switch with Day / Night only: never with the app theme or the rider's accent, so a green
 * accent can't be confused with guidance.
 */
data class RidingColors(
    val night: Boolean,
    val guidance: Color,
    val guidanceBand: Color,
    val onGuidance: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val line: Color,
    val overspeed: Color,
    val onOverspeed: Color,
    val warnChip: Color,
    val onWarnChip: Color,
)

val RidingDay = RidingColors(
    night = false,
    guidance = Color(0xFF0B6E59), guidanceBand = Color(0xFF085846), onGuidance = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF), surfaceHigh = Color(0xFFEDEFF2),
    onSurface = Color(0xFF111316), onSurfaceVariant = Color(0xFF4A4F57), line = Color(0xFFE3E5E9),
    overspeed = Color(0xFFE5362B), onOverspeed = Color(0xFFFFFFFF),
    warnChip = Color(0xFFFFB224), onWarnChip = Color(0xFF111316),
)

/** Night: a darker green to cut glare, and a surface that is deliberately not pure black. */
val RidingNight = RidingColors(
    night = true,
    guidance = Color(0xFF0A5A4A), guidanceBand = Color(0xFF07443A), onGuidance = Color(0xFFFFFFFF),
    surface = Color(0xFF16191C), surfaceHigh = Color(0xFF262A2F),
    onSurface = Color(0xFFF2F3F5), onSurfaceVariant = Color(0xFFA8AFB8), line = Color(0xFF2E3238),
    overspeed = Color(0xFFE5362B), onOverspeed = Color(0xFFFFFFFF),
    warnChip = Color(0xFFFFB224), onWarnChip = Color(0xFF111316),
)

val LocalRidingColors = staticCompositionLocalOf { RidingDay }

/** Riding text stops growing past this font scale, so the maneuver card and trip bar can't overflow. */
const val RIDING_MAX_FONT_SCALE = 1.15f

/**
 * Wrap riding surfaces only. [night] comes from the Day/Night rule, not from the app theme.
 * Material components inside use the standard (not expressive) motion scheme, so nothing bounces.
 * The font-scale clamp covers the whole subtree: keep scrolling body content (lists) outside it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RidingTheme(night: Boolean, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    // A linear clamp: with Android 14+ non-linear font scaling it can differ slightly from the system curve.
    val clamped = remember(density) { Density(density.density, minOf(density.fontScale, RIDING_MAX_FONT_SCALE)) }
    CompositionLocalProvider(
        LocalRidingColors provides (if (night) RidingNight else RidingDay),
        LocalDensity provides clamped,
    ) {
        MaterialExpressiveTheme(motionScheme = MotionScheme.standard(), content = content)
    }
}
