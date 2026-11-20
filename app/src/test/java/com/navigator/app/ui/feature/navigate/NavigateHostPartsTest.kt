package com.navigator.app.ui.feature.navigate

import androidx.compose.runtime.saveable.SaverScope
import com.navigator.app.nav.destination.SavedPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigateHostPartsTest {
    @Test fun theRidingOverlaysStartClosedAndAreIndependent() {
        val s = RidingOverlayState()
        assertFalse(s.showTripSheet || s.showTripStops || s.showBike || s.showEndConfirm)
        s.showTripSheet = true
        assertTrue(s.showTripSheet)
        assertFalse(s.showBike)
        assertFalse(s.mapButton.overview)
    }

    @Test fun aPendingStopCandidateSurvivesTheSaver() {
        val saver = SavedPlaceSaver
        val scope = SaverScope { true }
        val place = SavedPlace(12.9, 77.6, "Toit", address = "Indiranagar")
        assertEquals(place, saver.restore(with(saver) { scope.save(place)!! }))
        val bare = SavedPlace(1.0, 2.0, "Pin")
        assertEquals(bare, saver.restore(with(saver) { scope.save(bare)!! }))
        assertNull(saver.restore(with(saver) { scope.save(null)!! }))
    }
}
