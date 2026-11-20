package com.navigator.app.ui.feature.settings.picker

import android.content.Context

/** An app's name from its package, or the package itself when it is not installed. */
fun interface AppLabels {
    fun label(packageName: String): String
}

class AndroidAppLabels(context: Context) : AppLabels {
    private val pm = context.applicationContext.packageManager

    override fun label(packageName: String): String = try {
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
        packageName
    }
}

/** Whether [packageName] is installed; false once it has been removed from the phone. */
class AndroidAppInstalled(context: Context) : (String) -> Boolean {
    private val pm = context.packageManager

    override fun invoke(packageName: String): Boolean = try {
        pm.getApplicationInfo(packageName, 0); true
    } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
        false
    }
}
