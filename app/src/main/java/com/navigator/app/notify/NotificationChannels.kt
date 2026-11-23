package com.navigator.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.navigator.app.R

/**
 * Every channel the app posts on. A channel's importance can't be changed once created, so the
 * connection channel moved to a new id ([CONNECTION]) and the old one is deleted.
 */
object NotificationChannels {
    const val GUIDANCE = "nav_guidance"
    const val GUIDANCE_IDLE = "nav_guidance_idle"
    const val RECORDING = "ride_recording"
    const val RECORDING_QUIET = "ride_recording_quiet"
    const val CONNECTION = "bccu_connection_v2"
    const val CONNECTION_ALERT = "bccu_alert"
    const val OVERSPEED = "overspeed_alert"
    const val RIDE_SAVED = "ride_saved"

    private const val OLD_CONNECTION = "bccu_connection"

    fun ensure(context: Context, id: String) {
        runCatching {
            val nm = context.getSystemService(NotificationManager::class.java)
            val res = context.resources
            val channel = when (id) {
                GUIDANCE -> NotificationChannel(id, res.getString(R.string.live_channel_guidance), NotificationManager.IMPORTANCE_LOW).apply {
                    description = res.getString(R.string.live_channel_guidance_desc)
                    setShowBadge(false)
                }
                GUIDANCE_IDLE -> NotificationChannel(id, res.getString(R.string.live_channel_idle), NotificationManager.IMPORTANCE_MIN).apply {
                    description = res.getString(R.string.live_channel_idle_desc)
                    setShowBadge(false)
                }
                RECORDING -> NotificationChannel(id, res.getString(R.string.rec_channel), NotificationManager.IMPORTANCE_LOW)
                RECORDING_QUIET -> NotificationChannel(id, res.getString(R.string.rec_channel_quiet), NotificationManager.IMPORTANCE_MIN)
                CONNECTION -> NotificationChannel(id, res.getString(R.string.conn_channel), NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) }
                CONNECTION_ALERT -> NotificationChannel(id, res.getString(R.string.conn_channel_alert), NotificationManager.IMPORTANCE_HIGH)
                OVERSPEED -> NotificationChannel(id, res.getString(R.string.overspeed_channel), NotificationManager.IMPORTANCE_HIGH)
                RIDE_SAVED -> NotificationChannel(id, res.getString(R.string.rec_saved_title), NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
                else -> return
            }
            nm.createNotificationChannel(channel)
            // After the new one exists: a failure to remove the old one must not cost the rider the new one.
            if (id == CONNECTION) runCatching { nm.deleteNotificationChannel(OLD_CONNECTION) }
        }
    }
}
