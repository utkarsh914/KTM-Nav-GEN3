package com.navigator.app.nav.providers

import org.junit.Assert.assertEquals
import org.junit.Test

class RideKeyTest {
    @Test fun aReplanOfTheSameRideKeepsItsKey() {
        assertEquals(7L, chooseRideKey(continuesRide = true, previousRideKey = 7L, tripId = 9L))
    }

    @Test fun everythingElseIsANewRide() {
        assertEquals(9L, chooseRideKey(continuesRide = false, previousRideKey = 7L, tripId = 9L))
        assertEquals(9L, chooseRideKey(continuesRide = true, previousRideKey = null, tripId = 9L))
    }
}
