package com.navigator.app.data.connection

import android.content.Context
import com.navigator.app.ble.BccuConnectionService.ConnectionState
import com.navigator.app.data.settings.SettingsRepository
import com.navigator.app.nav.DashPreview
import com.navigator.app.settings.AppSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ConnectionRepositoryTest {
    private lateinit var ctx: Context
    private lateinit var settings: SettingsRepository
    private val state = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val btOn = MutableStateFlow(true)
    private val hint = MutableStateFlow(false)
    private var stopped = 0
    private var cancelled = 0

    @Before fun setUp() {
        ctx = RuntimeEnvironment.getApplication()
        ctx.getSharedPreferences("opendash_pairing", Context.MODE_PRIVATE).edit().clear().commit()
        settings = SettingsRepository(AppSettings(ctx))
    }

    private fun TestScope.repo() = ConnectionRepository(
        scope = backgroundScope.also { },
        settings = settings,
        sources = LinkSources(
            state = state, scanning = MutableStateFlow(false), attempt = MutableStateFlow(0),
            repairHint = hint, lastError = MutableStateFlow(null), deviceName = MutableStateFlow(null),
            bluetoothOn = btOn,
        ),
        stopService = { stopped++ },
        cancelNotifications = { cancelled++ },
    )

    @Test fun rePairForgetsTheBikeItsFlagAndItsKeys() = runTest(UnconfinedTestDispatcher()) {
        val s = settings.legacy
        s.bondedDeviceAddress = "AA:BB:CC:DD:EE:FF"
        s.bondedDeviceName = "KTM3638"
        s.markPairedBefore("AA:BB:CC:DD:EE:FF")
        s.storeSessionKeys("AA:BB:CC:DD:EE:FF", listOf(ByteArray(16) { 1 }))
        val repo = repo()
        repo.rePair()
        assertNull(s.bondedDeviceAddress)
        assertNull(s.bondedDeviceName)
        assertFalse(s.hasPairedBefore("AA:BB:CC:DD:EE:FF"))
        assertNull(s.loadSessionKeys("AA:BB:CC:DD:EE:FF"))
        assertEquals(1, stopped)
        assertFalse("the snapshot follows", settings.snapshot.value.isPaired)
        assertEquals(BikeStatus.NotPaired, repo.status.value)
    }

    @Test fun rePairLeavesOtherBikesAlone() = runTest(UnconfinedTestDispatcher()) {
        val s = settings.legacy
        s.bondedDeviceAddress = "AA:00"
        s.markPairedBefore("AA:00")
        s.markPairedBefore("BB:00")
        repo().rePair()
        assertTrue(s.hasPairedBefore("BB:00"))
    }

    @Test fun statusFollowsTheLink() = runTest(UnconfinedTestDispatcher()) {
        settings.legacy.bondedDeviceAddress = "AA:00"
        settings.legacy.bondedDeviceName = "KTM3638"
        settings.refresh()
        val repo = repo()
        assertEquals(BikeStatus.Disconnected("KTM3638"), repo.status.value)
        state.value = ConnectionState.AUTHENTICATED
        assertEquals(BikeStatus.Connected("KTM3638"), repo.status.value)
        btOn.value = false
        assertEquals(BikeStatus.BluetoothOff, repo.status.value)
        btOn.value = true
        state.value = ConnectionState.DISCONNECTED
        hint.value = true
        assertEquals(BikeStatus.NeedsRepair("KTM3638"), repo.status.value)
    }

    @Test fun pairingDuringTheSessionIsSeenWithoutASettingsChange() = runTest(UnconfinedTestDispatcher()) {
        settings.legacy.bondedDeviceAddress = "AA:00"
        settings.refresh()
        val repo = repo()
        state.value = ConnectionState.CONNECTING
        assertEquals(BikeStatus.Connecting(null, firstPair = true), repo.status.value)
        settings.legacy.markPairedBefore("AA:00") // what the service does after the handshake, on the raw prefs
        state.value = ConnectionState.AUTHENTICATED
        state.value = ConnectionState.DISCONNECTED
        state.value = ConnectionState.CONNECTING
        assertEquals(BikeStatus.Connecting(null, firstPair = false), repo.status.value)
    }

    @Test fun quitStopsTheLinkAndClearsNotifications() = runTest(UnconfinedTestDispatcher()) {
        repo().quit()
        assertEquals(1, stopped)
        assertEquals(1, cancelled)
    }

    @Test fun bannersAreCapturedForTheStatusScreen() {
        DashPreview.banner("Hi Sam!")
        assertEquals("Hi Sam!", DashPreview.state.value.lastBanner)
    }
}
