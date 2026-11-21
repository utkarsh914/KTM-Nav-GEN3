package com.navigator.app.ui.feature.navigate

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.navigator.app.ui.theme.KtmTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w400dp-h800dp")
class WhereToTilesTest {
    @get:Rule val rule = createComposeRule()
    private val log = mutableListOf<String>()

    private fun show(mapUsable: Boolean) = rule.setContent {
        KtmTheme(darkTheme = true) {
            WhereToSheetContent(
                NavigateUiState(tiles = WhereToTiles.build(emptyList(), emptyList()) { null }),
                voiceAvailable = false,
                actions = WhereToActions({}, {}, { log += "tile" }, {}, {}, {}, {}),
                mapUsable = mapUsable,
            )
        }
    }

    @Test fun pickOnMapIsOfferedWhileTheMapWorks() {
        show(mapUsable = true)
        rule.onNodeWithText("Drop a pin").assertIsDisplayed()
        rule.onNodeWithText("Pick on map").performClick()
        assertEquals(listOf("tile"), log)
    }

    @Test fun pickOnMapSaysItNeedsTheMapAndIsInertWhileTheMapIsDown() {
        show(mapUsable = false)
        rule.onNodeWithText("Needs map").assertIsDisplayed()
        rule.onNodeWithText("Pick on map").assertIsNotEnabled()
        rule.onNodeWithText("Set work").assertIsDisplayed() // the other tiles keep working
    }
}
