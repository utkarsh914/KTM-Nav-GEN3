package com.navigator.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AccentTonesTest {
    private val darkSurf = KtmDarkColors.surf
    private val lightSurf = KtmLightColors.surf

    @Test fun everyPresetTextIsReadableOnItsSurface() {
        for (p in AccentPalettes) {
            val dark = contrastRatio(p.tones.textOnDark, darkSurf)
            val light = contrastRatio(p.tones.textOnLight, lightSurf)
            assertTrue("${p.id} dark text $dark", dark >= 4.5)
            assertTrue("${p.id} light text $light", light >= 4.5)
        }
    }

    @Test fun orangeMatchesTheDesignNumbers() {
        assertEquals(Color(0xFFFF6600), KtmOrange.fill)
        assertEquals(Color(0xFFC24E00), KtmOrange.textOnLight)
        assertEquals(4.8, contrastRatio(KtmOrange.textOnLight, lightSurf), 0.1)
        assertTrue(contrastRatio(KtmOrange.textOnDark, darkSurf) >= 6.4)
    }

    @Test fun anyCustomSeedYieldsReadableTextAndContent() {
        val rnd = Random(42)
        repeat(300) {
            val seed = Color(rnd.nextInt(0x1000000) or (0xFF shl 24))
            val t = AccentTones.from(seed)
            val label = hexOf(seed)
            assertTrue("$label dark ${contrastRatio(t.textOnDark, KtmDarkColors.surf)}", contrastRatio(t.textOnDark, KtmDarkColors.surf) >= 4.5)
            assertTrue("$label dark bg", contrastRatio(t.textOnDark, KtmDarkColors.bg) >= 4.5)
            assertTrue("$label light ${contrastRatio(t.textOnLight, KtmLightColors.surf)}", contrastRatio(t.textOnLight, KtmLightColors.surf) >= 4.5)
            assertTrue("$label light bg", contrastRatio(t.textOnLight, KtmLightColors.bg) >= 4.5)
            // Best of near-black (#0B0C0E, the design's "black") and white: at worst about 4.4.
            assertTrue("$label onFill", contrastRatio(t.onFill, seed) >= 4.3)
            assertTrue("$label container", contrastRatio(t.onContainerDark, t.containerDark) >= 4.5)
            assertTrue("$label container light", contrastRatio(t.onContainerLight, t.containerLight) >= 4.5)
        }
    }

    @Test fun extremeSeedsStayReadable() {
        for (c in listOf(Color.Black, Color.White, Color(0xFFFFFF00), Color(0xFF0000FF), Color(0xFF808080))) {
            val t = AccentTones.from(c)
            assertTrue(contrastRatio(t.textOnDark, darkSurf) >= 4.5)
            assertTrue(contrastRatio(t.textOnLight, lightSurf) >= 4.5)
        }
    }

    @Test fun storedIdsResolve() {
        assertEquals("orange", accentFor(null).id)
        assertEquals("orange", accentFor("").id)
        assertEquals("teal", accentFor("teal").id)
        assertEquals("orange", accentFor("nonsense").id)
        val custom = accentFor("#12AB34")
        assertEquals("#12AB34", custom.id)
        assertEquals(custom.tones.fill, parseHexColor(hexOf(custom.tones.fill)))
    }

    @Test fun parseHexHandlesBothLengthsAndRejectsGarbage() {
        assertEquals(Color(0xFFFF6600), parseHexColor("#FF6600"))
        assertEquals(Color(0x80FF6600), parseHexColor("#80FF6600"))
        assertEquals(null, parseHexColor("#FF66"))
        assertEquals(null, parseHexColor("zzzzzz"))
        assertEquals(null, parseHexColor("-12345"))
        assertEquals(null, parseHexColor("+12345"))
        assertEquals(null, parseHexColor("#"))
    }

    @Test fun legacyOrangeIdAndNullResolveToTheSameTones() {
        assertEquals(accentFor(null).tones, accentFor("orange").tones)
    }

    @Test fun forThemePicksTheRightText() {
        assertEquals(KtmOrange.textOnDark, KtmOrange.forTheme(true).text)
        assertEquals(KtmOrange.textOnLight, KtmOrange.forTheme(false).text)
        assertNotNull(KtmOrange.forTheme(false).container)
    }
}
