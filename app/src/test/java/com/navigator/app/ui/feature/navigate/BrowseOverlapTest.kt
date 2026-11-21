package com.navigator.app.ui.feature.navigate

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.navigator.app.ui.theme.KtmTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** On a small phone, what hangs from the top of the Browse map never sits on top of anything else. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp")
class BrowseOverlapTest {
    @get:Rule val rule = createComposeRule()

    @Test fun theGreetingSitsAboveTheLocationBannerNotOnIt() {
        rule.setContent {
            KtmTheme(darkTheme = true) {
                NavigateBrowse(
                    state = NavigateUiState(tiles = WhereToTiles.build(emptyList(), emptyList()) { null }, greeting = GreetingUi("Asha")),
                    location = LocationState.PermissionOff, mapState = MapState.Preparing, mapBearing = 0f, voiceAvailable = false,
                    actions = NavigateBrowseActions(WhereToActions({}, {}, {}, {}, {}, {}, {}), {}, {}, {}, {}, {}, {}, { _, _ -> }),
                    recordControls = {},
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Hi Asha!").assertIsDisplayed()
        val greeting = rule.onNodeWithText("Hi Asha!").getUnclippedBoundsInRoot()
        val banner = rule.onNodeWithText("Location permission is off").getUnclippedBoundsInRoot()
        assertTrue("greeting ends ${greeting.bottom}, banner starts ${banner.top}", greeting.bottom <= banner.top)
    }
}
