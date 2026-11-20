package com.navigator.app.ui.feature.navigate.riding

import com.navigator.app.nav.model.LaneInfo
import com.navigator.app.nav.model.LaneShape
import com.navigator.app.nav.model.NavSessionState
import com.navigator.app.nav.model.NormalizedManeuver
import com.navigator.app.nav.model.NormalizedNavigationState
import com.navigator.app.ui.shell.ChipTone
import com.navigator.app.ui.shell.RidingChipMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class RidingModelsTest {
    private fun nav(
        session: NavSessionState = NavSessionState.ENROUTE,
        distance: Int? = 800,
        lanes: List<LaneInfo> = emptyList(),
        next: NormalizedManeuver? = NormalizedManeuver.LEFT,
        instruction: String? = "Turn right onto Foo St",
        road: String? = "Foo St",
        exitNumber: String? = null,
        exit: Int? = null,
    ) = NormalizedNavigationState(
        sessionState = session, maneuver = NormalizedManeuver.RIGHT, distanceToManeuverMeters = distance, roadName = road,
        fullInstruction = instruction, nextManeuver = next, lanes = lanes, exitNumber = exitNumber, roundaboutExit = exit,
        remainingTimeSeconds = 1_500, remainingDistanceMeters = 12_000,
    )

    private val lanes = listOf(LaneInfo(listOf(LaneShape.STRAIGHT), false), LaneInfo(listOf(LaneShape.RIGHT), true))

    @Test fun beforeTheFirstInstructionTheCardHoldsItsPlace() {
        assertEquals(ManeuverUi.Starting, ManeuverModel.from(nav(NavSessionState.IDLE)))
        assertEquals(ManeuverUi.Starting, ManeuverModel.from(nav(NavSessionState.STOPPED)))
    }

    @Test fun theThenBandShowsFarFromTheTurnAndLanesReplaceItWithin400m() {
        val far = ManeuverModel.from(nav(distance = 401, lanes = lanes)) as ManeuverUi.Active
        assertTrue(far.footer is ManeuverFooter.Then)
        val near = ManeuverModel.from(nav(distance = 400, lanes = lanes)) as ManeuverUi.Active
        assertEquals(ManeuverFooter.Lanes(lanes), near.footer)
        val noLanes = ManeuverModel.from(nav(distance = 100)) as ManeuverUi.Active
        assertTrue("without lane data the Then band stays", noLanes.footer is ManeuverFooter.Then)
        val nothingNext = ManeuverModel.from(nav(next = null)) as ManeuverUi.Active
        assertEquals(ManeuverFooter.None, nothingNext.footer)
        val unknownNext = ManeuverModel.from(nav(next = NormalizedManeuver.UNKNOWN)) as ManeuverUi.Active
        assertEquals(ManeuverFooter.None, unknownNext.footer)
    }

    @Test fun reroutingKeepsTheLastInstructionButNoFooter() {
        val a = ManeuverModel.from(nav(NavSessionState.REROUTING, lanes = lanes, distance = 50)) as ManeuverUi.Active
        assertTrue(a.rerouting)
        assertEquals("Foo St", a.road)
        assertEquals(ManeuverFooter.None, a.footer)
    }

    @Test fun anExitNumberOrARoundaboutExitBecomesTheBadge() {
        assertEquals("12A", (ManeuverModel.from(nav(exitNumber = "12A", exit = 2)) as ManeuverUi.Active).exit)
        assertEquals("2", (ManeuverModel.from(nav(exit = 2)) as ManeuverUi.Active).exit)
        assertNull((ManeuverModel.from(nav()) as ManeuverUi.Active).exit)
        assertNull((ManeuverModel.from(nav(exitNumber = " ")) as ManeuverUi.Active).exit)
    }

    @Test fun theRoadAlwaysGetsItsOwnLine() {
        assertEquals("Turn right onto" to "Foo St", ManeuverModel.splitRoad("Turn right onto Foo St", "Foo St"))
        assertEquals("Head north on toward Bar" to "Foo St", ManeuverModel.splitRoad("Head north on Foo St, toward Bar", "Foo St"))
        assertEquals("Continue straight" to "Foo St", ManeuverModel.splitRoad("Continue straight", "Foo St"))
        assertEquals("Turn left" to null, ManeuverModel.splitRoad("Turn left", null))
        assertEquals("" to "Foo St", ManeuverModel.splitRoad(null, "Foo St"))
        assertEquals("" to "Foo St", ManeuverModel.splitRoad("Foo St", "Foo St"))
        assertEquals("turn onto" to "FOO ST", ManeuverModel.splitRoad("turn onto foo st", "FOO ST"))
    }

    @Test fun timeLeftIsMinutesThenHoursAndMinutesThenWholeHours() {
        assertEquals(TimeLeft(0, 1), TimeLeft.of(1))
        assertEquals(TimeLeft(0, 59), TimeLeft.of(59 * 60))
        assertEquals(TimeLeft(1, 0), TimeLeft.of(3_600))
        assertEquals(TimeLeft.Kind.Minutes, TimeLeft.of(3_540).kind)
        assertEquals(TimeLeft(9, 59), TimeLeft.of(9 * 3_600 + 59 * 60))
        assertEquals(TimeLeft.Kind.HoursMinutes, TimeLeft.of(9 * 3_600 + 59 * 60).kind)
        assertEquals(TimeLeft.Kind.Hours, TimeLeft.of(10 * 3_600).kind)
        assertEquals(TimeLeft(0, 0), TimeLeft.of(-5))
    }

    @Test fun theBarShowsSpeedTimeDistanceAndArrival() {
        val ui = TripBarModel.from(nav(), 42.7f, null, 24_000, nowMs = 0L, destination = "Toit", zone = ZoneOffset.UTC)
        assertEquals("42", ui.speed)
        assertFalse(ui.overspeed)
        assertNull(ui.alert)
        assertEquals(TimeLeft(0, 25), ui.timeLeft)
        assertEquals("00:25", ui.arrival)
        assertEquals(0.5f, ui.progress, 0.001f)
        assertFalse(ui.starting)
    }

    @Test fun withoutAFixSpeedIsADashAndGuidanceStartingShowsTheDestination() {
        val starting = nav(NavSessionState.IDLE).copy(remainingTimeSeconds = null, remainingDistanceMeters = null)
        val ui = TripBarModel.from(starting, null, 80, null, 0L, "Toit")
        assertEquals("–", ui.speed)
        assertTrue(ui.starting)
        assertEquals("Toit", ui.destination)
        assertEquals(0f, ui.progress, 0f)
    }

    @Test fun overspeedTurnsOnAboveTheLimitAndCarriesTheAlertValue() {
        assertFalse(TripBarModel.from(nav(), 80f, 80, null, 0L, null).overspeed)
        assertFalse("90.4 shows as 90: not over a limit of 90", TripBarModel.from(nav(), 90.4f, 90, null, 0L, null).overspeed)
        val over = TripBarModel.from(nav(), 81.2f, 80, null, 0L, null)
        assertTrue(over.overspeed)
        assertEquals(80, over.alert)
        assertFalse("alert off: never red", TripBarModel.from(nav(), 200f, null, null, 0L, null).overspeed)
    }

    @Test fun progressIsOneMinusRemainingOverInitialAndStaysInRange() {
        assertEquals(0f, TripBarModel.progress(10_000, 10_000), 0f)
        assertEquals(0.75f, TripBarModel.progress(2_500, 10_000), 0.001f)
        assertEquals("a longer route after a reroute never goes negative", 0f, TripBarModel.progress(12_000, 10_000), 0f)
        assertEquals(0f, TripBarModel.progress(null, 10_000), 0f)
        assertEquals(0f, TripBarModel.progress(100, 0), 0f)
    }

    @Test fun panningShowsRecenterAndTappingItFollowsAgain() {
        var s = MapButtonState()
        assertFalse(s.recenter)
        s = MapButtonMachine.reduce(s, MapButtonEvent.Pan(1_000)).first
        assertTrue(s.recenter)
        val (after, effect) = MapButtonMachine.reduce(s, MapButtonEvent.Tap)
        assertEquals(MapButtonEffect.Follow, effect)
        assertFalse(after.recenter)
    }

    @Test fun overviewTurnsIntoRecenterAndBack() {
        val (s, e) = MapButtonMachine.reduce(MapButtonState(), MapButtonEvent.Tap)
        assertEquals(MapButtonEffect.ShowOverview, e)
        assertTrue(s.recenter)
        assertEquals(MapButtonEffect.Follow, MapButtonMachine.reduce(s, MapButtonEvent.Tap).second)
    }

    @Test fun aPannedMapFollowsAgainOnceMovingFastForAWhile() {
        val panned = MapButtonMachine.reduce(MapButtonState(), MapButtonEvent.Pan(1_000)).first
        assertEquals("too soon", MapButtonEffect.None, MapButtonMachine.reduce(panned, MapButtonEvent.Speed(40f, 5_000)).second)
        assertEquals("too slow", MapButtonEffect.None, MapButtonMachine.reduce(panned, MapButtonEvent.Speed(15f, 20_000)).second)
        val (s, e) = MapButtonMachine.reduce(panned, MapButtonEvent.Speed(16f, 11_000))
        assertEquals(MapButtonEffect.Follow, e)
        assertFalse(s.recenter)
    }

    @Test fun anOverviewIsNotSnappedBackBySpeed() {
        val overview = MapButtonMachine.reduce(MapButtonState(), MapButtonEvent.Tap).first
        assertEquals(MapButtonEffect.None, MapButtonMachine.reduce(overview, MapButtonEvent.Speed(60f, 999_999)).second)
        assertFalse(MapButtonMachine.reduce(overview, MapButtonEvent.Reset).first.recenter)
    }

    @Test fun aTimedChipBeatsTheOfflineChipWhichBeatsNothing() {
        val msg = RidingChipMessage(1, "Stop added", ChipTone.Ok)
        assertEquals(ChipSource.Timed(msg), RidingChips.pick(msg, offline = true, reroutePaused = false))
        assertEquals(ChipSource.Offline(false), RidingChips.pick(null, true, false))
        assertEquals(ChipSource.Offline(true), RidingChips.pick(null, true, true))
        assertNull(RidingChips.pick(null, false, true))
    }

    @Test fun nightFollowsTheSunOrTheClockWithoutAFix() {
        val bengaluru = 12.97 to 77.59
        val noonLocal = 6L * 3_600_000 + 30 * 60_000 // 06:30 UTC = 12:00 IST
        val midnightLocal = 18L * 3_600_000 + 30 * 60_000
        assertFalse(DayNightRule.isNight(noonLocal, bengaluru))
        assertTrue(DayNightRule.isNight(midnightLocal, bengaluru))
        assertTrue(DayNightRule.isNight(0L, null, hourOfDay = 22))
        assertFalse(DayNightRule.isNight(0L, null, hourOfDay = 12))
    }
}
