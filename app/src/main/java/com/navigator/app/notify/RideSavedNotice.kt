package com.navigator.app.notify

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import com.navigator.app.R
import com.navigator.app.app.AppIntents
import com.navigator.app.nav.ktm.DistanceFormatter
import com.navigator.app.nav.model.DistanceUnits
import com.navigator.app.ride.RecordedRide
import com.navigator.app.ride.RideEvents
import com.navigator.app.ride.RideFinish
import com.navigator.app.ride.RideSource
import com.navigator.app.ride.rideDurationText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * A silent "Ride saved" row after a mirrored trip (SY4): in mirror mode the rider is in Google Maps and never sees the
 * app's finish screen, so this is how a recorded ride is found again. It opens the ride.
 */
object RideSavedNotice {
    private const val NOTIFICATION_ID = 45

    /** Only a mirrored ride: other rides end in the app, which already shows them. */
    fun shouldPost(ride: RecordedRide): Boolean = ride.source == RideSource.MIRROR

    fun start(context: Context, scope: CoroutineScope) {
        val app = context.applicationContext
        scope.launch {
            RideEvents.lastFinish.filterIsInstance<RideFinish.Saved>().map { it.ride }.collect { ride ->
                if (shouldPost(ride)) post(app, ride)
            }
        }
    }

    private fun post(context: Context, ride: RecordedRide) {
        runCatching {
            NotificationChannels.ensure(context, NotificationChannels.RIDE_SAVED)
            val text = context.getString(
                R.string.rec_saved_text,
                DistanceFormatter.format(ride.distanceMeters, DistanceUnits.METRIC), rideDurationText(context.resources, ride.durationSeconds),
            )
            val n = Notification.Builder(context, NotificationChannels.RIDE_SAVED)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.rec_saved_title))
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(NotificationActions.open(context, NotificationRequestCodes.RIDE_SAVED, AppIntents.REQUEST_RIDE, ride.id))
                .build()
            context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, n)
        }
    }
}
