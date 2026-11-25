package com.navigator.app.ble

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.navigator.app.BuildConfig
import com.navigator.app.R
import com.navigator.app.app.AppIntents
import com.navigator.app.audio.TurnBeeper
import com.navigator.app.data.connection.ConnectionMapping
import com.navigator.app.location.SpeedMonitor
import com.navigator.app.logging.AppLogger
import com.navigator.app.nav.DashOutput
import com.navigator.app.nav.DashPreview
import com.navigator.app.nav.DashPreviewTee
import com.navigator.app.nav.NavigationCoordinator
import com.navigator.app.nav.NavigationProvider
import com.navigator.app.nav.model.NavSessionState
import com.navigator.app.nav.providers.GoogleNavSdkController
import com.navigator.app.nav.providers.GoogleNavSdkProvider
import com.navigator.app.nav.providers.NotificationNavProvider
import com.navigator.app.notify.ConnectionNotice
import com.navigator.app.notify.ConnectionNoticeContent
import com.navigator.app.notify.NotificationActions
import com.navigator.app.notify.NotificationChannels
import com.navigator.app.notify.NotificationRequestCodes
import com.navigator.app.notify.StatusNotification
import com.navigator.app.ride.RideSession
import com.navigator.app.ride.formatElapsed
import com.navigator.app.settings.AppSettings
import com.navigator.app.ui.AppForegroundState
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Owns the BLE connection to the bike's BCCU: handshake, session crypto, and
 * all reads/writes. This is the real-app home for the protocol validated in
 * bccu-test-app's BccuBleClient - same handshake, same framing, same RCM
 * decode, carried over unchanged. Runs as a foreground service so it
 * survives while the app is backgrounded (e.g. while Maps is in front).
 */
@SuppressLint("MissingPermission") // permissions are checked before starting this service
class BccuConnectionService : LifecycleService() {

    enum class ConnectionState { DISCONNECTED, CONNECTING, AUTHENTICATED }

    companion object {
        private const val ALERT_ID = 44
        private const val NOTIFICATION_CLEAR_DELAY_MS = 5000L
        /** The dash's bottom banner shows at most 16 characters. */
        private const val NOTIFICATION_BANNER_CHARS = 16
        /** Per-character marquee step; ~3 chars/sec reads comfortably at a glance. */
        private const val MARQUEE_STEP_MS = 320L
        /** How many times a long message scrolls fully before the banner clears. */
        private const val MARQUEE_CYCLES = 2
        /** Grace period before a removed nav notification blanks the center guidance. */
        private const val GUIDANCE_CLEAR_DELAY_MS = 4000L
        /** How long the "Hi <name>!" connect greeting stays on the center display before clearing. */
        private const val GREETING_MS = 6000L
        private const val RESYNC_MIN_INTERVAL_MS = 3_000L
        private const val RESYNC_DELAY_MS = 300L
        /** Below this, TURN_ROAD payloads (up to ~130 B encrypted) exceed MTU-3. */
        private const val MIN_COMFORTABLE_MTU = 185
        /**
         * If a handshake doesn't reach AUTHENTICATED within this long, drop the
         * link and re-scan. Armed at LINK-UP (not on m1): a reused/zombie ACL
         * never re-sends m1, so an m1-armed timer can leave a silent connection
         * unguarded forever (field log 20260709_173524, 19:17:15). A bonded dash
         * finishes the whole handshake in ~3s and kicks unauthenticated links
         * itself at ~15s, so 25s means "dead or dash still booting - recycle".
         * A genuine first pairing includes the rider physically confirming the
         * "add device" prompt on the dash (~30s in field logs), so it gets a
         * much longer budget. We never clear the pairing here (see the
         * cmd=HELLO handler: the dash owns the prompt decision, not us).
         */
        private const val HANDSHAKE_TIMEOUT_KNOWN_MS = 25_000L
        private const val HANDSHAKE_TIMEOUT_FIRST_PAIR_MS = 75_000L
        /**
         * Grace between SEEING the bike advertise and actually connecting. Kept
         * short: this used to be 10 s because connecting into a half-awake dash
         * right after ignition-on was suspected of re-triggering its "add device"
         * prompt (R21). That's moot now - the prompt is driven solely by our
         * AppIdKeyArrStatus status reply, not by connection timing - so we no
         * longer pay 10 s on every reconnect. A small grace remains so we don't
         * connect into a not-yet-connectable advertise (status-255 churn); a dash
         * that's genuinely still booting is covered by the handshake watchdog,
         * which drops and rescans on a stalled auth.
         */
        private const val CONNECT_SETTLE_MS = 2_000L
        /** Cooldown before rescanning after a stalled handshake: one quickish retry, then hang back. */
        private const val RETRY_COOLDOWN_FIRST_MS = 15_000L
        private const val RETRY_COOLDOWN_LATER_MS = 45_000L
        /** How often the reconnect watchdog checks that we're linked (or retries). */
        private const val RECONNECT_WATCHDOG_MS = 20_000L
        /**
         * After auth the dash is documented to re-send AssignKeyIndex ~1 Hz as a heartbeat
         * (BCCU_BLE_PROTOCOL.md), but that is not confirmed in field logs, so silence this
         * long on an "authenticated" link is only LOGGED (once per session) for now - it
         * does not recycle the link. Promote to a recycle once a capture confirms it.
         */
        private const val LIVENESS_TIMEOUT_MS = 30_000L
        /** Rescan delays after consecutive failed connects to an absent bike (capped at 30 s). */
        private val ABSENT_BACKOFF_MS = longArrayOf(1_500L, 5_000L, 15_000L, 30_000L)
        /** Handshake stalls/drops for a KNOWN bike before suggesting a re-pair. */
        private const val REPAIR_HINT_AFTER = 4
        private const val LAST_SEEN_EVERY_MS = 60_000L
        // Stuck-connect limits (direct vs. background autoConnect) live in [ReconnectPolicy].
        const val EXTRA_DEVICE_ADDRESS = "device_address"
        /** Set by the ACL receiver: the bike's own link just came up, so it is certainly in range. */
        const val EXTRA_BIKE_PRESENT = "bike_present"

        private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
        val connectionState: StateFlow<ConnectionState> = _connectionState

        private val _linkUp = MutableStateFlow(false)

        /** The GATT link itself is up (before the handshake): what tells "waiting for the dash" from "securing". */
        val linkUpState: StateFlow<Boolean> = _linkUp

        /** True after repeated failed handshakes with a known bike: a re-pair may be needed. */
        private val _repairHint = MutableStateFlow(false)
        val repairHint: StateFlow<Boolean> = _repairHint

        /** Human-readable name of the bike we're connected/connecting to (e.g. "KTM3638"). */
        private val _deviceName = MutableStateFlow<String?>(null)
        val deviceName: StateFlow<String?> = _deviceName

        /** True while a reconnect/pairing scan is running. */
        private val _scanning = MutableStateFlow(false)
        val scanning: StateFlow<Boolean> = _scanning

        /** The bike is not there: connects to it keep failing (parked, ignition off). Cleared the moment it is seen again. */
        private val _bikeAbsent = MutableStateFlow(false)
        val bikeAbsent: StateFlow<Boolean> = _bikeAbsent

        /** A link that worked has just dropped and the service is getting it back (cleared on success or when the bike proves absent). */
        private val _reconnecting = MutableStateFlow(false)
        val reconnecting: StateFlow<Boolean> = _reconnecting

        /** When "Retry now" would act again (wall-clock ms): the end of the handshake cooldown, which a tap does not cut short (D36). Null when a tap would act. */
        private val _retryAvailableAtMs = MutableStateFlow<Long?>(null)
        val retryAvailableAtMs: StateFlow<Long?> = _retryAvailableAtMs

        /** Failed attempts since the last success (handshake stalls plus connects to an absent bike); 0 when fine. */
        private val _attempt = MutableStateFlow(0)
        val attempt: StateFlow<Int> = _attempt

        /** Short reason for the last failed attempt, for the status screen; cleared on authentication. */
        private val _lastError = MutableStateFlow<String?>(null)
        val lastError: StateFlow<String?> = _lastError

        fun start(context: Context, deviceAddress: String, bikePresent: Boolean = false) {
            if (AppSettings(context).quitUntilOpen) {
                AppLogger.log("BLE", "Start ignored: the rider quit and has not reopened the app")
                return
            }
            val intent = Intent(context, BccuConnectionService::class.java)
                .putExtra(EXTRA_DEVICE_ADDRESS, deviceAddress)
                .putExtra(EXTRA_BIKE_PRESENT, bikePresent)
            // ForegroundServiceStartNotAllowedException when a battery-optimised app is
            // started from the background (ACL broadcast); never crash the receiver.
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                AppLogger.log("BLE", "!! cannot start BccuConnectionService: ${e.javaClass.simpleName}: ${e.message}")
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BccuConnectionService::class.java))
        }

        // Simple same-process bridge so other components (notification listener,
        // controller state machine) can reach the running service instance
        // without binder/messenger plumbing. Null when the service isn't running.
        @Volatile private var runningInstance: BccuConnectionService? = null

        fun sendNotificationIfRunning(text: String, icon: BccuProtocol.NotificationIcon) {
            // Callers may be on a worker thread (notification listener); the banner state
            // machine (jobs, marquee) is main-thread territory.
            val svc = runningInstance ?: return
            svc.lifecycleScope.launch { svc.sendNotification(text, icon) }
        }

        /** Low-priority live status (overspeed readout): yields to any message currently on the banner. */
        fun sendSpeedBannerIfRunning(text: String) {
            runningInstance?.sendSpeedBanner(text)
        }

        /** Re-check location permission-dependent features (permission granted / app foregrounded). */
        fun reevaluateLocationIfRunning() {
            runningInstance?.reevaluateLocationFeatures()
        }

        /**
         * The rider's "Retry now" (bike sheet): try to reach the bike this moment instead of waiting out the
         * reconnect pacing. Starts the service when it is not running (the bike was left offline).
         */
        fun retryNow(context: Context) {
            val svc = runningInstance
            if (svc != null) {
                svc.lifecycleScope.launch { svc.retryNow() }
            } else {
                AppSettings(context).bondedDeviceAddress?.let { start(context, it) }
            }
        }

        fun sendTurnIconIfRunning(icon: BccuProtocol.TurnIcon) {
            runningInstance?.sendTurnIcon(icon)
        }

        /**
         * Turn the dash's center guidance view on/off. Exposed for the Symbol
         * Testing probe, which needs the guidance view lit to render turn icons
         * outside of an active navigation session.
         */
        fun setNavStateIfRunning(guidanceOn: Boolean, gpsIconOn: Boolean) {
            runningInstance?.sendNavigationState(guidanceOn, gpsIconOn)
        }

        /** Switch the active navigation provider (used by GoogleNavSdkController). */
        fun setNavProviderIfRunning(provider: NavigationProvider) {
            runningInstance?.setNavProvider(provider)
        }

        /** What the bike link says right now, for the app notification; null while the service is not running. */
        private val _notice = MutableStateFlow<ConnectionNotice?>(null)
        val notice: StateFlow<ConnectionNotice?> = _notice

        /** Whether the bike service is up (a hard quit waits for this to turn false). */
        val isRunning: Boolean get() = runningInstance != null

        /** Blank the dash's guidance view (a test turn, a hard quit); nothing happens when the service is not running. */
        fun clearGuidanceIfRunning() {
            // Callers may be on any thread; the guidance state machine is main-thread territory.
            val svc = runningInstance ?: return
            svc.lifecycleScope.launch { svc.clearGuidance() }
        }

        /** Updates the dash's center guidance view (distance/road/ETA/remaining distance), not the bottom notification banner. */
        fun sendGuidanceIfRunning(distanceText: String?, roadText: String?, etaText: String? = null, remainingDistanceText: String? = null) {
            runningInstance?.sendGuidance(distanceText, roadText, etaText, remainingDistanceText)
        }
    }

    // Written from GATT binder callbacks and read on the main thread (and vice
    // versa) - @Volatile so each thread sees the other's latest value.
    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var currentDeviceAddress: String? = null
    @Volatile private var tempIv: ByteArray? = null
    @Volatile private var tempSecret: ByteArray? = null
    @Volatile private var sessionKeys: List<ByteArray>? = null
    @Volatile private var activeSessionKey: ByteArray? = null
    @Volatile private var clearJob: Job? = null
    @Volatile private var marqueeJob: Job? = null
    @Volatile private var guidanceClearJob: Job? = null
    private var lastNotificationIcon: BccuProtocol.NotificationIcon = BccuProtocol.NotificationIcon.NOTIFICATION_WAYPOINT
    private var reconnectWatchdogJob: Job? = null
    @Volatile private var handshakeTimeoutJob: Job? = null
    @Volatile private var handshakeRecoveries: Int = 0
        set(value) { field = value; publishAttempt() }
    /** Pending scan-found -> connect delay (dash boot grace); active means "we will connect shortly". */
    private var settleJob: Job? = null
    private var scanCallback: ScanCallback? = null
    /** Ends a scan that hears nothing and falls back to a background connect (see [ReconnectPolicy.SCAN_WINDOW_MS]). */
    private var scanTimeoutJob: Job? = null
    /** True once the current GATT client has actually reached STATE_CONNECTED (vs. a pending connect request). */
    @Volatile private var linkUp = false
        set(value) { field = value; _linkUp.value = value }
    @Volatile private var scanning: Boolean = false
        set(value) { field = value; _scanning.value = value }
    @Volatile private var scanTargetAddress: String? = null

    /** All GATT callbacks run on this thread (serialised, off main and off binder threads). */
    private var bleThread: HandlerThread? = null
    private var bleHandler: Handler? = null
    /** Held while connect() assigns [gatt], so the first callback never sees a half-published client. */
    private val settingsStore by lazy { AppSettings(this) }
    private val gattLock = Any()
    /** Last time the dash sent us anything on the auth channel (heartbeat liveness). */
    @Volatile private var lastAuthMsgMs = 0L
    @Volatile private var livenessLogged = false
    /** Pending delayed rescan (handshake cooldown or absent-bike backoff); mutated on main only. */
    @Volatile private var rescanJob: Job? = null
    /** Consecutive failed connects to an absent bike; drives [ABSENT_BACKOFF_MS]. */
    @Volatile private var absentFailures = 0
        set(value) { field = value; publishAttempt() }

    /**
     * Drops of a link that came up but never authenticated, for a known bike (a dash that lost its keys may kick
     * such links before the handshake timer fires). They count toward the re-pair hint and the attempt number only:
     * no cooldown, no change to scan timing.
     */
    @Volatile private var preAuthDrops = 0
        set(value) { field = value; publishAttempt() }

    private fun publishAttempt() {
        _attempt.value = ConnectionMapping.attempt(handshakeRecoveries, absentFailures, preAuthDrops)
    }

    // Serialises all GATT reads/writes (Android allows one outstanding op at a
    // time); coalescing keys drop stale display writes on a degraded link. See
    // [SerialGattQueue].
    private val gattOps = SerialGattQueue(
        onOpFailed = { reason ->
            AppLogger.log("BLE", "!! GATT op $reason - released queue")
            requestGuidanceResync()
        },
    )

    /** Characteristics that make up the center guidance view. A failed write to
     *  one of them triggers a resync (the encoder believes it was delivered and
     *  its dedup would otherwise never re-send it). */
    private val guidanceUuids = setOf(
        BccuProtocol.NAVIGATION_STATE, BccuProtocol.TURN_ICON, BccuProtocol.TURN_DISTANCE,
        BccuProtocol.TURN_ROAD, BccuProtocol.ETA, BccuProtocol.REMAINING_DISTANCE,
    )
    @Volatile private var lastResyncRequestMs = 0L

    /** Re-send the full current guidance (rate-limited so a flaky link can't loop). */
    private fun requestGuidanceResync() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastResyncRequestMs < RESYNC_MIN_INTERVAL_MS) return
        lastResyncRequestMs = now
        lifecycleScope.launch {
            delay(RESYNC_DELAY_MS)
            if (hasSession()) navCoordinator?.resync()
        }
    }

    @Volatile private var connectingSinceMs: Long = 0L
    @Volatile private var pendingAutoConnect: Boolean = false

    /** Set first in [onDestroy]: a late GATT callback or posted runnable must not touch the dead service. */
    @Volatile private var destroyed = false

    /** The dash's key selection arrived on the GATT thread and its bookkeeping is queued for main: the handshake timer must not tear the link down in that gap. */
    @Volatile private var authCompleting = false

    /**
     * Bridges the navigation pipeline (provider -> encoder -> DashWrite) to this
     * service's existing send/clear methods. The provider path adds dedup /
     * throttle / state-machine / clear handling that the old direct calls lacked.
     */
    private var navCoordinator: NavigationCoordinator? = null

    private fun startNavigationCoordinator() {
        val output = object : DashOutput {
            override fun setNavState(guidanceOn: Boolean, gpsIconOn: Boolean) =
                sendNavigationState(guidanceOn, gpsIconOn)
            override fun turnIcon(icon: BccuProtocol.TurnIcon) = sendTurnIcon(icon)
            override fun guidance(distance: String?, road: String?, eta: String?, remaining: String?) =
                sendGuidance(distance, road, eta, remaining)
            override fun clearNow() = clearGuidance()
            override fun clearDebounced() = scheduleGuidanceClear()
            override fun offlineBanner(show: Boolean) = showOfflineBanner(show)
        }
        navCoordinator = NavigationCoordinator(
            scope = lifecycleScope,
            fallbackProvider = NotificationNavProvider,
            output = DashPreviewTee(output), // read-only mirror of what the dash is told; the status screen shows it
        ).also {
            it.start()
            // Service (re)started mid-trip (bike connected after in-app nav began,
            // or the service was recreated): the Nav SDK provider is only selected
            // when a trip STARTS, so pick it up here or the dash gets nothing.
            val sdk = GoogleNavSdkProvider
            if (sdk.isTripInProgress) {
                AppLogger.log("Nav", "In-app trip already in progress - driving dash from Nav SDK")
                it.setProvider(sdk)
            }
        }
    }

    /** Switch the active navigation provider (notification <-> Google Nav SDK). */
    fun setNavProvider(provider: NavigationProvider) {
        navCoordinator?.setProvider(provider)
    }

    override fun onCreate() {
        super.onCreate()
        watchLastSeen()
        try {
            _notice.value = ConnectionNotice.CONNECTING
            startForegroundWithTypes()
        } catch (e: Exception) {
            // e.g. Nearby-devices (BLUETOOTH_CONNECT) revoked, or FGS start not allowed.
            AppLogger.log("BLE", "!! cannot start foreground service: ${e.javaClass.simpleName}: ${e.message}")
            fgsStartFailed = true
            stopSelf()
            return
        }
        bleThread = HandlerThread("BccuGatt").apply { start() }
        bleHandler = Handler(bleThread!!.looper)
        runningInstance = this
        bluetoothOn.value = (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.isEnabled != false
        watchNotice()
        startReconnectWatchdog()
        registerAdapterStateReceiver()
        startNavigationCoordinator()
        speedMonitor = SpeedMonitor(this).also { it.start() }
        // Keep the live GPS speed flowing to the beeper's stationary duck
        // continuously, not just when a guidance update happens to arrive.
        lifecycleScope.launch {
            SpeedMonitor.speedKmh.collect { kmh ->
                TurnBeeper.gpsSpeedKmh = kmh
            }
        }
    }

    /**
     * The one place a GATT link is dropped and its per-link state reset (this used to be
     * copy-pasted at five call sites, each forgetting something different).
     *
     * @param state connection state to publish, or null to leave it (watchdog recycle: the
     *   scan that follows publishes CONNECTING itself)
     * @param cancelHandshakeTimer false when called from the handshake timer itself
     * @param disconnectFirst false when the peer already disconnected (only close the client)
     */
    private fun teardownLink(
        reason: String,
        state: ConnectionState? = ConnectionState.DISCONNECTED,
        cancelHandshakeTimer: Boolean = true,
        resetCrypto: Boolean = true,
        disconnectFirst: Boolean = true,
    ) {
        AppLogger.log("BLE", "teardownLink: $reason")
        DashPreview.reset() // the link is gone: nothing is on the dash any more
        val g = synchronized(gattLock) { gatt.also { gatt = null } }
        try {
            if (disconnectFirst) g?.disconnect()
            g?.close()
        } catch (e: Exception) { /* already gone */ }
        linkUp = false
        // Drop any op stranded in flight on the dead link - otherwise the queue stays busy
        // and every op on the next connection stalls behind it.
        gattOps.clear()
        if (cancelHandshakeTimer) { handshakeTimeoutJob?.cancel(); handshakeTimeoutJob = null; _retryAvailableAtMs.value = null }
        if (resetCrypto) {
            tempIv = null; tempSecret = null; sessionKeys = null; activeSessionKey = null
        }
        if (state != null) _connectionState.value = state
    }

    /** Delay before the next rescan after [failures] consecutive failed connects to an absent bike. */
    private fun absentBackoffMs(failures: Int): Long =
        ABSENT_BACKOFF_MS[(failures - 1).coerceIn(0, ABSENT_BACKOFF_MS.size - 1)]

    /** Cooldown after a stalled/dropped handshake: one quickish retry, then hang back. */
    private fun handshakeCooldownMs(recoveries: Int): Long =
        if (recoveries <= 1) RETRY_COOLDOWN_FIRST_MS else RETRY_COOLDOWN_LATER_MS

    /** Rescan for the bonded bike after [delayMs] unless something else connects/scans first. */
    private fun scheduleRescan(delayMs: Long) {
        lifecycleScope.launch {
            rescanJob?.cancel()
            rescanJob = launch {
                delay(delayMs)
                val addr = AppSettings(this@BccuConnectionService).bondedDeviceAddress
                if (addr != null && gatt == null) startBleScan(addr)
            }
        }
    }

    /**
     * A sign the bike is here (its classic link came up, Bluetooth switched on, the app came
     * to the foreground): forget the absent-bike backoff and, if a long wait is pending,
     * rescan now. A handshake cooldown is left alone: it runs in the handshake timer, not in [rescanJob].
     */
    private fun resetAbsentBackoff(reason: String, rescan: Boolean = true) {
        absentFailures = 0
        // The caller connects directly: a scan started here would be cut short again at once.
        if (!rescan) return
        lifecycleScope.launch {
            if (rescanJob?.isActive == true && gatt == null) {
                AppLogger.log("BLE", "Backoff reset ($reason) - rescanning now")
                rescanJob?.cancel(); rescanJob = null
                AppSettings(this@BccuConnectionService).bondedDeviceAddress?.let { startBleScan(it) }
            }
        }
    }

    private var lastRetryNowMs = 0L

    /** "Retry now" (main thread): see [ReconnectPolicy.retryNow]. The protocol and the link handling are as they were. */
    private fun retryNow() {
        val address = AppSettings(this).bondedDeviceAddress ?: return
        val now = SystemClock.elapsedRealtime()
        val action = ReconnectPolicy.retryNow(
            authenticated = _connectionState.value == ConnectionState.AUTHENTICATED,
            hasGatt = gatt != null, linkUp = linkUp, backgroundConnect = pendingAutoConnect,
            scanning = scanning, settling = settleJob?.isActive == true,
            handshaking = handshakeTimeoutJob?.isActive == true,
            sinceLastRetryMs = now - lastRetryNowMs,
        )
        AppLogger.log("BLE", "Retry now (rider): $action")
        if (action == ReconnectPolicy.RetryNow.Ignore) return
        // Nothing to scan with: leave any pending wait as it is rather than cancel it for nothing.
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) return
        lastRetryNowMs = now
        absentFailures = 0
        rescanJob?.cancel(); rescanJob = null
        if (action == ReconnectPolicy.RetryNow.RecycleAndScan) teardownLink("retry now", state = null)
        startBleScan(address)
    }

    private fun noteHandshakeFailure(addr: String?) {
        handshakeRecoveries++
        suggestRepairIfNeeded(addr)
    }

    private fun suggestRepairIfNeeded(addr: String?) {
        if (addr != null && handshakeRecoveries + preAuthDrops >= REPAIR_HINT_AFTER &&
            AppSettings(this).hasPairedBefore(addr) && !_repairHint.value
        ) {
            _repairHint.value = true
        }
    }

    private var adapterStateReceiver: BroadcastReceiver? = null
    private var speedMonitor: SpeedMonitor? = null
        private set

    /**
     * Explicit FGS types (API 34 enforces them): connectedDevice always;
     * location only when the permission is actually granted AND we're eligible
     * to use it (app visible, or "allow all the time" granted) - defaulting to
     * the manifest types would throw SecurityException on a fresh install where
     * the boot/ACL receiver starts us before the rider grants location.
     * Safe to call again later to UPGRADE the type once permissions arrive.
     */
    private fun startForegroundWithTypes() {
        var fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        val fine = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val background = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (fine && (background || AppForegroundState.isForeground)) {
            fgsType = fgsType or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }
        ServiceCompat.startForeground(
            this, StatusNotification.ID, StatusNotification.build(this), fgsType
        )
    }

    /**
     * Re-check location-dependent features: called when the app comes to the
     * foreground or a permission was just granted. Location permission is often
     * granted AFTER this long-lived service started (the permission dialog is
     * async), which used to leave the speed monitor dead and the FGS without
     * the location type until the next reboot.
     */
    private fun reevaluateLocationFeatures() {
        runCatching { startForegroundWithTypes() }
        speedMonitor?.start()
        // App came to the foreground: the rider is probably about to ride - skip any long wait.
        resetAbsentBackoff("app foreground")
    }

    private val bluetoothOn = MutableStateFlow(true)

    /**
     * Android does NOT reliably deliver onConnectionStateChange when the LOCAL
     * adapter is switched off (unlike an out-of-range drop) - field log
     * opendash_log_20260709_004422 showed the app stuck on "Connected" forever
     * after the rider toggled Bluetooth off. Observe the adapter state directly:
     * on OFF force-disconnect and show it; on ON resume the scan-to-reconnect
     * path immediately (the manifest AutoConnectReceiver also covers the case
     * where this service isn't running).
     */
    private fun registerAdapterStateReceiver() {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                when (i?.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                    BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> {
                        AppLogger.log("BLE", "Bluetooth adapter turned OFF - forcing disconnect state")
                        stopBleScan()
                        settleJob?.cancel(); settleJob = null
                        rescanJob?.cancel(); rescanJob = null
                        bluetoothOn.value = false
                        teardownLink("adapter off")
                    }
                    BluetoothAdapter.STATE_ON -> {
                        AppLogger.log("BLE", "Bluetooth adapter turned ON - resuming reconnect scan")
                        bluetoothOn.value = true
                        absentFailures = 0
                        rescanJob?.cancel(); rescanJob = null
                        val addr = AppSettings(this@BccuConnectionService).bondedDeviceAddress
                        if (addr != null && gatt == null) startBleScan(addr)
                    }
                }
            }
        }
        registerReceiver(r, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        adapterStateReceiver = r
    }

    /**
     * Belt-and-suspenders auto-connect. `connectGatt(autoConnect=true)` usually
     * relinks on its own, but it can silently never fire (a known Android BLE
     * quirk), and the initial connect is deferred when Bluetooth is off. This
     * loop re-issues a connect when we're not linked and no GATT client is in
     * flight, and force-recycles a connection that's been stuck "connecting"
     * far too long.
     */
    private fun startReconnectWatchdog() {
        reconnectWatchdogJob?.cancel()
        reconnectWatchdogJob = lifecycleScope.launch {
            while (true) {
                delay(RECONNECT_WATCHDOG_MS)
                try {
                    val address = AppSettings(this@BccuConnectionService).bondedDeviceAddress ?: continue
                    if (_connectionState.value == ConnectionState.AUTHENTICATED) {
                        // The dash heartbeats ~1 Hz after auth; long silence = a dead link that
                        // never produced a disconnect callback.
                        val silentMs = SystemClock.elapsedRealtime() - lastAuthMsgMs
                        if (lastAuthMsgMs != 0L && silentMs > LIVENESS_TIMEOUT_MS && !livenessLogged) {
                            livenessLogged = true
                            AppLogger.log("BLE", "Watchdog: no dash message for ${silentMs / 1000}s while authenticated (log only, link kept)")
                        }
                        continue
                    }
                    val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
                    if (adapter == null || !adapter.isEnabled) continue
                    val stuckLimit = ReconnectPolicy.stuckLimitMs(pendingAutoConnect)
                    if (gatt == null) {
                        // Not linked and nothing in flight: make sure we're scanning
                        // for the bike so we grab it the moment it advertises. A
                        // pending settle delay or retry cooldown counts as in
                        // flight - restarting the scan would cut the pacing short.
                        if (!scanning && settleJob?.isActive != true && handshakeTimeoutJob?.isActive != true &&
                            rescanJob?.isActive != true
                        ) {
                            AppLogger.log("BLE", "Watchdog: not connected - (re)starting scan")
                            startBleScan(address)
                        }
                    } else if (_connectionState.value == ConnectionState.CONNECTING &&
                        SystemClock.elapsedRealtime() - connectingSinceMs > stuckLimit
                    ) {
                        if (tempIv != null && !AppSettings(this@BccuConnectionService).hasPairedBefore(address)) {
                            // A FIRST-pair handshake is genuinely in progress (m1
                            // arrived) - the rider may be confirming the "add
                            // device" prompt on the dash, which takes >45s. The
                            // long first-pair handshake timeout owns that recovery;
                            // recycling here would tear the link mid-confirm. A
                            // KNOWN bike never legitimately waits this long (its
                            // 25s handshake timer fires first), so it falls through.
                            continue
                        }
                        // A direct connect that's been hung too long is dead - recycle
                        // the GATT and fall back to scanning (which reconnects when the
                        // dash is actually reachable, avoiding the status-255 churn).
                        // Clear the op queue and any leftover crypto too: a lost
                        // in-flight op would otherwise leave the queue stuck busy
                        // and stall every op on the NEXT connection forever.
                        AppLogger.log("BLE", "Watchdog: connect stuck >${stuckLimit}ms (background=$pendingAutoConnect) - recycling + scanning")
                        teardownLink("stuck connect", state = null)
                        startBleScan(address)
                    }
                } catch (e: Exception) {
                    AppLogger.log("BLE", "!! Watchdog iteration threw: $e")
                }
            }
        }
    }

    /**
     * Actively scan for the bonded bike and connect the instant it advertises.
     * More reliable than connectGatt(autoConnect=true), which was observed to
     * silently never relink on real phones. Matches the target by MAC (the same
     * device the phone is already bonded/paired with for media), so it also
     * covers "the bike is already saved on my phone". Low-power scan; stops as
     * soon as the device is found or we authenticate.
     */
    @SuppressLint("MissingPermission")
    private fun startBleScan(address: String) {
        if (scanning) return
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val scanner = adapter?.bluetoothLeScanner
        if (adapter == null || !adapter.isEnabled || scanner == null) {
            AppLogger.log("BLE", "Scan: adapter/scanner unavailable, deferring")
            return
        }
        val target = address.uppercase()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val dev = result.device ?: return
                if (dev.address?.uppercase() != target) return
                _bikeAbsent.value = false
                AppLogger.log("BLE", "Scan: found bike ${dev.address} (rssi=${result.rssi}) - settling ${CONNECT_SETTLE_MS / 1000}s before connect (dash boot grace)")
                stopBleScan()
                if (gatt != null || settleJob?.isActive == true) return
                settleJob = lifecycleScope.launch {
                    delay(CONNECT_SETTLE_MS)
                    if (gatt == null) connect(target, autoConnect = false)
                }
            }
            override fun onScanFailed(errorCode: Int) {
                AppLogger.log("BLE", "!! Scan failed: $errorCode")
                scanning = false
                scanTimeoutJob?.cancel(); scanTimeoutJob = null
                if (gatt == null && settleJob?.isActive != true) {
                    // Can't hear the bike at all - connect to the saved address instead.
                    connect(target, autoConnect = true)
                }
            }
        }
        val filters = listOf(
            ScanFilter.Builder().setDeviceAddress(target).build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()
        try {
            scanner.startScan(filters, settings, cb)
            scanCallback = cb
            scanTargetAddress = target
            scanning = true
            _connectionState.value = ConnectionState.CONNECTING
            AppLogger.log("BLE", "Scanning for bike $address to reconnect (window ${ReconnectPolicy.SCAN_WINDOW_MS / 1000}s)")
            scanTimeoutJob?.cancel()
            scanTimeoutJob = lifecycleScope.launch {
                delay(ReconnectPolicy.SCAN_WINDOW_MS)
                if (!scanning || gatt != null) return@launch
                // The dash may be linked to the phone over classic Bluetooth and
                // not advertising over BLE at all; waiting longer would hang
                // forever. A phone-bonded dash is probably classic-linked: a
                // direct connect works against it (re-pairing proved that), and a
                // failed attempt falls back to scanning via the watchdog. An
                // unbonded one gets a background connect that waits for it.
                val bonded = runCatching {
                    adapter.getRemoteDevice(target).bondState == BluetoothDevice.BOND_BONDED
                }.getOrDefault(false)
                AppLogger.log("BLE", "Scan heard nothing in ${ReconnectPolicy.SCAN_WINDOW_MS / 1000}s - falling back to a ${if (bonded) "direct" else "background"} connect")
                scanTimeoutJob = null
                stopBleScan()
                connect(target, autoConnect = !bonded)
            }
        } catch (e: Exception) {
            AppLogger.log("BLE", "!! startScan threw: $e")
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScan() {
        scanTimeoutJob?.cancel(); scanTimeoutJob = null
        if (!scanning) return
        scanning = false
        scanTargetAddress = null
        try {
            val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            scanCallback?.let { adapter?.bluetoothLeScanner?.stopScan(it) }
        } catch (e: Exception) { /* ignore */ }
        scanCallback = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (fgsStartFailed) {
            stopSelf()
            return START_NOT_STICKY
        }
        val address = intent?.getStringExtra(EXTRA_DEVICE_ADDRESS) ?: AppSettings(this).bondedDeviceAddress
        if (address != null) {
            val present = intent?.getBooleanExtra(EXTRA_BIKE_PRESENT, false) == true
            // A stalled/dropped handshake is cooling down: neither the bike's classic link coming
            // up (R21) nor ANY other start (app open, boot, sticky restart) may cut that pacing
            // short and direct-connect into the same stall. Only for the same bike.
            // (The cooldown runs inside the handshake timer job, which is what makes it active here.)
            val inHandshakeCooldown = gatt == null &&
                address.equals(currentDeviceAddress, ignoreCase = true) &&
                handshakeTimeoutJob?.isActive == true
            if (inHandshakeCooldown) {
                AppLogger.log("BLE", "Start (present=$present) during a handshake retry cooldown - leaving it to the cooldown")
                return START_STICKY
            }
            if (present) { _bikeAbsent.value = false; resetAbsentBackoff("bike link up", rescan = false) }
            if (ReconnectPolicy.shouldPreemptBackgroundConnect(present, pendingAutoConnect, gatt != null, linkUp)) {
                // A background connect request is parked but nothing linked up, yet
                // the bike's own link is here: recycle it into a direct connect.
                AppLogger.log("BLE", "Bike link is up but background connect is still pending - connecting directly")
                teardownLink("preempt background connect", state = null, resetCrypto = false)
            }
            if (ReconnectPolicy.shouldPreemptScan(present, scanning, gatt != null)) {
                // The bike's own link is up, so it is here even if it isn't
                // advertising over BLE - a scan would wait for nothing.
                AppLogger.log("BLE", "Bike link is up - cutting the scan short and connecting directly")
                stopBleScan()
                settleJob?.cancel(); settleJob = null
            }
            connect(address)
        }
        return START_STICKY
    }

    /**
     * @param autoConnect false for the initial (fast, foreground) attempt;
     * true for reconnects, so Android holds a background connection request and
     * links up on its own the moment the bike is back in range - far more
     * reliable and battery-friendly than us polling connectGatt() on a timer.
     */
    private fun connect(address: String, autoConnect: Boolean = false) {
        if (gatt != null) return
        // Already scanning for this same bike (e.g. the in-service adapter-ON
        // handler races AutoConnectReceiver's onStartCommand): let the scan win.
        // A blind direct connect while the bike is off just churns status-255
        // failures - the scan-first design exists precisely to avoid that.
        if (scanning && address.equals(scanTargetAddress, ignoreCase = true)) {
            AppLogger.log("BLE", "connect($address) skipped - scan already hunting for it")
            return
        }
        if (scanning) stopBleScan()
        AppLogger.log("BLE", "Connecting to $address (autoConnect=$autoConnect)")
        currentDeviceAddress = address
        _connectionState.value = ConnectionState.CONNECTING
        connectingSinceMs = SystemClock.elapsedRealtime()
        pendingAutoConnect = autoConnect
        linkUp = false
        // getSystemService(BluetoothManager) is the non-deprecated path (getDefaultAdapter()
        // is deprecated since API 31); on any BLE-capable minSdk-26 device this is non-null.
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            // Bluetooth is off; stay CONNECTING and let the AutoConnectReceiver's
            // adapter-on broadcast (or the next onStartCommand) retry.
            AppLogger.log("BLE", "Bluetooth adapter unavailable/off, deferring connect")
            bluetoothOn.value = false
            return
        }
        bluetoothOn.value = true
        try {
            val device = adapter.getRemoteDevice(address)
            _deviceName.value = settingsStore.bondedDeviceName ?: device.name
            synchronized(gattLock) {
                // Deprecated in API 37, but its replacement (connectGatt(BluetoothGattConnectionSettings,
                // Executor, callback)) is API 37-only and minSdk is 36. Revisit when minSdk reaches 37.
                @Suppress("DEPRECATION")
                gatt = device.connectGatt(
                    this, autoConnect, callback, BluetoothDevice.TRANSPORT_LE,
                    BluetoothDevice.PHY_LE_1M_MASK, bleHandler,
                )
            }
        } catch (e: Exception) {
            // SecurityException (Nearby devices revoked) / IllegalArgumentException (bad address).
            AppLogger.log("BLE", "!! connect failed: ${e.javaClass.simpleName}: ${e.message}")
            gatt = null
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    /** Set when onCreate could not enter the foreground; the service shuts itself down. */
    private var fgsStartFailed = false

    override fun onDestroy() {
        destroyed = true
        AppLogger.log("BLE", "Service destroyed")
        runCatching { getSystemService(NotificationManager::class.java).cancel(ALERT_ID) }
        adapterStateReceiver?.let { runCatching { unregisterReceiver(it) } }
        adapterStateReceiver = null
        speedMonitor?.stop(); speedMonitor = null
        navCoordinator?.stop(); navCoordinator = null
        stopBleScan()
        settleJob?.cancel(); settleJob = null
        reconnectWatchdogJob?.cancel(); reconnectWatchdogJob = null
        rescanJob?.cancel(); rescanJob = null
        handshakeTimeoutJob?.cancel(); handshakeTimeoutJob = null
        // Under the lock the callbacks use: a connect() still publishing its client can't slip one in after this.
        val g = synchronized(gattLock) { gatt.also { gatt = null } }
        runCatching { g?.disconnect(); g?.close() }
        bleThread?.quitSafely(); bleThread = null; bleHandler = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _linkUp.value = false
        _scanning.value = false
        _attempt.value = 0
        _lastError.value = null
        _repairHint.value = false
        _bikeAbsent.value = false
        _reconnecting.value = false
        _retryAvailableAtMs.value = null
        _deviceName.value = null
        _notice.value = null
        DashPreview.reset() // nothing on the dash any more, and no banner text kept
        if (runningInstance === this) runningInstance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    // --- Foreground notification plumbing ---

    private fun updateForegroundNotification(status: ConnectionNotice) {
        // GATT callbacks run on the BLE thread; the notice, the heads-up flag and the notification itself are main-thread state.
        if (Looper.myLooper() !== Looper.getMainLooper()) {
            lifecycleScope.launch { updateForegroundNotification(status) }
            return
        }
        if (destroyed) return
        _notice.value = status
        StatusNotification.refresh(this)
        val manager = getSystemService(NotificationManager::class.java)
        // A real problem gets one heads-up on its own channel; it goes again when the problem goes.
        if (status == ConnectionNotice.CANT_CONNECT) {
            if (!alertShown) {
                alertShown = true
                NotificationChannels.ensure(this, NotificationChannels.CONNECTION_ALERT)
                val content = ConnectionNoticeContent.of(status, resources, settingsStore.bondedDeviceName)
                manager.notify(
                    ALERT_ID,
                    NotificationCompat.Builder(this, NotificationChannels.CONNECTION_ALERT)
                        .setContentTitle(content.title).setContentText(content.text)
                        .setSmallIcon(R.drawable.ic_notification).setAutoCancel(true)
                        .setContentIntent(NotificationActions.open(this, NotificationRequestCodes.ALERT_OPEN, AppIntents.REQUEST_REPAIR))
                        .addAction(0, getString(R.string.conn_action_repair), NotificationActions.open(this, NotificationRequestCodes.ALERT_REPAIR, AppIntents.REQUEST_REPAIR))
                        .addAction(0, getString(R.string.conn_action_quit), NotificationActions.open(this, NotificationRequestCodes.ALERT_QUIT, AppIntents.REQUEST_QUIT))
                        .build(),
                )
            }
        } else if (alertShown && status == ConnectionNotice.CONNECTED) {
            // Only a working link (or Re-pair, which stops the service) takes the heads-up away: while the problem
            // stands it stays, whatever the link is doing in between.
            alertShown = false
            manager.cancel(ALERT_ID)
        }
    }

    private var alertShown = false

    /** The notification says what the app says: both come from [ConnectionMapping] over the same facts. */
    private var firstNotice = true

    private fun watchNotice() {
        val facts = combine(_connectionState, _linkUp, _scanning, _attempt, _repairHint) { st, up, scan, attempt, hint -> arrayOf<Any?>(st, up, scan, attempt, hint) }
        val more = combine(_lastError, _bikeAbsent, _reconnecting, bluetoothOn) { err, absent, re, bt -> arrayOf<Any?>(err, absent, re, bt) }
        lifecycleScope.launch {
            combine(facts, more) { a, b ->
                val addr = settingsStore.bondedDeviceAddress
                ConnectionMapping.notice(
                    ConnectionMapping.detail(
                        state = a[0] as ConnectionState, bluetoothOn = b[3] as Boolean, scanning = a[2] as Boolean, attempt = a[3] as Int,
                        repairHint = a[4] as Boolean, paired = addr?.let { settingsStore.hasPairedBefore(it) } ?: false,
                        lastError = b[0] as String?, linkUp = a[1] as Boolean, bikeAbsent = b[1] as Boolean, reconnecting = b[2] as Boolean,
                    ),
                )
            }.distinctUntilChanged().collect {
                // The service starts as "connecting" (its foreground notice): not "not connected" for the moment before connect() runs.
                val settling = firstNotice && it == ConnectionNotice.DISCONNECTED
                firstNotice = false
                if (!settling) updateForegroundNotification(it)
            }
        }
    }

    /** The bike is linked now: remember it for "Last seen" (not after Re-pair, which leaves no bike to stamp). */
    private fun stampLastSeen() {
        if (settingsStore.bondedDeviceAddress == null) return
        settingsStore.bondedLastSeenMs = System.currentTimeMillis()
    }

    /** While linked the time keeps moving, so a killed app or process is not far off. */
    private fun watchLastSeen() {
        lifecycleScope.launch {
            while (true) {
                delay(LAST_SEEN_EVERY_MS)
                if (_connectionState.value == ConnectionState.AUTHENTICATED) stampLastSeen()
            }
        }
    }

    /**
     * Every override here runs try/catch-wrapped. BLE stack callbacks are a
     * classic place for a rare/edge-case exception (stale GATT reference,
     * unexpected characteristic state after a fast disconnect/reconnect) to
     * otherwise kill the whole app process - we'd rather log it and keep
     * running than crash the foreground service mid-ride.
     */
    private val callback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (destroyed) return
            try {
                AppLogger.log("BLE", "onConnectionStateChange status=$status newState=$newState")
                // A late callback from a client we already recycled (adapter-off
                // teardown, stuck-connect recycle, handshake recovery) must not
                // clobber the CURRENT connection's state - just release it.
                // (gattLock: connect() may still be publishing the client it just created.)
                val stale = synchronized(gattLock) { g !== gatt }
                if (stale) {
                    AppLogger.log("BLE", "Ignoring state change from a stale GATT client")
                    try { g.close() } catch (e: Exception) { /* already gone */ }
                    return
                }
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    _bikeAbsent.value = false
                    linkUp = true
                    absentFailures = 0
                    connectingSinceMs = SystemClock.elapsedRealtime()
                    // Arm the handshake watchdog NOW, not when m1 arrives: a link
                    // where the BCCU never restarts auth (reused ACL) would
                    // otherwise sit silent with no timer guarding it at all.
                    startHandshakeTimeout()
                    if (!g.discoverServices()) AppLogger.log("BLE", "!! discoverServices() was not accepted")
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    // An AUTHENTICATED session ending (ignition off, out of
                    // range) closes this boot cycle - the next one deserves a
                    // fresh retry budget, not leftovers from the last stall.
                    val wasAuthenticated = _connectionState.value == ConnectionState.AUTHENTICATED
                    // The drop is the last moment it was there.
                    if (wasAuthenticated) stampLastSeen()
                    // Whether the link ever came up decides if this was a failed CONNECT (bike
                    // absent) or a drop of a real link.
                    val linkWasUp = linkUp
                    if (wasAuthenticated) { handshakeRecoveries = 0; preAuthDrops = 0 }
                    // Release the old GATT client fully before requesting a new
                    // one - reusing a disconnected BluetoothGatt is a known source
                    // of silent reconnect failures (status 133).
                    teardownLink("peer disconnected (status=$status)", disconnectFirst = false)
                    // Reconnect by actively SCANNING for the bike and connecting the
                    // moment it advertises again. Field logs showed connectGatt(
                    // autoConnect=true) silently never relinks on some phones, and a
                    // blind direct connect to the address fails with status 255 while
                    // the bike is off - even though classic/media BT reconnects fine.
                    // Scanning is the reliable signal that the dash is actually back.
                    when {
                        wasAuthenticated -> {
                            _reconnecting.value = true
                            absentFailures = 0
                            scheduleRescan(1_500L)
                        }
                        linkWasUp -> {
                            val known = currentDeviceAddress?.let { settingsStore.hasPairedBefore(it) } == true
                            // A first pairing that drops is still pairing: it must not read as a reconnect.
                            if (known) _reconnecting.value = true
                            if (known) {
                                preAuthDrops++
                                suggestRepairIfNeeded(currentDeviceAddress)
                            }
                            // The dash dropped a link before auth finished (it kicks
                            // unauthenticated links at ~15 s). Keep the historical quick
                            // rescan: a dash that is still waking up is covered by the
                            // handshake watchdog, and slowing this would delay a normal
                            // ignition-on connect.
                            scheduleRescan(1_500L)
                        }
                        else -> {
                            // Connect attempt failed outright (bike off / out of range): back off.
                            absentFailures++
                            if (absentFailures >= ConnectionMapping.BIKE_ABSENT_AFTER_FAILURES) {
                                _bikeAbsent.value = true
                                _reconnecting.value = false
                                preAuthDrops = 0 // a bike that is simply off is not a lost key
                            }
                            _lastError.value = "Bike not found"
                            val wait = absentBackoffMs(absentFailures)
                            AppLogger.log("BLE", "Connect failed - bike absent? rescanning in ${wait / 1000}s (failure $absentFailures)")
                            scheduleRescan(wait)
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.log("BLE", "!! onConnectionStateChange threw: $e")
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (destroyed) return
            try {
                AppLogger.log("BLE", "onServicesDiscovered status=$status, ${g.services.size} services")
                if (status != BluetoothGatt.GATT_SUCCESS) AppLogger.log("BLE", "!! service discovery status=$status")
                // Diagnostic only: full per-connect GATT dump is noisy and the log
                // is mirrored to public storage, so keep it to debug builds.
                if (BuildConfig.DEBUG) dumpGattTable(g)
                val mainService = g.getService(BccuProtocol.MAIN_SERVICE)
                if (mainService == null) {
                    AppLogger.log("BLE", "!! MAIN_SERVICE not found on this device")
                    return
                }
                if (!g.requestMtu(517)) AppLogger.log("BLE", "!! requestMtu() was not accepted")
            } catch (e: Exception) {
                AppLogger.log("BLE", "!! onServicesDiscovered threw: $e")
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            if (destroyed) return
            try {
                AppLogger.log("BLE", "onMtuChanged mtu=$mtu status=$status")
                if (status != BluetoothGatt.GATT_SUCCESS || mtu < MIN_COMFORTABLE_MTU) {
                    // Long road names would need prepare/execute (long) writes,
                    // which the BCCU may not support - surface it in the logs.
                    AppLogger.log("BLE", "!! MTU negotiation weak (mtu=$mtu status=$status) - long labels may fail to write")
                }
                if (g.getService(BccuProtocol.MAIN_SERVICE) == null) {
                    // MTU event raced ahead of service discovery (seen on a reused
                    // ACL, field log 19:17:15). onServicesDiscovered's requestMtu
                    // lands us here again once the services actually exist.
                    AppLogger.log("BLE", "MTU changed before services discovered - deferring CCCD setup")
                    return
                }
                g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                enableIndication(BccuProtocol.AUTH_REQUEST)
            } catch (e: Exception) {
                AppLogger.log("BLE", "!! onMtuChanged threw: $e")
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            if (destroyed) return
            try {
                val statusName = if (status == BluetoothGatt.GATT_SUCCESS) "SUCCESS" else "FAILED($status)"
                AppLogger.log("BLE", "onDescriptorWrite ${d.characteristic?.uuid} -> $statusName")
                // A late completion from a recycled client must not release the
                // CURRENT connection's in-flight op (two outstanding ops -> BUSY).
                if (g !== gatt) return
                if (!gattOps.finish(d.characteristic?.uuid)) {
                    AppLogger.log("BLE", "Ignored late descriptor-write completion for ${d.characteristic?.uuid}")
                }
            } catch (e: Exception) {
                AppLogger.log("BLE", "!! onDescriptorWrite threw: $e")
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (destroyed) return
            try {
                val statusName = if (status == BluetoothGatt.GATT_SUCCESS) "SUCCESS" else "FAILED($status)"
                AppLogger.log("BLE", "onCharacteristicWrite ${c.uuid} -> $statusName")
                if (g !== gatt) return
                if (!gattOps.finish(c.uuid)) {
                    AppLogger.log("BLE", "Ignored late write completion for ${c.uuid}")
                }
                if (status != BluetoothGatt.GATT_SUCCESS && c.uuid in guidanceUuids) requestGuidanceResync()
            } catch (e: Exception) {
                AppLogger.log("BLE", "!! onCharacteristicWrite threw: $e")
            }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            if (destroyed) return
            try {
                handleIncoming(c.uuid, value)
            } catch (e: Exception) {
                AppLogger.log("BLE", "!! onCharacteristicChanged threw: $e")
            }
        }

        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            if (destroyed) return
            try {
                logProbeValue(readLabel(c, status), value)
            } catch (e: Exception) {
                AppLogger.log("Probe", "!! onCharacteristicRead threw: $e")
            } finally {
                if (g === gatt) gattOps.finish(c.uuid)
            }
        }
    }

    private fun handleIncoming(uuid: UUID, value: ByteArray) {
        when (uuid) {
            BccuProtocol.AUTH_REQUEST -> handleAuthMessage(value)
            BccuProtocol.BASE_VIN -> {
                // VIN pushed on 0002 after we wrote the GET_VIN_REQUEST. Decrypt
                // on the data plane (framed) and log both raw and decoded.
                logProbeValue("NOTIFY VIN 0002", value)
                val key = activeSessionKey; val iv = tempIv
                if (key != null && iv != null) {
                    runCatching { BccuCrypto.decryptData(value, key, iv) }.onSuccess {
                        AppLogger.log("Probe", "  VIN decoded: \"${printableAscii(it)}\"")
                    }
                }
            }
            else -> {
                // Undocumented services 0200/0300 - log whatever they push (raw
                // plus decrypt attempts) so we can reverse-engineer them.
                val s = uuid.toString()
                if (s.startsWith("71ced1ac-020") || s.startsWith("71ced1ac-030")) {
                    logProbeValue("NOTIFY $uuid", value)
                }
            }
        }
    }

    private fun readLabel(c: BluetoothGattCharacteristic, status: Int): String {
        val ok = if (status == BluetoothGatt.GATT_SUCCESS) "" else " (status=$status)"
        return "READ ${c.service?.uuid}/${c.uuid}$ok"
    }

    private fun printableAscii(data: ByteArray): String =
        data.map { b -> val c = b.toInt() and 0xFF; if (c in 32..126) c.toChar() else '.' }.joinToString("")

    /**
     * Log a probed characteristic value as hex plus its printable-ASCII
     * rendering, followed by session-crypto decrypt attempts. AES-CBC/NoPadding
     * "succeeds" on any block-aligned input, so both renderings are candidates,
     * not confirmations - whichever line reads as sane text/values in the log
     * is the real framing. Attempted with the active session key (data plane)
     * and, failing that, the handshake temp secret, in both framed and raw modes.
     */
    private fun logProbeValue(label: String, data: ByteArray) {
        AppLogger.log("Probe", "$label (${data.size}B): ${BccuCrypto.hex(data)}  \"${printableAscii(data)}\"")
        val iv = tempIv ?: return
        if (data.isEmpty() || data.size % 16 != 0) return // not AES-CBC ciphertext
        val keys = listOfNotNull(
            activeSessionKey?.let { "session" to it },
            tempSecret?.let { "tempSecret" to it },
        )
        for ((name, key) in keys) {
            runCatching { BccuCrypto.decryptData(data, key, iv) }.onSuccess {
                AppLogger.log("Probe", "  decrypt[$name,framed] (${it.size}B): ${BccuCrypto.hex(it)}  \"${printableAscii(it)}\"")
            }
            runCatching { BccuCrypto.decryptControl(data, key, iv) }.onSuccess {
                AppLogger.log("Probe", "  decrypt[$name,raw]   (${it.size}B): ${BccuCrypto.hex(it)}  \"${printableAscii(it)}\"")
            }
        }
    }

    private fun handleAuthMessage(value: ByteArray) {
        lastAuthMsgMs = SystemClock.elapsedRealtime()
        val iv = tempIv
        val secret = tempSecret

        if (iv == null || secret == null) {
            if (value.size < 16) return
            AppLogger.log("Auth", "Received m1 (bike nonce)")
            val m1 = value.copyOfRange(0, 16)
            val m2 = BccuCrypto.randomNonce()
            val (newIv, newSecret) = BccuCrypto.computeTempIvAndSecret(m1, m2)
            tempIv = newIv
            tempSecret = newSecret
            writeCharacteristic(BccuProtocol.AUTH_REPLY, m2)
            return
        }

        val decrypted = try {
            BccuCrypto.decryptControl(value, secret, iv)
        } catch (e: Exception) {
            AppLogger.log("Auth", "!! Failed to decrypt control message: ${e.message}")
            return
        }
        if (decrypted.size < 3) return
        val cmd = decrypted[2].toInt() and 0xFF

        when {
            cmd == BccuProtocol.CMD_HELLO -> {
                // This is the dash's AppIdKeyArrStatus request: "do you already
                // hold this bike's key array?" The dash decides whether to show
                // its physical "add device" prompt ENTIRELY from our status reply
                // (confirmed from the official SDK's BCcuAuth.handleAuthMessage):
                //   - reply VALID_KEY_ARRAY (1) when we have the persisted array
                //     -> the dash trusts us, skips the prompt AND the re-derive,
                //     and resumes by AssignKeyIndex (cmd 16..31) into that array.
                //   - reply INITIAL_CONNECTION (0) when we don't -> the dash
                //     prompts the rider and issues ComputeKeyArr to build a fresh
                //     array (genuine first pairing).
                // The old code ALWAYS replied 0 (echoing HELLO), so the dash
                // treated us as brand-new on every connection and re-prompted +
                // re-derived every time - THE root cause of "asks to register the
                // app on every reconnect". (An earlier "reply GENERATE_KEYS"
                // attempt looked ignored only because back then no key array was
                // persisted, so the follow-up AssignKeyIndex stalled on an empty
                // pool; we persist it now, so the resume completes.)
                val address = currentDeviceAddress
                val storedKeys = address?.let {
                    AppSettings(this@BccuConnectionService).loadSessionKeys(it)
                }
                if (!storedKeys.isNullOrEmpty()) {
                    sessionKeys = storedKeys
                    AppLogger.log("Auth", "cmd=AppIdKeyArrStatus (known bike $address, ${storedKeys.size} keys) -> VALID_KEY_ARRAY(1); dash should resume without prompting")
                    replyControl(BccuProtocol.STATUS_VALID_KEY_ARRAY)
                } else {
                    AppLogger.log("Auth", "cmd=AppIdKeyArrStatus (new bike $address, no stored keys) -> INITIAL_CONNECTION(0); dash will prompt + generate keys")
                    replyControl(BccuProtocol.STATUS_INITIAL_CONNECTION)
                }
            }
            cmd == BccuProtocol.CMD_GENERATE_KEYS -> {
                AppLogger.log("Auth", "cmd=ComputeKeyArr, deriving session keys")
                val mirrored = BccuCrypto.buildMirrored(decrypted)
                val keys = BccuCrypto.deriveSessionKeys(decrypted, mirrored, iv, secret)
                sessionKeys = keys
                // Persist the freshly-derived array per-MAC. On the NEXT
                // connection we answer AppIdKeyArrStatus with VALID_KEY_ARRAY and
                // the dash resumes into this same array (no prompt, no re-derive),
                // so our copy must survive disconnects and process death.
                currentDeviceAddress?.let {
                    AppSettings(this@BccuConnectionService).storeSessionKeys(it, keys)
                }
                replyControl(BccuProtocol.STATUS_KEY_ARRAY_GENERATED)
            }
            cmd in 16..31 -> {
                val keyIndex = cmd and 0x0F
                var keys = sessionKeys
                if (keys == null) {
                    // Reconnect: the dash skipped GENERATE_KEYS and selected a
                    // key from the pool it has kept since pairing. Restore the
                    // pool we persisted then instead of stalling forever.
                    keys = currentDeviceAddress?.let {
                        AppSettings(this@BccuConnectionService).loadSessionKeys(it)
                    }
                    if (keys != null) {
                        AppLogger.log("Auth", "Restored persisted session key pool (${keys.size} keys)")
                        sessionKeys = keys
                    }
                }
                if (keys == null || keyIndex >= keys.size) {
                    AppLogger.log("Auth", "!! Bike selected key index $keyIndex but no key pool yet")
                    return
                }
                activeSessionKey = keys[keyIndex]
                replyControl(BccuProtocol.CMD_KEY_ACK_BASE or keyIndex)
                if (_connectionState.value != ConnectionState.AUTHENTICATED && !authCompleting) {
                    AppLogger.log("Auth", ">>> AUTHENTICATED <<< (key index $keyIndex)")
                    // The state and its bookkeeping are main-thread business (the handshake timer runs there):
                    // queued, and the timer is told it is coming, so a handshake finishing as the timer fires keeps its link.
                    authCompleting = true
                    val link = gatt
                    lifecycleScope.launch {
                        authCompleting = false
                        if (destroyed || link == null || link !== gatt || !linkUp) return@launch // the link was recycled meanwhile
                        handshakeTimeoutJob?.cancel(); handshakeTimeoutJob = null
                        _retryAvailableAtMs.value = null
                        handshakeRecoveries = 0
                        preAuthDrops = 0
                        absentFailures = 0
                        _repairHint.value = false
                        _reconnecting.value = false
                        _lastError.value = null
                        lastAuthMsgMs = SystemClock.elapsedRealtime()
                        livenessLogged = false
                        stopBleScan()
                        _connectionState.value = ConnectionState.AUTHENTICATED
                        stampLastSeen()
                    }
                    currentDeviceAddress?.let {
                        val s = AppSettings(this@BccuConnectionService)
                        s.markPairedBefore(it)
                        // Read straight back so a future "first time pairing" log
                        // unambiguously means the flag failed to persist (vs. a
                        // genuine first run) - narrows the reconnect-prompt bug.
                        AppLogger.log("Auth", "markPairedBefore($it) persisted=${s.hasPairedBefore(it)}")
                    }
                    enableNotification(BccuProtocol.TBT_NAV_REQUEST)
                    sendNavigationState(guidanceOn = true, gpsIconOn = true)
                    // Re-push any active guidance snapshot so a reconnect never
                    // leaves the TFT stale, THEN greet only if nothing is being
                    // guided. Both on main, in order: the greeting used to race
                    // the re-push from this binder thread, and its 6 s clear could
                    // then blank live guidance.
                    lifecycleScope.launch {
                        navCoordinator?.reAuthNow()
                        if (navCoordinator?.isGuidanceActive != true) sendGreeting()
                    }
                    // Diagnostics only: reads device info / VIN and probes the
                    // undocumented 0200/0300 services, queuing ~20 serialised GATT
                    // ops for ~10s after every auth (results are logged, never used
                    // functionally). Field logs tied that post-auth churn - and a
                    // status-133 mystery-char read - to spontaneous link drops, so
                    // keep it out of release builds.
                    if (BuildConfig.DEBUG) probeVehicleInfo()
                }
            }
        }
    }

    /**
     * Watchdog for a handshake that never completes, armed the moment the link
     * comes up. The budget spans link-up -> AUTHENTICATED: a bonded dash gets
     * there in ~3s, but right after ignition-on its pairing manager can lag its
     * radio - it sends m1/HELLO then goes silent (or, on a reused ACL, never
     * sends m1 at all) - and the only cure is a clean link drop + a genuinely
     * fresh connection. Recovery goes through SCAN, never a direct connect: the
     * dash only advertises once the old ACL is truly gone, so "found in scan"
     * guarantees the next connection restarts auth with a fresh m1. (A direct
     * connect 1.5s after close() was field-observed re-attaching to the
     * still-live ACL in 78ms: no m1, no auth, dead until ignition-off.) There
     * is no retry cap - each cycle is cheap and self-paced by the dash's own
     * advertising, and capping it left a stalled dash permanently unrecovered.
     * We deliberately do NOT clear the persisted key array here: the dash skips
     * its prompt only when we answer AppIdKeyArrStatus with VALID_KEY_ARRAY,
     * which needs that array - dropping it would force a needless re-pair.
     */
    private fun startHandshakeTimeout() {
        handshakeTimeoutJob?.cancel()
        handshakeTimeoutJob = lifecycleScope.launch {
            val addr = currentDeviceAddress ?: return@launch
            val known = AppSettings(this@BccuConnectionService).hasPairedBefore(addr)
            val budget = if (known) HANDSHAKE_TIMEOUT_KNOWN_MS else HANDSHAKE_TIMEOUT_FIRST_PAIR_MS
            // A tap during the handshake is ignored too: say when it will act again.
            _retryAvailableAtMs.value = System.currentTimeMillis() + budget
            delay(budget)
            // Main thread, like the bookkeeping of a handshake that just finished: the two cannot interleave.
            if (_connectionState.value == ConnectionState.AUTHENTICATED || authCompleting || destroyed) return@launch
            noteHandshakeFailure(addr)
            // Back off instead of looping: one quickish retry, then long
            // cooldowns. Rapid drop/reconnect cycles against a booting dash
            // were suspected of re-triggering its "add device" prompt (R21
            // field report) - giving it time to finish waking beats hammering.
            val cooldown = handshakeCooldownMs(handshakeRecoveries)
            _lastError.value = "Handshake did not complete"
            AppLogger.log("Auth", "Handshake didn't complete in ${budget}ms - dropping the link, retrying in ${cooldown / 1000}s (attempt $handshakeRecoveries; pairing kept)")
            // (Stale-GATT guard: the late DISCONNECTED callback no longer clears the queue
            // for us, so teardownLink does it.) This job is the handshake timer: don't cancel it.
            teardownLink("handshake timeout", cancelHandshakeTimer = false)
            _retryAvailableAtMs.value = System.currentTimeMillis() + cooldown
            try {
                delay(cooldown)
            } finally {
                _retryAvailableAtMs.value = null
            }
            if (!destroyed && gatt == null) startBleScan(addr)
        }
    }


    private fun replyControl(command: Int) {
        val iv = tempIv ?: return
        val secret = tempSecret ?: return
        val msg = BccuCrypto.buildControlMessage(command)
        val encrypted = BccuCrypto.encryptControl(msg, secret, iv)
        writeCharacteristic(BccuProtocol.AUTH_REPLY, encrypted)
    }

    /**
     * Encrypt [payload] with the live session key/IV and write it to [uuid].
     * Returns false (without writing) when there's no authenticated session yet,
     * so the dash-write methods below don't each repeat the key/iv null-guard and
     * the `BccuCrypto.encryptData(payload, key, iv)` call.
     */
    private fun sendEncrypted(uuid: UUID, payload: ByteArray, coalesce: Boolean = false): Boolean {
        if (activeSessionKey == null || tempIv == null) return false
        // Encrypted when the op actually runs, with the key/IV live THEN: a frame that waited in
        // the queue across a re-auth must not go out under the previous session's key.
        writeCharacteristic(uuid, coalesce = coalesce) {
            val key = activeSessionKey
            val iv = tempIv
            if (key == null || iv == null) null else BccuCrypto.encryptData(payload, key, iv)
        }
        return true
    }

    /** True once the crypto handshake has produced a usable session key + IV. */
    private fun hasSession(): Boolean = activeSessionKey != null && tempIv != null

    fun sendTurnIcon(icon: BccuProtocol.TurnIcon, visibility: BccuProtocol.Visibility = BccuProtocol.Visibility.FULL) {
        val payload = BccuProtocol.buildTurnIconPayload(icon, visibility)
        // Live guidance: cancel a pending greeting / "nav ended" clear, like
        // sendGuidance does (an icon-only update used to let it fire).
        if (visibility != BccuProtocol.Visibility.OFF) guidanceClearJob?.cancel()
        if (sendEncrypted(BccuProtocol.TURN_ICON, payload, coalesce = true)) {
            AppLogger.log("Dash", "Sending TURN_ICON = ${icon.name}")
        } else {
            AppLogger.log("Dash", "!! Cannot send TURN_ICON - not authenticated yet")
        }
    }

    /**
     * Send a notification to the dash's bottom banner.
     *
     * - Short text (fits the 16-char banner): sent once, then auto-cleared
     *   after [NOTIFICATION_CLEAR_DELAY_MS], matching BikeConnect's timed clear.
     * - Long text, with marquee enabled: scrolled one character at a time
     *   right-to-left across the 16-char window so the whole message can be
     *   read, then cleared. See [runMarquee].
     */
    fun sendNotification(text: String, icon: BccuProtocol.NotificationIcon) {
        val key = activeSessionKey
        val iv = tempIv
        if (key == null || iv == null) {
            AppLogger.log("Dash", "!! Cannot send NOTIFICATION ${AppLogger.sensitive(text)} - not authenticated yet (state=${_connectionState.value})")
            return
        }
        DashPreview.banner(text)
        clearJob?.cancel()
        marqueeJob?.cancel()
        lastNotificationIcon = icon
        bannerOwnedByMessage = true
        offlineBannerShown = false // any new message replaces our "Offline" banner

        val marquee = AppSettings(this).marqueeEnabled && text.length > NOTIFICATION_BANNER_CHARS
        if (marquee) {
            AppLogger.log("Dash", "Sending NOTIFICATION (marquee) icon=${icon.name} text=${AppLogger.sensitive(text)}")
            marqueeJob = lifecycleScope.launch { runMarquee(text, icon) }
        } else {
            AppLogger.log("Dash", "Sending NOTIFICATION icon=${icon.name} text=${AppLogger.sensitive(text)}")
            writeNotificationFrame(text, icon, BccuProtocol.Visibility.FULL)
            clearJob = lifecycleScope.launch {
                delay(NOTIFICATION_CLEAR_DELAY_MS)
                clearNotificationDisplay()
            }
        }
    }

    /**
     * Scroll [text] across the dash's 16-char banner one character per step.
     * The window slides right-to-left (start index advances), so the text
     * appears to scroll leftwards - the effect the user asked for. A gap of a
     * few spaces separates the end of one pass from the start of the next; the
     * whole message is scrolled [MARQUEE_CYCLES] times, then the banner clears.
     */
    private suspend fun runMarquee(text: String, icon: BccuProtocol.NotificationIcon) {
        val gap = "   "
        // Step by grapheme cluster, not UTF-16 unit: a raw substring window cut
        // emoji surrogate pairs (shown as '?') on almost every frame.
        val chars = BccuProtocol.graphemes(text + gap)
        repeat(MARQUEE_CYCLES) {
            for (start in chars.indices) {
                val window = (0 until NOTIFICATION_BANNER_CHARS).joinToString("") { chars[(start + it) % chars.size] }
                writeNotificationFrame(window, icon, BccuProtocol.Visibility.FULL)
                delay(MARQUEE_STEP_MS)
            }
        }
        clearNotificationDisplay()
    }

    // True while a mirrored message (not a status readout) is on the banner.
    // The overspeed readout must never steal the banner mid-message: without
    // this, speed refreshes every second cancelled the message marquee and the
    // two texts ping-ponged unreadably (R19 review finding).
    @Volatile private var bannerOwnedByMessage = false

    /**
     * Live overspeed readout for the bottom banner. Lower priority than
     * messages: skipped while a message is still displaying; auto-clears a few
     * seconds after the last update (i.e. once the rider slows back down).
     */
    fun sendSpeedBanner(text: String) {
        if (activeSessionKey == null || tempIv == null) return
        if (bannerOwnedByMessage && (marqueeJob?.isActive == true || clearJob?.isActive == true)) {
            return // a message is on the banner - let it finish its scroll/clear
        }
        DashPreview.banner(text)
        bannerOwnedByMessage = false
        marqueeJob?.cancel()
        clearJob?.cancel()
        lastNotificationIcon = BccuProtocol.NotificationIcon.NOTIFICATION_WAYPOINT
        writeNotificationFrame(text, BccuProtocol.NotificationIcon.NOTIFICATION_WAYPOINT, BccuProtocol.Visibility.FULL)
        clearJob = lifecycleScope.launch {
            delay(NOTIFICATION_CLEAR_DELAY_MS)
            clearNotificationDisplay()
        }
    }

    // True while our "Offline" heads-up is (or was last) on the banner, so a
    // reconnect only clears the banner if it was ours to clear.
    @Volatile private var offlineBannerShown = false

    /**
     * Show/clear the dash "Offline" alert (network dropped mid-trip). This is the
     * human-readable heads-up; the persistent offline state is also reflected via
     * the GPS/nav status bit in NAVIGATION_STATE (see the encoder). The banner
     * auto-clears itself after the usual delay, so this mainly matters on the
     * drop; on reconnect we clear it if it's still ours.
     */
    private fun showOfflineBanner(show: Boolean) {
        if (show) {
            sendNotification("Offline", BccuProtocol.NotificationIcon.WARNING)
            offlineBannerShown = true
        } else if (offlineBannerShown) {
            offlineBannerShown = false
            clearNotificationDisplay()
        }
    }

    private fun writeNotificationFrame(text: String, icon: BccuProtocol.NotificationIcon, visibility: BccuProtocol.Visibility) {
        val payload = BccuProtocol.buildNotificationPayload(icon, text, visibility)
        sendEncrypted(BccuProtocol.NOTIFICATION, payload, coalesce = true)
    }

    /**
     * Clear the banner the way BikeConnect does (confirmed from its decompiled
     * `h9.b`): keep the last valid icon byte and flip visibility to OFF (the
     * enum's byte value 1 = NONE), rather than sending icon UNKNOWN(0). The old
     * clear used UNKNOWN(0), which the hardware calibration pass found does NOT
     * render - a very likely reason earlier clear attempts left the banner
     * stuck on screen. Text is blanked too for good measure.
     */
    private fun clearNotificationDisplay() {
        DashPreview.clearBanner()
        bannerOwnedByMessage = false
        offlineBannerShown = false
        val payload = BccuProtocol.buildNotificationPayload(lastNotificationIcon, "", BccuProtocol.Visibility.OFF)
        if (sendEncrypted(BccuProtocol.NOTIFICATION, payload, coalesce = true)) {
            AppLogger.log("Dash", "Clearing NOTIFICATION display (visibility OFF, icon=${lastNotificationIcon.name})")
        }
    }

    /**
     * Friendly greeting shown on the dash's center display the moment we connect
     * & authenticate: the START/"begin your ride" turn icon plus "Hi <name>!" on
     * the road line. Auto-clears after [GREETING_MS], or the instant real
     * navigation guidance arrives (sendGuidance cancels the same clear job). The
     * dash can only render its own built-in glyphs, so the icon is the START code
     * (the app's own connect banner shows the walking-person icon).
     */
    private fun sendGreeting() {
        val name = AppSettings(this).userName?.trim()
        val text = if (!name.isNullOrBlank()) "Hi $name!" else "Welcome!"
        val full = BccuProtocol.Visibility.FULL
        // Bail (without scheduling the clear job) if we're not authenticated yet.
        if (!sendEncrypted(BccuProtocol.TURN_ICON,
                BccuProtocol.buildTurnIconPayload(BccuProtocol.TurnIcon.START, full), coalesce = true)) return
        AppLogger.log("Dash", "Sending connect greeting: ${AppLogger.sensitive(text)}")
        sendEncrypted(BccuProtocol.TURN_ROAD, BccuProtocol.buildTurnRoadPayload(text, full), coalesce = true)
        sendEncrypted(BccuProtocol.TURN_DISTANCE,
            BccuProtocol.buildTurnDistancePayload("", BccuProtocol.Visibility.OFF), coalesce = true)
        // Reuse the guidance-clear job: a real guidance update cancels it, so the
        // greeting is replaced seamlessly the moment navigation starts.
        guidanceClearJob?.cancel()
        guidanceClearJob = lifecycleScope.launch {
            delay(GREETING_MS)
            clearGuidance()
        }
    }

    fun sendNavigationState(guidanceOn: Boolean, gpsIconOn: Boolean, volume: Int = 255) {
        val payload = BccuProtocol.buildNavigationStatePayload(guidanceOn, gpsIconOn, volume)
        if (sendEncrypted(BccuProtocol.NAVIGATION_STATE, payload)) {
            AppLogger.log("Dash", "Sending NAVIGATION_STATE guidanceOn=$guidanceOn gpsIconOn=$gpsIconOn volume=$volume")
        } else {
            AppLogger.log("Dash", "!! Cannot send NAVIGATION_STATE - not authenticated yet")
        }
    }

    /**
     * Updates the dash's center guidance view - TURN_DISTANCE, TURN_ROAD, ETA,
     * and REMAINING_DISTANCE, confirmed from the official app's NavableBleImpl
     * (see BCCU_BLE_PROTOCOL.md). This is a different display region than
     * sendNotification()'s bottom banner. Any field can be null to leave that
     * characteristic unchanged.
     */
    fun sendGuidance(distanceText: String?, roadText: String?, etaText: String? = null, remainingDistanceText: String? = null) {
        if (!hasSession()) {
            AppLogger.log("Dash", "!! Cannot send guidance update - not authenticated yet")
            return
        }
        // A fresh guidance update means navigation is still active; cancel any
        // pending "nav ended" clear that a transient notification-remove queued.
        guidanceClearJob?.cancel()
        if (distanceText != null) {
            AppLogger.log("Dash", "Sending TURN_DISTANCE = ${AppLogger.sensitive(distanceText)}")
            sendEncrypted(BccuProtocol.TURN_DISTANCE, BccuProtocol.buildTurnDistancePayload(distanceText), coalesce = true)
        }
        if (roadText != null) {
            AppLogger.log("Dash", "Sending TURN_ROAD = ${AppLogger.sensitive(roadText)}")
            sendEncrypted(BccuProtocol.TURN_ROAD, BccuProtocol.buildTurnRoadPayload(roadText), coalesce = true)
        }
        if (etaText != null) {
            AppLogger.log("Dash", "Sending ETA = ${AppLogger.sensitive(etaText)}")
            sendEncrypted(BccuProtocol.ETA, BccuProtocol.buildEtaPayload(etaText), coalesce = true)
        }
        if (remainingDistanceText != null) {
            AppLogger.log("Dash", "Sending REMAINING_DISTANCE = ${AppLogger.sensitive(remainingDistanceText)}")
            sendEncrypted(BccuProtocol.REMAINING_DISTANCE, BccuProtocol.buildRemainingDistancePayload(remainingDistanceText), coalesce = true)
        }
    }

    /**
     * Debounced clear of the center guidance view. Google Maps briefly removes
     * and re-posts its notification during a route, so we wait a moment and let
     * a subsequent guidance update cancel this - only a genuine end-of-nav (no
     * update for GUIDANCE_CLEAR_DELAY_MS) actually blanks the display.
     */
    private fun scheduleGuidanceClear() {
        guidanceClearJob?.cancel()
        guidanceClearJob = lifecycleScope.launch {
            delay(GUIDANCE_CLEAR_DELAY_MS)
            clearGuidance()
        }
    }

    /**
     * Blank the dash's center guidance view, the way the official app's
     * `disableGuidanceAndNotificationWidgets()` does: every label characteristic
     * to empty text + visibility OFF, and the turn icon to UNDEFINED + OFF.
     * Without this, the last turn ("1st Cross Rd / 0 m") stayed on the dash
     * forever after the rider exited Maps.
     */
    private fun clearGuidance() {
        // Navigation genuinely ended (this call is debounced past Maps' routine
        // remove+repost) - let the next route's first approach beep fire again.
        TurnBeeper.reset()
        if (!hasSession()) return
        AppLogger.log("Dash", "Clearing center guidance (navigation ended)")
        val off = BccuProtocol.Visibility.OFF
        sendEncrypted(BccuProtocol.TURN_ICON, BccuProtocol.buildTurnIconPayload(BccuProtocol.TurnIcon.UNDEFINED, off), coalesce = true)
        sendEncrypted(BccuProtocol.TURN_DISTANCE, BccuProtocol.buildTurnDistancePayload("", off), coalesce = true)
        sendEncrypted(BccuProtocol.TURN_INFO, BccuProtocol.buildTurnInfoPayload("", off), coalesce = true)
        sendEncrypted(BccuProtocol.TURN_ROAD, BccuProtocol.buildTurnRoadPayload("", off), coalesce = true)
        sendEncrypted(BccuProtocol.ETA, BccuProtocol.buildEtaPayload("", off), coalesce = true)
        sendEncrypted(BccuProtocol.REMAINING_DISTANCE, BccuProtocol.buildRemainingDistancePayload("", off), coalesce = true)
    }

    // --- Diagnostics ---

    /** Log every discovered service + characteristic, so a real run reveals what this specific bike exposes (e.g. whether the PRPC telemetry service is present). */
    private fun dumpGattTable(g: BluetoothGatt) {
        AppLogger.log("GATT", "=== GATT table dump (${g.services.size} services) ===")
        for (service in g.services) {
            AppLogger.log("GATT", "Service ${service.uuid}")
            for (c in service.characteristics) {
                val props = c.properties
                val flags = buildString {
                    if (props and BluetoothGattCharacteristic.PROPERTY_READ != 0) append("R")
                    if (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) append("W")
                    if (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) append("w")
                    if (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) append("N")
                    if (props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) append("I")
                }
                AppLogger.log("GATT", "  char ${c.uuid} [$flags]")
            }
        }
        AppLogger.log("GATT", "=== end GATT dump ===")
    }

    private fun findCharacteristic(g: BluetoothGatt, uuid: UUID): BluetoothGattCharacteristic? {
        for (service in g.services) {
            val c = service.getCharacteristic(uuid)
            if (c != null) return c
        }
        return null
    }

    private fun enableIndication(uuid: UUID) = enableCccd(uuid, indication = true)
    private fun enableNotification(uuid: UUID) = enableCccd(uuid, indication = false)

    private fun enableCccd(uuid: UUID, indication: Boolean) {
        gattOps.enqueue(target = uuid) {
            val g = gatt
            val characteristic = g?.let { findCharacteristic(it, uuid) }
            if (g == null || characteristic == null) {
                AppLogger.log("BLE", "!! Cannot enable ${if (indication) "indication" else "notification"} on $uuid - characteristic not found")
                gattOps.finish()
                return@enqueue
            }
            g.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(BccuProtocol.CCCD)
            if (descriptor == null) {
                AppLogger.log("BLE", "!! Cannot enable ${if (indication) "indication" else "notification"} on $uuid - no CCCD descriptor")
                gattOps.finish()
                return@enqueue
            }
            val value = if (indication)
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            else
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            val accepted = g.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
            AppLogger.log("BLE", "writeDescriptor CCCD for $uuid (${if (indication) "indication" else "notification"}) queued=$accepted")
            if (!accepted) {
                gattOps.finish()
            }
        }
    }

    /** Queue a read of a single characteristic (result surfaces in onCharacteristicRead -> logProbeValue). */
    private fun readCharacteristic(uuid: UUID) {
        gattOps.enqueue(target = uuid) {
            val g = gatt
            val characteristic = g?.let { findCharacteristic(it, uuid) }
            if (g == null || characteristic == null) {
                AppLogger.log("Probe", "read: characteristic $uuid not found, skipping")
                gattOps.finish()
                return@enqueue
            }
            val accepted = g.readCharacteristic(characteristic)
            AppLogger.log("Probe", "readCharacteristic $uuid queued=$accepted")
            if (!accepted) gattOps.finish()
        }
    }

    /**
     * Post-auth exploration of everything this bike might expose beyond nav.
     * Reads the standard Device Information service (model/serial/firmware),
     * reads the VIN, and enables notify/indicate + reads on the two
     * undocumented services (0200/0300) so a subsequent engine-on/revving
     * capture shows whether any of those characteristics carry live telemetry
     * (RPM/speed/gear/etc). All output lands under the [Probe] log tag. Safe to
     * call on any bike: missing services/characteristics are skipped with a log
     * line, never a crash.
     */
    private fun probeVehicleInfo() {
        val g = gatt ?: return
        AppLogger.log("Probe", "=== probeVehicleInfo: reading device info / VIN / mystery services ===")

        // Standard Device Information service: model, serial, all revisions.
        val deviceInfo = g.getService(BccuProtocol.DEVICE_INFO_SERVICE)
        if (deviceInfo != null) {
            for (c in deviceInfo.characteristics) readCharacteristic(c.uuid)
        } else {
            AppLogger.log("Probe", "Device Information service (180a) not present")
        }

        // VIN: reading 0002 cold returns all-zeros (confirmed in the field) - the
        // official app first *requests* it by writing to GET_VIN_REQUEST (0001),
        // then the value shows up on 0002 (readable + notify). Enable notify so we
        // catch the async push, fire the request, then also read.
        val key = activeSessionKey; val iv = tempIv
        if (key != null && iv != null && g.getService(BccuProtocol.BASE_SERVICE) != null) {
            enableNotification(BccuProtocol.BASE_VIN)
            // A 1-byte request marker; encrypted on the data plane like every 000x write.
            writeCharacteristic(BccuProtocol.BASE_GET_VIN_REQUEST, BccuCrypto.encryptData(byteArrayOf(0x01), key, iv))
        }
        readCharacteristic(BccuProtocol.BASE_VIN)

        // Undocumented 0200/0300: subscribe to every notify/indicate char and
        // read every readable one, so an engine-on capture reveals live data.
        for (svcUuid in listOf(BccuProtocol.MYSTERY_SERVICE_0200, BccuProtocol.MYSTERY_SERVICE_0300)) {
            val svc = g.getService(svcUuid)
            if (svc == null) {
                AppLogger.log("Probe", "Mystery service $svcUuid not present")
                continue
            }
            for (c in svc.characteristics) {
                val props = c.properties
                if (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) enableNotification(c.uuid)
                else if (props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) enableIndication(c.uuid)
                if (props and BluetoothGattCharacteristic.PROPERTY_READ != 0) readCharacteristic(c.uuid)
            }
        }
        AppLogger.log("Probe", "=== probeVehicleInfo: requests queued ===")
    }

    /**
     * @param coalesce true for "latest value wins" display writes (guidance
     * labels, marquee frames, banner): a still-queued older write to the same
     * characteristic is dropped, keeping the queue bounded and frames fresh on
     * a slow link. Never set for control-plane writes (auth replies, PRPC
     * requests) where every message matters.
     */
    private fun writeCharacteristic(uuid: UUID, data: ByteArray, coalesce: Boolean = false) =
        writeCharacteristic(uuid, coalesce) { data }

    /** [payload] is evaluated inside the queued op (null = nothing to send any more). */
    private fun writeCharacteristic(uuid: UUID, coalesce: Boolean = false, payload: () -> ByteArray?) {
        gattOps.enqueue(if (coalesce) uuid else null, target = uuid) {
            val g = gatt
            val characteristic = g?.let { findCharacteristic(it, uuid) }
            if (g == null || characteristic == null) {
                AppLogger.log("BLE", "!! Characteristic $uuid not found, cannot write")
                gattOps.finish()
                return@enqueue
            }
            val data = try {
                payload()
            } catch (e: Exception) {
                AppLogger.log("BLE", "!! building payload for $uuid failed: ${e.javaClass.simpleName}")
                null
            }
            if (data == null) {
                gattOps.finish()
                return@enqueue
            }
            val accepted = g.writeCharacteristic(
                characteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
            ) == BluetoothStatusCodes.SUCCESS
            AppLogger.log("BLE", "writeCharacteristic $uuid (${data.size} bytes) queued=$accepted")
            if (!accepted) {
                gattOps.finish()
                if (uuid in guidanceUuids) requestGuidanceResync()
            }
        }
    }
}
