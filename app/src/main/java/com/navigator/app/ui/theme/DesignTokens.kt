package com.navigator.app.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

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
 * How riding surfaces choose light/dark, independent of [ThemeMode]: follow the app theme, or
 * switch by day and night. Shown to the rider as "Riding screens".
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
 * Legacy colour set read by the screens that predate the design system. It is derived from the new
 * tokens ([KtmColors] + [AccentScheme]), so those screens already follow the new palette; it goes
 * away, with [Ktm], when the last old screen is replaced (B19).
 */
data class LegacyTheme(
    val isDark: Boolean,
    val wordmark: String,
    val wordmarkItalic: Boolean,
    val wordmarkTracking: TextUnit,
    val accent: Color,
    val accentDeep: Color,
    val onAccent: Color,
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

internal fun legacyThemeFor(dark: Boolean, accent: AccentOption): LegacyTheme {
    val c = if (dark) KtmDarkColors else KtmLightColors
    val a = accent.tones.forTheme(dark)
    // Old screens paint text and icons with this colour as well as fills, so on light surfaces use
    // the tone-shifted accent (orange on white is only ~2.9:1). Dark keeps the true fill.
    val legacyAccent = if (dark) a.fill else a.text
    return LegacyTheme(
        isDark = dark,
        wordmark = "KTM", wordmarkItalic = true, wordmarkTracking = (-0.01).em,
        accent = legacyAccent, accentDeep = accent.deep, onAccent = AccentTones.onColorFor(legacyAccent),
        screen = c.bg, black = if (dark) Color.Black else Color.White, screenDeep = c.bg,
        surface = c.surf, surfaceAlt = c.surf2, surfaceDisabled = c.surf3,
        border = c.line, bezel = c.line2, rowDivider = c.line, connBorder = c.okContainer, borderSoft = c.line2,
        head = c.fg, textPrimary = c.fg, textSecondary = c.fg2, muted = c.fg3, muted2 = c.fg3, dim = c.fg3,
        green = c.ok, danger = c.bad,
    )
}

/**
 * Reactive accessor kept so the ~40 files that read `Ktm.Orange`, `Ktm.Screen`, … keep working
 * until their batch replaces them. New code uses `MaterialTheme` and [KtmTheme] instead.
 */
object Ktm {
    var current by mutableStateOf(legacyThemeFor(dark = true, accent = accentFor(null)))
        private set

    var currentAccent: AccentOption by mutableStateOf(accentFor(null))
        private set

    /** Set the light/dark appearance and the accent. Omitting [accent] keeps the current one. */
    fun apply(dark: Boolean, accent: AccentOption = currentAccent) {
        currentAccent = accent
        current = legacyThemeFor(dark, accent)
    }

    val Orange get() = current.accent
    val OrangeDeep get() = current.accentDeep
    val OnAccent get() = current.onAccent

    val Screen get() = current.screen
    val Black get() = current.black
    val ScreenDeep get() = current.screenDeep

    val Surface get() = current.surface
    val SurfaceAlt get() = current.surfaceAlt
    val SurfaceDisabled get() = current.surfaceDisabled

    val Border get() = current.border
    val Bezel get() = current.bezel
    val RowDivider get() = current.rowDivider
    val ConnBorder get() = current.connBorder
    val BorderSoft get() = current.borderSoft

    val White get() = current.head
    val TextPrimary get() = current.textPrimary
    val TextSecondary get() = current.textSecondary
    val Muted get() = current.muted
    val Muted2 get() = current.muted2
    val Dim get() = current.dim

    val Green get() = current.green
    val Danger get() = current.danger

    val RadiusCard = 16.dp
    val RadiusButton = 12.dp
    val RadiusRow = 13.dp

    /** Shared height for the floating map controls so they line up across both home screens. */
    val ControlHeight = 52.dp
}
