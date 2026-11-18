package com.navigator.app.notifications

import android.app.Notification
import com.navigator.app.settings.AppSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavNotificationFilterTest {

    private val maps = NavNotificationFilter.GOOGLE_MAPS
    private val known = AppSettings.KNOWN_NAV_APPS

    private fun check(pkg: String, flags: Int = 0, category: String? = null, override: String? = null) =
        NavNotificationFilter.isNavigation(pkg, flags, category, override, known)

    @Test fun mapsOngoingIsGuidance() =
        assertTrue(check(maps, flags = Notification.FLAG_ONGOING_EVENT))

    @Test fun mapsNavigationCategoryAloneIsGuidance() =
        assertTrue(check(maps, category = Notification.CATEGORY_NAVIGATION))

    @Test fun mapsTrafficOrSharingAlertIsNotGuidance() {
        assertFalse(check(maps))
        assertFalse(check(maps, category = Notification.CATEGORY_RECOMMENDATION))
        assertFalse(check(maps, category = Notification.CATEGORY_STATUS))
    }

    @Test fun mapsGroupSummaryIsNotGuidance() =
        assertFalse(check(maps, flags = Notification.FLAG_ONGOING_EVENT or Notification.FLAG_GROUP_SUMMARY))

    @Test fun otherKnownAppsKeepPackageOnlyRule() {
        assertTrue(check("com.waze"))
        assertTrue(check("net.osmand"))
    }

    @Test fun unknownPackageIsNeverGuidance() =
        assertFalse(check("com.example.chat", flags = Notification.FLAG_ONGOING_EVENT))

    @Test fun overrideReplacesKnownList() {
        assertTrue(check("com.custom.nav", override = "com.custom.nav"))
        assertFalse(check(maps, flags = Notification.FLAG_ONGOING_EVENT, override = "com.custom.nav"))
    }
}
