package com.navigator.app.notify

import android.app.Notification
import android.os.Build

/** Asks Android 16 for the promoted slot (a Live Update); older versions just keep the normal ongoing notification. */
object PromotedOngoing {
    fun apply(builder: Notification.Builder, chip: String? = null) {
        // Both calls are newer than API 36.0: a NoSuchMethodError on older builds just leaves the regular notification.
        chip?.let { runCatching { builder.setShortCriticalText(it) } }
        // setRequestPromotedOngoing is API 36.1: gated so 36.0 builds never reach it.
        if (Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) {
            runCatching { builder.setRequestPromotedOngoing(true) }
        }
    }
}
