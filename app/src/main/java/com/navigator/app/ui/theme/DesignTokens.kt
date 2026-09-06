package com.navigator.app.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/**
 * Multi-brand theme tokens. The app is one binary that re-skins per motorcycle
 * brand (KTM / Husqvarna / … — all Pierer Mobility, all the same BCCU dash), so
 * every colour is a [BrandTheme] token rather than a fixed value.
 *
 * The [Ktm] accessor object is kept (all existing `Ktm.Xxx` call sites still
 * compile) but each colour is now a getter over a reactive [current] theme:
 * reading `Ktm.Orange` inside a composable subscribes to `current`, so setting
 * a new brand ([applyBrand]) recomposes the whole app live. Radii are constant
 * across brands and stay plain vals.
 *
 * Semantic colours (Green/Danger) ARE themed for legibility (a light theme
 * needs a darker green/red) but keep their meaning — never repurposed for brand
 * identity. Brand identity is [BrandTheme.accent] only.
 */

enum class Brand(val id: String) {
    KTM("ktm"),
    HUSQVARNA("husqvarna");

    companion object {
        fun fromId(id: String?): Brand = entries.firstOrNull { it.id == id } ?: KTM
    }
}

/** One brand's full token set + identity metadata. */
data class BrandTheme(
    val brand: Brand,
    val isDark: Boolean,
    // Identity
    val displayName: String,
    val wordmark: String,
    val tagline: String,
    val wordmarkItalic: Boolean,
    val wordmarkTracking: TextUnit,
    // Brand accent
    val accent: Color,
    val accentDeep: Color,
    val onAccent: Color, // text/icon colour that sits ON an accent fill
    // Backgrounds
    val screen: Color,
    val black: Color,
    val screenDeep: Color,
    // Surfaces
    val surface: Color,
    val surfaceAlt: Color,
    val surfaceDisabled: Color,
    // Borders
    val border: Color,
    val bezel: Color,
    val rowDivider: Color,
    val connBorder: Color,
    val borderSoft: Color,
    // Text
    val head: Color, // strongest heading text (was "White")
    val textPrimary: Color,
    val textSecondary: Color,
    val muted: Color,
    val muted2: Color,
    val dim: Color,
    // Semantic (themed for contrast, meaning fixed)
    val green: Color,
    val danger: Color,
)

/** How the app chooses between the light and dark neutral palettes. */
enum class ThemeMode(val id: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/**
 * How the navigation (map) screen chooses light/dark, independent of the app's
 * [ThemeMode]. [APP] mirrors the app selection; [DAY_NIGHT] is dark at night
 * (18:00–06:00) and light during the day.
 */
enum class NavThemeMode(val id: String) {
    APP("app"),
    DAY_NIGHT("day_night");

    companion object {
        fun fromId(id: String?): NavThemeMode = entries.firstOrNull { it.id == id } ?: DAY_NIGHT
    }
}

/** True when the local wall-clock time is "night" for nav theming (18:00–06:00). */
fun isNightTime(hourOfDay: Int = java.time.LocalTime.now().hour): Boolean =
    hourOfDay < 6 || hourOfDay >= 18

/**
 * The neutral (non-accent) half of a theme: backgrounds, surfaces, borders, text
 * and the contrast-tuned semantic colours. Light/dark is chosen here; the brand
 * only layers its accent on top (see [BrandIdentity]/[resolve]).
 */
data class NeutralPalette(
    val isDark: Boolean,
    val screen: Color,
    val black: Color,
    val screenDeep: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val surfaceDisabled: Color,
    val border: Color,
    val bezel: Color,
    val rowDivider: Color,
    val connBorder: Color,
    val borderSoft: Color,
    val head: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val muted: Color,
    val muted2: Color,
    val dim: Color,
    val green: Color,
    val danger: Color,
)

/** The brand half of a theme: identity + accent, independent of light/dark. */
data class BrandIdentity(
    val brand: Brand,
    val displayName: String,
    val wordmark: String,
    val tagline: String,
    val wordmarkItalic: Boolean,
    val wordmarkTracking: TextUnit,
    val accent: Color,
    val accentDeep: Color,
    val onAccent: Color, // text/icon colour that sits ON an accent fill
)

/** Dark neutrals — the original OpenDash KTM handoff greys, 1:1. */
val DarkNeutrals = NeutralPalette(
    isDark = true,
    screen = Color(0xFF0B0C0E),
    black = Color(0xFF000000),
    screenDeep = Color(0xFF050505),
    surface = Color(0xFF141618),
    surfaceAlt = Color(0xFF101214),
    surfaceDisabled = Color(0xFF1A1C1F),
    border = Color(0xFF24272D),
    bezel = Color(0xFF2A2D33),
    rowDivider = Color(0xFF202329),
    connBorder = Color(0xFF1D3A2B),
    borderSoft = Color(0xFF3A3D44),
    head = Color(0xFFFFFFFF),
    textPrimary = Color(0xFFE6E7EA),
    textSecondary = Color(0xFFC9CDD3),
    muted = Color(0xFF9AA0A8),
    muted2 = Color(0xFF8A8F98),
    dim = Color(0xFF6C727B),
    green = Color(0xFF34D07F),
    danger = Color(0xFFFF4438),
)

/** Light neutrals — clean neutral greys on white, tuned to sit under any accent. */
val LightNeutrals = NeutralPalette(
    isDark = false,
    screen = Color(0xFFF3F3F5),
    black = Color(0xFFFFFFFF), // "max-contrast bg" → white in a light theme
    screenDeep = Color(0xFFEAEAEC),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFF6F6F8),
    surfaceDisabled = Color(0xFFE2E2E5),
    border = Color(0xFFDBDBDF),
    bezel = Color(0xFFCBCBD1),
    rowDivider = Color(0xFFE9E9EC),
    connBorder = Color(0xFFBFE0CC),
    borderSoft = Color(0xFFC5C5CC),
    head = Color(0xFF16181C),
    textPrimary = Color(0xFF2A2D33),
    textSecondary = Color(0xFF4A4E56),
    muted = Color(0xFF6D7178),
    muted2 = Color(0xFF7C8088),
    dim = Color(0xFF9A9EA6),
    green = Color(0xFF1B9A56),
    danger = Color(0xFFE5362B),
)

/** KTM — orange, "READY TO RACE". */
val KtmIdentity = BrandIdentity(
    brand = Brand.KTM,
    displayName = "KTM",
    wordmark = "KTM",
    tagline = "READY TO RACE",
    wordmarkItalic = true,
    wordmarkTracking = (-0.01).em,
    accent = Color(0xFFFF6600),
    accentDeep = Color(0xFFE05500),
    onAccent = Color(0xFF0B0C0E),
)

/** Husqvarna — blue, "Pioneering since 1903". */
val HusqvarnaIdentity = BrandIdentity(
    brand = Brand.HUSQVARNA,
    displayName = "Husqvarna",
    wordmark = "HUSQVARNA",
    tagline = "PIONEERING SINCE 1903",
    wordmarkItalic = false,
    wordmarkTracking = 0.22.em,
    accent = Color(0xFF2C5CB0),
    accentDeep = Color(0xFF1E4488),
    onAccent = Color(0xFFFFFFFF),
)

fun identityFor(brand: Brand): BrandIdentity = when (brand) {
    Brand.KTM -> KtmIdentity
    Brand.HUSQVARNA -> HusqvarnaIdentity
}

/**
 * A selectable app accent, chosen independently of the motorcycle brand. When
 * set it overrides the brand's own [BrandIdentity.accent] app-wide (the brand
 * still supplies neutrals + wordmark). `null` (no selection) follows the brand.
 */
data class AccentOption(
    val id: String,
    val displayName: String,
    val color: Color,
    val deep: Color,
    /** Content colour that reads on top of an [color] fill. */
    val onAccent: Color,
)

/** Preset accents offered in Settings, in display order. Each carries a
 *  contrast-correct [AccentOption.onAccent] (near-black on light fills, white on dark). */
val AccentPalettes: List<AccentOption> = listOf(
    AccentOption("orange", "Orange", Color(0xFFFF6600), Color(0xFFE05500), Color(0xFF0B0C0E)),
    AccentOption("blue", "Blue", Color(0xFF2C7BE5), Color(0xFF1C5BB8), Color(0xFFFFFFFF)),
    AccentOption("purple", "Purple", Color(0xFF8B5CF6), Color(0xFF6D28D9), Color(0xFFFFFFFF)),
    AccentOption("teal", "Teal", Color(0xFF14B8A6), Color(0xFF0D9488), Color(0xFF06201C)),
    AccentOption("green", "Green", Color(0xFF22C55E), Color(0xFF16A34A), Color(0xFF06210F)),
    AccentOption("magenta", "Magenta", Color(0xFFEC4899), Color(0xFFBE185D), Color(0xFFFFFFFF)),
)

/**
 * Resolve a stored accent id to an [AccentOption]:
 *  - `null` -> null (follow the brand's own accent),
 *  - a preset id (e.g. "blue") -> that preset,
 *  - a hex string ("#RRGGBB" / "#AARRGGBB") -> a [customAccent] built from it.
 */
fun accentFor(id: String?): AccentOption? {
    if (id.isNullOrBlank()) return null
    AccentPalettes.firstOrNull { it.id == id }?.let { return it }
    return parseHexColor(id)?.let { customAccent(it) }
}

/** Parse "#RRGGBB" or "#AARRGGBB" to a [Color], or null if malformed. */
fun parseHexColor(s: String): Color? {
    val hex = s.trim().removePrefix("#")
    val v = hex.toLongOrNull(16) ?: return null
    return when (hex.length) {
        6 -> Color(0xFF000000L or v)          // opaque RRGGBB
        8 -> Color(v and 0xFFFFFFFFL)         // AARRGGBB
        else -> null
    }
}

/** Canonical "#RRGGBB" for a colour (used as the stored custom-accent id). */
fun hexOf(color: Color): String {
    fun ch(f: Float) = (f * 255f + 0.5f).toInt().coerceIn(0, 255)
    return "#%02X%02X%02X".format(ch(color.red), ch(color.green), ch(color.blue))
}

/** Build a full accent from an arbitrary colour: a darker "deep" shade and a
 *  contrast-correct onAccent (near-black on light colours, white on dark). Its id
 *  is the canonical hex so it round-trips through [accentFor]. */
fun customAccent(color: Color): AccentOption {
    val luminance = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
    val deep = Color(
        red = color.red * 0.78f,
        green = color.green * 0.78f,
        blue = color.blue * 0.78f,
        alpha = 1f,
    )
    val onAccent = if (luminance > 0.6f) Color(0xFF0B0C0E) else Color(0xFFFFFFFF)
    return AccentOption(id = hexOf(color), displayName = "Custom", color = color, deep = deep, onAccent = onAccent)
}

/** Combine a brand's identity with a neutral palette into a full [BrandTheme].
 *  An optional [accent] override replaces the brand's own accent triplet. */
fun resolve(
    identity: BrandIdentity,
    neutrals: NeutralPalette,
    accent: AccentOption? = null,
): BrandTheme = BrandTheme(
    brand = identity.brand,
    isDark = neutrals.isDark,
    displayName = identity.displayName,
    wordmark = identity.wordmark,
    tagline = identity.tagline,
    wordmarkItalic = identity.wordmarkItalic,
    wordmarkTracking = identity.wordmarkTracking,
    accent = accent?.color ?: identity.accent,
    accentDeep = accent?.deep ?: identity.accentDeep,
    onAccent = accent?.onAccent ?: identity.onAccent,
    screen = neutrals.screen,
    black = neutrals.black,
    screenDeep = neutrals.screenDeep,
    surface = neutrals.surface,
    surfaceAlt = neutrals.surfaceAlt,
    surfaceDisabled = neutrals.surfaceDisabled,
    border = neutrals.border,
    bezel = neutrals.bezel,
    rowDivider = neutrals.rowDivider,
    connBorder = neutrals.connBorder,
    borderSoft = neutrals.borderSoft,
    head = neutrals.head,
    textPrimary = neutrals.textPrimary,
    textSecondary = neutrals.textSecondary,
    muted = neutrals.muted,
    muted2 = neutrals.muted2,
    dim = neutrals.dim,
    green = neutrals.green,
    danger = neutrals.danger,
)

/** Resolve the full palette for a brand at the requested light/dark appearance,
 *  with an optional independent accent override. */
fun themeFor(brand: Brand, dark: Boolean, accent: AccentOption? = null): BrandTheme =
    resolve(identityFor(brand), if (dark) DarkNeutrals else LightNeutrals, accent)

/** Identity-only convenience (displayName doesn't depend on light/dark). Keeps
 *  the current accent override so callers reading e.g. displayName don't reset it. */
fun themeFor(brand: Brand): BrandTheme = themeFor(brand, dark = Ktm.current.isDark, accent = Ktm.currentAccent)

/**
 * Reactive theme accessor. Historically named `Ktm`; kept so the ~40 files
 * that read `Ktm.Orange`, `Ktm.Screen`, … keep working unchanged — but every
 * colour now resolves against the live [current] brand and is Compose-reactive.
 */
object Ktm {
    /** The active brand's tokens. Set via [apply]/[applyBrand]; drives live re-theme. */
    var current by mutableStateOf(themeFor(Brand.KTM, dark = true))
        private set

    /** The active accent override, or null when following the brand's own accent. */
    var currentAccent: AccentOption? = null
        private set

    /** Set the brand, light/dark appearance and (optionally) the accent override.
     *  Omitting [accent] keeps whatever override is currently active. */
    fun apply(brand: Brand, dark: Boolean, accent: AccentOption? = currentAccent) {
        currentAccent = accent
        current = themeFor(brand, dark, accent)
    }

    /** Switch brand while keeping the current light/dark appearance + accent. */
    fun applyBrand(brand: Brand) {
        current = themeFor(brand, dark = current.isDark, accent = currentAccent)
    }

    /** Change only the accent override (keeps brand + light/dark), driving a live re-theme. */
    fun applyAccent(accent: AccentOption?) {
        currentAccent = accent
        current = themeFor(current.brand, current.isDark, accent)
    }

    // Brand
    val Orange get() = current.accent
    val OrangeDeep get() = current.accentDeep
    /** Content colour that sits on an [Orange] fill (near-black on KTM, white on Husqvarna). */
    val OnAccent get() = current.onAccent

    // Backgrounds
    val Screen get() = current.screen
    val Black get() = current.black
    val ScreenDeep get() = current.screenDeep

    // Surfaces / cards
    val Surface get() = current.surface
    val SurfaceAlt get() = current.surfaceAlt
    val SurfaceDisabled get() = current.surfaceDisabled

    // Borders
    val Border get() = current.border
    val Bezel get() = current.bezel
    val RowDivider get() = current.rowDivider
    val ConnBorder get() = current.connBorder
    val BorderSoft get() = current.borderSoft

    // Text
    val White get() = current.head // "White" historically meant "strongest heading text"
    val TextPrimary get() = current.textPrimary
    val TextSecondary get() = current.textSecondary
    val Muted get() = current.muted
    val Muted2 get() = current.muted2
    val Dim get() = current.dim

    // Semantic
    val Green get() = current.green
    val Danger get() = current.danger

    // Common radii (brand-independent)
    val RadiusCard = 16.dp
    val RadiusButton = 12.dp
    val RadiusRow = 13.dp

    /** Shared height for the floating map controls (search bar, icon buttons,
     *  connection pill) so they line up across both home screens. */
    val ControlHeight = 52.dp
}
