package com.navigator.app.notify

import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.navigator.app.app.AppIntents
import com.navigator.app.app.appContainer
import com.navigator.app.settings.AppSettings
import kotlinx.coroutines.launch

/** The buttons on our notifications: open the app on a request, or toggle something without opening it. */
object NotificationActions {
    const val ACTION_TOGGLE_BEEPS = "com.navigator.app.action.TOGGLE_BEEPS"
    const val ACTION_QUIT = "com.navigator.app.action.QUIT"

    private const val IMMUTABLE_UPDATE = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    /** Open the app, optionally on a dialog or page ([request], see [AppIntents.EXTRA_REQUEST]). */
    fun open(context: Context, code: Int, request: String? = null, rideId: String? = null, openNav: Boolean = true): PendingIntent {
        val intent = AppIntents.internal(context)
            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(AppIntents.EXTRA_OPEN_NAV, openNav && request == null)
        request?.let { intent.putExtra(AppIntents.EXTRA_REQUEST, it) }
        rideId?.let { intent.putExtra(AppIntents.EXTRA_RIDE_ID, it) }
        return PendingIntent.getActivity(context, code, intent, IMMUTABLE_UPDATE)
    }

    /** Quit without opening the app: only offered while nothing is running, so there is nothing to confirm. */
    fun quitNow(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, NotificationRequestCodes.STATUS_QUIT_NOW,
        Intent(context, NotificationActionReceiver::class.java).setAction(ACTION_QUIT), IMMUTABLE_UPDATE,
    )

    /** The system's own "turn Bluetooth on" prompt. */
    fun turnOnBluetooth(context: Context): PendingIntent = PendingIntent.getActivity(
        context, NotificationRequestCodes.BLUETOOTH_TURN_ON,
        Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE,
    )

    fun toggleBeeps(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, NotificationRequestCodes.TOGGLE_BEEPS,
        Intent(context, NotificationActionReceiver::class.java).setAction(ACTION_TOGGLE_BEEPS), IMMUTABLE_UPDATE,
    )
}

/** Handles the notification buttons that must not open the app. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NotificationActions.ACTION_TOGGLE_BEEPS -> {
                val settings = AppSettings(context)
                settings.turnBeepEnabled = !settings.turnBeepEnabled
                // The in-process snapshot is a copy: re-read it so the app and the notification agree at once.
                runCatching { context.appContainer.settings.refresh() }
                StatusNotification.refresh(context)
            }
            NotificationActions.ACTION_QUIT -> {
                val container = context.appContainer
                // Something started since the button was drawn: only redraw it (as the one that asks), do nothing else.
                if (container.quit.needsConfirm()) {
                    StatusNotification.refresh(context)
                    return
                }
                val pending = goAsync()
                container.appScope.launch {
                    try {
                        container.quit.quit()
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
