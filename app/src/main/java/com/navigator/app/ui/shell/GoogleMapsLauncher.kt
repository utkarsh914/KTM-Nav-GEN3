package com.navigator.app.ui.shell

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/** Brings Google Maps to the front (or its website when the app is missing). False when neither could open. */
fun openGoogleMapsApp(context: Context): Boolean {
    val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.maps")
        ?: Intent(Intent.ACTION_VIEW, "https://maps.google.com".toUri())
    return runCatching { context.startActivity(intent) }.isSuccess
}
