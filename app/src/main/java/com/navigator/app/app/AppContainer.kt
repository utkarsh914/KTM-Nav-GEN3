package com.navigator.app.app

import android.content.Context
import com.navigator.app.OpenDashApplication
import com.navigator.app.ble.BikeScanner
import com.navigator.app.R
import com.navigator.app.nav.destination.LinkResolution
import com.navigator.app.nav.destination.MapsUrlResolver
import com.navigator.app.ui.feature.navigate.link.LinkOutcome
import com.navigator.app.nav.destination.PlacesClient
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.ui.feature.navigate.link.SharedLinkController
import com.navigator.app.data.connection.ConnectionRepository
import com.navigator.app.data.places.PlacesRepository
import com.navigator.app.data.permissions.PermissionsRepository
import com.navigator.app.data.settings.SettingsRepository
import com.navigator.app.location.SpeedSource
import com.navigator.app.net.ConnectivityMonitor
import com.navigator.app.settings.AppSettings
import com.navigator.app.trip.SdkGuidanceSource
import com.navigator.app.trip.TripController
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
    val intents: AppIntents
    val trip: TripController
    val speed: SpeedSource
    val connection: ConnectionRepository
    val places: PlacesRepository

    /** A shared Google Maps link being resolved; app-scoped so it survives leaving the Navigate tab. */
    val sharedLink: SharedLinkController

    /** The pairing scan (used by the pairing screens). Start and stop it with the screen. */
    val bikeScanner: BikeScanner

    /** The visible activity, for the few SDK calls that need one. Held weakly. */
    val activityHolder: ActivityHolder
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val appContext = context.applicationContext

    override val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    override val settings = SettingsRepository(AppSettings(appContext))
    // Lazy: the grant reads touch system services, so keep them off the cold-start path.
    override val intents = AppIntents()
    override val activityHolder = ActivityHolder()
    override val sharedLink by lazy {
        val auth = PlacesClient.androidAuth(appContext)
        val fallback = appContext.getString(R.string.shared_link_default_label)
        SharedLinkController(appScope) { link ->
            when (val r = MapsUrlResolver.resolveDetailed(link, auth)) {
                is LinkResolution.Found -> LinkOutcome.Found(SavedPlace(r.destination.lat, r.destination.lng, r.destination.label ?: fallback))
                is LinkResolution.Failed -> LinkOutcome.Failed(r.reason)
            }
        }
    }
    override val places by lazy { PlacesRepository(appContext) }
    override val connection by lazy { ConnectionRepository.create(appContext, appScope, settings) }
    override val bikeScanner by lazy { BikeScanner(appContext) { settings.legacy } }
    override val speed by lazy { SpeedSource(appContext) }
    override val trip by lazy {
        TripController(
            scope = // Not `.immediate`: the provider's own state writes must finish before the controller reacts to them.
            CoroutineScope(SupervisorJob() + Dispatchers.Main),
            guidance = SdkGuidanceSource { activityHolder.current },
            isOnline = ConnectivityMonitor.isOnline,
        )
    }
    override val permissions by lazy { PermissionsRepository(appContext, settings, appScope) }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as? OpenDashApplication)?.container
        ?: error("appContainer needs OpenDashApplication as the application class")

/** Weak reference to the foreground activity (set by `MainActivity`). */
class ActivityHolder {
    private var ref: java.lang.ref.WeakReference<android.app.Activity>? = null
    var current: android.app.Activity?
        get() = ref?.get()
        set(value) { ref = value?.let { java.lang.ref.WeakReference(it) } }
}
