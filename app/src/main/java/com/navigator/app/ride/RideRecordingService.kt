package com.navigator.app.ride

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.navigator.app.R
import com.navigator.app.logging.AppLogger
import com.navigator.app.notify.NotificationRequestCodes
import com.navigator.app.notify.StatusNotification

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
                StatusNotification.refresh(this)
                return START_NOT_STICKY
            }
            else -> {
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
                        StatusNotification.refresh(this)
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

    override fun onDestroy() {
        // Safety net: finalise if we were killed without an explicit stop.
        recorder?.stop()
        recorder = null
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        ServiceCompat.startForeground(
            this, StatusNotification.ID, StatusNotification.build(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
    }

    companion object {
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

        fun pauseIntent(context: Context): PendingIntent = servicePending(context, NotificationRequestCodes.RECORDING_TOGGLE, ACTION_PAUSE)
        fun resumeIntent(context: Context): PendingIntent = servicePending(context, NotificationRequestCodes.RECORDING_TOGGLE, ACTION_RESUME)
        fun stopIntent(context: Context): PendingIntent = servicePending(context, NotificationRequestCodes.RECORDING_STOP, ACTION_STOP)

        private fun servicePending(context: Context, code: Int, action: String): PendingIntent = PendingIntent.getService(
            context, code, Intent(context, RideRecordingService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** Stop recording and finalise the ride. */
        fun stop(context: Context) {
            val intent = Intent(context, RideRecordingService::class.java).setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
        }
    }
}

private fun Intent.doubleOrNull(key: String): Double? =
    if (hasExtra(key)) getDoubleExtra(key, 0.0) else null
