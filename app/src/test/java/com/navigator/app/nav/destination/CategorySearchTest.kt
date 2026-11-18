package com.navigator.app.nav.destination

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategorySearchTest {

    @Test fun encodesTheCanonicalExample() {
        val pts = listOf(38.5 to -120.2, 40.7 to -120.95, 43.252 to -126.453)
        assertEquals("_p~iF~ps|U_ulLnnqC_mqNvxq`@", PolylineCodec.encode(pts))
    }

    @Test fun encodeDecodeRoundTrip() {
        val pts = listOf(12.97164 to 77.59456, 12.97200 to 77.59500, 12.9 to 77.7, -33.86 to 151.2, 0.0 to 0.0)
        val back = RoutesClient.decodePolyline(PolylineCodec.encode(pts))
        assertEquals(pts.size, back.size)
        pts.zip(back).forEach { (a, b) ->
            assertEquals(a.first, b.latitude, 1e-5)
            assertEquals(a.second, b.longitude, 1e-5)
        }
    }

    @Test fun thinningKeepsEndsAndRespectsTheCap() {
        val pts = (0 until 5000).map { it.toDouble() to it.toDouble() }
        val t = PolylineCodec.thin(pts, 1000)
        assertTrue(t.size <= 1001)
        assertEquals(pts.first(), t.first())
        assertEquals(pts.last(), t.last())
        assertEquals(pts, PolylineCodec.thin(pts, 10_000))
    }

    private val json = """{"places":[
        {"id":"a","displayName":{"text":"Far Petrol"},"formattedAddress":"NH 48","location":{"latitude":13.0,"longitude":77.6}},
        {"id":"b","displayName":{"text":"Near Petrol"},"location":{"latitude":12.9717,"longitude":77.5947}},
        {"id":"c","displayName":{"text":""},"location":{"latitude":1,"longitude":1}},
        {"id":"d","displayName":{"text":"No location"}},
        {"id":"e","displayName":{"text":"Bad coords"},"location":{"latitude":999,"longitude":1}},
        "junk"]}"""

    @Test fun parsesSortsAndSkipsBadEntries() {
        val out = PlacesClient.parseCategoryResults(json, 12.9716 to 77.5946)
        assertEquals(listOf("Near Petrol", "Far Petrol"), out.map { it.name })
        assertNull(out[0].address)
        assertEquals("NH 48", out[1].address)
        assertTrue(out[0].distanceMeters!! < out[1].distanceMeters!!)
    }

    @Test fun withoutOriginKeepsServerOrderAndNoDistance() {
        val out = PlacesClient.parseCategoryResults(json, null)
        assertEquals(listOf("Far Petrol", "Near Petrol"), out.map { it.name })
        assertNull(out[0].distanceMeters)
    }

    @Test fun garbageResponsesGiveNoResults() {
        assertTrue(PlacesClient.parseCategoryResults("not json", null).isEmpty())
        assertTrue(PlacesClient.parseCategoryResults("{}", null).isEmpty())
    }

    @Test fun requestBodyUsesRouteOrCircleNeverBoth() {
        val route = listOf(12.0 to 77.0, 12.1 to 77.1, 12.2 to 77.2)
        val along = PlacesClient.buildCategoryBody(PlaceCategory.FUEL, 12.0 to 77.0, route)
        assertTrue(along.has("searchAlongRouteParameters"))
        assertTrue(!along.has("locationBias"))
        assertEquals("petrol pump", along.getString("textQuery"))
        val encoded = along.getJSONObject("searchAlongRouteParameters").getJSONObject("polyline").getString("encodedPolyline")
        assertEquals(route.size, RoutesClient.decodePolyline(encoded).size)

        val near = PlacesClient.buildCategoryBody(PlaceCategory.COFFEE, 12.0 to 77.0, null)
        assertTrue(near.has("locationBias"))
        assertTrue(!near.has("searchAlongRouteParameters"))
        assertEquals(5000.0, near.getJSONObject("locationBias").getJSONObject("circle").getDouble("radius"), 0.0)

        // A one-point "route" is not a route: fall back to the circle.
        assertTrue(PlacesClient.buildCategoryBody(PlaceCategory.ATM, 1.0 to 1.0, listOf(1.0 to 1.0)).has("locationBias"))
        val bare = PlacesClient.buildCategoryBody(PlaceCategory.EV, null, null)
        assertTrue(!bare.has("locationBias") && !bare.has("searchAlongRouteParameters"))
    }

    @Test fun onlyWhatIsAheadOfTheRiderIsSearched() {
        val route = (0..10).map { 12.0 + it * 0.01 to 77.0 }
        val ahead = PolylineCodec.aheadOf(route, 12.052, 77.0)
        assertEquals(route[5], ahead.first())
        assertEquals(route.last(), ahead.last())
        // At the very end it still returns two points (a valid polyline).
        assertEquals(2, PolylineCodec.aheadOf(route, 12.1, 77.0).size)
        assertEquals(route, PolylineCodec.aheadOf(route, 12.0, 77.0))
        assertEquals(1, PolylineCodec.aheadOf(listOf(1.0 to 1.0), 1.0, 1.0).size)
    }
}
