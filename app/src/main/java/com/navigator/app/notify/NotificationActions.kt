package com.navigator.app.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.navigator.app.app.AppIntents
import com.navigator.app.app.appContainer
import com.navigator.app.settings.AppSettings
import com.navigator.app.ui.MainActivity

/** The buttons on our notifications: open the app on a request, or toggle something without opening it. */
object NotificationActions {
    const val ACTION_TOGGLE_BEEPS = "com.navigator.app.action.TOGGLE_BEEPS"

    private const val IMMUTABLE_UPDATE = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    /** Open the app, optionally on a dialog or page ([request], see [AppIntents.EXTRA_REQUEST]). */
    fun open(context: Context, code: Int, request: String? = null, rideId: String? = null, openNav: Boolean = true): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(AppIntents.EXTRA_OPEN_NAV, openNav && request == null)
        request?.let { intent.putExtra(AppIntents.EXTRA_REQUEST, it) }
        rideId?.let { intent.putExtra(AppIntents.EXTRA_RIDE_ID, it) }
        return PendingIntent.getActivity(context, code, intent, IMMUTABLE_UPDATE)
    }

    fun toggleBeeps(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, NotificationRequestCodes.TOGGLE_BEEPS,
        Intent(context, NotificationActionReceiver::class.java).setAction(ACTION_TOGGLE_BEEPS), IMMUTABLE_UPDATE,
    )
}

/** Handles the notification buttons that must not open the app. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotificationActions.ACTION_TOGGLE_BEEPS) return
        val settings = AppSettings(context)
        settings.turnBeepEnabled = !settings.turnBeepEnabled
        // The in-process snapshot is a copy: re-read it so the app and the notification agree at once.
        runCatching { context.appContainer.settings.refresh() }
        com.navigator.app.nav.providers.NavLiveUpdate.refreshNow()
    }
}
