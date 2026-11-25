package com.navigator.app.ui.map

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.libraries.navigation.NavigationView

/**
 * App-scoped holder that keeps ONE [NavigationView] alive across route changes.
 *
 * The map surface is expensive to spin up (blank frame + tile fetch), so tearing
 * it down every time the user opens Settings and rebuilding it on return caused a
 * sub-second reload flash. Instead this holder is remembered *above* the route
 * switch: the view is created lazily on first map use and only destroyed when the
 * host activity goes away. Returning to the map home just re-attaches the same,
 * already-loaded view (see [rememberRetainedNavigationView]).
 */
class RetainedNavigationView(
    private val context: Context,
    private val lifecycle: Lifecycle,
) {
    /** Read by the map layer, which hosts the view as soon as a page creates it. */
    var view: NavigationView? by mutableStateOf(null)
        private set

    private var funnel: PaddingFunnel? = null
    private var funnelMap: com.google.android.gms.maps.GoogleMap? = null
    private var pendingShell = MapInsets()

    /** Whether the SDK's guidance chrome is on, as the last owner left it (null before any): the SDK re-pads its own chrome when it flips. */
    var guidanceOn: Boolean? = null

    /** Which screen the shared map answers to (see [MapOwnership]). */
    val ownership = MapOwnership()

    /** False while the shell's coverage (bar, rail, strip) is still moving: the map is padded for it once it holds still. */
    var shellSettled: Boolean by mutableStateOf(true)

    /**
     * One funnel for the life of the map: a new owner changes nothing here, it asks for its own padding (the controller
     * replays it) and only a different total is sent, so the camera does not jump while a page change is under way.
     */
    internal fun attachMap(map: com.google.android.gms.maps.GoogleMap) {
        if (funnel != null && funnelMap === map) return
        funnelMap = map
        funnel = PaddingFunnel { p -> runCatching { map.setPadding(p.left, p.top, p.right, p.bottom) } }
            .also { it.setShell(pendingShell); it.attach() }
    }

    /** A page's padding on the shared map (plus the shell's). */
    internal fun requestPadding(p: MapInsets) { funnel?.request(p) }

    internal fun setShellInsets(i: MapInsets) { pendingShell = i; funnel?.setShell(i) }

    internal fun reapplyPadding() { funnel?.reapply() }

    /** How many pages hold the map: with none, the layer behind the pages is hidden from screen readers. */
    var claims: Int by mutableIntStateOf(0)

    /** True once the one-time camera framing has run, so re-entry doesn't snap
     *  the camera back to the starting location. */
    var mapInitialized: Boolean = false

    /** Create the view on first use, bringing it up to the current lifecycle
     *  state; subsequent calls return the same retained instance. */
    fun getOrCreate(): NavigationView {
        view?.let { return it }
        val v = NavigationView(context)
        v.onCreate(Bundle())
        val state = lifecycle.currentState
        if (state.isAtLeast(Lifecycle.State.STARTED)) { v.onStart(); started = true }
        if (state.isAtLeast(Lifecycle.State.RESUMED)) { v.onResume(); resumed = true }
        view = v
        return v
    }

    // The view is told each step once: a view created while the activity is resuming would otherwise get onResume from
    // its creation and again from the lifecycle event, and the SDK throws "Observer already added" on the second.
    private var started = false
    private var resumed = false

    fun onLifecycleEvent(event: Lifecycle.Event) {
        val v = view ?: return
        when (event) {
            Lifecycle.Event.ON_START -> if (!started) { started = true; v.onStart() }
            Lifecycle.Event.ON_RESUME -> if (!resumed) { resumed = true; v.onResume() }
            Lifecycle.Event.ON_PAUSE -> if (resumed) { resumed = false; v.onPause() }
            // The activity saves its state after onStop; the view gets the same call. The map is retained
            // in memory (never restored from this bundle), but the SDK expects the call.
            Lifecycle.Event.ON_STOP -> if (started) { started = false; v.onStop(); v.onSaveInstanceState(Bundle()) }
            else -> {}
        }
    }

    fun onConfigurationChanged(config: Configuration) {
        view?.onConfigurationChanged(config)
    }

    fun onTrimMemory(level: Int) {
        view?.onTrimMemory(level)
    }

    fun destroy() {
        val v = view ?: return
        runCatching { v.onPause() }
        runCatching { v.onStop() }
        runCatching { v.onDestroy() }
        view = null
        started = false
        resumed = false
        mapInitialized = false
        funnel?.detach()
        funnel = null
        funnelMap = null
    }
}

/**
 * Remembers a single [RetainedNavigationView] scoped to the caller. Place this at
 * app scope (above the route switch) so the map survives navigation. The view is
 * only instantiated on first [RetainedNavigationView.getOrCreate], so screens that
 * never show the map (e.g. onboarding, the notification-mirror home) pay nothing.
 */
@Composable
fun rememberRetainedNavigationView(): RetainedNavigationView {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val holder = remember(context) { RetainedNavigationView(context, lifecycleOwner.lifecycle) }
    DisposableEffect(lifecycleOwner, holder) {
        val observer = LifecycleEventObserver { _, event -> holder.onLifecycleEvent(event) }
        // The activity handles configuration changes itself (no recreation), so the view is told here.
        val callbacks = object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) = holder.onConfigurationChanged(newConfig)
            override fun onTrimMemory(level: Int) = holder.onTrimMemory(level)
            @Deprecated("Deprecated in API 34", ReplaceWith("onTrimMemory"))
            override fun onLowMemory() = Unit
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        context.registerComponentCallbacks(callbacks)
        onDispose {
            context.unregisterComponentCallbacks(callbacks)
            lifecycleOwner.lifecycle.removeObserver(observer)
            holder.destroy()
        }
    }
    return holder
}

