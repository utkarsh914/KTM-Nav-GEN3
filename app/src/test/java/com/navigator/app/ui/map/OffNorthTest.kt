package com.navigator.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OffNorthTest {
    @get:Rule val rule = createComposeRule()

    private val bearing = mutableFloatStateOf(0f)
    private var composed = 0
    private var shown = false

    @Composable private fun Reader() {
        val offNorth by rememberOffNorth { bearing.floatValue }
        shown = offNorth
        SideEffect { composed++ }
    }

    @Test fun theReaderRecomposesWhenTheCompassAppearsOrGoesNotOnEveryFrame() {
        rule.setContent { Reader() }
        rule.runOnIdle { assertEquals(1, composed); assertEquals(false, shown) }

        rule.runOnIdle { bearing.floatValue = 10f }
        rule.runOnIdle { assertEquals(2, composed); assertEquals(true, shown) }

        for (deg in 11..60) rule.runOnIdle { bearing.floatValue = deg.toFloat() }
        rule.runOnIdle { assertEquals("a heading change must not recompose the reader", 2, composed) }

        rule.runOnIdle { bearing.floatValue = 0.2f }
        rule.runOnIdle { assertEquals(3, composed); assertEquals(false, shown) }
    }
}
