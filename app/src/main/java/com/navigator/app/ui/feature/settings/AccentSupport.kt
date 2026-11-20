package com.navigator.app.ui.feature.settings

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.navigator.app.R
import com.navigator.app.ui.theme.AccentTones
import com.navigator.app.ui.theme.accentFor
import com.navigator.app.ui.theme.contrastRatio
import com.navigator.app.ui.theme.parseHexColor

/** The accent names the rider reads, in strings.xml (the theme keeps only ids). */
object AccentNames {
    @StringRes
    fun res(id: String?): Int = when (accentFor(id).id) {
        "orange" -> R.string.accent_orange
        "blue" -> R.string.accent_blue
        "purple" -> R.string.accent_purple
        "teal" -> R.string.accent_teal
        "green" -> R.string.accent_green
        "magenta" -> R.string.accent_magenta
        else -> R.string.accent_custom
    }

    /** True for a stored id that is a hex colour rather than a preset. */
    fun isCustom(id: String?): Boolean = id != null && accentFor(id).id.startsWith("#")
}

/** What the custom-colour sheet tells the rider about a colour on the current theme (ST12). */
data class AccentCheck(val textContrast: Double, val adjusted: Boolean) {
    companion object {
        /** Text in the accent is always drawn at a readable tone; [adjusted] says the colour itself was not readable as text. */
        fun of(seed: Color, background: Color, dark: Boolean): AccentCheck {
            val text = AccentTones.from(seed).forTheme(dark).text
            return AccentCheck(contrastRatio(text, background), adjusted = contrastRatio(seed, background) < READABLE)
        }

        const val READABLE = 4.5
    }
}

/** The hex field: six digits, with or without "#". Null when it is not a colour yet. */
object HexInput {
    fun parse(text: String): Color? {
        val digits = text.trim().removePrefix("#")
        return if (digits.length == 6) parseHexColor(digits) else null
    }

    /** Keeps what can be part of a hex colour, at most six digits. */
    fun clean(text: String): String = text.filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }.take(6).uppercase()
}
