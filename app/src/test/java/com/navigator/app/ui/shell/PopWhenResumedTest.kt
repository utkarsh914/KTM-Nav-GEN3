package com.navigator.app.ui.shell

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PopWhenResumedTest {
    @get:Rule val rule = createComposeRule()

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test fun aCoveredPageLeavesOnceItIsResumedAgain() {
        val owner = Owner().apply { registry.currentState = Lifecycle.State.STARTED }
        var pops = 0
        var gone by mutableStateOf(true)
        rule.setContent { CompositionLocalProvider(LocalLifecycleOwner provides owner) { PopWhenResumed(gone) { pops++ } } }
        rule.waitForIdle()
        assertEquals("covered: the pop waits", 0, pops)
        rule.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        rule.waitForIdle()
        assertEquals(1, pops)
    }

    @Test fun nothingPopsWhileThePageIsStillThere() {
        val owner = Owner().apply { registry.currentState = Lifecycle.State.RESUMED }
        var pops = 0
        rule.setContent { CompositionLocalProvider(LocalLifecycleOwner provides owner) { PopWhenResumed(false) { pops++ } } }
        rule.waitForIdle()
        assertEquals(0, pops)
    }
}
