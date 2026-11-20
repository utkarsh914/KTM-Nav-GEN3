package com.navigator.app.data.mirror

import android.content.Context
import com.navigator.app.data.permissions.PermissionGrants
import com.navigator.app.data.permissions.PermissionsRepository
import com.navigator.app.data.settings.SettingsRepository
import com.navigator.app.notifications.NotificationRepository.NavGuidance
import com.navigator.app.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MirrorRepositoryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private var grants = PermissionGrants(true, true, false, true, notificationAccess = true, batteryUnrestricted = true)
    private val listener = MutableStateFlow(true)
    private val paused = MutableStateFlow(false)
    private val guidance = MutableStateFlow<NavGuidance?>(null)
    private val navText = MutableStateFlow<String?>(null)
    private val navPackage = MutableStateFlow<String?>(null)
    private val last = MutableStateFlow(0L)
    private var sending: Boolean? = null
    private lateinit var permissions: PermissionsRepository

    @Before fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        ctx.getSharedPreferences("opendash_pairing", Context.MODE_PRIVATE).edit().clear().commit()
        permissions = PermissionsRepository({ grants }, SettingsRepository(AppSettings(ctx)), scope)
    }

    @After fun tearDown() = scope.cancel()

    private fun repo() = MirrorRepository(
        permissions, now = { 50_000L }, tickMs = 60_000L, listenerConnected = listener, paused = paused,
        guidance = guidance, navText = navText, navPackage = navPackage, lastUpdateMs = last,
        setPausedImpl = { sending = !it },
    )

    @Test fun readyThenLiveWhenGoogleMapsGuides() = runBlocking {
        val r = repo()
        assertTrue(r.status.first() is MirrorStatus.Ready)
        navPackage.value = "com.google.android.apps.maps"
        guidance.value = NavGuidance("400 m", "MG Road", null, null, "Turn right")
        last.value = 49_000L
        val s = r.status.first() as MirrorStatus.Live
        assertEquals(Freshness.JustNow, s.freshness)
    }

    @Test fun aRevokedAccessIsSeenAfterARefresh() = runBlocking {
        val r = repo()
        assertTrue(r.status.first() is MirrorStatus.Ready)
        grants = grants.copy(notificationAccess = false)
        r.refresh()
        assertTrue(r.status.first() is MirrorStatus.Setup)
    }

    @Test fun theListenerDroppingIsASetupRow() = runBlocking {
        val r = repo()
        listener.value = false
        val s = r.status.first() as MirrorStatus.Setup
        assertEquals(false, s.rows.first { it.kind == MirrorSetupKind.Listener }.done)
    }

    @Test fun sendingIsTheOppositeOfPaused() {
        val r = repo()
        r.setSending(false)
        assertEquals(false, sending)
        r.setSending(true)
        assertEquals(true, sending)
    }
}
