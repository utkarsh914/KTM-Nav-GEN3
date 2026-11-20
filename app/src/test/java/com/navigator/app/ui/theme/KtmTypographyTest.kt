package com.navigator.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class KtmTypographyTest {
    private val condensed = FontFamily.Serif
    private val barlow = FontFamily.SansSerif
    private val mono = FontFamily.Monospace
    private val t = ktmTypography(condensed, barlow)
    private val x = ktmTypeExtras(condensed, barlow, mono)

    private fun TextStyle.check(family: FontFamily, weight: FontWeight, size: Int, line: Int) {
        assertEquals(family, fontFamily)
        assertEquals(weight, fontWeight)
        assertEquals(size.toFloat(), fontSize.value, 0f)
        assertEquals(line.toFloat(), lineHeight.value, 0f)
    }

    @Test fun displayAndHeadlineRolesAreCondensedBold() {
        t.displayLarge.check(condensed, FontWeight.Bold, 64, 64)
        t.displayMedium.check(condensed, FontWeight.Bold, 46, 48)
        t.displaySmall.check(condensed, FontWeight.Bold, 36, 40)
        t.headlineLarge.check(condensed, FontWeight.Bold, 30, 36)
        t.headlineMedium.check(condensed, FontWeight.Bold, 28, 32)
        t.headlineSmall.check(condensed, FontWeight.Bold, 24, 28)
        t.titleLarge.check(condensed, FontWeight.Bold, 19, 24)
    }

    @Test fun titlesBodyAndLabelsMatchTheTable() {
        t.titleMedium.check(barlow, FontWeight.Medium, 20, 26)
        t.titleSmall.check(barlow, FontWeight.SemiBold, 14, 20)
        t.bodyLarge.check(barlow, FontWeight.Medium, 16, 24)
        t.bodyMedium.check(barlow, FontWeight.Normal, 15, 22)
        t.bodySmall.check(barlow, FontWeight.Normal, 14, 20)
        t.labelLarge.check(condensed, FontWeight.Bold, 18, 22)
        t.labelMedium.check(barlow, FontWeight.SemiBold, 15, 20)
        t.labelSmall.check(barlow, FontWeight.Medium, 13, 18)
    }

    @Test fun extensionsMatchTheTable() {
        x.buttonCompact.check(condensed, FontWeight.Bold, 16, 20)
        x.ridingLabel.check(barlow, FontWeight.SemiBold, 17, 22)
        x.ridingInstructionLand.check(barlow, FontWeight.Medium, 22, 28)
        x.ridingSupport.check(barlow, FontWeight.Medium, 17, 22)
        x.ridingDistanceNarrow.check(condensed, FontWeight.Bold, 50, 52)
        x.ridingInstructionNarrow.check(barlow, FontWeight.Medium, 19, 24)
        x.mono.check(mono, FontWeight.Normal, 13, 18)
    }

    @Test fun numbersAreTabularAndNoRoleSpacesLetters() {
        listOf(t.displayLarge, t.displayMedium, t.displaySmall, t.headlineMedium, t.labelLarge)
            .forEach { assertEquals("tnum", it.fontFeatureSettings) }
        listOf(t.displayLarge, t.titleSmall, t.bodyMedium, t.labelSmall, x.mono)
            .forEach { assertEquals(0f, it.letterSpacing.value, 0f) }
    }

    @Test fun smallestTextIsThirteen() {
        val all = listOf(
            t.displayLarge, t.displayMedium, t.displaySmall, t.headlineLarge, t.headlineMedium, t.headlineSmall,
            t.titleLarge, t.titleMedium, t.titleSmall, t.bodyLarge, t.bodyMedium, t.bodySmall,
            t.labelLarge, t.labelMedium, t.labelSmall,
            x.buttonCompact, x.ridingLabel, x.ridingInstructionLand, x.ridingSupport, x.mono,
            x.ridingDistanceNarrow, x.ridingInstructionNarrow,
        )
        assertEquals(13f, all.minOf { it.fontSize.value }, 0f)
        assertEquals(22, all.size)
    }

    @Test fun defaultFamiliesFollowTheFontMode() {
        AppFonts.apply(FontMode.SYSTEM)
        try {
            assertEquals(FontFamily.Default, ktmTypography().bodyLarge.fontFamily)
        } finally {
            AppFonts.apply(FontMode.BRAND)
        }
        assertNotEquals(FontFamily.Default, ktmTypography().bodyLarge.fontFamily)
    }
}
