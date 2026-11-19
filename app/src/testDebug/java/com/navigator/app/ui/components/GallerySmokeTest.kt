package com.navigator.app.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.TokenGallery
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Composes the debug galleries for every theme: catches runtime crashes the compiler can't (bad require, modifiers). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w400dp-h2400dp")
class GallerySmokeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun tokensComposeInDark() {
        rule.setContent { KtmTheme(darkTheme = true) { TokenGallery() } }
        rule.waitForIdle()
    }

    @Test fun tokensComposeInLight() {
        rule.setContent { KtmTheme(darkTheme = false) { TokenGallery() } }
        rule.waitForIdle()
    }

    @Test fun componentsComposeInDark() {
        rule.setContent { KtmTheme(darkTheme = true) { ComponentGallery() } }
        rule.waitForIdle()
    }

    @Test fun componentsComposeInLightWithACustomAccent() {
        rule.setContent {
            KtmTheme(darkTheme = false, accent = com.navigator.app.ui.theme.accentFor("#8A66C6")) { ComponentGallery() }
        }
        rule.waitForIdle()
    }
}
