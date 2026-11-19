package com.navigator.app.app

import android.content.Context
import com.navigator.app.OpenDashApplication
import com.navigator.app.data.permissions.PermissionsRepository
import com.navigator.app.data.settings.SettingsRepository
import com.navigator.app.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency injection: the app-scoped objects ViewModels, services and receivers share.
 * Later batches add rides, places, connection, trip and the other repositories here.
 */
interface AppContainer {
    /** For work that must outlive any screen. Store I/O should switch to `Dispatchers.IO`. */
    val appScope: CoroutineScope
    val settings: SettingsRepository
    val permissions: PermissionsRepository
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val appContext = context.applicationContext

    override val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    override val settings = SettingsRepository(AppSettings(appContext))
    // Lazy: the grant reads touch system services, so keep them off the cold-start path.
    override val permissions by lazy { PermissionsRepository(appContext, settings, appScope) }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as? OpenDashApplication)?.container
        ?: error("appContainer needs OpenDashApplication as the application class")
