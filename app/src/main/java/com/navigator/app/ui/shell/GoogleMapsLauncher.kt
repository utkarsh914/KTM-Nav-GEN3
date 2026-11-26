package com.navigator.app.ui.shell

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.navigator.app.notifications.NavNotificationFilter

/** The one way the app hands something to Google Maps: Maps itself, else anything that can view it, else the website. */
object GoogleMapsLauncher {
    const val MAPS_PACKAGE = NavNotificationFilter.GOOGLE_MAPS

    /** [intent] is a VIEW of a Maps link or geo: URI. False when nothing at all could open it. */
    fun open(context: Context, intent: Intent): Boolean {
        val inMaps = Intent(intent).setPackage(MAPS_PACKAGE)
        val anyViewer = Intent(intent).setPackage(null)
        return start(context, inMaps) || start(context, anyViewer) || start(context, Intent(Intent.ACTION_VIEW, WEBSITE.toUri()))
    }

    /** Brings Google Maps to the front (or its website when the app is missing). */
    fun openApp(context: Context): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(MAPS_PACKAGE)
        return (launch != null && start(context, launch)) || start(context, Intent(Intent.ACTION_VIEW, WEBSITE.toUri()))
    }

    private fun start(context: Context, intent: Intent) = runCatching { context.startActivity(intent) }.isSuccess

    private const val WEBSITE = "https://maps.google.com"
}
