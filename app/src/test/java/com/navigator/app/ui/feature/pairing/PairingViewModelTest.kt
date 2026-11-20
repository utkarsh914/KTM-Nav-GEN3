package com.navigator.app.ui.feature.pairing

import com.navigator.app.ble.ScanSource
import com.navigator.app.ble.ScannedBike
import com.navigator.app.data.connection.ConnectionDetail
import com.navigator.app.data.connection.ConnectionPhase
import com.navigator.app.data.permissions.PermissionGrants
import com.navigator.app.data.permissions.PermissionPolicy
import com.navigator.app.data.settings.NavEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PairingViewModelTest {
    private class FakeScan : ScanSource {
        override val results = MutableStateFlow<List<ScannedBike>>(emptyList())
        override val scanning = MutableStateFlow(false)
        override val scanError = MutableStateFlow<Int?>(null)
        var starts = 0; var stops = 0
        override fun start() { starts++; scanning.value = true }
        override fun stop() { stops++; scanning.value = false }
    }

    private class FakeSession : PairingSession {
        val log = mutableListOf<String>()
        override fun begin(address: String, name: String?) { log += "begin $address" }
        override fun abandon() { log += "abandon" }
        override fun commit() { log += "commit" }
        override fun bondDebugDevice() { log += "debug" }
    }

    private val ktm = ScannedBike("AA:01", "KTM 1290", -50, pairedBefore = false)
    private val headset = ScannedBike("BB:02", "Headset", -70, pairedBefore = false)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun grants(nearby: Boolean = true) = PermissionGrants(nearby, true, true, true, true, true)

    private class Rig(
        val scan: FakeScan, val session: FakeSession, val link: MutableStateFlow<ConnectionDetail>,
        val perms: MutableStateFlow<com.navigator.app.data.permissions.PermissionsState>, val vm: PairingViewModel,
    )

    private fun kotlinx.coroutines.test.TestScope.rig(nearby: Boolean = true): Rig {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val scan = FakeScan(); val session = FakeSession()
        val link = MutableStateFlow(ConnectionDetail())
        val perms = MutableStateFlow(PermissionPolicy.build(grants(nearby), NavEngine.MIRROR))
        val vm = PairingViewModel(scan, perms, {}, link, session, { testScheduler.currentTime }, debug = true)
        vm.ui.launchIn(backgroundScope)
        return Rig(scan, session, link, perms, vm)
    }

    @Test fun listsDashesApartFromOtherDevicesAndScansOnOpen() = runTest(UnconfinedTestDispatcher()) {
        val r = rig()
        r.scan.results.value = listOf(headset, ktm)
        assertEquals(listOf(ktm), r.vm.ui.value.bikes)
        assertEquals(listOf(headset), r.vm.ui.value.others)
        assertEquals(1, r.scan.starts)
    }

    @Test fun withoutPermissionItIsBlockedAndDoesNotScan() = runTest(UnconfinedTestDispatcher()) {
        val r = rig(nearby = false)
        assertEquals(PairingPhase.Blocked(BlockReason.NO_PERMISSION), r.vm.ui.value.phase)
        assertEquals(0, r.scan.starts)
        r.perms.value = PermissionPolicy.build(grants(true), NavEngine.MIRROR)
        assertEquals(PairingPhase.Scanning, r.vm.ui.value.phase)
        assertEquals(1, r.scan.starts)
    }

    @Test fun pickingStopsTheScanAndStartsTheLink() = runTest(UnconfinedTestDispatcher()) {
        val r = rig()
        r.vm.pick(ktm)
        assertTrue(r.vm.ui.value.phase is PairingPhase.Confirming)
        assertEquals(listOf("begin AA:01"), r.session.log)
        assertFalse(r.scan.scanning.value)
        r.vm.pick(headset)
        assertEquals(listOf("begin AA:01"), r.session.log)
    }

    @Test fun onlyARealHandshakeEndsPairing() = runTest(UnconfinedTestDispatcher()) {
        val r = rig()
        r.vm.pick(ktm)
        r.link.value = ConnectionDetail(phase = ConnectionPhase.HANDSHAKE_FIRST_PAIR)
        assertTrue(r.vm.ui.value.phase is PairingPhase.Confirming)
        r.link.value = ConnectionDetail(phase = ConnectionPhase.AUTHENTICATED)
        assertEquals(PairingEvent.Paired, r.vm.events.first())
        assertEquals(listOf("begin AA:01", "commit"), r.session.log)
    }

    @Test fun aLinkAlreadyAuthenticatedAtThePickDoesNotCompleteThePairing() = runTest(UnconfinedTestDispatcher()) {
        val r = rig()
        r.link.value = ConnectionDetail(phase = ConnectionPhase.AUTHENTICATED)
        r.vm.pick(ktm)
        assertEquals(listOf("begin AA:01"), r.session.log)
        r.link.value = ConnectionDetail(phase = ConnectionPhase.CONNECTING)
        r.link.value = ConnectionDetail(phase = ConnectionPhase.AUTHENTICATED)
        assertEquals(PairingEvent.Paired, r.vm.events.first())
    }

    @Test fun anAlreadyAuthenticatedLinkDoesNotPairWithoutAnAttempt() = runTest(UnconfinedTestDispatcher()) {
        val r = rig()
        r.link.value = ConnectionDetail(phase = ConnectionPhase.AUTHENTICATED)
        assertTrue(r.session.log.isEmpty())
    }

    @Test fun twoMinutesWithoutAnswerFailsAndRestoresTheOldBike() = runTest(UnconfinedTestDispatcher()) {
        val r = rig()
        r.vm.pick(ktm)
        advanceTimeBy(119_000)
        assertTrue(r.vm.ui.value.phase is PairingPhase.Confirming)
        advanceTimeBy(2_000)
        assertEquals(PairingPhase.Failed, r.vm.ui.value.phase)
        assertEquals("abandon", r.session.log.last())
    }

    @Test fun retryGoesBackToScanning() = runTest(UnconfinedTestDispatcher()) {
        val r = rig()
        r.vm.pick(ktm); advanceTimeBy(121_000)
        r.vm.retry()
        assertEquals(PairingPhase.Scanning, r.vm.ui.value.phase)
        assertEquals(2, r.scan.starts)
    }

    @Test fun cancelAbandonsAndScansAgain() = runTest(UnconfinedTestDispatcher()) {
        val r = rig()
        r.vm.pick(ktm)
        r.vm.cancel()
        assertEquals("abandon", r.session.log.last())
        assertEquals(PairingPhase.Scanning, r.vm.ui.value.phase)
    }

    @Test fun theDebugSkipBondsAndLeaves() = runTest(UnconfinedTestDispatcher()) {
        val r = rig()
        r.vm.skipForDebug()
        assertEquals(PairingEvent.Paired, r.vm.events.first())
        assertEquals(listOf("debug"), r.session.log)
    }
}
