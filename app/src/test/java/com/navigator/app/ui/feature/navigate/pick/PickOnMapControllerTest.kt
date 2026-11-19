package com.navigator.app.ui.feature.navigate.pick

import com.navigator.app.ui.map.FakeMapController
import com.navigator.app.ui.map.MapPoint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PickOnMapControllerTest {
    private val here = MapPoint(12.97, 77.59)
    private val there = MapPoint(13.0, 77.6)

    @Test fun streetNamePrefersTheStreetThenTheAreaAndSkipsBareNumbers() {
        assertEquals("100 Feet Rd", StreetName.pick("100 Feet Rd", "Indiranagar", "Bengaluru", "12"))
        assertEquals("Indiranagar", StreetName.pick(null, "Indiranagar", "Bengaluru", null))
        assertEquals("Toit", StreetName.pick("", null, "Bengaluru", "Toit"))
        assertEquals("Bengaluru", StreetName.pick(null, null, "Bengaluru", "42"))
        assertNull(StreetName.pick(null, " ", null, "7"))
    }

    @Test fun startingSwitchesTheCameraListenerOnAndStoppingOff() = runTest {
        val map = FakeMapController().apply { center = here }
        val c = PickOnMapController(backgroundScope, map, { "Main Rd" })
        c.start(); runCurrent()
        assertTrue(map.idleListening)
        assertEquals(here, c.state.value.center)
        assertEquals("Main Rd", c.state.value.street)
        c.stop()
        assertFalse(map.idleListening)
    }

    @Test fun eachSettledCameraLooksUpTheStreetUnderThePinAndKeepsTheLatest() = runTest {
        val map = FakeMapController().apply { center = here }
        val gates = mutableMapOf<MapPoint, CompletableDeferred<String?>>()
        val c = PickOnMapController(backgroundScope, map, { p -> gates.getOrPut(p) { CompletableDeferred() }.await() })
        c.start(); runCurrent()
        map.cameraIdle.tryEmit(there); runCurrent()
        // The older lookup finishing late must not overwrite the newer point's.
        gates[here]!!.complete("Old Rd"); gates[there]!!.complete("New Rd"); runCurrent()
        assertEquals(there, c.state.value.center)
        assertEquals("New Rd", c.state.value.street)
        assertFalse(c.state.value.resolving)
    }

    @Test fun usingThePointNamesItByStreetOrFallsBack() = runTest {
        val map = FakeMapController().apply { center = here }
        val c = PickOnMapController(backgroundScope, map, { null })
        assertNull("before the map is ready there is no point", c.use("Dropped pin"))
        c.start(); runCurrent()
        assertEquals(com.navigator.app.nav.destination.SavedPlace(12.97, 77.59, "Dropped pin"), c.use("Dropped pin"))
    }
}
