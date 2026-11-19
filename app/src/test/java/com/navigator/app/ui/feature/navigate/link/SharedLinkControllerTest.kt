package com.navigator.app.ui.feature.navigate.link

import com.navigator.app.nav.destination.LinkFailure
import com.navigator.app.nav.destination.SavedPlace
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SharedLinkControllerTest {
    private val toit = SavedPlace(12.97, 77.64, "Toit")

    @Test fun aLinkShowsLoadingThenHandsOverThePlace() = runTest {
        val gate = CompletableDeferred<LinkOutcome>()
        val c = SharedLinkController(backgroundScope) { gate.await() }
        val got = mutableListOf<SavedPlace>()
        backgroundScope.launch { c.resolved.toList(got) }
        c.open("https://maps.app.goo.gl/x"); runCurrent()
        assertEquals(SharedLinkState.Loading, c.state.value)
        gate.complete(LinkOutcome.Found(toit)); runCurrent()
        assertEquals(SharedLinkState.Idle, c.state.value)
        assertEquals(listOf(toit), got)
    }

    @Test fun aFailureCanBeRetriedWithTheSameLink() = runTest {
        val tried = mutableListOf<String>()
        var ok = false
        val c = SharedLinkController(backgroundScope) { link -> tried += link; if (ok) LinkOutcome.Found(toit) else LinkOutcome.Failed(LinkFailure.NO_CONNECTION) }
        val got = mutableListOf<SavedPlace>()
        backgroundScope.launch { c.resolved.toList(got) }
        c.open("L"); runCurrent()
        assertEquals(SharedLinkState.Failed, c.state.value)
        assertEquals(LinkFailure.NO_CONNECTION, c.failure.value)
        ok = true
        c.retry(); runCurrent()
        assertEquals(listOf("L", "L"), tried)
        assertEquals(listOf(toit), got)
    }

    @Test fun aLookupThatThrowsIsAFailureNotACrash() = runTest {
        val c = SharedLinkController(backgroundScope) { error("boom") }
        c.open("L"); runCurrent()
        assertEquals(SharedLinkState.Failed, c.state.value)
        assertEquals(LinkFailure.UNREADABLE, c.failure.value)
    }

    @Test fun aNewerLinkReplacesTheOlderOne() = runTest {
        val gates = mutableMapOf<String, CompletableDeferred<LinkOutcome>>()
        val c = SharedLinkController(backgroundScope) { link -> gates.getOrPut(link) { CompletableDeferred() }.await() }
        val got = mutableListOf<SavedPlace>()
        backgroundScope.launch { c.resolved.toList(got) }
        c.open("old"); runCurrent()
        c.open("new"); runCurrent()
        gates["old"]!!.complete(LinkOutcome.Found(SavedPlace(1.0, 1.0, "Old")))
        gates["new"]!!.complete(LinkOutcome.Found(toit)); runCurrent()
        assertEquals(listOf(toit), got)
    }

    @Test fun dismissingStopsEverythingAndForgetsTheLink() = runTest {
        val gate = CompletableDeferred<LinkOutcome>()
        val c = SharedLinkController(backgroundScope) { gate.await() }
        val got = mutableListOf<SavedPlace>()
        backgroundScope.launch { c.resolved.toList(got) }
        c.open("L"); runCurrent()
        c.dismiss(); gate.complete(LinkOutcome.Found(toit)); runCurrent()
        assertEquals(SharedLinkState.Idle, c.state.value)
        assertEquals(emptyList<SavedPlace>(), got)
        c.retry(); runCurrent()
        assertEquals("nothing left to retry", SharedLinkState.Idle, c.state.value)
    }
}
