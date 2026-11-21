package com.navigator.app.ui.theme

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Riding text stops growing at 1.15x, except in a sheet with scrolling body text, which follows the rider's setting. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RidingFontScaleTest {
    @get:Rule val rule = createComposeRule()

    @Test fun theClampHoldsInsideButASheetOutsideItGetsTheRidersScaleBack() {
        var riding = 0f
        var sheet = 0f
        var plain = 0f
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                KtmTheme(darkTheme = true) {
                    plain = LocalDensity.current.fontScale
                    RidingTheme(night = true) {
                        riding = LocalDensity.current.fontScale
                        RidingTheme(night = true, controlSystemBars = false, clampFont = false) { sheet = LocalDensity.current.fontScale }
                    }
                }
            }
        }
        assertEquals(2f, plain, 0.001f)
        assertEquals(RIDING_MAX_FONT_SCALE, riding, 0.001f)
        assertEquals(2f, sheet, 0.001f)
    }
}
