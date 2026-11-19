package com.navigator.app.ui.map

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pins the contract view models are written against: clearing drops what was drawn, events reach collectors. */
class FakeMapControllerTest {
    private val here = MapPoint(12.97, 77.59)

    @Test fun clearingForgetsWhatWasDrawnIncludingTheUserMarker() {
        val map = FakeMapController()
        map.showPlace(here, "Toit"); map.showUserMarker(here, 90f)
        map.clearOverlays()
        assertNull(map.place); assertNull(map.userMarker)
        assertEquals(listOf("place", "clear"), map.calls)
    }

    @Test fun mapEventsReachWhoeverCollects() = runTest {
        val map = FakeMapController()
        val longs = mutableListOf<MapPoint>(); val pans = mutableListOf<Unit>()
        backgroundScope.launch { map.longClicks.toList(longs) }
        backgroundScope.launch { map.gesturePans.toList(pans) }
        runCurrent()
        map.longClicks.tryEmit(here); map.gesturePans.tryEmit(Unit)
        runCurrent()
        assertEquals(listOf(here), longs); assertEquals(1, pans.size)
    }
}
