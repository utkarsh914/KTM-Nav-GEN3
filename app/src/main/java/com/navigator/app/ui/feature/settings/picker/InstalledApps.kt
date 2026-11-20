package com.navigator.app.ui.feature.settings.picker

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The apps a rider could pick: ones with a launcher icon. */
fun interface InstalledApps {
    suspend fun launchable(): List<AppEntry>
}

/** Slow binder IPC (every installed app): always off the main thread. */
class AndroidInstalledApps(context: Context, private val io: CoroutineDispatcher = Dispatchers.IO) : InstalledApps {
    private val pm = context.applicationContext.packageManager

    // The manifest's <queries> lists launcher activities, which is exactly the set wanted here.
    @SuppressLint("QueryPermissionsNeeded")
    override suspend fun launchable(): List<AppEntry> = withContext(io) {
        runCatching {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .map { AppEntry(it.packageName, pm.getApplicationLabel(it).toString()) }
        }.getOrDefault(emptyList())
    }
}
