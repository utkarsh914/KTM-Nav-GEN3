package com.navigator.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct

/** What an accent looks like on one theme. Fills keep the true colour; text uses a tone-shifted one. */
data class AccentScheme(
    /** Buttons, switches, selected states. The true accent colour. */
    val fill: Color,
    /** Content on a [fill]. */
    val onFill: Color,
    /** Accent-coloured text and icons on the neutral surfaces (contrast ≥ 4.5:1). */
    val text: Color,
    val container: Color,
    val onContainer: Color,
    /** Accent on a snackbar, which uses the inverse surface. */
    val inverseAccent: Color,
)

/**
 * One accent, resolved for both themes. Presets carry the design's hand-tuned tones; a custom colour
 * derives its tones with HCT (T70 for text on dark, T40 on light), so any seed stays readable.
 */
data class AccentTones(
    val fill: Color,
    val onFill: Color,
    val textOnDark: Color,
    val textOnLight: Color,
    val containerDark: Color,
    val onContainerDark: Color,
    val containerLight: Color,
    val onContainerLight: Color,
    val inverseOnDark: Color,
    val inverseOnLight: Color,
) {
    fun forTheme(dark: Boolean) = if (dark) {
        AccentScheme(fill, onFill, textOnDark, containerDark, onContainerDark, inverseOnDark)
    } else {
        AccentScheme(fill, onFill, textOnLight, containerLight, onContainerLight, inverseOnLight)
    }

    companion object {
        private val Black = Color(0xFF0B0C0E)
        private val White = Color(0xFFFFFFFF)

        /** Derive every tone from one seed with HCT. */
        fun from(seed: Color): AccentTones {
            val hct = Hct.fromInt(seed.toArgb())
            fun tone(t: Double) = Color(Hct.from(hct.hue, hct.chroma, t).toInt())
            return AccentTones(
                fill = seed,
                onFill = onColorFor(seed),
                textOnDark = tone(70.0),
                textOnLight = tone(40.0),
                containerDark = tone(20.0),
                onContainerDark = tone(90.0),
                containerLight = tone(90.0),
                onContainerLight = tone(10.0),
                inverseOnDark = tone(40.0),
                inverseOnLight = tone(75.0),
            )
        }

        /** Presets: design fill, dark text, light text and on-fill, with derived containers. */
        fun preset(fill: Long, textDark: Long, textLight: Long, onFill: Color): AccentTones {
            val d = from(Color(fill))
            return d.copy(
                fill = Color(fill), onFill = onFill,
                textOnDark = Color(textDark), textOnLight = Color(textLight),
            )
        }

        /** Whichever of near-black and white reads better on [fill]. */
        fun onColorFor(fill: Color): Color =
            if (contrastRatio(fill, Black) >= contrastRatio(fill, White)) Black else White
    }
}

/** A selectable accent. `null` in settings means [KtmOrange]. */
data class AccentOption(val id: String, val tones: AccentTones)

private val OnBlack = Color(0xFF0B0C0E)
private val OnWhite = Color(0xFFFFFFFF)

/** KTM orange: the default accent. Dark/light containers are the design's own. */
val KtmOrange: AccentTones = AccentTones.preset(0xFFFF6600, 0xFFFF7A1F, 0xFFC24E00, OnBlack).copy(
    containerDark = Color(0xFF3D1F0B), onContainerDark = Color(0xFFFFD3B3),
    containerLight = Color(0xFFFFE2CF), onContainerLight = Color(0xFF6B2A00),
    inverseOnDark = Color(0xFFB84A00), inverseOnLight = Color(0xFFFF9A57),
)

/** The hues a "Custom" swatch shows before a custom colour is chosen. */
val RainbowHint: List<Color> = listOf(
    Color(0xFFEF4444), Color(0xFFF59E0B), Color(0xFF22C55E), Color(0xFF3B82F6), Color(0xFF8B5CF6), Color(0xFFEF4444),
)

/** Preset accents offered in Settings, in display order (the first is the default). */
val AccentPalettes: List<AccentOption> = listOf(
    AccentOption("orange", KtmOrange),
    AccentOption("blue", AccentTones.preset(0xFF2570D8, 0xFF6AA5F5, 0xFF1F5FBF, OnWhite)),
    AccentOption("purple", AccentTones.preset(0xFF7C4DEB, 0xFFA98BFA, 0xFF6D3FD8, OnWhite)),
    AccentOption("teal", AccentTones.preset(0xFF14B8A6, 0xFF2DD4BF, 0xFF0E7C70, OnBlack)),
    AccentOption("green", AccentTones.preset(0xFF22C55E, 0xFF4ADE80, 0xFF15803D, OnBlack)),
    AccentOption("magenta", AccentTones.preset(0xFFD02E7C, 0xFFF472B6, 0xFFBE185D, OnWhite)),
)

/**
 * Resolve a stored accent id: `null` is the default orange, a preset id is that preset, a hex string
 * ("#RRGGBB" / "#AARRGGBB") is a custom accent. Anything unreadable falls back to the default.
 */
fun accentFor(id: String?): AccentOption {
    if (id.isNullOrBlank()) return AccentPalettes.first()
    AccentPalettes.firstOrNull { it.id == id }?.let { return it }
    return parseHexColor(id)?.let { customAccent(it) } ?: AccentPalettes.first()
}

/** Parse "#RRGGBB" or "#AARRGGBB" to a [Color], or null if malformed. */
fun parseHexColor(s: String): Color? {
    val hex = s.trim().removePrefix("#")
    if (hex.isEmpty() || !hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
    val v = hex.toLongOrNull(16) ?: return null
    return when (hex.length) {
        6 -> Color(0xFF000000L or v)
        8 -> Color(v and 0xFFFFFFFFL)
        else -> null
    }
}

/** Canonical "#RRGGBB" for a colour (the stored id of a custom accent). */
fun hexOf(color: Color): String {
    fun ch(f: Float) = (f * 255f + 0.5f).toInt().coerceIn(0, 255)
    return "#%02X%02X%02X".format(ch(color.red), ch(color.green), ch(color.blue))
}

/** An accent from any colour. Its id is the canonical hex, so it round-trips through [accentFor]. */
fun customAccent(color: Color): AccentOption {
    val opaque = color.copy(alpha = 1f)
    return AccentOption(id = hexOf(opaque), tones = AccentTones.from(opaque))
}

/** WCAG contrast ratio between two opaque colours. */
fun contrastRatio(a: Color, b: Color): Double {
    fun lin(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.04045) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    }
    fun lum(c: Color) = 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
    val l1 = lum(a); val l2 = lum(b)
    return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
}
