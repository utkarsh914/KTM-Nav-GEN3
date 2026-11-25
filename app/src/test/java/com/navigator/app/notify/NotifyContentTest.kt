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
import java.time.ZoneOffset
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

    @Test fun theChipNeverEndsMidToken() {
        val r = res
        assertEquals("92 km/h", LiveChips.pick(92, null, null, null, false, r))
        assertEquals("the unit goes before the number does", "125", LiveChips.pick(125, null, null, null, false, r))
        assertEquals("10:05", LiveChips.pick(null, null, null, "10:05 am", false, r))
        assertEquals("9:05 am", LiveChips.pick(null, null, null, "9:05 am", false, r))
        assertEquals("22:23", LiveChips.pick(null, null, null, "22:23", false, r))
        assertEquals("123 km", LiveChips.pick(null, "123.4 km", null, null, false, r))
        assertNull("nothing fits: nothing, not half a thing", LiveChips.pick(null, "123456 km", null, null, false, r))
    }

    @Test fun updatingKeepsTheTripFiguresAndSaysSo() {
        val c = build(LiveInput(NormalizedNavigationState(NavSessionState.IDLE, remainingDistanceMeters = 5200, remainingTimeSeconds = 600), now, destination = "Toit", updating = true))
        assertEquals("Updating route\u2026", c.title)
        assertEquals("Toit \u00b7 ETA 22:23 \u00b7 5.2 km", c.text)
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

    private fun of(n: ConnectionNotice) = ConnectionNoticeContent.of(n, res)

    @Test fun theTitleSaysTheStateAndTheSecondLineTheFix() {
        assertEquals("Reconnecting to the dash", of(ConnectionNotice.RECONNECTING).title)
        assertEquals("Retrying automatically", of(ConnectionNotice.RECONNECTING).text)
        assertEquals("Bluetooth is off", of(ConnectionNotice.BLUETOOTH_OFF).title)
    }

    @Test fun aParkedBikeSaysNotConnectedAndOffersOnlyQuit() {
        val c = of(ConnectionNotice.DISCONNECTED)
        assertEquals("Dash not connected", c.title)
        assertEquals("It connects when the bike is on and near.", c.text)
        assertEquals(listOf(ConnectionAction.QUIT), c.actions)
        assertFalse(c.alert)
        assertEquals("KTM 7F21 not connected", ConnectionNoticeContent.of(ConnectionNotice.DISCONNECTED, res, "KTM 7F21").title)
    }

    @Test fun theTitleNamesTheBikeWhenItIsKnown() {
        val named = ConnectionNoticeContent.of(ConnectionNotice.CONNECTED, res, "KTM 7F21")
        assertEquals("Dash connected \u00b7 KTM 7F21", named.title)
        assertEquals("Reconnecting to KTM 7F21\u2026", ConnectionNoticeContent.of(ConnectionNotice.RECONNECTING, res, "KTM 7F21").title)
        assertEquals("Dash connected", ConnectionNoticeContent.of(ConnectionNotice.CONNECTED, res, " ").title)
        assertEquals("Dash connected", ConnectionNoticeContent.of(ConnectionNotice.CONNECTED, res, "").title)
    }

    @Test fun actionsMatchTheState() {
        assertEquals(listOf(ConnectionAction.TURN_ON, ConnectionAction.QUIT), of(ConnectionNotice.BLUETOOTH_OFF).actions)
        assertEquals(listOf(ConnectionAction.REPAIR, ConnectionAction.QUIT), of(ConnectionNotice.CANT_CONNECT).actions)
        assertEquals(listOf(ConnectionAction.QUIT), of(ConnectionNotice.CONNECTED).actions)
    }

    @Test fun onlyCantConnectGetsAHeadsUp() {
        assertEquals(listOf(ConnectionNotice.CANT_CONNECT), ConnectionNotice.entries.filter { of(it).alert })
    }

    @Test fun connectedSaysItOnceInTheTitle() = assertNull(of(ConnectionNotice.CONNECTED).text)
}
