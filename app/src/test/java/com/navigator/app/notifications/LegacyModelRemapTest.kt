package com.navigator.app.notifications

import com.navigator.app.ble.BccuProtocol.TurnIcon
import com.navigator.app.nav.ktm.KtmManeuverMapping
import com.navigator.app.nav.model.NormalizedManeuver
import com.navigator.app.nav.model.RoundaboutRotation
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The legacy model's roundabout classes, once corrected, must agree with the
 * hardware-verified SDK mapping for the same Google Maps icon.
 */
class LegacyModelRemapTest {

    // Legacy training labels: section index per exit, reused for both banks.
    private val legacySect = mapOf(
        NormalizedManeuver.ROUNDABOUT_SLIGHT_RIGHT to 2,
        NormalizedManeuver.ROUNDABOUT_RIGHT to 4,
        NormalizedManeuver.ROUNDABOUT_SHARP_RIGHT to 6,
        NormalizedManeuver.ROUNDABOUT_STRAIGHT to 8,
        NormalizedManeuver.ROUNDABOUT_SLIGHT_LEFT to 10,
        NormalizedManeuver.ROUNDABOUT_LEFT to 12,
        NormalizedManeuver.ROUNDABOUT_SHARP_LEFT to 14,
        NormalizedManeuver.ROUNDABOUT_UTURN to 16,
    )

    @Test fun correctedLegacyOutputMatchesSdkMapping() {
        for ((maneuver, sect) in legacySect) {
            assertEquals(
                "cw $maneuver",
                KtmManeuverMapping.toTurnIcon(maneuver, RoundaboutRotation.CLOCKWISE),
                LegacyModelRemap.correct(26 + sect - 1),
            )
            assertEquals(
                "ccw $maneuver",
                KtmManeuverMapping.toTurnIcon(maneuver, RoundaboutRotation.COUNTERCLOCKWISE),
                LegacyModelRemap.correct(42 + sect - 1),
            )
        }
    }

    @Test fun nonRoundaboutCodesPassThrough() {
        assertEquals(TurnIcon.QUITE_LEFT, LegacyModelRemap.correct(12))
        assertEquals(TurnIcon.ENTER_HIGHWAY_RIGHT_LANE, LegacyModelRemap.correct(14))
    }
}
