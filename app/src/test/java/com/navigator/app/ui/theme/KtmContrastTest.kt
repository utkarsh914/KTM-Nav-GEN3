package com.navigator.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.navigator.app.ui.components.BadgeTone
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
}
