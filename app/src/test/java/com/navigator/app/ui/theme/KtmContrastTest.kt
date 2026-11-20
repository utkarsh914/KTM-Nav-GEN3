package com.navigator.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.navigator.app.ui.components.BadgeTone
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.buttonPalette
import com.navigator.app.ui.components.badgeColors
import org.junit.Assert.assertTrue
import org.junit.Test

private val AccentSchemeForTest = AccentScheme(Color.Red, Color.White, Color.Red, Color.Black, Color.White, Color.Red)

class KtmContrastTest {
    private fun ratio(a: Color, b: Color): Double {
        val hi = maxOf(a.luminance(), b.luminance()).toDouble()
        val lo = minOf(a.luminance(), b.luminance()).toDouble()
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test fun statusBadgesAreReadableInBothThemes() {
        for (c in listOf(KtmDarkColors, KtmLightColors)) {
            for (tone in listOf(BadgeTone.Ok, BadgeTone.Warn, BadgeTone.Bad, BadgeTone.Info, BadgeTone.Neutral)) {
                val (container, content) = badgeColors(tone, c, AccentSchemeForTest)
                assertTrue("$tone on ${if (c.isDark) "dark" else "light"}: ${ratio(container, content)}", ratio(container, content) >= 4.5)
            }
        }
    }

    @Test fun amberAttentionTextIsReadableOnTheSurface() {
        assertTrue(ratio(KtmLightColors.warn, KtmLightColors.surf) >= 4.5)
        assertTrue(ratio(KtmDarkColors.warn, KtmDarkColors.surf) >= 4.5)
    }

    @Test fun subtleTextIsReadableOnSurfaceAndSecondarySurface() {
        for (c in listOf(KtmDarkColors, KtmLightColors)) {
            assertTrue(ratio(c.fg3, c.surf) >= 4.5)
            assertTrue(ratio(c.fg3, c.surf2) >= 4.5)
        }
    }

    @Test fun statusTextIsReadableOnTheSurfaces() {
        for (c in listOf(KtmDarkColors, KtmLightColors)) {
            for (bg in listOf(c.bg, c.surf, c.surf2)) {
                for (text in listOf(c.okText, c.badText, c.infoText, c.warn)) assertTrue("${ratio(text, bg)}", ratio(text, bg) >= 4.5)
            }
            assertTrue(ratio(c.fg3, c.surf3) >= 4.5)
        }
    }

    @Test fun statusChipsAreReadable() {
        for (c in listOf(KtmDarkColors, KtmLightColors)) {
            for (fill in listOf(c.ok, c.warn, c.bad, c.info)) assertTrue("${ratio(c.onStatus, fill)}", ratio(c.onStatus, fill) >= 4.5)
        }
    }

    @Test fun dangerButtonsAreReadable() {
        for (c in listOf(KtmDarkColors, KtmLightColors)) {
            for (style in listOf(ButtonStyle.Danger, ButtonStyle.DangerTonal)) {
                val p = buttonPalette(style, true, c, AccentSchemeForTest)
                assertTrue("$style ${ratio(p.container, p.content)}", ratio(p.container, p.content) >= 4.5)
            }
        }
    }

    @Test fun everyPresetAccentFillCarriesItsText() {
        for (option in AccentPalettes) {
            for (dark in listOf(true, false)) {
                val t = option.tones.forTheme(dark)
                assertTrue("${option.id} $dark ${ratio(t.onFill, t.fill)}", ratio(t.onFill, t.fill) >= 4.5)
            }
        }
    }
}
