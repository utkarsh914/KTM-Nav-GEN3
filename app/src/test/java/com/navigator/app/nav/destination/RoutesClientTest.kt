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
}
