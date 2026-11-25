package com.navigator.app.data.permissions

import android.content.Context
import com.navigator.app.data.settings.NavEngine
import com.navigator.app.data.settings.SettingsRepository
import com.navigator.app.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PermissionsRepositoryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var settings: SettingsRepository
    private var grants = PermissionGrants(false, false, false, false, false, false)

    @Before fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        ctx.getSharedPreferences("opendash_pairing", Context.MODE_PRIVATE).edit().clear().commit()
        settings = SettingsRepository(AppSettings(ctx))
    }

    @After fun tearDown() = scope.cancel()

    @Test fun refreshRereadsGrants() {
        val repo = PermissionsRepository({ grants }, settings, scope)
        assertTrue(repo.state.value.attentionNeeded)
        grants = PermissionGrants(true, true, false, true, false, false)
        assertTrue(repo.state.value.attentionNeeded) // not re-read yet
        repo.refresh()
        assertFalse(repo.state.value.attentionNeeded) // updated before refresh() returns
    }

    @Test fun refreshIsSynchronousEvenWhenTheScopeIsAsync() {
        val repo = PermissionsRepository({ grants }, settings, CoroutineScope(SupervisorJob() + Dispatchers.Default))
        grants = PermissionGrants(true, true, true, true, true, true)
        repo.refresh()
        assertFalse(repo.state.value.anyMissing)
    }

    @Test fun withoutTheInAppEngineNotificationAccessIsRequiredEvenIfInAppIsStored() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        val noKey = SettingsRepository(AppSettings(ctx)) { false }
        assertEquals("stored default", NavEngine.IN_APP, noKey.snapshot.value.engine)
        assertEquals("what runs", NavEngine.MIRROR, noKey.engineInUse())
        grants = PermissionGrants(true, true, false, true, false, false)
        val repo = PermissionsRepository({ grants }, noKey, scope)
        assertTrue("notification access is missing and required", repo.state.value.attentionNeeded)
    }

    @Test fun engineSwitchChangesWhatIsRequired() {
        grants = PermissionGrants(true, true, false, true, false, false)
        val repo = PermissionsRepository({ grants }, settings, scope)
        assertFalse(repo.state.value.attentionNeeded)
        settings.update { googleNavEnabled = false }
        assertEquals(NavEngine.MIRROR, settings.snapshot.value.engine)
        assertTrue(repo.state.value.attentionNeeded)
    }
}
