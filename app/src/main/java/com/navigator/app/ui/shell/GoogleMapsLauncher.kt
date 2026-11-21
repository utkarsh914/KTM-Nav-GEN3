package com.navigator.app.ui.shell

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.navigator.app.ui.feature.places.PlaceActions

/** Brings Google Maps to the front (or its website when the app is missing). False when neither could open. */
fun openGoogleMapsApp(context: Context): Boolean {
    val intent = context.packageManager.getLaunchIntentForPackage(PlaceActions.MAPS_PACKAGE)
        ?: Intent(Intent.ACTION_VIEW, "https://maps.google.com".toUri())
    return runCatching { context.startActivity(intent) }.isSuccess
}
