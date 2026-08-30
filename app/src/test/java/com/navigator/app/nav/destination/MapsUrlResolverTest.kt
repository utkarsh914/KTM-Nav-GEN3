package com.navigator.app.nav.destination

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapsUrlResolverTest {

    private fun coords(s: String): PlaceLocation? = MapsUrlResolver.parseCoordinates(s)

    @Test fun pinFromData3d4d_preferredOverViewportAt() {
        // The /@ viewport is 12.90,77.60 but the real pin (!3d!4d) is 12.9172,77.6229.
        val url = "https://www.google.com/maps/place/Silk+Board/@12.9000,77.6000,15z/" +
            "data=!3m1!4b1!4m6!3m5!1s0x0:0x0!8m2!3d12.9172!4d77.6229"
        assertEquals(PlaceLocation(12.9172, 77.6229), coords(url))
    }

    @Test fun atViewportAloneIsIgnored() {
        // Only an @-viewport, no real pin -> we must NOT use it.
        val url = "https://www.google.com/maps/place/Somewhere/@12.9000,77.6000,15z/"
        assertNull(coords(url))
    }

    @Test fun queryLatLng_api1() {
        assertEquals(
            PlaceLocation(47.5951518, -122.3316393),
            coords("https://www.google.com/maps/search/?api=1&query=47.5951518,-122.3316393"),
        )
    }

    @Test fun queryLatLng_urlEncodedComma() {
        assertEquals(
            PlaceLocation(47.5951518, -122.3316393),
            coords("https://www.google.com/maps/search/?api=1&query=47.5951518%2C-122.3316393"),
        )
    }

    @Test fun destinationAndPlainQ() {
        assertEquals(PlaceLocation(12.9, 77.6), coords("https://www.google.com/maps/dir/?api=1&destination=12.9,77.6"))
        assertEquals(PlaceLocation(12.9, 77.6), coords("https://maps.google.com/?q=12.9,77.6"))
        assertEquals(PlaceLocation(12.9, 77.6), coords("https://maps.google.com/?ll=12.9,77.6"))
    }

    @Test fun geoAndNavigationSchemes() {
        assertEquals(PlaceLocation(12.9172, 77.6229), coords("geo:12.9172,77.6229"))
        assertEquals(PlaceLocation(12.9172, 77.6229), coords("google.navigation:q=12.9172,77.6229"))
    }

    @Test fun outOfRangeRejected() {
        assertNull(coords("?q=200.0,999.0"))
    }

    @Test fun noCoordinatesReturnsNull() {
        assertNull(coords("https://www.google.com/maps/place/Some+Mall/"))
    }

    @Test fun shortLinkDetection() {
        assertTrue(MapsUrlResolver.isShortLink("https://maps.app.goo.gl/abc123"))
        assertTrue(MapsUrlResolver.isShortLink("https://goo.gl/maps/xyz"))
        assertEquals(false, MapsUrlResolver.isShortLink("https://www.google.com/maps/place/X"))
    }

    @Test fun placeNameExtraction() {
        assertEquals(
            "Silk Board Junction",
            MapsUrlResolver.placeName("https://www.google.com/maps/place/Silk+Board+Junction/@12.9,77.6,15z/"),
        )
        assertNull(MapsUrlResolver.placeName("https://maps.google.com/?q=12.9,77.6"))
    }

    @Test fun labelFromPlacePath() {
        assertEquals(
            "Silk Board Junction",
            MapsUrlResolver.labelFrom("https://www.google.com/maps/place/Silk+Board+Junction/@12.9,77.6,15z/"),
        )
        assertEquals("Shared location", MapsUrlResolver.labelFrom("https://maps.google.com/?q=12.9,77.6"))
    }
}
