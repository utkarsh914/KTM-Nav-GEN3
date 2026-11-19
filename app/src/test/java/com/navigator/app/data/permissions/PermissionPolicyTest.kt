package com.navigator.app.data.permissions

import com.navigator.app.data.settings.NavEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionPolicyTest {
    private val none = PermissionGrants(false, false, false, false, false, false)
    private val all = PermissionGrants(true, true, true, true, true, true)

    @Test fun rowsComeInRequestOrder() {
        val ids = PermissionPolicy.build(none, NavEngine.IN_APP).items.map { it.id }
        assertEquals(
            listOf(
                PermissionId.NEARBY_DEVICES, PermissionId.LOCATION, PermissionId.LOCATION_ALWAYS,
                PermissionId.NOTIFICATIONS, PermissionId.NOTIFICATION_ACCESS, PermissionId.BATTERY,
            ),
            ids,
        )
    }

    @Test fun inAppEngineDoesNotRequireNotificationAccess() {
        val s = PermissionPolicy.build(none, NavEngine.IN_APP)
        assertFalse(s[PermissionId.NOTIFICATION_ACCESS].required)
        assertEquals(
            listOf(PermissionId.NEARBY_DEVICES, PermissionId.LOCATION, PermissionId.NOTIFICATIONS),
            s.requiredMissing.map { it.id },
        )
    }

    @Test fun mirrorEngineRequiresNotificationAccess() {
        val s = PermissionPolicy.build(none, NavEngine.MIRROR)
        assertTrue(s[PermissionId.NOTIFICATION_ACCESS].required)
        assertEquals(4, s.requiredMissing.size)
    }

    @Test fun optionalPermissionsNeverRaiseAttention() {
        val s = PermissionPolicy.build(all.copy(locationAlways = false, batteryUnrestricted = false), NavEngine.IN_APP)
        assertFalse(s.attentionNeeded)
    }

    @Test fun anyMissingCountsOptionalRowsToo() {
        val s = PermissionPolicy.build(all.copy(batteryUnrestricted = false), NavEngine.IN_APP)
        assertFalse(s.attentionNeeded)
        assertTrue(s.anyMissing)
        assertFalse(PermissionPolicy.build(all, NavEngine.IN_APP).anyMissing)
    }

    @Test fun attentionClearsWhenRequiredAreGranted() {
        assertTrue(PermissionPolicy.build(all.copy(location = false), NavEngine.IN_APP).attentionNeeded)
        val s = PermissionPolicy.build(all, NavEngine.MIRROR)
        assertFalse(s.attentionNeeded)
        assertTrue(s.requiredMissing.isEmpty())
    }
}
