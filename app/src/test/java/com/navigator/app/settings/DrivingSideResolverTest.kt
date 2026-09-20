package com.navigator.app.settings

import com.navigator.app.nav.model.DrivingSide
import org.junit.Assert.assertEquals
import org.junit.Test

class DrivingSideResolverTest {

    @Test fun explicitPreferencesWin() {
        // Explicit choice ignores the region entirely.
        assertEquals(DrivingSide.LEFT, DrivingSideResolver.resolve(DrivingSidePreference.LEFT, "US"))
        assertEquals(DrivingSide.RIGHT, DrivingSideResolver.resolve(DrivingSidePreference.RIGHT, "IN"))
    }

    @Test fun autoInfersIndiaAsLeft() {
        // The user's case: India drives on the left.
        assertEquals(DrivingSide.LEFT, DrivingSideResolver.resolve(DrivingSidePreference.AUTO, "IN"))
    }

    @Test fun autoInfersKnownLeftHandRegions() {
        for (r in listOf("GB", "JP", "AU", "ZA", "NZ", "IE", "TH", "MY", "SG", "KE")) {
            assertEquals("$r should be LEFT", DrivingSide.LEFT,
                DrivingSideResolver.resolve(DrivingSidePreference.AUTO, r))
        }
    }

    @Test fun autoInfersKnownRightHandRegions() {
        for (r in listOf("US", "DE", "FR", "IT", "ES", "CN", "BR", "RU", "CA", "MX")) {
            assertEquals("$r should be RIGHT", DrivingSide.RIGHT,
                DrivingSideResolver.resolve(DrivingSidePreference.AUTO, r))
        }
    }

    @Test fun autoIsCaseAndWhitespaceInsensitive() {
        assertEquals(DrivingSide.LEFT, DrivingSideResolver.resolve(DrivingSidePreference.AUTO, " in "))
        assertEquals(DrivingSide.LEFT, DrivingSideResolver.resolve(DrivingSidePreference.AUTO, "gb"))
    }

    @Test fun autoWithUnknownRegionIsUnknown() {
        // Undetectable region -> UNKNOWN (downstream treats as LHT/clockwise).
        assertEquals(DrivingSide.UNKNOWN, DrivingSideResolver.resolve(DrivingSidePreference.AUTO, ""))
    }

    @Test fun fromIdRoundTripAndDefault() {
        assertEquals(DrivingSidePreference.LEFT, DrivingSidePreference.fromId("left"))
        assertEquals(DrivingSidePreference.RIGHT, DrivingSidePreference.fromId("right"))
        assertEquals(DrivingSidePreference.AUTO, DrivingSidePreference.fromId("auto"))
        // Unknown / null falls back to AUTO.
        assertEquals(DrivingSidePreference.AUTO, DrivingSidePreference.fromId(null))
        assertEquals(DrivingSidePreference.AUTO, DrivingSidePreference.fromId("garbage"))
    }
}
