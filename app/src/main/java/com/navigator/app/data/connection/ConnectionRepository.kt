package com.navigator.app.data.connection

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.navigator.app.ble.BccuConnectionService
import com.navigator.app.ble.BccuConnectionService.ConnectionState
import com.navigator.app.ble.BccuProtocol
import com.navigator.app.data.settings.SettingsRepository
import com.navigator.app.nav.DashPreview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The raw facts about the link. The defaults read the service's flows; tests pass their own. */
class LinkSources(
    val state: StateFlow<ConnectionState> = BccuConnectionService.connectionState,
    val scanning: StateFlow<Boolean> = BccuConnectionService.scanning,
    val attempt: StateFlow<Int> = BccuConnectionService.attempt,
    val repairHint: StateFlow<Boolean> = BccuConnectionService.repairHint,
    val lastError: StateFlow<String?> = BccuConnectionService.lastError,
    val deviceName: StateFlow<String?> = BccuConnectionService.deviceName,
    val linkUp: StateFlow<Boolean> = BccuConnectionService.linkUpState,
    val bluetoothOn: StateFlow<Boolean>,
)

/** What the dash is currently told to show, as the status screen wants it. */
data class DashSnapshot(val preview: DashPreview.State)

/**
 * The bike as the UI sees it: status, detail, what the dash shows, the test sends, re-pair and quit.
 * Wraps [BccuConnectionService] (whose BLE internals are untouched) and the pairing settings.
 */
class ConnectionRepository(
    scope: CoroutineScope,
    private val settings: SettingsRepository,
    sources: LinkSources,
    private val stopService: () -> Unit,
    private val cancelNotifications: () -> Unit,
    private val retryService: () -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _lastSeenMs = MutableStateFlow(settings.legacy.bondedLastSeenMs.takeIf { it > 0 })

    /** When the bike was last linked, kept across restarts; null when it never was (or after Re-pair). */
    val lastSeenMs: StateFlow<Long?> = _lastSeenMs.asStateFlow()

    init {
        scope.launch {
            var wasUp = false
            sources.state.collectLatest { st ->
                val up = st == ConnectionState.AUTHENTICATED
                // The drop is the last moment it was there; while it is, the time keeps moving so a killed app is not far off.
                if (wasUp && !up) markSeen()
                wasUp = up
                while (up) { markSeen(); delay(LAST_SEEN_EVERY_MS) }
            }
        }
    }

    private fun markSeen() {
        // Re-pair forgets the bike: its drop must not stamp a time on nobody.
        if (settings.legacy.bondedDeviceAddress == null) return
        val t = now()
        _lastSeenMs.value = t
        settings.legacy.bondedLastSeenMs = t
    }

    val detail: StateFlow<ConnectionDetail> = combine(
        sources.state, sources.bluetoothOn, sources.scanning, combine(sources.attempt, sources.linkUp) { a, up -> a to up },
        combine(sources.repairHint, sources.lastError, settings.snapshot) { hint, error, snap -> Triple(hint, error, snap) },
    ) { state, btOn, scanning, (attempt, linkUp), (hint, error, snap) ->
        // Read on every change of the link: the service marks a bike paired in the raw prefs, which the snapshot does not see.
        val paired = snap.bondedDeviceAddress?.let { settings.legacy.hasPairedBefore(it) } ?: false
        ConnectionMapping.detail(state, btOn, scanning, attempt, hint, paired, error, linkUp)
    }.stateIn(scope, SharingStarted.Eagerly, ConnectionDetail())

    val status: StateFlow<BikeStatus> = combine(detail, settings.snapshot, sources.deviceName) { d, snap, live ->
        ConnectionMapping.status(d, snap.bondedDeviceAddress, live ?: snap.bondedDeviceName)
    }.stateIn(scope, SharingStarted.Eagerly, BikeStatus.NotPaired)

    val dash: StateFlow<DashSnapshot> = DashPreview.state.map(::DashSnapshot)
        .stateIn(scope, SharingStarted.Eagerly, DashSnapshot(DashPreview.state.value))

    /** Show a message on the dash banner (does nothing when the bike is not connected). */
    fun sendTestNotification(text: String) {
        BccuConnectionService.sendNotificationIfRunning(text, BccuProtocol.NotificationIcon.NOTIFICATION_WAYPOINT)
    }

    fun sendTestTurn() {
        BccuConnectionService.sendTurnIconIfRunning(BccuProtocol.TurnIcon.GO_STRAIGHT)
        BccuConnectionService.sendGuidanceIfRunning("50 m", "Test Road")
    }

    /**
     * Forget this bike so the next pairing starts from scratch. The paired-before flag and the session
     * keys go too, not just the address: re-pairing the same bike would otherwise still reply
     * GENERATE_KEYS and stall if the dash lost its keys.
     */
    fun rePair() {
        val s = settings.legacy
        s.bondedDeviceAddress?.let { s.clearPairedBefore(it) }
        s.bondedDeviceAddress = null
        s.bondedDeviceName = null
        s.bondedLastSeenMs = 0L
        s.bondedPairedMs = 0L
        _lastSeenMs.value = null
        stopService()
        settings.refresh()
    }

    /** The rider asked to try the bike now (the bike sheet's Retry now). */
    fun retryNow() = retryService()

    /** Stop the dash link and clear the app's notifications; the caller finishes the activity. */
    fun quit() {
        stopService()
        cancelNotifications()
    }

    companion object {
        private const val LAST_SEEN_EVERY_MS = 60_000L

        fun create(context: Context, scope: CoroutineScope, settings: SettingsRepository): ConnectionRepository {
            val app = context.applicationContext
            val manager = app.getSystemService(BluetoothManager::class.java)
            val btOn = MutableStateFlow(manager?.adapter?.isEnabled == true)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, i: Intent?) {
                    btOn.value = manager?.adapter?.isEnabled == true
                }
            }
            ContextCompat.registerReceiver(
                app, receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            return ConnectionRepository(
                scope, settings, LinkSources(bluetoothOn = btOn.asStateFlow()),
                stopService = { BccuConnectionService.stop(app) },
                cancelNotifications = { app.getSystemService(NotificationManager::class.java).cancelAll() },
                retryService = { BccuConnectionService.retryNow(app) },
            )
        }
    }
}
