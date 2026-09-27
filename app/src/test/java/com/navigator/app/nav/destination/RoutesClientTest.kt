package com.navigator.app.nav.destination

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutesClientTest {

    /** A route with a valid encoded polyline (so parseRoutes keeps it). */
    private fun routeJson(duration: String, staticDuration: String?): String {
        val staticField = staticDuration?.let { """"staticDuration": "$it",""" } ?: ""
        return """
            {
              "duration": "$duration",
              $staticField
              "distanceMeters": 12000,
              "polyline": { "encodedPolyline": "_p~iF~ps|U_ulLnnqC_mqNvxq`@" },
              "routeToken": "tok123"
            }
        """.trimIndent()
    }

    private fun wrap(vararg routes: String): String =
        """{ "routes": [ ${routes.joinToString(",")} ] }"""

    @Test fun delayIsLiveMinusStatic() {
        // Live 3120s, free-flow 2700s -> 420s delay.
        val out = RoutesClient.parseRoutes(wrap(routeJson("3120s", "2700s")))
        assertEquals(1, out.size)
        assertEquals(3120, out[0].durationSeconds)
        assertEquals(420, out[0].delaySeconds)
    }

    @Test fun noDelayWhenStaticEqualsLive() {
        val out = RoutesClient.parseRoutes(wrap(routeJson("1800s", "1800s")))
        assertEquals(0, out[0].delaySeconds)
    }

    @Test fun delayNeverNegativeWhenStaticExceedsLive() {
        // Free-flow longer than live (shouldn't happen, but must clamp to 0).
        val out = RoutesClient.parseRoutes(wrap(routeJson("1500s", "1800s")))
        assertEquals(0, out[0].delaySeconds)
    }

    @Test fun missingStaticDurationYieldsZeroDelay() {
        val out = RoutesClient.parseRoutes(wrap(routeJson("2000s", null)))
        assertEquals(2000, out[0].durationSeconds)
        assertEquals(0, out[0].delaySeconds)
    }

    @Test fun parsesMultipleAlternatesBestFirst() {
        val out = RoutesClient.parseRoutes(
            wrap(routeJson("1000s", "900s"), routeJson("1200s", "1000s")),
        )
        assertEquals(2, out.size)
        assertEquals(100, out[0].delaySeconds)
        assertEquals(200, out[1].delaySeconds)
    }

    @Test fun emptyOnNoRoutes() {
        assertTrue(RoutesClient.parseRoutes("""{ "routes": [] }""").isEmpty())
    }

    // ---- Multi-stop request body ----------------------------------------

    @Test fun noIntermediatesRequestsAlternates() {
        val body = RoutesClient.buildRequestBody(
            origin = 12.0 to 77.0,
            dest = 13.0 to 78.0,
            travelMode = "TWO_WHEELER",
        )
        assertTrue(body.getBoolean("computeAlternativeRoutes"))
        assertTrue(!body.has("intermediates"))
    }

    @Test fun intermediatesDisableAlternatesAndAreOrdered() {
        val body = RoutesClient.buildRequestBody(
            origin = 12.0 to 77.0,
            dest = 13.0 to 78.0,
            travelMode = "DRIVE",
            intermediates = listOf(12.3 to 77.3, 12.6 to 77.6),
        )
        // Alternates are incompatible with intermediates -> must be off.
        assertTrue(!body.getBoolean("computeAlternativeRoutes"))
        val inter = body.getJSONArray("intermediates")
        assertEquals(2, inter.length())
        val first = inter.getJSONObject(0).getJSONObject("location").getJSONObject("latLng")
        assertEquals(12.3, first.getDouble("latitude"), 1e-9)
        assertEquals(77.3, first.getDouble("longitude"), 1e-9)
    }

    @Test fun parsesLegsForMultiStopRoute() {
        val json = """
            {
              "routes": [{
                "duration": "1000s",
                "staticDuration": "1000s",
                "distanceMeters": 20000,
                "polyline": { "encodedPolyline": "_p~iF~ps|U_ulLnnqC_mqNvxq`@" },
                "routeToken": "tok123",
                "legs": [
                  { "duration": "400s", "distanceMeters": 8000,
                    "polyline": { "encodedPolyline": "_p~iF~ps|U_ulLnnqC" } },
                  { "duration": "600s", "distanceMeters": 12000,
                    "polyline": { "encodedPolyline": "_mqNvxq`@" } }
                ]
              }]
            }
        """.trimIndent()
        val out = RoutesClient.parseRoutes(json)
        assertEquals(1, out.size)
        assertEquals(2, out[0].legs.size)
        assertEquals(400, out[0].legs[0].durationSeconds)
        assertEquals(12000, out[0].legs[1].distanceMeters)
    }

    @Test fun singleLegNotExposedAsLegs() {
        // A one-leg route (no stops) should report no per-leg breakdown.
        val out = RoutesClient.parseRoutes(wrap(routeJson("1000s", "900s")))
        assertTrue(out[0].legs.isEmpty())
    }
}
