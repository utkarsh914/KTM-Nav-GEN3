package com.navigator.app.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RideLimitPolicyTest {
    @Test fun raisedForOnlyWhenTheRidesExceedTheLimit() {
        assertNull(RideLimitPolicy.raisedFor(100, 100))
        assertNull(RideLimitPolicy.raisedFor(3, 100))
        assertEquals(101, RideLimitPolicy.raisedFor(101, 100))
        assertEquals(1_000, RideLimitPolicy.raisedFor(5_000, 100))
    }

    @Test fun aNewPhoneWithNoStoredLimitKeepsItsRides() {
        assertEquals(150, RideLimitPolicy.initial(null, 150, 100))
        assertNull(RideLimitPolicy.initial(null, 80, 100))
        assertNull(RideLimitPolicy.initial(50, 150, 100)) // the rider chose 50: not ours to change
    }
}
