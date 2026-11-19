package com.navigator.app.ui.theme

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyBridgeTest {
    @After fun reset() = Ktm.apply(dark = true, accent = accentFor(null))

    @Test fun legacyGettersFollowTheNewTokens() {
        Ktm.apply(dark = false, accent = accentFor("blue"))
        assertFalse(Ktm.current.isDark)
        assertEquals(KtmLightColors.bg, Ktm.Screen)
        assertEquals(KtmLightColors.surf, Ktm.Surface)
        assertEquals(KtmLightColors.fg, Ktm.White)
        assertEquals(KtmLightColors.ok, Ktm.Green)
        assertEquals(KtmLightColors.bad, Ktm.Danger)
        // Light: the tone-shifted accent, so old orange text is readable on white.
        assertEquals(accentFor("blue").tones.textOnLight, Ktm.Orange)
    }

    @Test fun legacyAccentIsReadableAsTextOnLightAndTrueFillOnDark() {
        for (a in AccentPalettes) {
            Ktm.apply(dark = false, accent = a)
            assertTrue(contrastRatio(Ktm.Orange, KtmLightColors.surf) >= 4.5)
            assertTrue(contrastRatio(Ktm.OnAccent, Ktm.Orange) >= 4.3)
            Ktm.apply(dark = true, accent = a)
            assertEquals(a.tones.fill, Ktm.Orange)
        }
    }

    @Test fun accentSurvivesRepeatedThemeFlips() {
        Ktm.apply(dark = false, accent = accentFor("purple"))
        repeat(3) { Ktm.apply(dark = it % 2 == 0) }
        assertEquals("purple", Ktm.currentAccent.id)
    }

    @Test fun darkUsesTheDarkNeutralsAndKeepsTheAccent() {
        Ktm.apply(dark = false, accent = accentFor("teal"))
        Ktm.apply(dark = true)
        assertEquals(KtmDarkColors.bg, Ktm.Screen)
        assertEquals("teal", Ktm.currentAccent.id)
    }
}
