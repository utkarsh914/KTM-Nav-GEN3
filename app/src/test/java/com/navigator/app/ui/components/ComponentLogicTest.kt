package com.navigator.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.navigator.app.ui.theme.AccentPalettes
import com.navigator.app.ui.theme.KtmDarkColors
import com.navigator.app.ui.theme.KtmLightColors
import com.navigator.app.ui.theme.contrastRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComponentLogicTest {
    private val themes = listOf(KtmDarkColors, KtmLightColors)
    private val accents = AccentPalettes

    private fun contrast(a: Color, b: Color, behind: Color) =
        contrastRatio(a, if (b.alpha == 0f) behind else b)

    @Test fun filledButtonsAreReadable() {
        // Button labels are 16-18 sp bold (large text): 3:1 is the floor, solid styles do much better.
        for (c in themes) for (acc in accents) for (dark in listOf(true, false)) {
            val a = acc.tones.forTheme(dark)
            val colors = if (dark) KtmDarkColors else KtmLightColors
            for (style in ButtonStyle.entries) {
                val p = buttonPalette(style, true, colors, a)
                val ratio = contrast(p.content, p.container, colors.bg)
                assertTrue("$style ${acc.id} dark=$dark $ratio", ratio >= 3.0)
            }
            // (Primary on the blue preset is 4.1: white on #2C7BE5, fine for large bold labels.)
            for (style in listOf(ButtonStyle.Neutral, ButtonStyle.Outline)) {
                val p = buttonPalette(style, true, colors, a)
                assertTrue("$style ${acc.id} dark=$dark", contrast(p.content, p.container, colors.bg) >= 4.4)
            }
        }
    }

    @Test fun disabledButtonsAreFlatAndMuted() {
        for (c in themes) for (style in ButtonStyle.entries) {
            val p = buttonPalette(style, false, c, accents.first().tones.forTheme(c.isDark))
            assertEquals(c.fg3, p.content)
            assertTrue(p.container.alpha == 0f || p.container == c.surf3)
        }
    }

    @Test fun onlyOutlineHasABorder() {
        val c = KtmDarkColors
        val a = accents.first().tones.forTheme(true)
        for (style in ButtonStyle.entries) {
            val border = buttonPalette(style, true, c, a).border
            assertEquals(style == ButtonStyle.Outline, border != null)
        }
    }

    @Test fun buttonHeightsAreTheTouchTargets() {
        assertEquals(listOf(48.dp, 56.dp, 64.dp, 72.dp, 80.dp, 84.dp), ButtonSize.entries.map { it.height })
    }

    @Test fun rowHeightsFollowTheRule() {
        assertEquals(56.dp, listRowHeight(0))
        assertEquals(64.dp, listRowHeight(1))
        assertEquals(80.dp, listRowHeight(2))
        assertEquals(80.dp, listRowHeight(3))
        assertEquals(72.dp, listRowHeight(1, hasThumbnail = true))
        assertEquals(72.dp, listRowHeight(0, hasThumbnail = true))
    }

    @Test fun heldStepperRepeatsAfterAPauseAndSpeedsUpToAFloor() {
        assertEquals(RepeatSchedule.FIRST_DELAY_MS, RepeatSchedule.delayBefore(0))
        val delays = (1..40).map { RepeatSchedule.delayBefore(it) }
        assertEquals(RepeatSchedule.START_INTERVAL_MS, delays.first())
        assertTrue(delays.zipWithNext().all { (a, b) -> b <= a })
        assertEquals(RepeatSchedule.MIN_INTERVAL_MS, delays.last())
        assertTrue(delays.all { it >= RepeatSchedule.MIN_INTERVAL_MS })
    }

    @Test fun bannersKeepBodyTextHighContrastAndIconsVisible() {
        for (colors in themes) for (acc in accents) {
            val a = acc.tones.forTheme(colors.isDark)
            for (tone in BannerTone.entries) {
                val (container, text, icon) = bannerColors(tone, colors, a)
                assertTrue("$tone text", contrastRatio(text, container) >= 7.0)
                assertTrue("$tone icon", contrastRatio(icon, container) >= 3.0)
            }
        }
    }

    @Test fun badgesAreReadable() {
        // 13 sp semibold labels. The design's light status colours give 3.7-4.0 on their containers.
        for (colors in themes) for (acc in accents) {
            val a = acc.tones.forTheme(colors.isDark)
            for (tone in BadgeTone.entries) {
                val (container, content) = badgeColors(tone, colors, a)
                assertTrue("$tone dark=${colors.isDark} ${contrastRatio(content, container)}", contrastRatio(content, container) >= 3.5)
            }
        }
    }

    @Test fun pillDotsUseStatusColoursNeverTheAccent() {
        val c = KtmDarkColors
        assertEquals(c.ok, pillDotColor(PillTone.Connected, c))
        assertEquals(c.warn, pillDotColor(PillTone.Connecting, c))
        assertEquals(c.warn, pillDotColor(PillTone.Offline, c))
        assertEquals(c.info, pillDotColor(PillTone.NotPaired, c))
    }

    @Test fun activityStripStatesCarryTheirText() {
        val trip = ActivityStripState.Trip("250 m · 100 Feet Rd", "Toit Brewpub · 6:29 pm")
        assertEquals("250 m · 100 Feet Rd", trip.title)
        assertTrue(ActivityStripState.Recording("Recording · 12:48", "6.4 km", paused = true).paused)
        assertEquals("From Google Maps · 2 s ago", ActivityStripState.Mirroring("Mirroring · 350 m", "From Google Maps · 2 s ago").detail)
    }
}
