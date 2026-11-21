package com.navigator.app.ui.feature.navigate

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.location.Location
import android.location.LocationManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.navigator.app.R
import com.navigator.app.location.hasLocationPermission
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.nav.ktm.DistanceFormatter
import com.navigator.app.nav.model.DistanceUnits
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.theme.KtmIcons

/** Pill shown while planning another trip (confirm, preview) when one keeps running in the background. */
@Composable
internal fun ResumeNavPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    KtmButton(stringResource(R.string.navigate_resume), onClick, modifier, icon = KtmIcons.Navigation)
}

/** Unwrap the hosting Activity from a (possibly wrapped) Compose context. */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Straight-line distance label from [origin] to a point, or null if unknown. */
internal fun distanceLabel(origin: Pair<Double, Double>?, lat: Double, lng: Double): String? {
    origin ?: return null
    val out = FloatArray(1)
    Location.distanceBetween(origin.first, origin.second, lat, lng, out)
    val meters = out[0].toInt()
    if (meters <= 0) return null
    return "~" + DistanceFormatter.format(meters, DistanceUnits.METRIC)
}

/** Location permission granted AND the system location switch on; re-checked whenever the screen resumes. */
@Composable
internal fun rememberLocationReady(): Boolean {
    val context = LocalContext.current
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    fun check(): Boolean {
        if (!hasLocationPermission(context)) return false
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return true
        return runCatching { lm.isLocationEnabled }.getOrDefault(true)
    }
    var ready by remember { mutableStateOf(check()) }
    DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) ready = check()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return ready
}

/** DL3: [place] is wanted while a trip runs; [proceed] carries on once the rider says Replace. */
internal class ReplaceRequest(val place: SavedPlace, val cancelFirst: Boolean, val proceed: () -> Unit)

/** A place waiting to be added as a stop, in a form a bundle can hold. */
internal val SavedPlaceSaver = Saver<SavedPlace?, List<Any>>(
    save = { p -> p?.let { listOf(it.lat, it.lng, it.label, it.address.orEmpty()) } ?: emptyList() },
    restore = { l -> if (l.size < 4) null else SavedPlace(l[0] as Double, l[1] as Double, l[2] as String, (l[3] as String).ifEmpty { null }) },
)
