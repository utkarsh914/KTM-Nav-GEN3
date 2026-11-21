package com.navigator.app.app

import android.content.Intent
import com.navigator.app.logging.AppLogger
import com.navigator.app.nav.destination.FavoriteSlot
import com.navigator.app.nav.destination.AppShortcuts
import com.navigator.app.nav.destination.SavedPlace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Something from outside the UI that wants the Navigate tab: a share, a shortcut, a notification tap. */
sealed interface AppIntent {
    /** Text shared into the app (ACTION_SEND) that may hold a Google Maps link. */
    data class SharedLink(val text: String) : AppIntent

    /** Launcher shortcut "Ride home / work". */
    data class QuickRoute(val slot: FavoriteSlot) : AppIntent

    /** The foreground notification was tapped while a trip is running. */
    data object OpenNav : AppIntent

    /** A saved place the rider asked to see on the map, or to get [directions] to. */
    data class OpenPlace(val place: SavedPlace, val directions: Boolean = false) : AppIntent

    /** The notification's End trip: ending needs a confirm, so the app opens on it (DL2). */
    data object ConfirmEndTrip : AppIntent

    /** The notification's Quit: opens the quit confirm (DL1). */
    data object ConfirmQuit : AppIntent

    /** The notification's Re-pair: opens the bike's settings page, where the re-pair confirm is. */
    data object OpenRePair : AppIntent

    /** The "Where to?" shortcut: Navigate with the search open. */
    data object OpenSearch : AppIntent

    /** A saved ride (the "Ride saved" notification): its replay. */
    data class OpenRide(val rideId: String) : AppIntent
}

/** What the shell knows when it decides how to treat an [AppIntent]. */
data class AppReadiness(val setupDone: Boolean, val inAppMaps: Boolean)

enum class RejectReason { MAP_NEEDS_IN_APP }

sealed interface IntentOutcome {
    /** Switch to the Navigate tab; the screen consumes the pending item. */
    data object Navigate : IntentOutcome

    /** Keep it pending until first-run setup is finished. */
    data object Defer : IntentOutcome

    /** Forget it: it would be stale by the time setup ends. */
    data object Drop : IntentOutcome

    /** Forget it and tell the rider why nothing happened. */
    data class Reject(val reason: RejectReason) : IntentOutcome

    /** Mirror mode, Ride home / work: Google Maps starts the guidance. */
    data object OpenInMaps : IntentOutcome

    /** The shell handles it itself (a dialog or a page), whatever tab is showing. */
    data object Act : IntentOutcome
}

/** Items waiting for the Navigate screen, at most one of each kind (the newest wins). */
data class PendingIntents(
    val sharedLink: String? = null,
    val quickRoute: FavoriteSlot? = null,
    val openNav: Boolean = false,
    val place: SavedPlace? = null,
    /** The pending [place] is for the route preview, not the place card. */
    val placeDirections: Boolean = false,
    val confirmEnd: Boolean = false,
    val openSearch: Boolean = false,
    val quit: Boolean = false,
    val rePair: Boolean = false,
    val rideId: String? = null,
) {
    val isEmpty: Boolean
        get() = sharedLink == null && quickRoute == null && !openNav && place == null && !confirmEnd && !openSearch && !quit && !rePair && rideId == null
}

/**
 * The one door for outside requests. Producers (the activity, shortcut and notification code) call
 * [post]; the shell watches [pending], applies [decide] and the Navigate screen consumes what it
 * handled. State, not events: with `launchMode=singleTask` a second request reaches the LIVE
 * activity through `onNewIntent`, so there is no fresh composition to read an extra from.
 */
class AppIntents {
    private val _pending = MutableStateFlow(PendingIntents())
    val pending: StateFlow<PendingIntents> = _pending.asStateFlow()

    fun post(intent: AppIntent) {
        _pending.update {
            when (intent) {
                is AppIntent.SharedLink -> it.copy(sharedLink = intent.text)
                is AppIntent.QuickRoute -> it.copy(quickRoute = intent.slot)
                AppIntent.OpenNav -> it.copy(openNav = true)
                is AppIntent.OpenPlace -> it.copy(place = intent.place, placeDirections = intent.directions)
                AppIntent.ConfirmEndTrip -> it.copy(confirmEnd = true)
                AppIntent.ConfirmQuit -> it.copy(quit = true)
                AppIntent.OpenRePair -> it.copy(rePair = true)
                AppIntent.OpenSearch -> it.copy(openSearch = true)
                is AppIntent.OpenRide -> it.copy(rideId = intent.rideId)
            }
        }
    }

    fun consumeSharedLink() = _pending.update { it.copy(sharedLink = null) }
    fun consumeQuickRoute() = _pending.update { it.copy(quickRoute = null) }
    fun consumeOpenNav() = _pending.update { it.copy(openNav = false) }
    fun consumePlace() = _pending.update { it.copy(place = null, placeDirections = false) }
    fun consumeConfirmEnd() = _pending.update { it.copy(confirmEnd = false) }
    fun consumeOpenSearch() = _pending.update { it.copy(openSearch = false) }
    fun consumeQuit() = _pending.update { it.copy(quit = false) }
    fun consumeRePair() = _pending.update { it.copy(rePair = false) }
    fun consumeRide() = _pending.update { it.copy(rideId = null) }

    companion object {
        /** Notification deep-link extra: bring up the active navigation screen. */
        const val EXTRA_OPEN_NAV = "com.navigator.app.extra.OPEN_NAV"

        /** What a notification action or a shortcut asks for, as an extra. */
        const val EXTRA_REQUEST = "com.navigator.app.extra.REQUEST"
        const val EXTRA_RIDE_ID = "com.navigator.app.extra.RIDE_ID"
        const val REQUEST_END_TRIP = "end_trip"
        const val REQUEST_QUIT = "quit"
        const val REQUEST_REPAIR = "repair"
        const val REQUEST_SEARCH = "search"
        const val REQUEST_RIDE = "ride"

        /** The routing table: what to do with [intent] given [readiness]. */
        fun decide(intent: AppIntent, readiness: AppReadiness): IntentOutcome = when (intent) {
            // Both engines show the Navigate tab: in-app resolves the link there, mirror mode offers Google Maps.
            is AppIntent.SharedLink -> if (readiness.setupDone) IntentOutcome.Navigate else IntentOutcome.Defer
            is AppIntent.QuickRoute -> when {
                !readiness.setupDone -> IntentOutcome.Drop
                readiness.inAppMaps -> IntentOutcome.Navigate
                else -> IntentOutcome.OpenInMaps
            }
            // There is no trip to end in mirror mode; the search shortcut still lands on the Navigate tab.
            AppIntent.ConfirmEndTrip ->
                if (readiness.setupDone && readiness.inAppMaps) IntentOutcome.Navigate
                else if (readiness.setupDone) IntentOutcome.Reject(RejectReason.MAP_NEEDS_IN_APP) else IntentOutcome.Drop
            AppIntent.OpenSearch -> if (readiness.setupDone) IntentOutcome.Navigate else IntentOutcome.Drop
            // Quit works at any time: the notification's Quit must also end a link left running during setup.
            AppIntent.ConfirmQuit -> IntentOutcome.Act
            AppIntent.OpenRePair, is AppIntent.OpenRide ->
                if (readiness.setupDone) IntentOutcome.Act else IntentOutcome.Drop
            AppIntent.OpenNav -> if (readiness.setupDone) IntentOutcome.Navigate else IntentOutcome.Drop
            is AppIntent.OpenPlace -> when {
                !readiness.setupDone -> IntentOutcome.Drop
                readiness.inAppMaps -> IntentOutcome.Navigate
                else -> IntentOutcome.Reject(RejectReason.MAP_NEEDS_IN_APP)
            }
        }

        /**
         * The intents an activity launch or `onNewIntent` carries. Pure over the extracted fields so
         * it is JVM-testable; [fromIntent] reads them off a real [Intent].
         */
        fun parse(
            action: String?, type: String?, text: String?, openNav: Boolean, shortcutSlot: FavoriteSlot?,
            request: String? = null, rideId: String? = null,
        ): List<AppIntent> =
            buildList {
                if (action == Intent.ACTION_SEND && type == "text/plain" && !text.isNullOrBlank()) add(AppIntent.SharedLink(text.trim()))
                if (openNav) add(AppIntent.OpenNav)
                shortcutSlot?.let { add(AppIntent.QuickRoute(it)) }
                when (request) {
                    REQUEST_END_TRIP -> add(AppIntent.ConfirmEndTrip)
                    REQUEST_QUIT -> add(AppIntent.ConfirmQuit)
                    REQUEST_REPAIR -> add(AppIntent.OpenRePair)
                    REQUEST_SEARCH -> add(AppIntent.OpenSearch)
                    REQUEST_RIDE -> rideId?.takeIf { it.isNotBlank() }?.let { add(AppIntent.OpenRide(it)) }
                }
            }

        fun fromIntent(intent: Intent?): List<AppIntent> {
            intent ?: return emptyList()
            val parsed = parse(
                action = intent.action,
                type = intent.type,
                text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim(),
                openNav = intent.getBooleanExtra(EXTRA_OPEN_NAV, false),
                shortcutSlot = AppShortcuts.slotFrom(intent),
                request = intent.getStringExtra(EXTRA_REQUEST),
                rideId = intent.getStringExtra(EXTRA_RIDE_ID),
            )
            parsed.filterIsInstance<AppIntent.SharedLink>().firstOrNull()?.let {
                AppLogger.log("MapsUrl", "Received shared text (${it.text.length} chars)")
            }
            return parsed
        }
    }
}
