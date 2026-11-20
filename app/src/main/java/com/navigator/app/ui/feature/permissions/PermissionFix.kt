package com.navigator.app.ui.feature.permissions

import android.Manifest
import androidx.annotation.StringRes
import com.navigator.app.R
import com.navigator.app.data.permissions.PermissionId
import com.navigator.app.data.permissions.PermissionsState

/** Why the app is about to leave for an Android screen; each has a sheet that explains it first. */
enum class Explainer(@StringRes val title: Int, @StringRes val body: Int, @StringRes val go: Int) {
    BACKGROUND_LOCATION(R.string.perm_background_title, R.string.perm_background_body, R.string.perm_background_go),
    NOTIFICATION_ACCESS(R.string.perm_access_title, R.string.perm_access_body, R.string.perm_access_go),
}

/** What tapping Allow on a permission does next. */
sealed interface FixStep {
    /** Android's own permission dialog for these. */
    data class Request(val permissions: List<String>) : FixStep

    /** An explainer sheet first, then Android's screen. */
    data class Explain(val explainer: Explainer) : FixStep

    /** Android's battery dialog for this app. */
    data object OpenBattery : FixStep
}

/** The pure rules for fixing a permission, used by Settings, for onboarding (B17) to reuse. */
object PermissionFix {
    val LOCATION = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    /**
     * "All the time" location can only be asked once plain location is allowed, so it asks for that first and the rider
     * taps again. Fine goes with coarse: Android silently ignores a request for fine alone.
     */
    fun stepFor(id: PermissionId, state: PermissionsState): FixStep = when (id) {
        PermissionId.NEARBY_DEVICES -> FixStep.Request(listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN))
        PermissionId.LOCATION -> FixStep.Request(LOCATION)
        PermissionId.LOCATION_ALWAYS ->
            if (state[PermissionId.LOCATION].granted) FixStep.Explain(Explainer.BACKGROUND_LOCATION) else FixStep.Request(LOCATION)
        PermissionId.NOTIFICATIONS -> FixStep.Request(listOf(Manifest.permission.POST_NOTIFICATIONS))
        PermissionId.NOTIFICATION_ACCESS -> FixStep.Explain(Explainer.NOTIFICATION_ACCESS)
        PermissionId.BATTERY -> FixStep.OpenBattery
    }

    /** Whether the button reads Open (it leaves the app) or Allow (a system dialog). */
    fun opensSettings(id: PermissionId): Boolean = id == PermissionId.NOTIFICATION_ACCESS || id == PermissionId.BATTERY
}
