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

    @Test fun hostAllowlistAcceptsGoogleHostsOnly() {
        assertTrue(MapsUrlResolver.isAllowedHost("https://maps.app.goo.gl/abc"))
        assertTrue(MapsUrlResolver.isAllowedHost("https://goo.gl/maps/xyz"))
        assertTrue(MapsUrlResolver.isAllowedHost("https://www.google.com/maps/place/X"))
        assertTrue(MapsUrlResolver.isAllowedHost("https://maps.google.co.in/maps?q=1,2"))
        assertTrue(MapsUrlResolver.isAllowedHost("https://google.de/maps"))
        assertEquals(false, MapsUrlResolver.isAllowedHost("https://evil.com/?u=maps.app.goo.gl"))
        assertEquals(false, MapsUrlResolver.isAllowedHost("https://maps.app.goo.gl.evil.com/x"))
        assertEquals(false, MapsUrlResolver.isAllowedHost("https://notgoogle.com/maps"))
        assertEquals(false, MapsUrlResolver.isAllowedHost("https://google.com.evil.io/maps"))
        assertEquals(false, MapsUrlResolver.isAllowedHost("not a url"))
    }

    @Test fun shortLinkIsHostBased() {
        assertEquals(false, MapsUrlResolver.isShortLink("https://evil.com/maps.app.goo.gl"))
    }

    @Test fun trailingPunctuationTrimmedFromSharedLinks() {
        assertEquals("https://maps.app.goo.gl/abc123", MapsUrlResolver.trimUrl("https://maps.app.goo.gl/abc123)."))
        assertEquals("https://maps.app.goo.gl/abc123", MapsUrlResolver.trimUrl("https://maps.app.goo.gl/abc123,"))
    }

    @Test fun extractUrlTakesTheLinkOutOfSharedText() {
        assertEquals("https://maps.app.goo.gl/abc", MapsUrlResolver.extractUrl("Toit Brewpub\nhttps://maps.app.goo.gl/abc"))
        assertEquals("https://maps.app.goo.gl/abc", MapsUrlResolver.extractUrl("  see https://maps.app.goo.gl/abc.  "))
        assertEquals(null, MapsUrlResolver.extractUrl("no link here"))
    }

    // The links below are what maps.app.goo.gl redirected to in November 2026.
    private val listedPlace = "https://www.google.com/maps?q=Stillgood,+Stillgood+coffee+truck,+100+Feet+Rd,+HAL+2nd+Stage,+" +
        "Indiranagar,+Bengaluru,+Karnataka+560008&ftid=0x3bae133273a08e27:0x4b5c269406e8365e&entry=gps&shh=CAE"
    private val droppedPin = "https://www.google.com/maps/place/13.030169,77.570922/data=!4m6!3m5!1s0!7e2!8m2!3d13.0301692!4d77.570922" +
        "!17m2!4m1!1e3!18m1!1e1?utm_source=mstt_1&entry=gps"

    @Test fun aListedPlaceLinkNowCarriesItsNameInTheQueryNotThePath() {
        assertEquals(null, MapsUrlResolver.placeName(listedPlace))
        assertEquals(
            "Stillgood, Stillgood coffee truck, 100 Feet Rd, HAL 2nd Stage, Indiranagar, Bengaluru, Karnataka 560008",
            MapsUrlResolver.queryText(listedPlace),
        )
        assertEquals("Stillgood", MapsUrlResolver.shortName(MapsUrlResolver.queryText(listedPlace)!!))
        assertEquals(null, MapsUrlResolver.parseCoordinates(listedPlace))
    }

    @Test fun aDroppedPinKeepsItsCoordinatesAndIsNotTakenForAName() {
        val loc = MapsUrlResolver.parseCoordinates(droppedPin)!!
        assertEquals(13.0301692, loc.lat, 1e-7)
        assertEquals(77.570922, loc.lng, 1e-7)
        assertEquals(null, MapsUrlResolver.queryText(droppedPin))
        assertEquals(null, MapsUrlResolver.queryText("https://www.google.com/maps?q=13.03,77.57"))
        assertEquals(null, MapsUrlResolver.queryText("https://www.google.com/maps?q=13.03%2C77.57"))
    }

    @Test fun aPlaceIdLinkIsRecognisedAndNotSearchedAsText() {
        val url = "https://www.google.com/maps/place?q=place_id:ChIJf2-y-BwVrjsR0G-Ts_6cAVs"
        assertEquals("ChIJf2-y-BwVrjsR0G-Ts_6cAVs", MapsUrlResolver.placeId(url))
        assertEquals(null, MapsUrlResolver.queryText(url))
        assertEquals(null, MapsUrlResolver.placeId(listedPlace))
    }

    @Test fun anIntentRedirectFallsBackToItsWebPage() {
        val loc = "intent://maps.app.goo.gl/Nygodx1ofAtPP8v3A#Intent;package=com.google.android.gms;" +
            "action=com.google.firebase.dynamiclinks.VIEW_DYNAMIC_LINK;scheme=https;" +
            "S.browser_fallback_url=https://www.google.com/maps/place/Vennila's%2Bready%2Bmade%2Bidly;end"
        assertEquals("https://www.google.com/maps/place/Vennila's+ready+made+idly", MapsUrlResolver.intentFallback(loc))
        assertEquals(null, MapsUrlResolver.intentFallback("https://www.google.com/maps"))
        assertEquals(null, MapsUrlResolver.intentFallback("intent://x#Intent;scheme=https;end"))
    }
}
