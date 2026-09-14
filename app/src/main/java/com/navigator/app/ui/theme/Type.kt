@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.navigator.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.navigator.app.R

/**
 * The three type families from the KTM handoff, bundled as real TTFs under
 * res/font so fidelity does not depend on network/Play-Services at runtime.
 *
 * - [BarlowCondensed]: display / headings / labels (hero headlines & road
 *   names use its italic).
 * - [Barlow]: body & secondary copy.
 * - [JetBrainsMono]: numeric / technical / MAC / codes / time / RSSI.
 *
 * These are the *brand* families. The public [Barlow]/[BarlowCondensed]/
 * [JetBrainsMono] accessors below resolve against the live [AppFonts.mode], so
 * switching to [FontMode.SYSTEM] swaps every call site to the phone's default
 * font with no per-screen edits (the accessors read Compose state).
 */
private val BrandBarlowCondensed = FontFamily(
    Font(R.font.barlow_condensed_medium, FontWeight.Medium),
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
    Font(R.font.barlow_condensed_semibold_italic, FontWeight.SemiBold, FontStyle.Italic),
    Font(R.font.barlow_condensed_bold_italic, FontWeight.Bold, FontStyle.Italic),
)

private val BrandBarlow = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_bold, FontWeight.Bold),
)

// JetBrains Mono ships as a single variable font; select weights via the
// weight axis so 400/500/700 all render from the one bundled file.
private val BrandJetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_variable, FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.jetbrains_mono_variable, FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.jetbrains_mono_variable, FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

/** Whether the app renders in the bundled brand fonts or the phone's default. */
enum class FontMode(val id: String) {
    /** Bundled Barlow / Barlow Condensed / JetBrains Mono (on-brand, default). */
    BRAND("brand"),

    /** The phone's own system font throughout the app. */
    SYSTEM("system");

    companion object {
        fun fromId(id: String?): FontMode = entries.firstOrNull { it.id == id } ?: BRAND
    }
}

/**
 * Reactive font-mode accessor, mirroring [Ktm] for colours. The public font
 * families below read [mode] (Compose state), so flipping it re-fonts the whole
 * app live — every `fontFamily = Barlow` call site follows without edits.
 */
object AppFonts {
    var mode by mutableStateOf(FontMode.BRAND)
        private set

    fun apply(mode: FontMode) { this.mode = mode }
}

/**
 * Public font families. In [FontMode.BRAND] these return the bundled families;
 * in [FontMode.SYSTEM] they collapse to [FontFamily.Default] (the phone font).
 * Backed by property getters that read [AppFonts.mode], so reads inside a
 * @Composable are reactive and re-font live on a mode switch.
 */
val Barlow: FontFamily
    get() = if (AppFonts.mode == FontMode.SYSTEM) FontFamily.Default else BrandBarlow

val BarlowCondensed: FontFamily
    get() = if (AppFonts.mode == FontMode.SYSTEM) FontFamily.Default else BrandBarlowCondensed

val JetBrainsMono: FontFamily
    get() = if (AppFonts.mode == FontMode.SYSTEM) FontFamily.Default else BrandJetBrainsMono

/**
 * The app Typography, resolved against the live [AppFonts.mode]. Built inside
 * [OpenDashTheme] on each composition so a font-mode switch rebuilds it.
 */
fun appTypography(): Typography = Typography(
    // Body defaults to Barlow so any un-styled Material text stays on-brand.
    bodyLarge = Typography().bodyLarge.copy(fontFamily = Barlow),
    bodyMedium = Typography().bodyMedium.copy(fontFamily = Barlow),
    bodySmall = Typography().bodySmall.copy(fontFamily = Barlow),
    labelLarge = Typography().labelLarge.copy(fontFamily = BarlowCondensed),
)

/**
 * Named text-style tokens — the app's recurring roles in one place so sizes,
 * families and weights stay consistent (colour is applied per call site via the
 * theme so these carry no colour). Prefer these over ad-hoc fontFamily/fontSize.
 *
 * These are property getters so the family follows the live [AppFonts.mode]
 * when read inside a @Composable.
 */
object AppText {
    /** Screen/section hero heading (card titles, place names). */
    val CardTitle: TextStyle
        get() = TextStyle(
            fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 20.sp,
        )

    /** Small uppercase, letter-spaced eyebrow / group label. */
    val SectionLabel: TextStyle
        get() = TextStyle(
            fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 2.0.sp,
        )

    /** Primary body copy. */
    val Body: TextStyle
        get() = TextStyle(fontFamily = Barlow, fontSize = 14.sp)

    /** Secondary/hint body copy. */
    val BodySmall: TextStyle
        get() = TextStyle(fontFamily = Barlow, fontSize = 12.sp)

    /** Uppercase stat label (DISTANCE, AVG …). */
    val StatLabel: TextStyle
        get() = TextStyle(
            fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.0.sp,
        )

    /** Monospace numeric/technical value. */
    val StatValue: TextStyle
        get() = TextStyle(fontFamily = JetBrainsMono, fontSize = 13.sp)

    /** Full-width action-button label. */
    val ButtonLabel: TextStyle
        get() = TextStyle(
            fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 1.5.sp,
        )
}
