package com.navigator.app.app

import android.content.Intent
import com.navigator.app.logging.AppLogger
import com.navigator.app.nav.destination.FavoriteSlot
import com.navigator.app.nav.destination.PlaceShortcuts
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
}

/** What the shell knows when it decides how to treat an [AppIntent]. */
data class AppReadiness(val setupDone: Boolean, val inAppMaps: Boolean)

enum class RejectReason { SHORTCUT_NEEDS_IN_APP, MAP_NEEDS_IN_APP }

sealed interface IntentOutcome {
    /** Switch to the Navigate tab; the screen consumes the pending item. */
    data object Navigate : IntentOutcome

    /** Keep it pending until first-run setup is finished. */
    data object Defer : IntentOutcome

    /** Forget it: it would be stale by the time setup ends. */
    data object Drop : IntentOutcome

    /** Forget it and tell the rider why nothing happened. */
    data class Reject(val reason: RejectReason) : IntentOutcome
}

/** Items waiting for the Navigate screen, at most one of each kind (the newest wins). */
data class PendingIntents(
    val sharedLink: String? = null,
    val quickRoute: FavoriteSlot? = null,
    val openNav: Boolean = false,
    val place: SavedPlace? = null,
    /** The pending [place] is for the route preview, not the place card. */
    val placeDirections: Boolean = false,
) {
    val isEmpty: Boolean get() = sharedLink == null && quickRoute == null && !openNav && place == null
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
            }
        }
    }

    fun consumeSharedLink() = _pending.update { it.copy(sharedLink = null) }
    fun consumeQuickRoute() = _pending.update { it.copy(quickRoute = null) }
    fun consumeOpenNav() = _pending.update { it.copy(openNav = false) }
    fun consumePlace() = _pending.update { it.copy(place = null, placeDirections = false) }

    companion object {
        /** Notification deep-link extra: bring up the active navigation screen. */
        const val EXTRA_OPEN_NAV = "com.navigator.app.extra.OPEN_NAV"

        /** The routing table: what to do with [intent] given [readiness]. */
        fun decide(intent: AppIntent, readiness: AppReadiness): IntentOutcome = when (intent) {
            // Both engines show the Navigate tab: in-app resolves the link there, mirror mode offers Google Maps.
            is AppIntent.SharedLink -> if (readiness.setupDone) IntentOutcome.Navigate else IntentOutcome.Defer
            is AppIntent.QuickRoute -> when {
                !readiness.setupDone -> IntentOutcome.Drop
                readiness.inAppMaps -> IntentOutcome.Navigate
                else -> IntentOutcome.Reject(RejectReason.SHORTCUT_NEEDS_IN_APP)
            }
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
        fun parse(action: String?, type: String?, text: String?, openNav: Boolean, shortcutSlot: FavoriteSlot?): List<AppIntent> =
            buildList {
                if (action == Intent.ACTION_SEND && type == "text/plain" && !text.isNullOrBlank()) add(AppIntent.SharedLink(text.trim()))
                if (openNav) add(AppIntent.OpenNav)
                shortcutSlot?.let { add(AppIntent.QuickRoute(it)) }
            }

        fun fromIntent(intent: Intent?): List<AppIntent> {
            intent ?: return emptyList()
            val parsed = parse(
                action = intent.action,
                type = intent.type,
                text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim(),
                openNav = intent.getBooleanExtra(EXTRA_OPEN_NAV, false),
                shortcutSlot = PlaceShortcuts.slotFrom(intent),
            )
            parsed.filterIsInstance<AppIntent.SharedLink>().firstOrNull()?.let {
                AppLogger.log("MapsUrl", "Received shared text (${it.text.length} chars)")
            }
            return parsed
        }
    }
}
