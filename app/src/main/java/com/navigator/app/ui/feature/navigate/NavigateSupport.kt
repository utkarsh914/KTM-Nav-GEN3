package com.navigator.app.ui.feature.navigate

import android.content.Context
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
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.theme.KtmIcons

/** Pill shown while planning another trip (confirm, preview) when one keeps running in the background. */
@Composable
internal fun ResumeNavPill(onClick: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    // [compact]: the room beside Back and the compass is short, so the label is one word.
    val label = stringResource(if (compact) R.string.navigate_resume_short else R.string.navigate_resume)
    KtmButton(label, onClick, modifier, icon = KtmIcons.Navigation)
}

/** Location permission granted AND the system location switch on; re-checked whenever the screen resumes. */
@Composable
internal fun rememberLocationReady(): Boolean {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    fun check(): Boolean {
        if (!hasLocationPermission(context)) return false
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return true
        return runCatching { lm.isLocationEnabled }.getOrDefault(true)
    }
    var ready by remember { mutableStateOf(check()) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) ready = check()
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

/**
 * The last non-null [value]: a stage that is leaving keeps rendering what it showed, while the state it read from has
 * already been cleared (no empty card popping out of the exit transition).
 */
@androidx.compose.runtime.Composable
internal fun <T : Any> rememberLast(value: T?): T? {
    val last = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(value) }
    if (value != null) last.value = value
    return value ?: last.value
}
