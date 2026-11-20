package com.navigator.app.nav

import com.navigator.app.ble.BccuProtocol.TurnIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DashPreviewTeeTest {

    private class Recorder : DashOutput {
        val calls = mutableListOf<String>()
        override fun setNavState(guidanceOn: Boolean, gpsIconOn: Boolean) { calls += "nav $guidanceOn $gpsIconOn" }
        override fun turnIcon(icon: TurnIcon) { calls += "icon $icon" }
        override fun guidance(distance: String?, road: String?, eta: String?, remaining: String?) {
            calls += "g $distance|$road|$eta|$remaining"
        }
        override fun clearNow() { calls += "clearNow" }
        override fun clearDebounced() { calls += "clearDebounced" }
        override fun offlineBanner(show: Boolean) { calls += "offline $show" }
    }

    @Before fun resetPreview() = DashPreview.reset()

    @Test fun forwardsEveryCallUnchangedAndRecordsState() {
        val rec = Recorder()
        val tee = DashPreviewTee(rec)
        tee.setNavState(true, true)
        tee.turnIcon(TurnIcon.KEEP_LEFT)
        tee.guidance("200 m", "NH 48", null, null)
        tee.guidance(null, null, "12:45", "9 km")
        tee.offlineBanner(true)
        assertEquals(
            listOf("nav true true", "icon KEEP_LEFT", "g 200 m|NH 48|null|null", "g null|null|12:45|9 km", "offline true"),
            rec.calls,
        )
        val s = DashPreview.state.value
        assertEquals(TurnIcon.KEEP_LEFT, s.icon)
        assertEquals("200 m", s.distance)
        assertEquals("NH 48", s.road)
        assertEquals("12:45", s.eta)
        assertEquals("9 km", s.remaining)
        assertEquals(true, s.offlineBanner)

        tee.clearDebounced()
        tee.clearNow()
        assertEquals(listOf("clearDebounced", "clearNow"), rec.calls.takeLast(2))
        assertNull(DashPreview.state.value.icon)
        assertNull(DashPreview.state.value.distance)
    }

    @Test fun aClearedBannerIsNoLongerShownAsOnTheDash() {
        DashPreview.banner("WhatsApp: hi")
        assertEquals("WhatsApp: hi", DashPreview.state.value.lastBanner)
        DashPreview.clearBanner()
        assertNull(DashPreview.state.value.lastBanner)
    }
}
