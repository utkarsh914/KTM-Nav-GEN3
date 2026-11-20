package com.navigator.app.ui.feature.settings.picker

import com.navigator.app.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class KnownNamesTest {
    @Test fun everyKnownNavAppHasAReadableName() {
        for (pkg in AppSettings.KNOWN_NAV_APPS) assertNotEquals("no name for $pkg", pkg, KnownNames.of(pkg))
    }

    @Test fun anUnknownPackageKeepsItsId() {
        assertEquals("org.example.nav", KnownNames.of("org.example.nav"))
    }
}
