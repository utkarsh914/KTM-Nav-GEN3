package com.navigator.app.nav.providers

import com.google.android.libraries.mapsplatform.turnbyturn.model.Lane
import com.google.android.libraries.mapsplatform.turnbyturn.model.LaneDirection
import com.navigator.app.nav.model.LaneInfo
import com.navigator.app.nav.model.LaneShape

/**
 * Maps the Google Navigation SDK turn-by-turn `Lane`/`LaneDirection` guidance to
 * the app's [LaneInfo]/[LaneShape]. Isolated here (mirroring [GoogleNavManeuverMap])
 * so any SDK API drift only touches this one file.
 *
 * API shape (Nav SDK 7.9.0, beta): `Lane.laneDirections()` -> `List<LaneDirection>`;
 * `LaneDirection.laneShape()` -> `@LaneDirection.LaneShape int`; `isRecommended` ->
 * whether that direction is on the active route. Callers wrap the whole extraction
 * in `runCatching` so a signature change degrades to "no lanes" rather than crashing.
 */
object GoogleNavLaneMap {

    /** Convert one SDK [Lane] to a [LaneInfo] (its directions + whether any of
     *  them leads to the current maneuver). */
    fun toLaneInfo(lane: Lane): LaneInfo {
        val directions = lane.laneDirections().orEmpty()
        val shapes = directions.map { toShape(it.laneShape()) }
        val recommended = directions.any { it.isRecommended }
        return LaneInfo(directions = shapes, recommended = recommended)
    }

    private fun toShape(shape: Int): LaneShape = when (shape) {
        LaneDirection.LaneShape.STRAIGHT -> LaneShape.STRAIGHT
        LaneDirection.LaneShape.SLIGHT_LEFT -> LaneShape.SLIGHT_LEFT
        LaneDirection.LaneShape.NORMAL_LEFT -> LaneShape.LEFT
        LaneDirection.LaneShape.SHARP_LEFT -> LaneShape.SHARP_LEFT
        LaneDirection.LaneShape.U_TURN_LEFT -> LaneShape.UTURN_LEFT
        LaneDirection.LaneShape.SLIGHT_RIGHT -> LaneShape.SLIGHT_RIGHT
        LaneDirection.LaneShape.NORMAL_RIGHT -> LaneShape.RIGHT
        LaneDirection.LaneShape.SHARP_RIGHT -> LaneShape.SHARP_RIGHT
        LaneDirection.LaneShape.U_TURN_RIGHT -> LaneShape.UTURN_RIGHT
        else -> LaneShape.UNKNOWN
    }
}
