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
import com.navigator.app.nav.ktm.KtmManeuverMapping
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

    /** App context, kept to surface a Toast when the picked preview route can't
     *  be honored and guidance falls back to the SDK's own default route. */
    @Volatile private var appContext: Context? = null

    /** True only while guidance is live. Set when guidance actually starts and
     *  cleared on stop/arrival, so continued-movement NavInfo frames that arrive
     *  after the trip ends can't flip the state back to ENROUTE (which would
     *  otherwise resume navigation once the rider keeps moving past the
     *  destination). See [onNavInfo]. */
    @Volatile private var guidanceActive = false

    /** Last ENROUTE snapshot, so a REROUTING update can keep showing the previous
     *  turn (dimmed) on the phone instead of blanking the custom guidance header. */
    @Volatile private var lastEnroute: NormalizedNavigationState? = null

    /** Last logged step (raw maneuver + instruction), so each step is logged once. */
    @Volatile private var lastLoggedStep: String? = null

    /** The destination of the trip currently being guided, for observers that
     *  need to label it (e.g. ride recording). Set on [startNavigation], cleared
     *  on stop/arrival. Exposed as a flow so ride recording can start the moment
     *  a trip is *requested* — independent of whether guidance actually reaches
     *  ENROUTE (which needs network for the initial route). */
    private val _currentDestination = MutableStateFlow<NavDestination?>(null)
    val currentDestination: StateFlow<NavDestination?> = _currentDestination.asStateFlow()

    /** Generation of the trip in progress; every async callback checks it. See [TripGeneration]. */
    private val trips = TripGeneration()
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** Why the last requested trip couldn't start (null while fine). Drives the
     *  route-failure card; cleared when a new trip starts or the rider dismisses it. */
    private val _startFailure = MutableStateFlow<StartFailure?>(null)
    val startFailure: StateFlow<StartFailure?> = _startFailure.asStateFlow()

    fun clearStartFailure() { _startFailure.value = null }

    /** Record a failure that happened before the provider could start the trip
     *  (e.g. no navigator). The trip counts as ended. */
    fun reportStartFailure(reason: StartFailure) {
        onMain {
            trips.invalidate()
            guidanceActive = false
            stopSdkGuidance()
            _currentDestination.value = null
            _startFailure.value = reason
            _state.value = NormalizedNavigationState(
                sessionState = NavSessionState.STOPPED,
                producedAtMs = System.currentTimeMillis(),
            )
        }
    }

    /** Run [block] on the main thread (SDK callbacks may arrive on other threads;
     *  all trip state transitions are serialised on main). */
    private fun onMain(block: () -> Unit) {
        if (android.os.Looper.myLooper() === android.os.Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    private val arrivalListener = Navigator.ArrivalListener { event ->
        val isFinal = event.isFinalDestination
        onMain {
            if (!guidanceActive) return@onMain // late event from a trip that already ended
            if (!isFinal) {
                // Reached an intermediate stop: advance to the next waypoint and keep
                // guidance running. Do NOT emit ARRIVED (that would end the trip).
                AppLogger.log("Nav", "Nav SDK arrival at stop - continuing to next destination")
                runCatching { navigator?.continueToNextDestination() }
                runCatching { navigator?.startGuidance() }
                return@onMain
            }
            AppLogger.log("Nav", "Nav SDK arrival (final)")
            trips.invalidate()
            guidanceActive = false
            lastEnroute = null
            lastLoggedStep = null
            _currentDestination.value = null
            _state.value = NormalizedNavigationState(
                sessionState = NavSessionState.ARRIVED,
                producedAtMs = System.currentTimeMillis(),
            )
        }
    }

    override fun attach() { /* driven by the controller once the navigator is ready */ }

    /** True while an in-app trip is requested or being guided. */
    val isTripInProgress: Boolean
        get() = _currentDestination.value != null || _state.value.sessionState.let {
            it == NavSessionState.ENROUTE || it == NavSessionState.REROUTING
        }

    override fun detach() {
        stopNavigation()
    }

    /** Called from [GoogleNavSdkController] on NavigatorListener.onNavigatorReady. */
    fun onNavigatorReady(nav: Navigator, context: Context) {
        navigator = nav
        appContext = context.applicationContext
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
            reportStartFailure(StartFailure.NAVIGATOR_UNAVAILABLE)
            return
        }
        val trip = trips.next()
        // Frames from a previous trip still being guided must not update state
        // while the new route is computed (guidanceActive is set again on route OK).
        guidanceActive = false
        _startFailure.value = null
        _currentDestination.value = dest
        // Drop the previous trip's terminal state (STOPPED/ARRIVED) so nothing
        // collecting this flow for the new trip mistakes it for this trip ending.
        _state.value = NormalizedNavigationState(
            sessionState = NavSessionState.IDLE,
            producedAtMs = System.currentTimeMillis(),
        )
        // Intermediate stops (in order) followed by the final destination. For a
        // plain single-destination trip this is a one-element list.
        val waypoints = buildWaypoints(dest)
        val token = dest.routeToken
        if (!token.isNullOrBlank()) {
            if (guideWithToken(nav, trip, waypoints, token, mode)) return
            // Token path couldn't even be attempted (SDK threw) - the started
            // route will be the SDK's own, so tell the rider.
            notifyRouteSubstituted()
        }
        setDestinationAndGuide(nav, trip, waypoints, mode, allowFallback = mode == TravelMode.TWO_WHEELER)
    }

    /** Stops (in order) + final destination as SDK [Waypoint]s. */
    private fun buildWaypoints(dest: NavDestination): List<Waypoint> {
        fun wp(lat: Double, lng: Double, label: String?): Waypoint =
            Waypoint.builder()
                .setLatLng(lat, lng)
                .also { b -> label?.let { b.setTitle(it) } }
                .build()
        val list = ArrayList<Waypoint>(dest.stops.size + 1)
        dest.stops.forEach { list += wp(it.lat, it.lng, it.label) }
        list += wp(dest.lat, dest.lng, dest.label)
        return list
    }

    /**
     * Guide the exact route the user picked in the preview, using the Routes API
     * route token via [CustomRoutesOptions]. Returns false (and the caller falls
     * back to default routing) if the token path can't be *attempted* at all.
     *
     * The token is consumed with the *same* [mode] that produced it in the
     * preview: a mismatch (e.g. a DRIVE-derived token consumed as TWO_WHEELER)
     * makes the SDK reject it and silently pick its own route — the exact cause
     * of "preview shows one route, guidance runs another".
     *
     * On a non-OK result we retry the token once (guards a transient reject)
     * before defaulting to the SDK's own route, and surface that substitution to
     * the rider so it isn't a silent swap.
     */
    private fun guideWithToken(
        nav: Navigator,
        trip: Long,
        waypoints: List<Waypoint>,
        token: String,
        mode: TravelMode,
        retriesLeft: Int = 1,
    ): Boolean = runCatching {
        val options = CustomRoutesOptions.builder()
            .setRouteToken(token)
            .setTravelMode(
                if (mode == TravelMode.TWO_WHEELER) {
                    CustomRoutesOptions.TravelMode.TWO_WHEELER
                } else {
                    CustomRoutesOptions.TravelMode.DRIVING
                },
            )
            .build()
        AppLogger.log("Nav", "setDestinations(routeToken, $mode, ${waypoints.size} wp) -> (${AppLogger.sensitive(waypoints.lastOrNull()?.title)})")
        nav.setDestinations(waypoints, options).setOnResultListener { status ->
            onMain {
                if (!trips.isCurrent(trip)) {
                    AppLogger.log("Nav", "Route (token) result $status ignored - trip superseded/ended")
                    return@onMain
                }
                when (RouteResultPolicy.decide(
                    ok = status == Navigator.RouteStatus.OK,
                    canceled = status == Navigator.RouteStatus.ROUTE_CANCELED,
                    canRetry = true,
                )) {
                    RouteResultAction.START -> {
                        AppLogger.log("Nav", "Route (token) OK - starting guidance")
                        guidanceActive = true
                        nav.startGuidance()
                    }
                    // Canceled while this trip is still current: something outside the
                    // provider replaced the request. Never retried; end the trip visibly.
                    RouteResultAction.IGNORE_CANCELED -> failTrip(trip, "token route canceled by the SDK")
                    else -> if (retriesLeft > 0) {
                        AppLogger.log("Nav", "token route failed ($status) - retrying token ($retriesLeft left)")
                        guideWithToken(nav, trip, waypoints, token, mode, retriesLeft - 1)
                    } else {
                        AppLogger.log("Nav", "token route failed ($status) - falling back to default route")
                        notifyRouteSubstituted()
                        setDestinationAndGuide(nav, trip, waypoints, mode, allowFallback = mode == TravelMode.TWO_WHEELER)
                    }
                }
            }
        }
        true
    }.getOrElse {
        AppLogger.log("Nav", "setDestinations(routeToken) failed: ${it.message} - default routing")
        false
    }

    /** Tell the rider the picked route couldn't be loaded so we're using the
     *  recommended one — avoids a surprising silent route swap at Start. */
    private fun notifyRouteSubstituted() {
        val ctx = appContext ?: return
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(
                ctx,
                "Couldn't load the selected route - using the recommended route",
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun setDestinationAndGuide(
        nav: Navigator,
        trip: Long,
        waypoints: List<Waypoint>,
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
        AppLogger.log("Nav", "setDestinations mode=$mode, ${waypoints.size} wp -> (${AppLogger.sensitive(waypoints.lastOrNull()?.title)})")
        nav.setDestinations(waypoints, options).setOnResultListener { status ->
            onMain {
                if (!trips.isCurrent(trip)) {
                    AppLogger.log("Nav", "Route result $status ignored - trip superseded/ended")
                    return@onMain
                }
                when (RouteResultPolicy.decide(
                    ok = status == Navigator.RouteStatus.OK,
                    canceled = status == Navigator.RouteStatus.ROUTE_CANCELED,
                    canRetry = allowFallback,
                )) {
                    RouteResultAction.START -> {
                        AppLogger.log("Nav", "Route OK - starting guidance")
                        guidanceActive = true
                        nav.startGuidance()
                    }
                    RouteResultAction.IGNORE_CANCELED -> failTrip(trip, "route canceled by the SDK")
                    RouteResultAction.RETRY -> {
                        AppLogger.log("Nav", "TWO_WHEELER route failed ($status) - retrying DRIVING")
                        setDestinationAndGuide(nav, trip, waypoints, TravelMode.DRIVING, allowFallback = false)
                    }
                    RouteResultAction.FAIL -> failTrip(trip, "route failed: $status")
                }
            }
        }
    }

    /** Make sure the SDK isn't still guiding (and voicing) a route the app no longer tracks. */
    private fun stopSdkGuidance() {
        navigator?.let { nav ->
            runCatching { nav.stopGuidance() }
            runCatching { nav.clearDestinations() }
        }
    }

    /** The requested route could not be started: end the trip and surface the reason. */
    private fun failTrip(trip: Long, why: String) {
        if (!trips.isCurrent(trip)) return
        AppLogger.log("Nav", "!! $why")
        trips.invalidate()
        guidanceActive = false
        stopSdkGuidance()
        _startFailure.value = StartFailure.ROUTE
        _state.value = NormalizedNavigationState(
            sessionState = NavSessionState.STOPPED,
            producedAtMs = System.currentTimeMillis(),
        )
    }

    override fun stopNavigation() {
        // Stop consuming updates *before* tearing down guidance so any in-flight
        // NavInfo frame (or one produced by continued movement) can't flip the
        // state back to ENROUTE and resume navigation.
        trips.invalidate()
        guidanceActive = false
        lastEnroute = null
        lastLoggedStep = null
        _currentDestination.value = null
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
        // Arrives on the SDK's HandlerThread; hop to main so the guidance check and
        // the state write can't interleave with stopNavigation()/arrival there.
        val trip = trips.current()
        onMain {
            if (trips.isCurrent(trip)) handleNavInfo(navInfo)
        }
    }

    private fun handleNavInfo(navInfo: NavInfo) {
        // Ignore updates once the trip has ended (stopped or arrived). The SDK's
        // NavInfo stream keeps delivering ENROUTE frames while the rider moves
        // past the destination; without this guard those frames would overwrite
        // the ARRIVED/STOPPED state and resume navigation.
        if (!guidanceActive) return
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

        // Raw SDK maneuver per step, so a dash/phone icon mismatch can be traced
        // to what Google actually sent. The log file is shareable, so road names
        // and instruction text (location history) are only logged in debug builds,
        // as lengths otherwise - same policy as the notification path.
        val stepKey = "${step?.maneuver}|$fullInstruction|$road"
        if (stepKey != lastLoggedStep) {
            lastLoggedStep = stepKey
            val chosen = KtmManeuverMapping.resolveIcon(mapped.maneuver, mapped.rotation, side, fullInstruction)
            val textOverride = fullInstruction?.let { KtmManeuverMapping.explicitTurnFromInstruction(it) }
            val text = if (com.navigator.app.BuildConfig.DEBUG) {
                "road=\"$road\", instruction=\"$fullInstruction\""
            } else {
                "road=<${road?.length ?: 0} chars>, instruction=<${fullInstruction?.length ?: 0} chars>"
            }
            AppLogger.log(
                "Nav",
                "Step: sdkManeuver=${step?.maneuver} -> ${mapped.maneuver}, side=$side, " +
                    "icon=${chosen.name}, textTurn=${textOverride?.name}, $text",
            )
        }

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
            units = deviceUnits(),
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

    /** Road distances in miles for the countries that use them; metric elsewhere. */
    private fun deviceUnits(): DistanceUnits =
        if (java.util.Locale.getDefault().country.uppercase() in IMPERIAL_COUNTRIES) DistanceUnits.IMPERIAL
        else DistanceUnits.METRIC

    private val IMPERIAL_COUNTRIES = setOf("US", "GB", "LR", "MM")

    private const val NUM_NEXT_STEPS_TO_PREVIEW = 3
}
