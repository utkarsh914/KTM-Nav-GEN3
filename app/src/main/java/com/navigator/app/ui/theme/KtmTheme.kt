package com.navigator.app.ui.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

internal val LocalAccentScheme = staticCompositionLocalOf { KtmOrange.forTheme(dark = true) }

/**
 * The app theme. Feature code reads `MaterialTheme.colorScheme` / `.typography` / `.shapes` and the
 * KTM extras through [KtmTheme]: status colours, the accent's text tone, riding colours and extra
 * type roles. Day/Night for riding surfaces is separate: wrap those in [RidingTheme].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KtmTheme(
    darkTheme: Boolean,
    accent: AccentOption = accentFor(null),
    /** The app's one observer of "Remove animations"; null (previews, tests) makes the theme watch the setting itself. */
    reducedMotion: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) KtmDarkColors else KtmLightColors
    val accentScheme = accent.tones.forTheme(darkTheme)
    val colorScheme = remember(colors, accentScheme) { ktmColorScheme(colors, accentScheme) }
    // Families follow AppFonts, so a font-mode switch rebuilds the type roles live.
    val fontMode = AppFonts.mode
    val typography = remember(fontMode) { ktmTypography() }
    val typeExtras = remember(fontMode) { ktmTypeExtras() }
    val context = LocalContext.current
    val reduced = reducedMotion ?: rememberReducedMotion()
    val motion = remember(reduced) { KtmMotionSpecs(reduced = reduced) }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect { SystemBarIcons.apply(view, SystemBarIcons.resolve(darkTheme)) }
    }

    CompositionLocalProvider(
        LocalKtmColors provides colors,
        LocalAccentScheme provides accentScheme,
        LocalKtmTypeExtras provides typeExtras,
        LocalKtmMotion provides motion,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = KtmShapes.material,
            typography = typography,
        ) {
            // Un-styled Material text (text-field input, dialog buttons) stays in Barlow.
            CompositionLocalProvider(
                LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = Barlow),
                content = content,
            )
        }
    }
}

/** KTM-specific theme values, read like `MaterialTheme`. */
object KtmTheme {
    /** Neutrals and status colours. */
    val colors: KtmColors @Composable @ReadOnlyComposable get() = LocalKtmColors.current

    /** The rider's accent for the current theme: true fill, and a tone-shifted text colour. */
    val accent: AccentScheme @Composable @ReadOnlyComposable get() = LocalAccentScheme.current

    /** Only valid inside [RidingTheme]. */
    val riding: RidingColors @Composable @ReadOnlyComposable get() = LocalRidingColors.current

    /** The five type roles beyond Material's slots. */
    val type: KtmTypeExtras @Composable @ReadOnlyComposable get() = LocalKtmTypeExtras.current

    val motion: KtmMotionSpecs @Composable @ReadOnlyComposable get() = LocalKtmMotion.current
}

/**
 * Keeps content clear of the system bars AND the display cutout. Plain `systemBarsPadding()`
 * ignores cutouts, which under Android 16's enforced edge-to-edge puts content behind the
 * notch/camera in landscape (and on foldable/large-screen layouts). In portrait the two
 * insets overlap, so the result is unchanged there.
 */
@Composable
fun Modifier.edgePadding(): Modifier =
    this.windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
