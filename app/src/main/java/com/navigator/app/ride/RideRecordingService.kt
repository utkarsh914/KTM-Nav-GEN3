package com.navigator.app.ride

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.navigator.app.R
import com.navigator.app.logging.AppLogger

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
                refreshNotification()
                return START_NOT_STICKY
            }
            else -> {
                createChannel()
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
            this, NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
    }

    private fun refreshNotification() {
        runCatching {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun buildNotification(): android.app.Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, com.navigator.app.ui.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, RideRecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val session = RideSession.state.value
        val paused = recorder?.isPaused == true || session.paused
        val toggle = PendingIntent.getService(
            this, 2,
            Intent(this, RideRecordingService::class.java).setAction(if (paused) ACTION_RESUME else ACTION_PAUSE),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val b = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (paused) "Ride paused" else "Recording ride")
            .setContentText(
                if (paused) "Tap Resume to keep recording" else "Saving your GPS track",
            )
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .addAction(0, if (paused) "Resume" else "Pause", toggle)
            .addAction(0, "Stop", stop)
        if (!paused) {
            // System-drawn elapsed timer (excludes earlier paused time).
            b.setUsesChronometer(true).setShowWhen(true)
                .setWhen(System.currentTimeMillis() - session.elapsedMs(System.currentTimeMillis()))
        } else b.setShowWhen(false)
        return b.build()
    }

    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "Ride recording", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    companion object {
        private const val CHANNEL_ID = "ride_recording"
        private const val NOTIFICATION_ID = 43
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
