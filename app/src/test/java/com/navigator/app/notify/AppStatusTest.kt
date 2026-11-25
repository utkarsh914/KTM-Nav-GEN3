package com.navigator.app.notify

import android.content.res.Resources
import com.navigator.app.nav.ktm.ClockFormat
import com.navigator.app.nav.model.DistanceUnits
import com.navigator.app.nav.model.NavSessionState
import com.navigator.app.nav.model.NormalizedManeuver
import com.navigator.app.nav.model.NormalizedNavigationState
import com.navigator.app.ride.RideSessionState
import com.navigator.app.trip.TripPhase
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The one notification decides what it is about in a fixed order: trip, failure, recording, mirror, bike, ready. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppStatusTest {
    private val res: Resources get() = RuntimeEnvironment.getApplication().resources
    private val now = 1_700_000_000_000L
    private val recording = RideSessionState(active = true, segmentStartMs = now - 65_000, distanceMeters = 2400)

    @Before fun pin24h() { ClockFormat.use24h = { true } }

    private fun enroute() = NormalizedNavigationState(
        sessionState = NavSessionState.ENROUTE, maneuver = NormalizedManeuver.LEFT, fullInstruction = "Turn left onto MG Road",
        distanceToManeuverMeters = 220, remainingDistanceMeters = 5200, remainingTimeSeconds = 600, units = DistanceUnits.METRIC,
    )

    private fun input(
        phase: TripPhase = TripPhase.NONE, nav: NormalizedNavigationState = NormalizedNavigationState(NavSessionState.IDLE),
        rec: RideSessionState = RideSessionState(), connection: ConnectionNotice? = null, mirror: Boolean = false,
        live: Boolean = true, confirm: Boolean = false, bike: String? = null,
    ) = AppStatusInput(
        phase = phase, trip = LiveInput(nav, now, destination = "Toit", recording = rec, routeTotalM = 5200, stopMarks = listOf(2000)),
        connection = connection, bikeName = bike, mirrorActive = mirror, mirrorToTurn = "350 m", liveUpdates = live, quitNeedsConfirm = confirm,
    )

    private fun kind(i: AppStatusInput) = AppStatusBuilder.build(i, res, ZoneOffset.UTC).kind

    @Test fun eachKindWinsOverTheOnesBelowIt() {
        val everything = input(TripPhase.GUIDING, enroute(), recording, ConnectionNotice.CONNECTED, mirror = true)
        assertEquals(StatusKind.TRIP, kind(everything))
        assertEquals(StatusKind.FAILED, kind(everything.copy(phase = TripPhase.FAILED)))
        assertEquals(StatusKind.RECORDING, kind(everything.copy(phase = TripPhase.NONE, mirrorActive = false)))
        assertEquals("a mirrored trip records itself: the mirror line carries it", StatusKind.MIRRORING, kind(everything.copy(phase = TripPhase.NONE)))
        assertEquals(StatusKind.BIKE, kind(input(connection = ConnectionNotice.CONNECTED)))
        assertEquals(StatusKind.READY, kind(input()))
    }

    @Test fun everyLivePhaseIsTheTripAndFinishedOrNoneIsNot() {
        listOf(TripPhase.STARTING, TripPhase.GUIDING, TripPhase.UPDATING).forEach { assertEquals(it.name, StatusKind.TRIP, kind(input(it, connection = ConnectionNotice.CONNECTED))) }
        listOf(TripPhase.NONE, TripPhase.FINISHED).forEach { assertEquals(it.name, StatusKind.BIKE, kind(input(it, connection = ConnectionNotice.CONNECTED))) }
    }

    @Test fun theTripKeepsTheTurnsAndSaysCalculatingOrUpdatingBetweenLegs() {
        val trip = AppStatusBuilder.build(input(TripPhase.GUIDING, enroute()), res, ZoneOffset.UTC)
        assertEquals("220 m \u00b7 Turn left onto MG Road", trip.title)
        assertEquals(listOf(StatusAction.OPEN, StatusAction.MUTE_BEEPS, StatusAction.END_TRIP), trip.actions)
        assertEquals(0 to 5200, trip.progress)
        assertEquals(listOf(2000), trip.stopPoints)
        val starting = AppStatusBuilder.build(input(TripPhase.STARTING), res, ZoneOffset.UTC)
        assertEquals("Calculating route\u2026", starting.text)
        val updating = AppStatusBuilder.build(input(TripPhase.UPDATING, NormalizedNavigationState(NavSessionState.IDLE, remainingDistanceMeters = 5200, remainingTimeSeconds = 600)), res, ZoneOffset.UTC)
        assertEquals("Updating route\u2026", updating.title)
        assertTrue(updating.text!!.contains("Toit"))
    }

    @Test fun onlyATripOrARecordingCanBePromotedAndOnlyWithLiveUpdatesOn() {
        assertTrue(AppStatusBuilder.build(input(TripPhase.GUIDING, enroute()), res).promoted)
        assertFalse(AppStatusBuilder.build(input(TripPhase.GUIDING, enroute(), live = false), res).promoted)
        assertTrue(AppStatusBuilder.build(input(rec = recording), res).promoted)
        assertFalse(AppStatusBuilder.build(input(rec = recording, live = false), res).promoted)
        listOf(
            input(TripPhase.FAILED), input(mirror = true), input(connection = ConnectionNotice.CONNECTED), input(),
        ).forEach { assertFalse(AppStatusBuilder.build(it, res).kind.name, AppStatusBuilder.build(it, res).promoted) }
    }

    @Test fun aFailedStartOffersOpenAndNamesTheDestination() {
        val c = AppStatusBuilder.build(input(TripPhase.FAILED), res)
        assertEquals("Navigation didn't start", c.title)
        assertEquals("Toit", c.text)
        assertEquals(listOf(StatusAction.OPEN), c.actions)
    }

    @Test fun aRecordingAloneRunsAChronometerAndPausesToResume() {
        val running = AppStatusBuilder.build(input(rec = recording), res)
        assertEquals(listOf(StatusAction.PAUSE, StatusAction.STOP_RECORDING), running.actions)
        assertEquals(now - 65_000, running.chronometerBaseMs)
        val paused = AppStatusBuilder.build(input(rec = recording.copy(paused = true, elapsedBeforeMs = 65_000)), res)
        assertEquals(listOf(StatusAction.RESUME, StatusAction.STOP_RECORDING), paused.actions)
        assertNull(paused.chronometerBaseMs)
    }

    @Test fun mirroringNamesTheBikeAndCarriesTheTurnAndTheRecording() {
        val c = AppStatusBuilder.build(input(rec = recording, mirror = true, bike = "KTM 7F21"), res)
        assertEquals("Sending Google Maps to KTM 7F21", c.title)
        assertEquals("350 m \u00b7 \u25cf Rec 1:05", c.text)
        assertEquals(listOf(StatusAction.QUIT), c.actions)
        assertEquals("Sending Google Maps to the dash", AppStatusBuilder.build(input(mirror = true), res).title)
    }

    @Test fun theBikeKindIsTheConnectionStatesOwnWords() {
        val cant = AppStatusBuilder.build(input(connection = ConnectionNotice.CANT_CONNECT), res)
        assertEquals(listOf(StatusAction.REPAIR, StatusAction.QUIT), cant.actions)
        assertEquals(listOf(StatusAction.TURN_ON, StatusAction.QUIT), AppStatusBuilder.build(input(connection = ConnectionNotice.BLUETOOTH_OFF), res).actions)
        assertEquals("Dash connected \u00b7 KTM 7F21", AppStatusBuilder.build(input(connection = ConnectionNotice.CONNECTED, bike = "KTM 7F21"), res).title)
    }

    @Test fun readyIsTheFloor() {
        val c = AppStatusBuilder.build(input(), res)
        assertEquals("KTM Navigator", c.title)
        assertEquals("Ready", c.text)
        assertEquals(listOf(StatusAction.QUIT), c.actions)
    }

    @Test fun theQuitButtonKnowsWhetherItMustAsk() {
        assertFalse(AppStatusBuilder.build(input(), res).quitNeedsConfirm)
        assertTrue(AppStatusBuilder.build(input(confirm = true), res).quitNeedsConfirm)
    }

    @Test fun anUnchangedContentIsNotPostedAgain() {
        val a = AppStatusBuilder.build(input(TripPhase.GUIDING, enroute()), res, ZoneOffset.UTC)
        val same = AppStatusBuilder.build(input(TripPhase.GUIDING, enroute()), res, ZoneOffset.UTC)
        assertFalse(StatusNotification.shouldPost(a, same))
        assertTrue(StatusNotification.shouldPost(null, a))
        val moved = AppStatusBuilder.build(input(TripPhase.GUIDING, enroute().copy(distanceToManeuverMeters = 200)), res, ZoneOffset.UTC)
        assertTrue(StatusNotification.shouldPost(a, moved))
        assertNotEquals(a, AppStatusBuilder.build(input(TripPhase.GUIDING, enroute(), confirm = true), res, ZoneOffset.UTC))
    }
}
