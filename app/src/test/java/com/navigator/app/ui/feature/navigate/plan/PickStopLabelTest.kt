package com.navigator.app.ui.feature.navigate.plan

import com.navigator.app.R
import org.junit.Assert.assertEquals
import org.junit.Test

class PickStopLabelTest {
    @Test fun noInternetIsSaidBeforeTheStopLimit() {
        assertEquals(R.string.trip_stop_offline, pickUseLabel(canAdd = true, offline = true))
        assertEquals("offline is the reason even when the limit is also reached", R.string.trip_stop_offline, pickUseLabel(canAdd = false, offline = true))
        assertEquals(R.string.route_stop_limit, pickUseLabel(canAdd = false, offline = false))
        assertEquals(R.string.stop_add_here, pickUseLabel(canAdd = true, offline = false))
    }
}
