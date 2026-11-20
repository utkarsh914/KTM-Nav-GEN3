package com.navigator.app.app

import android.content.Intent
import com.navigator.app.nav.destination.FavoriteSlot
import com.navigator.app.nav.destination.SavedPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIntentsTest {
    private val ready = AppReadiness(setupDone = true, inAppMaps = true)
    private val mirrorOnly = AppReadiness(setupDone = true, inAppMaps = false)
    private val firstRun = AppReadiness(setupDone = false, inAppMaps = true)
    private val place = SavedPlace(lat = 12.97, lng = 77.64, label = "Toit", address = "100 Feet Rd")

    @Test fun aSharedLinkOpensNavigateWhenReadyAndWaitsForSetup() {
        val link = AppIntent.SharedLink("https://maps.app.goo.gl/x")
        assertEquals(IntentOutcome.Navigate, AppIntents.decide(link, ready))
        assertEquals("kept until pairing is done", IntentOutcome.Defer, AppIntents.decide(link, firstRun))
        assertEquals("mirror mode shows the Navigate tab too: its card offers Google Maps", IntentOutcome.Navigate, AppIntents.decide(link, mirrorOnly))
    }

    @Test fun aShortcutIsDroppedDuringSetupBecauseItWouldBeStale() {
        val go = AppIntent.QuickRoute(FavoriteSlot.HOME)
        assertEquals(IntentOutcome.Navigate, AppIntents.decide(go, ready))
        assertEquals(IntentOutcome.Drop, AppIntents.decide(go, firstRun))
        assertEquals("mirror mode: Google Maps starts the route", IntentOutcome.OpenInMaps, AppIntents.decide(go, mirrorOnly))
    }

    @Test fun aNotificationTapNeedsNoInAppMapsButNeedsSetup() {
        assertEquals(IntentOutcome.Navigate, AppIntents.decide(AppIntent.OpenNav, ready))
        assertEquals("mirror mode has a home too", IntentOutcome.Navigate, AppIntents.decide(AppIntent.OpenNav, mirrorOnly))
        assertEquals(IntentOutcome.Drop, AppIntents.decide(AppIntent.OpenNav, firstRun))
    }

    @Test fun aPlaceOnTheMapNeedsInAppMaps() {
        val open = AppIntent.OpenPlace(place)
        assertEquals(IntentOutcome.Navigate, AppIntents.decide(open, ready))
        assertEquals(IntentOutcome.Reject(RejectReason.MAP_NEEDS_IN_APP), AppIntents.decide(open, mirrorOnly))
        assertEquals(IntentOutcome.Drop, AppIntents.decide(open, firstRun))
    }

    @Test fun aPlaceKeepsWhetherItIsForDirections() {
        val intents = AppIntents()
        intents.post(AppIntent.OpenPlace(place, directions = true))
        assertTrue(intents.pending.value.placeDirections)
        intents.post(AppIntent.OpenPlace(place))
        assertTrue(!intents.pending.value.placeDirections)
        intents.post(AppIntent.OpenPlace(place, directions = true))
        intents.consumePlace()
        assertNull(intents.pending.value.place)
        assertTrue(!intents.pending.value.placeDirections)
    }

    @Test fun parsesAShareAShortcutAndANotificationTap() {
        assertEquals(
            listOf<AppIntent>(AppIntent.SharedLink("https://maps.app.goo.gl/x")),
            AppIntents.parse(Intent.ACTION_SEND, "text/plain", "  https://maps.app.goo.gl/x ", false, null),
        )
        assertEquals(listOf<AppIntent>(AppIntent.OpenNav), AppIntents.parse(Intent.ACTION_MAIN, null, null, true, null))
        assertEquals(
            listOf<AppIntent>(AppIntent.QuickRoute(FavoriteSlot.WORK)),
            AppIntents.parse(Intent.ACTION_MAIN, null, null, false, FavoriteSlot.WORK),
        )
    }

    @Test fun ignoresShareThatIsNotTextOrIsBlank() {
        assertTrue(AppIntents.parse(Intent.ACTION_SEND, "image/png", "x", false, null).isEmpty())
        assertTrue(AppIntents.parse(Intent.ACTION_SEND, "text/plain", "   ", false, null).isEmpty())
        assertTrue(AppIntents.parse(Intent.ACTION_SEND, "text/plain", null, false, null).isEmpty())
        assertTrue(AppIntents.parse(null, null, null, false, null).isEmpty())
    }

    @Test fun pendingKeepsTheNewestOfEachKindAndConsumesIndependently() {
        val intents = AppIntents()
        assertTrue(intents.pending.value.isEmpty)
        intents.post(AppIntent.SharedLink("one"))
        intents.post(AppIntent.SharedLink("two"))
        intents.post(AppIntent.OpenNav)
        intents.post(AppIntent.QuickRoute(FavoriteSlot.HOME))
        intents.post(AppIntent.OpenPlace(place))
        with(intents.pending.value) {
            assertEquals("two", sharedLink)
            assertTrue(openNav)
            assertEquals(FavoriteSlot.HOME, quickRoute)
            assertEquals(place, this.place)
        }
        intents.consumeSharedLink()
        intents.consumeOpenNav()
        assertNull(intents.pending.value.sharedLink)
        assertEquals(FavoriteSlot.HOME, intents.pending.value.quickRoute)
        intents.consumeQuickRoute()
        intents.consumePlace()
        assertTrue(intents.pending.value.isEmpty)
    }

    @Test fun endTripNeedsInAppMapsAndSetupButSearchOnlyLandsOnNavigate() {
        val end = AppIntent.ConfirmEndTrip
        assertEquals(IntentOutcome.Navigate, AppIntents.decide(end, ready))
        assertEquals(IntentOutcome.Reject(RejectReason.MAP_NEEDS_IN_APP), AppIntents.decide(end, mirrorOnly))
        assertEquals(IntentOutcome.Drop, AppIntents.decide(end, firstRun))
        assertEquals(IntentOutcome.Navigate, AppIntents.decide(AppIntent.OpenSearch, ready))
        assertEquals(IntentOutcome.Navigate, AppIntents.decide(AppIntent.OpenSearch, mirrorOnly))
        assertEquals(IntentOutcome.Drop, AppIntents.decide(AppIntent.OpenSearch, firstRun))
    }

    @Test fun quitRePairAndRideAreActedOnByTheShellInEitherEngine() {
        for (i in listOf(AppIntent.ConfirmQuit, AppIntent.OpenRePair, AppIntent.OpenRide("7"))) {
            assertEquals(IntentOutcome.Act, AppIntents.decide(i, ready))
            assertEquals(IntentOutcome.Act, AppIntents.decide(i, mirrorOnly))
            assertEquals(IntentOutcome.Drop, AppIntents.decide(i, firstRun))
        }
    }

    @Test fun parsesTheNotificationRequests() {
        fun parse(request: String?, rideId: String? = null) = AppIntents.parse(null, null, null, false, null, request, rideId)
        assertEquals(listOf<AppIntent>(AppIntent.ConfirmEndTrip), parse(AppIntents.REQUEST_END_TRIP))
        assertEquals(listOf<AppIntent>(AppIntent.ConfirmQuit), parse(AppIntents.REQUEST_QUIT))
        assertEquals(listOf<AppIntent>(AppIntent.OpenRePair), parse(AppIntents.REQUEST_REPAIR))
        assertEquals(listOf<AppIntent>(AppIntent.OpenSearch), parse(AppIntents.REQUEST_SEARCH))
        assertEquals(listOf<AppIntent>(AppIntent.OpenRide("7")), parse(AppIntents.REQUEST_RIDE, "7"))
        assertTrue("a ride request with no id is nothing", parse(AppIntents.REQUEST_RIDE, null).isEmpty())
        assertTrue(parse("unknown").isEmpty())
    }

    @Test fun postedRequestsWaitUntilTheyAreConsumed() {
        val intents = AppIntents()
        intents.post(AppIntent.ConfirmEndTrip); intents.post(AppIntent.OpenSearch); intents.post(AppIntent.ConfirmQuit)
        intents.post(AppIntent.OpenRePair); intents.post(AppIntent.OpenRide("9"))
        with(intents.pending.value) {
            assertTrue(confirmEnd && openSearch && quit && rePair && rideId == "9")
        }
        intents.consumeConfirmEnd(); intents.consumeOpenSearch(); intents.consumeQuit(); intents.consumeRePair(); intents.consumeRide()
        assertTrue(intents.pending.value.isEmpty)
    }
}
