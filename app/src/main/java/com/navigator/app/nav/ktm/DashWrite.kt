package com.navigator.app.nav.ktm

import com.navigator.app.ble.BccuProtocol

/**
 * One intended change to the KTM dash's center guidance view, produced by
 * [KtmNavigationEncoder]. The coordinator applies each of these via the
 * existing `BccuConnectionService` public methods - the encoder itself is pure
 * and does no I/O, so its output is fully unit-testable.
 *
 * Label strings are already formatted and length-safe for their characteristic
 * (the protocol layer still ellipsizes as a backstop).
 */
sealed interface DashWrite {

    /** NAVIGATION_STATE (0703): the dash needs guidanceOn=true before it renders guidance. */
    data class SetNavState(val guidanceOn: Boolean, val gpsIconOn: Boolean = true) : DashWrite

    /** TURN_ICON (0704). */
    data class TurnIcon(val icon: BccuProtocol.TurnIcon) : DashWrite

    /** TURN_DISTANCE (0705), e.g. "110 m". */
    data class TurnDistance(val text: String) : DashWrite

    /** TURN_ROAD (0707), e.g. "1st Cross Rd". */
    data class TurnRoad(val text: String) : DashWrite

    /** ETA (0708), e.g. "12:45". */
    data class Eta(val text: String) : DashWrite

    /** REMAINING_DISTANCE (0709), e.g. "12 km". */
    data class RemainingDistance(val text: String) : DashWrite

    /**
     * Blank the center guidance view.
     * @param immediate true = clear now (explicit stop/arrival cleanup);
     *   false = debounced clear (a transient notification remove / brief gap).
     */
    data class ClearGuidance(val immediate: Boolean) : DashWrite
}
