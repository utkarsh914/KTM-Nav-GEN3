package com.navigator.app.notify

/**
 * Every PendingIntent request code the app uses, in one place. Two pending intents with the same code and an equal
 * intent are the same pending intent (the later one rewrites the earlier), so each distinct action needs its own.
 */
object NotificationRequestCodes {
    /** Tap on an ongoing notification (connection, recording): front the app on the active trip. */
    const val OPEN_APP = 0
    const val RECORDING_STOP = 1
    const val RECORDING_TOGGLE = 2
    const val BLUETOOTH_TURN_ON = 3
    const val CONNECTION_REPAIR = 4
    const val STATUS_QUIT_NOW = 5
    const val ALERT_OPEN = 6
    const val ALERT_REPAIR = 7
    const val ALERT_QUIT = 8
    const val TOGGLE_BEEPS = 71
    const val LIVE_END_TRIP = 72
    const val RIDE_SAVED = 73
    const val STATUS_QUIT_CONFIRM = 74
}
