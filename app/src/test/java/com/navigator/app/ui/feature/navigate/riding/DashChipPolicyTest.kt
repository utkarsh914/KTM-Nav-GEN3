package com.navigator.app.ui.feature.navigate.riding

import com.navigator.app.data.connection.BikeStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class DashChipPolicyTest {
    private fun cls(s: BikeStatus) = DashChipPolicy.classOf(s)
    private val grace = DashChipPolicy.DOWN_GRACE_MS

    @Test fun everyStatusHasTheClassItsWordsFit() {
        assertEquals(DashClass.UP, cls(BikeStatus.Connected("KTM")))
        assertEquals(DashClass.TRYING, cls(BikeStatus.Connecting("KTM", firstPair = false)))
        assertEquals(DashClass.TRYING, cls(BikeStatus.Reconnecting("KTM", attempt = 2)))
        assertEquals(DashClass.DOWN, cls(BikeStatus.Disconnected("KTM")))
        assertEquals(DashClass.NEEDS_REPAIR, cls(BikeStatus.NeedsRepair("KTM")))
        assertEquals(DashClass.BLUETOOTH_OFF, cls(BikeStatus.BluetoothOff))
        assertEquals(DashClass.NOT_PAIRED, cls(BikeStatus.NotPaired))
    }

    @Test fun aLinkThatIsDownGetsItsChipOnlyAfterTheGraceAndWithWordsThatFit() {
        assertEquals(DashChipStep.After(grace, DashChip.LOST_RETRYING), DashChipPolicy.step(DashClass.TRYING, lostShown = false))
        assertEquals(DashChipStep.After(grace, DashChip.LOST), DashChipPolicy.step(DashClass.DOWN, lostShown = false))
        assertEquals(DashChipStep.After(grace, DashChip.LOST), DashChipPolicy.step(DashClass.NEEDS_REPAIR, lostShown = false))
        assertEquals(DashChipStep.After(grace, DashChip.BLUETOOTH_OFF), DashChipPolicy.step(DashClass.BLUETOOTH_OFF, lostShown = false))
    }

    @Test fun theGreenChipFollowsOnlyARedOne() {
        assertEquals(DashChipStep.Now(DashChip.BACK), DashChipPolicy.step(DashClass.UP, lostShown = true))
        assertEquals("a blip that never showed red does not announce a return", DashChipStep.Nothing, DashChipPolicy.step(DashClass.UP, lostShown = false))
    }

    @Test fun anUnpairedBikeSaysNothing() {
        assertEquals(DashChipStep.Nothing, DashChipPolicy.step(DashClass.NOT_PAIRED, lostShown = false))
        assertEquals(DashChipStep.Nothing, DashChipPolicy.step(DashClass.NOT_PAIRED, lostShown = true))
    }
}
