package com.navigator.app.ui.feature.settings.picker

import android.content.Context
import android.content.pm.PackageManager.NameNotFoundException

/** An app's name from its package, or the package itself when it is not installed. */
fun interface AppLabels {
    fun label(packageName: String): String
}

class AndroidAppLabels(context: Context) : AppLabels {
    private val pm = context.applicationContext.packageManager

    override fun label(packageName: String): String = try {
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (e: NameNotFoundException) {
        // Removed since it was chosen: say which app it was rather than showing a package id.
        KnownNames.of(packageName)
    }
}

/** Names for the navigation apps the app knows, for when the app is no longer on the phone. */
object KnownNames {
    private val names = mapOf(
        "com.google.android.apps.maps" to "Google Maps",
        "com.waze" to "Waze",
        "com.here.app.maps" to "HERE WeGo",
        "com.sygic.aura" to "Sygic",
        "net.osmand" to "OsmAnd",
        "com.kurviger.app" to "Kurviger",
    )

    fun of(packageName: String): String = names[packageName] ?: packageName
}

/** Whether [packageName] is installed; false once it has been removed. Sees what the manifest's MAIN `<queries>` shows: launchable apps. */
class AndroidAppInstalled(context: Context) : (String) -> Boolean {
    private val pm = context.packageManager

    override fun invoke(packageName: String): Boolean = try {
        pm.getApplicationInfo(packageName, 0); true
    } catch (e: NameNotFoundException) {
        false
    }
}
