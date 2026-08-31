package com.navigator.app.nav.providers

import android.content.Context
import com.google.android.libraries.mapsplatform.turnbyturn.model.NavInfo
import com.google.android.libraries.mapsplatform.turnbyturn.model.NavState
import com.google.android.libraries.navigation.CustomRoutesOptions
import com.google.android.libraries.navigation.Navigator
import com.google.android.libraries.navigation.RoutingOptions
import com.google.android.libraries.navigation.Waypoint
import com.navigator.app.logging.AppLogger
import com.navigator.app.nav.RoutingNavigationProvider
import com.navigator.app.nav.model.DistanceUnits
import com.navigator.app.nav.model.DrivingSide
import com.navigator.app.nav.model.NavDestination
import com.navigator.app.nav.model.NavSessionState
import com.navigator.app.nav.model.NormalizedManeuver
import com.navigator.app.nav.model.NormalizedNavigationState
import com.navigator.app.nav.model.TravelMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Runs Google's own navigation engine in-app and normalises its ~1 Hz
 * `NavInfo`/`StepInfo` feed into [NormalizedNavigationState] for the KTM
 * pipeline. Process singleton so [NavInfoReceivingService] (created by the SDK)
 * can forward updates to it.
 *
 * Orchestration of ToS + `getNavigator` (which needs an Activity) lives in
 * [GoogleNavSdkController]; this object just owns the [Navigator] once ready.
 */
object GoogleNavSdkProvider : RoutingNavigationProvider {

    override val id: String = "google_nav_sdk"

    private val _state = MutableStateFlow(NormalizedNavigationState.IDLE)
    override val state: StateFlow<NormalizedNavigationState> = _state.asStateFlow()

    @Volatile private var navigator: Navigator? = null
    @Volatile private var serviceRegistered = false
    @Volatile private var arrivalListenerAdded = false

    /** Last ENROUTE snapshot, so a REROUTING update can keep showing the previous
     *  turn (dimmed) on the phone instead of blanking the custom guidance header. */
    @Volatile private var lastEnroute: NormalizedNavigationState? = null

    private val arrivalListener = Navigator.ArrivalListener {
        AppLogger.log("Nav", "Nav SDK arrival")
        _state.value = NormalizedNavigationState(
            sessionState = NavSessionState.ARRIVED,
            producedAtMs = System.currentTimeMillis(),
        )
    }

    override fun attach() { /* driven by the controller once the navigator is ready */ }

    override fun detach() {
        stopNavigation()
    }

    /** Called from [GoogleNavSdkController] on NavigatorListener.onNavigatorReady. */
    fun onNavigatorReady(nav: Navigator, context: Context) {
        navigator = nav
        if (!arrivalListenerAdded) {
            nav.addArrivalListener(arrivalListener)
            arrivalListenerAdded = true
        }
        if (!serviceRegistered) {
            serviceRegistered = nav.registerServiceForNavUpdates(
                context.packageName,
                NavInfoReceivingService::class.java.name,
                NUM_NEXT_STEPS_TO_PREVIEW,
            )
            AppLogger.log("Nav", "registerServiceForNavUpdates -> $serviceRegistered")
        }
    }

    override fun startNavigation(dest: NavDestination, mode: TravelMode) {
        val nav = navigator ?: run {
            AppLogger.log("Nav", "!! startNavigation with no navigator")
            return
        }
        val waypoint = Waypoint.builder()
            .setLatLng(dest.lat, dest.lng)
            .also { b -> dest.label?.let { b.setTitle(it) } }
            .build()
        val token = dest.routeToken
        if (!token.isNullOrBlank() && guideWithToken(nav, waypoint, token, mode)) return
        setDestinationAndGuide(nav, waypoint, mode, allowFallback = mode == TravelMode.TWO_WHEELER)
    }

    /**
     * Guide the exact route the user picked in the preview, using the Routes API
     * route token via [CustomRoutesOptions]. Returns false (and the caller falls
     * back to default routing) if the token path can't be used.
     */
    private fun guideWithToken(
        nav: Navigator,
        waypoint: Waypoint,
        token: String,
        mode: TravelMode,
    ): Boolean = runCatching {
        val options = CustomRoutesOptions.builder()
            .setRouteToken(token)
            .setTravelMode(CustomRoutesOptions.TravelMode.TWO_WHEELER)
            .build()
        AppLogger.log("Nav", "setDestinations(routeToken) -> (${waypoint.title ?: "?"})")
        nav.setDestinations(listOf(waypoint), options).setOnResultListener { status ->
            if (status == Navigator.RouteStatus.OK) {
                AppLogger.log("Nav", "Route (token) OK - starting guidance")
                nav.startGuidance()
            } else {
                AppLogger.log("Nav", "token route failed ($status) - default routing")
                setDestinationAndGuide(nav, waypoint, mode, allowFallback = mode == TravelMode.TWO_WHEELER)
            }
        }
        true
    }.getOrElse {
        AppLogger.log("Nav", "setDestinations(routeToken) failed: ${it.message} - default routing")
        false
    }

    private fun setDestinationAndGuide(
        nav: Navigator,
        waypoint: Waypoint,
        mode: TravelMode,
        allowFallback: Boolean,
    ) {
        val options = RoutingOptions().travelMode(
            if (mode == TravelMode.TWO_WHEELER) {
                RoutingOptions.TravelMode.TWO_WHEELER
            } else {
                RoutingOptions.TravelMode.DRIVING
            },
        )
        AppLogger.log("Nav", "setDestination mode=$mode -> (${waypoint.title ?: "?"})")
        nav.setDestination(waypoint, options).setOnResultListener { status ->
            when {
                status == Navigator.RouteStatus.OK -> {
                    AppLogger.log("Nav", "Route OK - starting guidance")
                    nav.startGuidance()
                }
                allowFallback -> {
                    AppLogger.log("Nav", "TWO_WHEELER route failed ($status) - retrying DRIVING")
                    setDestinationAndGuide(nav, waypoint, TravelMode.DRIVING, allowFallback = false)
                }
                else -> {
                    AppLogger.log("Nav", "!! Route failed: $status")
                    _state.value = NormalizedNavigationState(
                        sessionState = NavSessionState.STOPPED,
                        producedAtMs = System.currentTimeMillis(),
                    )
                }
            }
        }
    }

    override fun stopNavigation() {
        navigator?.let { nav ->
            runCatching { nav.stopGuidance() }
            runCatching { nav.clearDestinations() }
        }
        _state.value = NormalizedNavigationState(
            sessionState = NavSessionState.STOPPED,
            producedAtMs = System.currentTimeMillis(),
        )
    }

    /** Forwarded from [NavInfoReceivingService] on each ~1 Hz update. */
    fun onNavInfo(navInfo: NavInfo) {
        val now = System.currentTimeMillis()
        val session = when (navInfo.navState) {
            NavState.ENROUTE -> NavSessionState.ENROUTE
            NavState.REROUTING -> NavSessionState.REROUTING
            NavState.STOPPED -> NavSessionState.STOPPED
            else -> return // UNKNOWN - ignore
        }
        if (session != NavSessionState.ENROUTE) {
            // While rerouting, keep the last turn (the UI dims it) rather than
            // blanking. The dash encoder still blanks on sessionState, unaffected.
            _state.value = if (session == NavSessionState.REROUTING) {
                lastEnroute?.copy(sessionState = NavSessionState.REROUTING, producedAtMs = now)
                    ?: NormalizedNavigationState(sessionState = session, producedAtMs = now)
            } else {
                if (session == NavSessionState.STOPPED) lastEnroute = null
                NormalizedNavigationState(sessionState = session, producedAtMs = now)
            }
            return
        }

        val step = navInfo.currentStep
        val mapped = step?.let { GoogleNavManeuverMap.toNormalized(it.maneuver) }
            ?: MappedManeuver(NormalizedManeuver.UNKNOWN)
        // Ordinal exit count. Retained for logging/diagnostics only: the dash
        // renders roundabout glyphs by exit ANGLE (from the maneuver bucket), not
        // by ordinal, so this does not drive the icon (see KtmManeuverMapping).
        val exit = step?.roundaboutTurnNumber?.let { if (it >= 1) it else null }
        if (exit != null) AppLogger.log("Nav", "roundabout exit #$exit (maneuver=${mapped.maneuver}, rot=${mapped.rotation})")
        val side = step?.let { GoogleNavManeuverMap.drivingSide(it.drivingSide) } ?: DrivingSide.UNKNOWN
        val road = step?.let { it.simpleRoadName ?: it.fullRoadName }
        // The step immediately after the current one, for the "Then <icon>" hint in
        // the custom nav header. remainingSteps starts at the next step; the service
        // is registered for NUM_NEXT_STEPS_TO_PREVIEW previews so this is populated.
        val nextManeuver = runCatching {
            navInfo.remainingSteps?.firstOrNull()?.let {
                GoogleNavManeuverMap.toNormalized(it.maneuver).maneuver
            }
        }.getOrNull()
        // Richer detail for the phone guidance UI (beta getters — guarded so an SDK
        // change degrades to "not shown" rather than crashing).
        val fullInstruction = runCatching {
            step?.fullInstructionText?.let { stripHtml(it) }?.takeIf { it.isNotBlank() }
        }.getOrNull()
        val exitNumber = runCatching {
            step?.exitNumber?.trim()?.takeIf { it.isNotBlank() }
        }.getOrNull()
        val lanes = runCatching {
            step?.lanes?.map { GoogleNavLaneMap.toLaneInfo(it) }.orEmpty()
        }.getOrDefault(emptyList())

        val enroute = NormalizedNavigationState(
            sessionState = NavSessionState.ENROUTE,
            maneuver = mapped.maneuver,
            roundaboutRotation = mapped.rotation,
            roundaboutExit = exit,
            drivingSide = side,
            distanceToManeuverMeters = navInfo.distanceToCurrentStepMeters,
            roadName = road,
            remainingTimeSeconds = navInfo.timeToFinalDestinationSeconds,
            remainingDistanceMeters = navInfo.distanceToFinalDestinationMeters,
            nextManeuver = nextManeuver,
            units = DistanceUnits.METRIC,
            producedAtMs = now,
            fullInstruction = fullInstruction,
            exitNumber = exitNumber,
            lanes = lanes,
        )
        lastEnroute = enroute
        _state.value = enroute
    }

    /** Strip HTML tags/entities from the SDK's `fullInstructionText` (which may embed
     *  `<b>`/`<div>` markup) into plain display text. */
    private fun stripHtml(s: String): String =
        s.replace(Regex("<[^>]*>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace(Regex("\\s+"), " ")
            .trim()

    private const val NUM_NEXT_STEPS_TO_PREVIEW = 3
}
