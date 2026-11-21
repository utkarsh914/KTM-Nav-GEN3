package com.navigator.app.notify

import android.content.res.Resources
import com.navigator.app.ble.BccuProtocol.TurnIcon
import com.navigator.app.nav.ktm.ClockFormat
import com.navigator.app.nav.model.DistanceUnits
import com.navigator.app.nav.model.NavSessionState
import com.navigator.app.nav.model.NormalizedManeuver
import com.navigator.app.nav.model.NormalizedNavigationState
import com.navigator.app.ride.RecordedRide
import com.navigator.app.ride.RideSessionState
import com.navigator.app.ride.RideSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LiveUpdateContentTest {
    private val res: Resources get() = RuntimeEnvironment.getApplication().resources
    private val now = 1_700_000_000_000L // 2023-11-14 22:13:20 UTC

    @Before fun pin24h() { ClockFormat.use24h = { true } }

    @Test fun theEtaFollowsThePhones12HourSetting() {
        ClockFormat.use24h = { false }
        try {
            assertTrue(build(LiveInput(enroute(), now, destination = "Toit")).text.contains("ETA 10:23 PM"))
        } finally { ClockFormat.use24h = { true } }
    }

    private fun enroute(
        instr: String? = "Turn left onto MG Road", road: String? = "MG Road", toTurn: Int? = 220,
        remaining: Int? = 5200, secs: Int? = 600, state: NavSessionState = NavSessionState.ENROUTE,
    ) = NormalizedNavigationState(
        sessionState = state, maneuver = NormalizedManeuver.LEFT, fullInstruction = instr, roadName = road,
        distanceToManeuverMeters = toTurn, remainingDistanceMeters = remaining, remainingTimeSeconds = secs, units = DistanceUnits.METRIC,
    )

    private fun build(input: LiveInput) = LiveUpdateBuilder.build(input, res, ZoneOffset.UTC)

    @Test fun theTurnIsTheTitleAndTheTripIsTheSecondLine() {
        val c = build(LiveInput(enroute(), now, destination = "Toit"))
        assertEquals("220 m \u00b7 Turn left onto MG Road", c.title)
        assertEquals("Toit \u00b7 ETA 22:23 \u00b7 5.2 km", c.text)
        assertEquals(now + 600_000, c.etaMs)
        assertEquals(TurnIcon.QUITE_LEFT, c.turn)
    }

    @Test fun imperialRegionsStillGetKilometres() {
        val c = build(LiveInput(enroute().copy(units = DistanceUnits.IMPERIAL), now, destination = "Toit"))
        assertEquals("220 m \u00b7 Turn left onto MG Road", c.title)
        assertEquals("Toit \u00b7 ETA 22:23 \u00b7 5.2 km", c.text)
    }

    @Test fun theMirrorLineIsRepostedWhenItChangesAndWhenItGoesAway() {
        assertTrue(ConnectionNoticeContent.repostForLine(true, "350 m", null))
        assertTrue("trip ended: the old line must not stay", ConnectionNoticeContent.repostForLine(true, null, "350 m"))
        assertFalse(ConnectionNoticeContent.repostForLine(true, "350 m", "350 m"))
        assertFalse(ConnectionNoticeContent.repostForLine(true, null, null))
        assertFalse("not off a link that is not connected", ConnectionNoticeContent.repostForLine(false, null, "350 m"))
    }

    @Test fun fallsBackToRoadNameThenContinue() {
        assertEquals("220 m \u00b7 MG Road", build(LiveInput(enroute(instr = null), now)).title)
        assertEquals("220 m \u00b7 Continue", build(LiveInput(enroute(instr = " ", road = null), now)).title)
    }

    @Test fun recordingAndDashShareOneLine() {
        val rec = RideSessionState(active = true, segmentStartMs = now - 750_000)
        val c = build(LiveInput(enroute(), now, recording = rec, dashConnected = true))
        assertEquals("\u25cf Rec 12:30 \u00b7 Dash connected", c.subText)
        assertEquals("Rec paused \u00b7 Dash not connected", build(LiveInput(enroute(), now, recording = rec.copy(paused = true), dashConnected = false)).subText)
        assertNull("no bike and no recording says nothing", build(LiveInput(enroute(), now)).subText)
    }

    @Test fun overspeedIsAStateOfTheSameNotification() {
        val c = build(LiveInput(enroute(), now, overspeedKmh = 92))
        assertTrue(c.overspeed)
        assertEquals("Over your alert \u00b7 92 km/h", c.title)
        assertEquals("220 m \u00b7 Turn left onto MG Road", c.text)
        val limited = build(LiveInput(enroute(), now, overspeedKmh = 92, overspeedLimitKmh = 80))
        assertEquals("Alert at 80 \u00b7 220 m \u00b7 Turn left onto MG Road", limited.text)
        assertEquals("92 km/h", c.chip)
    }

    @Test fun overspeedOutsideGuidanceIsNotThisNotificationsBusiness() {
        assertFalse(build(LiveInput(enroute(state = NavSessionState.IDLE), now, overspeedKmh = 92)).overspeed)
    }

    @Test fun threeButtonsAndTheBeepLabelFollowsTheSetting() {
        assertEquals(listOf(LiveAction.OPEN, LiveAction.MUTE_BEEPS, LiveAction.END_TRIP), build(LiveInput(enroute(), now, beepsOn = true)).actions)
        assertEquals(listOf(LiveAction.OPEN, LiveAction.UNMUTE_BEEPS, LiveAction.END_TRIP), build(LiveInput(enroute(), now, beepsOn = false)).actions)
    }

    @Test fun reroutingAndPreStartStates() {
        val re = build(LiveInput(enroute(state = NavSessionState.REROUTING), now, destination = "Toit"))
        assertEquals("Rerouting\u2026", re.title)
        assertEquals("Reroute", re.chip)
        assertEquals("Toit \u00b7 ETA 22:23 \u00b7 5.2 km", re.text)
        val idle = build(LiveInput(NormalizedNavigationState(NavSessionState.IDLE), now))
        assertEquals("Navigating", idle.title)
        assertEquals("Calculating route\u2026", idle.text)
        assertNull(idle.chip)
        assertNull(idle.turn)
    }

    @Test fun missingNumbersDegradeGracefully() {
        val c = build(LiveInput(enroute(toTurn = null, remaining = null, secs = null), now))
        assertEquals("Turn left onto MG Road", c.title)
        assertEquals("Guidance active", c.text)
        assertNull(build(LiveInput(enroute(secs = -5), now)).etaMs)
    }

    @Test fun theChipPrefersOverspeedThenTheTurnThenRecordingThenTheEta() {
        val r = res
        assertEquals("92 km/h", LiveChips.pick(92, "220 m", "12:30", "22:23", false, r))
        assertEquals("220 m", LiveChips.pick(null, "220 m", "12:30", "22:23", false, r))
        assertEquals("12:30", LiveChips.pick(null, null, "12:30", "22:23", false, r))
        assertEquals("22:23", LiveChips.pick(null, null, null, "22:23", false, r))
        assertEquals("Reroute", LiveChips.pick(null, "220 m", null, null, true, r))
        assertNull(LiveChips.pick(null, null, null, null, false, r))
        assertTrue(LiveChips.pick(null, "123.4 km", null, null, false, r)!!.length <= LiveChips.MAX)
    }

    @Test fun anArrivedTripIsJustNavigating() {
        val c = build(LiveInput(enroute(state = NavSessionState.ARRIVED), now))
        assertEquals("Navigating", c.title)
        assertNull(c.chip)
        assertFalse(c.overspeed)
    }

    @Test fun anHourLongRecordingIsLeftOffTheChipRatherThanCutShort() {
        val rec = RideSessionState(active = true, segmentStartMs = now - 36_123_000)
        assertNull(build(LiveInput(enroute(toTurn = null, secs = null), now, recording = rec)).chip)
    }

    @Test fun theChipFallsBackToRecordingTimeWhenThereIsNoTurn() {
        val rec = RideSessionState(active = true, segmentStartMs = now - 65_000)
        assertEquals("1:05", build(LiveInput(enroute(toTurn = null, secs = null), now, recording = rec)).chip)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecordingNoticeTest {
    private val res: Resources get() = RuntimeEnvironment.getApplication().resources
    private val now = 1_000_000L
    private val session = RideSessionState(active = true, segmentStartMs = now - 125_000, distanceMeters = 2400)

    @Test fun aLiveTripOrAMirroredTripMakesItSilent() {
        assertEquals(RecordingMode.WITH_TRIP, RecordingNotice.mode(true, RideSource.MANUAL))
        assertEquals(RecordingMode.MIRRORING, RecordingNotice.mode(false, RideSource.MIRROR))
        assertEquals(RecordingMode.ALONE, RecordingNotice.mode(false, RideSource.MANUAL))
    }

    @Test fun aloneItIsPromotedWithActionsAndNoSystemSpeak() {
        val c = RecordingNotice.build(session, now, RecordingMode.ALONE, dashConnected = true, res = res)
        assertEquals("Recording ride", c.title)
        assertEquals("2.4 km \u00b7 Dash connected", c.text)
        assertTrue(c.promoted && c.showActions)
        assertFalse(c.text.contains("GPS track"))
    }

    @Test fun silentRowsShowTimeAndDistanceWithoutActions() {
        val c = RecordingNotice.build(session, now, RecordingMode.WITH_TRIP, null, res)
        assertEquals("2:05 \u00b7 2.4 km", c.text)
        assertFalse(c.promoted || c.showActions)
    }

    @Test fun pausedSaysHowToResume() {
        val c = RecordingNotice.build(session.copy(paused = true, elapsedBeforeMs = 125_000), now, RecordingMode.ALONE, null, res)
        assertEquals("Ride paused \u00b7 2:05", c.title)
        assertEquals("Resume to keep recording", c.text)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ConnectionNoticeTest {
    private val res: Resources get() = RuntimeEnvironment.getApplication().resources

    private fun of(n: ConnectionNotice, line: String? = null) = ConnectionNoticeContent.of(n, line, res)

    @Test fun theTitleSaysTheStateAndTheSecondLineTheFix() {
        assertEquals("Reconnecting to the dash", of(ConnectionNotice.RECONNECTING).title)
        assertEquals("Is the ignition on? Retrying automatically", of(ConnectionNotice.RECONNECTING).text)
        assertEquals("Bluetooth is off", of(ConnectionNotice.BLUETOOTH_OFF).title)
    }

    @Test fun theTitleNamesTheBikeWhenItIsKnown() {
        val named = ConnectionNoticeContent.of(ConnectionNotice.CONNECTED, null, res, "KTM 7F21")
        assertEquals("Dash connected \u00b7 KTM 7F21", named.title)
        assertEquals("Reconnecting to KTM 7F21\u2026", ConnectionNoticeContent.of(ConnectionNotice.RECONNECTING, null, res, "KTM 7F21").title)
        assertEquals("Dash connected", ConnectionNoticeContent.of(ConnectionNotice.CONNECTED, null, res, " ").title)
    }

    @Test fun actionsMatchTheState() {
        assertEquals(listOf(ConnectionAction.TURN_ON, ConnectionAction.QUIT), of(ConnectionNotice.BLUETOOTH_OFF).actions)
        assertEquals(listOf(ConnectionAction.REPAIR, ConnectionAction.QUIT), of(ConnectionNotice.CANT_CONNECT).actions)
        assertEquals(listOf(ConnectionAction.QUIT), of(ConnectionNotice.CONNECTED).actions)
    }

    @Test fun onlyCantConnectGetsAHeadsUp() {
        assertEquals(listOf(ConnectionNotice.CANT_CONNECT), ConnectionNotice.entries.filter { of(it).alert })
    }

    @Test fun theMirrorLineReplacesTheHintWhileConnected() {
        val line = ConnectionNoticeContent.mirrorLine("350 m", "Turn right", "12:30", res)
        assertEquals("Mirroring to dash \u00b7 350 m \u00b7 Turn right \u00b7 \u25cf Rec 12:30", line)
        assertEquals(line, of(ConnectionNotice.CONNECTED, line).text)
        assertEquals("Mirroring to dash", ConnectionNoticeContent.mirrorLine(null, null, null, res))
        assertEquals("Guidance and messages go to the dash.", of(ConnectionNotice.CONNECTED).text)
    }
}

class GeoLengthTest {
    @Test fun aDegreeOfLatitudeIsAbout111Km() {
        assertEquals(111_195.0, GeoLength.between(0.0, 0.0, 1.0, 0.0), 100.0)
    }

    @Test fun aPolylineIsTheSumOfItsLegs() {
        val p = listOf(0.0 to 0.0, 0.0 to 0.01, 0.01 to 0.01)
        assertEquals(GeoLength.between(0.0, 0.0, 0.0, 0.01) + GeoLength.between(0.0, 0.01, 0.01, 0.01), GeoLength.polyline(p), 0.01)
        assertEquals(0.0, GeoLength.polyline(emptyList()), 0.0)
    }

    @Test fun stopsSitBetweenLegsAndTheDestinationHasNoMark() {
        val m = GeoLength.marks(listOf(1000.0, 2000.0, 500.0))
        assertEquals(listOf(1000, 3000), m.stops)
        assertEquals(3500, m.total)
        assertEquals(RouteMarks(), GeoLength.marks(emptyList()))
        assertEquals(RouteMarks(total = 800), GeoLength.marks(listOf(800.0)))
    }
}

class RideSavedNoticeTest {
    private fun ride(source: String?) = RecordedRide("1", 0, 1, 100, 10, source = source)

    @Test fun onlyAMirroredRideGetsTheRow() {
        assertTrue(RideSavedNotice.shouldPost(ride(RideSource.MIRROR)))
        for (s in listOf(RideSource.NAV, RideSource.MANUAL, RideSource.GPX, null)) assertFalse(RideSavedNotice.shouldPost(ride(s)))
    }
}
