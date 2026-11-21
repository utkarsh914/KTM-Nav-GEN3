package com.navigator.app.ride

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.navigator.app.R
import com.navigator.app.ble.BccuConnectionService
import com.navigator.app.logging.AppLogger
import com.navigator.app.nav.model.isActiveNav
import com.navigator.app.nav.providers.GoogleNavSdkProvider
import com.navigator.app.notify.NotificationActions
import com.navigator.app.notify.NotificationChannels
import com.navigator.app.notify.NotificationRequestCodes
import com.navigator.app.notify.PromotedOngoing
import com.navigator.app.notify.RecordingNotice
import com.navigator.app.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/**
 * Dedicated location foreground service that owns a [RideRecorder] for the life
 * of one navigation trip, so the GPS track keeps recording with the screen off /
 * app backgrounded. Kept separate from the BLE connection service so recording
 * works during standalone navigation (no bike connected) and never triggers a
 * BLE connection attempt.
 *
 * Started/stopped by the app-scoped nav-state observer in OpenDashApplication.
 */
class RideRecordingService : Service() {

    private var recorder: RideRecorder? = null
    private var source: String? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRecordingAndSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE, ACTION_RESUME -> {
                val r = recorder
                if (r == null) {
                    // Nothing recording (stale notification action): don't linger as an empty FGS.
                    stopRecordingAndSelf()
                    return START_NOT_STICKY
                }
                if (intent?.action == ACTION_PAUSE) r.pause() else r.resume()
                refreshNotification()
                return START_NOT_STICKY
            }
            else -> {
                // Before the first notification is built: it chooses its slot from the source.
                if (recorder == null) source = RideSource.clean(intent?.getStringExtra(EXTRA_SOURCE)) ?: RideSource.NAV
                try {
                    startForegroundCompat()
                } catch (e: Exception) {
                    // Location permission revoked / FGS start not allowed: don't crash the app.
                    AppLogger.log("Ride", "!! cannot start ride recording foreground service: ${e.javaClass.simpleName}")
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (recorder == null) {
                    val r = RideRecorder(applicationContext)
                    val ok = r.start(
                        destLabel = intent?.getStringExtra(EXTRA_LABEL),
                        destLat = intent?.doubleOrNull(EXTRA_LAT),
                        destLng = intent?.doubleOrNull(EXTRA_LNG),
                        source = RideSource.clean(intent?.getStringExtra(EXTRA_SOURCE)) ?: RideSource.NAV,
                    )
                    if (ok) {
                        recorder = r
                        refreshNotification()
                        watchTripAndRecording()
                    } else {
                        // Don't leave an empty "Recording ride" foreground notification behind.
                        AppLogger.log("Ride", "Recorder failed to start - stopping the service")
                        stopRecordingAndSelf()
                        return START_NOT_STICKY
                    }
                }
            }
        }
        // Don't auto-restart: recording is tied to a live trip; a restart with no
        // trip context would record an unbounded background track.
        return START_NOT_STICKY
    }

    private fun stopRecordingAndSelf() {
        recorder?.stop()
        recorder = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** The same notification moves between the promoted slot and a silent row as a trip starts and ends. */
    private var watching: Job? = null

    private fun watchTripAndRecording() {
        if (watching != null) return
        watching = scope.launch {
            combine(
                GoogleNavSdkProvider.state.map { it.sessionState.isActiveNav() }.distinctUntilChanged(),
                RideSession.state.sample(NOTIFICATION_SAMPLE_MS), BccuConnectionService.connectionState,
            ) { _, _, _ -> }.collect { if (recorder != null) refreshNotification() }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        // Safety net: finalise if we were killed without an explicit stop.
        recorder?.stop()
        recorder = null
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
    }

    private fun refreshNotification() {
        runCatching {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun buildNotification(): android.app.Notification {
        val open = NotificationActions.open(this, NotificationRequestCodes.OPEN_APP)
        val stop = PendingIntent.getService(
            this, NotificationRequestCodes.RECORDING_STOP,
            Intent(this, RideRecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val session = RideSession.state.value
        val now = System.currentTimeMillis()
        val mode = RecordingNotice.mode(GoogleNavSdkProvider.state.value.sessionState.isActiveNav(), source)
        val paired = AppSettings(this).bondedDeviceAddress != null
        val dash = if (paired) BccuConnectionService.connectionState.value == BccuConnectionService.ConnectionState.AUTHENTICATED else null
        val content = RecordingNotice.build(session.copy(paused = recorder?.isPaused == true || session.paused), now, mode, dash, resources)
        val paused = content.paused
        val toggle = PendingIntent.getService(
            this, NotificationRequestCodes.RECORDING_TOGGLE,
            Intent(this, RideRecordingService::class.java).setAction(if (paused) ACTION_RESUME else ACTION_PAUSE),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val channel = if (content.promoted) NotificationChannels.RECORDING else NotificationChannels.RECORDING_QUIET
        NotificationChannels.ensure(this, channel)
        val b = Notification.Builder(this, channel)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
        if (content.showActions) {
            // Visible without expanding: Pause / Stop are the point of the promoted notification (SY4).
            b.addAction(action(if (paused) R.string.rec_action_resume else R.string.rec_action_pause, toggle))
            b.addAction(action(R.string.rec_action_stop, stop))
            if (!paused) {
                // System-drawn elapsed timer (excludes earlier paused time).
                b.setUsesChronometer(true).setShowWhen(true).setWhen(now - session.elapsedMs(now))
            } else b.setShowWhen(false)
            // The Live Updates switch in Settings covers the recording notification too.
            if (runCatching { AppSettings(this).liveUpdatesEnabled }.getOrDefault(true)) PromotedOngoing.apply(b)
        } else {
            b.setShowWhen(false)
        }
        return b.build()
    }

    private fun action(label: Int, intent: PendingIntent) =
        Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_notification), getString(label), intent).build()

    companion object {
        private const val NOTIFICATION_ID = 43
        private const val NOTIFICATION_SAMPLE_MS = 2_000L
        private const val ACTION_STOP = "com.navigator.app.action.STOP_RIDE_RECORDING"
        private const val ACTION_PAUSE = "com.navigator.app.action.PAUSE_RIDE_RECORDING"
        private const val ACTION_RESUME = "com.navigator.app.action.RESUME_RIDE_RECORDING"
        private const val EXTRA_SOURCE = "source"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_LAT = "lat"
        private const val EXTRA_LNG = "lng"

        /** Start recording a trip toward the (optional) destination. */
        fun start(
            context: Context, destLabel: String?, destLat: Double?, destLng: Double?, source: String = RideSource.NAV,
        ): Boolean {
            val intent = Intent(context, RideRecordingService::class.java).apply {
                putExtra(EXTRA_SOURCE, source)
                putExtra(EXTRA_LABEL, destLabel)
                destLat?.let { putExtra(EXTRA_LAT, it) }
                destLng?.let { putExtra(EXTRA_LNG, it) }
            }
            return runCatching {
                context.startForegroundService(intent)
            }.onFailure { AppLogger.log("Ride", "!! start service failed: $it") }.isSuccess
        }

        /** Rider-initiated recording with no destination. */
        fun startManual(context: Context): Boolean = start(context, null, null, null, RideSource.MANUAL)

        fun pause(context: Context) = send(context, ACTION_PAUSE)
        fun resume(context: Context) = send(context, ACTION_RESUME)

        private fun send(context: Context, action: String) {
            runCatching { context.startService(Intent(context, RideRecordingService::class.java).setAction(action)) }
        }

        /** Stop recording and finalise the ride. */
        fun stop(context: Context) {
            val intent = Intent(context, RideRecordingService::class.java).setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
        }
    }
}

private fun Intent.doubleOrNull(key: String): Double? =
    if (hasExtra(key)) getDoubleExtra(key, 0.0) else null
