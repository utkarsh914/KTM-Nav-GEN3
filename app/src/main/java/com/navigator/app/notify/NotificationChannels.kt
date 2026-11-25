package com.navigator.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.navigator.app.R

/**
 * Every channel the app posts on. A channel's importance can't be changed once created, so a change of behaviour means
 * a new id; the retired ones are deleted by [deleteRetired]. All three foreground services share [STATUS].
 */
object NotificationChannels {
    /** The one ongoing "ride status" notification (trip, recording, mirroring, bike link, ready). */
    const val STATUS = "ride_status"
    const val CONNECTION_ALERT = "bccu_alert"
    const val OVERSPEED = "overspeed_alert"
    const val RIDE_SAVED = "ride_saved"

    /** Replaced by [STATUS] (the guidance, recording and connection notifications used to have one each). */
    internal val RETIRED = listOf(
        "nav_guidance", "nav_guidance_idle", "ride_recording", "ride_recording_quiet", "bccu_connection_v2", "bccu_connection",
    )

    fun ensure(context: Context, id: String) {
        runCatching {
            val nm = context.getSystemService(NotificationManager::class.java)
            val res = context.resources
            val channel = when (id) {
                STATUS -> NotificationChannel(id, res.getString(R.string.status_channel), NotificationManager.IMPORTANCE_LOW).apply {
                    description = res.getString(R.string.status_channel_desc)
                    setShowBadge(false)
                }
                CONNECTION_ALERT -> NotificationChannel(id, res.getString(R.string.conn_channel_alert), NotificationManager.IMPORTANCE_HIGH)
                OVERSPEED -> NotificationChannel(id, res.getString(R.string.overspeed_channel), NotificationManager.IMPORTANCE_HIGH)
                RIDE_SAVED -> NotificationChannel(id, res.getString(R.string.rec_saved_title), NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
                else -> return
            }
            nm.createNotificationChannel(channel)
        }
    }

    /** At app start, once the new channel exists: a failure to remove an old one must not cost the rider the new one. */
    fun deleteRetired(context: Context) {
        ensure(context, STATUS)
        val nm = context.getSystemService(NotificationManager::class.java)
        RETIRED.forEach { runCatching { nm.deleteNotificationChannel(it) } }
    }
}
