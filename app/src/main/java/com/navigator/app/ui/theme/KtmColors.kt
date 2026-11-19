package com.navigator.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Neutral and status tokens (design §3.1), dark and light. The accent is separate ([AccentScheme]) so
 * any accent the rider picks sits on the same neutrals. Roles are named like the design table; each
 * maps onto a Material 3 role in [ktmColorScheme].
 */
data class KtmColors(
    val isDark: Boolean,
    // Backgrounds and containers
    val bg: Color,
    val surf: Color,
    val surf2: Color,
    val surf3: Color,
    // Lines
    val line: Color,
    val line2: Color,
    // Text: primary / secondary / subtle
    val fg: Color,
    val fg2: Color,
    val fg3: Color,
    // Status. Each has a container variant and a content colour for a solid fill.
    val ok: Color,
    val okContainer: Color,
    val warn: Color,
    val warnContainer: Color,
    val bad: Color,
    val badContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val onStatus: Color,
    // Snackbars (M3 inverse roles)
    val inv: Color,
    val onInv: Color,
    // Lines we draw on browse and preview maps
    val routeSelected: Color,
    val routeSelectedCase: Color,
    val routeAlternate: Color,
)

val KtmDarkColors = KtmColors(
    isDark = true,
    bg = Color(0xFF0B0C0E), surf = Color(0xFF15171A), surf2 = Color(0xFF1D2024), surf3 = Color(0xFF262A2F),
    line = Color(0xFF2A2E34), line2 = Color(0xFF41464E),
    fg = Color(0xFFF2F3F5), fg2 = Color(0xFFB3B9C2), fg3 = Color(0xFF8C929B),
    ok = Color(0xFF3DD68C), okContainer = Color(0xFF0F2E1F),
    warn = Color(0xFFFFB224), warnContainer = Color(0xFF3A2A08),
    bad = Color(0xFFFF5A4E), badContainer = Color(0xFF3B1513),
    info = Color(0xFF6AA8FF), infoContainer = Color(0xFF162B4D),
    onStatus = Color(0xFF0B0C0E),
    inv = Color(0xFFE9EAEE), onInv = Color(0xFF15171B),
    routeSelected = Color(0xFF4C8DF6), routeSelectedCase = Color(0xFF1A56C4), routeAlternate = Color(0xFF5E7591),
)

val KtmLightColors = KtmColors(
    isDark = false,
    bg = Color(0xFFF4F4F6), surf = Color(0xFFFFFFFF), surf2 = Color(0xFFF0F0F3), surf3 = Color(0xFFE7E7EB),
    line = Color(0xFFE2E3E7), line2 = Color(0xFFC3C6CD),
    fg = Color(0xFF15171B), fg2 = Color(0xFF4A4F57), fg3 = Color(0xFF6B7079),
    ok = Color(0xFF1B8A4E), okContainer = Color(0xFFDDF3E6),
    warn = Color(0xFF9A5B00), warnContainer = Color(0xFFFFF0D2),
    bad = Color(0xFFD92D20), badContainer = Color(0xFFFDE3E0),
    info = Color(0xFF1A63D6), infoContainer = Color(0xFFDCE8FB),
    onStatus = Color(0xFFFFFFFF),
    inv = Color(0xFF2B2E33), onInv = Color(0xFFF2F3F5),
    routeSelected = Color(0xFF1A73E8), routeSelectedCase = Color(0xFF0D4FB0), routeAlternate = Color(0xFF8FA4BF),
)

val LocalKtmColors = staticCompositionLocalOf { KtmDarkColors }

/**
 * A complete Material 3 scheme: every role is set, so no default (purple) colour can leak into a
 * component. Primary is the true accent fill, because Material buttons and switches paint with it;
 * accent *text* must use [AccentScheme.text] instead (orange on white fails contrast).
 */
fun ktmColorScheme(c: KtmColors, a: AccentScheme): ColorScheme {
    val scrim = Color(0xFF000000)
    return if (c.isDark) {
        darkColorScheme(
            primary = a.fill, onPrimary = a.onFill, primaryContainer = a.container, onPrimaryContainer = a.onContainer,
            inversePrimary = a.inverseAccent,
            secondary = c.fg2, onSecondary = c.surf, secondaryContainer = c.surf3, onSecondaryContainer = c.fg,
            tertiary = c.info, onTertiary = c.onStatus, tertiaryContainer = c.infoContainer, onTertiaryContainer = c.fg,
            background = c.bg, onBackground = c.fg,
            surface = c.surf, onSurface = c.fg, surfaceVariant = c.surf2, onSurfaceVariant = c.fg2,
            surfaceTint = a.fill,
            inverseSurface = c.inv, inverseOnSurface = c.onInv,
            error = c.bad, onError = c.onStatus, errorContainer = c.badContainer, onErrorContainer = c.fg,
            outline = c.line2, outlineVariant = c.line, scrim = scrim,
            surfaceBright = c.surf3, surfaceDim = c.bg,
            surfaceContainerLowest = c.bg, surfaceContainerLow = c.surf, surfaceContainer = c.surf2,
            surfaceContainerHigh = c.surf3, surfaceContainerHighest = c.surf3,
            primaryFixed = a.fill, primaryFixedDim = a.text, onPrimaryFixed = a.onFill, onPrimaryFixedVariant = a.onFill,
            secondaryFixed = c.surf3, secondaryFixedDim = c.line2, onSecondaryFixed = c.fg, onSecondaryFixedVariant = c.fg2,
            tertiaryFixed = c.infoContainer, tertiaryFixedDim = c.info, onTertiaryFixed = c.fg, onTertiaryFixedVariant = c.fg2,
        )
    } else {
        lightColorScheme(
            primary = a.fill, onPrimary = a.onFill, primaryContainer = a.container, onPrimaryContainer = a.onContainer,
            inversePrimary = a.inverseAccent,
            secondary = c.fg2, onSecondary = c.surf, secondaryContainer = c.surf3, onSecondaryContainer = c.fg,
            tertiary = c.info, onTertiary = c.onStatus, tertiaryContainer = c.infoContainer, onTertiaryContainer = c.fg,
            background = c.bg, onBackground = c.fg,
            surface = c.surf, onSurface = c.fg, surfaceVariant = c.surf2, onSurfaceVariant = c.fg2,
            surfaceTint = a.fill,
            inverseSurface = c.inv, inverseOnSurface = c.onInv,
            error = c.bad, onError = c.onStatus, errorContainer = c.badContainer, onErrorContainer = c.fg,
            outline = c.line2, outlineVariant = c.line, scrim = scrim,
            surfaceBright = c.surf, surfaceDim = c.surf3,
            surfaceContainerLowest = c.surf, surfaceContainerLow = c.bg, surfaceContainer = c.surf2,
            surfaceContainerHigh = c.surf3, surfaceContainerHighest = c.surf3,
            primaryFixed = a.fill, primaryFixedDim = a.text, onPrimaryFixed = a.onFill, onPrimaryFixedVariant = a.onFill,
            secondaryFixed = c.surf3, secondaryFixedDim = c.line2, onSecondaryFixed = c.fg, onSecondaryFixedVariant = c.fg2,
            tertiaryFixed = c.infoContainer, tertiaryFixedDim = c.info, onTertiaryFixed = c.fg, onTertiaryFixedVariant = c.fg2,
        )
    }
}
