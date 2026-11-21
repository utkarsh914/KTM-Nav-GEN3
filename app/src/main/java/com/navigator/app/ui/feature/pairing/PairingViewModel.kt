package com.navigator.app.ui.feature.pairing

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.navigator.app.BuildConfig
import com.navigator.app.app.AppContainer
import com.navigator.app.ble.BikeScanning
import com.navigator.app.ble.ScanSource
import com.navigator.app.ble.ScannedBike
import com.navigator.app.data.connection.ConnectionDetail
import com.navigator.app.data.connection.ConnectionPhase
import com.navigator.app.data.permissions.PermissionId
import com.navigator.app.data.permissions.PermissionsState
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PairingUi(
    val phase: PairingPhase,
    /** Likely dashes (named KTM), best signal first. */
    val bikes: List<ScannedBike> = emptyList(),
    /** Everything else, folded away but reachable for dashes with unusual names. */
    val others: List<ScannedBike> = emptyList(),
    val scanning: Boolean = false,
    /** The Bluetooth stack refused the scan (for example scanning too often). */
    val scanFailed: Boolean = false,
    val debugSkip: Boolean = false,
)

sealed interface PairingEvent {
    /** The handshake completed (or a debug skip): leave the pairing screens. */
    data object Paired : PairingEvent
}

/** ON6 to ON9: scans, pairs, and decides which of the four screens shows. */
class PairingViewModel(
    private val scanner: ScanSource,
    private val permissions: StateFlow<PermissionsState>,
    private val refreshPermissions: () -> Unit,
    private val connection: StateFlow<ConnectionDetail>,
    private val session: PairingSession,
    private val clock: () -> Long,
    private val debug: Boolean = BuildConfig.DEBUG,
) : ViewModel() {
    private val attempt = MutableStateFlow<Attempt?>(null)
    private val failed = MutableStateFlow(false)
    /** An authenticated link left over from before the pick is not this pairing: wait until it has dropped. */
    private val linkDropped = MutableStateFlow(false)
    private val now = MutableStateFlow(clock())
    private var timer: Job? = null

    private val _events = Channel<PairingEvent>(Channel.BUFFERED)
    val events: Flow<PairingEvent> = _events.receiveAsFlow()

    private val phase: Flow<PairingPhase> = combine(permissions, connection, attempt, failed, now) { perms, link, a, f, t ->
        PairingPolicy.phase(perms[PermissionId.NEARBY_DEVICES].granted, link.bluetoothOn, a, f, t, link.phase, link.linkUp)
    }

    val ui: StateFlow<PairingUi> = combine(
        phase, scanner.results, combine(scanner.scanning, scanner.scanError) { s, e -> s to (e != null) },
    ) { phase, found, (scanning, scanFailed) ->
        val sorted = BikeScanning.sorted(found)
        PairingUi(
            phase, bikes = sorted.filter { it.isLikelyDash }, others = sorted.filterNot { it.isLikelyDash },
            scanning = scanning, scanFailed = scanFailed, debugSkip = debug,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PairingUi(PairingPhase.Scanning, debugSkip = debug))

    init {
        // Scan whenever the screen is the list, and only then: a Bluetooth scan is for finding a bike, not for waiting.
        viewModelScope.launch {
            phase.map { it is PairingPhase.Scanning }.distinctUntilChanged().collect { if (it) scanner.start() else scanner.stop() }
        }
        // Only leave once the handshake genuinely completes, never on the tap itself.
        viewModelScope.launch {
            combine(attempt, connection) { a, link ->
                if (a != null && link.phase != ConnectionPhase.AUTHENTICATED) linkDropped.value = true
                a != null && link.phase == ConnectionPhase.AUTHENTICATED && linkDropped.value
            }.collect { done ->
                if (done) {
                    timer?.cancel()
                    attempt.value = null
                    session.commit()
                    _events.send(PairingEvent.Paired)
                }
            }
        }
    }

    fun refreshPermissions() = refreshPermissions.invoke()

    fun scanAgain() { if (!scanner.scanning.value) scanner.start() }

    fun pick(bike: ScannedBike) {
        if (attempt.value != null) return
        scanner.stop()
        failed.value = false
        val start = clock()
        now.value = start
        linkDropped.value = false
        session.begin(bike.address, bike.name)
        attempt.value = Attempt(bike.address, bike.name, start)
        timer?.cancel()
        timer = viewModelScope.launch {
            while (true) {
                delay(TICK_MS)
                now.value = clock()
                val a = attempt.value ?: break
                if (PairingPolicy.timedOut(a.startedAtMs, now.value)) { giveUp(); break }
            }
        }
    }

    /** The rider cancelled: back to the list. */
    fun cancel() {
        if (attempt.value == null) return
        timer?.cancel()
        session.abandon()
        attempt.value = null
    }

    /** From the failure screen: look again. */
    fun retry() { failed.value = false }

    fun skipForDebug() {
        if (!debug) return
        session.bondDebugDevice()
        _events.trySend(PairingEvent.Paired)
    }

    private fun giveUp() {
        session.abandon()
        attempt.value = null
        failed.value = true
    }

    override fun onCleared() {
        timer?.cancel()
        scanner.stop()
        if (attempt.value != null) session.abandon()
    }

    companion object {
        private const val TICK_MS = 1_000L

        fun factory(container: AppContainer, context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PairingViewModel(
                container.bikeScanner, container.permissions.state, container.permissions::refresh, container.connection.detail,
                AndroidPairingSession(context.applicationContext, container.settings), System::currentTimeMillis,
            ) as T
        }
    }
}
