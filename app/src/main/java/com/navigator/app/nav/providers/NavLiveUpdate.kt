package com.navigator.app.nav.providers

import android.app.Application
import android.app.Notification
import com.google.android.libraries.navigation.NotificationContentProviderBase
import com.navigator.app.notify.StatusNotification

/**
 * What the Navigation SDK's foreground service shows: the app's one status notification ([StatusNotification], the same
 * id the bike link and the recording use). The SDK keeps its service in the foreground even with no trip, so the
 * notification must make sense without one: it then says what else is going on, or that the app is ready.
 */
class NavLiveUpdate(app: Application) : NotificationContentProviderBase(app) {
    private val context = app

    // The SDK calls this inside Service.startForeground: StatusNotification.build never throws.
    override fun getNotification(): Notification = StatusNotification.build(context)
}
