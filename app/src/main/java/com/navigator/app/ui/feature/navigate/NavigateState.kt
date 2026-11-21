package com.navigator.app.ui.feature.navigate

import androidx.annotation.StringRes
import com.navigator.app.R
import com.navigator.app.data.connection.BikeStatus
import com.navigator.app.data.connection.DashSnapshot
import com.navigator.app.nav.DashPreview
import com.navigator.app.nav.destination.FavoritePlace
import com.navigator.app.nav.destination.FavoriteSlot
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.nav.ktm.ClockFormat
import com.navigator.app.nav.ktm.DistanceFormatter
import com.navigator.app.nav.model.DistanceUnits
import com.navigator.app.nav.ktm.EtaFormatter
import com.navigator.app.nav.model.NormalizedNavigationState
import com.navigator.app.nav.model.isActiveNav
import com.navigator.app.trip.TripState
import com.navigator.app.ui.components.DashPreviewState
import com.navigator.app.ui.components.PillTone
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class TileKind { Home, Work, LastTrip, PickOnMap }

/** One tile of the 2 × 2 grid. [place] is null for an unset slot (and always for Pick on map). */
data class WhereToTile(val kind: TileKind, val place: SavedPlace?, val distance: String?) {
    val action: TileAction
        get() = when (kind) {
            TileKind.PickOnMap -> TileAction.Pick
            TileKind.Home -> place?.let(TileAction::Ride) ?: TileAction.SetSlot(FavoriteSlot.HOME)
            TileKind.Work -> place?.let(TileAction::Ride) ?: TileAction.SetSlot(FavoriteSlot.WORK)
            TileKind.LastTrip -> place?.let(TileAction::Ride) ?: TileAction.None
        }
}

sealed interface TileAction {
    /** Straight to the route preview. */
    data class Ride(val place: SavedPlace) : TileAction

    /** An unset Home or Work: open the place picker for it. */
    data class SetSlot(val slot: FavoriteSlot) : TileAction
    data object Pick : TileAction

    /** Nothing to do (no trip yet). */
    data object None : TileAction
}

object WhereToTiles {
    fun build(favorites: List<FavoritePlace>, recents: List<SavedPlace>, distance: (SavedPlace) -> String?): List<WhereToTile> {
        fun slot(s: FavoriteSlot) = favorites.firstOrNull { it.slot == s }?.place
        fun tile(kind: TileKind, p: SavedPlace?) = WhereToTile(kind, p, p?.let(distance))
        return listOf(
            tile(TileKind.Home, slot(FavoriteSlot.HOME)),
            tile(TileKind.Work, slot(FavoriteSlot.WORK)),
            tile(TileKind.LastTrip, recents.firstOrNull()),
            tile(TileKind.PickOnMap, null),
        )
    }
}

/** Straight-line distance, for the "how far" captions. Null without a position or when basically there. */
object StraightLine {
    fun meters(from: Pair<Double, Double>, lat: Double, lng: Double): Int {
        val r = 6_371_000.0
        val p1 = Math.toRadians(from.first)
        val p2 = Math.toRadians(lat)
        val dp = p2 - p1
        val dl = Math.toRadians(lng - from.second)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return (2 * r * atan2(sqrt(a), sqrt(1 - a))).roundToInt()
    }

    fun label(origin: Pair<Double, Double>?, place: SavedPlace): String? {
        origin ?: return null
        val m = meters(origin, place.lat, place.lng)
        return if (m <= 0) null else DistanceFormatter.format(m, DistanceUnits.METRIC)
    }
}

/** What the bike pill says and how it looks. [labelArg] is the bike's name for the named cases. */
data class BikePillUi(
    @StringRes val label: Int,
    val labelArg: String?,
    val tone: PillTone,
    val opensPairing: Boolean,
    /** The state in words for screen readers; the dot alone says it only in colour. */
    @StringRes val state: Int,
)

object BikePillMapper {
    fun map(status: BikeStatus): BikePillUi = when (status) {
        BikeStatus.NotPaired -> BikePillUi(R.string.bike_pill_pair, null, PillTone.NotPaired, opensPairing = true, state = R.string.bike_state_not_paired)
        BikeStatus.BluetoothOff -> BikePillUi(R.string.bike_pill_bluetooth_off, null, PillTone.Offline, false, R.string.bike_state_bluetooth_off)
        is BikeStatus.Connected -> named(status.name, PillTone.Connected, R.string.bike_state_connected)
        is BikeStatus.Connecting -> BikePillUi(R.string.bike_pill_connecting, null, PillTone.Connecting, false, R.string.bike_state_connecting)
        is BikeStatus.Reconnecting -> BikePillUi(R.string.bike_pill_reconnecting, null, PillTone.Connecting, false, R.string.bike_state_reconnecting)
        is BikeStatus.Disconnected -> named(status.name, PillTone.Offline, R.string.bike_state_disconnected)
        is BikeStatus.NeedsRepair -> BikePillUi(R.string.bike_pill_cant_connect, null, PillTone.Offline, false, R.string.bike_state_cant_connect)
    }

    private fun named(name: String?, tone: PillTone, @StringRes state: Int) =
        if (name.isNullOrBlank()) BikePillUi(R.string.bike_pill_generic, null, tone, false, state)
        else BikePillUi(R.string.bike_pill_named, name, tone, false, state)
}

/** Whether the map can show and use the rider's position. */
enum class LocationState {
    Ok, PermissionOff, ServicesOff;

    companion object {
        fun of(permissionGranted: Boolean, servicesOn: Boolean) = when {
            !permissionGranted -> PermissionOff
            !servicesOn -> ServicesOff
            else -> Ok
        }
    }
}

/** What the map is doing, for the placeholder card. */
enum class MapState { Ready, Preparing, Failed, Unavailable }

/** The running trip as the sheet's first row. Text is composed from these parts by the UI. */
data class ActivityCardModel(val distance: String?, val road: String?, val destination: String, val eta: String?)

object ActivityCard {
    /** Only a trip the rider stepped away from, and that is still guiding, gets a card. */
    fun build(trip: TripState, nav: NormalizedNavigationState, nowEpochMs: Long): ActivityCardModel? {
        val dest = trip.dest ?: return null
        if (!trip.minimised || trip.arrivalLatched || !nav.sessionState.isActiveNav()) return null
        return ActivityCardModel(
            distance = nav.preformattedDistance
                ?: nav.distanceToManeuverMeters?.let { DistanceFormatter.format(it, DistanceUnits.METRIC) },
            road = nav.roadName?.takeIf { it.isNotBlank() },
            destination = dest.label ?: "",
            eta = nav.preformattedEta
                ?: nav.remainingTimeSeconds?.let { ClockFormat.local(EtaFormatter.etaEpochMs(it, nowEpochMs)) },
        )
    }
}

object SheetPadding {
    /** The map pads by the visible sheet, but never more than half the screen (a full sheet covers the map anyway). */
    fun mapBottomPx(visibleSheetPx: Int, containerPx: Int): Int =
        min(visibleSheetPx, containerPx / 2).coerceAtLeast(0)

    /** Floating controls ride the sheet while it is below half the screen (where the map padding stops), then fade out. */
    fun controlsVisible(visibleSheetPx: Int, containerPx: Int): Boolean =
        containerPx <= 0 || visibleSheetPx <= containerPx / 2
}

object DashPreviewMapper {
    /** What the dash was last told, as the preview panel shows it. Nothing sent yet reads as [idle]; no connection as [offline]. */
    fun map(
        snapshot: DashSnapshot,
        connected: Boolean,
        time: String,
        idle: DashPreviewState.NotConnected,
        offline: DashPreviewState.NotConnected,
        bannerApp: String,
    ): DashPreviewState {
        val s = snapshot.preview
        if (!connected) return offline
        val guiding = s.guidanceOn == true && (s.distance != null || s.icon != null)
        return when {
            guiding -> DashPreviewState.Turn(
                time = time, icon = s.icon, distance = s.distance.orEmpty(), road = s.road.orEmpty(),
                eta = s.eta.orEmpty(), remaining = s.remaining.orEmpty(),
            )
            s.lastBanner != null -> DashPreviewState.Message(time = time, app = bannerApp, text = s.lastBanner)
            else -> idle
        }
    }
}

/** The in-app welcome that grows out of the bike pill when the dash connects. A blank [name] reads "Welcome!". */
data class GreetingUi(val name: String)

/** Everything the Navigate browse screen shows, from the repositories. */
data class NavigateUiState(
    val tiles: List<WhereToTile> = emptyList(),
    val favorites: List<FavoritePlace> = emptyList(),
    val recents: List<SavedPlace> = emptyList(),
    val bikeStatus: BikeStatus = BikeStatus.NotPaired,
    val bike: BikePillUi = BikePillMapper.map(BikeStatus.NotPaired),
    val dash: DashSnapshot = DashSnapshot(DashPreview.State()),
    val beepsOn: Boolean = true,
    val messagesOn: Boolean = true,
    val activity: ActivityCardModel? = null,
    val userName: String = "",
    val greeting: GreetingUi? = null,
    /** Where the rider is, for the straight-line distances on the rows. */
    val origin: Pair<Double, Double>? = null,
)
