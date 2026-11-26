package com.navigator.app.data.mirror

import com.navigator.app.ble.BccuProtocol.TurnIcon
import com.navigator.app.notifications.NotificationRepository.NavGuidance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorStatusTest {
    private val guidance = NavGuidance("400 m", "MG Road", "17:42", "12 km", "Turn right", TurnIcon.QUITE_RIGHT)

    private fun inputs(
        access: Boolean = true, listener: Boolean = true, battery: Boolean = true, paused: Boolean = false,
        g: NavGuidance? = null, text: String? = null, pkg: String? = null, last: Long = 0, now: Long = 0,
    ) = MirrorInputs(access, listener, battery, paused, g, text, pkg, last, now)

    private fun live(i: MirrorInputs) = MirrorStatusMapper.map(i) as MirrorStatus.Live

    @Test fun withoutAccessItIsSetupAndTheListenerRowCannotBeDone() {
        val s = MirrorStatusMapper.map(inputs(access = false, listener = true)) as MirrorStatus.Setup
        assertEquals(listOf(false, false, true), s.rows.map { it.done })
        assertEquals(listOf(MirrorSetupKind.Access, MirrorSetupKind.Listener, MirrorSetupKind.Battery), s.rows.map { it.kind })
    }

    @Test fun aListenerThatIsNotRunningIsItsOwnRow() {
        val s = MirrorStatusMapper.map(inputs(listener = false)) as MirrorStatus.Setup
        assertEquals(listOf(true, false, true), s.rows.map { it.done })
    }

    @Test fun theBatteryTipNeverBlocksReady() {
        val s = MirrorStatusMapper.map(inputs(battery = false))
        assertTrue(s is MirrorStatus.Ready)
        assertFalse((s as MirrorStatus.Ready).rows.last().done)
        assertFalse(s.rows.last().required)
    }

    @Test fun setUpWithNoGuidanceIsReady() {
        assertTrue(MirrorStatusMapper.map(inputs()) is MirrorStatus.Ready)
        assertTrue("a package with nothing to show is not a trip", MirrorStatusMapper.map(inputs(pkg = "maps")) is MirrorStatus.Ready)
    }

    @Test fun pausingClearsGuidanceButTheScreenKeepsTheResumeSwitch() {
        val s = MirrorStatusMapper.map(inputs(paused = true)) as MirrorStatus.Ready
        assertTrue(s.paused)
        assertFalse((MirrorStatusMapper.map(inputs()) as MirrorStatus.Ready).paused)
    }

    @Test fun guidanceMakesItLiveWithItsAge() {
        val s = live(inputs(g = guidance, pkg = "maps", last = 10_000, now = 12_000))
        assertEquals(Freshness.JustNow, s.freshness)
        assertFalse(s.stale)
        assertEquals("400 m", s.turn.distance)
        assertEquals("Turn right", s.turn.instruction)
    }

    @Test fun thirtySecondsOfSilenceIsStaleButNotWhilePaused() {
        val quiet = inputs(g = guidance, pkg = "maps", last = 1_000, now = 40_000)
        assertTrue(live(quiet).stale)
        assertEquals(Freshness.Seconds(39), live(quiet).freshness)
        assertFalse(live(quiet.copy(paused = true)).stale)
        assertTrue(live(quiet.copy(paused = true)).paused)
        assertFalse(live(quiet.copy(nowMs = 20_000)).stale)
    }

    @Test fun freshnessLabelsRoundDownToSecondsThenMinutes() {
        assertEquals(Freshness.JustNow, Freshness.of(2_999))
        assertEquals(Freshness.Seconds(3), Freshness.of(3_000))
        assertEquals(Freshness.Seconds(59), Freshness.of(59_999))
        assertEquals(Freshness.Minutes(1), Freshness.of(60_000))
        assertEquals(Freshness.Minutes(3), Freshness.of(200_000))
        assertEquals(Freshness.JustNow, Freshness.of(-5))
    }

    @Test fun aSentenceInPlaceOfADistanceBecomesTheInstruction() {
        val t = MirrorTurn.from(NavGuidance("Head toward MG Road", "MG Road", null, null, null), null)
        assertNull(t.distance)
        assertEquals("Head toward MG Road", t.instruction)
        assertNull("the road is not said twice", t.road)
    }

    @Test fun textOnlyGuidanceFallsBackToTheNotificationText() {
        val t = MirrorTurn.from(null, "Continue straight")
        assertEquals("Continue straight", t.instruction)
        assertNull(t.distance)
    }

    @Test fun distanceDetectionHandlesUnitsAndDecimalCommas() {
        listOf("400 m", "1.2 km", "1,2 km", "550 ft", "0.3 mi", " 200 meters").forEach { assertTrue(it, MirrorTurn.looksLikeDistance(it)) }
        listOf("Head toward X", "Turn right", "", null).forEach { assertFalse(it.toString(), MirrorTurn.looksLikeDistance(it)) }
    }

    @Test fun blankFieldsAreMissingNotEmpty() {
        val t = MirrorTurn.from(NavGuidance("400 m", " ", "", "12 km", "", null), null)
        assertNull(t.road); assertNull(t.instruction); assertNull(t.eta)
        assertEquals("12 km", t.remaining)
    }

    @Test fun theStatusWithoutItsAgeDoesNotChangeFromOneSecondToTheNext() {
        val turn = MirrorTurn(null, "400 m", "Turn right", "MG Road", null, null)
        val a = MirrorStatus.Live(turn, Freshness.Seconds(5), paused = false, stale = false)
        val b = MirrorStatus.Live(turn, Freshness.Seconds(6), paused = false, stale = false)
        assertEquals(a.withoutAge(), b.withoutAge())
        assertEquals("a real change still shows", false, a.withoutAge() == a.copy(stale = true).withoutAge())
        val ready = MirrorStatus.Ready(emptyList())
        assertEquals(ready, ready.withoutAge())
    }
}
