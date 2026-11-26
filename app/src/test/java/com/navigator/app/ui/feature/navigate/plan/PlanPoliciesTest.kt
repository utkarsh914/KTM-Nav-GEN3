package com.navigator.app.ui.feature.navigate.plan

import com.navigator.app.nav.destination.FavoritePlace
import com.navigator.app.nav.destination.FavoriteSlot
import com.navigator.app.nav.destination.RoutePreview
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.nav.model.TravelMode
import com.navigator.app.ui.map.MapPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanPoliciesTest {
    private fun p(n: Int) = SavedPlace(n.toDouble(), n.toDouble(), "S$n")

    @Test fun aStopGoesLastAndTheListStopsAtFour() {
        var stops = emptyList<SavedPlace>()
        assertEquals(1, StopList.nextNumber(stops))
        for (i in 1..4) stops = StopList.add(stops, p(i))
        assertEquals(listOf("S1", "S2", "S3", "S4"), stops.map { it.label })
        assertEquals(0, StopList.remaining(stops))
        assertFalse(StopList.canAdd(stops))
        assertEquals("a fifth is refused, not dropped in", stops, StopList.add(stops, p(5)))
        assertEquals(2, StopList.remaining(stops.take(2)))
    }

    @Test fun removingAndUndoPutTheStopBackWhereItWas() {
        val stops = listOf(p(1), p(2), p(3))
        val after = StopList.removeAt(stops, 1)
        assertEquals(listOf("S1", "S3"), after.map { it.label })
        assertEquals(stops, StopList.restore(after, 1, p(2)))
        assertEquals("a bad index changes nothing", stops, StopList.removeAt(stops, 7))
        assertEquals("undo past the end lands last", listOf("S1", "S3", "S2"), StopList.restore(after, 9, p(2)).map { it.label })
    }

    @Test fun undoIsRefusedWhenTheListFilledUpMeanwhile() {
        val full = listOf(p(1), p(2), p(3), p(4))
        assertEquals(full, StopList.restore(full, 0, p(9)))
    }

    @Test fun movingByRowsStaysInsideTheList() {
        val stops = listOf(p(1), p(2), p(3))
        assertEquals(listOf("S2", "S1", "S3"), StopList.move(stops, 0, 1).map { it.label })
        assertEquals(listOf("S1", "S3", "S2"), StopList.move(stops, 2, -1).map { it.label })
        assertEquals(listOf("S3", "S1", "S2"), StopList.move(stops, 2, -2).map { it.label })
        assertEquals(stops, StopList.move(stops, 0, -1))
        assertEquals(stops, StopList.move(stops, 2, 1))
        assertEquals(stops, StopList.move(stops, 1, 0))
    }

    @Test fun theFirstRouteIsTheFastestAndStopsLeaveASingleRoute() {
        assertEquals(RouteBadge.Fastest, RouteSelection.badge(0, stopCount = 0))
        assertEquals(RouteBadge.Alternative, RouteSelection.badge(1, stopCount = 0))
        assertEquals(RouteBadge.Only, RouteSelection.badge(0, stopCount = 2))
        assertEquals(1, RouteSelection.alternativeNumber(1))
        assertEquals(2, RouteSelection.alternativeNumber(2))
    }

    @Test fun delayReadsOnTimeUntilAMinuteThenWarnsThenTurnsHeavyAtFifteen() {
        assertEquals(DelayTone.OnTime, RouteSelection.delayTone(59))
        assertEquals(DelayTone.Minor, RouteSelection.delayTone(60))
        assertEquals(DelayTone.Minor, RouteSelection.delayTone(14 * 60 + 59))
        assertEquals(DelayTone.Heavy, RouteSelection.delayTone(15 * 60))
        assertEquals(7, RouteSelection.delayMinutes(7 * 60 + 30))
    }

    @Test fun aSelectionSurvivesAShorterListOfRoutes() {
        assertEquals(1, RouteSelection.clamp(1, 3))
        assertEquals(0, RouteSelection.clamp(2, 1))
        assertEquals(0, RouteSelection.clamp(2, 0))
        assertEquals(0, RouteSelection.clamp(-4, 3))
    }

    @Test fun timeLabelsOfSimilarRoutesDoNotSitOnTheSameSpot() {
        val fractions = (0..2).map { RouteSelection.calloutFraction(it, selected = 0) }
        assertEquals("all different", 3, fractions.toSet().size)
        assertEquals(0.5, RouteSelection.calloutFraction(2, selected = 2), 0.0)
    }

    @Test fun aRunningTripMakesStartAskFirst() {
        assertEquals(ReplaceDecision.Ask, ReplacePolicy.decide(tripLive = true))
        assertEquals(ReplaceDecision.Proceed, ReplacePolicy.decide(tripLive = false))
        assertEquals("said Replace at the place card: Start does it without asking again", ReplaceDecision.Proceed, ReplacePolicy.decide(tripLive = true, agreed = true))
    }

    @Test fun anUnnamedSpotIsCalledNewStop() {
        assertEquals("Toit", NewStop.title("Toit", "New stop"))
        assertEquals("New stop", NewStop.title(null, "New stop"))
        assertEquals("New stop", NewStop.title("  ", "New stop"))
    }

    @Test fun onlyThePlainSavedListCountsAsSaved() {
        val home = FavoritePlace(SavedPlace(12.97, 77.64, "Home"), FavoriteSlot.HOME)
        val cafe = FavoritePlace(SavedPlace(13.0, 77.5, "Cafe"), FavoriteSlot.OTHER)
        assertTrue(PlaceSaving.isSaved(listOf(home, cafe), SavedPlace(13.0, 77.5, "Cafe")))
        assertTrue("same spot to four decimals", PlaceSaving.isSaved(listOf(cafe), SavedPlace(13.00001, 77.50001, "x")))
        assertFalse("Home is its own slot", PlaceSaving.isSaved(listOf(home), SavedPlace(12.97, 77.64, "Home")))
        assertFalse(PlaceSaving.isSaved(emptyList(), SavedPlace(1.0, 1.0, "x")))
    }

    @Test fun aLabelIsNotPlacedOnAPin() {
        val line = (0..10).map { MapPoint(it * 0.01, it * 0.01) }
        val preferred = RouteSelection.calloutPoint(line, 0, 0, emptyList())!!
        assertEquals(line[5], preferred)
        val moved = RouteSelection.calloutPoint(line, 0, 0, listOf(line[5]))!!
        assertFalse("moved off the pin", moved == line[5])
        assertEquals(null, RouteSelection.calloutPoint(line, 0, 0, line))
        assertEquals(null, RouteSelection.calloutPoint(emptyList(), 0, 0, emptyList()))
    }

    @Test fun routesGetTimeLabelsOnlyWhenThereAreAlternatives() {
        fun r(m: Int) = RoutePreview(m * 60, 1000, emptyList(), null, 0, TravelMode.TWO_WHEELER, emptyList())
        assertEquals(listOf("30 min", "35 min"), RouteSheetMapper.callouts(listOf(r(30), r(35)), 0))
        assertTrue(RouteSheetMapper.callouts(listOf(r(30)), 0).isEmpty())
        assertTrue("with stops the header carries the time", RouteSheetMapper.callouts(listOf(r(30), r(35)), 1).isEmpty())
    }

    @Test fun stopsWithTheSameNameAreToldApart() {
        val a = SavedPlace(1.0, 1.0, "Churkhi Road", "Near the mill")
        val b = SavedPlace(2.0, 2.0, "Churkhi Road")
        val c = SavedPlace(3.0, 3.0, "Fuel")
        assertEquals(listOf("Churkhi Road · Near the mill", "Churkhi Road (2)", "Fuel"), RouteSheetMapper.distinctLabels(listOf(a, b, c)))
        assertEquals(listOf("Fuel"), RouteSheetMapper.distinctLabels(listOf(c)))
    }

    @Test fun aFailedRouteStillFramesTheDestinationButALoadingOneWaits() {
        assertTrue(PlanFraming.reframe(pickingStop = false, changed = true, waitingForRoutes = false))
        assertFalse("routes still loading: nothing to frame yet", PlanFraming.reframe(false, changed = true, waitingForRoutes = true))
        assertFalse("picking a stop: the pin owns the camera", PlanFraming.reframe(true, changed = true, waitingForRoutes = false))
        assertFalse("nothing changed", PlanFraming.reframe(false, changed = false, waitingForRoutes = false))
    }
}
