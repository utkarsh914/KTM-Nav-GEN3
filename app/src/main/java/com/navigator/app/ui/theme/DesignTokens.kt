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
    val board: Color,
    val screen: Color,
    val black: Color,
    val screenDeep: Color,
    // Surfaces
    val surface: Color,
    val surfaceAlt: Color,
    val surfaceAlt2: Color,
    val notifCard: Color,
    val surfaceDisabled: Color,
    val connBanner: Color,
    val monoChip: Color,
    val dashBanner: Color,
    // Borders
    val border: Color,
    val bezel: Color,
    val rowDivider: Color,
    val notifBorder: Color,
    val connBorder: Color,
    val monoChipBorder: Color,
    val borderSoft: Color,
    // Text
    val head: Color, // strongest heading text (was "White")
    val textPrimary: Color,
    val textSecondary: Color,
    val muted: Color,
    val muted2: Color,
    val dim: Color,
    val dim2: Color,
    val dim3: Color,
    val connSub: Color,
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
 * The neutral (non-accent) half of a theme: backgrounds, surfaces, borders, text
 * and the contrast-tuned semantic colours. Light/dark is chosen here; the brand
 * only layers its accent on top (see [BrandIdentity]/[resolve]).
 */
data class NeutralPalette(
    val isDark: Boolean,
    val board: Color,
    val screen: Color,
    val black: Color,
    val screenDeep: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val surfaceAlt2: Color,
    val notifCard: Color,
    val surfaceDisabled: Color,
    val connBanner: Color,
    val monoChip: Color,
    val dashBanner: Color,
    val border: Color,
    val bezel: Color,
    val rowDivider: Color,
    val notifBorder: Color,
    val connBorder: Color,
    val monoChipBorder: Color,
    val borderSoft: Color,
    val head: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val muted: Color,
    val muted2: Color,
    val dim: Color,
    val dim2: Color,
    val dim3: Color,
    val connSub: Color,
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
    board = Color(0xFF08090A),
    screen = Color(0xFF0B0C0E),
    black = Color(0xFF000000),
    screenDeep = Color(0xFF050505),
    surface = Color(0xFF141618),
    surfaceAlt = Color(0xFF101214),
    surfaceAlt2 = Color(0xFF101215),
    notifCard = Color(0xFF0F0F10),
    surfaceDisabled = Color(0xFF1A1C1F),
    connBanner = Color(0xFF0F1416),
    monoChip = Color(0xFF191B1F),
    dashBanner = Color(0xFF0C0C0C),
    border = Color(0xFF24272D),
    bezel = Color(0xFF2A2D33),
    rowDivider = Color(0xFF202329),
    notifBorder = Color(0xFF1E1E20),
    connBorder = Color(0xFF1D3A2B),
    monoChipBorder = Color(0xFF26282E),
    borderSoft = Color(0xFF3A3D44),
    head = Color(0xFFFFFFFF),
    textPrimary = Color(0xFFE6E7EA),
    textSecondary = Color(0xFFC9CDD3),
    muted = Color(0xFF9AA0A8),
    muted2 = Color(0xFF8A8F98),
    dim = Color(0xFF6C727B),
    dim2 = Color(0xFF5C626B),
    dim3 = Color(0xFF4C525B),
    connSub = Color(0xFF7B8188),
    green = Color(0xFF34D07F),
    danger = Color(0xFFFF4438),
)

/** Light neutrals — clean neutral greys on white, tuned to sit under any accent. */
val LightNeutrals = NeutralPalette(
    isDark = false,
    board = Color(0xFFE8E8EA),
    screen = Color(0xFFF3F3F5),
    black = Color(0xFFFFFFFF), // "max-contrast bg" → white in a light theme
    screenDeep = Color(0xFFEAEAEC),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFF6F6F8),
    surfaceAlt2 = Color(0xFFF2F2F4),
    notifCard = Color(0xFFFFFFFF),
    surfaceDisabled = Color(0xFFE2E2E5),
    connBanner = Color(0xFFEFF6F1), // faint green card
    monoChip = Color(0xFFEFEFF1),
    dashBanner = Color(0xFFF1F1F3),
    border = Color(0xFFDBDBDF),
    bezel = Color(0xFFCBCBD1),
    rowDivider = Color(0xFFE9E9EC),
    notifBorder = Color(0xFFE4E4E7),
    connBorder = Color(0xFFBFE0CC),
    monoChipBorder = Color(0xFFDBDBDF),
    borderSoft = Color(0xFFC5C5CC),
    head = Color(0xFF16181C),
    textPrimary = Color(0xFF2A2D33),
    textSecondary = Color(0xFF4A4E56),
    muted = Color(0xFF6D7178),
    muted2 = Color(0xFF7C8088),
    dim = Color(0xFF9A9EA6),
    dim2 = Color(0xFFACB0B7),
    dim3 = Color(0xFFBEC1C7),
    connSub = Color(0xFF6D7178),
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

/** Combine a brand's identity with a neutral palette into a full [BrandTheme]. */
fun resolve(identity: BrandIdentity, neutrals: NeutralPalette): BrandTheme = BrandTheme(
    brand = identity.brand,
    isDark = neutrals.isDark,
    displayName = identity.displayName,
    wordmark = identity.wordmark,
    tagline = identity.tagline,
    wordmarkItalic = identity.wordmarkItalic,
    wordmarkTracking = identity.wordmarkTracking,
    accent = identity.accent,
    accentDeep = identity.accentDeep,
    onAccent = identity.onAccent,
    board = neutrals.board,
    screen = neutrals.screen,
    black = neutrals.black,
    screenDeep = neutrals.screenDeep,
    surface = neutrals.surface,
    surfaceAlt = neutrals.surfaceAlt,
    surfaceAlt2 = neutrals.surfaceAlt2,
    notifCard = neutrals.notifCard,
    surfaceDisabled = neutrals.surfaceDisabled,
    connBanner = neutrals.connBanner,
    monoChip = neutrals.monoChip,
    dashBanner = neutrals.dashBanner,
    border = neutrals.border,
    bezel = neutrals.bezel,
    rowDivider = neutrals.rowDivider,
    notifBorder = neutrals.notifBorder,
    connBorder = neutrals.connBorder,
    monoChipBorder = neutrals.monoChipBorder,
    borderSoft = neutrals.borderSoft,
    head = neutrals.head,
    textPrimary = neutrals.textPrimary,
    textSecondary = neutrals.textSecondary,
    muted = neutrals.muted,
    muted2 = neutrals.muted2,
    dim = neutrals.dim,
    dim2 = neutrals.dim2,
    dim3 = neutrals.dim3,
    connSub = neutrals.connSub,
    green = neutrals.green,
    danger = neutrals.danger,
)

/** Resolve the full palette for a brand at the requested light/dark appearance. */
fun themeFor(brand: Brand, dark: Boolean): BrandTheme =
    resolve(identityFor(brand), if (dark) DarkNeutrals else LightNeutrals)

/** Identity-only convenience (accent/displayName don't depend on light/dark). */
fun themeFor(brand: Brand): BrandTheme = themeFor(brand, dark = Ktm.current.isDark)

/**
 * Reactive theme accessor. Historically named `Ktm`; kept so the ~40 files
 * that read `Ktm.Orange`, `Ktm.Screen`, … keep working unchanged — but every
 * colour now resolves against the live [current] brand and is Compose-reactive.
 */
object Ktm {
    /** The active brand's tokens. Set via [apply]/[applyBrand]; drives live re-theme. */
    var current by mutableStateOf(themeFor(Brand.KTM, dark = true))
        private set

    /** Set both the brand accent and the light/dark appearance. */
    fun apply(brand: Brand, dark: Boolean) {
        current = themeFor(brand, dark)
    }

    /** Switch brand while keeping the current light/dark appearance. */
    fun applyBrand(brand: Brand) {
        current = themeFor(brand, dark = current.isDark)
    }

    // Brand
    val Orange get() = current.accent
    val OrangeDeep get() = current.accentDeep
    /** Content colour that sits on an [Orange] fill (near-black on KTM, white on Husqvarna). */
    val OnAccent get() = current.onAccent

    // Backgrounds
    val Board get() = current.board
    val Screen get() = current.screen
    val Black get() = current.black
    val ScreenDeep get() = current.screenDeep

    // Surfaces / cards
    val Surface get() = current.surface
    val SurfaceAlt get() = current.surfaceAlt
    val SurfaceAlt2 get() = current.surfaceAlt2
    val NotifCard get() = current.notifCard
    val SurfaceDisabled get() = current.surfaceDisabled
    val ConnBanner get() = current.connBanner
    val MonoChip get() = current.monoChip
    val DashBanner get() = current.dashBanner

    // Borders
    val Border get() = current.border
    val Bezel get() = current.bezel
    val RowDivider get() = current.rowDivider
    val NotifBorder get() = current.notifBorder
    val ConnBorder get() = current.connBorder
    val MonoChipBorder get() = current.monoChipBorder
    val BorderSoft get() = current.borderSoft

    // Text
    val White get() = current.head // "White" historically meant "strongest heading text"
    val TextPrimary get() = current.textPrimary
    val TextSecondary get() = current.textSecondary
    val Muted get() = current.muted
    val Muted2 get() = current.muted2
    val Dim get() = current.dim
    val Dim2 get() = current.dim2
    val Dim3 get() = current.dim3
    val ConnSub get() = current.connSub

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
