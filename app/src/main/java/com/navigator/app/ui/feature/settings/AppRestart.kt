package com.navigator.app.ui.feature.settings

import android.content.Context
import android.content.Intent

/** Starts the app afresh so restored settings take effect everywhere (some are read once at start). */
object AppRestart {
    fun now(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        val component = launch.component ?: return
        context.startActivity(Intent.makeRestartActivityTask(component))
        Runtime.getRuntime().exit(0)
    }
}
