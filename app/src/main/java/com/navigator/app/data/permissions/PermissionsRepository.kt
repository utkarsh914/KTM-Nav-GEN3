package com.navigator.app.data.permissions

import android.Manifest
import android.content.Context
import android.os.PowerManager
import com.navigator.app.data.settings.SettingsRepository
import com.navigator.app.ui.OpenDashPermissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Permission status for onboarding, Settings, the mirror checklist and the Settings tab dot.
 * Android has no change callback for most of these, so the owner of a screen calls [refresh]
 * on resume and after a permission result.
 */
class PermissionsRepository(
    private val readGrants: () -> PermissionGrants,
    private val settings: SettingsRepository,
    scope: CoroutineScope,
) {
    constructor(context: Context, settings: SettingsRepository, scope: CoroutineScope) :
        this(androidGrantsReader(context.applicationContext), settings, scope)

    private val lock = Any()
    private var grants = readGrants()
    private val _state = MutableStateFlow(PermissionPolicy.build(grants, settings.snapshot.value.engine))

    /** [refresh] updates this before it returns; an engine change in settings follows on [scope]. */
    val state: StateFlow<PermissionsState> = _state.asStateFlow()

    init {
        scope.launch {
            settings.snapshot.map { it.engine }.distinctUntilChanged().collect { publish() }
        }
    }

    fun refresh() {
        synchronized(lock) { grants = readGrants() }
        publish()
    }

    private fun publish() {
        synchronized(lock) { _state.value = PermissionPolicy.build(grants, settings.snapshot.value.engine) }
    }

    private companion object {
        fun androidGrantsReader(context: Context): () -> PermissionGrants = {
            PermissionGrants(
                nearbyDevices = OpenDashPermissions.bluetoothGranted(context),
                location = OpenDashPermissions.isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION),
                locationAlways = OpenDashPermissions.isGranted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                notifications = OpenDashPermissions.notificationsPostGranted(context),
                notificationAccess = OpenDashPermissions.notificationAccessGranted(context),
                batteryUnrestricted = context.getSystemService(PowerManager::class.java)
                    ?.isIgnoringBatteryOptimizations(context.packageName) == true,
            )
        }
    }
}
