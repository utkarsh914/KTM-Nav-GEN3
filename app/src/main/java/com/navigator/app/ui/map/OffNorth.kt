package com.navigator.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import kotlin.math.abs

/** Below this the map counts as north-up and the compass stays away. */
private const val NORTH_EPSILON_DEG = 0.5f

/**
 * Whether the map is turned away from north. The camera bearing changes every frame while heading-up guidance
 * follows the bike: [bearing] is read here, so the reader recomposes only when this flips, and the compass icon
 * itself rotates in its graphics layer.
 */
@Composable
fun rememberOffNorth(bearing: () -> Float): State<Boolean> =
    remember(bearing) { derivedStateOf { abs(bearing()) > NORTH_EPSILON_DEG } }
