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
                    )
                    if (ok) {
                        recorder = r
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
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Recording ride")
            .setContentText("Saving your GPS track for this trip")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .build()
    }

    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "Ride recording", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    companion object {
        private const val CHANNEL_ID = "ride_recording"
        private const val NOTIFICATION_ID = 43
        private const val ACTION_STOP = "com.navigator.app.action.STOP_RIDE_RECORDING"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_LAT = "lat"
        private const val EXTRA_LNG = "lng"

        /** Start recording a trip toward the (optional) destination. */
        fun start(context: Context, destLabel: String?, destLat: Double?, destLng: Double?): Boolean {
            val intent = Intent(context, RideRecordingService::class.java).apply {
                putExtra(EXTRA_LABEL, destLabel)
                destLat?.let { putExtra(EXTRA_LAT, it) }
                destLng?.let { putExtra(EXTRA_LNG, it) }
            }
            return runCatching {
                context.startForegroundService(intent)
            }.onFailure { AppLogger.log("Ride", "!! start service failed: $it") }.isSuccess
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
