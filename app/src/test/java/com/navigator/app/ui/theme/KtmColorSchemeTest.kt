package com.navigator.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KtmColorSchemeTest {
    /** Every Color role of a [ColorScheme], by getter name. */
    private fun roles(cs: ColorScheme): Map<String, Long> =
        ColorScheme::class.java.methods
            .filter { it.name.startsWith("get") && it.parameterCount == 0 && it.returnType == java.lang.Long.TYPE }
            .associate { it.name.substringBefore('-') to it.invoke(cs) as Long }

    private fun assertNoDefaultsLeak(ours: ColorScheme, defaults: ColorScheme) {
        val a = roles(ours)
        val d = roles(defaults)
        assertTrue("found only ${a.size} roles", a.size >= 38)
        // These are deliberately Material's own value: a black scrim, and white content / lowest
        // surface in the light theme (the design's surf and on-status are pure white).
        val intended = setOf("getScrim", "getOnError", "getOnSecondary", "getOnTertiary", "getSurfaceContainerLowest")
        val leaked = a.filter { (k, v) -> k !in intended && d[k] == v }.keys
        assertTrue("roles still on Material defaults: $leaked", leaked.isEmpty())
    }

    @Test fun darkSchemeSetsEveryRole() =
        assertNoDefaultsLeak(ktmColorScheme(KtmDarkColors, KtmOrange.forTheme(true)), darkColorScheme())

    @Test fun lightSchemeSetsEveryRole() =
        assertNoDefaultsLeak(ktmColorScheme(KtmLightColors, KtmOrange.forTheme(false)), lightColorScheme())

    @Test fun everyAccentFillsPrimaryNotTheDefaultPurple() {
        for (p in AccentPalettes) for (dark in listOf(true, false)) {
            val cs = ktmColorScheme(if (dark) KtmDarkColors else KtmLightColors, p.tones.forTheme(dark))
            assertEquals(p.tones.fill, cs.primary)
        }
    }

    @Test fun neutralsMapToTheDesignRoles() {
        val d = ktmColorScheme(KtmDarkColors, KtmOrange.forTheme(true))
        assertEquals(KtmDarkColors.bg, d.background)
        assertEquals(KtmDarkColors.surf, d.surface)
        assertEquals(KtmDarkColors.surf2, d.surfaceContainer)
        assertEquals(KtmDarkColors.surf3, d.surfaceContainerHigh)
        assertEquals(KtmDarkColors.line, d.outlineVariant)
        assertEquals(KtmDarkColors.line2, d.outline)
        assertEquals(KtmDarkColors.fg2, d.onSurfaceVariant)
        assertEquals(KtmDarkColors.inv, d.inverseSurface)
        assertEquals(KtmDarkColors.bad, d.error)
    }

    @Test fun bodyTextHasContrastOnEverySurface() {
        for (c in listOf(KtmDarkColors, KtmLightColors)) {
            for (bg in listOf(c.bg, c.surf, c.surf2, c.surf3)) {
                assertTrue(contrastRatio(c.fg, bg) >= 7.0)
                assertTrue(contrastRatio(c.fg2, bg) >= 4.5)
                // fg3 is the subtle text: 4.5+ on bg and surf; the tonal light surfaces dip to ~4.0.
                val min = if (bg == c.bg || bg == c.surf) 4.5 else 4.0
                assertTrue("fg3 on $bg ${contrastRatio(c.fg3, bg)}", contrastRatio(c.fg3, bg) >= min)
            }
            assertTrue(contrastRatio(c.line2, c.surf) >= 1.5)
        }
    }

    @Test fun statusColoursReadOnTheirOwnSurfaces() {
        for (c in listOf(KtmDarkColors, KtmLightColors)) {
            for (s in listOf(c.ok, c.warn, c.bad, c.info)) {
                assertTrue(contrastRatio(s, c.surf) >= 3.0)
                // Light ok (#1B8A4E) with white is 4.4: fine for button and badge labels.
                assertTrue(contrastRatio(c.onStatus, s) >= 4.3)
            }
        }
    }

    @Test fun ridingColoursAreFixedAndLegible() {
        for (r in listOf(RidingDay, RidingNight)) {
            assertTrue(contrastRatio(r.onGuidance, r.guidance) >= 6.0)
            assertTrue(contrastRatio(r.onSurface, r.surface) >= 15.0)
            assertTrue(contrastRatio(r.onWarnChip, r.warnChip) >= 9.0)
            // Overspeed is for large numerals only (the design quotes 4.6; WCAG gives 4.3).
            assertTrue(contrastRatio(r.onOverspeed, r.overspeed) >= 4.0)
        }
    }
}
